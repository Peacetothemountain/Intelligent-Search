package com.pixel.intelligentsearch.feature.settings

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySeaLifeTest {

    private fun scene() = SeaScene(Random(7))

    /** The scene after [seconds] of frames at 60 a second. */
    private fun SeaScene.run(seconds: Float, level: Int, charging: Boolean = false, watts: Float = 0f): SeaScene {
        repeat((seconds * 60).toInt()) { advance(1f / 60f, W, H, level, charging, watts) }
        return this
    }

    private fun SeaScene.company() = listOf(star, fish, puffer, octopus).map { it.present }

    @Test
    fun theFullerTheBatteryTheMoreCompany() {
        fun at(level: Int) = scene().apply { settle(W, H, level) }.company()
        assertEquals(listOf(false, false, false, false), at(5))
        assertEquals(listOf(true, false, false, false), at(15))
        assertEquals(listOf(true, true, false, false), at(40))
        assertEquals(listOf(true, true, true, false), at(62))
        assertEquals(listOf(true, true, true, true), at(72))
        assertEquals(listOf(true, true, true, true), at(100))
    }

    @Test
    fun theWaterTurnsToTheWarningColorsAtTwentyAndTen() {
        fun band(level: Int) = scene().apply { settle(W, H, level) }.waterBand
        assertEquals(0, band(21))
        assertEquals(1, band(20))
        assertEquals(1, band(11))
        assertEquals(2, band(10))
    }

    @Test
    fun theStillPictureIsTheTrueLevelWithNothingMoving() {
        val scene = scene().run(3f, level = 72, charging = true, watts = 30f)
        scene.settle(W, H, 55)
        val first = listOf(scene.shown, scene.fish.x, scene.fish.y, scene.star.x, scene.star.y, scene.amp)
        scene.settle(W, H, 55)
        assertEquals(first, listOf(scene.shown, scene.fish.x, scene.fish.y, scene.star.x, scene.star.y, scene.amp))
        assertEquals(55f, scene.shown, 0f)
        assertEquals(0f, scene.amp, 0f)
        assertTrue(scene.bubbles.isEmpty())
    }

    @Test
    fun theWaterGlidesToANewReading() {
        val scene = scene().apply { settle(W, H, 20) }
        scene.run(0.1f, level = 80)
        assertTrue("part of the way after a tenth of a second, was ${scene.shown}", scene.shown > 21f && scene.shown < 79f)
        scene.run(3f, level = 80)
        assertEquals(80f, scene.shown, 0.5f)
    }

    @Test
    fun bubblesRiseOnlyWhileCharging() {
        // At 50% there is no puffer fish to let out bubbles of its own.
        assertTrue(scene().run(6f, level = 50).bubbles.isEmpty())
        assertFalse(scene().run(6f, level = 50, charging = true, watts = 18f).bubbles.isEmpty())
    }

    @Test
    fun everyoneStaysUnderTheWaterAtFullPower() {
        val scene = scene()
        repeat(40 * 60) {
            scene.advance(1f / 60f, W, H, 72, true, 45f)
            for (dweller in listOf(scene.star, scene.fish, scene.puffer, scene.octopus)) {
                if (dweller.present) assertTrue(dweller.y > scene.surfaceAt(dweller.x, W))
            }
        }
    }

    @Test
    fun tappingACreaturePlaysItsTrick() {
        // A second in, the puffer fish is calm: its own puff is still a second away.
        val scene = scene().run(1f, level = 72)
        assertEquals(0f, scene.pufferPuff, 0f)
        scene.tap(scene.puffer.x, scene.puffer.y)
        scene.run(1f, level = 72)
        assertTrue("puffed up a second after the tap, was ${scene.pufferPuff}", scene.pufferPuff > 0.9f)
        scene.tap(scene.star.x, scene.star.y)
        assertEquals(0f, scene.starHop, 0f)
        scene.run(2f, level = 72)
        scene.tap(scene.octopus.x, scene.octopus.y)
        assertEquals(0f, scene.octopusClock, 0f)
    }

    @Test
    fun pluggingInPlaysTheTricksButOpeningThePageWhileChargingDoesNot() {
        val opened = scene().run(1f, level = 72, charging = true, watts = 18f)
        assertEquals(-1f, opened.starHop, 0f)
        val plugged = scene().run(1f, level = 72).run(0.6f, level = 72, charging = true, watts = 18f)
        assertTrue("the starfish is in the air, was ${plugged.starHop}", plugged.starHop > 0f)
    }

    @Test
    fun thePufferFishAndTheOctopusMoveWithoutAJump() {
        var t = 0f
        while (t < SeaScene.PUFF_CYCLE) {
            assertTrue("puff at $t", kotlin.math.abs(puffAt(t + 0.004f) - puffAt(t)) < 0.06f)
            t += 0.004f
        }
        t = 0f
        while (t < SeaScene.OCTOPUS_CYCLE) {
            val next = (t + 0.004f) % SeaScene.OCTOPUS_CYCLE
            assertTrue("rise at $t", kotlin.math.abs(octopusRise(next) - octopusRise(t)) < 0.02f)
            assertTrue("spread at $t", kotlin.math.abs(octopusSpread(next) - octopusSpread(t)) < 0.06f)
            t += 0.004f
        }
    }

    private companion object {
        const val W = 150f
        const val H = 104f
    }
}
