package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pin bonus in the scoring matrix. */
class PinScoringTest {

    private val now = 1_000_000_000_000L
    private val app = SearchScoringEngine.EntityDomain.APPLICATION

    private fun score(
        query: String,
        title: String,
        pinned: Boolean = false,
        launches: Int = 0,
        lastUsed: Long = 0L
    ) = SearchScoringEngine.evaluateScore(
        query = query,
        targetTitle = title,
        metadata = SearchScoringEngine.ScoringMetadata(launchCount = launches, lastUsedTimestampMs = lastUsed, isPinned = pinned, domain = app),
        currentTimeMs = now
    ).totalScore

    @Test
    fun aPinnedWeakMatchOutscoresTheBestUnpinnedOne() {
        // An exact name used every day and just now: the most an unpinned result can reach.
        val best = score("chess", "chess", launches = 14, lastUsed = now)
        val pinnedTypo = score("chess", "Chesss", pinned = true)
        assertTrue("$pinnedTypo > $best", pinnedTypo > best)
    }

    @Test
    fun aPinDoesNothingWithoutAMatch() {
        assertEquals(0f, score("zzz", "Chess", pinned = true))
    }
}
