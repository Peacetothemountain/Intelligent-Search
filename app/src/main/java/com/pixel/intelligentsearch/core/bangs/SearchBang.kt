package com.pixel.intelligentsearch.core.bangs

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SearchBang(
    val prefix: String,
    val name: String,
    val urlTemplate: String,
    val targetPackage: String? = null,
    val appIntentUriTemplate: String? = null,
    val isBuiltIn: Boolean = false,
    val description: String = ""
) {
    init {
        require(prefix.startsWith("!")) { "Bang prefix must start with '!', received: $prefix" }
        require(prefix.length >= 2) { "Bang prefix must have at least 2 characters (e.g. '!g')" }
    }

    val displayPrefix: String
        get() = prefix.lowercase()

    fun buildWebUrl(query: String): String {
        val encodedQuery = android.net.Uri.encode(query.trim())
        return if (urlTemplate.contains("%s")) {
            urlTemplate.replace("%s", encodedQuery)
        } else {
            "$urlTemplate$encodedQuery"
        }
    }

    fun buildAppUri(query: String): android.net.Uri? {
        val template = appIntentUriTemplate ?: return null
        val encodedQuery = android.net.Uri.encode(query.trim())
        val uriStr = if (template.contains("%s")) {
            template.replace("%s", encodedQuery)
        } else {
            "$template$encodedQuery"
        }
        return android.net.Uri.parse(uriStr)
    }
}

data class ParsedBangQuery(
    val bang: SearchBang,
    val rawQuery: String,
    val extractedQuery: String,
    val isPrefix: Boolean
)
