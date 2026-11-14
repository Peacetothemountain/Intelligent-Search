package com.pixel.intelligentsearch.core.search

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import java.math.BigDecimal
import java.text.DecimalFormatSymbols

/**
 * The last few calculations, newest first, so a sum can carry on from the one before: "ans" in what is typed stands
 * for the last result, and the calculator card offers the earlier ones to tap back in.
 *
 * Kept on this phone in [store] (a preferences file of its own, never part of a backup), so the results are still there
 * after Android closes the app; with no store they last only while the app runs. A result is kept when its query ends
 * (the field is cleared or search closes), not on every keystroke.
 */
class CalculatorMemory(private val store: SharedPreferences? = null) {

    /** One kept result: [shown] as the card wrote it ("1,234.5"), and [value] as a number. */
    @Immutable
    data class Entry(val shown: String, val value: Double) {
        /** The number as it can be typed back into a sum: no grouping, no exponent, the phone's decimal point. */
        val typed: String get() = plain(value).let { if (decimalComma()) it.replace('.', ',') else it }
    }

    @Volatile
    private var loaded: List<Entry>? = null

    private var entries: List<Entry>
        get() = loaded ?: read().also { loaded = it }
        set(value) {
            loaded = value
            store?.edit { if (value.isEmpty()) remove(KEY) else putString(KEY, value.joinToString(LINE) { "${it.shown}$FIELD${it.value}" }) }
        }

    /** What [store] holds; anything it can't read is left out. */
    private fun read(): List<Entry> =
        store?.getString(KEY, null).orEmpty().split(LINE).mapNotNull { line ->
            val shown = line.substringBefore(FIELD, "")
            val value = line.substringAfter(FIELD, "").toDoubleOrNull()
            if (shown.isBlank() || value == null || value.isNaN() || value.isInfinite()) null else Entry(shown, value)
        }.take(MAX)

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
        /** The preferences file the results are kept in. */
        const val FILE = "calculator_memory"
        private const val KEY = "earlier"
        private const val LINE = "\n"
        private const val FIELD = "\t"
        private val PLAIN_NUMBER = Regex("-?(?:\\d+(?:[.,]\\d*)?|[.,]\\d+)")
        private val UNFINISHED = Regex("[-+*/x\u00d7\u00f7^(\\s]+$")

        /**
         * The part of [query] before an operator it ends with ("12*4+7+" is "12*4+7"), or null when it doesn't end
         * with one. A sum being typed shows its total so far, with the earlier results ready to tap in after it.
         */
        fun finishedPart(query: String): String? {
            val trimmed = query.trimEnd()
            val match = UNFINISHED.find(trimmed) ?: return null
            if (match.value.isBlank()) return null
            return trimmed.substring(0, match.range.first).trim().takeIf { part -> part.any { it.isDigit() } }
        }

        /** Whether [text] is a number on its own ("120", "-3.5", "-3,5"). */
        fun isPlainNumber(text: String): Boolean = PLAIN_NUMBER.matches(text.trim())

        /** [text] as a number, read with the phone's decimal point, or null when it is not a number on its own. */
        fun plainValue(text: String): Double? {
            val trimmed = text.trim()
            if (!PLAIN_NUMBER.matches(trimmed)) return null
            return (if (decimalComma()) trimmed.replace(".", "").replace(',', '.') else trimmed.replace(",", "")).toDoubleOrNull()
        }
        private val ANS = Regex("(?<![\\p{L}\\p{N}_.])ans(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
        // A number at the end, with its decimal point or grouping, whichever the phone writes: "1,5" is one number.
        private val TRAILING_NUMBER = Regex("(?:\\d+(?:[.,]\\d*)*|[.,]\\d+)$")

        /**
         * [text] with [number] recalled into it, as a calculator's memory recall does: a number [text] ends with is
         * replaced ("2+2" becomes "2+55"), and otherwise [number] goes on the end ("2+" becomes "2+55", "ans*" becomes
         * "ans*55", "(2+3)" becomes "(2+3)*55"). A negative number after something else goes in brackets, so "2*" becomes "2*(-3)".
         */
        fun recall(text: String, number: String): String {
            val kept = TRAILING_NUMBER.find(text)?.let { text.substring(0, it.range.first) } ?: text
            // After a closing bracket, "ans" or a constant, the number multiplies it: "(2+3)" becomes "(2+3)*55".
            val joint = if (kept.lastOrNull()?.let { it == ')' || it.isLetter() } == true) "*" else ""
            return kept + joint + if (kept.isNotEmpty() && number.startsWith("-")) "($number)" else number
        }

        private fun decimalComma(): Boolean = DecimalFormatSymbols.getInstance().decimalSeparator == ','

        internal fun plain(value: Double): String =
            if (value == Math.rint(value) && Math.abs(value) < 1e15) value.toLong().toString()
            else BigDecimal.valueOf(value).toPlainString()
    }
}
