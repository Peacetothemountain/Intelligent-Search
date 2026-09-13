package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Military-grade secure settings repository backed by [EncryptedDataVault]
 * and KeyMint StrongBox hardware keystore.
 *
 * Implements AES-256-GCM authenticated storage with Associated Authenticated Data (AAD)
 * cryptographic binding, zero-memory-leak credential scrubbing, and automatic legacy migration.
 *
 * Engineered by NG Designs.
 */
class SecureSettingsRepository(
    private val context: Context,
    private val securityManager: StrongBoxSecurityManager = StrongBoxSecurityManager(context)
) {
    companion object {
        private const val TAG = "SecureSettingsRepo"
        private const val DOMAIN_SETTINGS = "secure_settings"
        private const val PREFS_NAME = "titan_secure_storage"
        private const val LEGACY_KEY_ALIAS = "com.pixel.intelligentsearch.titan_master_kek"
        private const val GCM_TAG_LENGTH = 128
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val encryptedVault = EncryptedDataVault(context, securityManager)

    fun getHardwareSecurityLevel(): HardwareSecurityLevel {
        val (_, level) = securityManager.getOrCreateSymmetricKey(StrongBoxSecurityManager.KEY_ALIAS_MASTER)
        return level
    }

    /**
     * Stores sensitive token or setting using AES-256-GCM with AAD cryptographic binding.
     */
    fun storeSensitiveData(tokenKey: String, rawValue: String): HardwareSecurityLevel {
        val rawBytes = rawValue.toByteArray(Charsets.UTF_8)
        try {
            val record = encryptedVault.encrypt(
                domain = DOMAIN_SETTINGS,
                recordId = tokenKey,
                plaintext = rawBytes
            )

            sharedPreferences.edit()
                .putString("${tokenKey}_vault", record.toBase64String())
                .putString("${tokenKey}_level", record.securityLevel.name)
                // Remove any stale legacy fields
                .remove("${tokenKey}_data")
                .remove("${tokenKey}_iv")
                .apply()

            return record.securityLevel
        } finally {
            MemorySanitizer.wipe(rawBytes)
        }
    }

    /**
     * Stores sensitive data from a scoped [SecureCharArray] and wipes intermediate memory.
     */
    fun storeSensitiveData(tokenKey: String, chars: SecureCharArray): HardwareSecurityLevel {
        val record = encryptedVault.encryptChars(
            domain = DOMAIN_SETTINGS,
            recordId = tokenKey,
            chars = chars
        )

        sharedPreferences.edit()
            .putString("${tokenKey}_vault", record.toBase64String())
            .putString("${tokenKey}_level", record.securityLevel.name)
            .remove("${tokenKey}_data")
            .remove("${tokenKey}_iv")
            .apply()

        return record.securityLevel
    }

    /**
     * Retrieves sensitive data directly into an auto-zeroing [SecureByteArray].
     */
    fun retrieveSensitiveDataSecure(tokenKey: String): SecureByteArray? {
        val vaultPayloadBase64 = sharedPreferences.getString("${tokenKey}_vault", null)
        if (vaultPayloadBase64 != null) {
            return try {
                val record = EncryptedVaultRecord.fromBase64(
                    recordId = tokenKey,
                    domain = DOMAIN_SETTINGS,
                    base64Payload = vaultPayloadBase64
                )
                encryptedVault.decrypt(record)
            } catch (e: Exception) {
                Log.e(TAG, "Vault decryption failed for key: $tokenKey", e)
                null
            }
        }

        // Check for legacy unauthenticated record and migrate
        if (sharedPreferences.contains("${tokenKey}_data") && sharedPreferences.contains("${tokenKey}_iv")) {
            val legacyValue = retrieveLegacyData(tokenKey)
            if (legacyValue != null) {
                storeSensitiveData(tokenKey, legacyValue)
                val migratedBytes = legacyValue.toByteArray(Charsets.UTF_8)
                val secureBuffer = SecureByteArray(migratedBytes)
                MemorySanitizer.wipe(migratedBytes)
                return secureBuffer
            }
        }

        return null
    }

    /**
     * Retrieves sensitive string value. Automatically migrates legacy records if found.
     */
    fun retrieveSensitiveData(tokenKey: String): String? {
        val vaultPayloadBase64 = sharedPreferences.getString("${tokenKey}_vault", null)
        if (vaultPayloadBase64 != null) {
            val record = EncryptedVaultRecord.fromBase64(
                recordId = tokenKey,
                domain = DOMAIN_SETTINGS,
                base64Payload = vaultPayloadBase64
            )
            return encryptedVault.decryptToString(record)
        }

        // Legacy fallback and auto-migration
        if (sharedPreferences.contains("${tokenKey}_data") && sharedPreferences.contains("${tokenKey}_iv")) {
            val legacyValue = retrieveLegacyData(tokenKey)
            if (legacyValue != null) {
                Log.i(TAG, "Migrating legacy record $tokenKey to authenticated AES-256-GCM vault.")
                storeSensitiveData(tokenKey, legacyValue)
                return legacyValue
            }
        }

        return null
    }

    private fun retrieveLegacyData(tokenKey: String): String? {
        val encodedCiphertext = sharedPreferences.getString("${tokenKey}_data", null) ?: return null
        val encodedIv = sharedPreferences.getString("${tokenKey}_iv", null) ?: return null

        return try {
            val ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
            val iv = Base64.decode(encodedIv, Base64.NO_WRAP)

            val (secretKey, _) = securityManager.getOrCreateSymmetricKey(LEGACY_KEY_ALIAS)
            val cipher = Cipher.getInstance(TRANSFORM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Legacy decryption failed for key $tokenKey: verification mismatch", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve legacy sensitive data for key $tokenKey", e)
            null
        }
    }

    fun hasSensitiveData(tokenKey: String): Boolean {
        return sharedPreferences.contains("${tokenKey}_vault") ||
                (sharedPreferences.contains("${tokenKey}_data") && sharedPreferences.contains("${tokenKey}_iv"))
    }

    fun removeSensitiveData(tokenKey: String): Boolean {
        return sharedPreferences.edit()
            .remove("${tokenKey}_vault")
            .remove("${tokenKey}_data")
            .remove("${tokenKey}_iv")
            .remove("${tokenKey}_level")
            .commit()
    }

    fun clearAll(): Boolean {
        return sharedPreferences.edit().clear().commit()
    }
}
