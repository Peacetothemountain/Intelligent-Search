package com.pixel.intelligentsearch.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Voice search listens in the first of the user's languages the speech engine knows, rather than leaving the engine to
 * fail on the phone's first language when it has no model for it.
 */
class SpeechLanguageTest {

    private val onDevice = listOf("de-DE", "en-AU", "en-GB", "en-IN", "en-US", "es-ES", "fr-FR", "cmn-Hans-CN")

    private fun pick(vararg wanted: String, supported: List<String> = onDevice) =
        SpeechLanguage.pick(wanted.map(Locale::forLanguageTag), supported)

    @Test
    fun aLanguageTheEngineLacksFallsToTheUsersNextOne() {
        // Albanian first, English second: the on-device engine has no Albanian.
        assertEquals("en-US", pick("sq-AL", "en-US"))
    }

    @Test
    fun noneOfTheUsersLanguagesMeansNone() {
        assertNull(pick("sq-AL"))
    }

    @Test
    fun theUsersRegionWinsThenTheLanguagesUsualOne() {
        assertEquals("en-GB", pick("en-GB"))
        // English for a region the engine has no model for: the usual English, not whichever is listed first.
        assertEquals("en-US", pick("en-AL"))
        assertEquals("de-DE", pick("de-AT"))
    }

    @Test
    fun mandarinMatchesGooglesName() {
        assertEquals("cmn-Hans-CN", pick("zh-CN"))
    }

    @Test
    fun theOnlineEnginesAlbanianIsUsed() {
        assertEquals("sq-AL", pick("sq-AL", "en-US", supported = onDevice + "sq-AL"))
    }
}
