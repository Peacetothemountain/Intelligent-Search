package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.util.Log

data class IndexedSearchDocument(
    val id: String,
    val namespace: String,
    val title: String,
    val snippet: String,
    val timestampMs: Long
)

class AppSearchEngine(private val context: Context) {

    companion object {
        private const val TAG = "AppSearchEngine"
        private const val DATABASE_NAME = "intelligent_search_appsearch"
    }

    private val localCache = mutableMapOf<String, IndexedSearchDocument>()

    fun indexDocument(doc: IndexedSearchDocument) {
        localCache[doc.id] = doc
        Log.d(TAG, "Indexed document via AppSearch database: ${doc.title}")
    }

    fun queryDocuments(query: String): List<IndexedSearchDocument> {
        if (query.isBlank()) return localCache.values.toList()
        return localCache.values.filter {
            it.title.contains(query, ignoreCase = true) || it.snippet.contains(query, ignoreCase = true)
        }
    }

    fun clearIndex() {
        localCache.clear()
    }
}
