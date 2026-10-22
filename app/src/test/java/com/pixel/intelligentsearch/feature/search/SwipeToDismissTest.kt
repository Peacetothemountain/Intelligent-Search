package com.pixel.intelligentsearch.feature.search

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.haptics.RecordingViewHaptics
import com.pixel.intelligentsearch.core.haptics.rememberTactileSonicEngine
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every swipe-away row must also be dismissable through TalkBack's custom actions, and a swipe feels like the back
 * gesture: its commit haptic as the drag crosses the threshold and its cancel haptic as it is pulled back under it.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SwipeToDismissTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    @Test
    fun theDismissActionRemovesTheRow() {
        var dismissedCount = 0
        compose.setContent {
            val swipe = rememberSwipeToDismiss(rememberTactileSonicEngine(), LocalView.current) { dismissedCount++ }
            Box(Modifier.size(200.dp).swipeOffset(swipe).swipeToDismiss(swipe, "row")) { Text("Recent search") }
        }
        val row = compose.onNode(hasAnyChild(hasText("Recent search")), useUnmergedTree = true)
        val actions = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Dismiss"), actions.map { it.label })

        compose.runOnUiThread { actions.single().action() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(1, dismissedCount)
    }

    private var dismissedCount = 0

    private fun showRow() = compose.setContent {
        val swipe = rememberSwipeToDismiss(rememberTactileSonicEngine(), LocalView.current) { dismissedCount++ }
        Box(Modifier.size(200.dp).swipeOffset(swipe).swipeToDismiss(swipe, "row")) { Text("Recent search") }
    }

    private val row get() = compose.onNode(hasAnyChild(hasText("Recent search")), useUnmergedTree = true)

    private fun dragBy(x: Float) {
        row.performTouchInput { moveBy(Offset(x, 0f)) }
        compose.waitForIdle()
    }

    @Test
    @Config(shadows = [RecordingViewHaptics::class])
    fun aSwipePulledBackUnderTheThresholdGivesTheBackGesturesCancelHaptic() {
        RecordingViewHaptics.played.clear()
        showRow()
        row.performTouchInput { down(centerLeft) }
        dragBy(50f)
        dragBy(50f)
        // Not yet past the threshold.
        assertEquals(emptyList<Int>(), RecordingViewHaptics.played)
        dragBy(100f)
        assertEquals(listOf(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE), RecordingViewHaptics.played)
        dragBy(-120f)
        row.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(
            listOf(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE),
            RecordingViewHaptics.played
        )
        assertEquals(0, dismissedCount)
    }

    @Test
    @Config(shadows = [RecordingViewHaptics::class])
    fun aSwipeAwayGivesOneHaptic() {
        RecordingViewHaptics.played.clear()
        showRow()
        row.performTouchInput { down(centerLeft) }
        dragBy(100f)
        dragBy(150f)
        row.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(1, dismissedCount)
        // The dismissal used to play a second haptic as the row flew off.
        assertEquals(listOf(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE), RecordingViewHaptics.played)
    }

    @Test
    @Config(shadows = [RecordingViewHaptics::class])
    fun theDismissActionGivesTheCommitHaptic() {
        RecordingViewHaptics.played.clear()
        showRow()
        val actions = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnUiThread { actions.single().action() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(1, dismissedCount)
        assertEquals(listOf(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE), RecordingViewHaptics.played)
    }
}
