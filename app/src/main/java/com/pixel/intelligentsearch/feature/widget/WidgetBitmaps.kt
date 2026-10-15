package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.core.data.settings.get
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.PathParser
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper

private const val TAG = "SearchWidgetProvider"

// Memory-bounded LRU cache for generated vector bitmaps with density awareness.
// Size bounded by total bitmap memory (max 4MB) to prevent OOM across rapid theme/hue cycles.
internal val bitmapCache = object : LruCache<String, Bitmap>(4 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.byteCount / 1024).coerceAtLeast(1)
    }
}

fun clearBitmapCache() {
    bitmapCache.evictAll()
}

internal val PATH_G_MAIN by lazy {
    PathParser.createPathFromPathData("M12,18.63C15.23,18.63 17.45,16.79 17.92,14.26L20,12L22.409,14.26C21.604,19.462 17.649,23 12,23C5.92,23 1,18.08 1,12C1,5.92 5.92,1 12,1C14.97,1 17.45,2.09 19.36,3.87L16.21,7.02C15.06,5.94 13.62,5.38 12,5.38C8.41,5.38 5.49,8.43 5.49,12.01C5.49,15.59 8.41,18.63 12,18.63Z")
}
internal val PATH_G_COVER by lazy {
    PathParser.createPathFromPathData("M12,14.26V10H22.36C22.49,10.72 22.56,11.47 22.56,12.25C22.56,12.943 22.509,13.614 22.409,14.26H17.92H12Z")
}

internal val PATH_MIC_1 by lazy { PathParser.createPathFromPathData("M12,15c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v7c0,1.66 1.34,3 3,3z") }
internal val PATH_MIC_2 by lazy { PathParser.createPathFromPathData("M11,18.92h2V22h-2z") }
internal val PATH_MIC_3 by lazy { PathParser.createPathFromPathData("M7,12H5c0,1.93 0.78,3.68 2.05,4.95l1.41,-1.41C7.56,14.63 7,13.38 7,12z") }
internal val PATH_MIC_4 by lazy { PathParser.createPathFromPathData("M12,17c-1.38,0 -2.63,-0.56 -3.54,-1.47l-1.41,1.41C8.32,18.21 10.07,19 12.01,19c3.87,0 6.98,-3.14 6.98,-7h-2c0,2.76 -2.23,5 -4.99,5z") }

internal val PATH_LENS_1 by lazy { PathParser.createPathFromPathData("M75.0365 83.3333C79.6388 83.3333 83.3698 79.6023 83.3698 75C83.3698 70.3976 79.6388 66.6666 75.0365 66.6666C70.4341 66.6666 66.7031 70.3976 66.7031 75C66.7031 79.6023 70.4341 83.3333 75.0365 83.3333Z") }
internal val PATH_LENS_2 by lazy { PathParser.createPathFromPathData("M50.0364 66.6666C56.9399 66.6666 62.5364 61.0702 62.5364 54.1666C62.5364 47.2631 56.9399 41.6666 50.0364 41.6666C43.1328 41.6666 37.5364 47.2631 37.5364 54.1666C37.5364 61.0702 43.1328 66.6666 50.0364 66.6666Z") }
internal val PATH_LENS_3 by lazy { PathParser.createPathFromPathData("M12.5 70.4166C12.5 79.8489 20.151 87.5 29.5833 87.5H50V79.1666L29.1146 79.1145C24.5313 79.1145 20.8333 74.8489 20.8333 69.7916V60.4166H12.5V70.4166Z") }
internal val PATH_LENS_4 by lazy { PathParser.createPathFromPathData("M87.5001 37.9167C87.5001 28.4844 79.849 20.8334 70.4167 20.8334H60.4167L70.8334 29.1667C75.4167 29.1667 79.1667 33.4844 79.1667 38.5417V54.1667H87.5001V37.9167Z") }
internal val PATH_LENS_5 by lazy { PathParser.createPathFromPathData("M58.3333 12.5H41.6667L35.4167 20.8333H29.5833C20.151 20.8333 12.5 28.4844 12.5 37.9167V47.9167H20.8333V38.5417C20.8333 33.4844 24.5833 29.1667 29.1667 29.1667H70.8333L58.3333 12.5Z") }

internal val PATH_SEARCH_EXPR_1 by lazy { PathParser.createPathFromPathData("M 10.5,4 A 6.5,6.5 0 0 0 4,10.5 A 6.5,6.5 0 0 0 10.5,17 A 6.5,6.5 0 0 0 15,15") }
internal val PATH_SEARCH_EXPR_2 by lazy { PathParser.createPathFromPathData("M 14,5 A 6.5,6.5 0 0 0 10.5,4 A 6.5,6.5 0 0 0 5,7.5") }
internal val PATH_SEARCH_EXPR_3 by lazy { PathParser.createPathFromPathData("M 15.5,15.5 L 20,20") }
internal val PATH_SEARCH_EXPR_4 by lazy { PathParser.createPathFromPathData("M 18,0.5 C 18,4 21,6.5 24,6.5 C 21,6.5 18,9 18,12.5 C 18,9 15,6.5 12,6.5 C 15,6.5 18,4 18,0.5 Z") }

internal val PATH_MUSIC_1 by lazy { PathParser.createPathFromPathData("M 19,11.5 L 19,12.5") }
internal val PATH_MUSIC_2 by lazy { PathParser.createPathFromPathData("M 14.5,9.5 L 14.5,15.5") }
internal val PATH_MUSIC_3 by lazy { PathParser.createPathFromPathData("M 10,7 L 10,16") }
internal val PATH_MUSIC_4 by lazy { PathParser.createPathFromPathData("M 11.6,16.5 C 11.6,19.26 9.36,21.5 6.6,21.5 C 3.84,21.5 1.6,19.26 1.6,16.5 C 1.6,13.74 3.84,11.5 6.6,11.5 C 8.6,11.5 10.3,12.7 11.1,14.4 Z") }

internal val PATH_GEMINI_1 by lazy { PathParser.createPathFromPathData("M11.5,9.5 L9.0,4.0 L6.5,9.5 L1.0,12.0 l5.5,2.5 L9.0,20.0 l2.5,-5.5 L17.0,12.0 l-5.5,-2.5 Z") }
internal val PATH_GEMINI_2 by lazy { PathParser.createPathFromPathData("M19.0,9.0 l1.25,-2.75 L23.0,5.0 l-2.75,-1.25 L19.0,1.0 l-1.25,2.75 L15.0,5.0 l2.75,1.25 L19.0,9.0 Z") }
internal val PATH_GEMINI_3 by lazy { PathParser.createPathFromPathData("M19.0,15.0 l-1.25,2.75 L15.0,19.0 l2.75,1.25 L19.0,23.0 l1.25,-2.75 L23.0,19.0 l-2.75,-1.25 L19.0,15.0 Z") }

internal fun createCustomMaterialGBitmap(
    pColor: Int,
    sColor: Int,
    tColor: Int,
    isPillLight: Boolean,
    context: Context
): Bitmap {
    val metrics = context.resources.displayMetrics
    val density = metrics.density
    val densityDpi = metrics.densityDpi
    val sizePx = (24 * density).toInt().coerceAtLeast(48)
    val palette = MaterialYouPaletteHelper.getFourColorMaterialGPalette(pColor, sColor, tColor, isPillLight)
    val cacheKey = "material_g_v2_${palette.rightArcAndBar}_${palette.bottomArc}_${palette.leftArc}_${palette.topArc}_${isPillLight}_${sizePx}_${densityDpi}"
    bitmapCache.get(cacheKey)?.let { return it }

    return try {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            this.density = densityDpi
        }
        val canvas = Canvas(bitmap)
        val scale = sizePx / 24f
        canvas.scale(scale, scale)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Smooth 4-quadrant Material You color sweep around the G (Right/Bar -> Bottom -> Left -> Top -> Right):
        val colors = intArrayOf(
            palette.rightArcAndBar,
            palette.bottomArc,
            palette.leftArc,
            palette.topArc,
            palette.rightArcAndBar
        )
        val positions = floatArrayOf(
            0.0f,
            0.25f,
            0.50f,
            0.75f,
            1.0f
        )
        val sweepGradient = SweepGradient(12f, 12f, colors, positions)
        paint.shader = sweepGradient
        canvas.drawPath(PATH_G_MAIN, paint)

        paint.shader = null
        paint.color = palette.rightArcAndBar
        canvas.drawPath(PATH_G_COVER, paint)

        bitmapCache.put(cacheKey, bitmap)
        bitmap
    } catch (e: Throwable) {
        Log.w(TAG, "Failed to create custom material G bitmap", e)
        val fallbackSize = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        Bitmap.createBitmap(fallbackSize, fallbackSize, Bitmap.Config.ARGB_8888).apply {
            this.density = context.resources.displayMetrics.densityDpi
        }
    }
}

internal fun createThemedShortcutBitmap(
    context: Context,
    resId: Int,
    pColor: Int,
    sColor: Int,
    tColor: Int,
    isPillLight: Boolean = false
): Bitmap? {
    val metrics = context.resources.displayMetrics
    val density = metrics.density
    val densityDpi = metrics.densityDpi
    val sizePx = (24 * density).toInt().coerceAtLeast(48)
    val palette = MaterialYouPaletteHelper.getThreeTierAccentedPalette(pColor, sColor, isPillLight)
    val cacheKey = "shortcut_${resId}_${palette.primaryDarker}_${palette.secondLighter}_${palette.thirdLighter}_${isPillLight}_${sizePx}_${densityDpi}"
    bitmapCache.get(cacheKey)?.let { return it }

    return try {
        when (resId) {
            R.drawable.ic_mic -> {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                    this.density = densityDpi
                }
                val canvas = Canvas(bitmap)
                val scale = sizePx / 24f
                canvas.scale(scale, scale)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.FILL
                }

                // Capsule is third lighter (grounded in darker accented, never white)
                paint.color = palette.thirdLighter
                canvas.drawPath(PATH_MIC_1, paint)
                // Base stem is second lighter
                paint.color = palette.secondLighter
                canvas.drawPath(PATH_MIC_2, paint)
                // Left cradle is second lighter
                paint.color = palette.secondLighter
                canvas.drawPath(PATH_MIC_3, paint)
                // Right / bottom cradle is primary darker
                paint.color = palette.primaryDarker
                canvas.drawPath(PATH_MIC_4, paint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
            R.drawable.ic_camera -> {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                    this.density = densityDpi
                }
                val canvas = Canvas(bitmap)
                val scale = sizePx / 100f
                canvas.scale(scale, scale)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.FILL
                }

                // Dot is second lighter
                paint.color = palette.secondLighter
                canvas.drawPath(PATH_LENS_1, paint)
                // Center aperture is primary darker
                paint.color = palette.primaryDarker
                canvas.drawPath(PATH_LENS_2, paint)
                // Outer camera brackets are third lighter (grounded in darker accented, never white)
                paint.color = palette.thirdLighter
                canvas.drawPath(PATH_LENS_3, paint)
                paint.color = palette.thirdLighter
                canvas.drawPath(PATH_LENS_4, paint)
                paint.color = palette.thirdLighter
                canvas.drawPath(PATH_LENS_5, paint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
            R.drawable.ic_lens_action -> {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                    this.density = densityDpi
                }
                val canvas = Canvas(bitmap)
                val scale = sizePx / 24f
                canvas.scale(scale, scale)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.FILL
                }

                // Main center star is third lighter (grounded in darker accented, never white)
                paint.color = palette.thirdLighter
                canvas.drawPath(PATH_GEMINI_1, paint)
                // Top sparkle is primary darker
                paint.color = palette.primaryDarker
                canvas.drawPath(PATH_GEMINI_2, paint)
                // Side sparkle is second lighter
                paint.color = palette.secondLighter
                canvas.drawPath(PATH_GEMINI_3, paint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
            R.drawable.ic_search_lens_expressive -> {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                    this.density = densityDpi
                }
                val canvas = Canvas(bitmap)
                val scale = sizePx / 24f
                canvas.scale(scale, scale)
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.FILL
                }

                strokePaint.strokeWidth = 2.5f
                strokePaint.color = palette.thirdLighter
                canvas.drawPath(PATH_SEARCH_EXPR_1, strokePaint)

                strokePaint.strokeWidth = 2.5f
                strokePaint.color = palette.thirdLighter
                canvas.drawPath(PATH_SEARCH_EXPR_2, strokePaint)

                strokePaint.strokeWidth = 3.0f
                strokePaint.color = palette.secondLighter
                canvas.drawPath(PATH_SEARCH_EXPR_3, strokePaint)

                fillPaint.color = palette.primaryDarker
                canvas.drawPath(PATH_SEARCH_EXPR_4, fillPaint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
            R.drawable.ic_music -> {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                    this.density = densityDpi
                }
                val canvas = Canvas(bitmap)
                val scale = sizePx / 24f
                canvas.scale(scale, scale)
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = 2.5f
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.FILL
                }

                strokePaint.color = palette.thirdLighter
                canvas.drawPath(PATH_MUSIC_1, strokePaint)

                strokePaint.color = palette.secondLighter
                canvas.drawPath(PATH_MUSIC_2, strokePaint)

                strokePaint.color = palette.primaryDarker
                canvas.drawPath(PATH_MUSIC_3, strokePaint)

                fillPaint.color = palette.thirdLighter
                canvas.drawPath(PATH_MUSIC_4, fillPaint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
            else -> null
        }
    } catch (e: Throwable) {
        Log.w(TAG, "Failed to create themed shortcut bitmap", e)
        null
    }
}

internal fun createThemedActionIconBitmap(
    context: Context,
    resId: Int,
    pColor: Int,
    sColor: Int,
    tColor: Int,
    isPillLight: Boolean = false
): Bitmap? {
    val metrics = context.resources.displayMetrics
    val density = metrics.density
    val densityDpi = metrics.densityDpi
    val sizePx = (24 * density).toInt().coerceAtLeast(48)
    val palette = MaterialYouPaletteHelper.getThreeTierAccentedPalette(pColor, sColor, isPillLight)
    val cacheKey = "action_${resId}_${palette.primaryDarker}_${palette.secondLighter}_${palette.thirdLighter}_${isPillLight}_${sizePx}_${densityDpi}"
    bitmapCache.get(cacheKey)?.let { return it }

    return try {
        when (resId) {
            R.drawable.ic_search_lens_expressive -> {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                    this.density = densityDpi
                }
                val canvas = Canvas(bitmap)
                val scale = sizePx / 24f
                canvas.scale(scale, scale)
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.FILL
                }

                // Lens rim is third lighter (grounded in darker accented, never white)
                strokePaint.strokeWidth = 2.5f
                strokePaint.color = palette.thirdLighter
                canvas.drawPath(PATH_SEARCH_EXPR_1, strokePaint)

                strokePaint.strokeWidth = 2.5f
                strokePaint.color = palette.thirdLighter
                canvas.drawPath(PATH_SEARCH_EXPR_2, strokePaint)

                // Handle is second lighter
                strokePaint.strokeWidth = 3.0f
                strokePaint.color = palette.secondLighter
                canvas.drawPath(PATH_SEARCH_EXPR_3, strokePaint)

                // Sparkle star is primary darker
                fillPaint.color = palette.primaryDarker
                canvas.drawPath(PATH_SEARCH_EXPR_4, fillPaint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
            R.drawable.ic_music -> {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                    this.density = densityDpi
                }
                val canvas = Canvas(bitmap)
                val scale = sizePx / 24f
                canvas.scale(scale, scale)
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = 3.2f
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.FILL
                }

                strokePaint.color = palette.thirdLighter
                canvas.drawPath(PATH_MUSIC_1, strokePaint)

                strokePaint.color = palette.secondLighter
                canvas.drawPath(PATH_MUSIC_2, strokePaint)

                strokePaint.color = palette.primaryDarker
                canvas.drawPath(PATH_MUSIC_3, strokePaint)

                // Note head is third lighter (grounded in darker accented, never white)
                fillPaint.color = palette.thirdLighter
                canvas.drawPath(PATH_MUSIC_4, fillPaint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
            R.drawable.ic_lens_action -> {
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                    this.density = densityDpi
                }
                val canvas = Canvas(bitmap)
                val scale = sizePx / 24f
                canvas.scale(scale, scale)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.FILL
                }

                // Main center star is third lighter (grounded in darker accented, never white)
                paint.color = palette.thirdLighter
                canvas.drawPath(PATH_GEMINI_1, paint)
                // Top sparkle is primary darker
                paint.color = palette.primaryDarker
                canvas.drawPath(PATH_GEMINI_2, paint)
                // Side sparkle is second lighter
                paint.color = palette.secondLighter
                canvas.drawPath(PATH_GEMINI_3, paint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            }
            else -> null
        }
    } catch (e: Throwable) {
        Log.w(TAG, "Failed to create themed action icon bitmap", e)
        null
    }
}
