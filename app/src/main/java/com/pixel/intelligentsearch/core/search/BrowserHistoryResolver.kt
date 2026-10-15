package com.pixel.intelligentsearch.core.search

import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.pixel.intelligentsearch.core.security.CustomSearchUrl

/**
 * Works out where "Browser History" should take the user for their search engine.
 *
 * Engines that keep an account search history get their own history page. Private engines (DuckDuckGo, Brave
 * Search, Startpage, Ecosia, Tor) keep none, so the button opens the app that does hold the user's history:
 * the engine's own browser if installed, otherwise the default browser, with a note saying why. A custom
 * engine is matched by its host to a known engine; unknown hosts fall back to the default browser. Only fixed,
 * known https pages are ever opened from here; nothing from the custom address is turned into a history URL.
 */
object BrowserHistoryResolver {

    sealed interface Destination {
        val subtitle: String

        /** A history page on the web, opened in a browser. */
        data class WebPage(val uri: Uri, override val subtitle: String) : Destination

        /** An installed app holds the history; [note] explains when that isn't the engine's own history. */
        data class App(val packageName: String, override val subtitle: String, val note: String?) : Destination

        /** Tor Browser isn't installed: offer to install it. */
        data object InstallTor : Destination {
            override val subtitle = "Open Tor Browser."
        }

        /** Nothing suitable is installed. */
        data class Unavailable(override val subtitle: String, val message: String) : Destination
    }

    private enum class Engine(val label: String) {
        GOOGLE("Google"), BING("Bing"), DUCKDUCKGO("DuckDuckGo"), BRAVE("Brave Search"),
        STARTPAGE("Startpage"), ECOSIA("Ecosia"), TOR("Tor Browser"), OTHER("")
    }

    // Kept as the page the app has always opened for Google: My Activity, which holds Search and (when Chrome
    // sync uses Web & App Activity) Chrome history.
    private val GOOGLE_HISTORY = "https://myactivity.google.com/myactivity?product=6".toUri()
    private val BING_HISTORY = "https://www.bing.com/profile/history".toUri()

    private const val DUCKDUCKGO_APP = "com.duckduckgo.mobile.android"
    private const val BRAVE_APP = "com.brave.browser"
    private val TOR_APPS = listOf("org.torproject.torbrowser", "org.torproject.torbrowser_alpha")

    /**
     * [isInstalled] and [defaultBrowser] are injected so the decision can be tested; [forContext] supplies the
     * real PackageManager lookups.
     */
    fun resolve(
        searchEngine: String,
        customTemplate: String,
        isInstalled: (String) -> Boolean,
        defaultBrowser: () -> Pair<String, String>?
    ): Destination {
        val engine = engineFor(searchEngine, customTemplate)
        fun privateEngine(appPackage: String?, name: String): Destination {
            val note = "$name doesn't keep a search history. Your history is in your browser."
            if (appPackage != null && isInstalled(appPackage)) {
                return Destination.App(appPackage, "Open the $name app.", "$name doesn't keep a search history in your account.")
            }
            val browser = defaultBrowser()
                ?: return Destination.Unavailable("$name doesn't keep a search history.", note)
            return Destination.App(browser.first, "Open ${browser.second}; $name keeps no history.", note)
        }
        return when (engine) {
            Engine.GOOGLE -> Destination.WebPage(GOOGLE_HISTORY, "View Your Chrome and Google History.")
            Engine.BING -> Destination.WebPage(BING_HISTORY, "View Your Bing History.")
            Engine.DUCKDUCKGO -> privateEngine(DUCKDUCKGO_APP, engine.label)
            Engine.BRAVE -> privateEngine(BRAVE_APP, engine.label)
            Engine.STARTPAGE, Engine.ECOSIA -> privateEngine(null, engine.label)
            Engine.TOR -> TOR_APPS.firstOrNull(isInstalled)
                ?.let { Destination.App(it, "Open Tor Browser.", "Tor Browser deletes its history when it closes.") }
                ?: Destination.InstallTor
            Engine.OTHER -> {
                val browser = defaultBrowser()
                    ?: return Destination.Unavailable("View Your Browser History.", "No browser is set as default.")
                Destination.App(browser.first, "Open ${browser.second} to view your history.", null)
            }
        }
    }

    private fun engineFor(searchEngine: String, customTemplate: String): Engine = when (searchEngine) {
        "Google" -> Engine.GOOGLE
        "Bing" -> Engine.BING
        "DuckDuckGo" -> Engine.DUCKDUCKGO
        "Tor Project", "Tor Browser" -> Engine.TOR
        "Custom" -> {
            val host = (CustomSearchUrl.validate(customTemplate) as? CustomSearchUrl.Validation.Valid)?.host.orEmpty()
            fun isSite(domain: String) = host == domain || host.endsWith(".$domain")
            when {
                isSite("google.com") -> Engine.GOOGLE
                isSite("bing.com") -> Engine.BING
                isSite("duckduckgo.com") -> Engine.DUCKDUCKGO
                isSite("search.brave.com") -> Engine.BRAVE
                isSite("startpage.com") -> Engine.STARTPAGE
                isSite("ecosia.org") -> Engine.ECOSIA
                else -> Engine.OTHER
            }
        }
        else -> Engine.GOOGLE
    }

    fun forContext(context: Context, searchEngine: String, customTemplate: String): Destination {
        val pm = context.packageManager
        return resolve(
            searchEngine,
            customTemplate,
            isInstalled = { pkg -> pm.getLaunchIntentForPackage(pkg) != null },
            defaultBrowser = { defaultBrowser(pm) }
        )
    }

    /** The default browser's package and label, or null when none is set (the chooser would appear). */
    private fun defaultBrowser(pm: PackageManager): Pair<String, String>? {
        val probe = CustomSearchUrl.browsableViewIntent("https://example.com".toUri())
        val info = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return null
        // "android" is the system chooser, shown when no default browser is chosen.
        if (info.packageName == "android" || pm.getLaunchIntentForPackage(info.packageName) == null) return null
        return info.packageName to info.applicationInfo.loadLabel(pm).toString()
    }

    /** The intent that opens [destination], or null for [Destination.InstallTor] and [Destination.Unavailable]. */
    fun intentFor(context: Context, destination: Destination): Intent? = when (destination) {
        is Destination.WebPage -> CustomSearchUrl.browsableViewIntent(destination.uri)
        is Destination.App -> context.packageManager.getLaunchIntentForPackage(destination.packageName)
        Destination.InstallTor, is Destination.Unavailable -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
