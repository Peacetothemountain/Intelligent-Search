package com.pixel.intelligentsearch.feature.search

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.intelligentsearch.core.performance.ADPFThermalManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * Zero-Allocation, Hardware-Accelerated Matrix Particle Background.
 *
 * Guarantees zero GC heap allocations during continuous 120/144 FPS rendering by:
 * 1. Pre-rasterizing the vector sparkle shape into a cached offscreen [Bitmap] sprite.
 * 2. Pre-allocating contiguous native primitive buffers for grid coordinates and phase offsets.
 * 3. Eliminating DrawScope lambda closures (replacing translate{} closures with direct native Canvas blits).
 * 4. Dynamically throttling particle density and render frequency based on [ADPFThermalManager.ThermalThrottleLevel].
 *
 * Engineered by NG Designs.
 */
@Composable
fun AnimatedMatrixBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adpfThermalManager = remember(context) { ADPFThermalManager.getInstance(context) }
    val throttleLevel by adpfThermalManager.thermalThrottleLevel.collectAsStateWithLifecycle()

    // If device is in severe or critical thermal state, bypass rendering entirely to preserve battery & thermals
    if (throttleLevel == ADPFThermalManager.ThermalThrottleLevel.SEVERE ||
        throttleLevel == ADPFThermalManager.ThermalThrottleLevel.CRITICAL
    ) {
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "matrix_phase_transition")
    
    val background = MaterialTheme.colorScheme.background
    val isDark = (0.299f * background.red + 0.587f * background.green + 0.114f * background.blue) < 0.5f
    
    val animationDuration = when (throttleLevel) {
        ADPFThermalManager.ThermalThrottleLevel.MODERATE -> if (isDark) 12000 else 10000
        else -> if (isDark) 10000 else 8000
    }

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val density = LocalDensity.current
    val lightSpacingPx = with(density) { 24.dp.toPx() }
    val lightSparkleSizePx = with(density) { 5.5.dp.toPx() }

    Spacer(modifier = modifier.fillMaxSize().drawWithCache {
        val width = size.width
        val height = size.height

        // Dynamic spacing scaling based on thermal throttling
        val spacingMultiplier = when (throttleLevel) {
            ADPFThermalManager.ThermalThrottleLevel.MODERATE -> 1.75f // Reduce point density by ~67%
            ADPFThermalManager.ThermalThrottleLevel.LIGHT -> 1.25f
            else -> 1.0f
        }

        val spacing = (if (isDark) 55f else lightSpacingPx) * spacingMultiplier
        val sparkleSize = if (isDark) 4.5f else lightSparkleSizePx
        val baseColor = if (isDark) Color(0x60A09EB0) else Color(0xFF000000)
        val baseColorArgb = baseColor.toArgb()

        val cols = (width / spacing).toInt() + 1
        val rows = (height / spacing).toInt() + 1
        val totalPoints = (cols + 1) * (rows + 1)

        // Pre-allocate contiguous coordinate buffer: [cx, cy, offsetPhase]
        val pointData = FloatArray(totalPoints * 3)
        var ptr = 0
        for (c in 0..cols) {
            for (r in 0..rows) {
                val cx = c * spacing + (r % 2) * (spacing / 2f)
                val cy = r * spacing
                val offsetPhase = if (isDark) (cx + cy) / 200f else (cx + cy) / 150f
                pointData[ptr++] = cx
                pointData[ptr++] = cy
                pointData[ptr++] = offsetPhase
            }
        }
        val totalRecordedPoints = ptr

        // Pre-render sparkle diamond into an isolated hardware-accelerated offscreen bitmap sprite
        val spriteDimension = (sparkleSize * 2.5f).toInt().coerceAtLeast(16)
        val spriteBitmap = Bitmap.createBitmap(spriteDimension, spriteDimension, Bitmap.Config.ARGB_8888)
        val spriteCanvas = Canvas(spriteBitmap)
        val spriteHalf = spriteDimension / 2f

        val spritePath = AndroidPath().apply {
            val s = sparkleSize
            moveTo(spriteHalf, spriteHalf - s)
            quadTo(spriteHalf, spriteHalf, spriteHalf + s, spriteHalf)
            quadTo(spriteHalf, spriteHalf, spriteHalf, spriteHalf + s)
            quadTo(spriteHalf, spriteHalf, spriteHalf - s, spriteHalf)
            quadTo(spriteHalf, spriteHalf, spriteHalf, spriteHalf - s)
            close()
        }

        val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = baseColorArgb
            style = Paint.Style.FILL
        }
        spriteCanvas.drawPath(spritePath, spritePaint)

        // Reusable draw paint with bilinear bitmap filtering
        val blitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        onDrawBehind {
            val currentPhase = phase
            val baseAlpha = if (isDark) 0.8f else 0.75f
            val minAlpha = if (isDark) 0.2f else 0.45f
            val nativeCanvas = drawContext.canvas.nativeCanvas

            var i = 0
            while (i < totalRecordedPoints) {
                val cx = pointData[i++]
                val cy = pointData[i++]
                val offsetPhase = pointData[i++]

                val rawAlpha = ((sin((currentPhase + offsetPhase).toDouble()).toFloat() + 1f) * 0.5f) * baseAlpha + minAlpha
                val alpha = rawAlpha.coerceIn(0f, 1f)

                blitPaint.alpha = (alpha * 255f).toInt()
                nativeCanvas.drawBitmap(spriteBitmap, cx - spriteHalf, cy - spriteHalf, blitPaint)
            }
        }
    })
}
