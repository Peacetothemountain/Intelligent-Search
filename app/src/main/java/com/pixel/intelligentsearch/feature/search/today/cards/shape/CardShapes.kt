package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import com.pixel.intelligentsearch.feature.search.today.cards.CARD_SHAPES
import com.pixel.intelligentsearch.feature.settings.MaterialMorphShapes
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The Material shapes a Discover card can take, as polar outlines: for each of [OUTLINE_POINTS] directions round the
 * shape's center, how far its outline reaches. One outline per shape name, sampled once from the exact Material 3
 * Expressive polygon and shared by every card of that shape; morphs blend two of them point by point.
 *
 * NG Designs
 */

/** The side of a shaped design's square, in design units: every shaped design is drawn on 180 by 180. */
internal const val DESIGN = 180f

/** The middle of a shaped design's square, in design units. */
internal const val MID = DESIGN / 2f

/** Directions round an outline, one per degree. */
internal const val OUTLINE_POINTS = 360

/** How a shaped design arranges itself round its shape. */
internal enum class ShapeClass {
    /** Circles, squares, cookies and the arch: an even rim with room in the middle. */
    ROUND,

    /** Clovers and the flower: deep lobes that become compartments. */
    LOBED,

    /** Suns, bursts, the pentagon and the gem: points round a smaller middle. */
    SPIKY
}

/**
 * A shape's outline: its reach [r] in each of [OUTLINE_POINTS] directions (direction i is i degrees clockwise from
 * the +x axis, y down), in the polygon's own units, with the center [cx], [cy] and largest side [span] of its bounds.
 * [opt] is its optical size: the scale that gives it the same visual area as a circle filling the square, so a spiky
 * shape doesn't look smaller than a round one, scaled so the largest fills the square exactly (0.83 to 1).
 */
@Immutable
internal class CardOutline(val r: FloatArray, val cx: Float, val cy: Float, val span: Float, val opt: Float) {

    /** Design units per polygon unit, optical size included. */
    val scale: Float = if (span > 0f) DESIGN / span * opt else 1f

    /** The shortest distance from the design's middle to the outline, in design units. */
    val inner: Float by lazy(LazyThreadSafetyMode.NONE) {
        var m = Float.MAX_VALUE
        for (i in 0 until OUTLINE_POINTS) {
            val t = i * STEP
            m = min(m, hypot((r[i] * cos(t) - cx) * scale, (r[i] * sin(t) - cy) * scale))
        }
        m
    }

    /** How much a design's content shrinks to stay inside a narrow middle: [inner] over 80, kept within 0.7 to 1. */
    val contentFit: Float get() = (inner / 80f).coerceIn(0.7f, 1f)

    private val paths = HashMap<Float, Path>(4)

    /**
     * The outline in design units at [at] of its size round the middle (an edge gauge runs at 0.84 or so), starting at
     * the top and running clockwise. Kept per [at], so it is built once per shape.
     */
    fun path(at: Float = 1f): Path = paths.getOrPut(at) { Path().also { addTo(it, DESIGN, 0f, 0f, at) } }

    /** Adds the outline to [path] in a square of [side] at [left], [top], at [at] of its size round the square's middle. */
    fun addTo(path: Path, side: Float, left: Float, top: Float, at: Float = 1f) {
        val k = side / DESIGN * scale * at
        val half = side / 2f
        val start = OUTLINE_POINTS * 3 / 4
        for (j in 0..OUTLINE_POINTS) {
            val i = (start + j) % OUTLINE_POINTS
            val t = i * STEP
            val x = left + half + (r[i] * cos(t) - cx) * k
            val y = top + half + (r[i] * sin(t) - cy) * k
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    /** This outline [k] of the way to [other] (k may overshoot 1 a little, as a spring does). */
    fun blend(other: CardOutline, k: Float): CardOutline {
        val out = FloatArray(OUTLINE_POINTS) { r[it] + (other.r[it] - r[it]) * k }
        return CardOutline(
            out,
            cx + (other.cx - cx) * k,
            cy + (other.cy - cy) * k,
            (span + (other.span - span) * k).coerceAtLeast(0.01f),
            opt + (other.opt - opt) * k
        )
    }

    companion object {
        const val STEP = (2.0 * PI / OUTLINE_POINTS).toFloat()

        /** The largest optical size a shape may have before every shape is brought back within its square. */
        private const val OPT_MOST = 1.08f

        /** An outline from its reaches, measuring its bounds and optical size. */
        fun of(r: FloatArray): CardOutline {
            var x0 = Float.MAX_VALUE
            var x1 = -Float.MAX_VALUE
            var y0 = Float.MAX_VALUE
            var y1 = -Float.MAX_VALUE
            var area = 0.0
            for (i in 0 until OUTLINE_POINTS) {
                val t = i * STEP
                val x = r[i] * cos(t)
                val y = r[i] * sin(t)
                x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
                area += r[i].toDouble() * r[i] * PI / OUTLINE_POINTS
            }
            val span = max(x1 - x0, y1 - y0).coerceAtLeast(0.01f)
            // Optical size: the same visual area as a circle filling the square, within what the cell can take.
            // Never past the square: the most a shape grows is to fill it, and rounder shapes come down to match, so a
            // card's shape never reaches into the space between cards or past the screen's edge.
            val opt = sqrt((PI / 4) / (area / (span * span))).toFloat().coerceIn(0.9f, OPT_MOST) / OPT_MOST
            return CardOutline(r, (x0 + x1) / 2f, (y0 + y1) / 2f, span, opt)
        }
    }
}

/** The shapes' outlines, classes and lobes by the names [CARD_SHAPES] lists. */
internal object CardShapes {

    /** Each card shape's Material shape, from MaterialMorphShapes (the exact MaterialShapes definitions). */
    private val MATERIAL = mapOf(
        "circle" to "Circle", "square" to "Square", "cookie4" to "Cookie4Sided", "cookie6" to "Cookie6Sided",
        "cookie9" to "Cookie9Sided", "cookie12" to "Cookie12Sided", "clover4" to "Clover4Leaf", "clover8" to "Clover8Leaf",
        "sunny" to "Sunny", "verysunny" to "VerySunny", "softburst" to "SoftBurst", "flower" to "Flower",
        "pentagon" to "Pentagon", "gem" to "Gem", "arch" to "Arch"
    )

    /** How many lobes the lobed shapes have, for laying a list out one row per lobe. */
    private val LOBES = mapOf("clover4" to 4, "clover8" to 8, "flower" to 8)

    private const val SAMPLES_PER_CUBIC = 48

    private val outlines = HashMap<String, CardOutline>()
    private val lobeAngles = HashMap<String, FloatArray>()

    /** The circle every unknown name falls back to. */
    private val circle: CardOutline by lazy { CardOutline.of(FloatArray(OUTLINE_POINTS) { 1f }) }

    /** [name]'s outline, sampled the first time it is asked for and shared from then on (a circle for unknown names). */
    fun outline(name: String): CardOutline = synchronized(outlines) {
        outlines.getOrPut(name) {
            val polygon = MATERIAL[name]?.let { MaterialMorphShapes.byName[it] }
            if (polygon == null) circle else CardOutline.of(sample(polygon))
        }
    }

    /** How a design arranges itself round [name]. */
    fun shapeClass(name: String): ShapeClass = when (name) {
        "clover4", "clover8", "flower" -> ShapeClass.LOBED
        "sunny", "verysunny", "softburst", "pentagon", "gem" -> ShapeClass.SPIKY
        else -> ShapeClass.ROUND
    }

    /**
     * Whether [name]'s middle is too tight for a curved name along the bottom: its inner radius under 70 of 180 (the
     * four-leaf clover), when the name steps aside and a stale note takes the label's line.
     */
    fun isTight(name: String): Boolean = name == "clover4" || outline(name).inner < 70f

    /**
     * The directions of [name]'s lobes, in degrees clockwise from the top (a lobed shape's compartments), or an empty
     * array for shapes without lobes.
     */
    fun lobes(name: String): FloatArray = synchronized(lobeAngles) {
        lobeAngles.getOrPut(name) {
            val n = LOBES[name] ?: return@getOrPut FloatArray(0)
            val o = outline(name)
            val step = OUTLINE_POINTS / n
            // The phase whose n evenly spaced directions reach farthest: the lobes' tips.
            var best = 0
            var bestReach = -1f
            for (phase in 0 until step) {
                var reach = 0f
                for (j in 0 until n) reach += o.r[(phase + j * step) % OUTLINE_POINTS]
                if (reach > bestReach) { bestReach = reach; best = phase }
            }
            FloatArray(n) { j -> ((best + j * step) + 90f) % 360f }
        }
    }

    /** [polygon]'s outline: the outermost reach of its curves in each direction round its center, sampled densely. */
    private fun sample(polygon: RoundedPolygon): FloatArray {
        val cx = polygon.centerX
        val cy = polygon.centerY
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        for (c in polygon.cubics) {
            for (i in 0 until SAMPLES_PER_CUBIC) {
                val t = i.toFloat() / SAMPLES_PER_CUBIC
                val u = 1f - t
                val x = u * u * u * c.anchor0X + 3f * u * u * t * c.control0X + 3f * u * t * t * c.control1X + t * t * t * c.anchor1X
                val y = u * u * u * c.anchor0Y + 3f * u * u * t * c.control0Y + 3f * u * t * t * c.control1Y + t * t * t * c.anchor1Y
                xs += x - cx
                ys += y - cy
            }
        }
        val out = FloatArray(OUTLINE_POINTS)
        if (xs.isEmpty()) return out.also { it.fill(1f) }
        val step = 2.0 * PI / OUTLINE_POINTS
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
                val f = if (hi - lo < 1e-9) 0.0 else (k * step - a1) / (a2 - a1)
                val r = (r1 + (r2 - r1) * f).toFloat()
                val bin = Math.floorMod(k, OUTLINE_POINTS)
                out[bin] = max(out[bin], r)
                k++
            }
        }
        // Any direction no segment crossed (none should be) takes its neighbours' reach.
        for (k in 0 until OUTLINE_POINTS) {
            if (out[k] <= 0f) out[k] = max(out[(k + OUTLINE_POINTS - 1) % OUTLINE_POINTS], out[(k + 1) % OUTLINE_POINTS]).coerceAtLeast(0.05f)
        }
        return out
    }
}

/**
 * [name]'s outline as a Compose [Shape], for clipping: the shape at its optical size, centered in the largest square
 * that fits the bounds it is given.
 */
@Immutable
internal data class CardOutlineShape(val name: String) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = min(size.width, size.height)
        if (side <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
        val path = Path()
        CardShapes.outline(name).addTo(path, side, (size.width - side) / 2f, (size.height - side) / 2f)
        return Outline.Generic(path)
    }
}
