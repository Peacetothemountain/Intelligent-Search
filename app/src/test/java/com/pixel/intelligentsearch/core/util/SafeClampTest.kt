package com.pixel.intelligentsearch.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Settings pages crashed in production when drawing code clamped a position between bounds taken from a
 * tiny size (the diagnostics chart while it animated open, a slider narrower than its thumb).
 */
class SafeClampTest {

    @Test
    fun coerceInThrowsWhenTheBoundsCross() {
        // The production failure: a 10 px tall chart clamped between 4 px and (height - 8 px).
        val height = 10f
        assertThrows(IllegalArgumentException::class.java) { 5f.coerceIn(4f, height - 8f) }
    }

    @Test
    fun crossedBoundsCollapseToTheMinimum() {
        val height = 10f
        assertEquals(4f, 5f.clampSafely(4f, height - 8f))
        assertEquals(4f, (-3f).clampSafely(4f, height - 8f))
    }

    @Test
    fun ordinaryBoundsClampLikeCoerceIn() {
        assertEquals(4f, 1f.clampSafely(4f, 92f))
        assertEquals(50f, 50f.clampSafely(4f, 92f))
        assertEquals(92f, 120f.clampSafely(4f, 92f))
    }
}
