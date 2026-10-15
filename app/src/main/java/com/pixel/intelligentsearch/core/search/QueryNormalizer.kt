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
    // Combining marks stay: outside Latin they tell letters apart (バ vs パ, Devanagari vowel signs).
    private val PUNCTUATION_PATTERN = Pattern.compile("[^\\p{L}\\p{M}\\p{N}\\s]")
    private val WHITESPACE_PATTERN = Pattern.compile("\\s+")

    /**
     * True when [s] is already in canonical form: only `a-z` / `0-9` separated by single spaces, with
     * no leading or trailing space. Every normalization step is the identity for such input, so the
     * allocation-heavy Unicode pipeline can be skipped (the common case while typing a query).
     */
    private fun isCanonicalAscii(s: String): Boolean {
        var previousWasSpace = true // a leading space is not canonical
        for (c in s) {
            if (c in 'a'..'z' || c in '0'..'9') {
                previousWasSpace = false
            } else if (c == ' ' && !previousWasSpace) {
                previousWasSpace = true
            } else {
                return false
            }
        }
        return !previousWasSpace // also rejects the empty string and a trailing space
    }

    private fun isPureAscii(s: String): Boolean {
        for (i in 0 until s.length) {
            if (s[i].code >= 0x80) return false
        }
        return true
    }

    private fun normalizeAscii(s: String): String {
        val sb = StringBuilder(s.length)
        var pendingSpace = false
        for (i in 0 until s.length) {
            when (val c = s[i]) {
                in 'a'..'z', in '0'..'9' -> {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    sb.append(c)
                    pendingSpace = false
                }
                in 'A'..'Z' -> {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    sb.append((c.code + 32).toChar())
                    pendingSpace = false
                }
                else -> {
                    pendingSpace = true
                }
            }
        }
        return sb.toString()
    }

    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        if (isCanonicalAscii(input)) return input
        if (isPureAscii(input)) return normalizeAscii(input)
        val preCleaned = input
            .replace("ß", "ss").replace("ẞ", "ss")
            .replace("œ", "oe").replace("Œ", "oe")
            .replace('æ', 'a').replace('Æ', 'a')
            .replace('ø', 'o').replace('Ø', 'o')
            .replace('ł', 'l').replace('Ł', 'l')
            .replace('đ', 'd').replace('Đ', 'd')
            .replace('þ', 't').replace('Þ', 't')
            .replace('ð', 'd').replace('Ð', 'd')
        val decomposed = Normalizer.normalize(preCleaned, Normalizer.Form.NFD)
        val withoutAccents = DIACRITICS_PATTERN.matcher(decomposed).replaceAll("")
        val stripped = PUNCTUATION_PATTERN.matcher(withoutAccents.lowercase(java.util.Locale.ROOT)).replaceAll(" ")
        // Recompose what NFD split apart (Hangul syllables, kana with voicing marks) so keys match typed text.
        return Normalizer.normalize(WHITESPACE_PATTERN.matcher(stripped).replaceAll(" ").trim(), Normalizer.Form.NFC)
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
