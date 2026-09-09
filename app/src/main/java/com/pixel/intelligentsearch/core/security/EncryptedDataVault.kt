package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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

    private val secureRandom = SecureRandom()

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
        
        // Generate cryptographic 96-bit random nonce
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        // Bind AAD
        val aad = buildAad(domain, recordId, profileId)
        cipher.updateAAD(aad)

        val ciphertextWithTag = cipher.doFinal(plaintext)

        // Assemble binary vault envelope
        val levelByte: Byte = when (level) {
            HardwareSecurityLevel.STRONGBOX -> 0x01
            HardwareSecurityLevel.TEE -> 0x02
            HardwareSecurityLevel.SOFTWARE -> 0x03
        }

        val envelopeBuffer = ByteBuffer.allocate(HEADER_LENGTH + IV_LENGTH + ciphertextWithTag.size)
        envelopeBuffer.put(VAULT_MAGIC_BYTE)
        envelopeBuffer.put(VAULT_VERSION_BYTE)
        envelopeBuffer.put(levelByte)
        envelopeBuffer.put(iv)
        envelopeBuffer.put(ciphertextWithTag)

        val blindIndex = generateBlindIndexFor?.let { securityManager.computeBlindIndex(it) }

        return EncryptedVaultRecord(
            recordId = recordId,
            domain = domain,
            payload = envelopeBuffer.array(),
            blindIndex = blindIndex,
            securityLevel = level
        )
    }

    /**
     * Encrypts a [SecureCharArray] into an [EncryptedVaultRecord] and zeroizes intermediate memory.
     */
    fun encryptChars(
        domain: String,
        recordId: String,
        chars: SecureCharArray,
        profileId: String = "default"
    ): EncryptedVaultRecord {
        val charBuffer = java.nio.CharBuffer.wrap(chars.data)
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
        require(payload.size > HEADER_LENGTH + IV_LENGTH) { "Vault payload is truncated or corrupted." }

        val byteBuffer = ByteBuffer.wrap(payload)
        val magic = byteBuffer.get()
        val version = byteBuffer.get()
        val levelByte = byteBuffer.get()

        require(magic == VAULT_MAGIC_BYTE) { "Invalid vault record magic byte: $magic" }
        require(version == VAULT_VERSION_BYTE) { "Unsupported vault record version: $version" }

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
            Log.e(TAG, "Decryption failed for record ${record.recordId} in domain ${record.domain}", e)
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
     * returned by [BiometricPrompt.AuthenticationResult.cryptoObject].
     */
    fun decryptWithBiometricCipher(
        cipher: Cipher,
        record: EncryptedVaultRecord,
        profileId: String = "default"
    ): SecureByteArray {
        val payload = record.payload
        require(payload.size > HEADER_LENGTH + IV_LENGTH) { "Payload too short." }

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
