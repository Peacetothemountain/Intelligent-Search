package com.pixel.intelligentsearch.feature.settings

import androidx.collection.FloatFloatPair
import androidx.compose.ui.geometry.Offset
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.PointTransformer
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Material 3 Expressive shape set the Material Morph animation morphs between: the shapes of
 * `androidx.compose.material3.MaterialShapes`, the set Pixel's expressive UI (the Setup Wizard, the loading indicator,
 * Pixel launcher icon shapes) is drawn from, https://m3.material.io/styles/shape/overview-principles.
 *
 * `MaterialShapes` itself is still experimental and not in the stable Compose Material 3 this app is built with
 * (1.4.0), so its definitions are reproduced here from the AndroidX source, the same vertices and corner roundings
 * point for point, built with androidx.graphics.shapes (already a dependency):
 * https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt
 * (Copyright 2024 The Android Open Source Project, Apache License 2.0). Every polygon is normalized, as there, so it
 * fits the unit square.
 *
 * The animation uses the rounded, soft members of the set: the ones that read as one calm shape at the size of a
 * fingertip and morph smoothly into each other. It leaves out those that point a way (Arrow, Fan), the spiky bursts
 * with near-sharp corners (Burst, Boom) and the stepped pixel shapes (PixelCircle, PixelTriangle), which flicker
 * through in-between forms when morphed at this size.
 */
internal object MaterialMorphShapes {

    /** The animation's shapes by their MaterialShapes names, in MaterialShapes' order. */
    val byName: Map<String, RoundedPolygon> by lazy {
        linkedMapOf(
            "Circle" to RoundedPolygon.circle(numVertices = 10),
            "Square" to RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = round30),
            "Slanted" to customPolygon(
                listOf(
                    PointNRound(Offset(0.926f, 0.970f), CornerRounding(0.189f, 0.811f)),
                    PointNRound(Offset(-0.021f, 0.967f), CornerRounding(0.187f, 0.057f))
                ),
                reps = 2
            ),
            "Arch" to RoundedPolygon(numVertices = 4, perVertexRounding = listOf(round100, round100, round20, round20))
                .rotated(-135f),
            "SemiCircle" to RoundedPolygon.rectangle(
                width = 1.6f,
                height = 1f,
                perVertexRounding = listOf(round20, round20, round100, round100)
            ),
            "Oval" to RoundedPolygon.circle().transformed { x, y -> FloatFloatPair(x, y * 0.64f) }.rotated(-45f),
            "Pill" to customPolygon(
                listOf(
                    PointNRound(Offset(0.961f, 0.039f), CornerRounding(0.426f)),
                    PointNRound(Offset(1.001f, 0.428f)),
                    PointNRound(Offset(1.000f, 0.609f), CornerRounding(1.000f))
                ),
                reps = 2,
                mirroring = true
            ),
            "Triangle" to RoundedPolygon(numVertices = 3, rounding = round20).rotated(-90f),
            "Diamond" to customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 1.096f), CornerRounding(0.151f, 0.524f)),
                    PointNRound(Offset(0.040f, 0.500f), CornerRounding(0.159f))
                ),
                reps = 2
            ),
            "ClamShell" to customPolygon(
                listOf(
                    PointNRound(Offset(0.171f, 0.841f), CornerRounding(0.159f)),
                    PointNRound(Offset(-0.020f, 0.500f), CornerRounding(0.140f)),
                    PointNRound(Offset(0.170f, 0.159f), CornerRounding(0.159f))
                ),
                reps = 2
            ),
            "Pentagon" to customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, -0.009f), CornerRounding(0.172f)),
                    PointNRound(Offset(1.030f, 0.365f), CornerRounding(0.164f)),
                    PointNRound(Offset(0.828f, 0.970f), CornerRounding(0.169f))
                ),
                reps = 1,
                mirroring = true
            ),
            "Gem" to customPolygon(
                listOf(
                    PointNRound(Offset(0.499f, 1.023f), CornerRounding(0.241f, 0.778f)),
                    PointNRound(Offset(-0.005f, 0.792f), CornerRounding(0.208f)),
                    PointNRound(Offset(0.073f, 0.258f), CornerRounding(0.228f)),
                    PointNRound(Offset(0.433f, -0.000f), CornerRounding(0.491f))
                ),
                reps = 1,
                mirroring = true
            ),
            "Sunny" to RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.8f, rounding = round15),
            "VerySunny" to customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 1.080f), CornerRounding(0.085f)),
                    PointNRound(Offset(0.358f, 0.843f), CornerRounding(0.085f))
                ),
                reps = 8
            ),
            "Cookie4Sided" to customPolygon(
                listOf(
                    PointNRound(Offset(1.237f, 1.236f), CornerRounding(0.258f)),
                    PointNRound(Offset(0.500f, 0.918f), CornerRounding(0.233f))
                ),
                reps = 4
            ),
            "Cookie6Sided" to customPolygon(
                listOf(
                    PointNRound(Offset(0.723f, 0.884f), CornerRounding(0.394f)),
                    PointNRound(Offset(0.500f, 1.099f), CornerRounding(0.398f))
                ),
                reps = 6
            ),
            "Cookie7Sided" to RoundedPolygon.star(numVerticesPerRadius = 7, innerRadius = 0.75f, rounding = round50).rotated(-90f),
            "Cookie9Sided" to RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = 0.8f, rounding = round50).rotated(-90f),
            "Cookie12Sided" to RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = 0.8f, rounding = round50).rotated(-90f),
            "Ghostish" to customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0f), CornerRounding(1.000f)),
                    PointNRound(Offset(1f, 0f), CornerRounding(1.000f)),
                    PointNRound(Offset(1f, 1.140f), CornerRounding(0.254f, 0.106f)),
                    PointNRound(Offset(0.575f, 0.906f), CornerRounding(0.253f))
                ),
                reps = 1,
                mirroring = true
            ),
            "Clover4Leaf" to customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0.074f)),
                    PointNRound(Offset(0.725f, -0.099f), CornerRounding(0.476f))
                ),
                reps = 4,
                mirroring = true
            ),
            "Clover8Leaf" to customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0.036f)),
                    PointNRound(Offset(0.758f, -0.101f), CornerRounding(0.209f))
                ),
                reps = 8
            ),
            "SoftBurst" to customPolygon(
                listOf(
                    PointNRound(Offset(0.193f, 0.277f), CornerRounding(0.053f)),
                    PointNRound(Offset(0.176f, 0.055f), CornerRounding(0.053f))
                ),
                reps = 10
            ),
            "SoftBoom" to customPolygon(
                listOf(
                    PointNRound(Offset(0.733f, 0.454f)),
                    PointNRound(Offset(0.839f, 0.437f), CornerRounding(0.532f)),
                    PointNRound(Offset(0.949f, 0.449f), CornerRounding(0.439f, 1.000f)),
                    PointNRound(Offset(0.998f, 0.478f), CornerRounding(0.174f))
                ),
                reps = 16,
                mirroring = true
            ),
            "Flower" to customPolygon(
                listOf(
                    PointNRound(Offset(0.370f, 0.187f)),
                    PointNRound(Offset(0.416f, 0.049f), CornerRounding(0.381f)),
                    PointNRound(Offset(0.479f, 0.001f), CornerRounding(0.095f))
                ),
                reps = 8,
                mirroring = true
            ),
            "Puffy" to customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0.053f)),
                    PointNRound(Offset(0.545f, -0.040f), CornerRounding(0.405f)),
                    PointNRound(Offset(0.670f, -0.035f), CornerRounding(0.426f)),
                    PointNRound(Offset(0.717f, 0.066f), CornerRounding(0.574f)),
                    PointNRound(Offset(0.722f, 0.128f)),
                    PointNRound(Offset(0.777f, 0.002f), CornerRounding(0.360f)),
                    PointNRound(Offset(0.914f, 0.149f), CornerRounding(0.660f)),
                    PointNRound(Offset(0.926f, 0.289f), CornerRounding(0.660f)),
                    PointNRound(Offset(0.881f, 0.346f)),
                    PointNRound(Offset(0.940f, 0.344f), CornerRounding(0.126f)),
                    PointNRound(Offset(1.003f, 0.437f), CornerRounding(0.255f))
                ),
                reps = 2,
                mirroring = true
            ).transformed { x, y -> FloatFloatPair(x, y * 0.742f) },
            "PuffyDiamond" to customPolygon(
                listOf(
                    PointNRound(Offset(0.870f, 0.130f), CornerRounding(0.146f)),
                    PointNRound(Offset(0.818f, 0.357f)),
                    PointNRound(Offset(1.000f, 0.332f), CornerRounding(0.853f))
                ),
                reps = 4,
                mirroring = true
            ),
            "Bun" to customPolygon(
                listOf(
                    PointNRound(Offset(0.796f, 0.500f)),
                    PointNRound(Offset(0.853f, 0.518f), CornerRounding(1f)),
                    PointNRound(Offset(0.992f, 0.631f), CornerRounding(1f)),
                    PointNRound(Offset(0.968f, 1.000f), CornerRounding(1f))
                ),
                reps = 2,
                mirroring = true
            ),
            "Heart" to customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0.268f), CornerRounding(0.016f)),
                    PointNRound(Offset(0.792f, -0.066f), CornerRounding(0.958f)),
                    PointNRound(Offset(1.064f, 0.276f), CornerRounding(1.000f)),
                    PointNRound(Offset(0.501f, 0.946f), CornerRounding(0.129f))
                ),
                reps = 1,
                mirroring = true
            )
        ).mapValues { (_, polygon) -> polygon.normalized() }
    }

    /**
     * The names of the animation's shapes in the order one shape morphs into the next, shuffled from [seed] (null
     * draws it from [Random.Default]) so the same seed always gives the same sequence. The order never puts a shape
     * next to itself, so every bounce is a visible change.
     */
    fun sequence(seed: Int?): List<String> {
        val random = if (seed != null) Random(seed * 31 + 17) else Random.Default
        return byName.keys.shuffled(random)
    }

    private val round15 = CornerRounding(radius = 0.15f)
    private val round20 = CornerRounding(radius = 0.2f)
    private val round30 = CornerRounding(radius = 0.3f)
    private val round50 = CornerRounding(radius = 0.5f)
    private val round100 = CornerRounding(radius = 1f)

    private fun RoundedPolygon.rotated(degrees: Float): RoundedPolygon {
        val a = degrees / 180f * PI.toFloat()
        val c = cos(a)
        val s = sin(a)
        return transformed { x, y -> FloatFloatPair(x * c - y * s, x * s + y * c) }
    }

    private fun RoundedPolygon.transformed(f: (Float, Float) -> FloatFloatPair): RoundedPolygon =
        transformed(PointTransformer { x, y -> f(x, y) })

    private data class PointNRound(val o: Offset, val r: CornerRounding = CornerRounding.Unrounded)

    /** MaterialShapes' `doRepeat`: [points] repeated [reps] times around [center], mirrored in each section if asked. */
    private fun doRepeat(points: List<PointNRound>, reps: Int, center: Offset, mirroring: Boolean): List<PointNRound> =
        if (mirroring) {
            buildList {
                val angles = points.map { (it.o - center).angleDegrees() }
                val distances = points.map { (it.o - center).getDistance() }
                val actualReps = reps * 2
                val sectionAngle = 360f / actualReps
                repeat(actualReps) {
                    points.indices.forEach { index ->
                        val i = if (it % 2 == 0) index else points.lastIndex - index
                        if (i > 0 || it % 2 == 0) {
                            val a = (sectionAngle * it + if (it % 2 == 0) angles[i] else sectionAngle - angles[i] + 2 * angles[0]).toRadians()
                            add(PointNRound(Offset(cos(a), sin(a)) * distances[i] + center, points[i].r))
                        }
                    }
                }
            }
        } else {
            (0 until points.size * reps).map {
                PointNRound(points[it % points.size].o.rotateDegrees((it / points.size) * 360f / reps, center), points[it % points.size].r)
            }
        }

    private fun Offset.rotateDegrees(angle: Float, center: Offset): Offset {
        val a = angle.toRadians()
        val off = this - center
        return Offset(off.x * cos(a) - off.y * sin(a), off.x * sin(a) + off.y * cos(a)) + center
    }

    private fun Float.toRadians(): Float = this / 360f * 2 * PI.toFloat()

    private fun Offset.angleDegrees(): Float = atan2(y, x) * 180f / PI.toFloat()

    private fun customPolygon(pnr: List<PointNRound>, reps: Int, mirroring: Boolean = false): RoundedPolygon {
        val center = Offset(0.5f, 0.5f)
        val actualPoints = doRepeat(pnr, reps, center, mirroring)
        return RoundedPolygon(
            vertices = FloatArray(actualPoints.size * 2) { ix -> actualPoints[ix / 2].o.let { if (ix % 2 == 0) it.x else it.y } },
            perVertexRounding = actualPoints.map { it.r },
            centerX = center.x,
            centerY = center.y
        )
    }
}

/**
 * How a normalized polygon sits in a shape of radius 1 at the origin: [centerX], [centerY] is the point it turns
 * about, and [scale] makes its farthest point exactly 1 from there, so the shape fills its body's circle without ever
 * poking out of it whichever way it turns.
 */
internal class MorphShapeFit(val centerX: Float, val centerY: Float, val scale: Float) {
    companion object {
        fun of(polygon: RoundedPolygon): MorphShapeFit {
            val cx = polygon.centerX
            val cy = polygon.centerY
            // The outline's farthest point, sampled along every curve (the polygon's own bounds go by its control
            // points, which reach past the outline at rounded corners).
            var farthest = 0f
            for (c in polygon.cubics) {
                for (i in 0..FIT_SAMPLES) {
                    val t = i.toFloat() / FIT_SAMPLES
                    val u = 1f - t
                    val x = u * u * u * c.anchor0X + 3f * u * u * t * c.control0X + 3f * u * t * t * c.control1X + t * t * t * c.anchor1X
                    val y = u * u * u * c.anchor0Y + 3f * u * u * t * c.control0Y + 3f * u * t * t * c.control1Y + t * t * t * c.anchor1Y
                    farthest = max(farthest, hypot(x - cx, y - cy))
                }
            }
            return MorphShapeFit(cx, cy, if (farthest > 0f) 1f / farthest else 1f)
        }

        private const val FIT_SAMPLES = 24
    }
}

/** One step of the animation's shape sequence: the [morph] from one shape to the next, and how each sits in its body. */
internal class MorphStep(val morph: Morph, val from: MorphShapeFit, val to: MorphShapeFit)
