package com.ngdesigns.intelligentsearch.data.backup

import com.ngdesigns.intelligentsearch.data.settings.Settings
import com.ngdesigns.intelligentsearch.data.settings.SettingsJson
import com.ngdesigns.intelligentsearch.data.settings.forBackup
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** What a backup holds: the settings (minus what is tied to this phone) and, optionally, recent searches. */
@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    val createdAtMillis: Long,
    val appVersion: String,
    val settings: JsonElement,
    val history: List<String> = emptyList(),
) {
    companion object {
        const val FORMAT = "intelligent-search-2"

        fun of(settings: Settings, history: List<String>, createdAtMillis: Long, appVersion: String) = BackupDocument(
            createdAtMillis = createdAtMillis,
            appVersion = appVersion,
            settings = SettingsJson.format.encodeToJsonElement(Settings.serializer(), settings.forBackup()),
            history = history.take(MAX_HISTORY),
        )

        const val MAX_HISTORY = 500
    }

    /** The backed-up settings, sanitized, or null when the document doesn't hold readable settings. */
    fun readSettings(): Settings? = SettingsJson.decode(settings.toString())
}

/** Why a backup could not be opened, for a clear message rather than a crash. */
sealed class BackupError(message: String) : Exception(message) {
    class NotABackup : BackupError("This file isn't an Intelligent Search backup.")
    class WrongPassphraseOrDamaged : BackupError("The passphrase is wrong, or the file is damaged.")
    class NeedsPassphrase : BackupError("This backup is protected with a passphrase.")
    class OtherDevice : BackupError("This backup was made without a passphrase on another phone, so only that phone can open it.")
}

/**
 * Encrypts backups. With a passphrase the key comes from PBKDF2-HMAC-SHA256 (a random salt, 310,000 rounds), so
 * the file restores on any phone that knows the passphrase. Without one, [deviceKey] (kept in the phone's secure
 * hardware) seals it, so it restores only here. Either way the contents are AES-256-GCM, which also detects any
 * tampering.
 *
 * File layout: magic "ISB2", mode (1 passphrase, 2 device), then for mode 1 the 16-byte salt and the round count,
 * then the 12-byte IV and the ciphertext.
 */
class BackupCodec(private val deviceKey: () -> SecretKey?, private val random: SecureRandom = SecureRandom()) {

    fun seal(document: BackupDocument, passphrase: CharArray?): ByteArray {
        val plain = SettingsJson.format.encodeToString(BackupDocument.serializer(), document).encodeToByteArray()
        return if (passphrase != null && passphrase.isNotEmpty()) {
            val salt = ByteArray(16).also(random::nextBytes)
            val key = derive(passphrase, salt, ROUNDS)
            val (iv, cipher) = encrypt(key, plain)
            ByteBuffer.allocate(4 + 1 + 16 + 4 + 12 + cipher.size)
                .put(MAGIC).put(MODE_PASSPHRASE).put(salt).putInt(ROUNDS).put(iv).put(cipher).array()
        } else {
            val key = deviceKey() ?: throw GeneralSecurityException("No device key")
            val (iv, cipher) = encrypt(key, plain)
            ByteBuffer.allocate(4 + 1 + 12 + cipher.size).put(MAGIC).put(MODE_DEVICE).put(iv).put(cipher).array()
        }
    }

    /** Whether [bytes] needs a passphrase to open (or null when it isn't a backup at all). */
    fun needsPassphrase(bytes: ByteArray): Boolean? = when {
        bytes.size < 5 || !bytes.copyOf(4).contentEquals(MAGIC) -> null
        else -> bytes[4] == MODE_PASSPHRASE
    }

    fun open(bytes: ByteArray, passphrase: CharArray?): BackupDocument {
        val mode = needsPassphrase(bytes) ?: throw BackupError.NotABackup()
        val buf = ByteBuffer.wrap(bytes, 5, bytes.size - 5)
        val plain = try {
            if (mode) {
                if (passphrase == null || passphrase.isEmpty()) throw BackupError.NeedsPassphrase()
                if (buf.remaining() < 16 + 4 + 12 + 16) throw BackupError.NotABackup()
                val salt = ByteArray(16).also { buf.get(it) }
                val rounds = buf.int
                if (rounds !in 100_000..5_000_000) throw BackupError.NotABackup()
                decrypt(derive(passphrase, salt, rounds), buf)
            } else {
                if (buf.remaining() < 12 + 16) throw BackupError.NotABackup()
                val key = deviceKey() ?: throw BackupError.OtherDevice()
                try { decrypt(key, buf) } catch (_: GeneralSecurityException) { throw BackupError.OtherDevice() }
            }
        } catch (e: BackupError) {
            throw e
        } catch (_: GeneralSecurityException) {
            throw BackupError.WrongPassphraseOrDamaged()
        }
        val doc = try {
            SettingsJson.format.decodeFromString(BackupDocument.serializer(), plain.decodeToString())
        } catch (_: Exception) {
            throw BackupError.NotABackup()
        }
        if (doc.format != BackupDocument.FORMAT) throw BackupError.NotABackup()
        return doc
    }

    private fun encrypt(key: SecretKey, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        cipher.updateAAD(MAGIC)
        return iv to cipher.doFinal(plain)
    }

    private fun decrypt(key: SecretKey, buf: ByteBuffer): ByteArray {
        val iv = ByteArray(12).also { buf.get(it) }
        val rest = ByteArray(buf.remaining()).also { buf.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        cipher.updateAAD(MAGIC)
        return cipher.doFinal(rest)
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, rounds: Int): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, rounds, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private val MAGIC = "ISB2".encodeToByteArray()
        private const val MODE_PASSPHRASE: Byte = 1
        private const val MODE_DEVICE: Byte = 2
        const val ROUNDS = 310_000
    }
}
