package com.pixel.intelligentsearch.core.system

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** At a Glance's seek bar: a session's length and live position, and whether it takes a seek. */
@RunWith(RobolectricTestRunner::class)
class MediaProgressTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val session by lazy { MediaSession(context, "progress") }

    @After fun tearDown() = session.release()

    @Test
    fun thePositionMovesOnAtTheSpeedSinceItWasReported() {
        val playing = MediaSessionAccess.progressOf(200_000L, PlaybackState.STATE_PLAYING, 60_000L, 1_000L, 1f, 0L)!!
        assertEquals(60_000L, MediaSessionAccess.livePositionMs(playing, 1_000L))
        assertEquals(65_000L, MediaSessionAccess.livePositionMs(playing, 6_000L))
        // Never past the end, nor before the report.
        assertEquals(200_000L, MediaSessionAccess.livePositionMs(playing, 10_000_000L))
        assertEquals(60_000L, MediaSessionAccess.livePositionMs(playing, 0L))
        // Twice as fast at 2x.
        val fast = MediaSessionAccess.progressOf(200_000L, PlaybackState.STATE_PLAYING, 0L, 0L, 2f, 0L)!!
        assertEquals(20_000L, MediaSessionAccess.livePositionMs(fast, 10_000L))
    }

    @Test
    fun pausedOrBufferingItHoldsStill() {
        for (state in listOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_STOPPED)) {
            val progress = MediaSessionAccess.progressOf(200_000L, state, 60_000L, 1_000L, 1f, 0L)!!
            assertEquals(0f, progress.speed)
            assertEquals(60_000L, MediaSessionAccess.livePositionMs(progress, 50_000L))
        }
    }

    @Test
    fun withoutALengthOrAPositionThereIsNoBar() {
        assertNull(MediaSessionAccess.progressOf(0L, PlaybackState.STATE_PLAYING, 0L, 0L, 1f, 0L))
        assertNull(MediaSessionAccess.progressOf(200_000L, PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0L, 1f, 0L))
        // A position reported past the end reads as the end.
        assertEquals(200_000L, MediaSessionAccess.progressOf(200_000L, PlaybackState.STATE_PAUSED, 250_000L, 0L, 1f, 0L)!!.positionMs)
    }

    @Test
    fun onlyAPlayerThatTakesASeekCanBeSought() {
        assertTrue(MediaSessionAccess.canSeek(PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_PLAY_PAUSE))
        assertFalse(MediaSessionAccess.canSeek(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT))
        assertTrue(MediaSessionAccess.progressOf(1_000L, PlaybackState.STATE_PLAYING, 0L, 0L, 1f, PlaybackState.ACTION_SEEK_TO)!!.canSeek)
        assertFalse(MediaSessionAccess.progressOf(1_000L, PlaybackState.STATE_PLAYING, 0L, 0L, 1f, 0L)!!.canSeek)
    }

    @Test
    fun aSessionsProgressComesFromItsDurationAndPlaybackState() {
        val controller = session.controller
        shadowOf(controller).setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Midnight City")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 243_000L)
                .build()
        )
        shadowOf(controller).setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 98_000L, 1f, 5_000L)
                .setActions(PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_PLAY_PAUSE)
                .build()
        )
        assertEquals(243_000L, MediaSessionAccess.durationOf(controller))
        assertEquals(
            MediaSessionAccess.Progress(durationMs = 243_000L, positionMs = 98_000L, updatedAtElapsed = 5_000L, speed = 1f, canSeek = true),
            MediaSessionAccess.progressOf(controller)
        )

        // A live stream has no length, so no bar.
        shadowOf(controller).setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Radio").build())
        assertNull(MediaSessionAccess.durationOf(controller))
        assertNull(MediaSessionAccess.progressOf(controller))
    }
}
