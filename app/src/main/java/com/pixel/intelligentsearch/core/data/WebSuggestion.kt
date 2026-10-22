package com.pixel.intelligentsearch.core.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * One web search suggestion. [query] is what is searched and filled into the box. An entity suggestion (a person, a
 * song, a place) also has Google's display [title] ("Barack Obama" for "obama"), a [subtitle] ("44th U.S. President")
 * and an [imageUrl] for its thumbnail (https, or a data: URL); a plain query has none of them.
 * [boldRanges] are the parts of [query] the engine marked, which is what the user did not type (Google bolds the
 * completion, and a corrected word); empty when the engine marks nothing.
 */
@Immutable
data class WebSuggestion(
    val query: String,
    val title: String? = null,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val boldRanges: List<IntRange> = emptyList()
) {
    /** Whether this is an entity (it has a picture or a description), shown as a two-line row. */
    val isEntity: Boolean get() = imageUrl != null || !subtitle.isNullOrBlank()
}

/**
 * Reads suggestion responses. Google's own search boxes use the `gws-wiz` clients of `/complete/search`, which answer
 * `[[[text, type, subtypes, {zh, zi, zs}], ...], {...}]`: text carries `<b>` around the completion and HTML entities
 * (`don&#39;t`), and type 46 rows are entities with a display name (`zh`), a description (`zi`) and a thumbnail
 * (`zs`). The response is prefixed with `)]}'` (with `xssi=t`) or wrapped in `window.google.ac.h(...)`. DuckDuckGo,
 * Bing and the older `client=chrome` answer OpenSearch JSON instead: `[query, [suggestions...], ...]`.
 */
object WebSuggestionParser {

    /** Suggestions in [body], whichever of the two formats it is; empty when it is neither. */
    fun parse(body: String): List<WebSuggestion> {
        val json = unwrap(body) ?: return emptyList()
        val root = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        // OpenSearch puts the query string first; gws-wiz puts the list of rows first.
        return if (root.opt(0) is JSONArray) parseGwsWiz(root) else parseOpenSearch(root)
    }

    private fun unwrap(body: String): String? {
        var text = body.trim()
        if (text.startsWith(")]}'")) text = text.removePrefix(")]}'").trim()
        if (text.startsWith("window.google.ac.h(")) {
            text = text.removePrefix("window.google.ac.h(").trim().removeSuffix(";").trim().removeSuffix(")")
        }
        return text.takeIf { it.startsWith("[") }
    }

    private fun parseGwsWiz(root: JSONArray): List<WebSuggestion> {
        val rows = root.optJSONArray(0) ?: return emptyList()
        val out = ArrayList<WebSuggestion>(rows.length())
        for (i in 0 until rows.length()) {
            val row = rows.optJSONArray(i) ?: continue
            val (query, bold) = decodeMarkup(row.optString(0))
            if (query.isBlank()) continue
            val details = (0 until row.length()).firstNotNullOfOrNull { row.opt(it) as? JSONObject }
            val title = details?.optString("zh")?.let { decodeMarkup(it).first }?.takeIf { it.isNotBlank() }
            val subtitle = details?.optString("zi")?.let { decodeMarkup(it).first }?.takeIf { it.isNotBlank() }
            val image = details?.optString("zs")?.takeIf(::isUsableImageUrl)
            out += WebSuggestion(query, title, subtitle, image, bold)
        }
        return out.distinctBy { it.query.lowercase() }
    }

    private fun parseOpenSearch(root: JSONArray): List<WebSuggestion> {
        val list = root.optJSONArray(1) ?: return emptyList()
        return (0 until list.length())
            .mapNotNull { i -> list.optString(i).takeIf { it.isNotBlank() } }
            .map { WebSuggestion(it) }
            .distinctBy { it.query.lowercase() }
    }

    /** Only images the row can show safely: https, or an inline data: image. */
    internal fun isUsableImageUrl(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("data:image/")

    /**
     * [markup] as plain text, with the ranges that sat inside `<b>` tags. Other tags are dropped and HTML entities
     * decoded, so `don<b>&#39;</b>t` reads `don't` with the apostrophe bold.
     */
    internal fun decodeMarkup(markup: String): Pair<String, List<IntRange>> {
        if (markup.indexOf('<') < 0 && markup.indexOf('&') < 0) return markup to emptyList()
        val text = StringBuilder(markup.length)
        val bold = ArrayList<IntRange>()
        var boldStart = -1
        var i = 0
        while (i < markup.length) {
            val c = markup[i]
            if (c == '<') {
                val end = markup.indexOf('>', i)
                if (end < 0) { text.append(markup, i, markup.length); break }
                when (markup.substring(i + 1, end).trim().lowercase()) {
                    "b" -> if (boldStart < 0) boldStart = text.length
                    "/b" -> {
                        if (boldStart in 0 until text.length) bold += boldStart until text.length
                        boldStart = -1
                    }
                }
                i = end + 1
            } else if (c == '&') {
                val end = markup.indexOf(';', i)
                val decoded = if (end in (i + 2)..(i + 10)) decodeEntity(markup.substring(i + 1, end)) else null
                if (decoded != null) {
                    text.append(decoded)
                    i = end + 1
                } else {
                    text.append(c)
                    i++
                }
            } else {
                text.append(c)
                i++
            }
        }
        if (boldStart in 0 until text.length) bold += boldStart until text.length
        return text.toString() to merge(bold)
    }

    private fun decodeEntity(name: String): String? = when {
        name == "amp" -> "&"
        name == "lt" -> "<"
        name == "gt" -> ">"
        name == "quot" -> "\""
        name == "apos" -> "'"
        name == "nbsp" -> " "
        name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
        else -> null
    }

    private fun codePoint(cp: Int): String? = if (Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else null

    /** Joins touching ranges ("<b>taylor</b><b> </b>" is one bold run). */
    private fun merge(ranges: List<IntRange>): List<IntRange> {
        if (ranges.size < 2) return ranges
        val out = ArrayList<IntRange>(ranges.size)
        for (r in ranges.sortedBy { it.first }) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) out[out.size - 1] = last.first..maxOf(last.last, r.last) else out += r
        }
        return out
    }
}

/**
 * What a suggestion row shows bold: the engine's own marks when it sent any, else (engines without marks) the part
 * after what the user typed, when the suggestion starts with it. Typed text itself stays regular, as on Google.
 */
fun WebSuggestion.highlightFor(typed: String): List<IntRange> {
    if (boldRanges.isNotEmpty()) return boldRanges
    val t = typed.trim()
    return if (t.isNotEmpty() && query.length > t.length && query.startsWith(t, ignoreCase = true)) {
        listOf(t.length until query.length)
    } else emptyList()
}
