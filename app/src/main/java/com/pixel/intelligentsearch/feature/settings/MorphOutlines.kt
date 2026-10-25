package com.pixel.intelligentsearch.feature.settings

import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/**
 * The Material Morph shapes' outlines as the physics feels them: for each of [POINTS] directions round the shape's
 * center, how far its outline reaches, in radii of its body (fitted as it is drawn, see [MorphShapeFit]). A morphing
 * shape presses the rubber with its two outlines blended by the morph's progress.
 */
internal object MorphOutlines {
    const val POINTS = 96
    private const val SAMPLES_PER_CUBIC = 24

    /** A circle's outline. */
    val circle = FloatArray(POINTS) { 1f }

    private val cache = HashMap<String, FloatArray>()

    /** The outlines of the shapes [names], in that order. */
    fun forSequence(names: List<String>): List<FloatArray> = synchronized(cache) {
        names.map { name -> cache.getOrPut(name) { of(MaterialMorphShapes.byName.getValue(name)) } }
    }

    /** [polygon]'s outline: the outermost reach of its curves in each direction, sampled densely and interpolated. */
    fun of(polygon: RoundedPolygon): FloatArray {
        val fit = MorphShapeFit.of(polygon)
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        for (c in polygon.cubics) {
            for (i in 0 until SAMPLES_PER_CUBIC) {
                val t = i.toFloat() / SAMPLES_PER_CUBIC
                val u = 1f - t
                val x = u * u * u * c.anchor0X + 3f * u * u * t * c.control0X + 3f * u * t * t * c.control1X + t * t * t * c.anchor1X
                val y = u * u * u * c.anchor0Y + 3f * u * u * t * c.control0Y + 3f * u * t * t * c.control1Y + t * t * t * c.anchor1Y
                xs += (x - fit.centerX) * fit.scale
                ys += (y - fit.centerY) * fit.scale
            }
        }
        val out = FloatArray(POINTS)
        val step = 2.0 * PI / POINTS
        for (i in xs.indices) {
            val j = (i + 1) % xs.size
            val a1 = atan2(ys[i], xs[i]).toDouble()
            var a2 = atan2(ys[j], xs[j]).toDouble()
            while (a2 - a1 > PI) a2 -= 2.0 * PI
            while (a2 - a1 < -PI) a2 += 2.0 * PI
            val r1 = hypot(xs[i], ys[i])
            val r2 = hypot(xs[j], ys[j])
            val lo = minOf(a1, a2)
            val hi = maxOf(a1, a2)
            var k = ceil(lo / step).toInt()
            while (k <= floor(hi / step).toInt()) {
                val t = if (hi - lo < 1e-9) 0.0 else (k * step - a1) / (a2 - a1)
                val r = (r1 + (r2 - r1) * t).toFloat()
                val bin = Math.floorMod(k, POINTS)
                out[bin] = max(out[bin], r)
                k++
            }
        }
        // Any direction no segment crossed (none should be) takes its neighbours' reach.
        for (k in 0 until POINTS) if (out[k] <= 0f) out[k] = max(out[(k + POINTS - 1) % POINTS], out[(k + 1) % POINTS]).coerceAtLeast(0.05f)
        return out
    }
}
