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
 * How the Material Morph animation looks: Material 3 Expressive shapes, Material You color roles, a morph that
 * completes after every bounce, a scale-in that stays within its bounds, a roll that is the shape's own, and no fading.
 */
class MaterialMorphLookTest {

    /** Every shape in androidx.compose.material3.MaterialShapes, by name. */
    private val materialShapeNames = setOf(
        "Circle", "Square", "Slanted", "Arch", "Fan", "Arrow", "SemiCircle", "Oval", "Pill", "Triangle", "Diamond",
        "ClamShell", "Pentagon", "Gem", "Sunny", "VerySunny", "Cookie4Sided", "Cookie6Sided", "Cookie7Sided",
        "Cookie9Sided", "Cookie12Sided", "Ghostish", "Clover4Leaf", "Clover8Leaf", "Burst", "SoftBurst", "Boom",
        "SoftBoom", "Flower", "Puffy", "PuffyDiamond", "PixelCircle", "PixelTriangle", "Bun", "Heart"
    )

    private val phone = morphSceneFor(411f * 3f, 160f * 3f, 3f)!!

    private fun world(seed: Int) = MorphBouncePhysics(Random(seed)).apply {
        resize(phone.widthMetres, phone.heightMetres, phone.radiiMetres)
    }

    private fun quietWorld(seed: Int) = MorphBouncePhysics(Random(seed), autoSpawn = false).apply {
        resize(phone.widthMetres, phone.heightMetres, phone.radiiMetres)
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
        // Four shapes at most, five colors, given in turn: no two on screen alike.
        assertEquals(5, light.toSet().size)
        assertEquals(5, dark.toSet().size)
    }

    @Test
    fun theMorphStartsAfterEveryBounceAndHasCompletedBeforeTheNext() {
        var changes = 0
        for (seed in 1..12) {
            val physics = world(seed)
            val firstShape = HashMap<Int, Int>()
            val lastShape = HashMap<Int, Int>()
            val lastProgress = HashMap<Int, Float>()
            run(physics, 30f) {
                for (b in physics.bodies) {
                    val first = firstShape.getOrPut(b.id) { b.shapeIndex }
                    val before = lastShape[b.id]
                    if (before != null && b.shapeIndex != before) {
                        assertEquals("seed $seed: shape ${b.id} skipped a shape", before + 1, b.shapeIndex)
                        assertTrue("seed $seed: shape ${b.id} morph at ${b.morphProgress} just after it began", b.morphProgress < 0.5f)
                        // The shape it was morphing into had formed before this one began.
                        if (b.shapeIndex - first > 1) {
                            val p = lastProgress.getValue(b.id)
                            assertTrue("seed $seed: shape ${b.id} only $p formed at its next change", abs(p - 1f) <= 0.02f)
                        }
                        changes++
                    }
                    // One new shape for every bounce, begun as the shape leaves the rubber.
                    val made = b.shapeIndex - first
                    assertTrue("seed $seed: shape ${b.id} changed $made times in ${b.bounces} bounces", made == b.bounces || made == b.bounces - 1 && b.inContact)
                    lastShape[b.id] = b.shapeIndex
                    lastProgress[b.id] = b.morphProgress
                }
            }
        }
        assertTrue("only $changes changes", changes > 150)
    }

    @Test
    fun aSettledMorphIsExactlyTheNewShape() {
        val physics = world(5)
        var peak = 0f
        var settled = 0
        val began = HashMap<Int, Float>()
        val lastShape = HashMap<Int, Int>()
        var t = 0f
        run(physics, 20f) {
            t += 1f / 120f
            for (b in physics.bodies) {
                if (lastShape[b.id] != null && lastShape[b.id] != b.shapeIndex) began[b.id] = t
                lastShape[b.id] = b.shapeIndex
                peak = maxOf(peak, b.morphProgress)
                val since = began[b.id] ?: continue
                if (t - since > 0.4f) {
                    assertEquals("morph ${t - since}s after it began", 1f, b.morphProgress, 0f)
                    settled++
                }
            }
        }
        // Google's expressive spatial springs overshoot a touch, then settle.
        assertTrue("peak $peak", peak > 1.02f && peak < 1.12f)
        assertTrue(settled > 0)
    }

    @Test
    fun theScaleStaysWithinItsBoundsAndNothingFadesAsItLeaves() {
        var left = 0
        for (seed in 1..12) {
            val physics = world(seed)
            val lastSeen = HashMap<Int, MorphBouncePhysics.Body>()
            val scaleAtLastFrame = HashMap<Int, Float>()
            run(physics, 30f) {
                for (b in physics.bodies) {
                    val pose = morphPoseOf(b)
                    assertTrue("seed $seed: shape ${b.id} scale ${pose.scale}", pose.scale in 0f..MorphBouncePhysics.MAX_APPEAR)
                    assertTrue("seed $seed: shape ${b.id} rotation ${pose.rotationDegrees}", pose.rotationDegrees.isFinite())
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
        assertTrue("only $left shapes left", left > 60)
    }

    @Test
    fun aNewShapeScalesInFromNothing() {
        val physics = quietWorld(8)
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
    fun aShapeTurnsOnlyAsItRolls() {
        val physics = world(6)
        var checked = 0
        run(physics, 10f) {
            for (b in physics.bodies) {
                // Drawn at its own roll, and pressing the rubber at it: no turn is added that its spin didn't make.
                assertEquals(b.angle % 360f, morphPoseOf(b).rotationDegrees, 1e-3f)
                checked++
            }
        }
        assertTrue(checked > 100)
    }
}
