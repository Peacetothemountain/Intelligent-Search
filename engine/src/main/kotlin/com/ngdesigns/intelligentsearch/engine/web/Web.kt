package com.ngdesigns.intelligentsearch.engine.web

import java.net.URLEncoder

/** Where web searches go. [Tor] searches DuckDuckGo's onion service and must only ever open in Tor Browser. */
sealed class SearchEngine(val name: String) {
    abstract fun searchUrl(query: String): String
    /** The suggestion feed (OpenSearch JSON), or null for engines that offer none. */
    open fun suggestionsUrl(query: String): String? = null
    /** The account search history page, or null for private engines that keep none. */
    open val historyUrl: String? = null

    data object Google : SearchEngine("Google") {
        override fun searchUrl(query: String) = "https://www.google.com/search?q=${enc(query)}"
        override fun suggestionsUrl(query: String) = "https://suggestqueries.google.com/complete/search?client=chrome&q=${enc(query)}"
        override val historyUrl = "https://myactivity.google.com/myactivity?product=6"
    }

    data object Bing : SearchEngine("Bing") {
        override fun searchUrl(query: String) = "https://www.bing.com/search?q=${enc(query)}"
        override fun suggestionsUrl(query: String) = "https://api.bing.com/osjson.aspx?query=${enc(query)}"
        override val historyUrl = "https://www.bing.com/profile/history"
    }

    data object DuckDuckGo : SearchEngine("DuckDuckGo") {
        override fun searchUrl(query: String) = "https://duckduckgo.com/?q=${enc(query)}"
        override fun suggestionsUrl(query: String) = "https://duckduckgo.com/ac/?q=${enc(query)}&type=list"
    }

    data object Tor : SearchEngine("Tor") {
        const val ONION = "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/?q="
        val browserPackages = listOf("org.torproject.torbrowser", "org.torproject.torbrowser_alpha")
        override fun searchUrl(query: String) = ONION + enc(query)
    }

    /** A user's own engine; [template] has already passed [SafeUrl]. */
    data class Custom(val template: String) : SearchEngine(SafeUrl.displayName(template)) {
        override fun searchUrl(query: String) = SafeUrl.build(template, query)?.toString() ?: Google.searchUrl(query)
    }

    companion object {
        // A getter, not a stored list: the companion initializes before these subclasses, which would read as null.
        val builtIn: List<SearchEngine> get() = listOf(Google, Bing, DuckDuckGo, Tor)

        fun named(name: String, customTemplate: String = ""): SearchEngine =
            builtIn.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: SafeUrl.template(customTemplate)?.let { Custom(it.template) }
                ?: Google
    }
}

/**
 * A quick web shortcut: typing the trigger symbol and [key] ("!yt cats" or "cats !yt") searches [name]. [appUri]
 * opens the site's app directly when installed.
 */
data class WebShortcut(
    val key: String,
    val name: String,
    val urlTemplate: String,
    val appUri: String? = null,
    val appPackage: String? = null,
    val builtIn: Boolean = false,
) {
    fun url(query: String): String = SafeUrl.build(urlTemplate, query)?.toString() ?: SearchEngine.Google.searchUrl(query)
    fun appUrl(query: String): String? = appUri?.replace("%s", enc(query))
}

object WebShortcuts {
    val builtIn: List<WebShortcut> = listOf(
        WebShortcut("g", "Google", "https://www.google.com/search?q=%s", builtIn = true),
        WebShortcut("yt", "YouTube", "https://www.youtube.com/results?search_query=%s", "vnd.youtube://results?q=%s", "com.google.android.youtube", true),
        WebShortcut("w", "Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search=%s", builtIn = true),
        WebShortcut("r", "Reddit", "https://www.reddit.com/search/?q=%s", appPackage = "com.reddit.frontpage", builtIn = true),
        WebShortcut("m", "Google Maps", "https://www.google.com/maps/search/%s", "geo:0,0?q=%s", "com.google.android.apps.maps", true),
        WebShortcut("a", "Amazon", "https://www.amazon.com/s?k=%s", appPackage = "com.amazon.mShop.android.shopping", builtIn = true),
        WebShortcut("gh", "GitHub", "https://github.com/search?q=%s", builtIn = true),
        WebShortcut("d", "Google Drive", "https://drive.google.com/drive/search?q=%s", builtIn = true),
        WebShortcut("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s", builtIn = true),
        WebShortcut("x", "X", "https://x.com/search?q=%s", builtIn = true),
        WebShortcut("so", "Stack Overflow", "https://stackoverflow.com/search?q=%s", builtIn = true),
        WebShortcut("play", "Play Store", "https://play.google.com/store/search?q=%s&c=apps", "market://search?q=%s", "com.android.vending", true),
        WebShortcut("spot", "Spotify", "https://open.spotify.com/search/%s", "spotify:search:%s", "com.spotify.music", true),
    )

    data class Use(val shortcut: WebShortcut, val terms: String)

    /**
     * The shortcut a query invokes, at its start ("!g maps") or end ("cute cats !yt"), with the remaining search
     * terms. [symbol] is the user's trigger symbol.
     */
    fun parse(query: String, shortcuts: List<WebShortcut>, symbol: String = "!"): Use? {
        val q = query.trim()
        if (symbol.isEmpty() || !q.contains(symbol)) return null
        val tokens = q.split(Regex("\\s+"))
        fun find(token: String) = if (token.startsWith(symbol) && token.length > symbol.length) {
            val key = token.substring(symbol.length).lowercase()
            shortcuts.firstOrNull { it.key.equals(key, ignoreCase = true) }
        } else null
        find(tokens.first())?.let { return Use(it, tokens.drop(1).joinToString(" ")) }
        if (tokens.size > 1) find(tokens.last())?.let { return Use(it, tokens.dropLast(1).joinToString(" ")) }
        return null
    }

    /** Shortcuts whose key starts with what follows the symbol, for completing "!y" to "!yt". */
    fun completions(query: String, shortcuts: List<WebShortcut>, symbol: String = "!"): List<WebShortcut> {
        val q = query.trim()
        if (!q.startsWith(symbol) || q.contains(' ')) return emptyList()
        val typed = q.substring(symbol.length).lowercase()
        return shortcuts.filter { it.key.lowercase().startsWith(typed) || it.name.lowercase().startsWith(typed) }
    }

    /** A custom shortcut, or null when its address fails [SafeUrl] or its key is unusable. */
    fun custom(key: String, name: String, template: String, appPackage: String? = null): WebShortcut? {
        val k = key.trim().trimStart('!').lowercase()
        if (k.isEmpty() || k.length > 16 || !k.all { it.isLetterOrDigit() }) return null
        val safe = SafeUrl.template(template) ?: return null
        return WebShortcut(k, name.trim().ifEmpty { SafeUrl.displayName(safe.template) }, safe.template, appPackage = appPackage)
    }
}

private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")
