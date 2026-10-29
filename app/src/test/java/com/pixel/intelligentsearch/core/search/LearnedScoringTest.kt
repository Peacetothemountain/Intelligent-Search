package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The learned-pick and starred-contact terms in the scoring matrix, and that a pin still outranks them. */
class LearnedScoringTest {

    private val now = 1_000_000_000_000L

    private fun breakdown(
        query: String,
        title: String,
        learned: Float = 0f,
        favorite: Boolean = false,
        pinned: Boolean = false,
        launches: Int = 0,
        lastUsed: Long = 0L,
        domain: SearchScoringEngine.EntityDomain = SearchScoringEngine.EntityDomain.APPLICATION
    ) = SearchScoringEngine.evaluateScore(
        query = query,
        targetTitle = title,
        metadata = SearchScoringEngine.ScoringMetadata(
            launchCount = launches, lastUsedTimestampMs = lastUsed, isPinned = pinned, domain = domain,
            learnedWeight = learned, isFavorite = favorite
        ),
        currentTimeMs = now
    )

    @Test
    fun onePickLiftsAShortPrefixPastALongerOne() {
        // "ca": Camera leads Calculator on the match alone; one pick of Calculator turns that round.
        assertTrue(breakdown("ca", "Camera").totalScore > breakdown("ca", "Calculator").totalScore)
        assertTrue(breakdown("ca", "Calculator", learned = 1f).totalScore > breakdown("ca", "Camera").totalScore)
    }

    @Test
    fun onePickDoesNotOutweighAnExactName() {
        assertTrue(breakdown("maps", "Maps").totalScore > breakdown("maps", "Maps Lite", learned = 1f).totalScore)
    }

    @Test
    fun picksAddLessEachAndStopAtACap() {
        val one = breakdown("ca", "Calculator", learned = 1f).learnedScore
        val two = breakdown("ca", "Calculator", learned = 2f).learnedScore
        val many = breakdown("ca", "Calculator", learned = 500f).learnedScore
        assertTrue(two > one)
        assertTrue(two - one < one)
        assertEquals(900f, many, 0.001f)
    }

    @Test
    fun picksNeedAMatch() {
        assertEquals(0f, breakdown("zzz", "Calculator", learned = 50f).totalScore, 0.001f)
    }

    @Test
    fun aStarredContactLeadsAnEqualMatch() {
        val contact = SearchScoringEngine.EntityDomain.CONTACT
        assertTrue(
            breakdown("sam", "Sam Brown", favorite = true, domain = contact).totalScore >
                breakdown("sam", "Sam Adams", domain = contact).totalScore
        )
    }

    @Test
    fun aPinnedWeakMatchOutscoresAnExactHabitualStarredOne() {
        val best = breakdown("chess", "chess", learned = 500f, favorite = true, launches = 100, lastUsed = now).totalScore
        val pinnedTypo = breakdown("chess", "Chesss", pinned = true).totalScore
        assertTrue("$pinnedTypo > $best", pinnedTypo > best)
    }
}
