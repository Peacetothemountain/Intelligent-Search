package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.Morph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * How the Material Morph animation looks: Material 3 Expressive shapes, Material You colour roles, a morph that
 * completes on every bounce, squash, stretch and scale-in that stay within their bounds, and no fading.
 */
class MaterialMorphLookTest {

    /** Every shape in androidx.compose.material3.MaterialShapes, by name. */
    private val materialShapeNames = setOf(
        "Circle", "Square", "Slanted", "Arch", "Fan", "Arrow", "SemiCircle", "Oval", "Pill", "Triangle", "Diamond",
        "ClamShell", "Pentagon", "Gem", "Sunny", "VerySunny", "Cookie4Sided", "Cookie6Sided", "Cookie7Sided",
        "Cookie9Sided", "Cookie12Sided", "Ghostish", "Clover4Leaf", "Clover8Leaf", "Burst", "SoftBurst", "Boom",
        "SoftBoom", "Flower", "Puffy", "PuffyDiamond", "PixelCircle", "PixelTriangle", "Bun", "Heart"
    )

    private fun world(seed: Int) = MorphBouncePhysics(Random(seed)).apply {
        resize(4.11f, 1.6f, listOf(0.085f, 0.10f, 0.115f, 0.13f))
    }

    private fun run(physics: MorphBouncePhysics, seconds: Float, onFrame: () -> Unit) {
        var t = 0f
        while (t < seconds) {
            physics.advance(1f / 120f)
            onFrame()
            t += 1f / 120f
        }
    }

    @Test
    fun theShapesAreMaterialShapesNormalizedToTheUnitSquare() {
        val shapes = MaterialMorphShapes.byName
        assertTrue("only ${shapes.size} shapes", shapes.size >= 20)
        assertTrue("not Material shapes: ${shapes.keys - materialShapeNames}", materialShapeNames.containsAll(shapes.keys))
        // The ones Google's loading indicator morphs through are all there.
        for (name in listOf("SoftBurst", "Cookie9Sided", "Pentagon", "Pill", "Sunny", "Cookie4Sided", "Oval")) {
            assertTrue("missing $name", name in shapes)
        }
        for ((name, polygon) in shapes) {
            // Normalized: the outline fits the unit square, its longer side spanning it (to within the slack of the
            // control points, which normalizing fits rather than the curve itself).
            val b = outlineBounds(polygon.cubics)
            assertTrue("$name bounds $b", b[0] >= -0.01f && b[1] >= -0.01f && b[2] <= 1.01f && b[3] <= 1.01f)
            assertTrue("$name bounds $b", maxOf(b[2] - b[0], b[3] - b[1]) in 0.8f..1.01f)
            val fit = MorphShapeFit.of(polygon)
            assertTrue("$name fit ${fit.scale}", fit.scale.isFinite() && fit.scale in 1.4f..3f)
            // Fitted, it fills its body's circle without poking out of it, whichever way it turns.
            var farthest = 0f
            for (c in polygon.cubics) {
                for (i in 0..16) {
                    val t = i / 16f
                    val u = 1f - t
                    val x = u * u * u * c.anchor0X + 3f * u * u * t * c.control0X + 3f * u * t * t * c.control1X + t * t * t * c.anchor1X
                    val y = u * u * u * c.anchor0Y + 3f * u * u * t * c.control0Y + 3f * u * t * t * c.control1Y + t * t * t * c.anchor1Y
                    farthest = maxOf(farthest, kotlin.math.hypot(x - fit.centerX, y - fit.centerY) * fit.scale)
                }
            }
            assertTrue("$name reaches $farthest of its radius", farthest in 0.99f..1.005f)
        }
    }

    @Test
    fun theShapeSequenceIsSeededAndNeverRepeatsAShapeInARow() {
        val a = MaterialMorphShapes.sequence(3)
        assertEquals(a, MaterialMorphShapes.sequence(3))
        assertEquals(MaterialMorphShapes.byName.keys, a.toSet())
        assertEquals(a.size, a.toSet().size)
        assertTrue("seeds 3 and 4 give the same order", a != MaterialMorphShapes.sequence(4))
        // Shape n morphs into shape n + 1, the last back into the first.
        for (i in a.indices) assertTrue(a[i] != a[(i + 1) % a.size])
    }

    /** Left, top, right, bottom of the outline the cubics draw, sampled along each curve. */
    private fun outlineBounds(cubics: List<Cubic>): List<Float> {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        for (c in cubics) {
            for (i in 0..16) {
                val t = i / 16f
                val u = 1f - t
                val a = u * u * u
                val b = 3f * u * u * t
                val d = 3f * u * t * t
                val e = t * t * t
                xs += a * c.anchor0X + b * c.control0X + d * c.control1X + e * c.anchor1X
                ys += a * c.anchor0Y + b * c.control0Y + d * c.control1Y + e * c.anchor1Y
            }
        }
        return listOf(xs.min(), ys.min(), xs.max(), ys.max())
    }

    @Test
    fun everyMorphBetweenNeighboursStartsOnOneShapeAndEndsOnTheNext() {
        val names = MaterialMorphShapes.sequence(1)
        for (i in names.indices) {
            val where = "${names[i]} -> ${names[(i + 1) % names.size]}"
            val from = MaterialMorphShapes.byName.getValue(names[i])
            val to = MaterialMorphShapes.byName.getValue(names[(i + 1) % names.size])
            val morph = Morph(from, to)
            for (k in 0..3) {
                assertEquals("$where start $k", outlineBounds(from.cubics)[k], outlineBounds(morph.asCubics(0f))[k], 0.02f)
                assertEquals("$where end $k", outlineBounds(to.cubics)[k], outlineBounds(morph.asCubics(1f))[k], 0.02f)
            }
        }
    }

    @Test
    fun theColoursAreTheThemesRolesInLightAndDark() {
        assertEquals(listOf("primaryFixedDim", "tertiaryContainer", "secondaryFixedDim", "primaryContainer", "tertiaryFixedDim"), MORPH_COLOR_ROLES)
        for (scheme in listOf(lightColorScheme(), darkColorScheme())) {
            val expected = listOf(scheme.primaryFixedDim, scheme.tertiaryContainer, scheme.secondaryFixedDim, scheme.primaryContainer, scheme.tertiaryFixedDim)
            assertEquals(expected, morphPalette(scheme))
        }
        // The containers follow the theme; the fixed roles keep their tone in both.
        val light = morphPalette(lightColorScheme())
        val dark = morphPalette(darkColorScheme())
        assertTrue(light[1] != dark[1] && light[3] != dark[3])
        assertEquals(light[0], dark[0])
        // Three shapes at most, five colours, given in turn: no two on screen alike.
        assertEquals(5, light.toSet().size)
        assertEquals(5, dark.toSet().size)
    }

    @Test
    fun theMorphStartsOnEveryBounceAndHasCompletedBeforeTheNext() {
        var bounces = 0
        for (seed in 1..40) {
            val physics = world(seed)
            val lastShape = HashMap<Int, Int>()
            val lastProgress = HashMap<Int, Float>()
            run(physics, 30f) {
                for (b in physics.bodies) {
                    val before = lastShape[b.id]
                    if (before != null && b.shapeIndex != before) {
                        assertEquals("seed $seed: shape ${b.id} skipped a shape", before + 1, b.shapeIndex)
                        assertTrue("seed $seed: shape ${b.id} morph at ${b.morphProgress} just after a bounce", b.morphProgress < 0.5f)
                        // The shape it was morphing into had formed before this bounce began the next one.
                        if (b.bounces > 1) {
                            val p = lastProgress.getValue(b.id)
                            assertTrue("seed $seed: shape ${b.id} only $p formed at its next bounce", abs(p - 1f) <= 0.02f)
                        }
                        bounces++
                    }
                    assertEquals("seed $seed: shape ${b.id} changed shape ${b.shapeIndex - b.id * 7} times in ${b.bounces} bounces", b.bounces, b.shapeIndex - b.id * 7)
                    lastShape[b.id] = b.shapeIndex
                    lastProgress[b.id] = b.morphProgress
                }
            }
        }
        assertTrue("only $bounces bounces", bounces > 400)
    }

    @Test
    fun aSettledMorphIsExactlyTheNewShape() {
        val physics = MorphBouncePhysics(Random(5), autoSpawn = false).apply { resize(4.11f, 1.6f, listOf(0.1f)) }
        val body = physics.spawn()!!
        var peak = 0f
        var settledAfterBounce = false
        var sinceBounce = -1f
        var lastBounces = 0
        run(physics, 6f) {
            if (body !in physics.bodies) return@run
            if (body.bounces != lastBounces) { lastBounces = body.bounces; sinceBounce = 0f } else if (sinceBounce >= 0f) sinceBounce += 1f / 120f
            peak = maxOf(peak, body.morphProgress)
            if (sinceBounce > 0.4f && body.penetration == 0f) {
                assertEquals("morph ${sinceBounce}s after a bounce", 1f, body.morphProgress, 0f)
                settledAfterBounce = true
            }
        }
        // Google's expressive spatial springs overshoot a touch, then settle.
        assertTrue("peak $peak", peak > 1.02f && peak < 1.12f)
        assertTrue(settledAfterBounce)
    }

    @Test
    fun squashStretchAndScaleStayWithinTheirBoundsAndNothingFadesAsItLeaves() {
        var deepest = 0f
        var tallest = 0f
        var left = 0
        for (seed in 1..40) {
            val physics = world(seed)
            val lastSeen = HashMap<Int, MorphBouncePhysics.Body>()
            val scaleAtLastFrame = HashMap<Int, Float>()
            run(physics, 30f) {
                for (b in physics.bodies) {
                    val pose = morphPoseOf(b)
                    assertTrue("seed $seed: shape ${b.id} squash ${b.squash}", b.squash in -MorphBouncePhysics.MAX_JELLY_STRETCH..MorphBouncePhysics.MAX_SQUASH)
                    // Squashed or stretched, its area is kept.
                    assertEquals("seed $seed: shape ${b.id} area", 1f, pose.scaleX * pose.scaleY, 1e-4f)
                    assertTrue("seed $seed: shape ${b.id} stretch ${pose.stretch}", pose.stretch in 0f..MAX_FLIGHT_STRETCH)
                    assertTrue("seed $seed: shape ${b.id} scale ${pose.scale}", pose.scale in 0f..MorphBouncePhysics.MAX_APPEAR)
                    assertTrue("seed $seed: shape ${b.id} rotation ${pose.rotationDegrees}", pose.rotationDegrees.isFinite())
                    deepest = maxOf(deepest, b.squash)
                    tallest = minOf(tallest, b.squash)
                    lastSeen[b.id] = b
                    scaleAtLastFrame[b.id] = pose.scale
                }
                val alive = physics.bodies.map { it.id }.toSet()
                for (id in lastSeen.keys - alive) {
                    lastSeen.remove(id)
                    left++
                    // Shapes leave by flying off the screen, drawn whole to the last: nothing shrinks or fades.
                    assertEquals("seed $seed: shape $id scale as it left", 1f, scaleAtLastFrame.remove(id)!!, 0.03f)
                }
            }
        }
        // Dense as they are, shapes squash a little in the membrane and wobble a little off the hard floor.
        assertTrue("never squashed: $deepest", deepest > 0.08f)
        assertTrue("never wobbled tall: $tallest", tallest < -0.01f)
        assertTrue("only $left shapes left", left > 200)
    }

    @Test
    fun aNewShapeScalesInFromNothing() {
        val physics = MorphBouncePhysics(Random(8), autoSpawn = false).apply { resize(4.11f, 1.6f, listOf(0.1f)) }
        val body = physics.spawn()!!
        assertEquals(0f, morphPoseOf(body).scale, 0f)
        var t = 0f
        var grown = -1f
        while (t < 1f) {
            physics.advance(1f / 120f)
            t += 1f / 120f
            if (grown < 0f && body.appear == 1f) grown = t
        }
        assertTrue("grew in ${grown}s", grown in 0.2f..0.7f)
        assertEquals(1f, morphPoseOf(body).scale, 0f)
    }

    @Test
    fun eachMorphTurnsTheShapeAQuarterTurnTheWayItRolls() {
        val physics = MorphBouncePhysics(Random(6), autoSpawn = false).apply { resize(4.11f, 1.6f, listOf(0.1f)) }
        val body = physics.spawn()!!
        // The turn the morphs add to the shape's roll, between -180 and 180 degrees.
        fun morphTurn(): Float = (((morphPoseOf(body).rotationDegrees - body.angle) % 360f) + 540f) % 360f - 180f
        assertEquals(0f, morphTurn(), 0.01f)
        var t = 0f
        while (body in physics.bodies && body.bounces < 1 && t < 5f) { physics.advance(1f / 120f); t += 1f / 120f }
        assertEquals(1, body.bounces)
        while (body in physics.bodies && body.morphProgress != 1f) physics.advance(1f / 120f)
        assertEquals(body.turnDirection * MORPH_TURN_DEGREES, morphTurn(), 0.01f)
    }
}
