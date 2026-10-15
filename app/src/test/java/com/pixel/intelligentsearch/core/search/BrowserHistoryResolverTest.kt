package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.search.BrowserHistoryResolver.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserHistoryResolverTest {

    private val chrome = "com.android.chrome" to "Chrome"

    private fun resolve(engine: String, custom: String = "", installed: Set<String> = emptySet(), browser: Pair<String, String>? = chrome) =
        BrowserHistoryResolver.resolve(engine, custom, { it in installed }, { browser })

    @Test
    fun accountEnginesOpenTheirHistoryPages() {
        assertEquals("www.bing.com", (resolve("Bing") as Destination.WebPage).uri.host)
        assertEquals("myactivity.google.com", (resolve("Google") as Destination.WebPage).uri.host)
    }

    @Test
    fun privateEnginesOpenTheirAppOrTheDefaultBrowserWithANote() {
        val ddgApp = resolve("DuckDuckGo", installed = setOf("com.duckduckgo.mobile.android")) as Destination.App
        assertEquals("com.duckduckgo.mobile.android", ddgApp.packageName)
        val ddgWeb = resolve("DuckDuckGo") as Destination.App
        assertEquals("com.android.chrome", ddgWeb.packageName)
        assertTrue(ddgWeb.note!!.contains("doesn't keep"))
        assertTrue(resolve("DuckDuckGo", browser = null) is Destination.Unavailable)
    }

    @Test
    fun torOpensTorBrowserOrOffersInstall() {
        assertEquals(Destination.InstallTor, resolve("Tor Project"))
        assertEquals("org.torproject.torbrowser", (resolve("Tor Project", installed = setOf("org.torproject.torbrowser")) as Destination.App).packageName)
    }

    @Test
    fun customEnginesAreMatchedByHostAndNeverOpenedAsHistory() {
        assertEquals("www.bing.com", (resolve("Custom", "https://www.bing.com/search?q=%s") as Destination.WebPage).uri.host)
        assertTrue(resolve("Custom", "https://www.startpage.com/do/search?q=%s") is Destination.App)
        // Look-alike hosts don't count as the real engine.
        assertTrue(resolve("Custom", "https://bing.com.evil.example/?q=%s") is Destination.App)
        // An unknown or invalid custom address only ever leads to the default browser.
        assertEquals("com.android.chrome", (resolve("Custom", "https://searx.example.net/?q=%s") as Destination.App).packageName)
        assertEquals("com.android.chrome", (resolve("Custom", "javascript:alert(1)") as Destination.App).packageName)
    }
}
