package com.pixel.intelligentsearch.core.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes

/**
 * Decodes [bytes] scaled down by a power of two until its short side is about [px], so a large picture (a contact
 * photo, a site's icon) never becomes a bitmap many times the size it is drawn at. Null when it isn't an image.
 */
fun decodeSampledBitmap(bytes: ByteArray, px: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = sampleSize(bounds, px)
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/** The picture [id] decoded as [decodeSampledBitmap] decodes bytes: its short side about [px], never much more. */
fun decodeSampledResource(resources: Resources, @DrawableRes id: Int, px: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeResource(resources, id, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sample = sampleSize(bounds, px)
    return runCatching {
        BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/** The largest power of two that keeps the short side of what [bounds] measured at least [px]. */
private fun sampleSize(bounds: BitmapFactory.Options, px: Int): Int {
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
    return sample
}
