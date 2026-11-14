package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RamPeaksTest {

    private fun reading(apps: Float = 5400f, kernel: Float = 900f, other: Float = 2800f, usedPercent: Int = 58) =
        RamReading(floatArrayOf(apps, kernel, other), availableMb = 15_600f - apps - kernel - other, usedPercent = usedPercent)

    private fun scene() = RamScene().apply { resize(W, H) }

    /** The scene after [seconds] of frames at 60 a second. */
    private fun RamScene.run(seconds: Float, reading: RamReading): RamScene {
        repeat((seconds * 60).toInt()) { advance(1f / 60f, reading) }
        return this
    }

    @Test
    fun theScaleIsTheNextRoundSizeWithRoomAbove() {
        assertEquals(8000, ramCeilingMb(5400f))
        assertEquals(8000, ramCeilingMb(6900f))
        assertEquals(10_000, ramCeilingMb(7100f))
        assertEquals(500, ramCeilingMb(420f))
        assertEquals(500, ramCeilingMb(0f))
        assertEquals("8 GB", ramCeilingLabel(8000))
        assertEquals("1.5 GB", ramCeilingLabel(1500))
        assertEquals("750 MB", ramCeilingLabel(750))
    }

    @Test
    fun eachPeakStandsAtItsTrueShareOfTheScale() {
        val scene = scene().apply { settle(reading()) }
        val height = scene.floor - scene.top
        assertEquals(height * 5400f / 8000f, scene.peakHeight[0], 0.01f)
        assertEquals(height * 900f / 8000f, scene.peakHeight[1], 0.01f)
        assertEquals(height * 2800f / 8000f, scene.peakHeight[2], 0.01f)
    }

    @Test
    fun aBiggerReadingRaisesItsOwnPeakAndLeavesTheOthersAlone() {
        // The graph used to scale to its biggest part, so opening a big app shrank the other peaks instead.
        val before = scene().apply { settle(reading(apps = 5400f)) }
        val after = scene().apply { settle(reading(apps = 6500f)) }
        assertTrue(after.peakHeight[0] > before.peakHeight[0] + 5f)
        assertEquals(before.peakHeight[1], after.peakHeight[1], 0.01f)
        assertEquals(before.peakHeight[2], after.peakHeight[2], 0.01f)
    }

    @Test
    fun theStillPictureHasNothingMoving() {
        val scene = scene().run(3f, reading())
        scene.tap(scene.peakX[0], scene.curve(scene.peakX[0], false))
        scene.settle(reading())
        val first = (0..150).map { scene.curve(it.toFloat(), false) } + scene.peakX.toList() + scene.bar.toList()
        scene.settle(reading())
        assertEquals(first, (0..150).map { scene.curve(it.toFloat(), false) } + scene.peakX.toList() + scene.bar.toList())
        assertTrue(scene.still)
        assertEquals(0, scene.lightsOn)
        assertEquals(0, scene.ringCount)
        assertEquals(-1, scene.picked)
        assertEquals(scene.restX[1], scene.peakX[1], 0f)
    }

    @Test
    fun aPeakSpringsToANewReadingAndTheOutlineFollowsBehind() {
        val scene = scene().run(12f, reading(apps = 5400f))
        assertEquals(5400f, scene.shown[0], 5f)
        assertEquals(5400f, scene.ghost[0], 30f)

        scene.run(0.25f, reading(apps = 6500f))
        assertTrue("on its way after a quarter second, was ${scene.shown[0]}", scene.shown[0] > 5600f)
        assertTrue("the outline is still behind, was ${scene.ghost[0]}", scene.ghost[0] < scene.shown[0] - 200f)
        assertEquals("the changed peak rings", 1, scene.ringCount)

        scene.run(8f, reading(apps = 6500f))
        assertEquals(6500f, scene.shown[0], 5f)
        assertEquals(6500f, scene.ghost[0], 30f)
        assertEquals(0, scene.ringCount)
    }

    @Test
    fun thePeaksRiseOneAfterAnotherOnOpening() {
        val scene = scene().run(0.3f, reading())
        assertTrue("the first is on its way, was ${scene.grow[0]}", scene.grow[0] > 0.2f)
        assertEquals(0f, scene.grow[2], 0f)
        scene.run(3f, reading())
        for (i in 0..2) assertEquals(1f, scene.grow[i], 0.01f)
    }

    @Test
    fun tappingAPeakPicksItForAMoment() {
        val scene = scene().run(3f, reading())
        assertEquals(1, scene.tap(scene.peakX[1], scene.curve(scene.peakX[1], false)))
        assertEquals(1, scene.picked)
        scene.run(0.5f, reading())
        assertEquals(1f, scene.labelAlpha, 0f)
        assertTrue("its piece of the bar stands out, was ${scene.glow[1]}", scene.glow[1] > 0.9f)
        scene.run(RamScene.LABEL_SECONDS, reading())
        assertEquals(-1, scene.picked)
        assertEquals(0f, scene.labelAlpha, 0f)
    }

    @Test
    fun tappingOpenGraphSendsARippleAndPicksNothing() {
        val scene = scene().run(3f, reading())
        val between = (scene.peakX[1] + scene.peakX[2]) / 2f
        val calm = scene.curve(between, false)
        assertEquals(-1, scene.tap(between, scene.top + 4f))
        assertEquals(-1, scene.picked)
        assertEquals(1, scene.rippleCount)
        scene.run(0.15f, reading())
        assertTrue("the line lifts where it was tapped", scene.curve(between, false) < calm - 1f)
        scene.run(RamScene.RIPPLE_SECONDS, reading())
        assertEquals(0, scene.rippleCount)
    }

    @Test
    fun theBarAlwaysAddsUpToAllOfMemory() {
        val scene = scene()
        repeat(240) {
            scene.advance(1f / 60f, reading())
            assertEquals(1f, scene.bar.take(4).sum(), 0.0001f)
        }
        assertEquals(5400f / 15_600f, scene.bar[0], 0.002f)
        assertEquals(900f / 15_600f, scene.bar[1], 0.002f)
        assertEquals(2800f / 15_600f, scene.bar[2], 0.002f)
    }

    @Test
    fun fullerMemoryRunsMoreLights() {
        assertEquals(2, scene().run(5f, reading(usedPercent = 40)).lightsOn)
        assertEquals(7, scene().run(5f, reading(usedPercent = 93)).lightsOn)
    }

    @Test
    fun theLineStaysInsideTheGraphAtItsLiveliest() {
        val scene = scene()
        val full = reading(apps = 9200f, other = 4300f, usedPercent = 95)
        repeat(30 * 60) { frame ->
            scene.advance(1f / 60f, full)
            if (frame % 45 == 0) scene.tap(scene.peakX[frame / 45 % 3], scene.curve(scene.peakX[frame / 45 % 3], false))
            if (frame % 70 == 0) scene.tap(60f + frame % 50, scene.floor - 3f)
            var x = scene.left
            while (x <= scene.right) {
                val y = scene.curve(x, false)
                assertTrue("at $x on frame $frame: $y", y >= scene.top - 4f && y <= scene.floor)
                x += 3f
            }
        }
    }

    @Test
    fun aGraphWithNoBreakdownDrawsNoPeaks() {
        val scene = scene().run(1f, RamReading(FloatArray(0), availableMb = 4000f, usedPercent = 50))
        assertEquals(0, scene.count)
        assertEquals(0, scene.lightsOn)
        assertEquals(1f, scene.bar[0], 0f)
        assertEquals(-1, scene.tap(60f, 40f))
    }

    private companion object {
        const val W = 150f
        const val H = 104f
    }
}
