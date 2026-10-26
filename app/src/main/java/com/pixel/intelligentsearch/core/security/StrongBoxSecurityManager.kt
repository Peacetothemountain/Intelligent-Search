package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
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
        /**
         * Its partner, bound the same way. A new passphrase is encrypted under whichever of the two isn't in use, so
         * the working key is only deleted once its replacement is stored.
         */
        private const val KEY_ALIAS_SAVED_PASSPHRASE_ALT = "com.pixel.intelligentsearch.vault.saved_passphrase_v2_alt"
        private const val PASSPHRASE_AUTH_WINDOW_SECONDS = 60
        // Credential-protected storage only: the saved passphrase must never sit where it's readable before unlock.
        private const val SAVED_PASSPHRASE_PREFS = "secure_hardware_vault"
        private const val PREF_PASSPHRASE_CIPHER = "saved_passphrase_cipher"
        private const val PREF_PASSPHRASE_IV = "saved_passphrase_iv"
        /** Which of the bound pair encrypted the stored passphrase; absent when it was saved before the pair existed. */
        private const val PREF_PASSPHRASE_KEY_ALIAS = "saved_passphrase_key_alias"

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

    /**
     * The Keystore steps behind the saved passphrase. Tests supply software keys, since Robolectric has no
     * AndroidKeyStore and can't refuse a key the way an authentication-bound one is refused.
     */
    internal interface PassphraseKeys {
        /** The key stored under [alias], or null if there is none. */
        fun find(alias: String): SecretKey?
        /** A new authentication-bound key under [alias], replacing any key already there. */
        fun create(alias: String): SecretKey
        fun delete(alias: String)
        /** Where the Keystore refuses an authentication-bound key, with UserNotAuthenticatedException. */
        fun initCipher(cipher: Cipher, mode: Int, key: SecretKey, spec: GCMParameterSpec?)
    }

    private inner class KeystorePassphraseKeys : PassphraseKeys {
        override fun find(alias: String): SecretKey? = getExistingSymmetricKey(alias)
        override fun create(alias: String): SecretKey = newPassphraseKey(alias)
        override fun delete(alias: String) {
            deleteKey(alias)
        }
        override fun initCipher(cipher: Cipher, mode: Int, key: SecretKey, spec: GCMParameterSpec?) {
            if (spec == null) cipher.init(mode, key) else cipher.init(mode, key, spec)
        }
    }

    private var passphraseKeys: PassphraseKeys = KeystorePassphraseKeys()

    /** For tests: the saved-passphrase logic runs on [passphraseKeys] instead of the Android Keystore. */
    internal constructor(context: Context, passphraseKeys: PassphraseKeys) : this(context) {
        this.passphraseKeys = passphraseKeys
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
                val key = generateAesKey(alias, isStrongBox = true)
                Pair(key, getHardwareSecurityLevel(key))
            } catch (e: Exception) {
                throwIfTransientKeystoreFailure(e)
                Log.w(TAG, "StrongBox key provisioning failed (${e.javaClass.simpleName}); executing atomic TEE fallback.", e)
                strongBoxDisabledDueToFailure = true
                deleteKey(alias)
                val key = generateAesKey(alias, isStrongBox = false)
                Pair(key, getHardwareSecurityLevel(key))
            }
        } else {
            Log.i(TAG, "StrongBox hardware not present or disabled; utilizing ARM TrustZone TEE KeyStore for alias: $alias")
            val key = generateAesKey(alias, isStrongBox = false)
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
    private fun newPassphraseKey(alias: String): SecretKey {
        deleteKey(alias)
        // Binding needs a screen lock; without one there is nothing to bind to, so the key is created unbound.
        val bind = (context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isDeviceSecure == true
        if (isStrongBoxSupported()) {
            try {
                return generateAesKey(alias, isStrongBox = true, requireRecentUserAuth = bind)
            } catch (e: Exception) {
                throwIfTransientKeystoreFailure(e)
                deleteKey(alias)
            }
        }
        return generateAesKey(alias, isStrongBox = false, requireRecentUserAuth = bind)
    }

    /** How saving the passphrase went; [NEEDS_AUTHENTICATION] asks the caller to verify the user and try again. */
    enum class PassphraseSave { SAVED, NEEDS_AUTHENTICATION, FAILED }

    /**
     * Encrypts the backup passphrase with a Keystore key (StrongBox when available) that only works while the
     * phone is unlocked and within [PASSPHRASE_AUTH_WINDOW_SECONDS] of the user authenticating. The Keystore
     * refuses the key outside that window, so the caller then shows the system prompt and calls this again.
     *
     * The new key is made under the bound alias not in use, and the old one is deleted only after the new
     * passphrase is stored: a cancelled prompt or a failed write leaves the saved passphrase as it was.
     */
    @Synchronized
    fun savePassphrase(passphrase: String): PassphraseSave {
        val passBytes = passphrase.toByteArray(Charsets.UTF_8)
        var staging: String? = null
        return try {
            val sp = context.getSharedPreferences(SAVED_PASSPHRASE_PREFS, Context.MODE_PRIVATE)
            // No alias stored: anything saved predates the pair and sits under its first key or the original one,
            // so the new key goes under the second and can't take either one's place.
            val current = savedKeyAlias(sp) ?: KEY_ALIAS_SAVED_PASSPHRASE
            val next = if (current == KEY_ALIAS_SAVED_PASSPHRASE) KEY_ALIAS_SAVED_PASSPHRASE_ALT else KEY_ALIAS_SAVED_PASSPHRASE
            staging = next
            val key = passphraseKeys.create(next)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            passphraseKeys.initCipher(cipher, Cipher.ENCRYPT_MODE, key, null)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(passBytes)
            val previous = SavedPassphraseEntries.readFrom(sp)
            val saved = SavedPassphraseEntries(
                Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP),
                next
            )
            if (!saved.writeTo(sp)) {
                // commit() keeps the new values in memory even when they never reached storage. Putting the old
                // ones back keeps this session reading the passphrase the old key still decrypts.
                previous.writeTo(sp)
                passphraseKeys.delete(next)
                Log.e(TAG, "Failed to store passphrase saved to hardware security chip")
                return PassphraseSave.FAILED
            }
            // Only now is the old key unused.
            passphraseKeys.delete(current)
            passphraseKeys.delete(KEY_ALIAS_SAVED_PASSPHRASE_V1)
            PassphraseSave.SAVED
        } catch (e: android.security.keystore.UserNotAuthenticatedException) {
            // Longer than the window since the last unlock or prompt: not a failure, just a prompt away.
            staging?.let(passphraseKeys::delete)
            PassphraseSave.NEEDS_AUTHENTICATION
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save passphrase to hardware security chip", e)
            staging?.let(passphraseKeys::delete)
            PassphraseSave.FAILED
        } finally {
            MemorySanitizer.wipe(passBytes)
        }
    }

    /** Whether a passphrase is saved, without decrypting it. */
    fun hasSavedPassphrase(): Boolean = runCatching {
        context.getSharedPreferences(SAVED_PASSPHRASE_PREFS, Context.MODE_PRIVATE)
            .contains(PREF_PASSPHRASE_CIPHER)
    }.getOrDefault(false)

    /**
     * Decrypts the saved backup passphrase, or returns null if none is saved or the user hasn't authenticated recently.
     */
    @Synchronized
    fun getSavedPassphrase(): String? {
        var decrypted: ByteArray? = null
        return try {
            val sp = context.getSharedPreferences(SAVED_PASSPHRASE_PREFS, Context.MODE_PRIVATE)
            val cipherBase64 = sp.getString(PREF_PASSPHRASE_CIPHER, null) ?: return null
            val ivBase64 = sp.getString(PREF_PASSPHRASE_IV, null) ?: return null
            val cipherBytes = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val alias = savedKeyAlias(sp)
            val boundKey = passphraseKeys.find(alias ?: KEY_ALIAS_SAVED_PASSPHRASE)
            // Only a passphrase stored without an alias can be under the original key: a key left behind by an
            // unfinished save is never the one named, so it can't stand in for that one.
            val originalKey = if (boundKey == null && alias == null) passphraseKeys.find(KEY_ALIAS_SAVED_PASSPHRASE_V1) else null
            val key = boundKey ?: originalKey ?: return null
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            passphraseKeys.initCipher(cipher, Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            decrypted = cipher.doFinal(cipherBytes)
            val passphrase = String(decrypted, Charsets.UTF_8)
            // One-time move of a passphrase saved under the old unbound key onto an authentication-bound one; a
            // successful save deletes the old key.
            if (originalKey != null) savePassphrase(passphrase)
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
     * Clears any saved passphrase from the hardware security chip and storage. Returns false if storage
     * couldn't be updated; the keys are deleted either way, so what is left can't be decrypted.
     */
    @Synchronized
    fun clearSavedPassphrase(): Boolean {
        return try {
            val sp = context.getSharedPreferences(SAVED_PASSPHRASE_PREFS, Context.MODE_PRIVATE)
            val cleared = SavedPassphraseEntries(null, null, null).writeTo(sp)
            passphraseKeys.delete(KEY_ALIAS_SAVED_PASSPHRASE)
            passphraseKeys.delete(KEY_ALIAS_SAVED_PASSPHRASE_ALT)
            passphraseKeys.delete(KEY_ALIAS_SAVED_PASSPHRASE_V1)
            cleared
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear saved passphrase", e)
            false
        }
    }

    /** The bound alias the stored passphrase names, or null if it names none of the pair. */
    private fun savedKeyAlias(sp: SharedPreferences): String? =
        sp.getString(PREF_PASSPHRASE_KEY_ALIAS, null)
            ?.takeIf { it == KEY_ALIAS_SAVED_PASSPHRASE || it == KEY_ALIAS_SAVED_PASSPHRASE_ALT }

    /** The stored passphrase, its IV and the alias of the key that encrypted it; null means absent. */
    private class SavedPassphraseEntries(val cipher: String?, val iv: String?, val keyAlias: String?) {

        /** Writes all three in one commit and returns whether they reached storage. */
        fun writeTo(sp: SharedPreferences): Boolean {
            val editor = sp.edit()
            for ((name, value) in listOf(PREF_PASSPHRASE_CIPHER to cipher, PREF_PASSPHRASE_IV to iv, PREF_PASSPHRASE_KEY_ALIAS to keyAlias)) {
                if (value == null) editor.remove(name) else editor.putString(name, value)
            }
            return editor.commit()
        }

        companion object {
            fun readFrom(sp: SharedPreferences) = SavedPassphraseEntries(
                sp.getString(PREF_PASSPHRASE_CIPHER, null),
                sp.getString(PREF_PASSPHRASE_IV, null),
                sp.getString(PREF_PASSPHRASE_KEY_ALIAS, null)
            )
        }
    }
}
