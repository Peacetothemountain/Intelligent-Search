package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back swipes between Settings pages with Android's animations on, as on a phone: they play Android's own back
 * animation (SettingsNavHostBackTest turns animations off, where the plain transition plays instead).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class SettingsNativeBackTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1) val setUp = object : ExternalResource() {
        override fun before() {
            ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(SettingsManager.TUTORIAL_COMPLETED.name, true).commit()
            // ValueAnimator.setDurationScale is hidden; inside the Robolectric sandbox reflection reaches it.
            android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 1f)
        }
    }

    @get:Rule(order = 2) val compose = createAndroidComposeRule<SettingsActivity>(confinedEffects())

    private val dispatcher get() = compose.activity.onBackPressedDispatcher

    /** A back swipe from the left edge, let go most of the way across. */
    private fun swipeBack() {
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 900f, 0.7f, BackEventCompat.EDGE_LEFT))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.onBackPressed() }
    }

    /** Main, then Appearance, then Custom Icons. */
    private fun openCustomIcons() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        assertTrue("animations on", android.animation.ValueAnimator.areAnimatorsEnabled())
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Custom Icons").performClick()
        compose.settle(steps = 5)
    }

    private fun assertOnAppearance() {
        compose.onNodeWithText("Search Overlay").assertExists()
        compose.onNodeWithText("Launch Portal").assertDoesNotExist()
    }

    private fun assertOnMain() {
        compose.onNodeWithText("Launch Portal").assertExists()
        compose.onNodeWithText("Search Overlay").assertDoesNotExist()
    }

    @Test
    fun aSwipeGoesBackOnePage() {
        openCustomIcons()
        swipeBack()
        compose.settle(steps = 15)
        assertOnAppearance()
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    fun twoQuickSwipesGoBackTwoPages() {
        openCustomIcons()
        swipeBack()
        // About 300 ms later, while the first page is still leaving (450 ms), the user swipes again. That swipe
        // used to cancel the first one's animation before its page had left, keeping the user on Appearance.
        repeat(18) { compose.mainClock.advanceTimeByFrame() }
        swipeBack()
        compose.settle(steps = 15)
        assertOnMain()
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    fun twoQuickSwipesFromTheFirstSubPageLeaveSettings() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)
        swipeBack()
        repeat(18) { compose.mainClock.advanceTimeByFrame() }
        // The first swipe was going back to Main: the second goes on from there, as a swipe on Main does.
        swipeBack()
        compose.settle(steps = 15)
        assertTrue(compose.activity.isFinishing)
    }

    @Test
    fun aSecondSwipeCancelledHalfwayLeavesTheUserWhereTheFirstWent() {
        openCustomIcons()
        swipeBack()
        repeat(18) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(240f, 900f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 2)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.settle(steps = 15)
        assertOnAppearance()
    }

    @Test
    fun aCancelledSwipeStaysOnThePage() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)
        val section = compose.onNodeWithText("Search Overlay")
        val before = section.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 900f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 2)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.settle(steps = 10)
        assertEquals(before, section.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Launch Portal").assertDoesNotExist()
    }
}
