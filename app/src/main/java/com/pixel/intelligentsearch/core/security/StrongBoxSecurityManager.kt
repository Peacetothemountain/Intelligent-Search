package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

enum class HardwareSecurityLevel {
    STRONGBOX,
    TEE,
    SOFTWARE
}

/**
 * Android Keystore keys for the app: AES-256-GCM keys generated in StrongBox where the device has it,
 * otherwise in the TEE, used for device-bound backups and the saved backup passphrase.
 *
 * Engineered by NG Designs.
 */
@Singleton
class StrongBoxSecurityManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "StrongBoxSecManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"

        // Probe key measured by deviceKeySecurityLevel(); the alias predates the retired vault and is kept
        // so existing installs reuse their key instead of generating another one.
        private const val KEY_ALIAS_LEVEL_PROBE = "com.pixel.intelligentsearch.vault.master_v1"
        /** Original key: usable without authentication. Only read to migrate an existing saved passphrase. */
        private const val KEY_ALIAS_SAVED_PASSPHRASE_V1 = "com.pixel.intelligentsearch.vault.saved_passphrase_v1"
        /** Usable only while the phone is unlocked and within [PASSPHRASE_AUTH_WINDOW_SECONDS] of user authentication. */
        private const val KEY_ALIAS_SAVED_PASSPHRASE = "com.pixel.intelligentsearch.vault.saved_passphrase_v2"
        private const val PASSPHRASE_AUTH_WINDOW_SECONDS = 60
        // Credential-protected storage only: the saved passphrase must never sit where it's readable before unlock.
        private const val SAVED_PASSPHRASE_PREFS = "secure_hardware_vault"

        // Left behind by the removed EncryptedDataVault / SecureSettingsRepository; deleted once.
        private val RETIRED_KEY_ALIASES = listOf(
            "com.pixel.intelligentsearch.vault.biometric_gate_v1",
            "com.pixel.intelligentsearch.vault.blind_index_v1",
            "com.pixel.intelligentsearch.titan_master_kek"
        )
        private val RETIRED_PREFS_FILES = listOf("titan_secure_storage", "secure_crypto_seed_store")

        // Dynamic circuit-breaker for devices reporting StrongBox in PackageManager but lacking hardware support
        @Volatile
        private var strongBoxDisabledDueToFailure = false
    }

    // Opened on first use, so code paths that never touch the Keystore (e.g. passphrase backups) work
    // where it is unavailable, such as JVM tests.
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /**
     * A busy or briefly unavailable Keystore must not be mistaken for a corrupt key: purging the alias
     * would permanently orphan every backup and saved passphrase it protects.
     */
    private fun throwIfTransientKeystoreFailure(error: Exception) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is android.security.KeyStoreException && cause.isTransientFailure) throw error
            cause = cause.cause
        }
    }

    /**
     * Where this device's Keystore actually puts new keys (StrongBox, TEE or software), measured from a
     * probe key rather than inferred from feature flags.
     */
    fun deviceKeySecurityLevel(): HardwareSecurityLevel =
        getOrCreateSymmetricKey(KEY_ALIAS_LEVEL_PROBE).second

    /** Deletes keys and stores left by removed features. Safe to call repeatedly. */
    fun removeRetiredSecrets() {
        for (alias in RETIRED_KEY_ALIASES) {
            try {
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete retired key $alias: ${e.message}")
            }
        }
        for (name in RETIRED_PREFS_FILES) {
            runCatching { context.deleteSharedPreferences(name) }
            runCatching { context.createDeviceProtectedStorageContext().deleteSharedPreferences(name) }
        }
    }

    fun isStrongBoxSupported(): Boolean {
        if (strongBoxDisabledDueToFailure) return false
        return try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Retrieves or generates a symmetric AES-256 key backed by StrongBox discrete hardware,
     * automatically falling back to ARM TrustZone / Knox TEE if StrongBox is unavailable.
     */
    @Synchronized
    fun getOrCreateSymmetricKey(
        alias: String,
        requireBiometric: Boolean = false,
        authTimeoutSeconds: Int = 0,
        purgeIfUnrecoverable: Boolean = true
    ): Pair<SecretKey, HardwareSecurityLevel> {
        if (keyStore.containsAlias(alias)) {
            val entry = try {
                keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            } catch (e: Exception) {
                throwIfTransientKeystoreFailure(e)
                if (!purgeIfUnrecoverable) throw e
                Log.w(TAG, "Keystore getEntry failed for $alias: ${e.message}")
                null
            }
            if (entry != null) {
                val currentLevel = getHardwareSecurityLevel(entry.secretKey)
                return Pair(entry.secretKey, currentLevel)
            }
            val key = try {
                keyStore.getKey(alias, null) as? SecretKey
            } catch (e: Exception) {
                throwIfTransientKeystoreFailure(e)
                if (!purgeIfUnrecoverable) throw e
                Log.w(TAG, "Keystore getKey failed for $alias: ${e.message}")
                null
            }
            if (key != null) {
                val currentLevel = getHardwareSecurityLevel(key)
                return Pair(key, currentLevel)
            }

            // Key alias exists but cannot be loaded (e.g. invalidated biometric enrollment or corrupt Keymaster state).
            // Keys that protect stored data are never regenerated here: that would orphan the data for good.
            if (!purgeIfUnrecoverable) {
                throw java.security.UnrecoverableKeyException("Keystore alias $alias exists but cannot be loaded")
            }
            Log.w(TAG, "Keystore alias $alias exists but entry is unrecoverable. Purging and regenerating.")
            deleteKey(alias)
        }

        return if (isStrongBoxSupported()) {
            try {
                Log.i(TAG, "Provisioning KeyMint StrongBox AES-256 key for alias: $alias")
                val key = generateAesKey(alias, isStrongBox = true, requireBiometric, authTimeoutSeconds)
                Pair(key, getHardwareSecurityLevel(key))
            } catch (e: Exception) {
                throwIfTransientKeystoreFailure(e)
                if (requireBiometric && e is java.security.InvalidAlgorithmParameterException) {
                    throw e
                }
                Log.w(TAG, "StrongBox key provisioning failed (${e.javaClass.simpleName}); executing atomic TEE fallback.", e)
                strongBoxDisabledDueToFailure = true
                deleteKey(alias)
                val key = generateAesKey(alias, isStrongBox = false, requireBiometric, authTimeoutSeconds)
                Pair(key, getHardwareSecurityLevel(key))
            }
        } else {
            Log.i(TAG, "StrongBox hardware not present or disabled; utilizing ARM TrustZone TEE KeyStore for alias: $alias")
            val key = generateAesKey(alias, isStrongBox = false, requireBiometric, authTimeoutSeconds)
            Pair(key, getHardwareSecurityLevel(key))
        }
    }

    /**
     * Loads an existing key without ever creating or purging one, or returns null if the alias does not
     * exist. Use this when decrypting stored data: a load failure must surface as an error, not as a
     * fresh key that can never decrypt it.
     */
    @Synchronized
    fun getExistingSymmetricKey(alias: String): SecretKey? {
        if (!keyStore.containsAlias(alias)) return null
        return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: (keyStore.getKey(alias, null) as? SecretKey)
            ?: throw java.security.UnrecoverableKeyException("Keystore alias $alias exists but cannot be loaded")
    }

    private fun generateAesKey(
        alias: String,
        isStrongBox: Boolean,
        requireBiometric: Boolean,
        authTimeoutSeconds: Int,
        requireRecentUserAuth: Boolean = false
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
            setRandomizedEncryptionRequired(true)

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
            if (requireRecentUserAuth) {
                // The Keystore itself refuses the key unless the phone is unlocked and the user proved who they
                // are (fingerprint, face or screen lock) in the last minute, e.g. at the protected page's prompt.
                setUserAuthenticationRequired(true)
                setUserAuthenticationParameters(
                    PASSPHRASE_AUTH_WINDOW_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
                setUnlockedDeviceRequired(true)
            }
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Inspects key metadata via [KeyInfo] to determine exact physical residency.
     */
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
            Log.w(TAG, "KeyInfo spec query failed; defaulting to software level: ${e.message}")
            HardwareSecurityLevel.SOFTWARE
        }
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
            Log.e(TAG, "Failed to purge key alias: $alias", e)
            false
        }
    }

    /** A fresh authentication-bound key for the saved passphrase (StrongBox when available, else the TEE). */
    private fun newPassphraseKey(): SecretKey {
        deleteKey(KEY_ALIAS_SAVED_PASSPHRASE)
        // Binding needs a screen lock; without one there is nothing to bind to, so the key is created unbound.
        val bind = (context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isDeviceSecure == true
        if (isStrongBoxSupported()) {
            try {
                return generateAesKey(KEY_ALIAS_SAVED_PASSPHRASE, isStrongBox = true, requireBiometric = false, authTimeoutSeconds = 0, requireRecentUserAuth = bind)
            } catch (e: Exception) {
                throwIfTransientKeystoreFailure(e)
                deleteKey(KEY_ALIAS_SAVED_PASSPHRASE)
            }
        }
        return generateAesKey(KEY_ALIAS_SAVED_PASSPHRASE, isStrongBox = false, requireBiometric = false, authTimeoutSeconds = 0, requireRecentUserAuth = bind)
    }

    /** How saving the passphrase went; [NEEDS_AUTHENTICATION] asks the caller to verify the user and try again. */
    enum class PassphraseSave { SAVED, NEEDS_AUTHENTICATION, FAILED }

    /**
     * Encrypts the backup passphrase with a Keystore key (StrongBox when available) that only works while the
     * phone is unlocked and within [PASSPHRASE_AUTH_WINDOW_SECONDS] of the user authenticating. The Keystore
     * refuses the key outside that window, so the caller then shows the system prompt and calls this again.
     */
    @Synchronized
    fun savePassphrase(passphrase: String): PassphraseSave {
        val passBytes = passphrase.toByteArray(Charsets.UTF_8)
        return try {
            val key = newPassphraseKey()
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(passBytes)
            val sp = context.getSharedPreferences(SAVED_PASSPHRASE_PREFS, Context.MODE_PRIVATE)
            sp.edit()
                .putString("saved_passphrase_cipher", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
                .putString("saved_passphrase_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .commit()
            PassphraseSave.SAVED
        } catch (e: android.security.keystore.UserNotAuthenticatedException) {
            // Longer than the window since the last unlock or prompt: not a failure, just a prompt away.
            deleteKey(KEY_ALIAS_SAVED_PASSPHRASE)
            PassphraseSave.NEEDS_AUTHENTICATION
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save passphrase to hardware security chip", e)
            PassphraseSave.FAILED
        } finally {
            MemorySanitizer.wipe(passBytes)
        }
    }

    /** Whether a passphrase is saved, without decrypting it. */
    fun hasSavedPassphrase(): Boolean = runCatching {
        context.getSharedPreferences(SAVED_PASSPHRASE_PREFS, Context.MODE_PRIVATE)
            .contains("saved_passphrase_cipher")
    }.getOrDefault(false)

    /**
     * Decrypts the saved backup passphrase, or returns null if none is saved or the user hasn't authenticated recently.
     */
    @Synchronized
    fun getSavedPassphrase(): String? {
        var decrypted: ByteArray? = null
        return try {
            val sp = context.getSharedPreferences(SAVED_PASSPHRASE_PREFS, Context.MODE_PRIVATE)
            val cipherBase64 = sp.getString("saved_passphrase_cipher", null) ?: return null
            val ivBase64 = sp.getString("saved_passphrase_iv", null) ?: return null
            val cipherBytes = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val boundKey = getExistingSymmetricKey(KEY_ALIAS_SAVED_PASSPHRASE)
            val key = boundKey ?: getExistingSymmetricKey(KEY_ALIAS_SAVED_PASSPHRASE_V1) ?: return null
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            decrypted = cipher.doFinal(cipherBytes)
            val passphrase = String(decrypted, Charsets.UTF_8)
            // One-time move of a passphrase saved under the old unbound key onto the authentication-bound one.
            if (boundKey == null && savePassphrase(passphrase) == PassphraseSave.SAVED) deleteKey(KEY_ALIAS_SAVED_PASSPHRASE_V1)
            passphrase
        } catch (e: android.security.keystore.UserNotAuthenticatedException) {
            // Too long since the user authenticated: they type the passphrase instead.
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve saved passphrase: ${e.javaClass.simpleName}")
            null
        } finally {
            MemorySanitizer.wipe(decrypted)
        }
    }

    /**
     * Clears any saved passphrase from the hardware security chip and storage.
     */
    @Synchronized
    fun clearSavedPassphrase(): Boolean {
        return try {
            val sp = context.getSharedPreferences(SAVED_PASSPHRASE_PREFS, Context.MODE_PRIVATE)
            sp.edit().remove("saved_passphrase_cipher").remove("saved_passphrase_iv").commit()
            deleteKey(KEY_ALIAS_SAVED_PASSPHRASE)
            deleteKey(KEY_ALIAS_SAVED_PASSPHRASE_V1)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear saved passphrase", e)
            false
        }
    }
}
