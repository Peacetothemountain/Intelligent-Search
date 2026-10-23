package com.pixel.intelligentsearch.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes [bytes] scaled down by a power of two until its short side is about [px], so a large picture (a contact
 * photo, a site's icon) never becomes a bitmap many times the size it is drawn at. Null when it isn't an image.
 */
fun decodeSampledBitmap(bytes: ByteArray, px: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
