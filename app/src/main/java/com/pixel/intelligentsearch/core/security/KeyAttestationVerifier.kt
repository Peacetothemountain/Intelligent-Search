package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.security.SecureRandom
import java.security.cert.X509Certificate

data class AttestationResult(
    val isHardwareAttested: Boolean,
    val securityLevel: HardwareSecurityLevel,
    val attestationChallenge: String,
    val certificateCount: Int,
    val issuerName: String,
    val verifiedBootState: String = "VERIFIED",
    val deviceLocked: Boolean = true,
    val osPatchLevel: Int = 0,
    val isRootVerified: Boolean = false
)

/**
 * Hardware key attestation for the settings "about" panel.
 *
 * Generates an attestation key (StrongBox when available, else the TEE), checks that each certificate in its
 * chain is signed by the next and that the root is one of Google's published attestation roots, and parses the
 * attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) to confirm the challenge and security level. The result
 * is cached in memory for up to a day because generating a StrongBox key takes about a second.
 *
 * Engineered by NG Designs.
 */
class KeyAttestationVerifier(private val context: Context) {

    companion object {
        private const val TAG = "KeyAttestationVerifier"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17"
        private const val ATTESTATION_ALIAS = "is_hardware_attestation_key_v2"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // re-attest at most daily per process
        private const val GOOGLE_HW_ROOT_SHA256 = "2417A2069E66F42F995D7A942F2D9C55CDA3F29C280FB06DF7B09FEE13A28F58"
        private const val GOOGLE_SW_ROOT_SHA256 = "CE0EB739F57CF586E80EE7ED0E21D0D27F51D68ECDAFEFEE824E31671569B752"
        // The two roots Google currently publishes at https://android.googleapis.com/attestation/root
        // (fingerprints verified against that list on 2026-10-11).
        // "Key Attestation CA1", EC P-384, valid 2025-07-17 to 2035-07-15.
        private const val GOOGLE_2026_ECDSA_ROOT_SHA256 = "6D9DB4CE6C5C0B293166D08986E05774A8776CEB525D9E4329520DE12BA4BCC0"
        // RSA-4096 root, serial f92009e853b6b045, valid 2022-03-20 to 2042-03-15.
        private const val GOOGLE_RKP_RSA_ROOT_SHA256 = "CEDB1CB6DC896AE5EC797348BCE9286753C2B38EE71CE0FBE34A9A1248800DFC"
        private const val SAMSUNG_ROOT_SHA256 = "717CC80CE5CF852BDE1F33F6E8FE3D634842E5E6DEE3E5B5D829A09FD0DECE8B"

        private val TRUSTED_HARDWARE_ROOT_FINGERPRINTS = setOf(
            GOOGLE_HW_ROOT_SHA256,
            GOOGLE_2026_ECDSA_ROOT_SHA256,
            GOOGLE_RKP_RSA_ROOT_SHA256,
            SAMSUNG_ROOT_SHA256
        )

        private val TRUSTED_ROOT_FINGERPRINTS = TRUSTED_HARDWARE_ROOT_FINGERPRINTS + GOOGLE_SW_ROOT_SHA256

        @Volatile
        private var cachedResult: AttestationResult? = null
        @Volatile
        private var cacheTimestamp: Long = 0L
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Generates an EC P-256 key pair in StrongBox/TEE with attestation challenge,
     * queries the certificate chain, validates root signatures, parses the ASN.1 extension,
     * and returns the comprehensive hardware verification result.
     */
    @Synchronized
    fun generateAndVerifyAttestation(): AttestationResult {
        val now = System.currentTimeMillis()
        // Every attestation uses a fresh self-generated challenge, so a result cached within the last day is
        // returned as is.
        val cached = cachedResult
        if (cached != null && (now - cacheTimestamp < CACHE_TTL_MS)) {
            return cached
        }

        val actualChallenge = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val challengeDisplay = Base64.encodeToString(actualChallenge, Base64.NO_WRAP)

        return try {
            if (keyStore.containsAlias(ATTESTATION_ALIAS)) {
                keyStore.deleteEntry(ATTESTATION_ALIAS)
            }

            val hasStrongBox = try {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            } catch (_: Exception) {
                false
            }

            val keyPair: KeyPair = if (hasStrongBox) {
                try {
                    generateEcKeyPair(actualChallenge, isStrongBox = true)
                } catch (e: Exception) {
                    // StrongBoxUnavailableException / ProviderException are the expected causes, but any
                    // failure here should degrade to a TEE-backed attestation rather than abort.
                    val reason = if (e is StrongBoxUnavailableException || e is ProviderException) {
                        "StrongBox attestation key generation failed"
                    } else {
                        "StrongBox attestation attempt encountered an unexpected exception"
                    }
                    Log.w(TAG, "$reason; falling back to TEE", e)
                    if (keyStore.containsAlias(ATTESTATION_ALIAS)) keyStore.deleteEntry(ATTESTATION_ALIAS)
                    generateEcKeyPair(actualChallenge, isStrongBox = false)
                }
            } else {
                generateEcKeyPair(actualChallenge, isStrongBox = false)
            }

            val certChain = keyStore.getCertificateChain(ATTESTATION_ALIAS)
            if (certChain.isNullOrEmpty()) {
                return AttestationResult(
                    isHardwareAttested = false,
                    securityLevel = HardwareSecurityLevel.SOFTWARE,
                    attestationChallenge = challengeDisplay,
                    certificateCount = 0,
                    issuerName = "Unknown",
                    verifiedBootState = "UNKNOWN",
                    deviceLocked = false,
                    isRootVerified = false
                )
            }

            // Verify certificate chain continuity
            var isChainValid = true
            for (i in 0 until certChain.size - 1) {
                val current = certChain[i] as? X509Certificate
                val parent = certChain[i + 1] as? X509Certificate
                if (current != null && parent != null) {
                    try {
                        current.verify(parent.publicKey)
                        current.checkValidity()
                    } catch (certEx: Exception) {
                        Log.w(TAG, "Certificate verification failed at chain index $i: ${certEx.message}")
                        isChainValid = false
                        break
                    }
                }
            }

            // Verify Root Certificate
            val rootCert = certChain.last() as? X509Certificate
            var isRootVerified = false
            var isHardwareRootVerified = false
            if (rootCert != null) {
                try {
                    rootCert.verify(rootCert.publicKey)
                    rootCert.checkValidity()

                    val rootFingerprint = MessageDigest.getInstance("SHA-256").digest(rootCert.encoded)
                        .joinToString("") { "%02X".format(it) }

                    isRootVerified = rootFingerprint in TRUSTED_ROOT_FINGERPRINTS
                    isHardwareRootVerified = rootFingerprint in TRUSTED_HARDWARE_ROOT_FINGERPRINTS
                } catch (e: Exception) {
                    Log.w(TAG, "Root certificate verification failed: ${e.message}")
                }
            }

            val leafCert = certChain[0] as? X509Certificate
            val extensionBytes = leafCert?.getExtensionValue(ATTESTATION_OID)
            val parsedExtension = extensionBytes?.let { parseAttestationExtension(it) }

            val hardwareSecurityLevel = try {
                val factory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
                val keyInfo = factory.getKeySpec(keyPair.private, KeyInfo::class.java)
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> HardwareSecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> HardwareSecurityLevel.TEE
                    else -> HardwareSecurityLevel.SOFTWARE
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed retrieving KeyInfo for attestation key: ${e.message}")
                // Don't guess from the device having StrongBox: the key may have fallen back to the TEE. Use what
                // the attestation certificate itself states, or nothing.
                parsedExtension?.keymasterSecurityLevel ?: HardwareSecurityLevel.SOFTWARE
            }

            val challengeMatches = parsedExtension?.attestationChallenge?.contentEquals(actualChallenge) == true
            val result = AttestationResult(
                isHardwareAttested = certChain.size >= 2 && isChainValid && isHardwareRootVerified && parsedExtension != null && challengeMatches && parsedExtension.attestationSecurityLevel != HardwareSecurityLevel.SOFTWARE,
                securityLevel = hardwareSecurityLevel,
                attestationChallenge = challengeDisplay,
                certificateCount = certChain.size,
                issuerName = leafCert?.issuerX500Principal?.name ?: "Unknown",
                verifiedBootState = parsedExtension?.verifiedBootState ?: "UNVERIFIED",
                deviceLocked = parsedExtension?.deviceLocked ?: false,
                osPatchLevel = parsedExtension?.osPatchLevel ?: 0,
                isRootVerified = isRootVerified
            )

            cachedResult = result
            cacheTimestamp = now
            result
        } catch (e: Exception) {
            Log.e(TAG, "Hardware attestation failed: ${e.message}", e)
            AttestationResult(
                isHardwareAttested = false,
                securityLevel = HardwareSecurityLevel.SOFTWARE,
                attestationChallenge = challengeDisplay,
                certificateCount = 0,
                issuerName = "Unverified",
                verifiedBootState = "UNVERIFIED",
                deviceLocked = false,
                isRootVerified = false
            )
        } finally {
            try {
                if (keyStore.containsAlias(ATTESTATION_ALIAS)) {
                    keyStore.deleteEntry(ATTESTATION_ALIAS)
                }
            } catch (cleanupEx: Exception) {
                Log.w(TAG, "Failed to clean up attestation key", cleanupEx)
            }
        }
    }

    private fun generateEcKeyPair(challenge: ByteArray, isStrongBox: Boolean): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            ATTESTATION_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            setAttestationChallenge(challenge)
            if (isStrongBox) {
                setIsStrongBoxBacked(true)
            }
        }

        keyPairGenerator.initialize(specBuilder.build())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Pure Kotlin ASN.1 DER parser extracting KeyDescription and RootOfTrust
     * metadata from extension OID 1.3.6.1.4.1.11129.2.1.17.
     */
    private fun parseAttestationExtension(rawExtensionBytes: ByteArray): ParsedAttestationRecord? {
        return try {
            // rawExtensionBytes is an OCTET STRING wrapping the DER KeyDescription sequence
            val outerParser = DerParser(rawExtensionBytes)
            val outerTag = outerParser.readTag()
            if ((outerTag and 0xFF) != 0x04) return null // Must be OCTET STRING
            val innerBytes = outerParser.readBytes(outerParser.readLength())

            val parser = DerParser(innerBytes)
            val seqTag = parser.readTag()
            if ((seqTag and 0xFF) != 0x30) return null // Must be SEQUENCE
            parser.readLength()

            // 1. attestationVersion (INTEGER)
            val verTag = parser.readTag()
            val verLen = parser.readLength()
            val attestationVersion = parser.readInteger(verLen)

            // 2. attestationSecurityLevel (ENUMERATED)
            val attSecTag = parser.readTag()
            val attSecLen = parser.readLength()
            val attSecVal = parser.readInteger(attSecLen)
            val attestationSecurityLevel = when (attSecVal) {
                2 -> HardwareSecurityLevel.STRONGBOX
                1 -> HardwareSecurityLevel.TEE
                else -> HardwareSecurityLevel.SOFTWARE
            }

            // 3. keymasterVersion (INTEGER)
            parser.readTag()
            val kmVerLen = parser.readLength()
            val keymasterVersion = parser.readInteger(kmVerLen)

            // 4. keymasterSecurityLevel (ENUMERATED)
            parser.readTag()
            val kmSecLen = parser.readLength()
            val kmSecVal = parser.readInteger(kmSecLen)
            val keymasterSecurityLevel = when (kmSecVal) {
                2 -> HardwareSecurityLevel.STRONGBOX
                1 -> HardwareSecurityLevel.TEE
                else -> HardwareSecurityLevel.SOFTWARE
            }

            // 5. attestationChallenge (OCTET STRING)
            parser.readTag()
            val challengeLen = parser.readLength()
            val challengeBytes = parser.readBytes(challengeLen)

            // 6. uniqueId (OCTET STRING)
            parser.readTag()
            val uniqueIdLen = parser.readLength()
            parser.readBytes(uniqueIdLen)

            // 7. softwareEnforced (AuthorizationList SEQUENCE)
            parser.skipElement()

            // 8. teeEnforced / hardwareEnforced (AuthorizationList SEQUENCE)
            var verifiedBootState = "UNKNOWN"
            var deviceLocked = false
            var osVersion = 0
            var osPatchLevel = 0

            if (parser.hasMore()) {
                val hwSeqTag = parser.readTag()
                if ((hwSeqTag and 0xFF) == 0x30) {
                    val hwEnd = parser.offset + parser.readLength()
                    while (parser.offset < hwEnd && parser.hasMore()) {
                        val elementTag = parser.readTag()
                        val elementLen = parser.readLength()
                        val tagNumber = elementTag and 0xFFFF

                        when (tagNumber) {
                            704 -> {
                                // RootOfTrust SEQUENCE
                                val rotBytes = parser.readBytes(elementLen)
                                val rotParser = DerParser(rotBytes)
                                if (rotParser.hasMore() && (rotParser.readTag() and 0xFF) == 0x30) {
                                    rotParser.readLength()
                                    // verifiedBootKey (OCTET STRING)
                                    rotParser.skipElement()
                                    // deviceLocked (BOOLEAN)
                                    if (rotParser.hasMore()) {
                                        rotParser.readTag()
                                        val dlLen = rotParser.readLength()
                                        deviceLocked = rotParser.readBoolean(dlLen)
                                    }
                                    // verifiedBootState (ENUMERATED)
                                    if (rotParser.hasMore()) {
                                        rotParser.readTag()
                                        val vbsLen = rotParser.readLength()
                                        val vbsVal = rotParser.readInteger(vbsLen)
                                        verifiedBootState = when (vbsVal) {
                                            0 -> "VERIFIED"
                                            1 -> "SELF_SIGNED"
                                            2 -> "UNVERIFIED"
                                            else -> "FAILED"
                                         }
                                    }
                                }
                            }
                            705 -> {
                                osVersion = parser.readExplicitInteger(elementLen)
                            }
                            706 -> {
                                osPatchLevel = parser.readExplicitInteger(elementLen)
                            }
                            else -> {
                                parser.skipBytes(elementLen)
                            }
                        }
                    }
                }
            }

            ParsedAttestationRecord(
                attestationVersion = attestationVersion,
                attestationSecurityLevel = attestationSecurityLevel,
                keymasterVersion = keymasterVersion,
                keymasterSecurityLevel = keymasterSecurityLevel,
                attestationChallenge = challengeBytes,
                verifiedBootState = verifiedBootState,
                deviceLocked = deviceLocked,
                osVersion = osVersion,
                osPatchLevel = osPatchLevel
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ASN.1 attestation record", e)
            null
        }
    }

    private data class ParsedAttestationRecord(
        val attestationVersion: Int,
        val attestationSecurityLevel: HardwareSecurityLevel,
        val keymasterVersion: Int,
        val keymasterSecurityLevel: HardwareSecurityLevel,
        val attestationChallenge: ByteArray,
        val verifiedBootState: String,
        val deviceLocked: Boolean,
        val osVersion: Int,
        val osPatchLevel: Int
    )

    private class DerParser(private val bytes: ByteArray) {
        var offset = 0

        fun hasMore(): Boolean = offset < bytes.size

        fun readTag(): Int {
            val first = bytes[offset++].toInt() and 0xFF
            if ((first and 0x1F) != 0x1F) {
                return first
            }
            var tagNo = 0
            while (offset < bytes.size) {
                val b = bytes[offset++].toInt() and 0xFF
                tagNo = (tagNo shl 7) or (b and 0x7F)
                if ((b and 0x80) == 0) break
            }
            return (first and 0xE0 shl 16) or tagNo
        }

        fun readLength(): Int {
            val b = bytes[offset++].toInt() and 0xFF
            if ((b and 0x80) == 0) {
                if (b > bytes.size - offset) {
                    throw IllegalArgumentException("DER length out of bounds: $b")
                }
                return b
            }
            val numBytes = b and 0x7F
            if (numBytes < 1 || numBytes > 4) {
                throw IllegalArgumentException("Invalid DER length byte count: $numBytes")
            }
            var len = 0
            for (i in 0 until numBytes) {
                len = (len shl 8) or (bytes[offset++].toInt() and 0xFF)
            }
            if (len < 0 || len > bytes.size - offset) {
                throw IllegalArgumentException("DER length out of bounds: $len")
            }
            return len
        }

        fun readBytes(length: Int): ByteArray {
            val result = bytes.copyOfRange(offset, minOf(offset + length, bytes.size))
            offset += length
            return result
        }

        fun skipBytes(length: Int) {
            offset = minOf(offset + length, bytes.size)
        }

        fun skipElement() {
            readTag()
            val len = readLength()
            skipBytes(len)
        }

        fun readInteger(length: Int): Int {
            var value = 0
            for (i in 0 until length) {
                if (offset >= bytes.size) break
                value = (value shl 8) or (bytes[offset++].toInt() and 0xFF)
            }
            return value
        }

        fun readExplicitInteger(length: Int): Int {
            if (length >= 2 && offset < bytes.size && bytes[offset] == 0x02.toByte()) {
                offset++ // skip 0x02 tag
                val innerLen = readLength()
                return readInteger(innerLen)
            }
            return readInteger(length)
        }

        fun readBoolean(length: Int): Boolean {
            var boolVal = false
            for (i in 0 until length) {
                if (offset >= bytes.size) break
                if (bytes[offset++] != 0.toByte()) boolVal = true
            }
            return boolVal
        }
    }
}
