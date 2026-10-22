package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A swiped-away row dissolves with the scroll edge's grainy blur and fades as it goes: nothing at rest, growing with
 * the drag, and capped so it never turns to mush.
 */
class SwipeDissolveTest {

    @Test
    fun atRestThereIsNoDissolveAndNoFade() {
        assertEquals(0f, swipeDissolve(0f), 0f)
        assertEquals(1f, swipeAlpha(0f, maxFade = 0.6f), 0f)
    }

    @Test
    fun theDissolveGrowsWithTheDragAndEasesIn() {
        val steps = (0..20).map { swipeDissolve(it / 20f) }
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a })
        // A small nudge barely shows: a tenth of the way, under a tenth of the strength.
        assertTrue(swipeDissolve(0.05f) < 0.05f)
        // Halfway to full it is halfway in.
        assertEquals(0.5f, swipeDissolve(SWIPE_DISSOLVE_FULL_AT / 2), 1e-4f)
    }

    @Test
    fun theDissolveIsCappedOnceFullAndOnTheFlyOff() {
        assertEquals(1f, swipeDissolve(SWIPE_DISSOLVE_FULL_AT), 0f)
        assertEquals(1f, swipeDissolve(1f), 0f)
        assertEquals(1f, swipeDissolve(3f), 0f)
        assertEquals(0f, swipeDissolve(-1f), 0f)
        // The strongest blur stays gentler than the scroll edge's 10 dp.
        assertTrue(SWIPE_DISSOLVE_MAX_BLUR.value in 1f..8f)
    }

    @Test
    fun theRowFadesWithTheDragDownToItsFloor() {
        assertEquals(0.8f, swipeAlpha(0.2f, maxFade = 0.6f), 1e-6f)
        assertEquals(0.4f, swipeAlpha(0.6f, maxFade = 0.6f), 1e-6f)
        assertEquals(0.4f, swipeAlpha(2f, maxFade = 0.6f), 1e-6f)
        assertEquals(0.3f, swipeAlpha(1f, maxFade = 0.7f), 1e-6f)
    }

    @Test
    fun atTheDismissThresholdTheRowIsVisiblyButModeratelyDissolved() {
        // The default row dismisses past 130 px of a 600 px fade distance.
        val atThreshold = swipeDissolve(130f / 600f)
        assertTrue("$atThreshold", atThreshold in 0.25f..0.6f)
    }
}
