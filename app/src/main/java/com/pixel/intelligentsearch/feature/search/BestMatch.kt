package com.pixel.intelligentsearch.feature.search

import java.text.Normalizer
import java.util.Locale

/**
 * Picks the result the overlay highlights as the best match and offers as ghost text. A result the user has picked
 * for exactly this text before ([com.pixel.intelligentsearch.core.search.SelectionMemory.topPick]) comes first while
 * it is among the results. Otherwise matches are tried in three tiers (whole name, start of name, start of any word,
 * so "notes" finds "Keep Notes"); within a tier, result types are tried in the user's priority order, heaviest weight
 * first and apps, contacts, files on a tie.
 */
internal object BestMatch {

    /**
     * One kind of result: its user-set weight, whether it is searched at all, its items in display order, each item's
     * [com.pixel.intelligentsearch.core.search.ResultKeys] key (for the learned pick) and each item's name.
     */
    class Domain<T : Any>(
        val weight: Int,
        val enabled: Boolean,
        val items: List<T>,
        val keyOf: (T) -> String? = { null },
        val nameOf: (T) -> String
    )

    private val WORDS = Regex("\\s+")
    private val ACCENTS = Regex("\\p{InCombiningDiacriticalMarks}+")

    /**
     * [text] as names are compared: lowercase, accents off and runs of spaces as one, so "cafe" is "Café" and
     * "google  maps" is "Google Maps" as they are in the results. Punctuation stays: "c++" is not "c".
     */
    private fun fold(text: String): String {
        val plain = if (text.all { it.code < 0x80 }) text else Normalizer.normalize(text, Normalizer.Form.NFD).replace(ACCENTS, "")
        return plain.lowercase(Locale.ROOT).replace(WORDS, " ").trim()
    }

    fun find(query: String, domains: List<Domain<*>>, learnedPick: String? = null): Any? {
        val q = fold(query)
        if (q.isEmpty()) return null
        val ordered = domains.filter { it.enabled }.sortedByDescending { it.weight }
        if (learnedPick != null) {
            for (domain in ordered) {
                withKey(domain, learnedPick)?.let { return it }
            }
        }
        val tiers: List<(String) -> Boolean> = listOf(
            { name -> name == q },
            { name -> name.startsWith(q) },
            { name -> name.split(' ').any { it.startsWith(q) } }
        )
        for (matches in tiers) {
            for (domain in ordered) {
                firstMatch(domain, matches)?.let { return it }
            }
        }
        return null
    }

    private fun <T : Any> withKey(domain: Domain<T>, key: String): T? =
        domain.items.firstOrNull { item -> domain.keyOf(item) == key }

    private fun <T : Any> firstMatch(domain: Domain<T>, matches: (String) -> Boolean): T? =
        domain.items.firstOrNull { item -> matches(fold(domain.nameOf(item))) }
}
