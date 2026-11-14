package com.pixel.intelligentsearch.core.search

/**
 * The one kind of result a search can be limited to by the character it starts with: "@jo" finds only contacts,
 * "#tax" only files and ">wifi" only settings pages. [chipLabel] is what the chip over those results says, [emptyHint]
 * what is shown while only the marker is typed, [noun] what a search that found nothing says it looked for, and
 * [offMessage] what is shown instead of results while that kind of search is switched off (settings pages have no
 * switch).
 */
enum class SearchScope(
    val marker: Char,
    val chipLabel: String,
    val emptyHint: String,
    val noun: String,
    val offMessage: String?
) {
    CONTACTS('@', "Contacts only", "Type a name", "contacts", "Contact search is off. Turn it on in Search Sources."),
    FILES('#', "Files only", "Type a file name", "files", "File search is off. Turn it on in Search Sources."),
    SETTINGS('>', "Settings only", "Type a setting", "settings", null);

    /** What a search for [words] that found nothing says: No contacts match "jo". */
    fun noMatchMessage(words: String): String = "No $noun match \"$words\""
}

/**
 * A query limited to one kind of result: "@jo" searches the contacts for "jo". [rest] is the query after the marker,
 * trimmed; it is blank while only the marker is typed.
 */
data class ScopedQuery(val scope: SearchScope, val rest: String) {
    companion object {
        /**
         * The kind of result [query] is limited to, or null when it is an ordinary search. Only the query's very
         * first character counts, so "mail @work" and " @jo" search everything. [shortcutTrigger] is the symbol that
         * starts the user's web shortcuts, or null while those are switched off: a marker that is (or begins) that
         * symbol keeps meaning shortcuts, and the other markers still work. "@5" and "@ 5pm" stay the time they
         * read as ("at 5"), so a contacts search never begins with a digit.
         */
        fun parse(query: String, shortcutTrigger: String?): ScopedQuery? {
            val marker = query.firstOrNull() ?: return null
            val scope = SearchScope.entries.firstOrNull { it.marker == marker } ?: return null
            if (shortcutTrigger?.firstOrNull() == marker) return null
            val rest = query.substring(1).trim()
            if (scope == SearchScope.CONTACTS && rest.firstOrNull()?.isDigit() == true) return null
            return ScopedQuery(scope, rest)
        }
    }
}
