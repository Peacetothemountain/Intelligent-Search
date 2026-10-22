package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Opens web addresses the user typed, tapped or copied. Only http(s) addresses are opened, only by browsable
 * activities, and when Tor is the search engine only by Tor Browser: a Tor user's address must never fall
 * through to a normal browser, which would reveal it and their IP address.
 */
object SafeWebLaunch {

    val TOR_BROWSER_PACKAGES = listOf("org.torproject.torbrowser", "org.torproject.torbrowser_alpha")

    sealed interface Launch {
        data class Open(val intent: Intent) : Launch
        /** Tor is the engine but Tor Browser isn't installed; offer to install it rather than use another browser. */
        data object NeedsTorBrowser : Launch
        data object NotAWebAddress : Launch
    }

    private const val TOR_SEARCH_URL = "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/?q="

    /** A search for [query] on DuckDuckGo's onion service, opened only in Tor Browser. */
    fun torSearch(context: Context, query: String): Launch {
        val tor = installedTorBrowser(context) ?: return Launch.NeedsTorBrowser
        return Launch.Open(
            CustomSearchUrl.browsableViewIntent((TOR_SEARCH_URL + android.net.Uri.encode(query)).toUri())
                .setPackage(tor)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    fun installedTorBrowser(context: Context): String? =
        TOR_BROWSER_PACKAGES.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun forAddress(context: Context, address: String, searchEngine: String): Launch {
        val trimmed = address.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.isISOControl() }) return Launch.NotAWebAddress
        val hasWebScheme = trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)
        // "javascript:", "intent:", "file:" and the like are not addresses to open; "example.com:8080" is.
        if (!hasWebScheme && SCHEME.containsMatchIn(trimmed) && !trimmed.substringAfter(':').takeWhile { it != '/' }.all(Char::isDigit)) {
            return Launch.NotAWebAddress
        }
        val uri = (if (hasWebScheme) trimmed else "https://$trimmed").toUri()
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return Launch.NotAWebAddress
        }
        val intent = CustomSearchUrl.browsableViewIntent(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (NetworkPolicy.isTorEngine(searchEngine)) {
            intent.setPackage(installedTorBrowser(context) ?: return Launch.NeedsTorBrowser)
        }
        return Launch.Open(intent)
    }
}
