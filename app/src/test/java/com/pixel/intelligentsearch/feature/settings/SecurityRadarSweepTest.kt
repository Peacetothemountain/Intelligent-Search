package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** How brightly the security radar's beam lights each vertex as it sweeps past. */
class SecurityRadarSweepTest {

    private val turn = (2 * PI).toFloat()

    @Test
    fun aVertexUnderTheBeamIsFullyLit() {
        for (axis in listOf(-PI.toFloat() / 2, 0f, 1.3f, 4f)) {
            assertEquals(1f, sweepHighlight(axis, axis), 1e-5f)
            assertEquals(1f, sweepHighlight(axis + turn, axis), 1e-4f)
        }
    }

    @Test
    fun itFadesSteadilyBehindTheBeamAndIsDarkPastTheTrail() {
        val axis = 0.5f
        var last = 1f
        for (step in 1..100) {
            val lit = sweepHighlight(axis + BEAM_TRAIL * step / 100f, axis)
            assertTrue("behind $step: $lit", lit <= last && lit >= 0f)
            last = lit
        }
        assertEquals(0f, sweepHighlight(axis + BEAM_TRAIL, axis), 1e-5f)
        assertEquals(0f, sweepHighlight(axis + BEAM_TRAIL + 0.3f, axis), 0f)
        assertEquals(0f, sweepHighlight(axis + PI.toFloat(), axis), 0f)
    }

    @Test
    fun itBrightensJustBeforeTheBeamArrivesButNotLongBefore() {
        val axis = 2f
        assertTrue(sweepHighlight(axis - 0.03f, axis) > 0.5f)
        assertEquals(0f, sweepHighlight(axis - 0.5f, axis), 0f)
    }

    @Test
    fun itWrapsAroundTheTurn() {
        // The beam just past 3 o'clock still lights a vertex just above it, across the 0/2π seam.
        val nearSeam = turn - 0.1f
        assertEquals(sweepHighlight(0.6f, 0.4f), sweepHighlight(0.1f, nearSeam), 1e-4f)
        assertTrue(sweepHighlight(0.1f, nearSeam) > 0.7f)
    }

    @Test
    fun itAlwaysStaysBetweenDarkAndFullyLit() {
        for (step in -400..400) {
            val lit = sweepHighlight(step * 0.05f, 1f)
            assertTrue("$step: $lit", lit in 0f..1f)
        }
    }
}
