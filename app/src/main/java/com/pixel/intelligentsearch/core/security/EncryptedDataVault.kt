package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted vault record structure encapsulating binary envelope metadata,
 * random 96-bit nonce, GCM ciphertext, authentication tag, and blind index.
 */
data class EncryptedVaultRecord(
    val recordId: String,
    val domain: String,
    val payload: ByteArray,
    val blindIndex: String? = null,
    val securityLevel: HardwareSecurityLevel = HardwareSecurityLevel.STRONGBOX,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedVaultRecord
        return recordId == other.recordId &&
                domain == other.domain &&
                payload.contentEquals(other.payload) &&
                blindIndex == other.blindIndex &&
                securityLevel == other.securityLevel &&
                timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = recordId.hashCode()
        result = 31 * result + domain.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (blindIndex?.hashCode() ?: 0)
        result = 31 * result + securityLevel.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }

    fun toBase64String(): String {
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    companion object {
        fun fromBase64(
            recordId: String,
            domain: String,
            base64Payload: String,
            blindIndex: String? = null,
            securityLevel: HardwareSecurityLevel = HardwareSecurityLevel.STRONGBOX
        ): EncryptedVaultRecord {
            val payload = Base64.decode(base64Payload, Base64.NO_WRAP)
            return EncryptedVaultRecord(
                recordId = recordId,
                domain = domain,
                payload = payload,
                blindIndex = blindIndex,
                securityLevel = securityLevel
            )
        }
    }
}

/**
 * End-to-End Encrypted Data Vault implementing AES-256-GCM envelope encryption
 * with per-record random nonces, Associated Authenticated Data (AAD) binding,
 * blind indexing, and zero-memory-leak memory wiping.
 *
 * Engineered by NG Designs.
 */
@Singleton
class EncryptedDataVault @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val securityManager: StrongBoxSecurityManager
) {
    constructor(context: Context) : this(context, StrongBoxSecurityManager(context))

    companion object {
        private const val TAG = "EncryptedDataVault"
        private const val VAULT_MAGIC_BYTE: Byte = 0x53 // 'S' for StrongBox Vault
        private const val VAULT_VERSION_BYTE: Byte = 0x01
        private const val HEADER_LENGTH = 3 // Magic + Version + SecurityLevel
        private const val IV_LENGTH = StrongBoxSecurityManager.GCM_IV_LENGTH // 12 bytes
        private const val TAG_LENGTH_BITS = StrongBoxSecurityManager.GCM_TAG_LENGTH // 128 bits
    }

    /**
     * Constructs cryptographic Associated Authenticated Data (AAD) mathematically
     * binding the record ID, domain, user profile, and package identifier to
     * prevent record substitution or cross-domain ciphertext injection.
     */
    private fun buildAad(domain: String, recordId: String, profileId: String): ByteArray {
        val appPackage = context.packageName
        return "$appPackage:$domain:$recordId:$profileId:v1".toByteArray(Charsets.UTF_8)
    }

    /**
     * Assembles the binary vault envelope: magic, version, security level, 96-bit IV, ciphertext + tag.
     */
    private fun buildEnvelope(level: HardwareSecurityLevel, iv: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        require(iv.size == IV_LENGTH) { "Unexpected GCM IV length: ${iv.size}" }
        val levelByte: Byte = when (level) {
            HardwareSecurityLevel.STRONGBOX -> 0x01
            HardwareSecurityLevel.TEE -> 0x02
            HardwareSecurityLevel.SOFTWARE -> 0x03
        }
        return ByteBuffer.allocate(HEADER_LENGTH + IV_LENGTH + ciphertextWithTag.size)
            .put(VAULT_MAGIC_BYTE)
            .put(VAULT_VERSION_BYTE)
            .put(levelByte)
            .put(iv)
            .put(ciphertextWithTag)
            .array()
    }

    /**
     * Validates length, magic byte, and version of an envelope before any cryptographic operation.
     */
    private fun requireValidEnvelope(payload: ByteArray) {
        require(payload.size > HEADER_LENGTH + IV_LENGTH) { "Vault payload is truncated or corrupted." }
        require(payload[0] == VAULT_MAGIC_BYTE) { "Invalid vault record magic byte: ${payload[0]}" }
        require(payload[1] == VAULT_VERSION_BYTE) { "Unsupported vault record version: ${payload[1]}" }
    }

    /**
     * Encrypts a raw byte array into an [EncryptedVaultRecord] using the master StrongBox key.
     */
    fun encrypt(
        domain: String,
        recordId: String,
        plaintext: ByteArray,
        profileId: String = "default",
        generateBlindIndexFor: String? = null
    ): EncryptedVaultRecord {
        val (secretKey, level) = securityManager.getOrCreateSymmetricKey(StrongBoxSecurityManager.KEY_ALIAS_MASTER)
        val cipher = Cipher.getInstance(StrongBoxSecurityManager.CIPHER_ALGORITHM)

        // AndroidKeyStore generates the random IV internally when initialized with a KeyStore key.
        // The IV must come from the cipher itself: an externally generated IV would never be applied
        // to the operation and would yield a record that can no longer be decrypted.
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = checkNotNull(cipher.iv) { "Keystore cipher did not expose its GCM IV." }

        // Bind AAD
        cipher.updateAAD(buildAad(domain, recordId, profileId))
        val ciphertextWithTag = cipher.doFinal(plaintext)

        val blindIndex = generateBlindIndexFor?.let { securityManager.computeBlindIndex(it) }

        return EncryptedVaultRecord(
            recordId = recordId,
            domain = domain,
            payload = buildEnvelope(level, iv, ciphertextWithTag),
            blindIndex = blindIndex,
            securityLevel = level
        )
    }

    /**
     * Encrypts a raw byte array using an authenticated [Cipher] bound to a biometric session.
     */
    fun encryptWithBiometricCipher(
        cipher: Cipher,
        domain: String,
        recordId: String,
        plaintext: ByteArray,
        profileId: String = "default",
        generateBlindIndexFor: String? = null
    ): EncryptedVaultRecord {
        val iv = cipher.iv ?: throw IllegalStateException("Biometric cipher must have an initialized initialization vector (IV).")
        cipher.updateAAD(buildAad(domain, recordId, profileId))
        val ciphertextWithTag = cipher.doFinal(plaintext)
        val level = securityManager.getSecurityLevelForAlias(StrongBoxSecurityManager.KEY_ALIAS_BIOMETRIC_GATE)

        val blindIndex = generateBlindIndexFor?.let { securityManager.computeBlindIndex(it) }
        return EncryptedVaultRecord(
            recordId = recordId,
            domain = domain,
            payload = buildEnvelope(level, iv, ciphertextWithTag),
            blindIndex = blindIndex,
            securityLevel = level
        )
    }

    /**
     * Encrypts a [SecureCharArray] into an [EncryptedVaultRecord] and zeroizes the intermediate UTF-8
     * copies. The caller keeps ownership of [chars] and remains responsible for closing it; the
     * array is deliberately not wiped here so it stays usable after the call.
     */
    fun encryptChars(
        domain: String,
        recordId: String,
        chars: SecureCharArray,
        profileId: String = "default"
    ): EncryptedVaultRecord {
        val charBuffer = CharBuffer.wrap(chars.data)
        val byteBuffer = Charsets.UTF_8.encode(charBuffer)
        val tempBytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(tempBytes)
        try {
            return encrypt(domain, recordId, tempBytes, profileId)
        } finally {
            MemorySanitizer.wipe(tempBytes)
            MemorySanitizer.wipe(byteBuffer)
        }
    }

    /**
     * Decrypts an [EncryptedVaultRecord] into a scoped [SecureByteArray].
     * Throws [GeneralSecurityException] if ciphertext has been modified or AAD mismatches.
     */
    fun decrypt(
        record: EncryptedVaultRecord,
        profileId: String = "default"
    ): SecureByteArray {
        val payload = record.payload
        requireValidEnvelope(payload)

        val byteBuffer = ByteBuffer.wrap(payload)
        byteBuffer.position(HEADER_LENGTH)

        val iv = ByteArray(IV_LENGTH)
        byteBuffer.get(iv)

        val ciphertextWithTag = ByteArray(byteBuffer.remaining())
        byteBuffer.get(ciphertextWithTag)

        val (secretKey, _) = securityManager.getOrCreateSymmetricKey(StrongBoxSecurityManager.KEY_ALIAS_MASTER)
        val cipher = Cipher.getInstance(StrongBoxSecurityManager.CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val aad = buildAad(record.domain, record.recordId, profileId)
        cipher.updateAAD(aad)

        val decryptedRaw = cipher.doFinal(ciphertextWithTag)
        val secureBuffer = SecureByteArray(decryptedRaw)
        MemorySanitizer.wipe(decryptedRaw)
        return secureBuffer
    }

    /**
     * Decrypts a vault record directly into a String.
     * Note: For military-grade zero-memory persistence, prefer [decrypt] with [SecureByteArray].
     */
    fun decryptToString(
        record: EncryptedVaultRecord,
        profileId: String = "default"
    ): String? {
        return try {
            decrypt(record, profileId).use { secureBytes ->
                String(secureBytes.data, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed for record ${record.recordId} in domain ${record.domain}: ${e.message}")
            null
        }
    }

    /**
     * Extracts the IV from an [EncryptedVaultRecord] payload for Biometric CryptoObject preparation.
     */
    fun extractIv(payload: ByteArray): ByteArray {
        require(payload.size >= HEADER_LENGTH + IV_LENGTH) { "Payload too short to contain IV." }
        val iv = ByteArray(IV_LENGTH)
        System.arraycopy(payload, HEADER_LENGTH, iv, 0, IV_LENGTH)
        return iv
    }

    /**
     * Decrypts a biometric-locked vault record using the authenticated [Cipher]
     * returned by [android.hardware.biometrics.BiometricPrompt.AuthenticationResult.cryptoObject].
     */
    fun decryptWithBiometricCipher(
        cipher: Cipher,
        record: EncryptedVaultRecord,
        profileId: String = "default"
    ): SecureByteArray {
        val payload = record.payload
        requireValidEnvelope(payload)

        val byteBuffer = ByteBuffer.wrap(payload)
        byteBuffer.position(HEADER_LENGTH + IV_LENGTH)
        val ciphertextWithTag = ByteArray(byteBuffer.remaining())
        byteBuffer.get(ciphertextWithTag)

        val aad = buildAad(record.domain, record.recordId, profileId)
        cipher.updateAAD(aad)

        val decryptedRaw = cipher.doFinal(ciphertextWithTag)
        val secureBuffer = SecureByteArray(decryptedRaw)
        MemorySanitizer.wipe(decryptedRaw)
        return secureBuffer
    }
}
