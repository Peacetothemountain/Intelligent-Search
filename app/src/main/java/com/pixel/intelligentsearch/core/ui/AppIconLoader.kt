package com.pixel.intelligentsearch.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * Forgets [packageName]'s cached icon under every icon pack, so an app that was updated, reinstalled or came back
 * is drawn with its current icon. When [packageName] is an icon pack that drew cached icons, all of those (and its
 * parsed appfilter) go instead.
 */
fun evictThemedAppIcon(packageName: String) {
    val keys = themedIconCache.snapshot().keys
    if (keys.any { it.startsWith("$packageName:") }) {
        IconPackManager.clearCache()
        return
    }
    keys.filter { it.endsWith(":$packageName") }.forEach { themedIconCache.remove(it) }
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

/**
 * What an app's icon slot draws: the [themed] icon once it has loaded (an icon pack's, the app's monochrome icon or a
 * Material outline), else [fallback], the app's own icon rasterised at the size shown, while it loads or for another
 * profile's (badged) copy.
 */
@Immutable
class AppIcon(val themed: AppIconResult?, val fallback: ImageBitmap?)

/**
 * [packageName]'s icon as every result draws it: from memory at once when it is there, else read off the main thread.
 * [iconPack] is the Custom Icons choice (null: the one last read), and keys the icon, so changing packs redraws it.
 * Another profile's copy ([otherProfile]) keeps its own badged [fallback]: themed icons are looked up by package, i.e.
 * the personal app. [fallback] is rasterised at [sizePx] only while there is no themed icon.
 */
@Composable
fun rememberAppIcon(
    packageName: String,
    iconPack: String?,
    otherProfile: Boolean = false,
    fallback: Drawable? = null,
    sizePx: Int = 0
): AppIcon {
    val context = LocalContext.current
    val themed = remember(packageName, iconPack, otherProfile) {
        mutableStateOf(if (otherProfile) null else peekThemedAppIcon(packageName, iconPack))
    }
    LaunchedEffect(packageName, iconPack, otherProfile) {
        if (themed.value == null && !otherProfile) {
            themed.value = withContext(Dispatchers.IO) {
                getThemedAppIcon(context.applicationContext, packageName, activePackOverride = iconPack)
            }
        }
    }
    val loaded = themed.value
    // Rasterised only while the themed icon loads, at the size shown, and on the main thread so a cold cache never
    // leaves the slot blank for a frame.
    val fallbackBitmap = if (loaded == null && fallback != null && sizePx > 0) {
        remember(packageName, fallback, sizePx) {
            runCatching { fallback.toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
        }
    } else null
    return AppIcon(loaded, fallbackBitmap)
}

/**
 * Draws [icon] in [modifier]'s bounds: the themed icon (a monochrome one in [tint], as on the home screen), else the
 * app's own icon, else [placeholder].
 */
@Composable
fun AppIconImage(
    icon: AppIcon,
    modifier: Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    placeholder: @Composable () -> Unit = {}
) {
    val themed = icon.themed
    val fallback = icon.fallback
    when {
        themed != null -> Image(
            bitmap = themed.bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            colorFilter = if (themed.isMonochrome) ColorFilter.tint(tint) else null
        )
        fallback != null -> Image(bitmap = fallback, contentDescription = contentDescription, modifier = modifier)
        else -> placeholder()
    }
}
