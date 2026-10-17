package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typo-tolerant app search as people actually type: part of a name, with a slip in it. The index must offer the
 * app as a candidate, and the scoring must keep it (a zero score drops it from the results).
 */
class FuzzyTypingTest {

    private val apps = listOf("YouTube", "Spotify", "Instagram", "WhatsApp", "Calculator", "Google Maps", "Gmail", "Netflix", "Settings")

    private val index = RadixTreeIndex<String>().apply {
        for (app in apps) {
            insert(app.lowercase(), app)
            for (word in app.lowercase().split(" ")) insert(word, app)
        }
    }

    private fun found(query: String): String? {
        val candidates = index.searchFuzzy(query, maxDistance = SearchScoringEngine.fuzzyEditsFor(query.length), limit = 20).map { it.value }.distinct()
        val meta = SearchScoringEngine.ScoringMetadata(domain = SearchScoringEngine.EntityDomain.APPLICATION)
        return candidates
            .map { it to SearchScoringEngine.evaluateScore(query, it, meta).totalScore }
            .filter { it.second > 0f }
            .maxByOrNull { it.second }
            ?.first
    }

    @Test
    fun fullNamesWithASlipAreFound() {
        assertEquals("YouTube", found("yuotube"))
        assertEquals("Spotify", found("spotfy"))
        assertEquals("Instagram", found("instagarm"))
        assertEquals("Calculator", found("calculater"))
        assertEquals("Netflix", found("netflx"))
    }

    @Test
    fun partlyTypedNamesWithASlipAreFound() {
        assertEquals("YouTube", found("yuotu"))
        assertEquals("Spotify", found("sptoif"))
        assertEquals("Instagram", found("instgr"))
        assertEquals("WhatsApp", found("whtas"))
        assertEquals("Calculator", found("calcul8"))
        assertEquals("Google Maps", found("gogle ma"))
    }

    @Test
    fun shortOrUnrelatedTextDoesNotMatchEverything() {
        // Two letters are too short to guess at, and a word unlike every app finds nothing.
        assertEquals(null, found("zq"))
        assertEquals(null, found("xylophone"))
    }

    @Test
    fun allowedSlipsGrowWithTheQuery() {
        assertEquals(0, SearchScoringEngine.fuzzyEditsFor(2))
        assertEquals(1, SearchScoringEngine.fuzzyEditsFor(4))
        assertTrue(SearchScoringEngine.fuzzyEditsFor(8) >= 2)
    }
}
