package com.pixel.intelligentsearch.core.bangs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.forgetHeldListeners
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

    @Test
    fun theShortcutSettingsTakeEffectWithoutAListenerToKeep() {
        // In the release build the listener that cleared the cached switch, trigger symbol and shortcuts could be
        // garbage collected, leaving them as they were when the app started.
        assertEquals("YouTube", bangs.parseBangQuery("!yt cats")!!.bang.name)
        forgetHeldListeners(bangs)

        settings.set(SettingsManager.SEARCH_QUICK_SHORTCUTS, false)
        assertNull(bangs.parseBangQuery("!yt cats"))
        settings.set(SettingsManager.SEARCH_QUICK_SHORTCUTS, true)
        assertNotNull(bangs.parseBangQuery("!yt cats"))

        settings.set(SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL, "/")
        assertEquals("/", bangs.getTriggerSymbol())
        assertEquals("YouTube", bangs.parseBangQuery("/yt cats")!!.bang.name)

        settings.set(SettingsManager.DISABLED_WEB_SHORTCUTS, setOf("!yt"))
        assertNull(bangs.parseBangQuery("/yt cats"))
        assertTrue(bangs.getAllBangsSync().none { it.name == "YouTube" })
    }
}
