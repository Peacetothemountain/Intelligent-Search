package com.pixel.intelligentsearch.core.search

import android.view.textservice.SuggestionsInfo
import com.pixel.intelligentsearch.core.search.SpellingSuggester.Correction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Did you mean" must fix obvious typos only, and never touch shortcuts, addresses or sums. */
class SpellingSuggesterTest {

    @Test
    fun correctionsReplaceTheMisspelledWordsKeepingCase() {
        val query = "best resturant neer me"
        val fixed = SpellingSuggester.applyCorrections(
            query,
            listOf(Correction(5, 9, "restaurant"), Correction(15, 4, "near"))
        )
        assertEquals("best restaurant near me", fixed)
        assertEquals("Recieve" .let { SpellingSuggester.applyCorrections(it, listOf(Correction(0, 7, "receive"))) }, "Receive")
    }

    @Test
    fun noCorrectionOrTheSameTextOffersNothing() {
        assertNull(SpellingSuggester.applyCorrections("weather today", emptyList()))
        assertNull(SpellingSuggester.applyCorrections("Paris", listOf(Correction(0, 5, "paris"))))
    }

    @Test
    fun wordsFlaggedAsTyposCountAndKnownWordsDoNot() {
        val typo = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
        val recommended = SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS
        assertTrue(SpellingSuggester.isTypo(typo))
        assertTrue(SpellingSuggester.isTypo(typo or recommended))
        assertFalse(SpellingSuggester.isTypo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY))
        assertFalse(SpellingSuggester.isTypo(0))
    }

    @Test
    fun theOwnersExampleIsFixedWordByWord() {
        val query = "Chec the timee"
        val fixed = SpellingSuggester.applyCorrections(query, listOf(Correction(0, 4, "check"), Correction(9, 5, "time")))
        assertEquals("Check the time", fixed)
    }

    @Test
    fun zoneAbbreviationsAndUnitsAreNeverCorrected() {
        // "3pm pst in ist" was answered and still offered "Did you mean 3pm post in ist".
        assertNull(SpellingSuggester.applyCorrections("3pm pst in ist", listOf(Correction(4, 3, "post"))))
        assertNull(SpellingSuggester.applyCorrections("5 km to mi", listOf(Correction(2, 2, "me"))))
        assertNull(SpellingSuggester.applyCorrections("10 m2 to ft2", listOf(Correction(10, 3, "ft"))))
        // Other words in the same query are still fixed.
        assertEquals(
            "time in pst now",
            SpellingSuggester.applyCorrections("tiem in pst now", listOf(Correction(0, 4, "time"), Correction(8, 3, "post")))
        )
    }

    @Test
    fun wordsWithDigitsAreNeverCorrected() {
        // Gboard's suggestions as seen on the phone: "Did you mean trains to london at same" and the like.
        assertNull(SpellingSuggester.applyCorrections("trains to london at 8am", listOf(Correction(20, 3, "same"))))
        assertNull(SpellingSuggester.applyCorrections("call mom at 5pm", listOf(Correction(12, 3, "prom"))))
        assertNull(SpellingSuggester.applyCorrections("4pm meeting notes", listOf(Correction(0, 3, "spam"))))
        assertNull(SpellingSuggester.applyCorrections("ps5 price", listOf(Correction(0, 3, "past"))))
        assertNull(SpellingSuggester.applyCorrections("covid19 symptoms", listOf(Correction(0, 7, "covering"))))
        assertNull(SpellingSuggester.applyCorrections("open at 10am", listOf(Correction(8, 4, "10 am"))))
        // A real typo beside one is still fixed, and the time kept.
        assertEquals(
            "meeting at 8am",
            SpellingSuggester.applyCorrections("meetng at 8am", listOf(Correction(0, 6, "meeting"), Correction(10, 3, "same")))
        )
    }

    @Test
    fun aQueryAnsweredOnTheDeviceIsNotChecked() {
        assertTrue(SpellingSuggester.shouldCheck("3pm pst in ist", "!", isWebAddress = false))
        assertFalse(SpellingSuggester.shouldCheck("3pm pst in ist", "!", isWebAddress = false, answered = true))
    }

    @Test
    fun shortcutsAddressesSumsAndShortTextAreNotChecked() {
        assertTrue(SpellingSuggester.shouldCheck("wether tomorow", "!", isWebAddress = false))
        assertFalse(SpellingSuggester.shouldCheck("!yt cats", "!", isWebAddress = false))
        assertFalse(SpellingSuggester.shouldCheck("cats !w", "!", isWebAddress = false))
        assertFalse(SpellingSuggester.shouldCheck("/maps pizza", "/", isWebAddress = false))
        assertFalse(SpellingSuggester.shouldCheck("exmaple.com", "!", isWebAddress = true))
        assertFalse(SpellingSuggester.shouldCheck("12*4+7", "!", isWebAddress = false))
        assertFalse(SpellingSuggester.shouldCheck("hi", "!", isWebAddress = false))
    }
}
