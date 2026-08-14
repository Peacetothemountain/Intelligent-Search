package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

enum class HardwareSecurityLevel {
    STRONGBOX,
    TEE,
    SOFTWARE
}

data class EncryptedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val securityLevel: HardwareSecurityLevel
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedPayload
        return ciphertext.contentEquals(other.ciphertext) &&
                iv.contentEquals(other.iv) &&
                securityLevel == other.securityLevel
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + securityLevel.hashCode()
        return result
    }
}

class StrongBoxSecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "StrongBoxSecManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_KEY_SIZE = 256
        private const val RSA_KEY_SIZE = 2048
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    fun isStrongBoxSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    fun getOrCreateSymmetricKey(
        alias: String,
        requireBiometric: Boolean = false,
        authTimeoutSeconds: Int = 0
    ): Pair<SecretKey, HardwareSecurityLevel> {
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return Pair(entry.secretKey, getSecurityLevelForAlias(alias))
            }
        }

        return if (isStrongBoxSupported()) {
            try {
                Log.i(TAG, "Attempting StrongBox key generation for alias: $alias")
                val key = generateAesKey(alias, isStrongBox = true, requireBiometric, authTimeoutSeconds)
                Pair(key, HardwareSecurityLevel.STRONGBOX)
            } catch (e: Exception) {
                when (e) {
                    is StrongBoxUnavailableException, is ProviderException -> {
                        Log.w(TAG, "StrongBox key generation failed. Falling back to TEE.", e)
                        val key = generateAesKey(alias, isStrongBox = false, requireBiometric, authTimeoutSeconds)
                        Pair(key, HardwareSecurityLevel.TEE)
                    }
                    else -> throw e
                }
            }
        } else {
            Log.i(TAG, "StrongBox not supported on device. Utilizing TEE KeyStore for alias: $alias")
            val key = generateAesKey(alias, isStrongBox = false, requireBiometric, authTimeoutSeconds)
            Pair(key, HardwareSecurityLevel.TEE)
        }
    }

    private fun generateAesKey(
        alias: String,
        isStrongBox: Boolean,
        requireBiometric: Boolean,
        authTimeoutSeconds: Int
    ): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(AES_KEY_SIZE)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isStrongBox) {
                setIsStrongBoxBacked(true)
            }

            if (requireBiometric) {
                setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        authTimeoutSeconds,
                        KeyProperties.AUTH_BIOMETRIC_STRONG
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(authTimeoutSeconds)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setInvalidatedByBiometricEnrollment(true)
                }
            }
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    fun getSecurityLevelForAlias(alias: String): HardwareSecurityLevel {
        if (!keyStore.containsAlias(alias)) return HardwareSecurityLevel.SOFTWARE
        return if (isStrongBoxSupported()) {
            HardwareSecurityLevel.STRONGBOX
        } else {
            HardwareSecurityLevel.TEE
        }
    }
}
