package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * What keeps a switch's attached rows quick to rise away: the reveal's timing, the card's shadow drawn without a
 * per-size bitmap yet looking exactly as before, and the switch animating without recomposing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollapseResponsivenessTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun theRowsRiseAwayQuicklyAndFadeBeforeTheCardCloses() {
        val reveal = ExpressiveMotionTokens.Reveal
        assertTrue("collapse ${reveal.COLLAPSE_MILLIS} ms", reveal.COLLAPSE_MILLIS <= 220)
        assertTrue(reveal.FADE_OUT_MILLIS < reveal.COLLAPSE_MILLIS)
        assertTrue(reveal.FADE_IN_DELAY_MILLIS + reveal.FADE_IN_MILLIS <= 250)
        // Critically damped and stiff enough to open about as fast as it closes, with no overshoot to settle.
        assertTrue(reveal.EXPAND_STIFFNESS >= 600f)
    }

    /**
     * The shell's shadow is the same blurred shape Compose's dropShadow draws, so swapping it out (to stop the card
     * re-blurring a bitmap at every height) changed nothing anyone can see.
     */
    @Test
    fun theShellShadowLooksExactlyLikeComposesDropShadow() {
        val shape = RoundedCornerShape(28.dp)
        val color = Color.Black.copy(alpha = 0.30f)
        compose.setContent {
            androidx.compose.foundation.layout.Column(Modifier.background(Color.White)) {
                Box(Modifier.testTag("compose").padding(24.dp)) {
                    Box(Modifier.size(160.dp, 90.dp).dropShadow(shape, Shadow(radius = 10.dp, color = color, offset = DpOffset(0.dp, 4.dp))))
                }
                Box(Modifier.testTag("shell").padding(24.dp)) {
                    Box(Modifier.size(160.dp, 90.dp).shellShadow(shape, radius = 10.dp, color = color, offset = DpOffset(0.dp, 4.dp)))
                }
            }
        }
        val reference = compose.onNodeWithTag("compose").captureToImage().asAndroidBitmap()
        val drawn = compose.onNodeWithTag("shell").captureToImage().asAndroidBitmap()
        assertEquals(reference.width, drawn.width)
        assertEquals(reference.height, drawn.height)
        var worst = 0
        var shaded = 0
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val a = reference.getPixel(x, y)
                val b = drawn.getPixel(x, y)
                if (a != -1) shaded++
                for (shift in intArrayOf(0, 8, 16)) worst = maxOf(worst, abs((a shr shift and 0xFF) - (b shr shift and 0xFF)))
            }
        }
        assertTrue("the reference drew a shadow", shaded > 1000)
        assertTrue("worst channel difference $worst", worst <= 3)
    }

    /**
     * A toggle animates the track, border and thumb in the draw and layout phases: after the toggle itself, the
     * animation's frames apply no composition changes (reading the animated values in composition applied a change
     * on every frame).
     */
    @Test
    fun theSwitchAnimatesWithoutRecomposing() {
        var checked by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GoogleMessagesSwitch(checked = checked, onCheckedChange = { checked = it })
        }
        compose.mainClock.advanceTimeBy(500)
        fun changes() = androidx.compose.runtime.Recomposer.runningRecomposers.value.sumOf { it.changeCount }
        checked = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        // The toggle's own recomposition, then the rest of the animation.
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val afterToggle = changes()
        repeat(40) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("${changes() - afterToggle} composition changes while animating", changes() - afterToggle <= 1)
    }
}
