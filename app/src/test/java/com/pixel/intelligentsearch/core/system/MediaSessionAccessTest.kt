package com.pixel.intelligentsearch.core.system

import android.app.NotificationManager
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The widget's media row shows (and its buttons control) the session At a Glance would, never a hidden app's. */
@RunWith(RobolectricTestRunner::class)
class MediaSessionAccessTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var session: MediaSession

    @Before
    fun setUp() {
        SettingsManager.storedPrefs(context).edit().clear().commit()
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(NotificationAccess.component(context), true)
        session = MediaSession(context, "test")
        shadowOf(context.getSystemService(MediaSessionManager::class.java)).addController(player(MUSIC, "Golden Hour"))
    }

    /** A music app's session as the media session manager hands it over, [title] playing. */
    private fun player(packageName: String, title: String): MediaController = session.controller.also {
        shadowOf(it).setPackageName(packageName)
        shadowOf(it).setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build())
        shadowOf(it).setPlaybackState(
            PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0L, 1f, SystemClock.elapsedRealtime()).build()
        )
    }

    @After
    fun tearDown() = session.release()

    @Test
    fun thePlayingSessionShows() {
        assertEquals("Golden Hour", MediaSessionAccess.nowPlaying(context)?.title)
    }

    @Test
    fun aHiddenAppsSessionNeverShows() {
        SettingsManager.storedPrefs(context).edit().put(SettingsManager.HIDDEN_APPS, setOf(MUSIC)).commit()
        assertNull(MediaSessionAccess.current(context))
        assertNull(MediaSessionAccess.nowPlaying(context))
    }

    private companion object {
        const val MUSIC = "com.example.music"
    }
}
