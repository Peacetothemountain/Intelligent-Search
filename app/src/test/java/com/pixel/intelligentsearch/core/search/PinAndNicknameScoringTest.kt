package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pin bonus and nickname matching in the scoring matrix. */
class PinAndNicknameScoringTest {

    private val now = 1_000_000_000_000L
    private val app = SearchScoringEngine.EntityDomain.APPLICATION

    private fun score(
        query: String,
        title: String,
        pinned: Boolean = false,
        launches: Int = 0,
        lastUsed: Long = 0L,
        nicknames: List<String> = emptyList()
    ) = SearchScoringEngine.evaluateScore(
        query = query,
        targetTitle = title,
        metadata = SearchScoringEngine.ScoringMetadata(launchCount = launches, lastUsedTimestampMs = lastUsed, isPinned = pinned, domain = app),
        currentTimeMs = now,
        nicknames = nicknames
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

    @Test
    fun aNicknameScoresAboveTheSameMatchOnATitle() {
        val exactTitle = score("bank", "Bank")
        val exactNickname = score("bank", "Cheddar", nicknames = listOf("Bank"))
        assertTrue(exactNickname > exactTitle)
        val prefixNickname = score("ba", "Cheddar", nicknames = listOf("bank"))
        assertTrue(prefixNickname > score("ba", "Bandcamp"))
    }

    @Test
    fun aNicknameMatchesOnlyWholeOrByItsStart() {
        assertEquals(0f, score("ank", "Cheddar", nicknames = listOf("bank")))
        assertEquals(0f, score("bnak", "Cheddar", nicknames = listOf("bank")))
    }

    @Test
    fun withoutPinsOrNicknamesScoresAreUnchanged() {
        assertEquals(score("chr", "Chrome"), score("chr", "Chrome", nicknames = emptyList()))
    }
}
