package com.pixel.intelligentsearch.core.search

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

/**
 * The last few calculations, newest first, so a sum can carry on from the one before: "ans" in what is typed stands
 * for the last result, and the calculator card offers the earlier ones to tap back in.
 *
 * Kept in memory only: nothing is written to the phone's storage, and the results are gone once Android closes the
 * app. A result is kept when its query ends (the field is cleared or search closes), not on every keystroke.
 */
class CalculatorMemory {

    /** One kept result: [shown] as the card wrote it ("1,234.5"), and [value] as a number. */
    @Immutable
    data class Entry(val shown: String, val value: Double) {
        /** The number as it can be typed back into a sum: no grouping, no exponent. */
        val typed: String get() = plain(value)
    }

    @Volatile
    private var entries: List<Entry> = emptyList()

    /** The kept results, newest first. */
    val earlier: List<Entry> get() = entries

    /** The last result, which "ans" stands for. */
    val ans: Entry? get() = entries.firstOrNull()

    /** Keeps a result as the newest. The same result again moves to the front rather than being listed twice. */
    fun keep(shown: String, value: Double) {
        if (value.isNaN() || value.isInfinite() || shown.isBlank()) return
        entries = (listOf(Entry(shown, value)) + entries.filter { it.shown != shown }).take(MAX)
    }

    fun clear() {
        entries = emptyList()
    }

    /** Whether [query] has "ans" in it as a word of its own ("answer" and "cans" do not). */
    fun usesAns(query: String): Boolean = ans != null && ANS.containsMatchIn(query)

    /** [query] with each "ans" replaced by the last result, or null when it has none or nothing has been kept. */
    fun withAns(query: String): String? {
        val last = ans ?: return null
        if (!ANS.containsMatchIn(query)) return null
        return ANS.replace(query) { "(${last.typed})" }
    }

    companion object {
        const val MAX = 4
        private val ANS = Regex("(?<![\\p{L}\\p{N}_.])ans(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
        private val TRAILING_NUMBER = Regex("(?:\\d+(?:\\.\\d*)?|\\.\\d+)$")

        /**
         * [text] with [number] recalled into it, as a calculator's memory recall does: a number [text] ends with is
         * replaced ("2+2" becomes "2+55"), and otherwise [number] goes on the end ("2+" becomes "2+55", "ans*" becomes
         * "ans*55", "(2+3)" becomes "(2+3)*55"). A negative number after something else goes in brackets, so "2*" becomes "2*(-3)".
         */
        fun recall(text: String, number: String): String {
            val kept = TRAILING_NUMBER.find(text)?.let { text.substring(0, it.range.first) } ?: text
            // After a closing bracket the number multiplies what is in it: "(2+3)" becomes "(2+3)*55".
            val joint = if (kept.endsWith(")")) "*" else ""
            return kept + joint + if (kept.isNotEmpty() && number.startsWith("-")) "($number)" else number
        }

        internal fun plain(value: Double): String =
            if (value == Math.rint(value) && Math.abs(value) < 1e15) value.toLong().toString()
            else BigDecimal.valueOf(value).toPlainString()
    }
}
