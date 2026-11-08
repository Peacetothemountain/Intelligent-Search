package com.pixel.intelligentsearch.feature.search.today

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The scrim over On This Day's photo: the approved gradient, 15% black at the top to 65% at the bottom, made darker
 * behind [label] and [text] (rects in the tile) only as far as the photo under them needs for white text to read at
 * 4.5:1 (WCAG AA). [picture] is drawn over the [tile] cropped to fill it, centered.
 */
internal fun photoScrim(picture: ImageBitmap, tile: Size, label: Rect, text: Rect): Brush {
    fun approved(f: Float) = 0.15f + 0.5f * f
    val labelNeeds = scrimForWhiteText(brightestUnder(picture, tile, label), LABEL_ALPHA)
    val textNeeds = scrimForWhiteText(brightestUnder(picture, tile, text), DETAIL_ALPHA)
    val h = tile.height.takeIf { it > 0f } ?: return Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.65f)))
    val labelEnd = (label.bottom / h).coerceIn(0f, 1f)
    val textStart = (text.top / h).coerceIn(0f, 1f)
    fun stop(f: Float, needs: Float) = f to Color.Black.copy(alpha = max(approved(f), needs))
    // Labels that run into each other (very large text) take the darker of the two all the way down.
    if (labelEnd >= textStart) {
        val needs = max(labelNeeds, textNeeds)
        return Brush.verticalGradient(stop(0f, needs), stop(1f, needs))
    }
    return Brush.verticalGradient(stop(0f, labelNeeds), stop(labelEnd, labelNeeds), stop(textStart, textNeeds), stop(1f, textNeeds))
}

/** The label's white is drawn at 90%, the detail's at 85%: the dimmest white on the tile decides each band. */
private const val LABEL_ALPHA = 0.9f
private const val DETAIL_ALPHA = 0.85f

/**
 * The relative luminance most of the photo under [area] stays below (its 95th percentile, so a few bright pixels
 * don't darken the whole tile); 0 when it can't be read.
 */
private fun brightestUnder(picture: ImageBitmap, tile: Size, area: Rect): Float {
    if (tile.width <= 0f || tile.height <= 0f || area.isEmpty) return 0f
    // ContentScale.Crop, centered: the photo scaled to cover the tile, its overflow cut evenly from both sides.
    val scale = max(tile.width / picture.width, tile.height / picture.height)
    val left = (picture.width - tile.width / scale) / 2f
    val top = (picture.height - tile.height / scale) / 2f
    val x0 = (left + area.left / scale).toInt().coerceIn(0, picture.width - 1)
    val y0 = (top + area.top / scale).toInt().coerceIn(0, picture.height - 1)
    val x1 = (left + area.right / scale).toInt().coerceIn(x0 + 1, picture.width)
    val y1 = (top + area.bottom / scale).toInt().coerceIn(y0 + 1, picture.height)
    val pixels = runCatching { picture.toPixelMap(x0, y0, x1 - x0, y1 - y0).buffer }.getOrNull() ?: return 0f
    val luminance = FloatArray(pixels.size) { i ->
        val p = pixels[i]
        0.2126f * LINEAR[p shr 16 and 0xFF] + 0.7152f * LINEAR[p shr 8 and 0xFF] + 0.0722f * LINEAR[p and 0xFF]
    }
    luminance.sort()
    return luminance[min(luminance.size - 1, (luminance.size * 0.95f).toInt())]
}

/**
 * The least black, from 0 to 0.95, that a photo of relative luminance [photo] needs over it for white text at
 * [textAlpha] to read at 4.5:1. The photo is taken as a gray of that luminance; the canvas blends in sRGB.
 */
internal fun scrimForWhiteText(photo: Float, textAlpha: Float): Float {
    val gray = encode(photo)
    for (step in 0..95) {
        val scrim = step / 100f
        val behind = gray * (1f - scrim)
        val ink = textAlpha + (1f - textAlpha) * behind
        // A little over 4.5, so rounding to 8-bit color can't take it under.
        if ((decode(ink) + 0.05f) / (decode(behind) + 0.05f) >= 4.6f) return scrim
    }
    return 0.95f
}

private fun decode(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun encode(l: Float): Float = if (l <= 0.0031308f) l * 12.92f else 1.055f * l.pow(1f / 2.4f) - 0.055f

private val LINEAR = FloatArray(256) { decode(it / 255f) }
