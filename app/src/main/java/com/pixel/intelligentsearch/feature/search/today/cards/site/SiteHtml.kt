package com.pixel.intelligentsearch.feature.search.today.cards.site

import java.nio.charset.Charset
import java.text.BreakIterator
import java.util.Locale

/*
 * Reading a page's header into a card's few lines of plain text. Hand-written and tolerant, as a browser is with
 * broken markup, and never more than a scanner: no scripts run, no styles apply, nothing but the few tags below is
 * looked at.
 *
 * NG Designs
 */

/** What a page's header says about it, as raw (decoded, not yet cleaned) text. */
internal data class SiteHead(
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val siteName: String? = null
)

/** The longest a card's title (and site name) may be. */
internal const val SITE_TITLE_MAX = 80

/** The longest a card's description may be. */
internal const val SITE_DESCRIPTION_MAX = 160

/**
 * [body], the start of a page, as text: in the charset a byte-order mark names, else the one the server's
 * [contentType] declares, else the one a `<meta charset>` near the top declares, else UTF-8. An unknown charset counts
 * as none. Bytes the charset can't map become replacement characters, which [cleanSiteText] then drops.
 */
internal fun decodeSitePage(body: ByteArray, contentType: String?): String {
    val bom = when {
        body.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8
        body.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE
        body.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE
        else -> null
    }
    val charset = bom
        ?: charsetNamed(contentType?.let { CHARSET_PARAM.find(it)?.groupValues?.get(1) })
        ?: charsetNamed(META_CHARSET.find(String(body, 0, minOf(body.size, 4096), Charsets.ISO_8859_1))?.groupValues?.get(1))
        ?: Charsets.UTF_8
    val text = String(body, charset)
    return if (text.startsWith('\uFEFF')) text.substring(1) else text
}

private fun ByteArray.startsWith(vararg prefix: Int) = size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }

private val CHARSET_PARAM = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_.:-]+)", RegexOption.IGNORE_CASE)
private val META_CHARSET = Regex("<meta[^>]{0,512}?charset\\s*=\\s*[\"']?([A-Za-z0-9_.:-]+)", RegexOption.IGNORE_CASE)

private fun charsetNamed(name: String?): Charset? {
    if (name.isNullOrBlank()) return null
    // As browsers do: a page that says Latin-1 means Windows-1252.
    val canonical = if (name.equals("iso-8859-1", ignoreCase = true) || name.equals("latin1", ignoreCase = true)) "windows-1252" else name
    return runCatching { Charset.forName(canonical) }.getOrNull()
}

/**
 * The title, description, image and site name [html] declares in its header: `og:title`, else `twitter:title`, else
 * `<title>`; `og:description`, else `twitter:description`, else the meta description; `og:image` (or its `:url` and
 * `:secure_url` forms), else `twitter:image`; and `og:site_name`. The first of each counts. Comments are skipped,
 * `<script>`, `<style>` and similar bodies are stepped over unread, and scanning stops at `</head>` or `<body>`.
 * Entities in values are decoded; the image stays as written, to be resolved against the page.
 */
internal fun parseSiteHead(html: String): SiteHead {
    val metas = HashMap<String, String>()
    var titleTag: String? = null
    var i = 0
    val n = html.length
    while (i < n) {
        val lt = html.indexOf('<', i)
        if (lt < 0 || lt + 1 >= n) break
        if (html.startsWith("<!--", lt)) {
            val end = html.indexOf("-->", lt + 4)
            i = if (end < 0) n else end + 3
            continue
        }
        if (html[lt + 1] == '!' || html[lt + 1] == '?') {
            val end = html.indexOf('>', lt + 2)
            i = if (end < 0) n else end + 1
            continue
        }
        val closing = html[lt + 1] == '/'
        var p = if (closing) lt + 2 else lt + 1
        val nameStart = p
        while (p < n && (html[p].isLetterOrDigit() || html[p] == '-' || html[p] == ':')) p++
        if (p == nameStart) {
            i = lt + 1
            continue
        }
        val name = html.substring(nameStart, p).lowercase(Locale.ROOT)
        if (closing) {
            if (name == "head") break
            i = p
            continue
        }
        val (attributes, tagEnd) = readAttributes(html, p)
        i = tagEnd
        when (name) {
            "body" -> break
            "meta" -> {
                val key = (attributes["property"] ?: attributes["name"] ?: attributes["itemprop"])?.trim()?.lowercase(Locale.ROOT)
                val content = attributes["content"]
                if (key != null && content != null) metas.putIfAbsent(key, decodeEntities(content))
            }
            "title" -> {
                val end = indexOfIgnoreCase(html, "</title", i)
                val raw = html.substring(i, if (end < 0) minOf(n, i + 1024) else end)
                if (titleTag == null) titleTag = decodeEntities(raw)
                i = if (end < 0) n else end
            }
            in RAW_TEXT -> {
                val end = indexOfIgnoreCase(html, "</$name", i)
                i = if (end < 0) n else end
            }
        }
    }
    fun first(vararg keys: String) = keys.firstNotNullOfOrNull { key -> metas[key]?.takeIf { it.isNotBlank() } }
    return SiteHead(
        title = first("og:title", "twitter:title") ?: titleTag?.takeIf { it.isNotBlank() },
        description = first("og:description", "twitter:description", "description"),
        image = first("og:image", "og:image:secure_url", "og:image:url", "twitter:image", "twitter:image:src")?.trim(),
        siteName = first("og:site_name")
    )
}

/** Tags whose bodies are not markup: stepped over to their closing tag without reading. */
private val RAW_TEXT = setOf("script", "style", "noscript", "template", "textarea", "xmp", "iframe", "noembed", "noframes", "svg")

/** The attributes of the tag whose name ends at [from] (lowercase names, raw values), and where the tag ends. */
private fun readAttributes(html: String, from: Int): Pair<Map<String, String>, Int> {
    val attributes = HashMap<String, String>()
    val n = html.length
    var p = from
    while (p < n) {
        while (p < n && (html[p].isWhitespace() || html[p] == '/')) p++
        if (p >= n) break
        if (html[p] == '>') return attributes to p + 1
        val nameStart = p
        while (p < n && !html[p].isWhitespace() && html[p] != '=' && html[p] != '>' && html[p] != '/') p++
        val name = html.substring(nameStart, p).lowercase(Locale.ROOT)
        while (p < n && html[p].isWhitespace()) p++
        var value = ""
        if (p < n && html[p] == '=') {
            p++
            while (p < n && html[p].isWhitespace()) p++
            if (p < n && (html[p] == '"' || html[p] == '\'')) {
                val quote = html[p]
                val end = html.indexOf(quote, p + 1)
                value = html.substring(p + 1, if (end < 0) n else end)
                p = if (end < 0) n else end + 1
            } else {
                val valueStart = p
                while (p < n && !html[p].isWhitespace() && html[p] != '>') p++
                value = html.substring(valueStart, p)
            }
        }
        if (name.isNotEmpty()) attributes.putIfAbsent(name, value)
    }
    return attributes to n
}

private fun indexOfIgnoreCase(text: String, needle: String, from: Int): Int {
    var i = text.indexOf('<', from)
    while (i >= 0) {
        if (text.regionMatches(i, needle, 0, needle.length, ignoreCase = true)) return i
        i = text.indexOf('<', i + 1)
    }
    return -1
}

/**
 * [text] with its HTML character references decoded: numeric ones (`&#233;`, `&#xE9;`) and the named ones pages
 * commonly use in titles. An unknown name stays as written; a number that isn't a character becomes nothing.
 */
internal fun decodeEntities(text: String): String {
    if ('&' !in text) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c != '&') {
            out.append(c)
            i++
            continue
        }
        val semi = text.indexOf(';', i + 1)
        if (semi < 0 || semi - i > 12) {
            out.append(c)
            i++
            continue
        }
        val ref = text.substring(i + 1, semi)
        val decoded: String? = when {
            ref.startsWith("#x") || ref.startsWith("#X") -> codePointText(ref.substring(2).toIntOrNull(16))
            ref.startsWith("#") -> codePointText(ref.substring(1).toIntOrNull())
            else -> NAMED_ENTITIES[ref] ?: NAMED_ENTITIES[ref.lowercase(Locale.ROOT)].takeIf { ref.length <= 4 }
        }
        if (decoded == null) {
            out.append(c)
            i++
        } else {
            out.append(decoded)
            i = semi + 1
        }
    }
    return out.toString()
}

private fun codePointText(code: Int?): String? = when {
    code == null -> null
    // Negative, zero, surrogate and out-of-range references (Character.toChars would throw on them) read as nothing.
    code !in 1..0x10FFFF || code in 0xD800..0xDFFF -> ""
    else -> String(Character.toChars(code))
}

private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "ndash" to "\u2013", "mdash" to "\u2014", "hellip" to "\u2026", "middot" to "\u00B7", "bull" to "\u2022",
    "lsquo" to "\u2018", "rsquo" to "\u2019", "sbquo" to "\u201A", "ldquo" to "\u201C", "rdquo" to "\u201D", "bdquo" to "\u201E",
    "laquo" to "\u00AB", "raquo" to "\u00BB", "lsaquo" to "\u2039", "rsaquo" to "\u203A",
    "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122", "deg" to "\u00B0", "euro" to "\u20AC", "pound" to "\u00A3",
    "yen" to "\u00A5", "cent" to "\u00A2", "sect" to "\u00A7", "para" to "\u00B6", "times" to "\u00D7", "divide" to "\u00F7",
    "iexcl" to "\u00A1", "iquest" to "\u00BF", "frac12" to "\u00BD", "frac14" to "\u00BC", "frac34" to "\u00BE",
    "aacute" to "\u00E1", "eacute" to "\u00E9", "iacute" to "\u00ED", "oacute" to "\u00F3", "uacute" to "\u00FA",
    "Aacute" to "\u00C1", "Eacute" to "\u00C9", "Iacute" to "\u00CD", "Oacute" to "\u00D3", "Uacute" to "\u00DA",
    "agrave" to "\u00E0", "egrave" to "\u00E8", "igrave" to "\u00EC", "ograve" to "\u00F2", "ugrave" to "\u00F9",
    "acirc" to "\u00E2", "ecirc" to "\u00EA", "icirc" to "\u00EE", "ocirc" to "\u00F4", "ucirc" to "\u00FB",
    "auml" to "\u00E4", "euml" to "\u00EB", "iuml" to "\u00EF", "ouml" to "\u00F6", "uuml" to "\u00FC",
    "Auml" to "\u00C4", "Ouml" to "\u00D6", "Uuml" to "\u00DC", "szlig" to "\u00DF", "ntilde" to "\u00F1", "Ntilde" to "\u00D1",
    "ccedil" to "\u00E7", "Ccedil" to "\u00C7", "aring" to "\u00E5", "Aring" to "\u00C5", "oslash" to "\u00F8", "Oslash" to "\u00D8",
    "aelig" to "\u00E6", "AElig" to "\u00C6", "atilde" to "\u00E3", "otilde" to "\u00F5"
)

/**
 * [raw] made safe to show as plain text in at most [max] characters: entities already decoded, every control, format
 * (bidi marks, embeddings and isolates, zero-width spaces and joiners, soft hyphens), private-use, unassigned and lone
 * surrogate character removed, any run of whitespace (line breaks included) made one space, and the ends trimmed. Too
 * long, it is cut at a character boundary (never inside an emoji or accent), at the last space when that is near, and
 * ends in an ellipsis, all within [max].
 */
internal fun cleanSiteText(raw: String?, max: Int): String {
    if (raw.isNullOrEmpty()) return ""
    val out = StringBuilder(minOf(raw.length, max * 4))
    var space = false
    var i = 0
    while (i < raw.length) {
        val cp = raw.codePointAt(i)
        i += Character.charCount(cp)
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
            space = out.isNotEmpty()
            continue
        }
        when (Character.getType(cp)) {
            Character.CONTROL.toInt(), Character.FORMAT.toInt(), Character.PRIVATE_USE.toInt(),
            Character.SURROGATE.toInt(), Character.UNASSIGNED.toInt() -> continue
        }
        if (cp == 0xFFFD || cp == 0xFFFC) continue
        if (space) out.append(' ')
        space = false
        out.appendCodePoint(cp)
        // Enough to cut from: stop scanning long text early.
        if (out.length > max * 4) break
    }
    return capText(out.toString(), max)
}

private fun capText(text: String, max: Int): String {
    if (text.length <= max) return text
    val limit = max - 1 // room for the ellipsis
    val breaks = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    var cut = breaks.preceding(limit + 1).coerceAtLeast(0)
    if (cut == BreakIterator.DONE) cut = 0
    val space = text.lastIndexOf(' ', cut)
    if (space >= limit * 2 / 3) cut = space
    return text.substring(0, cut).trimEnd(' ', ',', ';', ':', '-', '\u2013', '\u2014') + "\u2026"
}
