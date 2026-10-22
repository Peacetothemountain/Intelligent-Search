package com.pixel.intelligentsearch.core.data

import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

object WebSearchProvider {
    private val suggestionCache = LruCache<String, List<String>>(128)

    /** Where tests send suggestion requests instead of the engine (the query is appended); null in the app. */
    @androidx.annotation.VisibleForTesting
    internal var endpointForTests: String? = null

    fun getCachedSuggestions(query: String, engine: String = "Google"): List<String>? {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank() || engine == "Tor Project" || engine == "Tor Browser" || engine == "Custom") return null
        val cacheKey = "$engine:$trimmed"
        return synchronized(suggestionCache) { suggestionCache.get(cacheKey) }
    }

    /**
     * Fetches suggestions for [query], blocking. [onConnection] is handed the connection before it connects, so a
     * caller on another thread can disconnect it to abandon the request.
     */
    fun getWebSuggestionsSync(
        query: String,
        engine: String = "Google",
        timeoutMs: Int = 600,
        onConnection: (HttpURLConnection) -> Unit = {}
    ): List<String> {
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
        val primaryUrl = endpointForTests?.let { it + encodedQuery } ?: when (engine) {
            "DuckDuckGo" -> "https://duckduckgo.com/ac/?q=$encodedQuery&type=list"
            "Bing" -> "https://api.bing.com/osjson.aspx?query=$encodedQuery"
            else -> "https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery"
        }

        var connection: HttpURLConnection? = null
        try {
            connection = java.net.URI.create(primaryUrl).toURL().openConnection() as HttpURLConnection
            onConnection(connection)
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

    suspend fun getWebSuggestions(query: String, engine: String = "Google", timeoutMs: Int = 800): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        // A blocking socket read ignores thread interrupts, so cancelling (the next query replaces this one) closes the
        // connection instead; otherwise the stale request would hold on until its timeout, and the next query's
        // suggestions would wait behind it. Only a cancelled request is closed: a finished one returns its socket to
        // the keep-alive pool.
        val connection = AtomicReference<HttpURLConnection?>()
        return coroutineScope {
            val request = async(Dispatchers.IO) {
                val job = coroutineContext.job
                getWebSuggestionsSync(trimmed, engine, timeoutMs) { opened ->
                    connection.set(opened)
                    // Cancelled before there was a connection to close: don't start it (the throw is caught there).
                    if (!job.isActive) throw CancellationException("Suggestion request cancelled")
                }
            }
            try {
                request.await()
            } catch (e: CancellationException) {
                connection.get()?.disconnect()
                throw e
            }
        }
    }
}
