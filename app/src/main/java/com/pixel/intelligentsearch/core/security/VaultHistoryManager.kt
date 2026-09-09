package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.pixel.intelligentsearch.core.data.HistoryEntity
import java.util.UUID

/**
 * Encrypted search history record model.
 */
data class EncryptedHistoryItem(
    val id: String,
    val encryptedRecord: EncryptedVaultRecord,
    val timestamp: Long
)

/**
 * Military-grade hardware-backed search history manager.
 *
 * Encrypts search queries with AES-256-GCM authenticated envelope encryption,
 * per-record random nonces, and HMAC-SHA256 blind indexing for query de-duplication
 * and instant matching without leaking plaintext queries to disk or database journals.
 *
 * Engineered by NG Designs.
 */
class VaultHistoryManager(
    private val context: Context,
    private val securityManager: StrongBoxSecurityManager = StrongBoxSecurityManager(context),
    private val encryptedVault: EncryptedDataVault = EncryptedDataVault(context, securityManager)
) {

    companion object {
        private const val TAG = "VaultHistoryManager"
        private const val DOMAIN_HISTORY = "search_history"
        private const val PREFS_NAME = "vault_search_history_storage"
        private const val MAX_HISTORY_ITEMS = 100
    }

    private val historyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Encrypts and inserts a search query into the encrypted history vault.
     */
    @Synchronized
    fun recordSearch(query: String, profileId: String = "default") {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        val blindIndex = securityManager.computeBlindIndex(trimmed)
        val recordId = UUID.nameUUIDFromBytes(blindIndex.toByteArray(Charsets.UTF_8)).toString()
        val rawBytes = trimmed.toByteArray(Charsets.UTF_8)

        try {
            val record = encryptedVault.encrypt(
                domain = DOMAIN_HISTORY,
                recordId = recordId,
                plaintext = rawBytes,
                profileId = profileId,
                generateBlindIndexFor = trimmed
            )

            val now = System.currentTimeMillis()
            val existingIds = getHistoryIndex().toMutableList()
            existingIds.removeAll { it.first == recordId }
            existingIds.add(0, Pair(recordId, now))

            // Prune to MAX_HISTORY_ITEMS
            val prunedList = if (existingIds.size > MAX_HISTORY_ITEMS) {
                val excess = existingIds.subList(MAX_HISTORY_ITEMS, existingIds.size)
                val editor = historyPrefs.edit()
                for ((excessId, _) in excess) {
                    editor.remove("${excessId}_payload")
                }
                editor.apply()
                existingIds.take(MAX_HISTORY_ITEMS)
            } else {
                existingIds
            }

            historyPrefs.edit()
                .putString("${recordId}_payload", record.toBase64String())
                .putString("history_index", serializeIndex(prunedList))
                .apply()
        } finally {
            MemorySanitizer.wipe(rawBytes)
        }
    }

    /**
     * Retrieves all decrypted history items sorted by timestamp descending.
     */
    @Synchronized
    fun getDecryptedHistory(profileId: String = "default"): List<HistoryEntity> {
        val index = getHistoryIndex()
        val result = mutableListOf<HistoryEntity>()

        for ((recordId, timestamp) in index) {
            val payloadBase64 = historyPrefs.getString("${recordId}_payload", null) ?: continue
            try {
                val record = EncryptedVaultRecord.fromBase64(
                    recordId = recordId,
                    domain = DOMAIN_HISTORY,
                    base64Payload = payloadBase64
                )
                val decryptedQuery = encryptedVault.decryptToString(record, profileId)
                if (!decryptedQuery.isNullOrBlank()) {
                    result.add(HistoryEntity(query = decryptedQuery, timestamp = timestamp))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt history record $recordId", e)
            }
        }

        return result
    }

    /**
     * Deletes a single history record matching the query by blind index.
     */
    @Synchronized
    fun deleteSearch(query: String) {
        val blindIndex = securityManager.computeBlindIndex(query.trim())
        val recordId = UUID.nameUUIDFromBytes(blindIndex.toByteArray(Charsets.UTF_8)).toString()

        val index = getHistoryIndex().toMutableList()
        index.removeAll { it.first == recordId }

        historyPrefs.edit()
            .remove("${recordId}_payload")
            .putString("history_index", serializeIndex(index))
            .apply()
    }

    /**
     * Purges all encrypted search history and wipes storage.
     */
    @Synchronized
    fun clearHistory() {
        historyPrefs.edit().clear().apply()
    }

    private fun getHistoryIndex(): List<Pair<String, Long>> {
        val rawIndex = historyPrefs.getString("history_index", null) ?: return emptyList()
        return rawIndex.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val id = parts[0]
                val ts = parts[1].toLongOrNull() ?: 0L
                Pair(id, ts)
            } else null
        }
    }

    private fun serializeIndex(index: List<Pair<String, Long>>): String {
        return index.joinToString(";") { "${it.first}:${it.second}" }
    }
}
