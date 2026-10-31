package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.view.View
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.feature.search.SearchActivity
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Back gestures on the Settings pages: a cancelled swipe puts the page back without dropping the next swipe's
 * refresh rate, and back on a sub-page returns to Main.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class SettingsNavHostBackTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    // Before the activity starts: a finished tutorial keeps a launcher start on Settings, and the hand-rolled
    // background loops stay still so the clock can be driven by hand.
    @get:Rule(order = 1) val setUp = object : ExternalResource() {
        override fun before() {
            ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(SettingsManager.TUTORIAL_COMPLETED.name, true).commit()
            disableHandRolledAnimations()
        }
    }

    @get:Rule(order = 2) val compose = createAndroidComposeRule<SettingsActivity>(confinedEffects())

    @Test
    fun cancellingTheExitSwipePutsThePageBack() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        val title = compose.onNodeWithText("Settings")
        val before = title.fetchSemanticsNode().boundsInRoot

        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(240f, 400f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 2)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.settle(steps = 10)

        assertEquals(before, title.fetchSemanticsNode().boundsInRoot)
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    @Config(sdk = [35])
    fun aSwipeStartedWhileACancelledOneSpringsBackKeepsTheHighRefreshRate() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(240f, 400f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 2)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        // The cancelled page is still springing back when the next swipe starts.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 400f, 0.3f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 3)

        val window = compose.activity.window
        assertEquals(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH, window.decorView.requestedFrameRate)
        assertTrue(window.attributes.frameRateBoostOnTouchEnabled)
    }

    @Test
    fun backOnASubPageReturnsToMain() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.settle(steps = 5)

        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Launch Portal").assertExists()
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    fun aSwipeLetGoOnASubPagePlaysTheReleaseThenReturnsToMain() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)

        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 1000f, 0.7f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 2)
        // Mid-swipe the page is still Appearance, only moved.
        compose.onNodeWithText("Search Overlay").assertExists()

        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.settle(steps = 10)

        compose.onNodeWithText("Launch Portal").assertExists()
        compose.onNodeWithText("Search Overlay").assertDoesNotExist()
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    fun aCancelledSwipeOnASubPageStaysThere() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)
        val section = compose.onNodeWithText("Search Overlay")
        val before = section.fetchSemanticsNode().boundsInRoot

        val dispatcher = compose.activity.onBackPressedDispatcher
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

    @Test
    fun backOnMainLeavesThroughTheExitHandlerAfterASettingChangedOnASubPage() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)
        // A setting changing redraws the whole settings shell while the sub-page is open.
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        compose.runOnIdle { prefs.edit().putBoolean(SettingsManager.VIBRATION.name, !prefs.getBoolean(SettingsManager.VIBRATION.name, true)).commit() }
        compose.settle(steps = 5)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.settle(steps = 5)
        compose.onNodeWithText("Launch Portal").assertExists()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.settle(steps = 5)

        // The exit handler goes back to the search overlay; the system's default back would only close Settings.
        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(SearchActivity::class.java.name, started?.component?.className)
        assertTrue(started.getBooleanExtra("FROM_BACK_SWIPE", false))
    }
}
