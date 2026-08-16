package com.pixel.intelligentsearch.core.data
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder

object WebSearchProvider {
    private val suggestionCache = LruCache<String, List<String>>(128)

    fun getCachedSuggestions(query: String): List<String>? {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) return null
        return synchronized(suggestionCache) { suggestionCache.get(trimmed) }
    }

    suspend fun getWebSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        
        val cacheKey = trimmed.lowercase()
        synchronized(suggestionCache) {
            val cached = suggestionCache.get(cacheKey)
            if (cached != null) return@withContext cached
        }

        val suggestions = mutableListOf<String>()
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val url = java.net.URI.create("https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            connection.setRequestProperty("Connection", "keep-alive")
            connection.connectTimeout = 1200
            connection.readTimeout = 1200

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                // Response format: ["query", ["suggestion1", "suggestion2", ...]]
                val jsonArray = JSONArray(response)
                if (jsonArray.length() >= 2) {
                    val suggestionsArray = jsonArray.getJSONArray(1)
                    for (i in 0 until suggestionsArray.length()) {
                        suggestions.add(suggestionsArray.getString(i))
                    }
                }
                if (suggestions.isNotEmpty()) {
                    synchronized(suggestionCache) {
                        suggestionCache.put(cacheKey, suggestions)
                    }
                }
            }
        } catch (e: Exception) {
            // Non-fatal network timeout / connection error
        }
        return@withContext suggestions
    }
}
