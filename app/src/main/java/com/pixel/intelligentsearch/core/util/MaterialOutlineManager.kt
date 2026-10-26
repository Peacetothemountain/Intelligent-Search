package com.pixel.intelligentsearch.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser
import kotlin.math.sqrt

/**
 * Provides official-grade Material outline & monochrome icons for Android applications.
 * Conforms apps lacking native monochrome layers (e.g. 2048, Test Kitchen, APKMirror,
 * Arby's, Burger King, Coverage, Flow) to the system-wide Material You outline design.
 */
object MaterialOutlineManager {

    /**
     * Generates a Material outline monochrome icon for the given application. Not cached here: the only caller
     * (AppIconLoader) caches the finished icon.
     */
    fun getMaterialOutlineIcon(
        context: Context,
        packageName: String,
        appLabel: String? = null,
        fallbackDrawable: Drawable? = null
    ): Bitmap? {
        // 1. Check precision handcrafted vector registry
        getPrecisionOutline(packageName.lowercase(), appLabel?.lowercase() ?: "")?.let { return it }

        // 2. Algorithmic silhouette extraction from app drawable
        val sourceDrawable = fallbackDrawable ?: try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

        return sourceDrawable?.let { extractMonochromeSilhouette(it) }
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
            pkg.endsWith(".coverage") || label == "coverage" -> {
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
            // Exact names only: "flow" alone also matched Stack Overflow, Microsoft Flow, workflow apps...
            pkg.contains("bigduckgames") || label == "flow" || label == "flow free" -> {
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

        // No precision outline matched: release the unused bitmap immediately
        bitmap.recycle()
        return null
    }

    /**
     * Algorithmic silhouette & outline extractor for arbitrary applications.
     * Takes the foreground layer or raw icon, isolates the logo from the icon's own plate (a rounded square or circle
     * of one color, as old-style icons draw) or from transparency, and constructs an antialiased monochrome
     * silhouette mask compatible with Material You. Null when no logo can be told apart from its plate: the caller
     * then shows the full-color icon, never a blank plate.
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
            // Draw without permanently altering the caller's drawable bounds
            val savedBounds = Rect(sourceDrawable.bounds)
            sourceDrawable.setBounds(0, 0, srcSize, srcSize)
            sourceDrawable.draw(srcCanvas)
            sourceDrawable.setBounds(savedBounds)

            val pixels = IntArray(srcSize * srcSize)
            srcBitmap.getPixels(pixels, 0, srcSize, 0, 0, srcSize, srcSize)
            srcBitmap.recycle()

            val silhouette = silhouetteFromPixels(pixels, srcSize) ?: return null

            val outBitmap = Bitmap.createBitmap(srcSize, srcSize, Bitmap.Config.ARGB_8888)
            outBitmap.setPixels(silhouette, 0, srcSize, 0, 0, srcSize, srcSize)

            // Scale to inner 72dp safe zone of standard 108dp canvas
            val finalBitmap = Bitmap.createBitmap(srcSize, srcSize, Bitmap.Config.ARGB_8888)
            val finalCanvas = Canvas(finalBitmap)
            val matrix = Matrix().apply {
                postScale(0.72f, 0.72f, srcSize / 2f, srcSize / 2f)
            }
            finalCanvas.drawBitmap(outBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            outBitmap.recycle()

            finalBitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Whether an app's monochrome layer is really a full-color picture: some apps (Pizza Hut) declare their colored
     * foreground as their monochrome icon, which tinted in one color draws as a blank plate. A true monochrome layer
     * is drawn in one color, its shape in its transparency.
     */
    fun isColorPicture(drawable: Drawable): Boolean = try {
        val size = 108
        val bitmap = androidx.core.graphics.createBitmap(size, size)
        val savedBounds = Rect(drawable.bounds)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        drawable.setBounds(savedBounds)
        val pixels = IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }
        bitmap.recycle()
        val opaque = pixels.filter { Color.alpha(it) >= 128 }
        if (opaque.isEmpty()) {
            false
        } else {
            val buckets = opaque.groupBy { ((Color.red(it) shr 4) shl 8) or ((Color.green(it) shr 4) shl 4) or (Color.blue(it) shr 4) }
            val main = buckets.values.maxBy { it.size }.first()
            val far = opaque.count { p ->
                val dr = (Color.red(p) - Color.red(main)).toFloat()
                val dg = (Color.green(p) - Color.green(main)).toFloat()
                val db = (Color.blue(p) - Color.blue(main)).toFloat()
                sqrt(dr * dr + dg * dg + db * db) > 60f
            }
            far >= opaque.size * 0.08f
        }
    } catch (e: Exception) {
        false
    }

    /**
     * The white silhouette (ARGB, [size] square) of the logo in an icon's [pixels]. An icon drawn on a plate of one
     * color keeps what differs from that plate; one already isolated by transparency keeps its shape, with high
     * contrast details cut out. Null when the result would be a blank plate or nothing at all.
     */
    internal fun silhouetteFromPixels(pixels: IntArray, size: Int): IntArray? {
        val bounds = opaqueBounds(pixels, size) ?: return null
        val plate = plateOf(pixels, size, bounds)
        val fromPlate = plate?.let { logoOnPlate(pixels, size, it) }?.takeIf { isUsable(it, size) }
        if (fromPlate != null) return fromPlate
        return logoByAlpha(pixels).takeIf { isUsable(it, size) }
    }

    /** The box around the icon's opaque pixels, as (left, top, right, bottom) inclusive; null when it has none. */
    private fun opaqueBounds(pixels: IntArray, size: Int): IntArray? {
        var left = size
        var top = size
        var right = -1
        var bottom = -1
        for (y in 0 until size) for (x in 0 until size) {
            if (Color.alpha(pixels[y * size + x]) >= 128) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }

    /**
     * The plate the logo sits on, if the icon has one: sampled along a ring a tenth of the way into the icon's opaque
     * box, where a plate shows and a logo rarely reaches. Most of the ring must be opaque, and fit one flat color or a
     * gentle gradient (as Geeni's light-to-dark blue).
     */
    private fun plateOf(pixels: IntArray, size: Int, bounds: IntArray): Plate? {
        val width = bounds[2] - bounds[0] + 1
        val height = bounds[3] - bounds[1] + 1
        if (width < size * 0.4f || height < size * 0.4f) return null
        val l = bounds[0] + (width * 0.1f).toInt().coerceAtLeast(1)
        val r = bounds[2] - (width * 0.1f).toInt().coerceAtLeast(1)
        val t = bounds[1] + (height * 0.1f).toInt().coerceAtLeast(1)
        val b = bounds[3] - (height * 0.1f).toInt().coerceAtLeast(1)
        if (r <= l || b <= t) return null
        val ring = ArrayList<IntArray>()
        for (x in l..r) {
            ring += intArrayOf(x, t)
            ring += intArrayOf(x, b)
        }
        for (y in t..b) {
            ring += intArrayOf(l, y)
            ring += intArrayOf(r, y)
        }
        val opaque = ring.filter { (x, y) -> Color.alpha(pixels[y * size + x]) >= 200 }
        if (opaque.size < ring.size / 2) return null
        // Fit, then fit again without what the logo put on the ring; most of the ring must match the plate.
        val first = Plate.fit(opaque, pixels, size) ?: return null
        val onPlate = opaque.filter { (x, y) -> first.distance(pixels[y * size + x], x, y) <= 48f }
        val plate = Plate.fit(onPlate, pixels, size) ?: return null
        val matching = opaque.count { (x, y) -> plate.distance(pixels[y * size + x], x, y) <= 36f }
        return plate.takeIf { matching >= opaque.size * 0.7f }
    }

    /** A plate's color across the icon, per channel c(x, y) = a + b·x + d·y: a flat color, or a linear gradient. */
    private class Plate(private val channels: Array<FloatArray>) {
        fun distance(p: Int, x: Int, y: Int): Float {
            val dr = Color.red(p) - at(0, x, y)
            val dg = Color.green(p) - at(1, x, y)
            val db = Color.blue(p) - at(2, x, y)
            return sqrt(dr * dr + dg * dg + db * db)
        }

        private fun at(channel: Int, x: Int, y: Int) = channels[channel].let { it[0] + it[1] * x + it[2] * y }

        companion object {
            /** The least-squares plate through the colors at [points]; null when there are too few to fit. */
            fun fit(points: List<IntArray>, pixels: IntArray, size: Int): Plate? {
                if (points.size < 8) return null
                var n = 0.0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
                for ((x, y) in points) {
                    n++; sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y
                }
                val det = n * (sxx * syy - sxy * sxy) - sx * (sx * syy - sxy * sy) + sy * (sx * sxy - sxx * sy)
                val channels = Array(3) { channel ->
                    var sc = 0.0; var sxc = 0.0; var syc = 0.0
                    for ((x, y) in points) {
                        val p = pixels[y * size + x]
                        val c = when (channel) { 0 -> Color.red(p); 1 -> Color.green(p); else -> Color.blue(p) }.toDouble()
                        sc += c; sxc += x * c; syc += y * c
                    }
                    if (kotlin.math.abs(det) < 1e-6) {
                        floatArrayOf((sc / n).toFloat(), 0f, 0f)
                    } else {
                        // Cramer's rule on the normal equations.
                        val a = (sc * (sxx * syy - sxy * sxy) - sx * (sxc * syy - sxy * syc) + sy * (sxc * sxy - sxx * syc)) / det
                        val b = (n * (sxc * syy - sxy * syc) - sc * (sx * syy - sxy * sy) + sy * (sx * syc - sxc * sy)) / det
                        val d = (n * (sxx * syc - sxc * sxy) - sx * (sx * syc - sxc * sy) + sc * (sx * sxy - sxx * sy)) / det
                        floatArrayOf(a.toFloat(), b.toFloat(), d.toFloat())
                    }
                }
                return Plate(channels)
            }
        }
    }

    /** What differs from the [plate], in white, as opaque as it differs (and as the pixel itself is). */
    private fun logoOnPlate(pixels: IntArray, size: Int, plate: Plate): IntArray = IntArray(pixels.size) { i ->
        val p = pixels[i]
        val a = Color.alpha(p)
        val diff = plate.distance(p, i % size, i / size)
        if (a >= 30 && diff > 42f) {
            val alphaFactor = ((diff - 42f) / 30f).coerceIn(0f, 1f)
            Color.argb((a * alphaFactor).toInt(), 255, 255, 255)
        } else {
            Color.TRANSPARENT
        }
    }

    /** A logo already isolated by transparency: its shape, with dark details on a light body (or the reverse) cut out. */
    private fun logoByAlpha(pixels: IntArray): IntArray {
        var minLum = 255f
        var maxLum = 0f
        var avgLum = 0f
        var count = 0
        for (p in pixels) {
            if (Color.alpha(p) > 40) {
                val lum = luminance(p)
                if (lum < minLum) minLum = lum
                if (lum > maxLum) maxLum = lum
                avgLum += lum
                count++
            }
        }
        if (count > 0) avgLum /= count
        val hasHighContrastCutout = (maxLum - minLum) > 70f
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val a = Color.alpha(p)
            val isCutout = hasHighContrastCutout && if (avgLum > 128) luminance(p) < avgLum - 40 else luminance(p) > avgLum + 40
            if (a < 30 || isCutout) Color.TRANSPARENT else Color.argb(a, 255, 255, 255)
        }
    }

    private fun luminance(p: Int) = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)

    /**
     * Whether a [silhouette] shows a logo: something is there, and it is not a large shape filled nearly solid (a
     * plate whose logo was lost, which tinted would draw as a blank square or circle).
     */
    private fun isUsable(silhouette: IntArray, size: Int): Boolean {
        var ink = 0f
        var left = size
        var top = size
        var right = -1
        var bottom = -1
        for (y in 0 until size) for (x in 0 until size) {
            val a = Color.alpha(silhouette[y * size + x])
            if (a > 0) {
                ink += a / 255f
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < 0 || ink < size * size * 0.01f) return false
        val boxWidth = right - left + 1
        val boxHeight = bottom - top + 1
        val large = boxWidth > size * 0.5f && boxHeight > size * 0.5f
        // A filled square fills its box fully, a filled circle about 79% of it.
        return !(large && ink / (boxWidth * boxHeight) > 0.72f)
    }
}
