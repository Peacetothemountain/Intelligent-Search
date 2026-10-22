package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings sliders sit inside scrolling pages: a vertical swipe that happens to start on one must scroll the
 * page and leave the setting alone, while a sideways drag still moves the slider.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h640dp-xhdpi")
class SettingsSliderGestureTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    // Scrolling runs in a coroutine; with queued effects performScrollTo would keep asking for a scroll that never
    // runs, so this rule runs effects immediately. Nothing here waits on background work.
    @get:Rule(order = 1) val compose = createComposeRule()

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppearanceScreen(prefs, {}, {}) } }
        compose.onNodeWithContentDescription("Background Blur").performScrollTo()
        compose.waitForIdle()
    }

    // Unclipped, so it still moves while the row is below the visible part of the page.
    private fun lowerRowTop() = compose.onNodeWithText("Search Pill Opacity").fetchSemanticsNode().positionInRoot.y

    @Test
    fun aVerticalSwipeStartingOnASliderScrollsThePage() {
        val topBefore = lowerRowTop()
        compose.onNodeWithContentDescription("Background Blur").performTouchInput {
            val start = Offset(width * 0.1f, centerY)
            down(start)
            repeat(10) { moveBy(Offset(0f, -30f)) }
            up()
        }
        compose.waitForIdle()

        assertEquals(50, prefs[SettingsManager.BACKGROUND_BLUR])
        assertTrue("the page should have scrolled up", lowerRowTop() < topBefore)
    }

    @Test
    fun aTapStillSetsTheValue() {
        compose.onNodeWithContentDescription("Background Blur").performTouchInput { click(Offset(width * 0.1f, centerY)) }
        compose.waitForIdle()

        assertEquals(10, prefs[SettingsManager.BACKGROUND_BLUR])
    }

    @Test
    fun aSidewaysDragStillMovesTheSlider() {
        compose.onNodeWithContentDescription("Background Blur").performTouchInput {
            down(Offset(width * 0.5f, centerY))
            repeat(10) { moveBy(Offset(-width * 0.04f, 0f)) }
            up()
        }
        compose.waitForIdle()

        assertNotEquals(50, prefs[SettingsManager.BACKGROUND_BLUR])
    }
}
