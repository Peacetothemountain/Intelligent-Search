package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.input.key.Key
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.WebSuggestion
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Arrow-key navigation of the overlay's results: which stops there are and in what order (headers and dividers are
 * never stops), and what each key does over a fixed list.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayKeyboardNavTest {

    // A best match, a row of three apps, one settings page, two suggestions, a contact and a file: rows of 1, 3, 1, 1, 1, 1, 1.
    private val rows = listOf(1, 3, 1, 1, 1, 1, 1)
    private fun reduce(at: NavPosition?, key: NavKey, queryEmpty: Boolean = false) = OverlayKeyboardNav.reduce(at, key, rows, queryEmpty)

    /** Plays [keys] from the search field, applying each move, and returns where the highlight ends up. */
    private fun play(vararg keys: NavKey): NavPosition? {
        var at: NavPosition? = null
        for (key in keys) {
            val outcome = reduce(at, key)
            if (outcome is NavOutcome.Move) at = outcome.to
            if (outcome is NavOutcome.Leave) at = null
        }
        return at
    }

    @Test
    fun downFromTheFieldHighlightsTheFirstResultAndUpGoesBack() {
        assertEquals(NavOutcome.Move(NavPosition(0, 0)), reduce(null, NavKey.Down))
        assertEquals(NavOutcome.Move(null), reduce(NavPosition(0, 0), NavKey.Up))
        // Up in the field is the field's (start of the line).
        assertEquals(NavOutcome.PassThrough, reduce(null, NavKey.Up))
    }

    @Test
    fun downAndUpMoveARowAtATimeIntoTheStartOfTheAppRow() {
        assertEquals(NavPosition(1, 0), play(NavKey.Down, NavKey.Down))
        assertEquals(NavPosition(2, 0), play(NavKey.Down, NavKey.Down, NavKey.Right, NavKey.Right, NavKey.Down))
        assertEquals(NavPosition(1, 0), play(NavKey.Down, NavKey.Down, NavKey.Down, NavKey.Up))
    }

    @Test
    fun downStopsAtTheLastResult() {
        val last = NavPosition(rows.lastIndex, 0)
        assertEquals(NavOutcome.Move(last), reduce(last, NavKey.Down))
        assertEquals(last, play(*Array(20) { NavKey.Down }))
    }

    @Test
    fun leftAndRightMoveAlongTheAppRowAndStopAtItsEnds() {
        assertEquals(NavPosition(1, 2), play(NavKey.Down, NavKey.Down, NavKey.Right, NavKey.Right, NavKey.Right))
        assertEquals(NavPosition(1, 0), play(NavKey.Down, NavKey.Down, NavKey.Right, NavKey.Left, NavKey.Left))
        // On a single result, and in the field, they move the text cursor instead.
        assertEquals(NavOutcome.PassThrough, reduce(NavPosition(0, 0), NavKey.Right))
        assertEquals(NavOutcome.PassThrough, reduce(null, NavKey.Left))
    }

    @Test
    fun tabStepsThroughEveryResultAndOnOutOfTheResults() {
        assertEquals(NavPosition(0, 0), play(NavKey.Tab))
        // Every app in the row is a stop of its own.
        assertEquals(NavPosition(1, 2), play(NavKey.Tab, NavKey.Tab, NavKey.Tab, NavKey.Tab))
        assertEquals(NavPosition(2, 0), play(NavKey.Tab, NavKey.Tab, NavKey.Tab, NavKey.Tab, NavKey.Tab))
        val last = NavPosition(rows.lastIndex, 0)
        // Past the last, the highlight goes and the key moves Android's focus on.
        assertEquals(NavOutcome.Leave, reduce(last, NavKey.Tab))
    }

    @Test
    fun shiftTabStepsBackAndFromTheFirstResultReturnsToTheField() {
        assertEquals(NavOutcome.Move(NavPosition(1, 2)), reduce(NavPosition(2, 0), NavKey.ShiftTab))
        assertEquals(NavOutcome.Move(NavPosition(1, 0)), reduce(NavPosition(1, 1), NavKey.ShiftTab))
        assertEquals(NavOutcome.Move(null), reduce(NavPosition(0, 0), NavKey.ShiftTab))
        assertEquals(NavOutcome.PassThrough, reduce(null, NavKey.ShiftTab))
        assertNull(play(NavKey.Tab, NavKey.Tab, NavKey.ShiftTab, NavKey.ShiftTab))
    }

    @Test
    fun enterOpensTheHighlightAndOtherwiseIsTheFields() {
        assertEquals(NavOutcome.Open, reduce(NavPosition(3, 0), NavKey.Enter))
        assertEquals(NavOutcome.PassThrough, reduce(null, NavKey.Enter))
        // A highlight the results no longer have is no highlight.
        assertEquals(NavOutcome.PassThrough, reduce(NavPosition(40, 0), NavKey.Enter))
    }

    @Test
    fun escapeClearsWhatWasTypedThenCloses() {
        assertEquals(NavOutcome.ClearQuery, reduce(NavPosition(2, 0), NavKey.Escape, queryEmpty = false))
        assertEquals(NavOutcome.ClearQuery, reduce(null, NavKey.Escape, queryEmpty = false))
        assertEquals(NavOutcome.Close, reduce(null, NavKey.Escape, queryEmpty = true))
    }

    @Test
    fun withNoResultsTheKeysStayTheFields() {
        assertEquals(NavOutcome.PassThrough, OverlayKeyboardNav.reduce(null, NavKey.Down, emptyList(), queryEmpty = false))
        assertEquals(NavOutcome.PassThrough, OverlayKeyboardNav.reduce(null, NavKey.Tab, emptyList(), queryEmpty = false))
    }

    @Test
    fun keysHeldWithCtrlAltOrMetaAndShiftArrowsStayTheFields() {
        assertEquals(NavKey.Down, OverlayKeyboardNav.navKeyOf(Key.DirectionDown, shift = false, otherModifier = false, rtl = false))
        // Ctrl+Backspace, Ctrl+arrows, Alt shortcuts: the platform's text editing.
        assertNull(OverlayKeyboardNav.navKeyOf(Key.DirectionDown, shift = false, otherModifier = true, rtl = false))
        assertNull(OverlayKeyboardNav.navKeyOf(Key.Backspace, shift = false, otherModifier = true, rtl = false))
        assertNull(OverlayKeyboardNav.navKeyOf(Key.Escape, shift = false, otherModifier = true, rtl = false))
        // Shift+arrows select text.
        assertNull(OverlayKeyboardNav.navKeyOf(Key.DirectionLeft, shift = true, otherModifier = false, rtl = false))
        assertEquals(NavKey.ShiftTab, OverlayKeyboardNav.navKeyOf(Key.Tab, shift = true, otherModifier = false, rtl = false))
        assertEquals(NavKey.Enter, OverlayKeyboardNav.navKeyOf(Key.NumPadEnter, shift = false, otherModifier = false, rtl = false))
        assertNull(OverlayKeyboardNav.navKeyOf(Key.A, shift = false, otherModifier = false, rtl = false))
    }

    @Test
    fun inRightToLeftLanguagesRightMovesTheWayTheAppRowReads() {
        assertEquals(NavKey.Left, OverlayKeyboardNav.navKeyOf(Key.DirectionRight, shift = false, otherModifier = false, rtl = true))
        assertEquals(NavKey.Right, OverlayKeyboardNav.navKeyOf(Key.DirectionLeft, shift = false, otherModifier = false, rtl = true))
    }

    private fun app(name: String) = AppItem(name, "com.example.${name.lowercase()}", ColorDrawable(0))
    private val battery = AndroidSettingResult("battery", "Battery", "", "battery", Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
    private val ada = ContactItem("Ada", "+1 555 0100", "content://contacts/ada")
    private val notes = FileItem("notes.txt", "/sdcard/notes.txt", "text/plain", "content://files/notes")

    @Test
    fun theStopsFollowTheListsSectionsAndKeys() {
        val camera = app("Camera")
        val sources = NavSources(
            bestMatch = camera,
            answers = listOf(ResultListKeys.MATH to AnswerAction.OpenCalculator),
            sectionOrder = listOf("apps", "android_settings", "web", "contacts", "files"),
            apps = listOf(app("Calendar"), app("Calculator"), app("Calendar")),
            androidSettings = listOf(battery),
            suggestions = listOf(WebSuggestion("cats"), WebSuggestion("cat facts")),
            history = listOf("CATS"),
            contacts = listOf(ada),
            files = listOf(notes)
        )
        val rows = sources.rows()
        assertEquals(
            listOf(
                listOf(ResultListKeys.BEST_MATCH),
                listOf(ResultListKeys.MATH),
                // A duplicate app shows once in the list, so it is one stop.
                listOf(ResultListKeys.APPS_ROW, ResultListKeys.APPS_ROW),
                listOf("android_setting_battery"),
                listOf("sugg_cats"),
                listOf("sugg_cat facts"),
                listOf(ResultListKeys.contact(ada)),
                listOf(ResultListKeys.file(notes))
            ),
            rows.map { row -> row.map { it.listKey } }
        )
        // The best match app opens out of the Best Match card's icon, a row's app out of its own.
        assertEquals(NavTarget.Result(camera, LaunchIconKeys.BEST_MATCH), rows[0][0].target)
        assertEquals(LaunchIconKeys.app(app("Calendar")), (rows[2][0].target as NavTarget.Result).iconKey)
        // A suggestion that is a past search is one.
        assertTrue((rows[4][0].target as NavTarget.WebQuery).isRecent)
        assertFalse((rows[5][0].target as NavTarget.WebQuery).isRecent)
        // Every stop is unique, so the highlight is never on two at once.
        assertEquals(rows.flatten().size, rows.flatten().map { it.key }.toSet().size)
    }

    @Test
    fun theEmptyOverlaysStopsAreItsActionsPinsAndRecentSearches() {
        val action = DirectAction("Search with Lens", "Copied Image", ClipboardLens.ICON_TYPE, null)
        val rows = NavSources(directActions = listOf(action), pinned = listOf(ada, battery), recents = listOf("weather", "weather", "news")).rows()
        assertEquals(
            listOf(listOf(ResultListKeys.directAction(action)), listOf(ResultListKeys.PINNED, ResultListKeys.PINNED), listOf("recent_weather"), listOf("recent_news")),
            rows.map { row -> row.map { it.listKey } }
        )
        assertEquals(NavTarget.WebQuery("news", isRecent = true), rows[3][0].target)
    }

    @Test
    fun positionsAreFoundByKeySoTheHighlightFollowsItsResult() {
        val rows = NavSources(sectionOrder = listOf("contacts", "files"), contacts = listOf(ada), files = listOf(notes)).rows()
        assertEquals(NavPosition(1, 0), OverlayKeyboardNav.positionOf(ResultListKeys.file(notes), rows))
        assertNull(OverlayKeyboardNav.positionOf("gone", rows))
        assertNull(OverlayKeyboardNav.positionOf(null, rows))
    }

    @Test
    fun theWebSectionListsMatchingPastSearchesBeforeTheEnginesSuggestions() {
        val shown = displayedSuggestions(
            query = "cat",
            recentSearches = listOf("cat videos", "dogs", "Cat Videos"),
            searchPreviousSearches = true,
            webSuggestions = listOf(WebSuggestion("cat videos"), WebSuggestion("cats")),
            maxCount = 5
        )
        assertEquals(listOf("cat videos", "Cat Videos", "cats"), shown.map { it.query })
        assertEquals(listOf("cats"), displayedSuggestions("cat", listOf("cat videos"), false, listOf(WebSuggestion("cats")), 5).map { it.query })
        assertEquals(1, displayedSuggestions("cat", emptyList(), true, listOf(WebSuggestion("cats"), WebSuggestion("catalog")), 0).size)
    }

    @Test
    fun eachAnswerCardOpensItsMainAction() {
        assertEquals(AnswerAction.WebSearch("define hello"), instantAnswerAction(InstantAnswer("hello", "", "dictionary"), "define hello"))
        assertEquals(AnswerAction.OpenAddress("example.com"), instantAnswerAction(InstantAnswer("example.com", "", "url"), "example.com"))
        assertEquals(AnswerAction.ShowAlarms, instantAnswerAction(InstantAnswer("10:00", "", "time"), "time"))
        assertEquals(AnswerAction.WebSearch("weather"), instantAnswerAction(InstantAnswer("Sunny", "", "weather"), "weather"))
        // A conversion's card is for reading and copying; it has nothing to open.
        assertNull(instantAnswerAction(InstantAnswer("1 km = 0.62 mi", "", "conversion"), "1 km in mi"))
    }

    @Test
    fun theDetailPaneShowsTheHighlightElseTheTopResultOnceSomethingIsTyped() {
        val rows = NavSources(sectionOrder = listOf("contacts", "files"), contacts = listOf(ada), files = listOf(notes)).rows()
        assertEquals(ResultListKeys.contact(ada), detailStop(rows, null, "a")?.key)
        assertEquals(ResultListKeys.file(notes), detailStop(rows, ResultListKeys.file(notes), "a")?.key)
        assertNull(detailStop(rows, null, ""))
    }

    @Test
    fun onlyExpandedScreensOfMediumHeightOrMoreGetTheDetailPane() {
        // Unfolded Pixel Fold in landscape, a tablet in landscape.
        assertTrue(useListDetailLayout(841f, 701f))
        assertTrue(useListDetailLayout(1280f, 800f))
        // Unfolded and upright, a tablet upright, a phone, a phone on its side.
        assertFalse(useListDetailLayout(673f, 841f))
        assertFalse(useListDetailLayout(800f, 1280f))
        assertFalse(useListDetailLayout(412f, 915f))
        assertFalse(useListDetailLayout(915f, 412f))
    }
}
