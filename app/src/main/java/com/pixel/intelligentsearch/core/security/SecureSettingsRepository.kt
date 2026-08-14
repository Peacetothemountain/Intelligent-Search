package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class SecureSettingsRepository(
    context: Context,
    private val securityManager: StrongBoxSecurityManager = StrongBoxSecurityManager(context)
) {
    companion object {
        private const val KEY_ALIAS = "com.pixel.intelligentsearch.titan_master_kek"
        private const val PREFS_NAME = "titan_secure_storage"
        private const val GCM_TAG_LENGTH = 128
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHardwareSecurityLevel(): HardwareSecurityLevel {
        val (_, level) = securityManager.getOrCreateSymmetricKey(KEY_ALIAS)
        return level
    }

    fun storeSensitiveData(tokenKey: String, rawValue: String): HardwareSecurityLevel {
        val (secretKey, level) = securityManager.getOrCreateSymmetricKey(KEY_ALIAS)
        
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(rawValue.toByteArray(Charsets.UTF_8))

        val encodedCiphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP)

        sharedPreferences.edit()
            .putString("${tokenKey}_data", encodedCiphertext)
            .putString("${tokenKey}_iv", encodedIv)
            .putString("${tokenKey}_level", level.name)
            .apply()

        return level
    }

    fun retrieveSensitiveData(tokenKey: String): String? {
        val encodedCiphertext = sharedPreferences.getString("${tokenKey}_data", null) ?: return null
        val encodedIv = sharedPreferences.getString("${tokenKey}_iv", null) ?: return null

        val ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
        val iv = Base64.decode(encodedIv, Base64.NO_WRAP)

        val (secretKey, _) = securityManager.getOrCreateSymmetricKey(KEY_ALIAS)
        
        val cipher = Cipher.getInstance(TRANSFORM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
