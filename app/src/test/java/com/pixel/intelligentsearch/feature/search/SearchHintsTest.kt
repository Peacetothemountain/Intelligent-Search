package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHintsTest {

    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun hintsShowOnlyThroughTheFirstWeek() {
        val installed = 1_000_000_000_000L
        assertTrue(SearchHints.inFirstWeek(installed, installed))
        assertTrue(SearchHints.inFirstWeek(installed, installed + 6 * day + day - 1))
        assertFalse(SearchHints.inFirstWeek(installed, installed + 7 * day))
        // A clock set back before the install, or a phone that won't say when it was: the plain hint.
        assertFalse(SearchHints.inFirstWeek(installed, installed - 1))
        assertFalse(SearchHints.inFirstWeek(0L, installed))
    }

    @Test
    fun theSumsAreOnlyOfferedWhileTheCalculatorIsOn() {
        assertTrue("18% of 64" in SearchHints.examples(calculatorOn = true))
        val without = SearchHints.examples(calculatorOn = false)
        assertFalse("18% of 64" in without)
        assertFalse("20 usd in eur" in without)
        assertTrue("10 min timer" in without)
    }

    @Test
    fun searchStaysUpFirstThenEachExampleTakesItsTurn() {
        val examples = SearchHints.examples(calculatorOn = true)
        // Opening: the plain hint, standing still.
        assertEquals(0, SearchHints.frame(0f).slot)
        assertEquals(0f, SearchHints.frame(1.5f).change, 0f)
        assertNull(SearchHints.shown(0, examples, opening = 0))

        // It gives way over the change, then the first example stays.
        val leaving = SearchHints.frame(SearchHints.FIRST_SECONDS + SearchHints.CHANGE_SECONDS / 2f)
        assertEquals(0, leaving.slot)
        assertEquals(0.5f, leaving.change, 0.01f)
        val first = SearchHints.frame(SearchHints.FIRST_SECONDS + SearchHints.CHANGE_SECONDS + 1f)
        assertEquals(1, first.slot)
        assertEquals(0f, first.change, 0f)
        assertEquals(examples[0], SearchHints.shown(1, examples, opening = 0))

        // One example per hold from then on.
        val second = SearchHints.frame(SearchHints.FIRST_SECONDS + 2 * SearchHints.CHANGE_SECONDS + SearchHints.HOLD_SECONDS + 1f)
        assertEquals(2, second.slot)
        assertEquals(examples[1], SearchHints.shown(2, examples, opening = 0))
    }

    @Test
    fun theExamplesGoRoundAndEachOpeningStartsFromTheNext() {
        val examples = SearchHints.examples(calculatorOn = true)
        assertEquals(examples[0], SearchHints.shown(examples.size + 1, examples, opening = 0))
        assertEquals(examples[1], SearchHints.shown(1, examples, opening = 1))
        assertEquals(examples[0], SearchHints.shown(1, examples, opening = examples.size))
    }

    @Test
    fun theChangeNeverRunsBackward() {
        var previous = SearchHints.frame(0f)
        var t = 0f
        while (t < 30f) {
            t += 1f / 60f
            val now = SearchHints.frame(t)
            assertTrue("at $t", now.slot > previous.slot || (now.slot == previous.slot && now.change >= previous.change))
            previous = now
        }
    }
}
