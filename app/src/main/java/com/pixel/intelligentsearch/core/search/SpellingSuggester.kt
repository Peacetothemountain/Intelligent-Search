package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import java.util.Locale

/**
 * "Did you mean" for search queries, from Android's spell checker (the one chosen in system settings, usually
 * Gboard's; it works offline). A correction is offered only for words the checker calls a typo *and* has a
 * recommended fix for, so ordinary words, names it knows and half-typed words are left alone. Create and call it
 * on the main thread: the checker answers on the thread that opened the session.
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
            if (!isObviousTypo(suggestion.suggestionsAttributes) || suggestion.suggestionsCount == 0) return@mapNotNull null
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

        /** A typo with a fix the checker recommends: the bar for correcting someone's search. */
        fun isObviousTypo(attributes: Int): Boolean =
            attributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0 &&
                attributes and SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS != 0

        /**
         * Whether [query] is worth spell checking: it has a real word of 3 or more letters, and it is not a
         * shortcut ("!yt cats", or the user's own trigger), a web address or a calculation.
         */
        fun shouldCheck(query: String, shortcutTrigger: String, isWebAddress: Boolean): Boolean {
            val q = query.trim()
            if (q.length < 3 || isWebAddress) return false
            if (q.startsWith("!") || (shortcutTrigger.isNotEmpty() && q.startsWith(shortcutTrigger))) return false
            if (q.contains(" !") || (shortcutTrigger.isNotEmpty() && q.contains(" $shortcutTrigger"))) return false
            return q.split(Regex("\\s+")).any { word -> word.length >= 3 && word.all { it.isLetter() || it == '\'' } }
        }

        /** [query] with each correction applied, or null when there is none or the result is the same. */
        fun applyCorrections(query: String, corrections: List<Correction>): String? {
            if (corrections.isEmpty()) return null
            val result = StringBuilder(query)
            // From the end, so earlier offsets stay valid.
            for (c in corrections.sortedByDescending { it.offset }) {
                if (c.offset < 0 || c.offset + c.length > result.length || c.replacement.isBlank()) continue
                val original = result.substring(c.offset, c.offset + c.length)
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
