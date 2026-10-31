package com.pixel.intelligentsearch.feature.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.feature.search.quickAppSearchIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * With Tor as the search engine, nothing goes around it: the widget's News, Sports, Finance and Weather shortcuts and
 * the Chrome pill's search used to open Google's pages in a normal browser. They search through Tor Browser now, as
 * every web search does; without it the shortcuts offer Tor Browser and the Chrome pill opens Chrome without the query.
 */
@RunWith(RobolectricTestRunner::class)
class TorShortcutsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val topics = listOf("News", "Sports", "Finance", "Weather")

    @Before fun useTor() {
        SettingsManager.storedPrefs(context).edit().put(SettingsManager.SEARCH_ENGINE, "Tor Project").commit()
    }

    private fun installTorBrowser() {
        val tor = ComponentName("org.torproject.torbrowser", "org.torproject.torbrowser.App")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(tor)
            addIntentFilterForActivity(tor, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
    }

    private fun installChrome() {
        val chrome = ComponentName("com.android.chrome", "com.google.android.apps.chrome.Main")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(chrome)
            addIntentFilterForActivity(chrome, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
    }

    @Test
    fun theWidgetsTopicShortcutsSearchThroughTorBrowser() {
        installTorBrowser()
        for (topic in topics) {
            val intent = getShortcutIntent(context, topic)
            assertEquals(topic, "org.torproject.torbrowser", intent.`package`)
            assertTrue(topic, intent.dataString.orEmpty().contains(".onion/?q=$topic"))
        }
    }

    @Test
    fun withoutTorBrowserTheyOfferItRatherThanABrowser() {
        for (topic in topics) {
            val intent = getShortcutIntent(context, topic)
            assertEquals(topic, "market://details?id=org.torproject.torbrowser", intent.dataString)
        }
    }

    @Test
    fun theChromePillsSearchGoesThroughTorBrowser() {
        installTorBrowser()
        installChrome()
        val intent = quickAppSearchIntent(context, "com.android.chrome", "cats")!!
        assertEquals("org.torproject.torbrowser", intent.`package`)
        assertFalse(intent.dataString.orEmpty().contains("google.com"))
    }

    @Test
    fun withoutTorBrowserTheChromePillOpensChromeWithoutTheQuery() {
        installChrome()
        val intent = quickAppSearchIntent(context, "com.android.chrome", "cats")!!
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertFalse(intent.dataString.orEmpty().contains("cats"))
    }

    @Test
    fun withAnotherEngineTheyAreAsBefore() {
        SettingsManager.storedPrefs(context).edit().put(SettingsManager.SEARCH_ENGINE, "Google").commit()
        assertEquals("https://news.google.com", getShortcutIntent(context, "News").dataString)
        assertTrue(quickAppSearchIntent(context, "com.android.chrome", "cats")!!.dataString!!.startsWith("https://google.com/search"))
    }
}
