package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the web shortcut editor fills in after an app is picked. */
class WebShortcutSuggestionTest {

    /** An app it doesn't know gets no address: one made up from the app's name could send queries to a stranger. */
    @Test
    fun anUnknownAppGetsNoSearchAddress() {
        val (slug, url) = suggestedWebShortcut("com.example.notes")
        assertNull(slug)
        assertNull(url)
    }

    @Test
    fun aKnownAppGetsItsTriggerAndAddress() {
        assertEquals("yt" to "https://www.youtube.com/results?search_query=%s", suggestedWebShortcut("com.google.android.youtube"))
    }

    /** Picking an app used to replace any address ending in "/search?q=%s" with one made up from the app's name. */
    @Test
    fun aTypedAddressSurvivesPickingAnApp() {
        val typed = "https://example.org/search?q=%s"
        val (prefix, url) = fieldsForPickedApp(
            pkg = "com.example.alpha", appName = "Alpha Notes", trigger = "!",
            prefix = "!", url = typed, lastSuggestedPrefix = null, lastSuggestedUrl = null
        )
        assertEquals(typed, url)
        assertEquals("!alph", prefix)
    }

    @Test
    fun aTypedTriggerSurvivesPickingAnApp() {
        val (prefix, url) = fieldsForPickedApp(
            pkg = "com.google.android.youtube", appName = "YouTube", trigger = "!",
            prefix = "!vids", url = "", lastSuggestedPrefix = null, lastSuggestedUrl = null
        )
        assertEquals("!vids", prefix)
        assertEquals("https://www.youtube.com/results?search_query=%s", url)
    }

    /** The picker's own earlier suggestions give way to the next pick's. */
    @Test
    fun aSecondPickReplacesTheFirstPicksSuggestions() {
        val (prefix, url) = fieldsForPickedApp(
            pkg = "com.spotify.music", appName = "Spotify", trigger = "!",
            prefix = "!yt", url = "https://www.youtube.com/results?search_query=%s",
            lastSuggestedPrefix = "!yt", lastSuggestedUrl = "https://www.youtube.com/results?search_query=%s"
        )
        assertEquals("!spot", prefix)
        assertEquals("https://open.spotify.com/search/%s", url)
    }

    @Test
    fun anUnknownAppLeavesABlankAddressBlank() {
        val (_, url) = fieldsForPickedApp(
            pkg = "com.example.notes", appName = "Notes", trigger = "!",
            prefix = "!", url = "", lastSuggestedPrefix = null, lastSuggestedUrl = null
        )
        assertEquals("", url)
    }
}
