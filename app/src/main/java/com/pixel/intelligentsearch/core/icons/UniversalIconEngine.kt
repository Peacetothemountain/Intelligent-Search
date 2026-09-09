package com.pixel.intelligentsearch.core.icons

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

data class IconPackThemeConfig(
    val iconBacks: List<String> = emptyList(),
    val iconMask: String? = null,
    val iconUpon: String? = null,
    val scaleFactor: Float = 0.72f
)

@Singleton
class UniversalIconEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val ICON_SIZE_PX = 192
        private val BITMAP_CACHE = LruCache<String, Bitmap>(512)
        private val APP_FILTER_CACHE = ConcurrentHashMap<String, Map<String, String>>()
        private val THEME_CONFIG_CACHE = ConcurrentHashMap<String, IconPackThemeConfig>()
        private val SHAPE_PATH_CACHE = ConcurrentHashMap<String, Path>()
    }

    fun getIcon(
        targetPackage: String,
        activeIconPack: String = "system_default",
        shape: AdaptiveIconShape = AdaptiveIconShape.SYSTEM_DEFAULT,
        dynamicMasking: Boolean = true
    ): Bitmap? {
        val cacheKey = "$activeIconPack:$targetPackage:${shape.name}:$dynamicMasking"
        val cached = BITMAP_CACHE.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val pm = context.packageManager
        var resolvedBitmap: Bitmap? = null

        // 1. Try resolving dedicated asset from active third-party icon pack
        if (activeIconPack != "system_default" && activeIconPack.isNotBlank()) {
            resolvedBitmap = loadDedicatedIconPackBitmap(pm, activeIconPack, targetPackage)
        }

        // 2. If no direct icon pack asset, load system/original application icon
        if (resolvedBitmap == null) {
            val originalDrawable = try {
                pm.getApplicationIcon(targetPackage)
            } catch (_: Exception) {
                null
            }

            if (originalDrawable != null) {
                if (activeIconPack != "system_default" && dynamicMasking) {
                    // Compose icon pack dynamic mask (iconback, mask, iconupon, scale)
                    resolvedBitmap = applyIconPackDynamicMask(pm, activeIconPack, originalDrawable, targetPackage)
                } else if (shape != AdaptiveIconShape.SYSTEM_DEFAULT) {
                    // Apply our native Adaptive Icon Shape
                    resolvedBitmap = applyNativeAdaptiveShape(originalDrawable, shape)
                } else {
                    // Default rendering
                    resolvedBitmap = drawableToBitmap(originalDrawable, ICON_SIZE_PX, ICON_SIZE_PX)
                }
            }
        }

        if (resolvedBitmap != null) {
            BITMAP_CACHE.put(cacheKey, resolvedBitmap)
        }
        return resolvedBitmap
    }

    private fun loadDedicatedIconPackBitmap(pm: PackageManager, iconPackPackage: String, targetPackage: String): Bitmap? {
        try {
            val appFilter = APP_FILTER_CACHE.computeIfAbsent(iconPackPackage) {
                parseAppFilter(iconPackPackage)
            }

            val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
            val mainActivity = launchIntent?.component?.className

            var drawableName: String? = null
            if (mainActivity != null) {
                val compKey = "componentinfo{$targetPackage/$mainActivity}".lowercase()
                drawableName = appFilter[compKey]
            }
            if (drawableName.isNullOrEmpty()) {
                drawableName = appFilter[targetPackage.lowercase()]
            }
            if (drawableName.isNullOrEmpty()) {
                val sanitized = targetPackage.replace(".", "_").lowercase()
                drawableName = appFilter[sanitized]
            }

            if (!drawableName.isNullOrEmpty()) {
                val iconPackRes = pm.getResourcesForApplication(iconPackPackage)
                val resId = iconPackRes.getIdentifier(drawableName, "drawable", iconPackPackage)
                if (resId != 0) {
                    val d = iconPackRes.getDrawable(resId, null)
                    if (d != null) {
                        return drawableToBitmap(d, ICON_SIZE_PX, ICON_SIZE_PX)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun applyIconPackDynamicMask(
        pm: PackageManager,
        iconPackPackage: String,
        originalDrawable: Drawable,
        targetPackage: String
    ): Bitmap {
        val config = THEME_CONFIG_CACHE.computeIfAbsent(iconPackPackage) {
            parseIconPackThemeConfig(iconPackPackage)
        }

        val baseBitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(baseBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            val iconPackRes = pm.getResourcesForApplication(iconPackPackage)

            // Step A: Draw iconback if available
            if (config.iconBacks.isNotEmpty()) {
                val hash = kotlin.math.abs(targetPackage.hashCode())
                val backDrawableName = config.iconBacks[hash % config.iconBacks.size]
                val backResId = iconPackRes.getIdentifier(backDrawableName, "drawable", iconPackPackage)
                if (backResId != 0) {
                    val backDrawable = iconPackRes.getDrawable(backResId, null)
                    backDrawable?.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
                    backDrawable?.draw(canvas)
                }
            }

            // Step B: Render scaled original icon into offscreen buffer
            val scale = config.scaleFactor.coerceIn(0.5f, 1.0f)
            val scaledSize = (ICON_SIZE_PX * scale).toInt()
            val offset = (ICON_SIZE_PX - scaledSize) / 2

            val appIconBuffer = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
            val appCanvas = Canvas(appIconBuffer)
            val originalBmp = drawableToBitmap(originalDrawable, scaledSize, scaledSize)
            appCanvas.drawBitmap(originalBmp, offset.toFloat(), offset.toFloat(), paint)

            // Step C: Mask offscreen buffer with iconmask
            if (!config.iconMask.isNullOrEmpty()) {
                val maskResId = iconPackRes.getIdentifier(config.iconMask, "drawable", iconPackPackage)
                if (maskResId != 0) {
                    val maskDrawable = iconPackRes.getDrawable(maskResId, null)
                    val maskBmp = drawableToBitmap(maskDrawable ?: originalDrawable, ICON_SIZE_PX, ICON_SIZE_PX)
                    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    }
                    appCanvas.drawBitmap(maskBmp, 0f, 0f, maskPaint)
                }
            }

            // Step D: Composite masked icon onto canvas
            canvas.drawBitmap(appIconBuffer, 0f, 0f, paint)

            // Step E: Draw iconupon (glass/gloss layer)
            if (!config.iconUpon.isNullOrEmpty()) {
                val uponResId = iconPackRes.getIdentifier(config.iconUpon, "drawable", iconPackPackage)
                if (uponResId != 0) {
                    val uponDrawable = iconPackRes.getDrawable(uponResId, null)
                    uponDrawable?.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
                    uponDrawable?.draw(canvas)
                }
            }
        } catch (_: Exception) {
            // Fallback: draw standard scaled bitmap
            val fallbackBmp = drawableToBitmap(originalDrawable, ICON_SIZE_PX, ICON_SIZE_PX)
            return fallbackBmp
        }

        return baseBitmap
    }

    private fun applyNativeAdaptiveShape(drawable: Drawable, shape: AdaptiveIconShape): Bitmap {
        val output = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val pathKey = "${shape.name}:$ICON_SIZE_PX"
        val clipPath = SHAPE_PATH_CACHE.computeIfAbsent(pathKey) {
            shape.createPath(ICON_SIZE_PX.toFloat(), ICON_SIZE_PX.toFloat())
        }

        canvas.save()
        canvas.clipPath(clipPath)
        val srcBitmap = drawableToBitmap(drawable, ICON_SIZE_PX, ICON_SIZE_PX)
        canvas.drawBitmap(srcBitmap, 0f, 0f, paint)
        canvas.restore()

        return output
    }

    private fun parseIconPackThemeConfig(iconPackPackage: String): IconPackThemeConfig {
        var backs = mutableListOf<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 0.72f

        try {
            val pm = context.packageManager
            val iconPackRes = pm.getResourcesForApplication(iconPackPackage)
            val resId = iconPackRes.getIdentifier("appfilter", "xml", iconPackPackage)
            if (resId != 0) {
                val xpp = iconPackRes.getXml(resId)
                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val name = xpp.name.lowercase()
                        if (name.startsWith("iconback")) {
                            for (i in 0 until xpp.attributeCount) {
                                if (xpp.getAttributeName(i).lowercase() == "img") {
                                    backs.add(xpp.getAttributeValue(i))
                                }
                            }
                        } else if (name == "iconmask") {
                            for (i in 0 until xpp.attributeCount) {
                                if (xpp.getAttributeName(i).lowercase() == "img") {
                                    mask = xpp.getAttributeValue(i)
                                }
                            }
                        } else if (name == "iconupon") {
                            for (i in 0 until xpp.attributeCount) {
                                if (xpp.getAttributeName(i).lowercase() == "img") {
                                    upon = xpp.getAttributeValue(i)
                                }
                            }
                        } else if (name == "scale") {
                            for (i in 0 until xpp.attributeCount) {
                                if (xpp.getAttributeName(i).lowercase() == "factor") {
                                    scale = xpp.getAttributeValue(i).toFloatOrNull() ?: 0.72f
                                }
                            }
                        }
                    }
                    eventType = xpp.next()
                }
            }
        } catch (_: Exception) {}

        return IconPackThemeConfig(backs, mask, upon, scale)
    }

    private fun parseAppFilter(iconPackPackage: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val pm = context.packageManager
            val iconPackRes = pm.getResourcesForApplication(iconPackPackage)
            val resId = iconPackRes.getIdentifier("appfilter", "xml", iconPackPackage)
            if (resId != 0) {
                val xpp = iconPackRes.getXml(resId)
                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && xpp.name == "item") {
                        var comp: String? = null
                        var drawable: String? = null
                        var pkg: String? = null
                        for (i in 0 until xpp.attributeCount) {
                            when (xpp.getAttributeName(i).lowercase()) {
                                "component" -> comp = xpp.getAttributeValue(i)
                                "drawable" -> drawable = xpp.getAttributeValue(i)
                                "package" -> pkg = xpp.getAttributeValue(i)
                            }
                        }
                        if (!drawable.isNullOrEmpty()) {
                            if (!comp.isNullOrEmpty()) {
                                val lowerComp = comp.lowercase()
                                map[lowerComp] = drawable
                                if (lowerComp.contains("{") && lowerComp.contains("/")) {
                                    val extractedPkg = lowerComp.substringAfter("{").substringBefore("/")
                                    map[extractedPkg] = drawable
                                }
                            }
                            if (!pkg.isNullOrEmpty()) {
                                map[pkg.lowercase()] = drawable
                            }
                        }
                    }
                    eventType = xpp.next()
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private fun drawableToBitmap(d: Drawable, width: Int, height: Int): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null && !d.bitmap.isRecycled) {
            if (d.bitmap.width == width && d.bitmap.height == height) {
                return d.bitmap
            }
        }
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, canvas.width, canvas.height)
        d.draw(canvas)
        return bmp
    }

    fun clearCache() {
        BITMAP_CACHE.evictAll()
        APP_FILTER_CACHE.clear()
        THEME_CONFIG_CACHE.clear()
        SHAPE_PATH_CACHE.clear()
    }
}
