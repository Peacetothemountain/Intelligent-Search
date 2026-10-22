package com.pixel.intelligentsearch.feature.launch

import com.pixel.intelligentsearch.feature.launch.EdgeSwipeGesture.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The edge swipe's threshold: in past it to arm (haptic), back to disarm, lift while armed to open search. */
class EdgeSwipeGestureTest {

    private val threshold = 144f
    private val slop = 24f

    @Test
    fun aSwipeInPastTheThresholdArmsOnceAndOpensOnLift() {
        val gesture = EdgeSwipeGesture(ScreenEdge.RIGHT, threshold, slop)
        gesture.down(1075f, 1000f)
        assertEquals(Event.NONE, gesture.move(1000f, 1004f))
        assertEquals(Event.ARMED, gesture.move(1075f - threshold, 1006f))
        // Further in doesn't arm again: the haptic plays once.
        assertEquals(Event.NONE, gesture.move(800f, 1010f))
        assertEquals(Event.OPEN, gesture.up(790f, 1010f))
    }

    @Test
    fun fromTheLeftEdgeInwardIsRightward() {
        val gesture = EdgeSwipeGesture(ScreenEdge.LEFT, threshold, slop)
        gesture.down(4f, 600f)
        assertEquals(Event.NONE, gesture.move(-20f, 600f))
        assertEquals(Event.ARMED, gesture.move(4f + threshold + 1f, 610f))
        assertEquals(Event.OPEN, gesture.up(200f, 610f))
    }

    @Test
    fun shortOfTheThresholdNothingOpens() {
        val gesture = EdgeSwipeGesture(ScreenEdge.RIGHT, threshold, slop)
        gesture.down(1075f, 1000f)
        assertEquals(Event.NONE, gesture.move(1075f - threshold + 10f, 1000f))
        assertEquals(Event.NONE, gesture.up(1075f - threshold + 10f, 1000f))
    }

    @Test
    fun pullingBackDisarmsAndLiftingThenOpensNothing() {
        val gesture = EdgeSwipeGesture(ScreenEdge.RIGHT, threshold, slop)
        gesture.down(1075f, 1000f)
        assertEquals(Event.ARMED, gesture.move(900f, 1000f))
        // Just under the threshold isn't enough to disarm (no flicker at the line)...
        assertEquals(Event.NONE, gesture.move(1075f - threshold + 5f, 1000f))
        assertTrue(gesture.isArmed)
        // ...back under three quarters of it is.
        assertEquals(Event.DISARMED, gesture.move(1075f - threshold * 0.5f, 1000f))
        assertFalse(gesture.isArmed)
        assertEquals(Event.NONE, gesture.up(1070f, 1000f))
    }

    @Test
    fun aMostlyVerticalDragAlongTheEdgeIsLetGo() {
        val gesture = EdgeSwipeGesture(ScreenEdge.RIGHT, threshold, slop)
        gesture.down(1075f, 1000f)
        assertEquals(Event.NONE, gesture.move(1060f, 1100f))
        // Coming in afterwards can't arm it: this was a scroll.
        assertEquals(Event.NONE, gesture.move(800f, 1110f))
        assertEquals(Event.NONE, gesture.up(800f, 1110f))
    }

    @Test
    fun aCancelledArmedSwipeDisarmsWithoutOpening() {
        val gesture = EdgeSwipeGesture(ScreenEdge.RIGHT, threshold, slop)
        gesture.down(1075f, 1000f)
        assertEquals(Event.ARMED, gesture.move(900f, 1000f))
        assertEquals(Event.DISARMED, gesture.cancel())
        assertEquals(Event.NONE, gesture.cancel())
    }

    @Test
    fun eachSwipeStartsAfresh() {
        val gesture = EdgeSwipeGesture(ScreenEdge.RIGHT, threshold, slop)
        gesture.down(1075f, 1000f)
        gesture.move(1060f, 1200f) // let go as a scroll
        gesture.up(1060f, 1200f)
        gesture.down(1075f, 500f)
        assertEquals(Event.ARMED, gesture.move(900f, 505f))
        assertEquals(Event.OPEN, gesture.up(900f, 505f))
    }
}
