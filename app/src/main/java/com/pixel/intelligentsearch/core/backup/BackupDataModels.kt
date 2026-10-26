package com.pixel.intelligentsearch.core.backup

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class KdfMetadata(
    val algorithm: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 65536,
    val saltBase64: String,
    val keyLengthBits: Int = 256
)

@Immutable
@Serializable
data class CipherMetadata(
    val algorithm: String = "AES/GCM/NoPadding",
    val ivBase64: String,
    val tagLengthBits: Int = 128
)

@Immutable
@Serializable
data class EncryptedBackupEnvelope(
    val format: String = "INTELLIGENT_SEARCH_ENCRYPTED_BACKUP",
    val schemaVersion: Int = 1,
    val timestampMs: Long = System.currentTimeMillis(),
    val isHardwareBacked: Boolean = false,
    val hardwareChip: String? = null,
    val deviceModel: String? = null,
    val kdf: KdfMetadata? = null,
    val cipher: CipherMetadata,
    val encryptedPayloadBase64: String,
    val payloadSha256: String,
    /** Schema 2: the ways to open this backup, each wrapping its random content key. Empty in schema 1 files. */
    val keySlots: List<KeySlot> = emptyList()
) {
    /** A schema 2 backup that opens with a passkey. */
    val passkeySlot: KeySlot? get() = keySlots.firstOrNull { it.type == KeySlot.TYPE_PASSKEY }

    /** A schema 2 backup that opens with a passphrase. */
    val passphraseSlot: KeySlot? get() = keySlots.firstOrNull { it.type == KeySlot.TYPE_PASSPHRASE }

    /** Whether typing a passphrase can open this backup. */
    val opensWithPassphrase: Boolean get() = kdf != null || passphraseSlot != null
}

@Immutable
@Serializable
data class BackupContentPayload(
    val schemaVersion: Int = 1,
    val exportTimestampMs: Long = System.currentTimeMillis(),
    val appVersionCode: Int = 92,
    val preferencesMap: Map<String, String> = emptyMap(),
    val customBangsJson: String = "[]",
    val sectionConfigsJson: String = "[]",
    val searchHistory: List<String> = emptyList(),
    val hiddenApps: List<String> = emptyList(),
    val sharedPreferencesJson: String = "{}"
)
