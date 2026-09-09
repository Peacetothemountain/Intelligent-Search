package com.pixel.intelligentsearch.core.icons

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.Immutable
import kotlin.math.cos
import kotlin.math.sin

@Immutable
enum class AdaptiveIconShape(val displayName: String, val description: String) {
    SYSTEM_DEFAULT("System Default", "Standard Android OS default icon shape"),
    CIRCLE("Circle", "Perfect circular geometry (Pixel style)"),
    SQUIRCLE("Squircle", "Continuous curvature super-ellipse"),
    TEARDROP("Teardrop", "Asymmetric droplet contour with single rounded apex"),
    ROUNDED_HEXAGON("Rounded Hexagon", "6-sided polygon with continuous corner smoothing"),
    OCTAGON("Rounded Octagon", "8-sided gemstone facet geometry");

    companion object {
        fun fromKey(key: String): AdaptiveIconShape {
            return entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: SYSTEM_DEFAULT
        }
    }

    /**
     * Builds a hardware-accelerated clipping Path scaled to (width, height)
     */
    fun createPath(width: Float, height: Float): Path {
        val path = Path()
        val bounds = RectF(0f, 0f, width, height)

        when (this) {
            SYSTEM_DEFAULT, CIRCLE -> {
                path.addOval(bounds, Path.Direction.CW)
            }
            SQUIRCLE -> {
                // Continuous curvature squircle / super-ellipse
                val radius = width * 0.32f
                path.addRoundRect(bounds, radius, radius, Path.Direction.CW)
            }
            TEARDROP -> {
                val r = width / 2f
                path.moveTo(r, 0f)
                path.arcTo(RectF(0f, 0f, width, height), 270f, 270f, false)
                path.lineTo(width, 0f)
                path.lineTo(r, 0f)
                path.close()
            }
            ROUNDED_HEXAGON -> {
                val cx = width / 2f
                val cy = height / 2f
                val r = (width / 2f) * 0.95f
                val cornerRadius = width * 0.12f

                val points = Array(6) { i ->
                    val angle = Math.toRadians((60.0 * i - 30.0)).toFloat()
                    floatArrayOf(cx + r * cos(angle), cy + r * sin(angle))
                }
                createRoundedPolygonPath(path, points, cornerRadius)
            }
            OCTAGON -> {
                val cx = width / 2f
                val cy = height / 2f
                val r = (width / 2f) * 0.98f
                val cornerRadius = width * 0.10f

                val points = Array(8) { i ->
                    val angle = Math.toRadians((45.0 * i - 22.5)).toFloat()
                    floatArrayOf(cx + r * cos(angle), cy + r * sin(angle))
                }
                createRoundedPolygonPath(path, points, cornerRadius)
            }
        }
        return path
    }

    private fun createRoundedPolygonPath(path: Path, points: Array<FloatArray>, cornerRadius: Float) {
        val n = points.size
        for (i in 0 until n) {
            val pPrev = points[(i - 1 + n) % n]
            val pCurr = points[i]
            val pNext = points[(i + 1) % n]

            val v1x = pPrev[0] - pCurr[0]
            val v1y = pPrev[1] - pCurr[1]
            val len1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y)

            val v2x = pNext[0] - pCurr[0]
            val v2y = pNext[1] - pCurr[1]
            val len2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y)

            val startX = pCurr[0] + (v1x / len1) * cornerRadius
            val startY = pCurr[1] + (v1y / len1) * cornerRadius

            val endX = pCurr[0] + (v2x / len2) * cornerRadius
            val endY = pCurr[1] + (v2y / len2) * cornerRadius

            if (i == 0) {
                path.moveTo(startX, startY)
            } else {
                path.lineTo(startX, startY)
            }
            path.quadTo(pCurr[0], pCurr[1], endX, endY)
        }
        path.close()
    }
}
