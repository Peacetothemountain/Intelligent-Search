package com.pixel.intelligentsearch.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

data class IndexedSearchDocument(
    val id: String,
    val namespace: String,
    val title: String,
    val snippet: String,
    val timestampMs: Long
)

@Singleton
class AppSearchEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_DOCUMENT_CAPACITY = 256
    }

    private val localCache = ConcurrentHashMap<String, IndexedSearchDocument>()
    private val evictionQueue = ConcurrentLinkedQueue<String>()

    fun indexDocument(doc: IndexedSearchDocument) {
        if (!localCache.containsKey(doc.id)) {
            if (localCache.size >= MAX_DOCUMENT_CAPACITY) {
                evictionQueue.poll()?.let { oldestId ->
                    localCache.remove(oldestId)
                }
            }
            evictionQueue.offer(doc.id)
        }
        localCache[doc.id] = doc
    }

    fun queryDocuments(query: String): List<IndexedSearchDocument> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return localCache.values.sortedByDescending { it.timestampMs }
        }
        return localCache.values.filter {
            it.title.contains(trimmed, ignoreCase = true) || 
            it.snippet.contains(trimmed, ignoreCase = true)
        }.sortedByDescending { it.timestampMs }
    }

    fun clearIndex() {
        localCache.clear()
        evictionQueue.clear()
    }
}
