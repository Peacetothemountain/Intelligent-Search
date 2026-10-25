package com.pixel.intelligentsearch.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pickers' one-shot icon animations (Widget Action Icon, Clock Style) play on every tap of an option, the one
 * already chosen included, not only when the choice changes.
 */
@RunWith(RobolectricTestRunner::class)
class SelectionReplayTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var motion: () -> Float
    private var selected by mutableStateOf(true)
    private var taps by mutableIntStateOf(0)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { motion = rememberSelectionMotion(selected, 600, taps) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1_000)
    }

    /** Lets the effect start the animation and a frame or two of it run. */
    private fun aFewFrames() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun tappingTheChosenOptionAgainPlaysItsAnimationAgain() {
        show()
        assertEquals("at rest when first shown", 1f, motion(), 0f)

        taps++
        aFewFrames()
        val playing = motion()
        assertTrue("plays on a tap while already chosen ($playing)", playing < 1f)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("and settles", 1f, motion(), 0f)

        taps++
        aFewFrames()
        assertTrue("and again on the next tap", motion() < 1f)
    }

    @Test
    fun anOptionThatIsNotChosenStaysStill() {
        selected = false
        show()
        taps++
        aFewFrames()
        assertEquals(1f, motion(), 0f)

        // Chosen by the tap: it plays then, once.
        selected = true
        taps++
        aFewFrames()
        assertTrue(motion() < 1f)
    }
}
