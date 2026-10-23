package com.pixel.intelligentsearch.feature.launch

import kotlin.math.max
import kotlin.math.roundToInt

/** A side of the screen the floating button rests on, as stored in settings. */
enum class ScreenEdge(val stored: String) {
    LEFT("left"),
    RIGHT("right");

    companion object {
        /** The edge stored as [stored]; an unknown value (an old or crafted backup) reads as the right edge. */
        fun from(stored: String): ScreenEdge = entries.firstOrNull { it.stored == stored } ?: RIGHT
    }
}

/**
 * The part of the screen the floating button may use, in screen pixels: the display minus the status bar, the
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
