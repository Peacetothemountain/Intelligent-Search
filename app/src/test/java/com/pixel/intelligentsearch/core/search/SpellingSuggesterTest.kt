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
    fun onlyTyposWithARecommendedFixCount() {
        val typo = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
        val recommended = SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS
        assertTrue(SpellingSuggester.isObviousTypo(typo or recommended))
        assertFalse(SpellingSuggester.isObviousTypo(typo))
        assertFalse(SpellingSuggester.isObviousTypo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY))
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
