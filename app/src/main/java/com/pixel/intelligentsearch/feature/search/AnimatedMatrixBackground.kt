package com.pixel.intelligentsearch.feature.search

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.core.performance.ADPFThermalManager
import kotlinx.coroutines.isActive
import org.intellij.lang.annotations.Language
import kotlin.math.PI
import kotlin.math.sin

/**
 * AGSL GPU fragment shader generating the 4-point astroid sparkle matrix grid.
 *
 * Replaces 1,000+ CPU JNI drawBitmap calls per frame with a single GPU rasterization pass.
 */
@Language("AGSL")
const val AGSL_MATRIX_SPARKLE_SHADER = """
    uniform float2 uResolution;
    uniform float uPhase;
    uniform float uSpacing;
    uniform float uSparkleSize;
    uniform float uBaseAlpha;
    uniform float uMinAlpha;
    uniform float uOffsetDivisor;
    layout(color) uniform half4 uBaseColor;

    half4 main(float2 fragCoord) {
        // Staggered hexagonal grid with each 4-point Gemini star centered inside its cell
        // so cell boundaries never clip the top, left, or bottom star arms.
        float row = floor(fragCoord.y / uSpacing);
        float rowOffset = mod(row, 2.0) * (uSpacing * 0.5);
        float col = floor((fragCoord.x - rowOffset) / uSpacing);
        
        float2 center = float2((col + 0.5) * uSpacing + rowOffset, (row + 0.5) * uSpacing);
        float2 d = abs(fragCoord - center);
        
        // Early bounding-box discard skips >85% of pixels before any sqrt/sin math
        if (d.x > uSparkleSize * 1.08 || d.y > uSparkleSize * 1.08) {
            return half4(0.0);
        }
        
        // Traveling diagonal wave + subtle per-star phase jitter for organic Gemini twinkle
        float cellJitter = fract(sin(row * 12.9898 + col * 78.233) * 43758.5453) * 1.85;
        float offsetPhase = (center.x + center.y) / uOffsetDivisor + cellJitter;
        float wave = (sin(uPhase + offsetPhase) + 1.0) * 0.5;
        
        // Subtle breathing of the 4 star tips during twinkle
        float effectiveSize = max(uSparkleSize * (0.85 + 0.18 * wave), 0.001);
        
        // Astroid / 4-point Gemini sparkle equation matching concave quadratic bezier arms
        float dist = (sqrt(max(d.x, 0.0)) + sqrt(max(d.y, 0.0))) / sqrt(effectiveSize);
        float shape = 1.0 - smoothstep(0.76, 1.01, dist);
        
        if (shape <= 0.0) {
            return half4(0.0);
        }
        
        float rawAlpha = wave * uBaseAlpha + uMinAlpha;
        half finalAlpha = uBaseColor.a * half(clamp(rawAlpha, 0.0, 1.0) * shape);
        
        // Skia AGSL requires premultiplied alpha output
        return half4(uBaseColor.rgb * finalAlpha, finalAlpha);
    }
"""

/**
 * Modern GPU-Accelerated Gemini Star Matrix Background for Android 17 / API 37.
 */
@Composable
fun AnimatedMatrixBackground(modifier: Modifier = Modifier, isPaused: Boolean = false) {
    val context = LocalContext.current
    val adpfThermalManager = remember(context) { ADPFThermalManager.getInstance(context) }
    val throttleLevelState = adpfThermalManager.thermalThrottleLevel.collectAsStateWithLifecycle()
    val throttleLevel by throttleLevelState

    val isSevereThrottled = throttleLevel == ADPFThermalManager.ThermalThrottleLevel.SEVERE ||
            throttleLevel == ADPFThermalManager.ThermalThrottleLevel.CRITICAL
    val animationsEnabled by com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled()
    val shouldAnimate = !isPaused && !isSevereThrottled && animationsEnabled

    val background = MaterialTheme.colorScheme.background
    val isDark = background.luminance() < 0.5f

    val animationDurationSec = when (throttleLevel) {
        ADPFThermalManager.ThermalThrottleLevel.MODERATE -> if (isDark) 10f else 8.8f
        else -> if (isDark) 8.2f else 7.2f
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val phaseState = remember { mutableFloatStateOf(0f) }

    // Battery-efficient 30 FPS phase pacing (~33.3ms interval): cuts GPU frame invalidations by 75%
    // on 120Hz displays while keeping the gentle Gemini star twinkle completely smooth.
    LaunchedEffect(lifecycleOwner, shouldAnimate, animationDurationSec) {
        if (shouldAnimate) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastUpdateNanos = 0L
                val minFrameIntervalNanos = 33_333_333L // ~30 FPS cap for ambient background twinkle
                val twoPi = (2.0f * PI.toFloat())
                while (isActive) {
                    // Sleep between updates instead of waking every vsync just to skip it; the phase uses
                    // the measured time step, so the motion is unchanged.
                    if (lastUpdateNanos != 0L) kotlinx.coroutines.delay(30)
                    withFrameNanos { frameTime ->
                        if (lastUpdateNanos == 0L) {
                            lastUpdateNanos = frameTime
                        } else if (frameTime - lastUpdateNanos >= minFrameIntervalNanos) {
                            val dt = ((frameTime - lastUpdateNanos) / 1_000_000_000f).coerceIn(0f, 0.25f)
                            val phaseDelta = (dt / animationDurationSec) * twoPi
                            phaseState.floatValue = (phaseState.floatValue + phaseDelta) % twoPi
                            lastUpdateNanos = frameTime
                        }
                    }
                }
            }
        }
    }

    // Smooth Material 3 Expressive color transitions
    val targetBaseColor = if (isDark) Color(0xFFA6A3B6) else Color(0xFF1E1E24)
    val animatedBaseColor by animateColorAsState(
        targetValue = targetBaseColor,
        animationSpec = tween(
            durationMillis = 600,
            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        ),
        label = "matrix_base_color"
    )

    val density = LocalDensity.current
    val darkSpacingPx = with(density) { 25.dp.toPx() }
    val lightSpacingPx = with(density) { 24.dp.toPx() }
    val darkSparkleSizePx = with(density) { 3.4.dp.toPx() }
    val lightSparkleSizePx = with(density) { 3.7.dp.toPx() }
    val darkOffsetDivisorPx = with(density) { 95.dp.toPx() }
    val lightOffsetDivisorPx = with(density) { 80.dp.toPx() }

    // AGSL RuntimeShader available on Android 13+ (API 33 to API 37)
    val runtimeShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(AGSL_MATRIX_SPARKLE_SHADER) }.getOrNull()
        } else {
            null
        }
    }

    val shaderBrush = remember(runtimeShader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runtimeShader?.let { ShaderBrush(it) } else null
    }

    Spacer(modifier = modifier.fillMaxSize().drawWithCache {
        val width = size.width
        val height = size.height
        val currentThrottle = throttleLevelState.value
        val currentBg = background
        val currentIsDark = currentBg.luminance() < 0.5f

        // Dynamic spacing scaling based on thermal throttling
        val spacingMultiplier = when (currentThrottle) {
            ADPFThermalManager.ThermalThrottleLevel.MODERATE -> 1.5f
            ADPFThermalManager.ThermalThrottleLevel.LIGHT -> 1.2f
            else -> 1.0f
        }

        val spacing = (if (currentIsDark) darkSpacingPx else lightSpacingPx) * spacingMultiplier
        val sparkleSize = if (currentIsDark) darkSparkleSizePx else lightSparkleSizePx
        val baseAlpha = if (currentIsDark) 0.42f else 0.45f
        val minAlpha = if (currentIsDark) 0.11f else 0.14f
        val offsetDivisor = if (currentIsDark) darkOffsetDivisorPx else lightOffsetDivisorPx

        // ----------------------------------------------------------------------------------
        // PATH A: Zero-CPU GPU AGSL RuntimeShader offloading (Android 17 / API 33+)
        // ----------------------------------------------------------------------------------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null && shaderBrush != null) {
            runtimeShader.setFloatUniform("uResolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
            runtimeShader.setFloatUniform("uSpacing", spacing)
            runtimeShader.setFloatUniform("uSparkleSize", sparkleSize)
            runtimeShader.setFloatUniform("uBaseAlpha", baseAlpha)
            runtimeShader.setFloatUniform("uMinAlpha", minAlpha)
            runtimeShader.setFloatUniform("uOffsetDivisor", offsetDivisor)

            onDrawBehind {
                if (isPaused) {
                    return@onDrawBehind
                }

                // Zero heap allocation color uniform binding
                runtimeShader.setColorUniform("uBaseColor", animatedBaseColor.toArgb())
                runtimeShader.setFloatUniform("uPhase", phaseState.floatValue)
                drawRect(brush = shaderBrush)
            }
        } else {
            // ----------------------------------------------------------------------------------
            // PATH B: Hardware-Accelerated Canvas Sprite Blitting Fallback (API 31-32)
            // ----------------------------------------------------------------------------------
            val cols = (width / spacing).toInt() + 1
            val rows = (height / spacing).toInt() + 1
            val totalPoints = (cols + 1) * (rows + 1)

            val pointData = FloatArray(totalPoints * 3)
            var ptr = 0
            for (c in 0..cols) {
                for (r in 0..rows) {
                    val cx = (c + 0.5f) * spacing + (r % 2) * (spacing / 2f)
                    val cy = (r + 0.5f) * spacing
                    val rawSin = sin(r * 12.9898f + c * 78.233f) * 43758.5453f
                    val cellJitter = (rawSin - kotlin.math.floor(rawSin)) * 1.85f
                    val offsetPhase = (cx + cy) / offsetDivisor + cellJitter
                    pointData[ptr++] = cx
                    pointData[ptr++] = cy
                    pointData[ptr++] = offsetPhase
                }
            }
            val totalRecordedPoints = ptr

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
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            spriteCanvas.drawPath(spritePath, spritePaint)

            val blitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            var lastBlitColor = 0
            var cachedColorFilter: android.graphics.PorterDuffColorFilter? = null

            onDrawBehind {
                if (isPaused) {
                    return@onDrawBehind
                }

                val currentColorInt = animatedBaseColor.toArgb()
                if (cachedColorFilter == null || currentColorInt != lastBlitColor) {
                    lastBlitColor = currentColorInt
                    cachedColorFilter = android.graphics.PorterDuffColorFilter(
                        currentColorInt,
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                    blitPaint.colorFilter = cachedColorFilter
                }

                val currentPhase = phaseState.floatValue
                val nativeCanvas = drawContext.canvas.nativeCanvas

                var i = 0
                while (i < totalRecordedPoints) {
                    val cx = pointData[i++]
                    val cy = pointData[i++]
                    val offsetPhase = pointData[i++]

                    val rawAlpha = ((sin(currentPhase + offsetPhase) + 1f) * 0.5f) * baseAlpha + minAlpha
                    val alpha = rawAlpha.coerceIn(0f, 1f)

                    blitPaint.alpha = (alpha * 255f).toInt()
                    nativeCanvas.drawBitmap(spriteBitmap, cx - spriteHalf, cy - spriteHalf, blitPaint)
                }
            }
        }
    })
}
