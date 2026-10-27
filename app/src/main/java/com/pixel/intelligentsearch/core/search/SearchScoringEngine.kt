package com.pixel.intelligentsearch.core.search

import kotlin.math.ln
import kotlin.math.pow

/**
 * Dynamic Multi-Factor Recency & Relevance Scoring Matrix.
 * Computes deterministic, normalized relevance ranks by combining:
 * - Match Precision: Exact, Prefix, Word-Boundary, Acronym, Substring, Phonetic, Fuzzy
 * - Usage Frequency: Sub-linear logarithmic scaling
 * - Recency Decay: Exponential half-life decay function
 * - Learned Picks: what the user opened after typing this text before ([SelectionMemory])
 * - Pin/Favorite Weight: a pinned result that matches at all outranks every unpinned one in its domain; a starred
 *   contact gets a small lift
 * - Domain Weight: Intent priority weighting across entity domains
 */
object SearchScoringEngine {

    enum class EntityDomain(val weight: Float) {
        SYSTEM_TOGGLE(2.0f),
        APPLICATION(1.8f),
        CONTACT(1.3f),
        FILE(1.0f)
    }

    data class ScoringMetadata(
        val launchCount: Int = 0,
        val lastUsedTimestampMs: Long = 0L,
        val isPinned: Boolean = false,
        val domain: EntityDomain = EntityDomain.APPLICATION,
        /** How strongly the user picked this result for the query being scored ([SelectionMemory.learnedWeight]). */
        val learnedWeight: Float = 0f,
        /** A contact starred in the Contacts app. */
        val isFavorite: Boolean = false
    )

    data class ScoreBreakdown(
        val totalScore: Float,
        val matchScore: Float,
        val frequencyScore: Float,
        val recencyScore: Float,
        val pinScore: Float,
        val domainMultiplier: Float,
        val learnedScore: Float = 0f,
        val favoriteScore: Float = 0f
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
    /**
     * What one pick for exactly the query adds is LEARNED_WEIGHT * ln 2 (about 280, less than an exact name's lead over
     * a short prefix, so one stray tap reorders little); repeated picks add less each, up to [LEARNED_MAX].
     */
    private const val LEARNED_WEIGHT = 400.0f
    private const val LEARNED_MAX = 900.0f
    private const val FAVORITE_BONUS = 150.0f

    /**
     * More than any unpinned result can reach (an exact match, 1000, plus frequency and recency, about 900, the most
     * learned picks add, 900, and a star, 150), so a pinned result that matches the query at all ranks first in its
     * domain, as Pin to Top promises.
     */
    private const val PIN_BONUS = 4000.0f

    // Half-life durations in milliseconds
    private const val HALF_LIFE_APP_MS = 48.0 * 60 * 60 * 1000 // 48 hours
    private const val HALF_LIFE_CONTACT_MS = 7.0 * 24 * 60 * 60 * 1000 // 7 days
    private const val HALF_LIFE_FILE_MS = 24.0 * 60 * 60 * 1000 // 24 hours
    private const val HALF_LIFE_DEFAULT_MS = 48.0 * 60 * 60 * 1000

    private val REGEX_WHITESPACE = Regex("\\s+")

    // Package and path segments shared by most subtitles; matching them ranks every app for "goo".
    private val GENERIC_SUBTITLE_TOKENS = setOf(
        "com", "org", "net", "io", "android", "google", "apps", "app", "storage", "emulated"
    )

    /**
     * Computes the full relevance score breakdown for an entity candidate given a search query.
     */
    fun evaluateScore(
        query: String,
        targetTitle: String,
        metadata: ScoringMetadata,
        currentTimeMs: Long = System.currentTimeMillis(),
        precomputedQueryMetaphone: DoubleMetaphone.MetaphoneResult? = null,
        precomputedTargetMetaphone: DoubleMetaphone.MetaphoneResult? = null,
        targetSubtitle: String? = null
    ): ScoreBreakdown {
        val q = query.trim().lowercase()
        val title = targetTitle.trim().lowercase()
        val rawTitle = targetTitle.trim()

        if (q.isEmpty() || (title.isEmpty() && targetSubtitle.isNullOrBlank())) {
            return ScoreBreakdown(0f, 0f, 0f, 0f, 0f, metadata.domain.weight)
        }

        // 1. Text & Structural Match Scoring. Accent/punctuation-folded forms are scored too, so "cafe"
        // ranks "Café" as an exact match rather than relying on the weaker phonetic fallback.
        val foldedQuery = QueryNormalizer.normalize(q)
        val titleMatchScore = if (title.isNotEmpty()) {
            val rawMatchScore = computeMatchScore(q, title, rawTitle, precomputedQueryMetaphone, precomputedTargetMetaphone)
            val foldedTitle = QueryNormalizer.normalize(title)
            if (rawMatchScore < SCORE_EXACT_MATCH && foldedQuery.isNotEmpty() && foldedTitle.isNotEmpty() &&
                (foldedQuery != q || foldedTitle != title)
            ) {
                maxOf(rawMatchScore, computeMatchScore(foldedQuery, foldedTitle, rawTitle, precomputedQueryMetaphone, precomputedTargetMetaphone))
            } else {
                rawMatchScore
            }
        } else {
            0.0f
        }

        val subtitleMatchScore = if (!targetSubtitle.isNullOrBlank()) {
            computeSubtitleMatchScore(q, foldedQuery, targetSubtitle)
        } else {
            0.0f
        }

        val matchScore = maxOf(titleMatchScore, subtitleMatchScore)
        if (matchScore <= 0.0f) {
            return ScoreBreakdown(0f, 0f, 0f, 0f, 0f, metadata.domain.weight)
        }

        // 2. Frequency Scoring (Logarithmic compression)
        val frequencyScore = if (metadata.launchCount > 0) {
            (FREQUENCY_WEIGHT * ln(1.0 + metadata.launchCount.toDouble())).toFloat()
        } else {
            0.0f
        }

        // 3. Recency Scoring (Exponential Half-Life Decay)
        val recencyScore = computeRecencyScore(metadata.lastUsedTimestampMs, currentTimeMs, metadata.domain)

        // 4. Learned picks: sub-linear, so a habit counts and a long one doesn't grow without end.
        val learnedScore = if (metadata.learnedWeight > 0f) {
            (LEARNED_WEIGHT * ln(1.0 + metadata.learnedWeight.toDouble())).toFloat().coerceAtMost(LEARNED_MAX)
        } else {
            0.0f
        }
        val favoriteScore = if (metadata.isFavorite) FAVORITE_BONUS else 0.0f

        // 5. Pin Bonus
        val pinScore = if (metadata.isPinned) PIN_BONUS else 0.0f

        // 6. Domain-Weighted Total
        val rawSum = matchScore + frequencyScore + recencyScore + learnedScore + favoriteScore + pinScore
        val effectiveMultiplier = metadata.domain.weight
        val totalScore = rawSum * effectiveMultiplier

        return ScoreBreakdown(
            totalScore = totalScore,
            matchScore = matchScore,
            frequencyScore = frequencyScore,
            recencyScore = recencyScore,
            pinScore = pinScore,
            domainMultiplier = effectiveMultiplier,
            learnedScore = learnedScore,
            favoriteScore = favoriteScore
        )
    }

    /**
     * Scores the secondary line: an app's package name, a contact's number or a file's path. Only
     * digit runs and whole-token or token-prefix matches count. The phonetic and edit-distance tiers used for titles would read "cam" as "com" and rank every
     * installed app, so they are not applied here.
     */
    private fun computeSubtitleMatchScore(
        query: String,
        foldedQuery: String,
        targetSubtitle: String
    ): Float {
        val sub = targetSubtitle.trim().lowercase()
        if (sub.isEmpty()) return 0.0f

        var score = 0.0f
        val queryTokens = foldedQuery.split(' ')
            .filter { it.isNotEmpty() && !it.all(Char::isDigit) && it !in GENERIC_SUBTITLE_TOKENS }
        if (queryTokens.isNotEmpty()) {
            val subTokens = QueryNormalizer.normalize(sub).split(' ')
                .filter { it.isNotEmpty() && it !in GENERIC_SUBTITLE_TOKENS }
            var allExact = true
            val allMatched = queryTokens.all { token ->
                when {
                    subTokens.any { it == token } -> true
                    token.length >= 3 && subTokens.any { it.startsWith(token) } -> {
                        allExact = false
                        true
                    }
                    else -> false
                }
            }
            if (allMatched) {
                score = if (allExact) SCORE_PREFIX_MAX * 0.85f else SCORE_SUBSTRING_MAX * 0.85f
            }
        }

        val queryDigits = query.filter { it.isDigit() }
        if (queryDigits.length >= 3) {
            val subDigits = sub.filter { it.isDigit() }
            if (subDigits.isNotEmpty()) {
                val digitScore = when {
                    subDigits == queryDigits -> SCORE_EXACT_MATCH * 0.85f
                    // The query adds a country or trunk code the saved number leaves out: "+1 415 555 2671"
                    // for "(415) 555-2671".
                    subDigits.length >= 7 && queryDigits.endsWith(subDigits) -> SCORE_EXACT_MATCH * 0.85f
                    subDigits.startsWith(queryDigits) -> SCORE_PREFIX_MAX * 0.85f
                    // The same national number behind different prefixes: "+44 7700 900123" for "07700 900123".
                    subDigits.length >= 10 && queryDigits.length >= 10 &&
                        subDigits.takeLast(10) == queryDigits.takeLast(10) -> SCORE_PREFIX_MAX * 0.85f
                    subDigits.contains(queryDigits) -> SCORE_SUBSTRING_MAX * 0.75f
                    else -> 0.0f
                }
                score = maxOf(score, digitScore)
            }
        }

        return score
    }

    private fun computeMatchScore(
        query: String, 
        title: String, 
        rawTitle: String = title, 
        precomputedQueryMetaphone: DoubleMetaphone.MetaphoneResult? = null,
        precomputedTargetMetaphone: DoubleMetaphone.MetaphoneResult? = null
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
        val titleMetaphone = precomputedTargetMetaphone ?: DoubleMetaphone.encode(title)
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

        // F. Typo-tolerant edit distance, with more slips allowed as the query grows (none under 3 letters, where
        // a guess would match almost anything). Swapped letters count as one slip.
        val allowedEdits = fuzzyEditsFor(query.length)
        if (allowedEdits > 0) {
            val editDistance = LevenshteinDistance.computeDistance(query, title, maxDistance = allowedEdits)
            if (editDistance <= allowedEdits) {
                return (SCORE_FUZZY_BASE - (editDistance * PENALTY_PER_EDIT_DISTANCE)).coerceAtLeast(10.0f)
            }
            for (w in words) {
                val tokenDist = LevenshteinDistance.computeDistance(query, w, maxDistance = allowedEdits)
                if (tokenDist <= allowedEdits) {
                    return (SCORE_FUZZY_BASE - (tokenDist * PENALTY_PER_EDIT_DISTANCE) - 30.0f).coerceAtLeast(10.0f)
                }
            }
            // G. A partly typed name with a slip ("yuotu", "gogle ma"): close to the start of the title or a word.
            val prefixDist = minOf(
                LevenshteinDistance.prefixDistance(query, title, maxDistance = allowedEdits),
                words.minOfOrNull { LevenshteinDistance.prefixDistance(query, it, maxDistance = allowedEdits) } ?: Int.MAX_VALUE
            )
            if (prefixDist <= allowedEdits) {
                return (SCORE_FUZZY_BASE - (prefixDist * PENALTY_PER_EDIT_DISTANCE) - 40.0f).coerceAtLeast(10.0f)
            }
        }

        return 0.0f
    }

    /**
     * How many typing slips a query of [length] letters may contain and still match: none under 3 letters,
     * one up to 5, two from 6. Shared by the scoring and the fuzzy index lookup.
     */
    fun fuzzyEditsFor(length: Int): Int = when {
        length < 3 -> 0
        length <= 5 -> 1
        else -> 2
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
