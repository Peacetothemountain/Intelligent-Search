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
    val kdf: KdfMetadata? = null,
    val cipher: CipherMetadata,
    val encryptedPayloadBase64: String,
    val payloadSha256: String
)

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
    val hiddenApps: List<String> = emptyList()
)
