package com.pixel.intelligentsearch.core.data

import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder

object WebSearchProvider {
    private val suggestionCache = LruCache<String, List<String>>(128)

    fun getCachedSuggestions(query: String, engine: String = "Google"): List<String>? {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) return null
        val cacheKey = "$engine:$trimmed"
        return synchronized(suggestionCache) { suggestionCache.get(cacheKey) }
    }

    fun getWebSuggestionsSync(query: String, engine: String = "Google", timeoutMs: Int = 600): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

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

        fun fetchFromUrl(urlString: String): Boolean {
            var connection: HttpURLConnection? = null
            try {
                val url = java.net.URI.create(urlString).toURL()
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                connection.setRequestProperty("Connection", "keep-alive")
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    if (jsonArray.length() >= 2) {
                        val suggestionsArray = jsonArray.getJSONArray(1)
                        for (i in 0 until suggestionsArray.length()) {
                            suggestions.add(suggestionsArray.getString(i))
                        }
                    }
                    return suggestions.isNotEmpty()
                }
            } catch (_: Exception) {
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
            }
            return false
        }

        val success = fetchFromUrl(primaryUrl)
        if (!success && engine != "Google") {
            // Fallback to Google suggestion API if primary provider times out
            fetchFromUrl("https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery")
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
        return@withContext getWebSuggestionsSync(trimmed, engine, timeoutMs = 800)
    }
}
