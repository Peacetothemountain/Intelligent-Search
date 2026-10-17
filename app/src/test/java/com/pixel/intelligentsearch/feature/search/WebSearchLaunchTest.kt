package com.pixel.intelligentsearch.feature.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.security.SafeWebLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WebSearchLaunchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun intentFor(engine: String, query: String, customUrl: String = ""): Intent {
        val launch = webSearchLaunch(context, engine, customUrl, query)
        assertTrue("expected Open, got $launch", launch is SafeWebLaunch.Launch.Open)
        return (launch as SafeWebLaunch.Launch.Open).intent
    }

    private fun installBrowser() {
        val component = ComponentName("org.example.browser", "org.example.browser.Main")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("https")
        })
    }

    @Test
    fun withoutABrowserNamedEnginesFallBackToTheSystemSearch() {
        for (engine in listOf("DuckDuckGo", "Bing", "Google")) {
            val intent = intentFor(engine, "cats & dogs")
            assertEquals(Intent.ACTION_WEB_SEARCH, intent.action)
            assertEquals("cats & dogs", intent.getStringExtra(android.app.SearchManager.QUERY))
            assertNull(intent.`package`)
        }
    }

    @Test
    fun withABrowserDuckDuckGoOpensItsEncodedSearchPage() {
        installBrowser()
        val intent = intentFor("DuckDuckGo", "cats & dogs")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://duckduckgo.com/?q=cats%20%26%20dogs", intent.dataString)
    }

    @Test
    fun aValidCustomAddressOpensWithTheQueryInserted() {
        val intent = intentFor("Custom", "kotlin", customUrl = "https://search.example.org/?q=%s")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.dataString!!.startsWith("https://search.example.org/"))
        assertTrue(intent.dataString!!.contains("kotlin"))
    }

    @Test
    fun anUnsafeCustomAddressFallsBackToTheSystemSearch() {
        for (bad in listOf("javascript:alert(1)//%s", "http://plain.example.org/?q=%s", "")) {
            assertEquals(Intent.ACTION_WEB_SEARCH, intentFor("Custom", "kotlin", customUrl = bad).action)
        }
    }

    @Test
    fun torWithoutTorBrowserAsksForItInsteadOfUsingAnotherBrowser() {
        installBrowser()
        assertEquals(SafeWebLaunch.Launch.NeedsTorBrowser, webSearchLaunch(context, "Tor Browser", "", "q"))
    }
}
