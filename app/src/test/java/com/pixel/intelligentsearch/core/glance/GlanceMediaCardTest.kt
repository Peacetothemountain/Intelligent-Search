package com.pixel.intelligentsearch.core.glance

import android.graphics.Bitmap
import android.media.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What At a Glance's media card shows for what the playing app reports. */
@RunWith(RobolectricTestRunner::class)
class GlanceMediaCardTest {

    private fun metadata(artist: String? = null, albumArtist: String? = null, subtitle: String? = null) =
        MediaMetadata.Builder().apply {
            artist?.let { putString(MediaMetadata.METADATA_KEY_ARTIST, it) }
            albumArtist?.let { putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, it) }
            subtitle?.let { putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, it) }
        }.build()

    @Test
    fun theArtistComesFirstThenTheAlbumsThenTheSubtitle() {
        assertEquals("M83", glanceArtistOf(metadata("M83", "Various", "Podcast")))
        assertEquals("Various", glanceArtistOf(metadata(null, "Various", "Podcast")))
        assertEquals("Podcast", glanceArtistOf(metadata(null, null, "Podcast")))
    }

    @Test
    fun noArtistIsBlankAndSpacesAreTrimmed() {
        assertEquals("", glanceArtistOf(metadata()))
        assertEquals("M83", glanceArtistOf(metadata("  M83 ")))
    }

    @Test
    fun largeArtworkShrinksToItsShortSideKeepingItsShape() {
        val scaled = scaledGlanceArtwork(Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888), 100)!!
        assertEquals(200, scaled.width)
        assertEquals(100, scaled.height)
    }

    @Test
    fun smallArtworkIsLeftAlone() {
        val small = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        assertSame(small, scaledGlanceArtwork(small, 100))
    }
}
