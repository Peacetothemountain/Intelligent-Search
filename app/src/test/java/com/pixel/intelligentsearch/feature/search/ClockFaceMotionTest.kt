package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Clock Style picker's face animation: it starts and ends at rest, lands back on the face's own look (a whole
 * symmetry step round, the hands an hour on), and pops no larger than [ClockFaceMotion.MAX_POP].
 */
class ClockFaceMotionTest {

    private val symmetry = mapOf(
        GlanceClockStyle.COOKIE to 30f,
        GlanceClockStyle.SUNNY to 45f,
        GlanceClockStyle.SQUARE to 90f,
        GlanceClockStyle.DIGITAL to 90f
    )

    @Test
    fun everyFaceStartsAndEndsAtRest() {
        for (style in GlanceClockStyle.entries) for (t in listOf(-0.5f, 0f, 1f, 1.5f)) {
            assertEquals("$style turn at $t", 0f, ClockFaceMotion.faceTurn(style, t), 0f)
        }
        for (t in listOf(-0.5f, 0f, 1f, 1.5f)) {
            assertEquals(1f, ClockFaceMotion.pop(t), 0f)
            assertEquals(0f, ClockFaceMotion.handSweep(t), 0f)
            assertEquals(0f, ClockFaceMotion.secondsLap(t), 0f)
        }
    }

    @Test
    fun theLastFrameAlreadyLooksLikeTheFaceAtRest() {
        for (style in GlanceClockStyle.entries) {
            // One whole step of the shape's symmetry looks the same as none.
            assertEquals("$style", symmetry.getValue(style), ClockFaceMotion.faceTurn(style, 0.999f), 0.5f)
        }
        assertEquals(1f, ClockFaceMotion.pop(0.999f), 1e-3f)
        // An hour on (the minute hand a whole turn, the hour hand one hour): the same 10:10.
        assertEquals(1f, ClockFaceMotion.handSweep(0.999f), 1e-3f)
        assertEquals(360f, ClockFaceMotion.secondsLap(0.999f), 0.5f)
    }

    @Test
    fun itMovesAndThePopStaysSmall() {
        val steps = (1 until 400).map { it / 400f }
        for (style in GlanceClockStyle.entries) assertTrue("$style turns", steps.any { ClockFaceMotion.faceTurn(style, it) != 0f })
        assertTrue(steps.any { ClockFaceMotion.handSweep(it) > 0.5f })
        for (t in steps) {
            val pop = ClockFaceMotion.pop(t)
            assertTrue("pop $pop at $t", pop >= 1f && pop <= ClockFaceMotion.MAX_POP + 1e-4f)
        }
    }
}
