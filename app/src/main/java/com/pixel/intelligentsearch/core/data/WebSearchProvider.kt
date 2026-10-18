package com.pixel.intelligentsearch.core.data

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder

object WebSearchProvider {
    private val suggestionCache = LruCache<String, List<String>>(128)

    fun getCachedSuggestions(query: String, engine: String = "Google"): List<String>? {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank() || engine == "Tor Project" || engine == "Tor Browser" || engine == "Custom") return null
        val cacheKey = "$engine:$trimmed"
        return synchronized(suggestionCache) { suggestionCache.get(cacheKey) }
    }

    fun getWebSuggestionsSync(query: String, engine: String = "Google", timeoutMs: Int = 600): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        if (engine == "Tor Project" || engine == "Tor Browser" || engine == "Custom") return emptyList()

        val cacheKey = "$engine:${trimmed.lowercase()}"
        synchronized(suggestionCache) {
            val cached = suggestionCache.get(cacheKey)
            if (cached != null) return cached
        }

        val suggestions = mutableListOf<String>()
        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val primaryUrl = when (engine) {
            "DuckDuckGo" -> "https://duckduckgo.com/ac/?q=$encodedQuery&type=list"
            "Bing" -> "https://api.bing.com/osjson.aspx?query=$encodedQuery"
            else -> "https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery"
        }

        var connection: HttpURLConnection? = null
        try {
            connection = java.net.URI.create(primaryUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                // Reading the body to the end and closing it returns the socket to the keep-alive pool.
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val suggestionsArray = JSONArray(response).optJSONArray(1)
                if (suggestionsArray != null) {
                    for (i in 0 until suggestionsArray.length()) {
                        suggestionsArray.optString(i).takeIf { it.isNotBlank() }?.let(suggestions::add)
                    }
                }
            } else {
                connection.disconnect()
            }
        } catch (_: Exception) {
            connection?.disconnect()
        }

        if (suggestions.isNotEmpty()) {
            synchronized(suggestionCache) {
                suggestionCache.put(cacheKey, suggestions)
            }
        }
        return suggestions
    }

    suspend fun getWebSuggestions(query: String, engine: String = "Google"): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        // runInterruptible lets cancellation interrupt the blocking socket read, so the next query's request doesn't
        // queue behind this one's timeout.
        return@withContext kotlinx.coroutines.runInterruptible { getWebSuggestionsSync(trimmed, engine, timeoutMs = 800) }
    }
}
