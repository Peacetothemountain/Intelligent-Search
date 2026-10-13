package com.pixel.intelligentsearch.core.backup

import com.pixel.intelligentsearch.core.security.MemorySanitizer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCryptoEngine {

    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    const val LEGACY_PBKDF2_ITERATIONS = 65536
    const val PBKDF2_ITERATIONS = 600_000
    private const val MIN_ACCEPTED_ITERATIONS = 10_000
    private const val MAX_ACCEPTED_ITERATIONS = 5_000_000
    private const val PBKDF2_KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    private val PORTABLE_DEFAULT_SALT = byteArrayOf(
        0x50.toByte(), 0x69.toByte(), 0x78.toByte(), 0x65.toByte(),
        0x6C.toByte(), 0x49.toByte(), 0x6E.toByte(), 0x74.toByte(),
        0x65.toByte(), 0x6C.toByte(), 0x6C.toByte(), 0x69.toByte(),
        0x67.toByte(), 0x65.toByte(), 0x6E.toByte(), 0x74.toByte()
    )

    fun getPortableDefaultKey(): SecretKey {
        val pass = "PORTABLE_BACKUP_INTELLIGENT_SEARCH_V2".toCharArray()
        return try {
            deriveKeyFromPassphrase(pass, PORTABLE_DEFAULT_SALT, LEGACY_PBKDF2_ITERATIONS)
        } finally {
            MemorySanitizer.wipe(pass)
        }
    }

    fun deriveKeyFromPassphrase(passphrase: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKey {
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) { "Unsupported KDF iteration count" }
        val spec = PBEKeySpec(passphrase, salt, iterations, PBKDF2_KEY_LENGTH_BITS)
        var keyBytes: ByteArray? = null
        try {
            keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            // PBEKeySpec keeps its own copy of the passphrase; wipe it and intermediate keyBytes as soon as the key is derived.
            spec.clearPassword()
            MemorySanitizer.wipe(keyBytes)
        }
    }

    fun generateRandomSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun generateRandomIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    fun encryptPayload(
        plainTextJson: String,
        secretKey: SecretKey,
        iv: ByteArray,
        aad: ByteArray? = null
    ): ByteArray {
        require(iv.size == GCM_IV_LENGTH_BYTES) { "Invalid GCM IV length: ${iv.size}" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(plainTextJson.toByteArray(Charsets.UTF_8))
    }

    /**
     * Encrypts with a cipher-generated IV, as required for Android Keystore keys created with
     * randomized encryption. Returns the IV and the ciphertext.
     */
    fun encryptPayloadWithGeneratedIv(
        plainTextJson: String,
        secretKey: SecretKey,
        aad: ByteArray? = null
    ): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        if (aad != null) cipher.updateAAD(aad)
        val cipherBytes = cipher.doFinal(plainTextJson.toByteArray(Charsets.UTF_8))
        return cipher.iv to cipherBytes
    }

    fun decryptPayload(
        cipherBytes: ByteArray,
        secretKey: SecretKey,
        iv: ByteArray,
        aad: ByteArray? = null
    ): String {
        require(iv.size == GCM_IV_LENGTH_BYTES) { "Invalid GCM IV length: ${iv.size}" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        if (aad != null) cipher.updateAAD(aad)
        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /** Standard alphabet without line breaks, byte-identical to android.util.Base64.NO_WRAP. */
    fun encodeBase64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    /** The MIME decoder tolerates line breaks or stray whitespace a text editor may add to a backup file. */
    fun decodeBase64(str: String): ByteArray = java.util.Base64.getMimeDecoder().decode(str)
}
