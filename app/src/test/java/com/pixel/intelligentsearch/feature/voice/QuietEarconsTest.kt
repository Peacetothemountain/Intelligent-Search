package com.pixel.intelligentsearch.feature.voice

import android.app.Activity
import android.app.Application
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAudioManager

/** The notification channel muted for the speech engine's tones always comes back, even when an unmute is dropped. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [QuietEarconsTest.DroppingAudio::class])
class QuietEarconsTest {

    /** Android 17 can ignore a volume change from an app with nothing on screen; this stands in for that. */
    @Implements(AudioManager::class)
    class DroppingAudio : ShadowAudioManager() {
        @Implementation
        override fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int) {
            if (direction == AudioManager.ADJUST_UNMUTE && dropUnmute) return
            super.adjustStreamVolume(streamType, direction, flags)
        }
    }

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val audio = app.getSystemService(AudioManager::class.java)
    private val notifications get() = audio.isStreamMute(AudioManager.STREAM_NOTIFICATION)

    @After
    fun reset() {
        dropUnmute = false
    }

    @Test
    fun theChannelIsQuietWhileATonePlaysAndBackAfter() {
        val tones = QuietEarcons(app, Handler(Looper.getMainLooper()))
        tones.hold()
        assertTrue(notifications)
        tones.restore()
        assertFalse(notifications)
    }

    @Test
    fun anUnmuteDroppedAfterTheScreenClosedIsTakenBackWhenAScreenShows() {
        val tones = QuietEarcons(app, Handler(Looper.getMainLooper()))
        tones.hold()
        dropUnmute = true
        tones.restore()
        assertTrue("the dropped unmute left the channel muted", notifications)

        dropUnmute = false
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        QuietEarcons.LeftMutedCheck.onActivityResumed(activity)
        assertFalse(notifications)
    }

    @Test
    fun aMuteTheAppIsStillHoldingIsLeftAlone() {
        val tones = QuietEarcons(app, Handler(Looper.getMainLooper()))
        tones.hold()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        QuietEarcons.LeftMutedCheck.onActivityResumed(activity)
        assertTrue(notifications)
        tones.restore()
    }

    private companion object {
        @JvmStatic
        var dropUnmute = false
    }
}
