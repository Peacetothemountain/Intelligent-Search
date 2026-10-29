package com.pixel.intelligentsearch.core.search

import android.app.SearchManager
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "play ..." asks a media app to search and play, sent to Spotify or YouTube Music when the query names one. */
@RunWith(RobolectricTestRunner::class)
class MediaPlayIntentTest {

    private fun play(query: String) = NaturalLanguageIntentParser.parse(query) as NaturalLanguageIntentParser.ParsedIntent.MediaPlay

    @Test
    fun playAsksAnyMediaAppToSearchAndPlay() {
        val play = play("play Bohemian Rhapsody")

        assertEquals("Bohemian Rhapsody", play.query)
        assertNull(play.appName)
        assertEquals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH, play.intent.action)
        assertEquals("Bohemian Rhapsody", play.intent.getStringExtra(SearchManager.QUERY))
        assertEquals("vnd.android.cursor.item/*", play.intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS))
        assertNull(play.intent.`package`)
        assertNull(play.fallbackIntent)
    }

    @Test
    fun aNamedAppGetsTheSearchAndAnyPlayerIsTheFallback() {
        val spotify = play("play Taylor Swift on Spotify")
        assertEquals("Taylor Swift", spotify.query)
        assertEquals("Spotify", spotify.appName)
        assertEquals("com.spotify.music", spotify.intent.`package`)
        assertEquals("Taylor Swift", spotify.intent.getStringExtra(SearchManager.QUERY))
        assertEquals("vnd.android.cursor.item/*", spotify.intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS))
        // Opened when Spotify isn't installed: the same search for whichever player there is.
        val fallback = spotify.fallbackIntent!!
        assertEquals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH, fallback.action)
        assertNull(fallback.`package`)
        assertEquals("Taylor Swift", fallback.getStringExtra(SearchManager.QUERY))

        for (query in listOf("play lo-fi beats on YouTube Music", "Play lo-fi beats in yt music", "play  lo-fi beats   on youtube music")) {
            val music = play(query)
            assertEquals(query, "lo-fi beats", music.query)
            assertEquals(query, "YouTube Music", music.appName)
            assertEquals(query, "com.google.android.apps.youtube.music", music.intent.`package`)
        }
    }

    @Test
    fun googlePlaySearchesAndVideosAreNotMediaActions() {
        for (query in listOf("play store", "Play Games", "play books", "play console", "play", "play ", "play !!", "playlist", "play cats on youtube")) {
            val parsed = NaturalLanguageIntentParser.parse(query)
            assertFalse(query, parsed is NaturalLanguageIntentParser.ParsedIntent.MediaPlay)
        }
    }
}
