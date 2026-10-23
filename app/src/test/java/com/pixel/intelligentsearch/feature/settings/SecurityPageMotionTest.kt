package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Hardware & Security page's motion: the secure chip's lights, the chain's pulse and sway, and the count-up. */
class SecurityPageMotionTest {

    @Test
    fun theChipsLightsComeInPinByPinRoundTheChip() {
        val pins = 12
        // Each pin's light starts after the one before it, and every one reaches the chip within the round.
        fun firstLit(pin: Int) = (0..2000).map { it / 2000f }.first { chipLightPosition(it, pin, pins) != null }
        for (pin in 1 until pins) assertTrue("pin $pin goes after pin ${pin - 1}", firstLit(pin) > firstLit(pin - 1))
        for (pin in 0 until pins) {
            val reaches = (0..2000).map { it / 2000f }.mapNotNull { chipLightPosition(it, pin, pins) }
            assertTrue("pin $pin's light reaches the chip", reaches.max() > 0.98f && reaches.min() < 0.02f)
        }
    }

    @Test
    fun theRestingChipIsStillBetweenRounds() {
        for (pin in 0 until 12) {
            assertEquals(null, chipLightPosition(CHIP_LIGHT_TRAVEL + 0.2f, pin, 12))
            assertEquals(0f, chipArrival(0.97f, pin, 12), 0f)
        }
        // While verifying it never rests: some pin is always lit.
        for (i in 0 until 100) {
            val p = i / 100f
            assertTrue("at $p", (0 until 12).any { chipLightPosition(p, it, 12, travel = 1f) != null })
        }
    }

    @Test
    fun theChipGlowsAsEachLightArrivesThenFades() {
        val pins = 12
        val arrival = (0..4000).map { it / 4000f }.first { chipArrival(it, 0, pins) > 0f }
        assertEquals("brightest as it arrives", 1f, chipArrival(arrival, 0, pins), 0.02f)
        assertTrue("then fading", chipArrival(arrival + 0.02f, 0, pins) < chipArrival(arrival, 0, pins))
        for (i in 0..400) for (pin in 0 until pins) assertTrue(chipArrival(i / 400f, pin, pins) in 0f..1f)
        assertTrue(CHIP_ROUND_VERIFYING_MILLIS * 2 < CHIP_ROUND_RESTING_MILLIS)
    }

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
