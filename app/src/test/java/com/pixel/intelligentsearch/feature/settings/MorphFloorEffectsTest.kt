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
    fun healingFollowsTheSilhouettesHealTimeAndTheRimFadesLast() {
        assertEquals(0f, healProgress(0f), 0f)
        assertEquals(0f, healProgress(MorphBouncePhysics.HEAL_DELAY), 0f)
        assertEquals(1f, healProgress(MorphBouncePhysics.HEAL_DELAY + MorphBouncePhysics.HEAL_TIME), 1e-5f)
        assertEquals(1f, healShade(0f), 0f)
        assertEquals(1f, healRim(0f), 0f)
        // The inside lightens first; the rim is still lit after the inside has gone, and out as the hollow closes.
        assertEquals(0f, healShade(0.45f), 1e-6f)
        assertTrue(healRim(0.45f) > 0.1f)
        assertEquals(0f, healRim(HEAL_CLOSE), 1e-6f)
        for (h in samples()) assertTrue(healRim(h) >= healShade(h) - 1e-6f)
    }

    @Test
    fun theClosingPixelsAppearAroundTheCloseAndAreAllGoneByTheEnd() {
        assertEquals(0f, healPixelPhase(0f), 0f)
        assertEquals(1f, healPixelPhase(1f), 0f)
        // The pixels' moment straddles the hollow closing.
        assertTrue(healPixelPhase(HEAL_CLOSE) in 0.2f..0.8f)
        for (delay in listOf(0f, 0.2f, MAX_PIXEL_DELAY)) {
            assertEquals(0f, pixelAlpha(0f, delay), 0f)
            assertEquals(0f, pixelAlpha(1f, delay), 0f)
            val peak = samples().maxOf { pixelAlpha(it, delay) }
            assertTrue("delay $delay: peak $peak", peak in 0.95f..1f)
        }
        assertEquals(0f, scanlineAlpha(0f), 0f)
        assertEquals(0f, scanlineAlpha(0.5f), 0f)
        assertTrue(samples().all { scanlineAlpha(it) in 0f..1f })
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
    fun aHardLandingSendsOneRippleThatIsGoneSoonAfter() {
        val physics = MorphBouncePhysics(kotlin.random.Random(3), autoSpawn = false).apply {
            resize(4f, 3f, listOf(0.1f, 0.12f, 0.14f, 0.16f))
        }
        val body = physics.spawn(0.5f)!!
        var sawRipple = false
        var t = 0f
        while (t < 8f && physics.ripples.none { it.active } && body.bounces < 3) {
            physics.advance(1f / 120f)
            t += 1f / 120f
        }
        val ripple = physics.ripples.firstOrNull { it.active }
        if (ripple != null) {
            sawRipple = true
            assertEquals(body.id, ripple.bodyId)
            assertTrue(body.bounces >= 2)
        }
        assertTrue("no ripple off the hard floor", sawRipple)
        assertEquals(1, physics.ripples.count { it.active })
        repeat(60) { physics.advance(1f / 120f) }
        assertTrue(physics.ripples.none { it.active })
    }
}
