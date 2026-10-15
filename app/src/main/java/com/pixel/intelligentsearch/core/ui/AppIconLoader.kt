package com.pixel.intelligentsearch.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.util.IconPackManager
import com.pixel.intelligentsearch.core.util.MaterialOutlineManager

/** Largest icon edge kept in memory; icons are shown far smaller than this. */
private const val MAX_ICON_PX = 192

/** Rasterizes an icon to at most [MAX_ICON_PX] per side, including bitmap icons from icon packs. */
private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        val bitmap = drawable.bitmap
        if (bitmap != null) {
            val largest = maxOf(bitmap.width, bitmap.height)
            if (largest <= MAX_ICON_PX) return bitmap
            val ratio = MAX_ICON_PX.toFloat() / largest
            return bitmap.scale((bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1))
        }
    }
    val width = (if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 100).coerceIn(1, MAX_ICON_PX)
    val height = (if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100).coerceIn(1, MAX_ICON_PX)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.mutate().apply { setBounds(0, 0, canvas.width, canvas.height) }.draw(canvas)
    return bitmap
}

/** An app icon ready to draw; monochrome icons are tinted to the theme. */
data class AppIconResult(val bitmap: ImageBitmap, val isMonochrome: Boolean)

private val themedIconCache = memoryLruCache<String, AppIconResult>(heapDivisor = 16) { it.bitmap.asAndroidBitmap().allocationByteCount }
@Volatile
private var cachedActivePack: String? = null

fun clearThemedIconCache() {
    cachedActivePack = null
    themedIconCache.evictAll()
}

fun peekThemedAppIcon(packageName: String, activePackOverride: String? = null): AppIconResult? {
    val activePack = activePackOverride ?: cachedActivePack ?: "system_default"
    return themedIconCache.get("$activePack:$packageName")
}

/** The themed icon for [packageName]: icon pack, then monochrome, then Material outline, then the app icon. */
fun getThemedAppIcon(context: Context, packageName: String, activePackOverride: String? = null): AppIconResult? {
    try {
        val activePack = activePackOverride ?: cachedActivePack ?: run {
            context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)[SettingsManager.ACTIVE_ICON_PACK]
                .also { cachedActivePack = it }
        }
        val cacheKey = "$activePack:$packageName"
        val cached = themedIconCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        if (activePack != "system_default") {
            val packDrawable = IconPackManager.getIconForPackage(context, activePack, packageName)
            if (packDrawable != null) {
                val res = AppIconResult(drawableToBitmap(packDrawable).asImageBitmap(), isMonochrome = false)
                themedIconCache.put(cacheKey, res)
                return res
            }
        }

        val pm = context.packageManager
        val icon = pm.getApplicationIcon(packageName)
        
        if (icon is AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val monochrome = icon.monochrome
                if (monochrome != null) {
                    val res = AppIconResult(drawableToBitmap(monochrome).asImageBitmap(), isMonochrome = true)
                    themedIconCache.put(cacheKey, res)
                    return res
                }
            }
        }

        if (activePack == "system_default") {
            val appLabel = try {
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) { null }

            val outlineBitmap = MaterialOutlineManager.getMaterialOutlineIcon(
                context, packageName, appLabel, icon
            )
            if (outlineBitmap != null) {
                val res = AppIconResult(outlineBitmap.asImageBitmap(), isMonochrome = true)
                themedIconCache.put(cacheKey, res)
                return res
            }
        }

        val res = AppIconResult(drawableToBitmap(icon).asImageBitmap(), isMonochrome = false)
        themedIconCache.put(cacheKey, res)
        return res
    } catch (e: Exception) {
        return null
    }
}

fun getAppName(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        "App"
    }
}
