package com.pixel.intelligentsearch.core.search

import java.util.Locale

/**
 * Numbers written as words, as voice search writes small ones down ("nine", "twenty-one", "one hundred and five",
 * "two thousand", "two point five"), turned into digits so a sum said aloud can be worked out. Only whole runs of
 * number words change; every other word is left as it was.
 */
internal object SpokenNumbers {

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80,
        "ninety" to 90
    )
    private val SCALES = mapOf("thousand" to 1_000L, "million" to 1_000_000L, "billion" to 1_000_000_000L)

    private val TOKEN = Regex("[A-Za-z]+|[^A-Za-z]+")
    /** The hyphen inside a number word ("twenty-one"), not a minus sign ("5-3"). */
    private val WORD_HYPHEN = Regex("(?<=[A-Za-z])-(?=[A-Za-z])")

    /** [text] with each run of number words as its digits ("what's nine times nine" is "what's 9 times 9"). */
    fun withDigits(text: String): String {
        if (UNITS.keys.none { text.contains(it, ignoreCase = true) } && TENS.keys.none { text.contains(it, ignoreCase = true) }) {
            return text
        }
        val tokens = TOKEN.findAll(text.replace(WORD_HYPHEN, " ")).map { it.value }.toList()
        val out = StringBuilder()
        var i = 0
        while (i < tokens.size) {
            val run = numberAt(tokens, i)
            if (run == null) {
                out.append(tokens[i])
                i++
            } else {
                out.append(run.first)
                i = run.second
            }
        }
        return out.toString()
    }

    /**
     * The number spelled from [start] in [tokens] and the index after it, or null when no number word starts there.
     * Spaces between the words are part of the run; "and" counts only after "hundred" ("one hundred and five").
     */
    private fun numberAt(tokens: List<String>, start: Int): Pair<String, Int>? {
        if (word(tokens, start) !in UNITS && word(tokens, start) !in TENS) return null
        var total = 0L
        var current = 0L
        var i = start
        var end = start
        var lastWasHundred = false
        while (i < tokens.size) {
            val w = word(tokens, i)
            when {
                w in UNITS -> { current += UNITS.getValue(w!!); lastWasHundred = false }
                w in TENS -> { current += TENS.getValue(w!!); lastWasHundred = false }
                w == "hundred" && current > 0 -> { current *= 100; lastWasHundred = true }
                w in SCALES && (current > 0 || total > 0) -> {
                    total += (if (current == 0L) 1 else current) * SCALES.getValue(w!!)
                    current = 0
                    lastWasHundred = false
                }
                w == "and" && lastWasHundred && word(tokens, i + 2).let { it in UNITS || it in TENS } -> {}
                else -> break
            }
            end = i + 1
            // Words are separated by one stretch of spaces; anything else ends the number.
            if (i + 1 < tokens.size && tokens[i + 1].isNotBlank()) break
            i += 2
        }
        var digits = (total + current).toString()
        // "two point five": a decimal point and the digits said one by one after it.
        if (word(tokens, end + 1) == "point") {
            val decimals = StringBuilder()
            var j = end + 3
            while (j - 1 < tokens.size && tokens[j - 1].isBlank() && UNITS[word(tokens, j)]?.takeIf { it < 10 } != null) {
                decimals.append(UNITS.getValue(word(tokens, j)!!))
                end = j + 1
                j += 2
            }
            if (decimals.isNotEmpty()) digits += ".$decimals"
        }
        return digits to end
    }

    private fun word(tokens: List<String>, index: Int): String? =
        tokens.getOrNull(index)?.takeIf { it.first().isLetter() }?.lowercase(Locale.ROOT)
}
