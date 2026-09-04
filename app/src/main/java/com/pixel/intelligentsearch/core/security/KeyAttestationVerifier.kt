package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.ProviderException
import java.security.cert.X509Certificate

data class AttestationResult(
    val isHardwareAttested: Boolean,
    val securityLevel: HardwareSecurityLevel,
    val attestationChallenge: String,
    val certificateCount: Int,
    val issuerName: String
)

class KeyAttestationVerifier(private val context: Context) {

    companion object {
        private const val TAG = "KeyAttestationVerifier"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17"
        private const val ATTESTATION_ALIAS = "pixel_hardware_attestation_key"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    fun generateAndVerifyAttestation(challenge: ByteArray = "PIXEL_11_ATTESTATION".toByteArray()): AttestationResult {
        return try {
            if (keyStore.containsAlias(ATTESTATION_ALIAS)) {
                keyStore.deleteEntry(ATTESTATION_ALIAS)
            }

            val hasStrongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            val keyPair: KeyPair = if (hasStrongBox) {
                try {
                    generateEcKeyPair(challenge, isStrongBox = true)
                } catch (e: Exception) {
                    when (e) {
                        is StrongBoxUnavailableException, is ProviderException -> {
                            Log.w(TAG, "StrongBox attestation key generation failed; falling back to TEE", e)
                            generateEcKeyPair(challenge, isStrongBox = false)
                        }
                        else -> throw e
                    }
                }
            } else {
                generateEcKeyPair(challenge, isStrongBox = false)
            }

            val certChain = keyStore.getCertificateChain(ATTESTATION_ALIAS)
            if (certChain.isNullOrEmpty()) {
                return AttestationResult(
                    isHardwareAttested = false,
                    securityLevel = HardwareSecurityLevel.SOFTWARE,
                    attestationChallenge = String(challenge),
                    certificateCount = 0,
                    issuerName = "Unknown"
                )
            }

            val leafCert = certChain[0] as? X509Certificate
            val hasAttestationExtension = leafCert?.getExtensionValue(ATTESTATION_OID) != null
            val issuerName = leafCert?.issuerX500Principal?.name ?: "Unknown"

            val factory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(keyPair.private, KeyInfo::class.java)
            val securityLevel = when (keyInfo.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> HardwareSecurityLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> HardwareSecurityLevel.TEE
                else -> HardwareSecurityLevel.SOFTWARE
            }

            AttestationResult(
                isHardwareAttested = hasAttestationExtension,
                securityLevel = securityLevel,
                attestationChallenge = String(challenge),
                certificateCount = certChain.size,
                issuerName = issuerName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Hardware attestation failed: ${e.message}", e)
            AttestationResult(
                isHardwareAttested = false,
                securityLevel = HardwareSecurityLevel.TEE,
                attestationChallenge = String(challenge),
                certificateCount = 0,
                issuerName = "Fallback/TEE"
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
}
