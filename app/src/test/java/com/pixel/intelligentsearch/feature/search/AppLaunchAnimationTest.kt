package com.pixel.intelligentsearch.feature.search

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How an app opened from the overlay is started, once the overlay has grown it out of the tapped icon (see
 * AppOpeningLayer): told where on screen it came from, as from a launcher icon, on the overlay's display, with its
 * icon on the splash screen. It asks for no opening animation of its own: Android 17 drops those for anything but the
 * launcher, which is why the overlay draws the motion itself.
 */
@RunWith(RobolectricTestRunner::class)
class AppLaunchAnimationTest {

    /** The overlay's view, placed away from the screen's corner so screen and view coordinates differ. */
    private val overlay: View = run {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = View(activity)
        activity.setContentView(FrameLayout(activity).apply {
            addView(view, FrameLayout.LayoutParams(1000, 2000).apply { leftMargin = 40; topMargin = 120 })
        })
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY))
        (view.parent as View).layout(0, 0, 1080, 2400)
        view.layout(40, 120, 1040, 2120)
        view
    }
    private val onScreen: IntArray get() = IntArray(2).also(overlay::getLocationOnScreen)

    /** A 48px icon at (100, 900) in the overlay's view. */
    private val icon = Rect(100f, 900f, 148f, 948f)

    @Test
    fun theAppIsToldTheTappedIconsBoundsOnScreen() {
        val (x, y) = onScreen
        assertNotEquals("the overlay must sit away from the screen's corner for this test", 0, x + y)
        val launch = AppLaunchAnimation.launch(overlay, icon)
        // As a launcher tells it (Intent.getSourceBounds).
        assertEquals(android.graphics.Rect(100 + x, 900 + y, 148 + x, 948 + y), launch.sourceBounds)
    }

    @Test
    fun iconBoundsBetweenPixelsCoverTheWholeIcon() {
        val (x, y) = onScreen
        val launch = AppLaunchAnimation.launch(overlay, Rect(100.4f, 900.6f, 148.2f, 948.5f))
        assertEquals(android.graphics.Rect(100 + x, 900 + y, 149 + x, 949 + y), launch.sourceBounds)
    }

    @Test
    fun withoutAnIconThereAreNoSourceBounds() {
        assertNull(AppLaunchAnimation.launch(overlay, null).sourceBounds)
    }

    @Test
    fun anIconScrolledOutOfSightCountsAsNoIcon() {
        assertNull(AppLaunchAnimation.launch(overlay, Rect(100f, 900f, 100f, 900f)).sourceBounds)
    }

    @Test
    fun theLaunchAsksForNoOpeningAnimationOfItsOwn() {
        val options = AppLaunchAnimation.launch(overlay, icon).options!!
        assertNotEquals(ANIM_CLIP_REVEAL, options.getInt(ANIM_TYPE, 0))
        assertNotEquals(ANIM_SCALE_UP, options.getInt(ANIM_TYPE, 0))
    }

    @Test
    fun theAppOpensOnTheOverlaysDisplay() {
        val options = AppLaunchAnimation.launch(overlay, icon).options!!
        assertEquals(overlay.display.displayId, options.getInt(LAUNCH_DISPLAY_ID, -1))
    }

    @Test
    fun fromAndroid13TheAppsSplashScreenShowsItsIconAsFromTheLauncher() {
        val options = AppLaunchAnimation.launch(overlay, icon).options!!
        assertEquals(SPLASH_SCREEN_STYLE_ICON, options.getInt(SPLASH_SCREEN_STYLE, -1))
    }

    @Test
    @Config(sdk = [32])
    fun onAndroid12TheAppKeepsItsOwnSplashScreen() {
        val (x, y) = onScreen
        val launch = AppLaunchAnimation.launch(overlay, icon)
        assertEquals(android.graphics.Rect(100 + x, 900 + y, 148 + x, 948 + y), launch.sourceBounds)
        // The splash screen style option arrived in Android 13; Android 12 isn't asked for it.
        assertNotEquals(SPLASH_SCREEN_STYLE_ICON, launch.options!!.getInt(SPLASH_SCREEN_STYLE, -1))
    }

    companion object {
        // ActivityOptions' bundle keys and its animation types (hidden API constants).
        const val ANIM_TYPE = "android:activity.animType"
        const val ANIM_SCALE_UP = 2
        const val ANIM_CLIP_REVEAL = 11
        const val LAUNCH_DISPLAY_ID = "android.activity.launchDisplayId"
        const val SPLASH_SCREEN_STYLE = "android.activity.splashScreenStyle"
        const val SPLASH_SCREEN_STYLE_ICON = 1
    }
}
