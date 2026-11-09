package com.pixel.intelligentsearch.feature.search.today.cards.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * NG Designs
 */

/** The edge of an app card's icon, in dp: enough for the card's states and the Add card list. */
private const val ICON_DP = 48

/** The accent when an icon has no color of its own (a gray icon): a quiet neutral. */
internal const val NEUTRAL_ACCENT = 0xFF8A8F98.toInt()

/** A few recent app cards' icons, so a page of cards doesn't draw the same icon again on every open. */
private val icons = LruCache<String, ImageBitmap>(24)

/** [packageName]'s icon, about [ICON_DP] across, drawn off the main thread; null when the app is gone. */
internal suspend fun appCardIcon(context: Context, packageName: String): ImageBitmap? {
    icons.get(packageName)?.let { return it }
    val app = context.applicationContext
    return withContext(Dispatchers.IO) {
        iconBitmap(app, packageName)?.asImageBitmap()?.also { icons.put(packageName, it) }
    }
}

/** [packageName]'s icon as a bitmap about [ICON_DP] across; null when the app is gone. Call off the main thread. */
internal fun iconBitmap(context: Context, packageName: String): Bitmap? {
    val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull() ?: return null
    val px = (ICON_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    return draw(drawable, px)
}

private fun draw(drawable: Drawable, px: Int): Bitmap {
    val bitmap = createBitmap(px, px)
    val canvas = Canvas(bitmap)
    drawable.mutate().setBounds(0, 0, px, px)
    drawable.draw(canvas)
    return bitmap
}

/**
 * The color an app is known by, from its [icon]: the hue that most of the icon's saturated, not-too-dark pixels share,
 * as the average of those pixels. A gray icon gives a quiet neutral. Read from a small copy, so it costs little.
 */
internal fun iconAccent(icon: Bitmap): Int {
    val small = if (icon.width > 32 || icon.height > 32) icon.scale(32, 32) else icon
    val pixels = IntArray(small.width * small.height)
    small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
    if (small !== icon) small.recycle()
    val buckets = 24
    val weight = FloatArray(buckets)
    val r = FloatArray(buckets)
    val g = FloatArray(buckets)
    val b = FloatArray(buckets)
    val hsv = FloatArray(3)
    for (p in pixels) {
        if (Color.alpha(p) < 128) continue
        Color.colorToHSV(p, hsv)
        val s = hsv[1]
        val v = hsv[2]
        if (s < 0.35f || v < 0.25f) continue
        val i = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
        val w = s * v
        weight[i] += w
        r[i] += Color.red(p) * w
        g[i] += Color.green(p) * w
        b[i] += Color.blue(p) * w
    }
    val best = weight.indices.maxByOrNull { weight[it] } ?: return NEUTRAL_ACCENT
    val total = weight[best]
    if (total <= 0f) return NEUTRAL_ACCENT
    return Color.rgb((r[best] / total).roundToInt(), (g[best] / total).roundToInt(), (b[best] / total).roundToInt())
}

/** [packageName]'s accent ([iconAccent] of its icon); the neutral accent when the app is gone. Call off the main thread. */
internal fun appAccent(context: Context, packageName: String): Int =
    iconBitmap(context, packageName)?.let { bitmap -> iconAccent(bitmap).also { bitmap.recycle() } } ?: NEUTRAL_ACCENT

/**
 * Opens an app card's app through the safe launch path, as its launcher icon does. A widget's own buttons (its
 * PendingIntents) never run from Discover: they could open anything, with the widget's app's rights.
 */
internal fun openAppCard(context: Context, data: AppCardData) {
    val launch = runCatching { context.packageManager.getLaunchIntentForPackage(data.packageName) }.getOrNull() ?: return
    launchSafeIntent(context, launch)
}
