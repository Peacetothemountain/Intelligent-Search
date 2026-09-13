package com.pixel.intelligentsearch.core.search

import kotlin.math.ln
import kotlin.math.pow

/**
 * Dynamic Multi-Factor Recency & Relevance Scoring Matrix.
 * Computes deterministic, normalized relevance ranks by combining:
 * - Match Precision: Exact, Prefix, Word-Boundary, Acronym, Substring, Phonetic, Fuzzy
 * - Usage Frequency: Sub-linear logarithmic scaling
 * - Recency Decay: Exponential half-life decay function
 * - Pin/Favorite Weight: User-pinned preference multipliers
 * - Domain Weight: Intent priority weighting across entity domains
 */
object SearchScoringEngine {

    enum class EntityDomain(val weight: Float) {
        DIRECT_ACTION(2.2f),
        SYSTEM_TOGGLE(2.0f),
        APPLICATION(1.8f),
        APP_SHORTCUT(1.5f),
        CONTACT(1.3f),
        FILE(1.0f),
        WEB_SUGGESTION(0.7f)
    }

    data class ScoringMetadata(
        val launchCount: Int = 0,
        val lastUsedTimestampMs: Long = 0L,
        val isPinned: Boolean = false,
        val domain: EntityDomain = EntityDomain.APPLICATION
    )

    data class ScoreBreakdown(
        val totalScore: Float,
        val matchScore: Float,
        val frequencyScore: Float,
        val recencyScore: Float,
        val pinScore: Float,
        val domainMultiplier: Float
    )

    // Constants for Match Scoring
    private const val SCORE_EXACT_MATCH = 1000.0f
    private const val SCORE_PREFIX_MAX = 600.0f
    private const val SCORE_ACRONYM_MATCH = 500.0f
    private const val SCORE_SUBSTRING_MAX = 300.0f
    private const val SCORE_PHONETIC_MATCH = 250.0f
    private const val SCORE_FUZZY_BASE = 200.0f
    private const val PENALTY_PER_EDIT_DISTANCE = 90.0f

    // Frequency & Recency Parameters
    private const val FREQUENCY_WEIGHT = 120.0f
    private const val RECENCY_MAX_BONUS = 350.0f
    private const val PIN_BONUS = 500.0f

    // Half-life durations in milliseconds
    private const val HALF_LIFE_APP_MS = 48.0 * 60 * 60 * 1000 // 48 hours
    private const val HALF_LIFE_CONTACT_MS = 7.0 * 24 * 60 * 60 * 1000 // 7 days
    private const val HALF_LIFE_FILE_MS = 24.0 * 60 * 60 * 1000 // 24 hours
    private const val HALF_LIFE_DEFAULT_MS = 48.0 * 60 * 60 * 1000

    private val REGEX_WHITESPACE = Regex("\\s+")

    /**
     * Computes the full relevance score breakdown for an entity candidate given a search query.
     */
    fun evaluateScore(
        query: String,
        targetTitle: String,
        metadata: ScoringMetadata,
        currentTimeMs: Long = System.currentTimeMillis(),
        domainWeightMultiplier: Float = 1.0f,
        precomputedQueryMetaphone: DoubleMetaphone.MetaphoneResult? = null
    ): ScoreBreakdown {
        val q = query.trim().lowercase()
        val title = targetTitle.trim().lowercase()
        val rawTitle = targetTitle.trim()

        if (q.isEmpty() || title.isEmpty()) {
            return ScoreBreakdown(0f, 0f, 0f, 0f, 0f, metadata.domain.weight * domainWeightMultiplier)
        }

        // 1. Text & Structural Match Scoring
        val matchScore = computeMatchScore(q, title, rawTitle, precomputedQueryMetaphone)

        // 2. Frequency Scoring (Logarithmic compression)
        val frequencyScore = if (metadata.launchCount > 0) {
            (FREQUENCY_WEIGHT * ln(1.0 + metadata.launchCount.toDouble())).toFloat()
        } else {
            0.0f
        }

        // 3. Recency Scoring (Exponential Half-Life Decay)
        val recencyScore = computeRecencyScore(metadata.lastUsedTimestampMs, currentTimeMs, metadata.domain)

        // 4. Pin Bonus
        val pinScore = if (metadata.isPinned) PIN_BONUS else 0.0f

        // 5. Domain-Weighted Total
        val rawSum = matchScore + frequencyScore + recencyScore + pinScore
        val effectiveMultiplier = metadata.domain.weight * domainWeightMultiplier
        val totalScore = rawSum * effectiveMultiplier

        return ScoreBreakdown(
            totalScore = totalScore,
            matchScore = matchScore,
            frequencyScore = frequencyScore,
            recencyScore = recencyScore,
            pinScore = pinScore,
            domainMultiplier = effectiveMultiplier
        )
    }

    private fun computeMatchScore(
        query: String, 
        title: String, 
        rawTitle: String = title, 
        precomputedQueryMetaphone: DoubleMetaphone.MetaphoneResult? = null
    ): Float {
        // A. Exact Match
        if (title == query) {
            return SCORE_EXACT_MATCH
        }

        // B. Word-Boundary Prefix Match (e.g. "note" in "Keep Notes")
        val words = title.split(REGEX_WHITESPACE)
        val isWordBoundaryPrefix = words.any { it.startsWith(query) }
        val lengthRatio = (query.length.toFloat() / title.length.toFloat()).coerceIn(0.05f, 1.0f)

        if (title.startsWith(query)) {
            return SCORE_PREFIX_MAX * lengthRatio + 150.0f // Pure leading prefix bonus
        } else if (isWordBoundaryPrefix) {
            return SCORE_PREFIX_MAX * lengthRatio
        }

        // C. Acronym / Initials Match (e.g. "wa" -> "WhatsApp", "yt" -> "YouTube", "gpm" -> "Google Play Music")
        if (matchesAcronym(query, rawTitle)) {
            return SCORE_ACRONYM_MATCH
        }

        // D. Substring Match
        if (title.contains(query)) {
            return SCORE_SUBSTRING_MAX * lengthRatio
        }

        // E. Phonetic Match via Double Metaphone
        val queryMetaphone = precomputedQueryMetaphone ?: DoubleMetaphone.encode(query)
        val titleMetaphone = DoubleMetaphone.encode(title)
        if (queryMetaphone.matches(titleMetaphone)) {
            return SCORE_PHONETIC_MATCH
        }

        // Check phonetic match across individual words (e.g. contact first/last name)
        for (w in words) {
            val wordMetaphone = DoubleMetaphone.encode(w)
            if (queryMetaphone.matches(wordMetaphone)) {
                return SCORE_PHONETIC_MATCH * 0.9f
            }
        }

        // F. Typo-Tolerant Edit Distance
        val editDistance = LevenshteinDistance.computeDistance(query, title, maxDistance = 2)
        if (editDistance <= 2) {
            return (SCORE_FUZZY_BASE - (editDistance * PENALTY_PER_EDIT_DISTANCE)).coerceAtLeast(10.0f)
        }

        // Check edit distance on word tokens for queries length >= 3
        if (query.length >= 3) {
            for (w in words) {
                val tokenDist = LevenshteinDistance.computeDistance(query, w, maxDistance = 2)
                if (tokenDist <= 2) {
                    return (SCORE_FUZZY_BASE - (tokenDist * PENALTY_PER_EDIT_DISTANCE) - 30.0f).coerceAtLeast(10.0f)
                }
            }
        }

        return 0.0f
    }

    private fun matchesAcronym(query: String, rawTitle: String): Boolean {
        if (query.length < 2 || rawTitle.isEmpty()) return false

        // Extract initials considering whitespace, delimiters, and CamelCase transitions
        val initials = StringBuilder()
        for (i in rawTitle.indices) {
            val c = rawTitle[i]
            if (i == 0 && c.isLetterOrDigit()) {
                initials.append(c)
            } else if (c.isLetterOrDigit()) {
                val prev = rawTitle[i - 1]
                val isAfterDelimiter = !prev.isLetterOrDigit()
                val isCamelCaseTransition = prev.isLowerCase() && c.isUpperCase()
                if (isAfterDelimiter || isCamelCaseTransition) {
                    initials.append(c)
                }
            }
        }

        val initialsStr = initials.toString()
        if (initialsStr.length < query.length) return false
        return initialsStr.startsWith(query, ignoreCase = true)
    }

    private fun computeRecencyScore(lastUsedMs: Long, currentTimeMs: Long, domain: EntityDomain): Float {
        if (lastUsedMs <= 0L || lastUsedMs > currentTimeMs) return 0.0f

        val elapsedMs = (currentTimeMs - lastUsedMs).toDouble()
        val halfLifeMs = when (domain) {
            EntityDomain.APPLICATION -> HALF_LIFE_APP_MS
            EntityDomain.CONTACT -> HALF_LIFE_CONTACT_MS
            EntityDomain.FILE -> HALF_LIFE_FILE_MS
            else -> HALF_LIFE_DEFAULT_MS
        }

        // Half-life decay: R = R_max * 2^(-delta_t / tau)
        val decayFactor = 2.0.pow(-elapsedMs / halfLifeMs)
        return (RECENCY_MAX_BONUS * decayFactor).toFloat().coerceIn(0.0f, RECENCY_MAX_BONUS)
    }
}
