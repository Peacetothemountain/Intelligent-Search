package com.pixel.intelligentsearch.feature.widget

import androidx.annotation.DrawableRes
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.widget.MAX_CORNER_RADIUS
import com.pixel.intelligentsearch.core.data.widget.MAX_OUTLINE_THICKNESS
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings

/**
 * The search bar's shape, in whole dp, as the widget and the editor's live preview both draw it: the pill keeps its
 * height (48dp on the Material design, 56dp on the System design, so every button stays a 48dp touch target) and the
 * outline grows around it.
 *
 * Corner Radius is the radius of the search bar itself: the pill, and the Material design's circle button, are drawn
 * with exactly the slider's value, up to half their height (fully round). The outline then follows them around, its
 * outer edge rounded by the pill's radius plus the outline (Material's nested shapes), so it never makes the bar look
 * less round than the slider says; square corners (0) stay square all the way out. Outline Thickness changes only
 * the outline, never the pill's corners.
 *
 * RemoteViews can't set a shape's radius, so the widget picks a background drawn with it ([background]); there is one
 * for every [STEP] dp up to the largest outer radius (the System pill's 28dp plus the thickest outline, 12dp), so
 * outlines and radii come in steps of [STEP] dp (a value between them, from an older backup, is drawn at the step
 * below).
 */
internal data class WidgetBarShape(
    /** The outline around the pill, 0 for none. */
    val outline: Int,
    /** The pill's height: the bar is this plus the outline above and below. */
    val pillHeight: Int,
    /** The outer edge's corner radius: the outline's, or the pill's when there is no outline. */
    val outerRadius: Int,
    /** The pill's corner radius: the Corner Radius slider's value, at most half the pill's height. */
    val pillRadius: Int,
    /** The Material design's circle button's corner radius: the pill's, so a fully round pill has a round circle. */
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

        /** The largest radius a background is drawn with: the System pill fully round inside the thickest outline. */
        const val MAX_BACKGROUND_RADIUS = SYSTEM_PILL_HEIGHT / 2 + MAX_OUTLINE_THICKNESS

        private fun step(value: Int) = value - value.mod(STEP)

        fun pillHeight(isMaterialDesign: Boolean): Int = if (isMaterialDesign) MATERIAL_PILL_HEIGHT else SYSTEM_PILL_HEIGHT

        fun of(isMaterialDesign: Boolean, outlineThickness: Int, cornerRadius: Int): WidgetBarShape {
            val pillHeight = pillHeight(isMaterialDesign)
            val outline = step(outlineThickness.coerceIn(0, MAX_OUTLINE_THICKNESS))
            val pillRadius = step(cornerRadius.coerceIn(0, MAX_CORNER_RADIUS)).coerceAtMost(pillHeight / 2)
            // At most half the bar's height, as the pill's radius is at most half the pill's.
            val outerRadius = if (pillRadius == 0) 0 else pillRadius + outline
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

        /**
         * The Corner Radius slider's end: the pill fully round, whatever the outline. A stored value above it (the
         * Material design's default, 32, from when the slider set the outer edge) draws fully round too.
         */
        fun maxCornerRadius(isMaterialDesign: Boolean): Int = pillHeight(isMaterialDesign) / 2

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
        fun background(radius: Int): Int = BACKGROUNDS[step(radius.coerceIn(0, MAX_BACKGROUND_RADIUS)) / STEP]
    }
}
