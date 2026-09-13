package com.pixel.intelligentsearch.core.search

import org.junit.Assert.*
import org.junit.Test

class SearchScoringEngineTest {

    @Test
    fun testExactMatchScoresHigherThanPrefix() {
        val meta = SearchScoringEngine.ScoringMetadata(domain = SearchScoringEngine.EntityDomain.APPLICATION)

        val exactBreakdown = SearchScoringEngine.evaluateScore("spotify", "spotify", meta)
        val prefixBreakdown = SearchScoringEngine.evaluateScore("spot", "spotify", meta)

        assertTrue(
            "Exact match (${exactBreakdown.totalScore}) must be higher than prefix match (${prefixBreakdown.totalScore})",
            exactBreakdown.totalScore > prefixBreakdown.totalScore
        )
    }

    @Test
    fun testAcronymMatchScoring() {
        val meta = SearchScoringEngine.ScoringMetadata(domain = SearchScoringEngine.EntityDomain.APPLICATION)
        val ytBreakdown = SearchScoringEngine.evaluateScore("yt", "YouTube", meta)
        val waBreakdown = SearchScoringEngine.evaluateScore("wa", "WhatsApp", meta)

        assertTrue("Acronym score must be > 400", ytBreakdown.matchScore >= 400f)
        assertTrue("Acronym score must be > 400", waBreakdown.matchScore >= 400f)
    }

    @Test
    fun testRecencyDecay_halfLife() {
        val metaRecent = SearchScoringEngine.ScoringMetadata(
            lastUsedTimestampMs = 1_000_000_000L,
            domain = SearchScoringEngine.EntityDomain.APPLICATION
        )
        // 48 hours later (1 half-life)
        val halfLifeLaterMs = 1_000_000_000L + (48L * 60 * 60 * 1000)
        val breakdown1 = SearchScoringEngine.evaluateScore("app", "app", metaRecent, currentTimeMs = halfLifeLaterMs)

        // 96 hours later (2 half-lives)
        val twoHalfLivesLaterMs = 1_000_000_000L + (96L * 60 * 60 * 1000)
        val breakdown2 = SearchScoringEngine.evaluateScore("app", "app", metaRecent, currentTimeMs = twoHalfLivesLaterMs)

        assertTrue("Recency score at 1 half-life should be ~175", breakdown1.recencyScore in 170f..180f)
        assertTrue("Recency score at 2 half-lives should be ~87.5", breakdown2.recencyScore in 80f..95f)
        assertTrue("Score at 1 half-life must be greater than at 2 half-lives", breakdown1.recencyScore > breakdown2.recencyScore)
    }

    @Test
    fun testFrequencyLogarithmicScaling() {
        val metaLowFreq = SearchScoringEngine.ScoringMetadata(launchCount = 2, domain = SearchScoringEngine.EntityDomain.APPLICATION)
        val metaHighFreq = SearchScoringEngine.ScoringMetadata(launchCount = 100, domain = SearchScoringEngine.EntityDomain.APPLICATION)

        val low = SearchScoringEngine.evaluateScore("app", "app", metaLowFreq)
        val high = SearchScoringEngine.evaluateScore("app", "app", metaHighFreq)

        assertTrue(high.frequencyScore > low.frequencyScore)
        // Sub-linear scaling check: 100 launches vs 2 launches ratio should be ~4.2x, NOT 50x
        val ratio = high.frequencyScore / low.frequencyScore
        assertTrue("Frequency scaling should be logarithmic (~4.2), was $ratio", ratio < 6.0f)
    }
}
