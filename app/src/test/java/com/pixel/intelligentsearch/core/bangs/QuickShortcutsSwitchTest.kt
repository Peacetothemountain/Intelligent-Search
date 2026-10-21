package com.pixel.intelligentsearch.core.bangs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "Enable Quick Web Shortcuts" off means no query is read as a shortcut and none are suggested. */
@RunWith(RobolectricTestRunner::class)
class QuickShortcutsSwitchTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsManager(context)
    private val bangs = SearchBangManager(context, settings)

    @Test
    fun switchedOffNoQueryIsAShortcut() {
        settings.set(SettingsManager.SEARCH_QUICK_SHORTCUTS, false)
        assertNull(bangs.parseBangQuery("!yt cats"))
        assertTrue(bangs.getBangSuggestions("!y").isEmpty())
    }

    @Test
    fun theSwitchTakesEffectAtOnceBothWays() {
        assertNotNull(bangs.parseBangQuery("!yt cats"))
        assertEquals("YouTube", bangs.getBangSuggestions("!y").first().name)

        settings.set(SettingsManager.SEARCH_QUICK_SHORTCUTS, false)
        assertNull(bangs.parseBangQuery("!yt cats"))
        assertTrue(bangs.getBangSuggestions("!y").isEmpty())

        settings.set(SettingsManager.SEARCH_QUICK_SHORTCUTS, true)
        assertEquals("cats", bangs.parseBangQuery("!yt cats")!!.extractedQuery)
    }
}
