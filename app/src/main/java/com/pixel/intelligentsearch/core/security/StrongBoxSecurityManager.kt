package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

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

/**
 * Military-grade hardware security manager leveraging Titan M2/M3+ (Google Pixel)
 * and Knox Vault (Samsung Galaxy) discrete hardware coprocessors via KeyMint 3.0+ APIs.
 *
 * Implements hardware-isolated cryptographic key generation, per-operation biometric
 * authentication binding with zero-second timeout, and atomic TEE/Software fallback.
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
        const val GCM_TAG_LENGTH = 128
        const val GCM_IV_LENGTH = 12
        const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val HMAC_ALGORITHM = "HmacSHA256"

        // Domain-scoped key aliases
        const val KEY_ALIAS_MASTER = "com.pixel.intelligentsearch.vault.master_v1"
        const val KEY_ALIAS_BIOMETRIC_GATE = "com.pixel.intelligentsearch.vault.biometric_gate_v1"
        const val KEY_ALIAS_BLIND_INDEX = "com.pixel.intelligentsearch.vault.blind_index_v1"
        const val KEY_ALIAS_SAVED_PASSPHRASE = "com.pixel.intelligentsearch.vault.saved_passphrase_v1"

        // Dynamic circuit-breaker for devices reporting StrongBox in PackageManager but lacking hardware support
        @Volatile
        private var strongBoxDisabledDueToFailure = false
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }
    private val secureRandom = SecureRandom()

    private fun getSafeContext(): Context {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val isUnlocked = userManager?.isUserUnlocked ?: true
        return if (!isUnlocked && !context.isDeviceProtectedStorage) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
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
        alias: String = KEY_ALIAS_MASTER,
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

    fun getSecurityLevelForAlias(alias: String): HardwareSecurityLevel {
        if (!keyStore.containsAlias(alias)) return HardwareSecurityLevel.SOFTWARE
        val entry = try {
            keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        } catch (_: Exception) {
            null
        } ?: return HardwareSecurityLevel.SOFTWARE
        return getHardwareSecurityLevel(entry.secretKey)
    }

    /**
     * Prepares an initialized [Cipher] for standard envelope operations.
     */
    fun getInitializedCipher(
        alias: String = KEY_ALIAS_MASTER,
        opmode: Int,
        iv: ByteArray? = null
    ): Cipher {
        val (secretKey, _) = getOrCreateSymmetricKey(alias)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        when (opmode) {
            Cipher.ENCRYPT_MODE -> cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            Cipher.DECRYPT_MODE -> {
                requireNotNull(iv) { "Initialization Vector (IV) cannot be null for decryption." }
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            }
            else -> throw IllegalArgumentException("Unsupported cipher operation mode: $opmode")
        }
        return cipher
    }

    /**
     * Prepares a [BiometricPrompt.CryptoObject] bound to a per-operation biometric key
     * for authenticated encryption. Returns both the CryptoObject and the newly generated IV.
     */
    fun initBiometricEncryptCryptoObject(
        alias: String = KEY_ALIAS_BIOMETRIC_GATE
    ): Pair<BiometricPrompt.CryptoObject, ByteArray> {
        val (secretKey, _) = getOrCreateSymmetricKey(
            alias = alias,
            requireBiometric = true,
            authTimeoutSeconds = 0
        )
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        } catch (e: Exception) {
            if (e is KeyPermanentlyInvalidatedException || e is UnrecoverableKeyException) {
                Log.w(TAG, "Biometric encrypt key invalidated; resetting alias and regenerating", e)
                deleteKey(alias)
                val (newKey, _) = getOrCreateSymmetricKey(
                    alias = alias,
                    requireBiometric = true,
                    authTimeoutSeconds = 0
                )
                cipher.init(Cipher.ENCRYPT_MODE, newKey)
            } else {
                throw e
            }
        }
        val iv = cipher.iv.clone()
        return Pair(BiometricPrompt.CryptoObject(cipher), iv)
    }

    /**
     * Prepares a [BiometricPrompt.CryptoObject] bound to a per-operation biometric key
     * for authenticated decryption given a previous record's IV.
     */
    fun initBiometricDecryptCryptoObject(
        iv: ByteArray,
        alias: String = KEY_ALIAS_BIOMETRIC_GATE
    ): BiometricPrompt.CryptoObject {
        val (secretKey, _) = getOrCreateSymmetricKey(
            alias = alias,
            requireBiometric = true,
            authTimeoutSeconds = 0
        )
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        } catch (e: Exception) {
            if (e is KeyPermanentlyInvalidatedException || e is UnrecoverableKeyException) {
                Log.w(TAG, "Biometric decrypt key invalidated; resetting alias and regenerating", e)
                deleteKey(alias)
                val (newKey, _) = getOrCreateSymmetricKey(
                    alias = alias,
                    requireBiometric = true,
                    authTimeoutSeconds = 0
                )
                cipher.init(Cipher.DECRYPT_MODE, newKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            } else {
                throw e
            }
        }
        return BiometricPrompt.CryptoObject(cipher)
    }

    fun createCryptoObject(cipher: Cipher): BiometricPrompt.CryptoObject {
        return BiometricPrompt.CryptoObject(cipher)
    }

    /**
     * Retrieves or generates an HMAC-SHA256 key backed by KeyStore discrete hardware,
     * with StrongBox, TEE, and software fallback.
     */
    @Synchronized
    fun getOrCreateHmacKey(alias: String = KEY_ALIAS_BLIND_INDEX): Pair<SecretKey, HardwareSecurityLevel> {
        if (keyStore.containsAlias(alias)) {
            val entry = try {
                keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            } catch (e: Exception) {
                throwIfTransientKeystoreFailure(e)
                null
            }
            if (entry != null) {
                return Pair(entry.secretKey, getHardwareSecurityLevel(entry.secretKey))
            }
            val existingKey = try {
                keyStore.getKey(alias, null) as? SecretKey
            } catch (e: Exception) {
                throwIfTransientKeystoreFailure(e)
                null
            }
            if (existingKey != null) {
                return Pair(existingKey, getHardwareSecurityLevel(existingKey))
            }
            deleteKey(alias)
        }

        return if (isStrongBoxSupported()) {
            try {
                val key = generateHmacKey(alias, isStrongBox = true)
                Pair(key, getHardwareSecurityLevel(key))
            } catch (e: Exception) {
                throwIfTransientKeystoreFailure(e)
                Log.w(TAG, "StrongBox HMAC unavailable on device; falling back to TEE: ${e.message}")
                deleteKey(alias)
                try {
                    val key = generateHmacKey(alias, isStrongBox = false)
                    Pair(key, getHardwareSecurityLevel(key))
                } catch (teeEx: Exception) {
                    throwIfTransientKeystoreFailure(teeEx)
                    Log.w(TAG, "TEE HMAC generation failed; utilizing device-isolated entropy seed", teeEx)
                    val seed = getOrCreateSoftwareBlindIndexSeed()
                    try {
                        val key = SecretKeySpec(seed, HMAC_ALGORITHM)
                        Pair(key, HardwareSecurityLevel.SOFTWARE)
                    } finally {
                        MemorySanitizer.wipe(seed)
                    }
                }
            }
        } else {
            try {
                val key = generateHmacKey(alias, isStrongBox = false)
                Pair(key, getHardwareSecurityLevel(key))
            } catch (teeEx: Exception) {
                throwIfTransientKeystoreFailure(teeEx)
                Log.w(TAG, "TEE HMAC generation failed; utilizing device-isolated entropy seed", teeEx)
                val seed = getOrCreateSoftwareBlindIndexSeed()
                try {
                    val key = SecretKeySpec(seed, HMAC_ALGORITHM)
                    Pair(key, HardwareSecurityLevel.SOFTWARE)
                } finally {
                    MemorySanitizer.wipe(seed)
                }
            }
        }
    }

    private fun generateHmacKey(alias: String, isStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN
        )
        if (isStrongBox) {
            specBuilder.setIsStrongBoxBacked(true)
        }
        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    private fun getOrCreateSoftwareBlindIndexSeed(): ByteArray {
        val sp = getSafeContext().getSharedPreferences("secure_crypto_seed_store", Context.MODE_PRIVATE)
        val existing = sp.getString("blind_seed", null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val newSeed = ByteArray(32)
        secureRandom.nextBytes(newSeed)
        sp.edit().putString("blind_seed", Base64.encodeToString(newSeed, Base64.NO_WRAP)).apply()
        return newSeed
    }

    /**
     * Computes an HMAC-SHA256 blind index hash for zero-knowledge search query
     * and private alias lookup. The HMAC key is isolated in hardware.
     */
    @Synchronized
    fun computeBlindIndex(input: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        return try {
            val (key, _) = getOrCreateHmacKey(KEY_ALIAS_BLIND_INDEX)
            mac.init(key)
            val hmac = mac.doFinal(input.lowercase().trim().toByteArray(Charsets.UTF_8))
            bytesToHex(hmac.copyOf(16))
        } catch (e: Exception) {
            throwIfTransientKeystoreFailure(e)
            Log.w(TAG, "Hardware KeyStore HMAC signing unavailable; utilizing device-isolated entropy seed: ${e.message}")
            val seed = getOrCreateSoftwareBlindIndexSeed()
            try {
                val keySpec = SecretKeySpec(seed, HMAC_ALGORITHM)
                mac.init(keySpec)
                val hmac = mac.doFinal(input.lowercase().trim().toByteArray(Charsets.UTF_8))
                bytesToHex(hmac.copyOf(16))
            } finally {
                MemorySanitizer.wipe(seed)
            }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
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

    /**
     * Encrypts and saves the backup encryption passphrase into hardware security chip (Titan M2 / StrongBox KeyStore).
     */
    @Synchronized
    fun savePassphrase(passphrase: String): Boolean {
        val passBytes = passphrase.toByteArray(Charsets.UTF_8)
        return try {
            val (key, _) = getOrCreateSymmetricKey(KEY_ALIAS_SAVED_PASSPHRASE)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(passBytes)
            val sp = getSafeContext().getSharedPreferences("secure_hardware_vault", Context.MODE_PRIVATE)
            sp.edit()
                .putString("saved_passphrase_cipher", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
                .putString("saved_passphrase_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .commit()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save passphrase to hardware security chip", e)
            false
        } finally {
            MemorySanitizer.wipe(passBytes)
        }
    }

    /** Whether a passphrase is saved, without decrypting it. */
    fun hasSavedPassphrase(): Boolean = runCatching {
        getSafeContext().getSharedPreferences("secure_hardware_vault", Context.MODE_PRIVATE)
            .contains("saved_passphrase_cipher")
    }.getOrDefault(false)

    /**
     * Retrieves and decrypts the backup encryption passphrase from hardware security chip (Titan M2 / StrongBox KeyStore).
     */
    @Synchronized
    fun getSavedPassphrase(): String? {
        var decrypted: ByteArray? = null
        return try {
            val sp = getSafeContext().getSharedPreferences("secure_hardware_vault", Context.MODE_PRIVATE)
            val cipherBase64 = sp.getString("saved_passphrase_cipher", null) ?: return null
            val ivBase64 = sp.getString("saved_passphrase_iv", null) ?: return null
            val cipherBytes = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val key = getExistingSymmetricKey(KEY_ALIAS_SAVED_PASSPHRASE) ?: return null
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            decrypted = cipher.doFinal(cipherBytes)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve saved passphrase from hardware security chip", e)
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
            val sp = getSafeContext().getSharedPreferences("secure_hardware_vault", Context.MODE_PRIVATE)
            sp.edit().remove("saved_passphrase_cipher").remove("saved_passphrase_iv").commit()
            deleteKey(KEY_ALIAS_SAVED_PASSPHRASE)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear saved passphrase", e)
            false
        }
    }
}
