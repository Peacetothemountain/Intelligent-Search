package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import com.pixel.intelligentsearch.core.search.ScopedQuery
import com.pixel.intelligentsearch.core.search.SearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A search limited to one kind of result ("@jo", "#tax", ">wifi"): Enter opens a result of that kind or nothing, the
 * arrow keys stop on what is shown, and the suggested ending in the search field goes on from the words after the marker.
 */
@RunWith(RobolectricTestRunner::class)
class TypedFiltersEnterTest {

    private val timer = DirectAction("Start 15 min Timer", "15 minutes", "timer", Intent("android.intent.action.SET_TIMER"))
    private val jo = ContactItem("Jo Park", "+1 202-555-0143", "content://contacts/jo")
    private val john = ContactItem("John Doe", "+1 202-555-0187", "content://contacts/john")
    private val taxes = FileItem("Taxes 2025.pdf", "/sdcard/Taxes 2025.pdf", "application/pdf", "content://files/taxes")
    private val notes = FileItem("Tax notes.txt", "/sdcard/Tax notes.txt", "text/plain", "content://files/notes")
    private val wifi = AndroidSettingResult("wifi", "Wi-Fi", "Settings", "wifi", Intent("android.settings.WIFI_SETTINGS"))
    private val hotspot = AndroidSettingResult("hotspot", "Hotspot", "Settings", "hotspot", Intent("android.settings.TETHER_SETTINGS"))

    private fun scoped(scope: SearchScope, words: String) = SearchUiState(
        query = "${scope.marker}$words",
        scoped = ScopedQuery(scope, words),
        scopedResultsIn = true
    )

    @Test
    fun enterOnALimitedSearchNeverSearchesTheWebRunsAnActionOrOpensTheTopResult() {
        // Whatever else the words could be read as: a shortcut, a timer.
        for (isBang in listOf(false, true)) for (phrasing in listOf(null, timer)) {
            assertEquals(
                EnterOutcome.OpenScopedResult,
                OverlayEnterKey.decide("@jo", highlighted = null, phrasingAction = phrasing, isBang = isBang, scoped = true)
            )
        }
        // The same words in an ordinary search go where they always went.
        assertEquals(EnterOutcome.SearchWeb, OverlayEnterKey.decide("!g jo", null, timer, isBang = true))
        assertEquals(EnterOutcome.RunAction(timer), OverlayEnterKey.decide("15 min", null, timer, isBang = false))
        assertEquals(EnterOutcome.OpenTopResult, OverlayEnterKey.decide("jo", null, null, isBang = false))
    }

    @Test
    fun theHighlightStillComesFirstAndNothingTypedStillDoesNothing() {
        val chip = NavTarget.RemoveScope(SearchScope.CONTACTS)
        assertEquals(EnterOutcome.OpenHighlighted(chip), OverlayEnterKey.decide("@jo", chip, timer, isBang = true, scoped = true))
        assertEquals(EnterOutcome.NothingTyped, OverlayEnterKey.decide("", null, null, isBang = false, scoped = true))
    }

    @Test
    fun enterOpensTheBestMatchElseTheFirstResultElseNothing() {
        val contacts = scoped(SearchScope.CONTACTS, "jo").copy(contacts = listOf(john, jo))
        assertEquals(jo, OverlayEnterKey.scopedTarget(contacts, bestMatch = jo))
        assertEquals(john, OverlayEnterKey.scopedTarget(contacts, bestMatch = null))
        assertNull(OverlayEnterKey.scopedTarget(scoped(SearchScope.CONTACTS, "zz"), bestMatch = null))

        val files = scoped(SearchScope.FILES, "tax").copy(files = listOf(notes, taxes))
        assertEquals(taxes, OverlayEnterKey.scopedTarget(files, bestMatch = taxes))
        assertEquals(notes, OverlayEnterKey.scopedTarget(files, bestMatch = null))

        // Settings pages have no Best Match: the first page opens.
        val pages = scoped(SearchScope.SETTINGS, "wifi").copy(androidSettings = listOf(wifi, hotspot))
        assertEquals(wifi, OverlayEnterKey.scopedTarget(pages, bestMatch = null))
        assertNull(OverlayEnterKey.scopedTarget(scoped(SearchScope.SETTINGS, "zz"), bestMatch = null))
    }

    @Test
    fun enterOnlyEverOpensTheKindOfResultAskedFor() {
        // A best match of another kind is not what was asked for.
        val contacts = scoped(SearchScope.CONTACTS, "tax").copy(contacts = listOf(john))
        assertEquals(john, OverlayEnterKey.scopedTarget(contacts, bestMatch = taxes))
        assertNull(OverlayEnterKey.scopedTarget(scoped(SearchScope.CONTACTS, "tax"), bestMatch = taxes))
        assertNull(OverlayEnterKey.scopedTarget(scoped(SearchScope.FILES, "jo"), bestMatch = jo))
        // An ordinary search has no such target.
        assertNull(OverlayEnterKey.scopedTarget(SearchUiState(query = "jo", contacts = listOf(jo)), bestMatch = jo))
    }

    @Test
    fun theChipLeadsTheStopsThenTheBestMatchAndTheResults() {
        val rows = NavSources(
            scope = SearchScope.CONTACTS,
            bestMatch = jo,
            sectionOrder = listOf("apps", "android_settings", "web", "contacts", "files"),
            contacts = listOf(john)
        ).rows()
        assertEquals(
            listOf(ResultListKeys.SCOPE_CHIP, ResultListKeys.BEST_MATCH, ResultListKeys.contact(john)),
            rows.map { it.single().key }
        )
        assertEquals(NavTarget.RemoveScope(SearchScope.CONTACTS), rows.first().single().target)
    }

    @Test
    fun theLineUnderTheChipIsAStopOnlyWhenItOpensSearchSources() {
        val off = NavSources(scope = SearchScope.FILES, scopeIsOff = true).rows()
        assertEquals(listOf(ResultListKeys.SCOPE_CHIP, ResultListKeys.SCOPE_NOTICE), off.map { it.single().key })
        assertEquals(NavTarget.OpenSearchSources, off.last().single().target)
        // "Type a file name" and "No files match" only say something.
        assertEquals(listOf(ResultListKeys.SCOPE_CHIP), NavSources(scope = SearchScope.FILES).rows().map { it.single().key })
        // Without a limited search there is neither.
        assertEquals(emptyList<String>(), NavSources(scopeIsOff = true).rows().map { it.single().key })
    }

    @Test
    fun theDetailPaneShowsTheTopResultNotTheChip() {
        val rows = NavSources(scope = SearchScope.CONTACTS, bestMatch = jo).rows()
        assertEquals(ResultListKeys.BEST_MATCH, detailStop(rows, null, "@jo")?.key)
        // Highlighted, the chip is shown like any other stop.
        assertEquals(ResultListKeys.SCOPE_CHIP, detailStop(rows, ResultListKeys.SCOPE_CHIP, "@jo")?.key)
        // With nothing found there is no result to show.
        assertNull(detailStop(NavSources(scope = SearchScope.CONTACTS).rows(), null, "@zz"))
        assertNull(detailStop(NavSources(scope = SearchScope.FILES, scopeIsOff = true).rows(), null, "#tax"))
    }

    @Test
    fun whatTheLineUnderTheChipSays() {
        assertNull(scopeNotice(SearchUiState(query = "jo")))
        assertEquals(ScopeNotice("Type a name"), scopeNotice(scoped(SearchScope.CONTACTS, "")))
        assertEquals(ScopeNotice("Type a file name"), scopeNotice(scoped(SearchScope.FILES, "")))
        assertEquals(ScopeNotice("Type a setting"), scopeNotice(scoped(SearchScope.SETTINGS, "")))
        assertEquals(ScopeNotice("No contacts match \"jo\""), scopeNotice(scoped(SearchScope.CONTACTS, "jo")))
        assertNull(scopeNotice(scoped(SearchScope.CONTACTS, "jo").copy(contacts = listOf(jo))))
        // While the results for the last letters are still on their way, nothing claims there are none.
        assertNull(scopeNotice(scoped(SearchScope.CONTACTS, "jo").copy(scopedResultsIn = false)))
        // Off comes before everything else, and only for the kinds that have a switch.
        assertEquals(
            ScopeNotice("Contact search is off. Turn it on in Search Sources.", opensSearchSources = true),
            scopeNotice(scoped(SearchScope.CONTACTS, "").copy(scopedSourceOff = true))
        )
        assertEquals(
            ScopeNotice("File search is off. Turn it on in Search Sources.", opensSearchSources = true),
            scopeNotice(scoped(SearchScope.FILES, "tax").copy(scopedSourceOff = true))
        )
    }

    private fun AnnotatedString.part(hidden: Boolean): String =
        spanStyles.filter { (it.item.color == Color.Transparent) == hidden }.joinToString("") { text.substring(it.start, it.end) }

    @Test
    fun theSuggestedEndingGoesOnFromTheWordsAfterTheMarker() {
        val ghost = ghostText("@jo", "John Doe", markerLength = 1)!!
        // Everything typed, marker included, is the hidden part, so the ending lines up with the field's own text.
        assertEquals("@jo", ghost.part(hidden = true))
        assertEquals("hn Doe", ghost.part(hidden = false))
    }

    @Test
    fun noSuggestedEndingWhenTheNameDoesNotGoOnFromThoseWords() {
        // Read with its marker, "@jo" starts no name.
        assertNull(ghostText("@jo", "John Doe"))
        // A space after the marker, a word inside the name, the whole name, only the marker: no ending fits.
        assertNull(ghostText("@ jo", "John Doe", markerLength = 1))
        assertNull(ghostText("@doe", "John Doe", markerLength = 1))
        assertNull(ghostText("@john doe", "John Doe", markerLength = 1))
        assertNull(ghostText("@", null, markerLength = 1))
        assertNull(ghostText("@", "John Doe", markerLength = 1))
        assertNull(ghostText("", "John Doe", markerLength = 1))
    }
}
