package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The curves behind the Material Morph animation's floor: ripples, the membrane's dip, and the silhouette healing. */
class MorphFloorEffectsTest {

    private fun samples(n: Int = 400): List<Float> = (0..n).map { it.toFloat() / n }

    @Test
    fun theHollowClosesOnASpringWithATinyOvershootAndEndsExactlyAtRest() {
        assertEquals(1f, healShrink(0f), 0f)
        assertEquals(0f, healShrink(1f), 0f)
        // Before healing and after it, nothing moves.
        assertEquals(1f, healShrink(-0.5f), 0f)
        assertEquals(0f, healShrink(1.5f), 0f)
        // Slow to start, as a spring let go from rest.
        assertTrue(healShrink(0.02f) > 0.99f)
        // It closes at HEAL_CLOSE, shrinking all the way there.
        assertEquals(0f, healShrink(HEAL_CLOSE), 1e-5f)
        val closing = samples().filter { it <= HEAL_CLOSE }
        closing.zipWithNext().forEach { (a, b) -> assertTrue("grew from $a to $b", healShrink(b) <= healShrink(a) + 1e-6f) }
        // Then overfills a touch, under a tenth of the silhouette, and settles back to flat.
        val overshoot = samples().minOf { healShrink(it) }
        assertTrue("overshoot $overshoot", overshoot < -0.02f && overshoot > -0.1f)
        assertTrue(healShrink(0.99f) > -0.001f)
    }

    @Test
    fun healingFollowsTheHollowsSizeAndTheRimFadesLast() {
        // A hollow closing as the rubber heals is drawn as far through its healing as its size says.
        assertEquals(0f, healForShrink(1f), 0f)
        assertEquals(HEAL_CLOSE, healForShrink(0f), 0f)
        for (h in samples(100).filter { it < HEAL_CLOSE * 0.98f }) assertEquals("at $h", h, healForShrink(healShrink(h)), 2e-3f)
        var last = 0f
        for (k in samples(100).reversed()) {
            val h = healForShrink(k)
            assertTrue("heal falls back at $k", h >= last - 1e-6f)
            last = h
        }
        assertEquals(1f, healShade(0f), 0f)
        assertEquals(1f, healRim(0f), 0f)
        // The inside lightens first; the rim is still lit after the inside has gone, and out as the hollow closes.
        assertEquals(0f, healShade(0.45f), 1e-6f)
        assertTrue(healRim(0.45f) > 0.1f)
        assertEquals(0f, healRim(HEAL_CLOSE), 1e-6f)
        for (h in samples()) assertTrue(healRim(h) >= healShade(h) - 1e-6f)
    }

    @Test
    fun eachPixelComesAndIsGoneWithinTheSparkle() {
        for (delay in listOf(0f, 0.2f, MAX_PIXEL_DELAY)) {
            assertEquals(0f, pixelAlpha(0f, delay), 0f)
            assertEquals(0f, pixelAlpha(1f, delay), 0f)
            val peak = samples().maxOf { pixelAlpha(it, delay) }
            assertTrue("delay $delay: peak $peak", peak in 0.95f..1f)
        }
    }

    @Test
    fun ripplesSpreadFromTheSilhouetteOutwardsAndFadeAsTheyGo() {
        for ((spread, ease) in listOf(SOFT_RIPPLE_SPREAD to SOFT_RIPPLE_EASE, HARD_RIPPLE_SPREAD to HARD_RIPPLE_EASE)) {
            assertEquals(1f, rippleScale(0f, spread, ease), 0f)
            assertEquals(spread, rippleScale(1f, spread, ease), 1e-5f)
            samples().zipWithNext().forEach { (a, b) -> assertTrue(rippleScale(b, spread, ease) >= rippleScale(a, spread, ease)) }
        }
        // The hard ripple's start is the sharper.
        assertTrue(rippleScale(0.1f, 2f, HARD_RIPPLE_EASE) > rippleScale(0.1f, 2f, SOFT_RIPPLE_EASE))
        assertEquals(0f, rippleAlpha(0f), 0f)
        assertEquals(0f, rippleAlpha(1f), 0f)
        assertEquals(0f, rippleAlpha(-0.2f), 0f)
        val peakAt = samples().maxBy { rippleAlpha(it) }
        assertTrue("peaks at $peakAt", peakAt < 0.15f)
        samples().filter { it > peakAt }.zipWithNext().forEach { (a, b) -> assertTrue(rippleAlpha(b) <= rippleAlpha(a)) }
        assertTrue(samples().all { rippleAlpha(it) in 0f..1f })
        // The soft landing's ripples start one after another and have all faded about 0.9 s after it.
        assertTrue(rippleProgress(0.05f, SOFT_RIPPLE_GAP, SOFT_RIPPLE_TIME) < 0f)
        val lastDone = (SOFT_RIPPLES - 1) * SOFT_RIPPLE_GAP + SOFT_RIPPLE_TIME
        assertTrue(lastDone < 1f)
        for (r in 0 until SOFT_RIPPLES) assertEquals(0f, rippleAlpha(rippleProgress(lastDone, r * SOFT_RIPPLE_GAP, SOFT_RIPPLE_TIME)), 1e-6f)
        assertEquals(0f, rippleAlpha(rippleProgress(MorphBouncePhysics.HARD_RIPPLE_TIME, 0f, MorphBouncePhysics.HARD_RIPPLE_TIME)), 0f)
    }

    @Test
    fun theMembraneDipsSmoothlyUnderTheShapeAndMeetsTheFlatFloor() {
        assertEquals(1f, membraneBump(0f), 0f)
        assertEquals(0f, membraneBump(1f), 0f)
        assertEquals(0f, membraneBump(-1f), 0f)
        assertEquals(0f, membraneBump(1.4f), 0f)
        // Symmetric, falling away from the center, and meeting the floor flat (no kink at the edge).
        for (u in samples()) {
            assertEquals(membraneBump(u), membraneBump(-u), 1e-6f)
        }
        samples().zipWithNext().forEach { (a, b) -> assertTrue(membraneBump(b) <= membraneBump(a)) }
        assertTrue((membraneBump(0.99f) - membraneBump(1f)) / 0.01f < 0.05f)
        assertEquals(0f, membraneStretch(0f, 0.1f), 0f)
        assertEquals(1f, membraneStretch(0.05f, 0.1f), 0f)
        assertEquals(0f, membraneStretch(-0.01f, 0.1f), 0f)
    }

    @Test
    fun aFirstLandingSendsSoftRipplesAndALaterOneASharpOne() {
        val scene = morphSceneFor(411f * 3f, 300f * 3f, 3f)!!
        val physics = MorphBouncePhysics(kotlin.random.Random(3), autoSpawn = false).apply {
            resize(scene.widthMetres, scene.heightMetres, scene.radiiMetres)
        }
        val body = physics.spawn()!!
        val kinds = ArrayList<Boolean>()
        var t = 0f
        while (t < 8f && body in physics.bodies) {
            physics.advance(1f / 120f)
            t += 1f / 120f
            val pressed = physics.imprints.firstOrNull { it.active && it.holding && it.bodyId == body.id && it.sincePressed <= 1f / 120f + 1e-4f }
            if (pressed != null) kinds += pressed.soft
        }
        assertTrue("landings $kinds", kinds.size >= 2)
        // The first landing's ripples are the soft ones; every later landing sends one sharp ripple.
        assertTrue(kinds.first())
        assertTrue(kinds.drop(1).none { it })
        assertEquals(0f, rippleAlpha(rippleProgress(MorphBouncePhysics.HARD_RIPPLE_TIME, 0f, MorphBouncePhysics.HARD_RIPPLE_TIME)), 0f)
    }
}
