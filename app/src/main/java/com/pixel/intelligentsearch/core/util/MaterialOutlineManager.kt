package com.pixel.intelligentsearch.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.PathParser
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Provides official-grade Material outline & monochrome icons for Android applications.
 * Conforms apps lacking native monochrome layers (e.g. 2048, Test Kitchen, APKMirror,
 * Arby's, Burger King, Coverage, Flow) to the system-wide Material You outline design.
 */
object MaterialOutlineManager {

    private val outlineCache = LruCache<String, Bitmap>(256)

    fun clearCache() {
        outlineCache.evictAll()
    }

    /**
     * Retrieve or generate a Material outline monochrome icon for the given application.
     */
    fun getMaterialOutlineIcon(
        context: Context,
        packageName: String,
        appLabel: String? = null,
        fallbackDrawable: Drawable? = null
    ): Bitmap? {
        val cacheKey = packageName.lowercase()
        val cached = outlineCache.get(cacheKey)
        if (cached != null) return cached

        // 1. Check precision handcrafted vector registry
        val precisionBitmap = getPrecisionOutline(cacheKey, appLabel?.lowercase() ?: "")
        if (precisionBitmap != null) {
            outlineCache.put(cacheKey, precisionBitmap)
            return precisionBitmap
        }

        // 2. Algorithmic silhouette extraction from app drawable
        val sourceDrawable = fallbackDrawable ?: try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

        if (sourceDrawable != null) {
            val extracted = extractMonochromeSilhouette(sourceDrawable)
            if (extracted != null) {
                outlineCache.put(cacheKey, extracted)
                return extracted
            }
        }

        return null
    }

    /**
     * High-fidelity Material You outline vector paths for popular and requested apps.
     */
    private fun getPrecisionOutline(pkg: String, label: String): Bitmap? {
        val size = 108
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        when {
            // 2048
            pkg.contains("2048") || pkg.contains("twozerofoureight") || label.contains("2048") -> {
                // Outer rounded tile
                canvas.drawRoundRect(RectF(22f, 22f, 86f, 86f), 16f, 16f, strokePaint)
                // Internal grid 2x2 cells
                canvas.drawRoundRect(RectF(28f, 28f, 51f, 51f), 8f, 8f, strokePaint)
                canvas.drawRoundRect(RectF(57f, 28f, 80f, 51f), 8f, 8f, strokePaint)
                canvas.drawRoundRect(RectF(28f, 57f, 51f, 80f), 8f, 8f, strokePaint)
                canvas.drawRoundRect(RectF(57f, 57f, 80f, 80f), 8f, 8f, strokePaint)
                // Center accent dots representing dice/tiles
                canvas.drawCircle(39.5f, 39.5f, 3f, fillPaint)
                canvas.drawCircle(68.5f, 68.5f, 3f, fillPaint)
                return bitmap
            }

            // Test Kitchen (Google)
            pkg.contains("tailwind") || pkg.contains("testkitchen") || label.contains("test kitchen") -> {
                // Erlenmeyer Chemistry Flask
                val flaskPath = PathParser.createPathFromPathData(
                    "M 48 24 L 60 24 M 51 24 L 51 40 L 30 76 C 27 82 32 86 38 86 L 70 86 C 76 86 81 82 78 76 L 57 40 L 57 24"
                )
                canvas.drawPath(flaskPath, strokePaint)
                // Fluid line
                val fluidPath = PathParser.createPathFromPathData("M 38 68 C 46 64 62 72 70 68")
                canvas.drawPath(fluidPath, strokePaint)
                // Sparkle at top right
                val sparklePath = PathParser.createPathFromPathData(
                    "M 74 24 L 76.5 31.5 L 84 34 L 76.5 36.5 L 74 44 L 71.5 36.5 L 64 34 L 71.5 31.5 Z"
                )
                canvas.drawPath(sparklePath, fillPaint)
                return bitmap
            }

            // APKMirror
            pkg.contains("apkmirror") || label.contains("apkmirror") -> {
                // APK Package / Download Box with M & Down Arrow
                val boxPath = PathParser.createPathFromPathData(
                    "M 30 40 L 54 26 L 78 40 L 78 72 L 54 86 L 30 72 Z"
                )
                canvas.drawPath(boxPath, strokePaint)
                // Internal 'M' ribbon & downward download arrow
                val arrowPath = PathParser.createPathFromPathData(
                    "M 54 36 L 54 64 M 44 54 L 54 64 L 64 54"
                )
                canvas.drawPath(arrowPath, strokePaint)
                return bitmap
            }

            // Arby's
            pkg.contains("arbys") || label.contains("arby's") || label.contains("arbys") -> {
                // Signature 10-Gallon Cowboy Hat Outline
                val hatPath = PathParser.createPathFromPathData(
                    "M 24 76 C 36 78 46 80 54 80 C 62 80 72 78 84 76 C 88 74 86 68 80 70 C 72 72 64 74 54 74 C 44 74 36 72 28 70 C 22 68 20 74 24 76 Z"
                )
                canvas.drawPath(hatPath, strokePaint)
                val crownPath = PathParser.createPathFromPathData(
                    "M 38 72 C 34 56 36 40 42 28 C 46 22 50 26 54 32 C 58 26 62 22 66 28 C 72 40 74 56 70 72"
                )
                canvas.drawPath(crownPath, strokePaint)
                return bitmap
            }

            // Burger King
            pkg.contains("burgerking") || label.contains("burger king") || label.contains("burgerking") -> {
                // Top Bun dome
                val topBun = PathParser.createPathFromPathData(
                    "M 26 48 C 26 30 38 24 54 24 C 70 24 82 30 82 48 Z"
                )
                canvas.drawPath(topBun, strokePaint)
                // Bottom Bun curve
                val bottomBun = PathParser.createPathFromPathData(
                    "M 28 66 C 28 78 40 84 54 84 C 68 84 80 78 80 66 Z"
                )
                canvas.drawPath(bottomBun, strokePaint)
                // "BK" stylized patty line
                val pattyLine = PathParser.createPathFromPathData(
                    "M 34 57 L 74 57"
                )
                canvas.drawPath(pattyLine, strokePaint)
                return bitmap
            }

            // Coverage
            pkg.contains("coverage") || label.contains("coverage") -> {
                // Central antenna mast
                canvas.drawLine(54f, 36f, 54f, 84f, strokePaint)
                canvas.drawCircle(54f, 32f, 4f, fillPaint)
                // Radiating concentric signal arcs left & right
                val arcsPath = PathParser.createPathFromPathData(
                    "M 44 40 C 38 48 38 60 44 68 M 34 32 C 24 44 24 64 34 76 M 64 40 C 70 48 70 60 64 68 M 74 32 C 84 44 84 64 74 76"
                )
                canvas.drawPath(arcsPath, strokePaint)
                return bitmap
            }

            // Flow / Flow Free
            pkg.contains("flow") || pkg.contains("bigduckgames") || label.contains("flow") -> {
                // Connected terminal nodes and curved fluid pipe
                val pipePath = PathParser.createPathFromPathData(
                    "M 32 36 L 64 36 C 74 36 76 46 76 56 L 76 72"
                )
                canvas.drawPath(pipePath, strokePaint)
                // Terminals
                canvas.drawCircle(32f, 36f, 7f, fillPaint)
                canvas.drawCircle(76f, 72f, 7f, fillPaint)
                // Complementary secondary path
                val secPath = PathParser.createPathFromPathData(
                    "M 36 72 L 50 72 C 58 72 58 60 50 60"
                )
                canvas.drawPath(secPath, strokePaint)
                canvas.drawCircle(36f, 72f, 5f, fillPaint)
                return bitmap
            }
        }

        return null
    }

    /**
     * Algorithmic silhouette & outline extractor for arbitrary applications.
     * Takes the foreground layer or raw icon, isolates the logo from any solid background,
     * and constructs an antialiased monochrome silhouette mask compatible with Material You.
     */
    fun extractMonochromeSilhouette(drawable: Drawable): Bitmap? {
        return try {
            val sourceDrawable = if (drawable is AdaptiveIconDrawable) {
                drawable.foreground ?: drawable
            } else {
                drawable
            }

            val srcSize = 108
            val srcBitmap = Bitmap.createBitmap(srcSize, srcSize, Bitmap.Config.ARGB_8888)
            val srcCanvas = Canvas(srcBitmap)
            sourceDrawable.setBounds(0, 0, srcSize, srcSize)
            sourceDrawable.draw(srcCanvas)

            val width = srcBitmap.width
            val height = srcBitmap.height
            val pixels = IntArray(width * height)
            srcBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // 1. Detect if image already has transparent outer boundary
            var transparentBorderCount = 0
            val borderStep = 2
            var borderTotal = 0
            for (x in 0 until width step borderStep) {
                if (Color.alpha(pixels[x]) < 30) transparentBorderCount++
                if (Color.alpha(pixels[(height - 1) * width + x]) < 30) transparentBorderCount++
                borderTotal += 2
            }
            for (y in 0 until height step borderStep) {
                if (Color.alpha(pixels[y * width]) < 30) transparentBorderCount++
                if (Color.alpha(pixels[y * width + (width - 1)]) < 30) transparentBorderCount++
                borderTotal += 2
            }

            val hasTransparentBorder = (transparentBorderCount.toFloat() / borderTotal.coerceAtLeast(1)) > 0.4f

            val outBitmap = Bitmap.createBitmap(srcSize, srcSize, Bitmap.Config.ARGB_8888)
            val outPixels = IntArray(width * height)

            if (hasTransparentBorder) {
                // Foreground logo is already isolated by alpha!
                // Calculate internal luminance variance to preserve cutouts/details
                var minLum = 255f
                var maxLum = 0f
                var avgLum = 0f
                var count = 0

                for (p in pixels) {
                    val a = Color.alpha(p)
                    if (a > 40) {
                        val lum = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
                        if (lum < minLum) minLum = lum
                        if (lum > maxLum) maxLum = lum
                        avgLum += lum
                        count++
                    }
                }

                if (count > 0) avgLum /= count
                val hasHighContrastCutout = (maxLum - minLum) > 70f

                for (i in pixels.indices) {
                    val p = pixels[i]
                    val a = Color.alpha(p)
                    if (a < 30) {
                        outPixels[i] = Color.TRANSPARENT
                    } else {
                        if (hasHighContrastCutout) {
                            val lum = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
                            // If it's a dark detail on light body or light detail on dark body, create crisp cutout
                            val isCutout = if (avgLum > 128) lum < (avgLum - 40) else lum > (avgLum + 40)
                            if (isCutout) {
                                outPixels[i] = Color.TRANSPARENT
                            } else {
                                outPixels[i] = Color.argb(a, 255, 255, 255)
                            }
                        } else {
                            outPixels[i] = Color.argb(a, 255, 255, 255)
                        }
                    }
                }
            } else {
                // Solid background: sample 4 corners to find background color
                val c1 = pixels[0]
                val c2 = pixels[width - 1]
                val c3 = pixels[(height - 1) * width]
                val c4 = pixels[width * height - 1]

                val bgR = (Color.red(c1) + Color.red(c2) + Color.red(c3) + Color.red(c4)) / 4
                val bgG = (Color.green(c1) + Color.green(c2) + Color.green(c3) + Color.green(c4)) / 4
                val bgB = (Color.blue(c1) + Color.blue(c2) + Color.blue(c3) + Color.blue(c4)) / 4

                for (i in pixels.indices) {
                    val p = pixels[i]
                    val r = Color.red(p)
                    val g = Color.green(p)
                    val b = Color.blue(p)

                    val dr = (r - bgR).toFloat()
                    val dg = (g - bgG).toFloat()
                    val db = (b - bgB).toFloat()
                    val diff = sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()

                    if (diff > 42f) {
                        val alphaFactor = ((diff - 42f) / 30f).coerceIn(0f, 1f)
                        val a = (255 * alphaFactor).toInt()
                        outPixels[i] = Color.argb(a, 255, 255, 255)
                    } else {
                        outPixels[i] = Color.TRANSPARENT
                    }
                }
            }

            outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)

            // Scale to inner 72dp safe zone of standard 108dp canvas
            val finalBitmap = Bitmap.createBitmap(srcSize, srcSize, Bitmap.Config.ARGB_8888)
            val finalCanvas = Canvas(finalBitmap)
            val matrix = Matrix().apply {
                postScale(0.72f, 0.72f, srcSize / 2f, srcSize / 2f)
            }
            finalCanvas.drawBitmap(outBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))

            finalBitmap
        } catch (e: Exception) {
            null
        }
    }
}
