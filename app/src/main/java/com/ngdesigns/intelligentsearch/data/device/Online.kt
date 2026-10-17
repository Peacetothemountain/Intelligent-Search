package com.ngdesigns.intelligentsearch.data.device

import com.ngdesigns.intelligentsearch.engine.web.SearchEngine
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Search suggestions from the user's web search engine. Private engines without a feed return nothing. */
interface SuggestionSource {
    suspend fun suggestions(query: String, engine: SearchEngine, limit: Int): List<String>
}

class WebSuggestionSource(private val timeoutMillis: Int = 2500) : SuggestionSource {
    override suspend fun suggestions(query: String, engine: SearchEngine, limit: Int): List<String> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val address = engine.suggestionsUrl(query.trim()) ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                    connectTimeout = timeoutMillis
                    readTimeout = timeoutMillis
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (connection.responseCode != 200) return@runCatching emptyList()
                    parse(connection.inputStream.bufferedReader().use { it.readText() }, limit)
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(emptyList())
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** OpenSearch suggestions: `["query", ["suggestion", ...], ...]`. */
        fun parse(body: String, limit: Int): List<String> = runCatching {
            val root = json.parseToJsonElement(body) as JsonArray
            (root[1] as JsonArray).mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                .filter { it.isNotBlank() }.distinct().take(limit)
        }.getOrDefault(emptyList())
    }
}
