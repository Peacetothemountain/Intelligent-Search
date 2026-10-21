package com.pixel.intelligentsearch.core.profile

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * An app opened from the overlay is started with where it came from: the tapped icon's bounds on screen and the
 * launch's animation, in the personal profile and through LauncherApps for work and Private Space apps alike.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [RecordingLauncherApps::class])
class LaunchSourceBoundsTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val manager = MultiProfileManager(activity)
    private val icon = Rect(140, 1020, 188, 1068)
    private val options = Bundle().apply { putInt("android:activity.animType", 11) }

    @Before fun forgetStarts() = RecordingLauncherApps.starts.clear()

    @Test
    fun aPersonalAppIsToldTheIconItWasOpenedFromAndGetsTheAnimation() {
        manager.launchApp("com.example.camera", null, "com.example.camera.Main", sourceBounds = icon, opts = options, activity = activity)
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(icon, started.intent.sourceBounds)
        assertSame(options, started.options)
    }

    @Test
    fun aWorkOrPrivateSpaceAppIsStartedThroughLauncherAppsWithTheSameSource() {
        val work = UserHandle.getUserHandleForUid(10 * 100_000 + 10_123)
        manager.launchApp("com.example.camera", work, "com.example.camera.Main", sourceBounds = icon, opts = options, activity = activity)
        val start = RecordingLauncherApps.starts.single()
        assertEquals("com.example.camera/com.example.camera.Main", start.target)
        assertEquals(work, start.user)
        assertEquals(icon, start.sourceBounds)
        assertSame(options, start.options)
    }

    @Test
    fun withoutASourceTheAppIsStartedAsBefore() {
        manager.launchApp("com.example.camera", null, "com.example.camera.Main", activity = activity)
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNull(started.intent.sourceBounds)
        assertNull(started.options)
    }
}
