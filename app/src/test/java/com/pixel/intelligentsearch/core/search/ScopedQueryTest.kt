package com.pixel.intelligentsearch.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A query that starts with "@", "#" or ">" searches contacts, files or settings pages only, unless that symbol is the
 * one that starts the user's web shortcuts.
 */
class ScopedQueryTest {

    private fun parse(query: String, shortcutTrigger: String? = "!") = ScopedQuery.parse(query, shortcutTrigger)

    @Test
    fun eachMarkerLimitsTheSearchToItsKindOfResult() {
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "jo"), parse("@jo"))
        assertEquals(ScopedQuery(SearchScope.FILES, "tax return"), parse("#tax return"))
        assertEquals(ScopedQuery(SearchScope.SETTINGS, "wifi"), parse(">wifi"))
    }

    @Test
    fun theWordsAfterTheMarkerAreTrimmed() {
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "jo smith"), parse("@  jo smith "))
        assertEquals(ScopedQuery(SearchScope.SETTINGS, "dark mode"), parse("> dark mode"))
    }

    @Test
    fun theMarkerAloneLimitsTheSearchWithNothingToSearchForYet() {
        assertEquals(ScopedQuery(SearchScope.CONTACTS, ""), parse("@"))
        assertEquals(ScopedQuery(SearchScope.FILES, ""), parse("# "))
        assertEquals(ScopedQuery(SearchScope.SETTINGS, ""), parse(">"))
    }

    @Test
    fun onlyTheVeryFirstCharacterCounts() {
        assertNull(parse("jo @work"))
        assertNull(parse("mail jo@example.com"))
        assertNull(parse(" @jo"))
        assertNull(parse("c# files"))
        assertNull(parse("5 > 3"))
        assertNull(parse(""))
        assertNull(parse("jo"))
    }

    @Test
    fun theSymbolThatStartsWebShortcutsKeepsMeaningShortcuts() {
        // With "@" for shortcuts there is no contacts filter; the other two still work.
        assertNull(parse("@jo", shortcutTrigger = "@"))
        assertNull(parse("@", shortcutTrigger = "@"))
        assertEquals(ScopedQuery(SearchScope.FILES, "tax"), parse("#tax", shortcutTrigger = "@"))
        assertEquals(ScopedQuery(SearchScope.SETTINGS, "wifi"), parse(">wifi", shortcutTrigger = "@"))

        assertNull(parse("#tax", shortcutTrigger = "#"))
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "jo"), parse("@jo", shortcutTrigger = "#"))

        assertNull(parse(">wifi", shortcutTrigger = ">"))
        assertEquals(ScopedQuery(SearchScope.FILES, "tax"), parse("#tax", shortcutTrigger = ">"))
    }

    @Test
    fun aLongerShortcutSymbolThatBeginsWithTheMarkerBlocksItToo() {
        // On the way to typing "##g", "#" must not flash a files filter.
        assertNull(parse("#", shortcutTrigger = "##"))
        assertNull(parse("#tax", shortcutTrigger = "##"))
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "jo"), parse("@jo", shortcutTrigger = "##"))
    }

    @Test
    fun withWebShortcutsSwitchedOffNoSymbolIsHeldBack() {
        // null: shortcuts are off, whatever symbol is saved for them.
        assertEquals(ScopedQuery(SearchScope.CONTACTS, "jo"), parse("@jo", shortcutTrigger = null))
        assertEquals(ScopedQuery(SearchScope.FILES, "tax"), parse("#tax", shortcutTrigger = null))
        assertEquals(ScopedQuery(SearchScope.SETTINGS, "wifi"), parse(">wifi", shortcutTrigger = null))
    }

    @Test
    fun anAtSignBeforeANumberStaysATime() {
        // "@5", "@ 5" and "@ 5pm" read as "at 5", as before.
        assertNull(parse("@5"))
        assertNull(parse("@ 5"))
        assertNull(parse("@ 5pm"))
        assertNull(parse("@7:30 call mom"))
        // The other markers have no such reading.
        assertEquals(ScopedQuery(SearchScope.FILES, "2025 taxes"), parse("#2025 taxes"))
        assertEquals(ScopedQuery(SearchScope.SETTINGS, "5g"), parse(">5g"))
    }

    @Test
    fun whatALimitedSearchSaysWhenItFindsNothing() {
        assertEquals("No contacts match “jo”", SearchScope.CONTACTS.noMatchMessage("jo"))
        assertEquals("No files match “tax return”", SearchScope.FILES.noMatchMessage("tax return"))
        assertEquals("No settings match “wifi”", SearchScope.SETTINGS.noMatchMessage("wifi"))
    }
}
