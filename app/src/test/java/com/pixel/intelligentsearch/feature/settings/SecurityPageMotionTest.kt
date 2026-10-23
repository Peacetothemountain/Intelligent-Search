package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Hardware & Security page's motion: the chain's pulse and sway, and the posture chip's count-up. */
class SecurityPageMotionTest {

    @Test
    fun thePulseTravelsDownTheChainFromTheLeafToTheRoot() {
        val steps = 5
        // Where along the chain the pulse is brightest, through its trip.
        fun peak(phase: Float) = (0 until steps).maxByOrNull { chainPulse(phase, it, steps) }!!
        var last = 0
        for (i in 1..20) {
            val phase = CHAIN_PULSE_TRAVEL * i / 21f
            val at = peak(phase)
            assertTrue("at $phase the pulse went back from $last to $at", at >= last)
            last = at
        }
        assertEquals(0, peak(CHAIN_PULSE_TRAVEL * 0.15f))
        assertEquals(steps - 1, peak(CHAIN_PULSE_TRAVEL * 0.85f))
    }

    @Test
    fun theRestingPulseLeavesTheChainStillBetweenTrips() {
        for (k in 0 until 4) {
            assertEquals(0f, chainPulse(CHAIN_PULSE_TRAVEL + 0.01f, k, 4), 0f)
            assertEquals(0f, chainPulse(0.99f, k, 4), 0f)
        }
        // While verifying it travels the whole trip, never resting.
        assertTrue(chainPulse(0.9f, 3, 4, travel = 1f) > 0.2f)
    }

    @Test
    fun thePulseOnlyEverLightsBetweenNothingAndFully() {
        for (steps in listOf(1, 2, 4, 9)) {
            for (k in 0 until steps) {
                for (i in 0..400) {
                    val v = chainPulse(i / 100f, k, steps)
                    assertTrue("$steps/$k at $i: $v", v in 0f..1f)
                }
            }
        }
        assertEquals(0f, chainPulse(0.3f, 0, 0), 0f)
    }

    @Test
    fun aVerifyingPulseRunsFasterThanTheRestingOne() {
        assertTrue(CHAIN_PULSE_VERIFYING_MILLIS * 2 < CHAIN_PULSE_RESTING_MILLIS)
    }

    @Test
    fun theChainSwaysAsAWaveNotAllAtOnce() {
        for (i in 0..100) {
            val p = i / 100f
            for (k in 0 until 6) assertTrue(chainSway(p, k) in -1f..1f)
        }
        // Neighbouring links are out of step, and the sway repeats each cycle.
        assertTrue(kotlin.math.abs(chainSway(0.2f, 0) - chainSway(0.2f, 1)) > 0.05f)
        assertEquals(chainSway(0.3f, 2), chainSway(1.3f, 2), 1e-4f)
    }

    @Test
    fun theCountUpRunsFromZeroToExactlyTheValue() {
        for (target in listOf(0, 1, 7, 42, 98, 100)) {
            assertEquals(0, countUpValue(0f, target))
            assertEquals(0, countUpValue(-1f, target))
            assertEquals(target, countUpValue(1f, target))
            assertEquals(target, countUpValue(2f, target))
            var last = 0
            for (step in 0..200) {
                val v = countUpValue(step / 200f, target)
                assertTrue("$target at $step: $v", v in last..target)
                last = v
            }
            assertEquals(target, last)
        }
    }

    @Test
    fun theCountUpLastsAsLongAsTheRadarTakesToDrawOut() {
        assertTrue(countUpMillis(5) > countUpMillis(3))
        assertEquals(countUpMillis(1) + 4 * RADAR_ENTRY_STAGGER_MILLIS.toInt(), countUpMillis(5))
    }
}
