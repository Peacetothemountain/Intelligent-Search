package com.pixel.intelligentsearch.feature.search

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The App Animations setting: on, an app opened from the overlay opens out of the icon that was tapped, the way an app
 * opens from a launcher icon, and is told where on screen it came from; off, the system default plays.
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
    fun offLeavesTheLaunchToTheSystemDefault() {
        assertNull(AppLaunchAnimation.launch(overlay, enabled = false, icon = icon, touch = Offset(120f, 900f)))
        assertNull(AppLaunchAnimation.launch(overlay, enabled = false, icon = null, touch = null))
    }

    @Test
    fun onOpensTheAppOutOfTheTappedIconOnScreen() {
        val (x, y) = onScreen
        assertNotEquals("the overlay must sit away from the screen's corner for this test", 0, x + y)
        val launch = AppLaunchAnimation.launch(overlay, enabled = true, icon = icon, touch = Offset(120f, 910f))!!
        val options = assertClipReveal(launch.options)
        // The window grows out of the icon's own bounds (position and size), not out of a point.
        assertEquals(100 + x, options.getInt(START_X))
        assertEquals(900 + y, options.getInt(START_Y))
        assertEquals(48, options.getInt(WIDTH))
        assertEquals(48, options.getInt(HEIGHT))
        // The launched app is told the same bounds on screen, as a launcher tells it (Intent.getSourceBounds).
        assertEquals(android.graphics.Rect(100 + x, 900 + y, 148 + x, 948 + y), launch.sourceBounds)
    }

    @Test
    fun iconBoundsBetweenPixelsCoverTheWholeIcon() {
        val (x, y) = onScreen
        val launch = AppLaunchAnimation.launch(overlay, enabled = true, icon = Rect(100.4f, 900.6f, 148.2f, 948.5f), touch = null)!!
        assertEquals(android.graphics.Rect(100 + x, 900 + y, 149 + x, 949 + y), launch.sourceBounds)
    }

    @Test
    fun withoutAnIconTheAppOpensOutOfTheTouch() {
        val (x, y) = onScreen
        val launch = AppLaunchAnimation.launch(overlay, enabled = true, icon = null, touch = Offset(120f, 910f))!!
        val options = assertClipReveal(launch.options)
        assertEquals(120 + x, options.getInt(START_X))
        assertEquals(910 + y, options.getInt(START_Y))
        assertEquals(0, options.getInt(WIDTH))
        assertEquals(0, options.getInt(HEIGHT))
        assertNull("no icon, so no source bounds to hand the app", launch.sourceBounds)
    }

    @Test
    fun aLaunchWithNeitherIconNorTouchOpensOutOfTheMiddleOfTheOverlay() {
        val (x, y) = onScreen
        val launch = AppLaunchAnimation.launch(overlay, enabled = true, icon = null, touch = null)!!
        val options = assertClipReveal(launch.options)
        assertEquals(500 + x, options.getInt(START_X))
        assertEquals(1000 + y, options.getInt(START_Y))
        assertNull(launch.sourceBounds)
    }

    @Test
    fun anIconScrolledOutOfSightCountsAsNoIcon() {
        val launch = AppLaunchAnimation.launch(overlay, enabled = true, icon = Rect(100f, 900f, 100f, 900f), touch = null)!!
        assertNull(launch.sourceBounds)
    }

    @Test
    fun theAppOpensOnTheOverlaysDisplay() {
        val options = AppLaunchAnimation.launch(overlay, enabled = true, icon = icon, touch = null)!!.options!!
        assertEquals(overlay.display.displayId, options.getInt(LAUNCH_DISPLAY_ID, -1))
    }

    @Test
    fun fromAndroid13TheAppsSplashScreenShowsItsIconAsFromTheLauncher() {
        val options = AppLaunchAnimation.launch(overlay, enabled = true, icon = icon, touch = null)!!.options!!
        assertEquals(SPLASH_SCREEN_STYLE_ICON, options.getInt(SPLASH_SCREEN_STYLE, -1))
    }

    @Test
    @Config(sdk = [32])
    fun onAndroid12TheAppStillOpensOutOfTheIconWithItsOwnSplashScreen() {
        val (x, y) = onScreen
        val launch = AppLaunchAnimation.launch(overlay, enabled = true, icon = icon, touch = null)!!
        val options = assertClipReveal(launch.options)
        assertEquals(100 + x, options.getInt(START_X))
        assertEquals(48, options.getInt(WIDTH))
        assertEquals(android.graphics.Rect(100 + x, 900 + y, 148 + x, 948 + y), launch.sourceBounds)
        // The splash screen style option arrived in Android 13; Android 12 isn't asked for it.
        assertNotEquals(SPLASH_SCREEN_STYLE_ICON, options.getInt(SPLASH_SCREEN_STYLE, -1))
    }

    private fun assertClipReveal(options: Bundle?): Bundle {
        assertNotNull("App Animations on must give the launch its own animation", options)
        assertEquals(ANIM_CLIP_REVEAL, options!!.getInt(ANIM_TYPE))
        return options
    }

    companion object {
        // ActivityOptions' bundle keys and its clip-reveal animation type (hidden API constants).
        const val ANIM_TYPE = "android:activity.animType"
        const val START_X = "android:activity.animStartX"
        const val START_Y = "android:activity.animStartY"
        const val WIDTH = "android:activity.animWidth"
        const val HEIGHT = "android:activity.animHeight"
        const val ANIM_CLIP_REVEAL = 11
        const val LAUNCH_DISPLAY_ID = "android.activity.launchDisplayId"
        const val SPLASH_SCREEN_STYLE = "android.activity.splashScreenStyle"
        const val SPLASH_SCREEN_STYLE_ICON = 1
    }
}
