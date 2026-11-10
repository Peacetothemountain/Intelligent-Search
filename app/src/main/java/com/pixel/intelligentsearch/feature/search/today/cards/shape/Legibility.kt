package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.ui.unit.Dp
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardState
import com.pixel.intelligentsearch.feature.search.today.cards.ShapedContent
import com.pixel.intelligentsearch.feature.search.today.cards.SnapLayout
import kotlin.math.max

/*
 * How small a card can go and still read: the grid asks these before it lets a card take a size.
 *
 * NG Designs
 */

/** The smallest a shaped design's main value may draw, in dp. */
private const val MIN_MAIN_DP = 11f

/**
 * The least a shaped design's content is scaled by its shape: the smallest optical size, so a design that fits here
 * fits in every shape (narrow middles shrink content further, but the main value still draws).
 */
private const val LEAST_SHAPE_SCALE = 0.9f

/** How much larger the user's font size draws shaped text, as [ShapedCard] draws it. */
internal fun shapedTextScale(fontScale: Float): Float = fontScale.coerceIn(1f, 1.15f)

/** The main value's size in [content]'s shaped design, in design units (of 180). */
internal fun mainUnits(content: ShapedContent): Float = when (content) {
    is ShapedContent.Weather -> 38f
    is ShapedContent.Timer -> 36f
    is ShapedContent.Alarm -> 36f
    is ShapedContent.Delivery -> 15f
    is ShapedContent.Media -> 12f
    is ShapedContent.Battery -> 42f
    is ShapedContent.Word -> 23f
    is ShapedContent.Countdown -> if (content.days <= 0) 30f else 46f
    is ShapedContent.Memory -> 16f
    is ShapedContent.Year -> 42f
    is ShapedContent.Event -> 30f
    is ShapedContent.Agenda -> 44f
    is ShapedContent.CatchUp -> 44f
    is ShapedContent.Search -> 15f
    is ShapedContent.Tomorrow -> 20f
    is ShapedContent.Site -> 14f
    is ShapedContent.App -> {
        val d = content.data
        val m = d.model
        val hasData = (d.state == AppCardState.READY || d.state == AppCardState.STALE) && m != null
        when {
            !hasData -> if (d.state == AppCardState.PAUSED) 12.5f else 14f
            m.layout == SnapLayout.HERO -> if (m.hero.length > 5) 28f else 34f
            m.layout == SnapLayout.LIST -> if (m.rows.isEmpty()) 15f else 12f
            m.layout == SnapLayout.MONTH -> 40f
            else -> 36f
        }
    }
}

/**
 * Whether [content]'s shaped design reads at [side]: its main value draws at 11 dp or more after optical scaling (in
 * the shape that scales it least, so in any shape) at the user's [fontScale].
 */
internal fun shapedFits(content: ShapedContent, side: Dp, fontScale: Float): Boolean {
    if (side.value <= 0f) return false
    val dp = mainUnits(content) * shapedTextScale(fontScale) * LEAST_SHAPE_SCALE * side.value / DESIGN
    return dp >= MIN_MAIN_DP
}

/**
 * Whether [content]'s box design (an app or website card) fits [width] by [height] without overflow at the user's
 * [fontScale], estimated from its lines' heights and its widest fixed parts. False for designs that have no box.
 */
internal fun boxFits(content: ShapedContent, width: Dp, height: Dp, fontScale: Float): Boolean {
    val w = width.value
    val h = height.value
    if (w <= 0f || h <= 0f) return false
    val fs = fontScale.coerceAtLeast(0.5f)
    val pad = 28f
    val label = 14f * fs
    val small = 15f * fs
    val big = 33f * fs
    return when (content) {
        is ShapedContent.App -> {
            val d = content.data
            val m = d.model
            val hasData = (d.state == AppCardState.READY || d.state == AppCardState.STALE) && m != null
            val (bodyH, bodyW) = when {
                !hasData -> (14f + max(28f, 21f * fs + small)) to (28f + 10f + 120f * fs)
                m.layout == SnapLayout.HERO || m.layout == SnapLayout.CLOCK -> {
                    val chars = if (m.layout == SnapLayout.CLOCK) 5 else m.hero.length.coerceAtLeast(1)
                    (8f + big + if (m.labels.isEmpty()) 0f else small) to (chars * 0.56f * 30f * fs)
                }
                m.layout == SnapLayout.LIST -> if (m.rows.isEmpty()) {
                    (12f + max(24f, 21f * fs + small)) to (34f + 70f * fs)
                } else {
                    (m.rows.size.coerceAtMost(3) * (8f + 18f * fs)) to (120f * fs)
                }
                else -> (6f + big + 10f + max(26f, 15f * fs)) to (7 * 26f + 6 * 2f)
            }
            val headW = 16f + 6f + 60f * fs
            pad + label + bodyH <= h && pad + max(headW, bodyW) <= w
        }
        is ShapedContent.Site -> {
            val textW = w * 0.56f - pad
            pad + label + 8f + 21f * fs <= h && textW >= 90f * fs
        }
        else -> false
    }
}
