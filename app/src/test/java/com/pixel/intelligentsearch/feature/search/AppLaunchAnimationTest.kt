package com.pixel.intelligentsearch.feature.search

import android.os.Bundle
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The App Animations setting: on, an app opened from the overlay grows out of where it was tapped; off, the system default plays. */
@RunWith(RobolectricTestRunner::class)
class AppLaunchAnimationTest {

    private val overlay = View(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 1000, 2000) }

    @Test
    fun offLeavesTheLaunchToTheSystemDefault() {
        assertNull(AppLaunchAnimation.options(overlay, enabled = false, origin = Offset(120f, 900f)))
        assertNull(AppLaunchAnimation.options(overlay, enabled = false, origin = null))
    }

    @Test
    fun onScalesTheAppUpFromTheTouchedPoint() {
        val options = assertScaleUp(AppLaunchAnimation.options(overlay, enabled = true, origin = Offset(120f, 900f)))
        assertEquals(120, options.getInt(START_X))
        assertEquals(900, options.getInt(START_Y))
    }

    @Test
    fun aLaunchWithoutATouchScalesUpFromTheMiddleOfTheOverlay() {
        val options = assertScaleUp(AppLaunchAnimation.options(overlay, enabled = true, origin = null))
        assertEquals(500, options.getInt(START_X))
        assertEquals(1000, options.getInt(START_Y))
    }

    private fun assertScaleUp(options: Bundle?): Bundle {
        assertNotNull("App Animations on must give the launch its own animation", options)
        assertEquals(ANIM_SCALE_UP, options!!.getInt(ANIM_TYPE))
        return options
    }

    companion object {
        // ActivityOptions' bundle keys and its scale-up animation type (hidden API constants).
        const val ANIM_TYPE = "android:activity.animType"
        const val START_X = "android:activity.animStartX"
        const val START_Y = "android:activity.animStartY"
        const val ANIM_SCALE_UP = 2
    }
}
