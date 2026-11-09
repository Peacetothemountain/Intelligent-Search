package com.pixel.intelligentsearch.feature.search.today.cards.site

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.get
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * A website card's picture and color.
 *
 * NG Designs
 */

/** The longest side a card's picture is kept at. */
internal const val SITE_IMAGE_MAX_SIDE = 512

/** Pictures larger than this on a side are not worth decoding for a card. */
private const val SOURCE_MAX_SIDE = 16_384

/**
 * [bytes] (a page's header image, already capped at its download limit) decoded small: its size is read first without
 * decoding, then it is decoded at the largest power-of-two step down that keeps it at least [SITE_IMAGE_MAX_SIDE] on its
 * longest side (so a huge picture never fills memory), then scaled so that side is at most [SITE_IMAGE_MAX_SIDE].
 * Animated images give their first frame. Null when it isn't a picture Android can decode, or is absurdly large or tiny.
 */
internal fun decodeSiteImage(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width < 8 || height < 8 || width > SOURCE_MAX_SIDE || height > SOURCE_MAX_SIDE) return null
    var sample = 1
    while (max(width, height) / (sample * 2) >= SITE_IMAGE_MAX_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull() ?: return null
    val longest = max(decoded.width, decoded.height)
    if (longest <= SITE_IMAGE_MAX_SIDE) return decoded
    val scale = SITE_IMAGE_MAX_SIDE.toFloat() / longest
    val scaled = decoded.scale(
        (decoded.width * scale).roundToInt().coerceIn(1, SITE_IMAGE_MAX_SIDE),
        (decoded.height * scale).roundToInt().coerceIn(1, SITE_IMAGE_MAX_SIDE),
        true
    )
    if (scaled !== decoded) decoded.recycle()
    return scaled
}

/** [bitmap] as the bytes the cache keeps: WebP (lossy, alpha kept), small and quick to load. */
internal fun encodeSiteImage(bitmap: Bitmap): ByteArray? {
    val out = ByteArrayOutputStream()
    return if (bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)) out.toByteArray() else null
}

/** The dominant saturated color of [bitmap] ([dominantAccent] over a small sample of its pixels), or null. */
internal fun imageAccent(bitmap: Bitmap): Int? {
    val step = max(1, max(bitmap.width, bitmap.height) / 64)
    val columns = (bitmap.width + step - 1) / step
    val rows = (bitmap.height + step - 1) / step
    val pixels = IntArray(columns * rows)
    var k = 0
    for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) pixels[k++] = bitmap[x, y]
    return dominantAccent(pixels.copyOf(k))
}

private const val HUE_BINS = 24

/**
 * The dominant saturated color among [pixels] (ARGB): pixels that are opaque enough, colorful enough and neither near
 * black nor near white are grouped by hue into [HUE_BINS] bins, each weighted by how saturated it is, and the heaviest
 * bin's pixels are averaged. Null when too few pixels are colorful (a gray or black-and-white picture), so the card
 * falls back to [domainAccent]. Pure, so it works on any pixels.
 */
internal fun dominantAccent(pixels: IntArray): Int? {
    if (pixels.isEmpty()) return null
    val weight = FloatArray(HUE_BINS)
    val red = FloatArray(HUE_BINS)
    val green = FloatArray(HUE_BINS)
    val blue = FloatArray(HUE_BINS)
    var colorful = 0
    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val value = high / 255f
        val saturation = if (high == 0) 0f else (high - low).toFloat() / high
        if (saturation < 0.35f || value < 0.2f || value > 0.98f && saturation < 0.5f) continue
        val hue = hueOf(r, g, b, high, low)
        val bin = ((hue / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
        val w = saturation * (0.5f + value / 2f)
        weight[bin] += w
        red[bin] += r * w
        green[bin] += g * w
        blue[bin] += b * w
        colorful++
    }
    if (colorful < max(4, pixels.size / 20)) return null
    val best = weight.indices.maxBy { weight[it] }
    val total = weight[best]
    if (total <= 0f) return null
    return (0xFF shl 24) or
        ((red[best] / total).roundToInt().coerceIn(0, 255) shl 16) or
        ((green[best] / total).roundToInt().coerceIn(0, 255) shl 8) or
        (blue[best] / total).roundToInt().coerceIn(0, 255)
}

private fun hueOf(r: Int, g: Int, b: Int, high: Int, low: Int): Float {
    val delta = (high - low).toFloat()
    if (delta == 0f) return 0f
    val hue = when (high) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}

/** Material-like tones a site without a picture is colored from: deep enough for light text, distinct from each other. */
private val DOMAIN_TONES = intArrayOf(
    0xFF6750A4.toInt(), // violet
    0xFF0B57D0.toInt(), // blue
    0xFF00639B.toInt(), // azure
    0xFF006A6A.toInt(), // teal
    0xFF146C2E.toInt(), // green
    0xFF5B6300.toInt(), // olive
    0xFF8B5000.toInt(), // amber
    0xFFA0410D.toInt(), // orange
    0xFFB3261E.toInt(), // red
    0xFF984061.toInt() // rose
)

/**
 * A color for [domain] when its page has no picture: one of a few Material-like tones, picked by a stable hash
 * (FNV-1a over the name without "www."), so a site keeps its color on every phone and every read.
 */
internal fun domainAccent(domain: String): Int {
    var hash = 0x811C9DC5.toInt()
    for (b in domain.removePrefix("www.").toByteArray(Charsets.UTF_8)) {
        hash = hash xor (b.toInt() and 0xFF)
        hash *= 0x01000193
    }
    return DOMAIN_TONES[Math.floorMod(hash, DOMAIN_TONES.size)]
}
