package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import com.pixel.intelligentsearch.core.answers.WorldClock
import java.util.Locale

/**
 * "Did you mean" for search queries, from Android's spell checker (the one chosen in system settings, usually the
 * keyboard's own, such as Gboard's; it works offline). Any word the checker flags as a typo is replaced by its top
 * suggestion ("Chec the timee" -> "Check the time"); words it knows are left alone. Create and call it on the main
 * thread: the checker answers on the thread that opened the session.
 */
class SpellingSuggester(private val context: Context) : SpellCheckerSession.SpellCheckerSessionListener {

    data class Correction(val offset: Int, val length: Int, val replacement: String)

    private var session: SpellCheckerSession? = null
    private var sessionTried = false
    private var sequence = 0
    private var pendingQuery: String? = null
    private var pendingCallback: ((String?) -> Unit)? = null

    /** Asks for a corrected [query]; [onResult] gets it, or null when nothing is obviously misspelled. */
    fun suggest(query: String, onResult: (String?) -> Unit) {
        val checker = openSession()
        if (checker == null) {
            onResult(null)
            return
        }
        sequence++
        pendingQuery = query
        pendingCallback = onResult
        checker.getSentenceSuggestions(arrayOf(TextInfo(query, 0, sequence)), MAX_SUGGESTIONS)
    }

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        val query = pendingQuery ?: return
        val callback = pendingCallback ?: return
        val info = results?.firstOrNull() ?: return callback(null)
        val corrections = (0 until info.suggestionsCount).mapNotNull { i ->
            val suggestion = info.getSuggestionsInfoAt(i) ?: return@mapNotNull null
            // Answers for an older query arrive after newer typing; only the latest counts.
            if (suggestion.sequence != sequence) return@mapNotNull null
            if (!isTypo(suggestion.suggestionsAttributes) || suggestion.suggestionsCount == 0) return@mapNotNull null
            Correction(info.getOffsetAt(i), info.getLengthAt(i), suggestion.getSuggestionAt(0))
        }
        callback(applyCorrections(query, corrections))
    }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) = Unit

    fun close() {
        session?.close()
        session = null
    }

    private fun openSession(): SpellCheckerSession? {
        if (!sessionTried) {
            sessionTried = true
            session = try {
                context.getSystemService(TextServicesManager::class.java)
                    ?.newSpellCheckerSession(null, Locale.getDefault(), this, true)
            } catch (e: RuntimeException) {
                null
            }
        }
        return session
    }

    companion object {
        private const val MAX_SUGGESTIONS = 3

        /** A word the checker flags as a typo, not one it merely doesn't know. */
        fun isTypo(attributes: Int): Boolean =
            attributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0 &&
                attributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY == 0

        /**
         * Whether [query] is worth spell checking: it has a real word of 3 or more letters, and it is not a
         * shortcut ("!yt cats", or the user's own trigger), a web address or a calculation. Nor is a query already
         * [answered] on the device (a sum, conversion, date or time): "3pm pst in ist" was understood, so offering
         * "3pm post in ist" would only be wrong.
         */
        fun shouldCheck(query: String, shortcutTrigger: String, isWebAddress: Boolean, answered: Boolean = false): Boolean {
            val q = query.trim()
            if (q.length < 3 || isWebAddress || answered) return false
            if (q.startsWith("!") || (shortcutTrigger.isNotEmpty() && q.startsWith(shortcutTrigger))) return false
            if (q.contains(" !") || (shortcutTrigger.isNotEmpty() && q.contains(" $shortcutTrigger"))) return false
            return q.split(Regex("\\s+")).any { word -> word.length >= 3 && word.all { it.isLetter() || it == '\'' } }
        }

        /**
         * Whether [word] is a term a dictionary doesn't know but the app does, kept as typed: a zone abbreviation
         * ("pst", not "post") or a unit or currency ("km", "ft2", "usd"). So is any word with a digit in it, a time,
         * model or code ("8am", "ps5", "covid19"): a checker's nearest word for it ("same", "past") is never meant.
         */
        fun isKnownTerm(word: String): Boolean =
            word.any { it.isDigit() } || WorldClock.isZoneAbbreviation(word) || MathematicalExpressionEngine.isUnitName(word)

        /** [query] with each correction applied, or null when there is none or the result is the same. */
        fun applyCorrections(query: String, corrections: List<Correction>): String? {
            if (corrections.isEmpty()) return null
            val result = StringBuilder(query)
            // From the end, so earlier offsets stay valid.
            for (c in corrections.sortedByDescending { it.offset }) {
                if (c.offset < 0 || c.offset + c.length > result.length || c.replacement.isBlank()) continue
                val original = result.substring(c.offset, c.offset + c.length)
                if (isKnownTerm(original)) continue
                result.replace(c.offset, c.offset + c.length, matchCase(original, c.replacement))
            }
            val corrected = result.toString()
            return corrected.takeUnless { it.equals(query, ignoreCase = true) }
        }

        /** Keeps the user's capitalisation: "Recieve" -> "Receive", "recieve" -> "receive". */
        private fun matchCase(original: String, replacement: String): String = when {
            original.isNotEmpty() && original.all { !it.isLetter() || it.isUpperCase() } && original.length > 1 -> replacement.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.uppercase() }
            else -> replacement.lowercase()
        }
    }
}
