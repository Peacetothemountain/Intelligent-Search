package com.pixel.intelligentsearch.core.backup

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * One way to open a key-slot backup (schema 2). The backup's settings are encrypted once with a random content key, and
 * each slot holds that key wrapped (AES-256-GCM) by one unlock method, so a single file opens with whichever the user
 * has: their passkey, or their passphrase. Adding a way to open a backup never re-encrypts its settings.
 */
@Immutable
@Serializable
data class KeySlot(
    /** [TYPE_PASSKEY] or [TYPE_PASSPHRASE]. */
    val type: String,
    val wrapIvBase64: String,
    val wrappedKeyBase64: String,
    /** A passkey slot's credential, so the system offers only that passkey on restore. Base64url, no padding. */
    val credentialId: String? = null,
    /** The website the passkey belongs to ([BackupPasskey.RP_ID]). */
    val rpId: String? = null,
    /** What the passkey's PRF is evaluated on; the same for every backup made with one passkey. */
    val prfSaltBase64: String? = null,
    /** A passphrase slot's key derivation. */
    val kdf: KdfMetadata? = null
) {
    companion object {
        const val TYPE_PASSKEY = "passkey"
        const val TYPE_PASSPHRASE = "passphrase"
    }
}

/** The key-slot scheme: content keys, wrapping them for each slot, and the passkey's wrapping key. */
object BackupKeySlots {

    /** Envelope schema that carries key slots instead of a single key. Older app versions can't open it. */
    const val SCHEMA_VERSION = 2
    private const val KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val PASSKEY_HKDF_INFO = "Intelligent Search backup passkey key v1"

    private val random = SecureRandom()

    /** A fresh random AES-256 key for one backup's settings. */
    fun newContentKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    /** Wraps [contentKey] with [kek] as a slot of [type]; the slot type is authenticated with it, so slots can't be swapped. */
    fun wrap(contentKey: ByteArray, kek: SecretKey, type: String): Pair<ByteArray, ByteArray> {
        val iv = BackupCryptoEngine.generateRandomIv()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(type))
        return iv to cipher.doFinal(contentKey)
    }

    /** The content key from [slot], or an [javax.crypto.AEADBadTagException] when [kek] isn't the slot's key. */
    fun unwrap(slot: KeySlot, kek: SecretKey): ByteArray {
        val iv = BackupCryptoEngine.decodeBase64(slot.wrapIvBase64)
        require(iv.size == 12) { "Invalid key slot IV length: ${iv.size}" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(slot.type))
        val key = cipher.doFinal(BackupCryptoEngine.decodeBase64(slot.wrappedKeyBase64))
        require(key.size == KEY_BYTES) { "Invalid content key length" }
        return key
    }

    /**
     * The key a passkey's PRF output ([prfOutput], 32 bytes from the authenticator) wraps backups with: HKDF-SHA256, so
     * the raw PRF output is never used as a key itself.
     */
    fun passkeyKek(prfOutput: ByteArray): SecretKey {
        require(prfOutput.size >= KEY_BYTES) { "PRF output too short" }
        val okm = hkdfSha256(ikm = prfOutput, salt = ByteArray(0), info = PASSKEY_HKDF_INFO.toByteArray(Charsets.UTF_8), length = KEY_BYTES)
        return SecretKeySpec(okm, "AES")
    }

    /** HKDF (RFC 5869) with HMAC-SHA256. */
    internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val take = minOf(previous.size, length - written)
            System.arraycopy(previous, 0, out, written, take)
            written += take
            counter++
        }
        prk.fill(0)
        return out
    }

    private fun aad(type: String): ByteArray = "INTELLIGENT_SEARCH_KEY_SLOT|$type".toByteArray(Charsets.UTF_8)
}
