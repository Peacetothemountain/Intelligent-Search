package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.security.NetworkPolicy.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The one network policy every feature, settings subtitle and the system search provider ask before going online. */
@RunWith(RobolectricTestRunner::class)
class NetworkPolicyTest {

    private val prefs: SharedPreferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun reset() {
        prefs.edit().clear().commit()
    }

    @Test
    fun torIsRecognisedByExactNameInAnyCase() {
        assertTrue(NetworkPolicy.isTorEngine("Tor Project"))
        assertTrue(NetworkPolicy.isTorEngine("tor browser"))
        assertTrue(NetworkPolicy.isTorEngine(" TOR PROJECT "))
        // A custom engine whose name merely contains "tor" is not Tor.
        assertFalse(NetworkPolicy.isTorEngine("Play Store"))
        assertFalse(NetworkPolicy.isTorEngine("Google"))
    }

    @Test
    fun suggestionsNeedWebSuggestionsAndAnEngineThatSuggests() {
        prefs.edit().put(SettingsManager.SEARCH_WEB, true).put(SettingsManager.SEARCH_WEB_SUGGESTIONS, true).commit()
        assertTrue(NetworkPolicy.webSuggestionsAllowed(prefs))
        for (engine in listOf("Tor Project", "Tor Browser", "Custom")) {
            prefs.edit().put(SettingsManager.SEARCH_ENGINE, engine).commit()
            assertFalse(engine, NetworkPolicy.webSuggestionsAllowed(prefs))
        }
        prefs.edit().put(SettingsManager.SEARCH_ENGINE, "DuckDuckGo").put(SettingsManager.SEARCH_WEB, false).commit()
        assertFalse(NetworkPolicy.webSuggestionsAllowed(prefs))
        prefs.edit().put(SettingsManager.SEARCH_WEB, true).put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
        assertFalse(NetworkPolicy.webSuggestionsAllowed(prefs))
    }

    @Test
    fun webAnswersFollowTheirSwitchWebWebSuggestionsAndTor() {
        prefs.edit().put(SettingsManager.SEARCH_WEB, true).put(SettingsManager.SEARCH_WEB_SUGGESTIONS, true)
            .put(SettingsManager.DICTIONARY_ANSWERS, true).commit()
        assertNull(NetworkPolicy.webAnswersBlock(prefs))
        assertTrue(NetworkPolicy.webAnswersAllowed(prefs, SettingsManager.DICTIONARY_ANSWERS))
        // Its own switch.
        assertFalse(NetworkPolicy.webAnswersAllowed(prefs, SettingsManager.WEATHER_ANSWERS))

        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
        assertEquals(Block.WEB_SUGGESTIONS_OFF, NetworkPolicy.webAnswersBlock(prefs))
        assertFalse(NetworkPolicy.webAnswersAllowed(prefs, SettingsManager.DICTIONARY_ANSWERS))

        prefs.edit().put(SettingsManager.SEARCH_WEB, false).commit()
        assertEquals(Block.WEB_OFF, NetworkPolicy.webAnswersBlock(prefs))

        // Tor outranks the rest: it is the reason the subtitle gives.
        prefs.edit().put(SettingsManager.SEARCH_ENGINE, "Tor Project").commit()
        assertEquals(Block.TOR, NetworkPolicy.webAnswersBlock(prefs))
        // A custom engine has no suggestions, yet answers don't depend on the engine's suggestion service.
        assertNull(NetworkPolicy.webAnswersBlock(web = true, webSuggestions = true, engine = "Custom"))
    }

    @Test
    fun aiNeedsWebAndNotTorButNotWebSuggestions() {
        prefs.edit().put(SettingsManager.SEARCH_WEB, true).put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
        assertNull(NetworkPolicy.aiBlock(prefs))
        prefs.edit().put(SettingsManager.SEARCH_WEB, false).commit()
        assertEquals(Block.WEB_OFF, NetworkPolicy.aiBlock(prefs))
        prefs.edit().put(SettingsManager.SEARCH_WEB, true).put(SettingsManager.SEARCH_ENGINE, "Tor Browser").commit()
        assertEquals(Block.TOR, NetworkPolicy.aiBlock(prefs))
    }

    @Test
    fun ratesNeedTheCalculatorAndLiveRatesAndNotTor() {
        prefs.edit().put(SettingsManager.SEARCH_CALCULATOR, true).put(SettingsManager.LIVE_CURRENCY_RATES, true).commit()
        assertTrue(NetworkPolicy.liveRatesAllowed(prefs))
        prefs.edit().put(SettingsManager.SEARCH_CALCULATOR, false).commit()
        assertFalse(NetworkPolicy.liveRatesAllowed(prefs))
        prefs.edit().put(SettingsManager.SEARCH_CALCULATOR, true).put(SettingsManager.SEARCH_ENGINE, "Tor Project").commit()
        assertFalse(NetworkPolicy.liveRatesAllowed(prefs))
    }

    @Test
    fun userFetchesStopOnlyForTor() {
        prefs.edit().put(SettingsManager.SEARCH_WEB, false).commit()
        assertTrue(NetworkPolicy.userFetchAllowed(prefs))
        prefs.edit().put(SettingsManager.SEARCH_ENGINE, "Tor Project").commit()
        assertFalse(NetworkPolicy.userFetchAllowed(prefs))
    }

    @Test
    fun everyBlockHasASubtitleSentence() {
        for (block in Block.entries) {
            val subtitle = NetworkPolicy.blockSubtitle(block)
            assertTrue(subtitle, subtitle.endsWith("."))
        }
        assertEquals("Needs Web On in Search Sources.", NetworkPolicy.blockSubtitle(Block.WEB_OFF))
    }
}
