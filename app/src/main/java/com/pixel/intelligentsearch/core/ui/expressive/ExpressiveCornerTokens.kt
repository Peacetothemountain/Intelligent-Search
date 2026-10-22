package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Rounded rectangle with its own radius per corner, behind the app's corner tokens.
 *
 * Despite the name, the corners are not continuous-curvature squircles: each is a circular arc, drawn as the usual
 * cubic Bézier approximation, with an effective radius of r × (1 + [smoothing] / 2), capped at half the shorter
 * side. Smoothing only makes the corner larger.
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

        val path = Path()
        val s = smoothing.coerceIn(0f, 1f)
        val halfW = w / 2f
        val halfH = h / 2f
        val minHalfDim = min(halfW, halfH)

        // Zero-allocation inline onset & handle calculations
        val lTL = if (tlPx > 0f) min(tlPx * (1f + s * 0.5f), minHalfDim) else 0f
        val cTL = if (tlPx > 0f) tlPx * 0.55228475f * (lTL / tlPx) else 0f

        val lTR = if (trPx > 0f) min(trPx * (1f + s * 0.5f), minHalfDim) else 0f
        val cTR = if (trPx > 0f) trPx * 0.55228475f * (lTR / trPx) else 0f

        val lBR = if (brPx > 0f) min(brPx * (1f + s * 0.5f), minHalfDim) else 0f
        val cBR = if (brPx > 0f) brPx * 0.55228475f * (lBR / brPx) else 0f

        val lBL = if (blPx > 0f) min(blPx * (1f + s * 0.5f), minHalfDim) else 0f
        val cBL = if (blPx > 0f) blPx * 0.55228475f * (lBL / blPx) else 0f

        path.moveTo(lTL, 0f)

        // Top edge to top-right
        path.lineTo(w - lTR, 0f)
        if (lTR > 0f) {
            path.cubicTo(
                w - lTR + cTR, 0f,
                w, lTR - cTR,
                w, lTR
            )
        } else {
            path.lineTo(w, 0f)
        }

        // Right edge to bottom-right
        path.lineTo(w, h - lBR)
        if (lBR > 0f) {
            path.cubicTo(
                w, h - lBR + cBR,
                w - lBR + cBR, h,
                w - lBR, h
            )
        } else {
            path.lineTo(w, h)
        }

        // Bottom edge to bottom-left
        path.lineTo(lBL, h)
        if (lBL > 0f) {
            path.cubicTo(
                lBL - cBL, h,
                0f, h - lBL + cBL,
                0f, h - lBL
            )
        } else {
            path.lineTo(0f, h)
        }

        // Left edge to top-left
        path.lineTo(0f, lTL)
        if (lTL > 0f) {
            path.cubicTo(
                0f, lTL - cTL,
                lTL - cTL, 0f,
                lTL, 0f
            )
        } else {
            path.lineTo(0f, 0f)
        }

        path.close()
        return Outline.Generic(path)
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
