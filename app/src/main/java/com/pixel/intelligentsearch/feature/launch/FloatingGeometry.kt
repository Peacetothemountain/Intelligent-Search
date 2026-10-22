package com.pixel.intelligentsearch.feature.launch

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** A side of the screen the floating button rests on or the edge swipe strip runs along, as stored in settings. */
enum class ScreenEdge(val stored: String, val label: String) {
    LEFT("left", "Left"),
    RIGHT("right", "Right");

    companion object {
        /** The edge stored as [stored]; an unknown value (an old or crafted backup) reads as the right edge. */
        fun from(stored: String): ScreenEdge = entries.firstOrNull { it.stored == stored } ?: RIGHT
    }
}

/**
 * The part of the screen the floating windows may use, in screen pixels: the display minus the status bar, the
 * navigation bar and any cutout. Nothing is placed over the system bars.
 */
data class SafeArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = max(0, right - left)
    val height: Int get() = max(0, bottom - top)
}

/** A window's place on screen, in pixels. */
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Where the floating search button goes. Pure arithmetic on pixels, so the snapping and clamping are unit tested;
 * [FloatingSearchOverlays] feeds it the display's size and system bar insets.
 */
object FloatingButtonGeometry {

    /** Positions are stored as per-mille of the height the button can travel, so they survive rotation. */
    const val POSITION_SCALE = 1000

    /** The edge nearer the button's centre: where a released drag snaps to. */
    fun nearestEdge(x: Int, sizePx: Int, area: SafeArea): ScreenEdge {
        val centre = x + sizePx / 2f
        return if (centre < area.left + area.width / 2f) ScreenEdge.LEFT else ScreenEdge.RIGHT
    }

    /** The button's left x when resting against [edge], [marginPx] in from it. */
    fun restingX(edge: ScreenEdge, sizePx: Int, area: SafeArea, marginPx: Int): Int = when (edge) {
        ScreenEdge.LEFT -> area.left + marginPx
        ScreenEdge.RIGHT -> area.right - marginPx - sizePx
    }.coerceIn(area.left, max(area.left, area.right - sizePx))

    /** Keeps a dragged button's left x wholly on screen, between the side insets. */
    fun clampX(x: Int, sizePx: Int, area: SafeArea): Int = x.coerceIn(area.left, max(area.left, area.right - sizePx))

    /** Keeps the button's top y wholly between the status bar and the navigation bar. */
    fun clampY(y: Int, sizePx: Int, area: SafeArea): Int = y.coerceIn(area.top, max(area.top, area.bottom - sizePx))

    /** The stored position (0 at the top of its travel, [POSITION_SCALE] at the bottom) for a button at [y]. */
    fun positionOf(y: Int, sizePx: Int, area: SafeArea): Int {
        val travel = area.height - sizePx
        if (travel <= 0) return 0
        return ((clampY(y, sizePx, area) - area.top) * POSITION_SCALE.toFloat() / travel).roundToInt()
            .coerceIn(0, POSITION_SCALE)
    }

    /** The top y for a stored [position]; the inverse of [positionOf], clamped into the area. */
    fun yFor(position: Int, sizePx: Int, area: SafeArea): Int {
        val travel = max(0, area.height - sizePx)
        return area.top + (position.coerceIn(0, POSITION_SCALE) * travel.toFloat() / POSITION_SCALE).roundToInt()
    }

    /** Where a released button settles: against the nearer edge, at its own height clamped into the area. */
    fun snapped(x: Int, y: Int, sizePx: Int, area: SafeArea, marginPx: Int): Pair<ScreenEdge, WindowBounds> {
        val edge = nearestEdge(x, sizePx, area)
        return edge to WindowBounds(restingX(edge, sizePx, area, marginPx), clampY(y, sizePx, area), sizePx, sizePx)
    }
}

/**
 * Where the edge swipe strip goes: a thin band against [edge] of the safe area, centred at [positionPercent] of its
 * height and [heightPercent] of it tall. [maxHeightPx], when set, caps the height: with gesture navigation Android
 * only lets an app keep the back gesture off 200dp of each edge, so a taller strip would lose the rest to Back.
 */
fun edgeStripBounds(
    edge: ScreenEdge,
    area: SafeArea,
    positionPercent: Int,
    heightPercent: Int,
    widthPx: Int,
    maxHeightPx: Int? = null
): WindowBounds {
    val wanted = (area.height * heightPercent.coerceIn(0, 100) / 100f).roundToInt()
    val height = (maxHeightPx?.let { minOf(wanted, it) } ?: wanted).coerceIn(0, area.height)
    val centre = area.top + (area.height * positionPercent.coerceIn(0, 100) / 100f).roundToInt()
    val top = (centre - height / 2).coerceIn(area.top, max(area.top, area.bottom - height))
    val width = minOf(widthPx, area.width)
    val x = if (edge == ScreenEdge.LEFT) area.left else area.right - width
    return WindowBounds(x, top, width, height)
}

/**
 * The edge swipe's gesture: a finger that lands on the strip and travels inward past [thresholdPx] arms it (the
 * predictive back haptic plays), pulling back below [releaseFraction] of the threshold disarms it (the cancel
 * haptic), and lifting while armed opens search. A drag that is mostly vertical before it arms (scrolling along the
 * edge) is let go and can't open anything. Pure, so the thresholds are unit tested.
 */
class EdgeSwipeGesture(
    private val edge: ScreenEdge,
    private val thresholdPx: Float,
    private val slopPx: Float,
    private val releaseFraction: Float = 0.75f
) {
    enum class Event { NONE, ARMED, DISARMED, OPEN }

    private var startX = 0f
    private var startY = 0f
    private var tracking = false
    private var armed = false

    val isArmed: Boolean get() = armed

    fun down(x: Float, y: Float) {
        startX = x
        startY = y
        tracking = true
        armed = false
    }

    /** How far the finger has come in from the edge (negative while moving toward it). */
    fun inward(x: Float): Float = if (edge == ScreenEdge.LEFT) x - startX else startX - x

    fun move(x: Float, y: Float): Event {
        if (!tracking) return Event.NONE
        val inward = inward(x)
        val vertical = abs(y - startY)
        if (!armed && vertical > slopPx && vertical > inward) {
            // Mostly vertical: a scroll along the edge, not a swipe in.
            tracking = false
            return Event.NONE
        }
        return when {
            !armed && inward >= thresholdPx -> { armed = true; Event.ARMED }
            armed && inward < thresholdPx * releaseFraction -> { armed = false; Event.DISARMED }
            else -> Event.NONE
        }
    }

    fun up(x: Float, y: Float): Event {
        val event = move(x, y).let { if (armed) Event.OPEN else Event.NONE }
        reset()
        return event
    }

    fun cancel(): Event {
        val wasArmed = armed
        reset()
        return if (wasArmed) Event.DISARMED else Event.NONE
    }

    private fun reset() {
        tracking = false
        armed = false
    }
}
