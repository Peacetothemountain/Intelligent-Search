package com.pixel.intelligentsearch.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pages' places in Android's own back animation (WindowManager Shell's DefaultCrossActivityBackAnimation). */
class NativeBackMotionTest {

    private val w = 1000f
    private val h = 2000f
    private val margin = 20f
    private val offset = 240f

    @Test
    fun theClosingPageShrinksToNinetyPercentTowardTheRightMarginOnALeftEdgeSwipe() {
        val start = NativeBackMotion.closing(0f, w, h, margin, fromRightEdge = false)
        assertEquals(BackFrame.Identity, start)
        val end = NativeBackMotion.closing(1f, w, h, margin, fromRightEdge = false)
        assertEquals(0.9f, end.scale, 1e-4f)
        // Its right edge stops the margin short of the screen's.
        assertEquals(w - margin, end.left + w * end.scale, 1e-3f)
        assertEquals(h * 0.05f, end.top, 1e-3f)
    }

    @Test
    fun aRightEdgeSwipeShrinksTheClosingPageIntoTheMiddle() {
        val end = NativeBackMotion.closing(1f, w, h, margin, fromRightEdge = true)
        assertEquals(w * 0.05f, end.left, 1e-3f)
        assertEquals(h * 0.05f, end.top, 1e-3f)
    }

    @Test
    fun thePageUnderneathWaitsToTheStartAndShrinksInStep() {
        val start = NativeBackMotion.entering(0f, w, h, offset)
        assertEquals(BackFrame(-offset, 0f, 1f), start)
        val end = NativeBackMotion.entering(1f, w, h, offset)
        assertEquals(0.9f, end.scale, 1e-4f)
        // Scaled about its own center, which stays the offset to the start of the screen's.
        assertEquals(w / 2f - offset, end.left + w * end.scale / 2f, 1e-3f)
    }

    @Test
    fun thePagesFollowTheFingerVerticallyButNeverPastTheMargin() {
        assertEquals(0f, NativeBackMotion.verticalShift(300f, 1f, h, margin), 0f)
        val room = (h - h * 0.9f) / 2f - margin
        assertEquals(room, NativeBackMotion.verticalShift(h, 0.9f, h, margin), 1e-3f)
        assertEquals(-room, NativeBackMotion.verticalShift(-h, 0.9f, h, margin), 1e-3f)
        val partial = NativeBackMotion.verticalShift(h / 4f, 0.9f, h, margin)
        assertTrue(partial > room / 2f && partial < room)
    }

    @Test
    fun theReleaseSpringStartsAtTheSwipesSpeedWithinItsLimits() {
        // 2 progress per second from an edge: 2 x 100 x 0.1 x 2 = 40, pushed in (negative).
        assertEquals(-40f, NativeBackMotion.flingVelocity(2f, 0.5f, edgeSwipe = true), 1e-3f)
        assertEquals(-20f, NativeBackMotion.flingVelocity(2f, 0.5f, edgeSwipe = false), 1e-3f)
        // A flick barely started still gets a small push; nothing goes past the maximum.
        assertEquals(-120f, NativeBackMotion.flingVelocity(0f, 0.05f, edgeSwipe = true), 1e-3f)
        assertEquals(-1000f, NativeBackMotion.flingVelocity(100f, 0.5f, edgeSwipe = true), 1e-3f)
    }

    @Test
    fun theClosingPageIsGoneWithinTheFirstFifthOfTheRelease() {
        assertEquals(1f, NativeBackMotion.postCommitClosingAlpha(0f), 0f)
        assertEquals(0.5f, NativeBackMotion.postCommitClosingAlpha(0.1f), 1e-4f)
        assertEquals(0f, NativeBackMotion.postCommitClosingAlpha(0.2f), 1e-4f)
        assertEquals(0f, NativeBackMotion.postCommitClosingAlpha(0.8f), 0f)
    }

    @Test
    fun theGestureAndReleaseCurvesAreAndroids() {
        assertEquals(0f, NativeBackMotion.GestureEasing.transform(0f), 1e-4f)
        assertEquals(1f, NativeBackMotion.GestureEasing.transform(1f), 1e-4f)
        // The emphasized curve passes (0.166666, 0.4) between its two segments.
        assertEquals(0.4f, NativeBackMotion.EmphasizedEasing.transform(0.166666f), 0.01f)
        assertEquals(1f, NativeBackMotion.EmphasizedEasing.transform(1f), 1e-4f)
    }
}
