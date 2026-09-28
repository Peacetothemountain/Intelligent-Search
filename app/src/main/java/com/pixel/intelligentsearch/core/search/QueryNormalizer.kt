package com.pixel.intelligentsearch.core.search

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * High-performance canonical query normalizer for Google-grade search indexing and matching.
 * Provides:
 * 1. Unicode NFD diacritic and accent stripping (e.g. "café" -> "cafe", "Müller" -> "muller")
 * 2. Punctuation folding and whitespace compaction
 * 3. Conjunctive multi-token extraction
 */
object QueryNormalizer {
    private val DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    private val PUNCTUATION_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s]")
    private val WHITESPACE_PATTERN = Pattern.compile("\\s+")

    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val withoutAccents = DIACRITICS_PATTERN.matcher(decomposed).replaceAll("")
        val stripped = PUNCTUATION_PATTERN.matcher(withoutAccents.lowercase(java.util.Locale.ROOT)).replaceAll(" ")
        return WHITESPACE_PATTERN.matcher(stripped).replaceAll(" ").trim()
    }

    fun tokenize(input: String?): List<String> {
        val normalized = normalize(input)
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(" ").filter { it.isNotEmpty() }
    }

    fun extractInitials(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val tokens = tokenize(input)
        if (tokens.size > 1) {
            val sb = StringBuilder(tokens.size)
            for (token in tokens) {
                sb.append(token[0])
            }
            return sb.toString()
        }
        // Support CamelCase / PascalCase words (e.g. YouTube -> "yt", WhatsApp -> "wa", SoundCloud -> "sc")
        val capitals = input.filter { it.isUpperCase() }.lowercase(java.util.Locale.ROOT)
        if (capitals.length in 2..5) {
            return capitals
        }
        return ""
    }

    fun containsAllTokens(target: String?, queryTokens: List<String>): Boolean {
        if (target.isNullOrBlank() || queryTokens.isEmpty()) return false
        val targetTokens = tokenize(target)
        return queryTokens.all { qToken ->
            targetTokens.any { tToken -> tToken.startsWith(qToken) || tToken.contains(qToken) }
        }
    }

    fun containsAllTokens(target: String?, query: String?): Boolean {
        if (target.isNullOrBlank() || query.isNullOrBlank()) return false
        val queryTokens = tokenize(query)
        return containsAllTokens(target, queryTokens)
    }
}
