package com.pixel.intelligentsearch.core.data

import android.util.LruCache
import com.pixel.intelligentsearch.core.net.HttpsCall
import com.pixel.intelligentsearch.core.net.HttpsClient
import com.pixel.intelligentsearch.core.net.HttpsGet
import com.pixel.intelligentsearch.core.net.withHttpsCall
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object WebSearchProvider {
    private val suggestionCache = LruCache<String, List<WebSuggestion>>(128)

    /** The longest suggestion answer read; real ones are a few KB. */
    private const val MAX_RESPONSE_BYTES = 64 * 1024

    /**
     * Opens the connection for each request in tests (a local server), in place of the engine's address; null in the
     * app. The request still goes through the app's [HttpsClient].
     */
    @androidx.annotation.VisibleForTesting
    internal var openForTests: ((URL) -> HttpURLConnection?)? = null

    /** Drops every cached suggestion, so a test starts from an empty cache. */
    @androidx.annotation.VisibleForTesting
    internal fun clearCacheForTests() = synchronized(suggestionCache) { suggestionCache.evictAll() }

    private fun suggestsFor(engine: String) = com.pixel.intelligentsearch.core.security.NetworkPolicy.engineSuggests(engine)

    fun getCachedSuggestions(query: String, engine: String = "Google"): List<WebSuggestion>? {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank() || !suggestsFor(engine)) return null
        val cacheKey = "$engine:$trimmed"
        return synchronized(suggestionCache) { suggestionCache.get(cacheKey) }
    }

    /**
     * The address suggestions for [encodedQuery] come from. Google is asked the way its own mobile search page asks
     * (`client=mobile-gws-wiz-hp`), which is the answer that carries entity names, descriptions and thumbnails and
     * marks the completion bold; `client=chrome` sends bare strings only. `hl` is the phone's language, as Google's
     * page sends it.
     */
    internal fun suggestUrl(engine: String, encodedQuery: String, locale: Locale = Locale.getDefault()): String = when (engine) {
        "DuckDuckGo" -> "https://duckduckgo.com/ac/?q=$encodedQuery&type=list"
        "Bing" -> "https://api.bing.com/osjson.aspx?query=$encodedQuery"
        else -> {
            val hl = URLEncoder.encode(locale.toLanguageTag().ifBlank { "en" }, "UTF-8")
            "https://suggestqueries.google.com/complete/search?client=mobile-gws-wiz-hp&xssi=t&hl=$hl&q=$encodedQuery"
        }
    }

    /**
     * Fetches suggestions for [query], blocking, over the app's [HttpsClient]: https only, redirects refused, nothing
     * cached, the answer capped at [MAX_RESPONSE_BYTES], and the same User-Agent from every phone. Each connect or
     * read may take [timeoutMs], the whole request three times that. Cancelling [call] from another thread ends it at
     * once.
     */
    fun getWebSuggestionsSync(
        query: String,
        engine: String = "Google",
        timeoutMs: Int = 600,
        call: HttpsCall = HttpsCall()
    ): List<WebSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        if (!suggestsFor(engine)) return emptyList()

        val cacheKey = "$engine:${trimmed.lowercase()}"
        synchronized(suggestionCache) {
            val cached = suggestionCache.get(cacheKey)
            if (cached != null) return cached
        }

        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val body = http(timeoutMs).get(suggestUrl(engine, encodedQuery), MAX_RESPONSE_BYTES, call)
        val suggestions = body?.let { runCatching { WebSuggestionParser.parse(it.toString(Charsets.UTF_8)) }.getOrNull() }.orEmpty()

        if (suggestions.isNotEmpty()) {
            synchronized(suggestionCache) {
                suggestionCache.put(cacheKey, suggestions)
            }
        }
        return suggestions
    }

    private fun http(timeoutMs: Int): HttpsGet {
        val totalTimeoutMs = timeoutMs * 3L
        val open = openForTests ?: return HttpsClient(timeoutMs, totalTimeoutMs, HttpsClient.ACCEPT_JSON)
        return HttpsClient(timeoutMs, totalTimeoutMs, open, System::nanoTime, HttpsClient.ACCEPT_JSON)
    }

    suspend fun getWebSuggestions(query: String, engine: String = "Google", timeoutMs: Int = 800): List<WebSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        // A blocking socket read ignores thread interrupts, so cancelling (the next query replaces this one) closes the
        // connection instead ([withHttpsCall]); otherwise the stale request would hold on until its timeout, and the
        // next query's suggestions would wait behind it.
        return withHttpsCall { call -> getWebSuggestionsSync(trimmed, engine, timeoutMs, call) }
    }
}
