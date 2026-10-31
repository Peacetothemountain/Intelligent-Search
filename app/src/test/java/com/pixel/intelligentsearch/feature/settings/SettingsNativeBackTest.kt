package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

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

    private fun prefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

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
    @Config(sdk = [35])
    fun aCancelledQuickSecondSwipeFromTheFirstSubPageGivesTheRefreshRateBack() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)
        swipeBack()
        repeat(18) { compose.mainClock.advanceTimeByFrame() }
        // The first swipe was going back to Main; the second, as a swipe on Main, is cancelled. The page that left
        // had handed it the high refresh rate, and nothing gave it back.
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(240f, 900f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 2)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.settle(steps = 15)
        assertOnMain()
        assertFalse(compose.activity.isFinishing)
        assertEquals(android.view.View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE, compose.activity.window.decorView.requestedFrameRate)
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
    /**
     * Just after letting go, Main is only a picture, and the real page under it is Appearance, leaving, invisible. A tap
     * on one of Appearance's switches where it is now drawn, and one on Main's Launch Portal row as the user sees it,
     * must change nothing and open nothing: the first used to turn System Vibration off unseen.
     */
    @Test
    fun aTapAsThePageUnderneathSettlesReachesNoPage() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        val launchPortal = compose.onNodeWithText("Launch Portal").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)
        val before = prefs().all
        swipeBack()
        // About 50 ms after letting go, while Appearance is still fading out.
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        val vibration = compose.onNodeWithText("System Vibration").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { click(vibration) }
        compose.onRoot().performTouchInput { click(launchPortal) }
        compose.settle(steps = 15)
        assertEquals(before, prefs().all)
        assertOnMain()
    }

    /**
     * Once the page that left is out of sight, the real page underneath settles the rest of the way itself, so a tap
     * reaches it at once: about 260 ms after letting go, with part of the settle still to come, a tap on Launch Portal
     * opens it. It used to be held back for the whole animation and the hand-over, which made the next tap feel slow.
     */
    @Test
    fun aTapWhileThePageUnderneathSettlesReachesIt() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        val launchPortal = compose.onNodeWithText("Launch Portal").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)
        swipeBack()
        repeat(16) { compose.mainClock.advanceTimeByFrame() }
        compose.onRoot().performTouchInput { click(launchPortal) }
        compose.settle(steps = 15)
        compose.onNodeWithText("Always Within Reach").assertExists()
    }

    /** Once the page underneath is really shown, taps reach it again. */
    @Test
    fun aTapOnceThePageHasSettledReachesIt() {
        compose.mainClock.autoAdvance = false
        compose.settle(steps = 5)
        val launchPortal = compose.onNodeWithText("Launch Portal").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithText("Appearance").performClick()
        compose.settle(steps = 5)
        swipeBack()
        compose.settle(steps = 10)
        compose.onRoot().performTouchInput { click(launchPortal) }
        compose.settle(steps = 15)
        compose.onNodeWithText("Always Within Reach").assertExists()
    }

    /**
     * The back arrow belongs to the page that is leaving, so in that half second it is held back with every other tap
     * rather than sent there; tapped again once the page is shown, it goes back.
     */
    @Test
    fun theBackArrowRightAfterASwipeIsHeldBackUntilThePageIsShown() {
        openCustomIcons()
        val arrow = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot.center
        swipeBack()
        repeat(12) { compose.mainClock.advanceTimeByFrame() }
        compose.onRoot().performTouchInput { click(arrow) }
        compose.settle(steps = 15)
        assertOnAppearance()
        compose.onRoot().performTouchInput { click(arrow) }
        compose.settle(steps = 15)
        assertOnMain()
    }

    /**
     * Two quick swipes: as the second starts, the page the first sent away (Custom Icons) must not come back. Its
     * title is looked for wherever it is laid out, frame by frame; it used to show there, whole, for a frame or two.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aSecondQuickSwipeNeverShowsThePageTheFirstSentAway() {
        openCustomIcons()
        val title = compose.onAllNodesWithText("Custom Icons").fetchSemanticsNodes().single()
        val reference = crop(compose.onRoot().captureToImage().asAndroidBitmap(), title.boundsInRoot)
        swipeBack()
        repeat(18) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 900f, 0.7f, BackEventCompat.EDGE_LEFT))
        }
        val likeness = mutableListOf<Float>()
        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            val frame = compose.onRoot().captureToImage().asAndroidBitmap()
            // Only the title (Appearance's own Custom Icons row is laid out at another size).
            compose.onAllNodesWithText("Custom Icons").fetchSemanticsNodes().filter { it.size == title.size }.forEach {
                likeness += difference(reference, crop(frame, it.boundsInRoot))
            }
        }
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.settle(steps = 15)
        assertOnMain()
        assertTrue("the page that left showed again: differences from its title $likeness", likeness.all { it > 0.05f })
    }

    private fun crop(bitmap: Bitmap, bounds: Rect): Bitmap {
        val left = bounds.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = bounds.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = bounds.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = bounds.bottom.toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /** Mean difference per color channel (0 to 1) between [a] and [b] scaled to [a]'s size. */
    private fun difference(a: Bitmap, b: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(b, a.width, a.height, true)
        var sum = 0L
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getPixel(x, y)
            val q = scaled.getPixel(x, y)
            for (shift in intArrayOf(0, 8, 16)) sum += abs((p shr shift and 0xFF) - (q shr shift and 0xFF))
        }
        return sum / (a.width * a.height * 3 * 255f)
    }
}
