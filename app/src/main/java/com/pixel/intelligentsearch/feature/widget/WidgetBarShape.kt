package com.pixel.intelligentsearch.feature.widget

import androidx.annotation.DrawableRes
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.widget.MAX_CORNER_RADIUS
import com.pixel.intelligentsearch.core.data.widget.MAX_OUTLINE_THICKNESS
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings

/**
 * The search bar's shape, in whole dp, as the widget and the editor's live preview both draw it: the pill keeps its
 * height (48dp on the Material design, 56dp on the System design, so every button stays a 48dp touch target) and the
 * outline grows around it. Every radius is at most half the height of what it rounds, so a corner is never asked to
 * be rounder than its shape and nothing is cut off at any size; the outline's inner edge follows its outer edge
 * (the pill's radius is the outer radius less the outline), as Material's nested shapes do.
 *
 * RemoteViews can't set a shape's radius, so the widget picks a background drawn with it ([background]); there is one
 * for every [STEP] dp, so outlines and radii come in steps of [STEP] dp (a value between them, from an older backup,
 * is drawn at the step below).
 */
internal data class WidgetBarShape(
    /** The outline around the pill, 0 for none. */
    val outline: Int,
    /** The pill's height: the bar is this plus the outline above and below. */
    val pillHeight: Int,
    /** The outer edge's corner radius: the outline's, or the pill's when there is no outline. */
    val outerRadius: Int,
    /** The pill's corner radius. */
    val pillRadius: Int,
    /** The Material design's circle button's corner radius: a circle unless the pill's corners are tighter. */
    val circleRadius: Int
) {
    /** The whole bar's height. */
    val height: Int get() = pillHeight + 2 * outline

    /** Whether the circle button is a full circle. */
    val circleIsRound: Boolean get() = circleRadius >= CIRCLE_SIZE / 2

    companion object {
        /** The Material design's circle button's size. */
        const val CIRCLE_SIZE = 48
        const val MATERIAL_PILL_HEIGHT = 48
        const val SYSTEM_PILL_HEIGHT = 56

        /** The outline and corner radius steps, in dp. */
        const val STEP = 2

        private fun step(value: Int) = value - value.mod(STEP)

        fun of(isMaterialDesign: Boolean, outlineThickness: Int, cornerRadius: Int): WidgetBarShape {
            val pillHeight = if (isMaterialDesign) MATERIAL_PILL_HEIGHT else SYSTEM_PILL_HEIGHT
            val outline = step(outlineThickness.coerceIn(0, MAX_OUTLINE_THICKNESS))
            val outerRadius = step(cornerRadius.coerceIn(0, MAX_CORNER_RADIUS)).coerceAtMost((pillHeight + 2 * outline) / 2)
            val pillRadius = (outerRadius - outline).coerceIn(0, pillHeight / 2)
            return WidgetBarShape(
                outline = outline,
                pillHeight = pillHeight,
                outerRadius = outerRadius,
                pillRadius = pillRadius,
                circleRadius = pillRadius.coerceAtMost(CIRCLE_SIZE / 2)
            )
        }

        fun of(settings: WidgetDesignSettings, isMaterialDesign: Boolean): WidgetBarShape =
            of(isMaterialDesign, settings.outlineThickness, settings.cornerRadius)

        /** The largest corner radius that changes anything for this outline: the bar is fully round from here on. */
        fun maxCornerRadius(isMaterialDesign: Boolean, outlineThickness: Int): Int =
            of(isMaterialDesign, outlineThickness, MAX_CORNER_RADIUS).outerRadius

        private val BACKGROUNDS = intArrayOf(
            R.drawable.bg_widget_r0, R.drawable.bg_widget_r2, R.drawable.bg_widget_r4, R.drawable.bg_widget_r6,
            R.drawable.bg_widget_r8, R.drawable.bg_widget_r10, R.drawable.bg_widget_r12, R.drawable.bg_widget_r14,
            R.drawable.bg_widget_r16, R.drawable.bg_widget_r18, R.drawable.bg_widget_r20, R.drawable.bg_widget_r22,
            R.drawable.bg_widget_r24, R.drawable.bg_widget_r26, R.drawable.bg_widget_r28, R.drawable.bg_widget_r30,
            R.drawable.bg_widget_r32, R.drawable.bg_widget_r34, R.drawable.bg_widget_r36, R.drawable.bg_widget_r38,
            R.drawable.bg_widget_r40
        )

        /** The rounded rectangle with [radius] dp corners (tinted when drawn) that the widget's backgrounds use. */
        @DrawableRes
        fun background(radius: Int): Int = BACKGROUNDS[step(radius.coerceIn(0, MAX_CORNER_RADIUS)) / STEP]
    }
}
