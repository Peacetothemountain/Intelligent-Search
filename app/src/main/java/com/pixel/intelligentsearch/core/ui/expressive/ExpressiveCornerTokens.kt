package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Rounded rectangle with its own radius per corner, behind the app's corner tokens.
 *
 * Despite the name, the corners are not continuous-curvature squircles: each is a circular arc with an effective
 * radius of r × (1 + [smoothing] / 2), capped at half the shorter side, and the outline is a plain rounded rectangle
 * ([Outline.Rounded]) so everything drawn in it takes the fast rounded-rectangle paths. Smoothing only makes the
 * corner larger.
 */
class SquircleShape(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp,
    val smoothing: Float = 0.6f
) : Shape {
    constructor(cornerRadius: Dp, smoothing: Float = 0.6f) : this(
        topStart = cornerRadius,
        topEnd = cornerRadius,
        bottomEnd = cornerRadius,
        bottomStart = cornerRadius,
        smoothing = smoothing
    )

    val cornerRadius: Dp
        get() = topStart

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) {
            return Outline.Rectangle(size.toRect())
        }

        val isLtr = layoutDirection == LayoutDirection.Ltr
        val tlDp = if (isLtr) topStart else topEnd
        val trDp = if (isLtr) topEnd else topStart
        val brDp = if (isLtr) bottomEnd else bottomStart
        val blDp = if (isLtr) bottomStart else bottomEnd

        val maxRadius = min(w, h) / 2f
        val tlPx = with(density) { tlDp.toPx() }.coerceIn(0f, maxRadius)
        val trPx = with(density) { trDp.toPx() }.coerceIn(0f, maxRadius)
        val brPx = with(density) { brDp.toPx() }.coerceIn(0f, maxRadius)
        val blPx = with(density) { blDp.toPx() }.coerceIn(0f, maxRadius)

        if (tlPx <= 0f && trPx <= 0f && brPx <= 0f && blPx <= 0f) {
            return Outline.Rectangle(size.toRect())
        }

        // Each corner is a circular arc of radius r × (1 + smoothing / 2), capped at half the shorter side. Handing
        // that over as a rounded rectangle, not a path, lets the card's background, border, clip and shadow all take
        // Android's rounded-rectangle fast paths, which a resizing card (a switch's rows rising away) depends on:
        // a generic path makes each of them rasterise the card again at every new height.
        val grow = 1f + smoothing.coerceIn(0f, 1f) * 0.5f
        val cap = min(w, h) / 2f
        fun corner(px: Float) = if (px > 0f) CornerRadius(min(px * grow, cap)) else CornerRadius.Zero
        return Outline.Rounded(
            RoundRect(
                rect = size.toRect(),
                topLeft = corner(tlPx),
                topRight = corner(trPx),
                bottomRight = corner(brPx),
                bottomLeft = corner(blPx)
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        return topStart == other.topStart &&
               topEnd == other.topEnd &&
               bottomEnd == other.bottomEnd &&
               bottomStart == other.bottomStart &&
               smoothing == other.smoothing
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }

    override fun toString(): String =
        "SquircleShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart, smoothing=$smoothing)"
}

/**
 * Corner radius tokens and their [SquircleShape]s.
 */
object ExpressiveCornerTokens {
    val Medium: Dp = 16.dp
    val Large: Dp = 20.dp
    val ExtraLarge: Dp = 28.dp
    val Full: Dp = 1000.dp

    val ShapeMedium: Shape = SquircleShape(Medium)
    val ShapeLarge: Shape = SquircleShape(Large)
    val ShapeExtraLarge: Shape = SquircleShape(ExtraLarge)
    val ShapeFull: Shape = SquircleShape(Full)
}
