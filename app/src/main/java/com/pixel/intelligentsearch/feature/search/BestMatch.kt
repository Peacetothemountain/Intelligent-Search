package com.pixel.intelligentsearch.feature.search

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

    fun find(query: String, domains: List<Domain<*>>, learnedPick: String? = null): Any? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val ordered = domains.filter { it.enabled }.sortedByDescending { it.weight }
        if (learnedPick != null) {
            for (domain in ordered) {
                withKey(domain, learnedPick)?.let { return it }
            }
        }
        val tiers: List<(String) -> Boolean> = listOf(
            { name -> name.equals(q, ignoreCase = true) },
            { name -> name.startsWith(q, ignoreCase = true) },
            { name -> name.split(WORDS).any { it.startsWith(q, ignoreCase = true) } }
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
        domain.items.firstOrNull { item -> matches(domain.nameOf(item)) }
}
