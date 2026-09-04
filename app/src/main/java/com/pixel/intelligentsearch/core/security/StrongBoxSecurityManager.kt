package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

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
        private const val GCM_TAG_LENGTH = 128
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    fun isStrongBoxSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    fun getOrCreateSymmetricKey(
        alias: String,
        requireBiometric: Boolean = false,
        authTimeoutSeconds: Int = 0
    ): Pair<SecretKey, HardwareSecurityLevel> {
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return Pair(entry.secretKey, getHardwareSecurityLevel(entry.secretKey))
            }
        }

        return if (isStrongBoxSupported()) {
            try {
                Log.i(TAG, "Attempting StrongBox hardware key generation for alias: $alias")
                val key = generateAesKey(alias, isStrongBox = true, requireBiometric, authTimeoutSeconds)
                Pair(key, getHardwareSecurityLevel(key))
            } catch (e: Exception) {
                Log.w(TAG, "StrongBox key generation failed (${e.javaClass.simpleName}). Falling back to TEE.", e)
                try {
                    val key = generateAesKey(alias, isStrongBox = false, requireBiometric, authTimeoutSeconds)
                    Pair(key, getHardwareSecurityLevel(key))
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "TEE fallback key generation failed", fallbackError)
                    throw fallbackError
                }
            }
        } else {
            Log.i(TAG, "StrongBox not supported. Utilizing TEE KeyStore for alias: $alias")
            val key = generateAesKey(alias, isStrongBox = false, requireBiometric, authTimeoutSeconds)
            Pair(key, getHardwareSecurityLevel(key))
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

            if (isStrongBox) {
                setIsStrongBoxBacked(true)
            }

            if (requireBiometric) {
                setUserAuthenticationRequired(true)
                setUserAuthenticationParameters(
                    authTimeoutSeconds,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
                if (authTimeoutSeconds == 0) {
                    setInvalidatedByBiometricEnrollment(true)
                }
            }
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    fun getHardwareSecurityLevel(secretKey: SecretKey): HardwareSecurityLevel {
        return try {
            val factory = SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
            when (keyInfo.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> HardwareSecurityLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> HardwareSecurityLevel.TEE
                else -> HardwareSecurityLevel.SOFTWARE
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query KeyInfo; falling back to system feature check", e)
            if (isStrongBoxSupported()) HardwareSecurityLevel.STRONGBOX else HardwareSecurityLevel.TEE
        }
    }

    fun getSecurityLevelForAlias(alias: String): HardwareSecurityLevel {
        if (!keyStore.containsAlias(alias)) return HardwareSecurityLevel.SOFTWARE
        val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            ?: return HardwareSecurityLevel.SOFTWARE
        return getHardwareSecurityLevel(entry.secretKey)
    }

    fun getInitializedCipher(
        alias: String,
        opmode: Int,
        iv: ByteArray? = null
    ): Cipher {
        val (secretKey, _) = getOrCreateSymmetricKey(alias)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        when (opmode) {
            Cipher.ENCRYPT_MODE -> cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            Cipher.DECRYPT_MODE -> {
                requireNotNull(iv) { "IV cannot be null for decryption mode" }
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            }
            else -> throw IllegalArgumentException("Unsupported cipher operation mode: $opmode")
        }
        return cipher
    }

    fun createCryptoObject(cipher: Cipher): BiometricPrompt.CryptoObject {
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun deleteKey(alias: String): Boolean {
        return try {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete key alias: $alias", e)
            false
        }
    }
}
