package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
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
                if (context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")) {
                    setIsStrongBoxBacked(true)
                }
            }

            keyPairGenerator.initialize(specBuilder.build())
            keyPairGenerator.generateKeyPair()

            val certChain = keyStore.getCertificateChain(ATTESTATION_ALIAS)
            if (certChain == null || certChain.isEmpty()) {
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

            val securityLevel = if (context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")) {
                HardwareSecurityLevel.STRONGBOX
            } else {
                HardwareSecurityLevel.TEE
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
        }
    }
}
