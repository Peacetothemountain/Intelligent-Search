package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.foundation.Canvas
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import org.intellij.lang.annotations.Language
import android.graphics.RuntimeShader
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*

@Language("AGSL")
private const val GEMINI_CORNER_SWIPE_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform half4 colorPrimary;
    uniform half4 colorSecondary;
    uniform half4 colorTertiary;
    uniform half4 colorAccent;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution.xy;
        float y = 1.0 - uv.y; // 0.0 at bottom edge, 1.0 at top
        
        // Multi-frequency harmonic dispersion ribbons
        float wave1 = sin(uv.x * 6.28 + time * 2.2) * 0.14 + sin(uv.x * 12.56 - time * 1.5) * 0.04;
        float wave2 = cos(uv.x * 9.42 - time * 1.8) * 0.09 + cos(uv.x * 15.7 + time * 2.1) * 0.03;
        float wave3 = sin((uv.x - 0.5) * 5.0 + time * 1.5) * 0.12;
        
        // Corner arcs originating from bottom-left (0,0) and bottom-right (1,0)
        float dLeft = length(float2(uv.x * 1.15, y * 1.85));
        float dRight = length(float2((1.0 - uv.x) * 1.15, y * 1.85));
        
        float bottomGlow = 1.0 - smoothstep(0.0, 0.85, y - wave1 - wave2);
        float cornerLeft = 1.0 - smoothstep(0.0, 0.95, dLeft - wave3);
        float cornerRight = 1.0 - smoothstep(0.0, 0.95, dRight + wave3);
        
        float intensity = clamp(bottomGlow * 0.90 + cornerLeft * 0.85 + cornerRight * 0.85, 0.0, 1.0);
        
        // Smoothly and subtly sweep the dynamic Material You palette across the entire wave ribbon
        float flow = fract(uv.x - time * 0.20);
        
        half4 mixedColor;
        if (flow < 0.25) {
            float t = flow / 0.25;
            float smoothT = t * t * (3.0 - 2.0 * t);
            mixedColor = mix(colorPrimary, colorSecondary, smoothT);
        } else if (flow < 0.50) {
            float t = (flow - 0.25) / 0.25;
            float smoothT = t * t * (3.0 - 2.0 * t);
            mixedColor = mix(colorSecondary, colorTertiary, smoothT);
        } else if (flow < 0.75) {
            float t = (flow - 0.50) / 0.25;
            float smoothT = t * t * (3.0 - 2.0 * t);
            mixedColor = mix(colorTertiary, colorAccent, smoothT);
        } else {
            float t = (flow - 0.75) / 0.25;
            float smoothT = t * t * (3.0 - 2.0 * t);
            mixedColor = mix(colorAccent, colorPrimary, smoothT);
        }
        
        float pulse = 0.90 + 0.10 * sin(time * 2.4);
        float finalAlpha = intensity * mixedColor.a * pulse;
        
        return half4(mixedColor.rgb * finalAlpha, finalAlpha);
    }
"""

@Composable
fun GeminiCornerSwipeWaveLayer(
    colorPrimary: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    colorSecondary: androidx.compose.ui.graphics.Color = colorPrimary,
    colorTertiary: androidx.compose.ui.graphics.Color = colorPrimary,
    colorAccent: androidx.compose.ui.graphics.Color = colorPrimary,
    modifier: Modifier = Modifier
) {
    val shader = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(GEMINI_CORNER_SWIPE_SHADER)
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && shader != null) {
        val brush = remember(shader) { androidx.compose.ui.graphics.ShaderBrush(shader) }
        var time by remember { mutableFloatStateOf(0f) }
        val lifecycleOwner = LocalLifecycleOwner.current
        val animationsEnabled by com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled()
        LaunchedEffect(lifecycleOwner, animationsEnabled) {
            if (!animationsEnabled) return@LaunchedEffect
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrame = androidx.compose.runtime.withFrameNanos { it }
                while (isActive) {
                    androidx.compose.runtime.withFrameNanos { frameTime ->
                        time += (frameTime - lastFrame) / 1_000_000_000f
                        lastFrame = frameTime
                    }
                }
            }
        }
        // A decorative motion effect: with system animations off it isn't drawn rather than shown frozen.
        if (animationsEnabled) androidx.compose.foundation.Canvas(modifier = modifier) {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("colorPrimary", colorPrimary.red, colorPrimary.green, colorPrimary.blue, colorPrimary.alpha)
            shader.setFloatUniform("colorSecondary", colorSecondary.red, colorSecondary.green, colorSecondary.blue, colorSecondary.alpha)
            shader.setFloatUniform("colorTertiary", colorTertiary.red, colorTertiary.green, colorTertiary.blue, colorTertiary.alpha)
            shader.setFloatUniform("colorAccent", colorAccent.red, colorAccent.green, colorAccent.blue, colorAccent.alpha)
            drawRect(brush = brush)
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "geminiWaveFallback")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
            label = "wavePhase"
        )
        androidx.compose.foundation.Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val offset = (phase / (2f * Math.PI.toFloat())) * w
            val gradientBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(colorPrimary, colorSecondary, colorTertiary, colorAccent, colorPrimary),
                startX = offset,
                endX = offset + w,
                tileMode = androidx.compose.ui.graphics.TileMode.Repeated
            )
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h)
                for (x in 0..w.toInt() step 6) {
                    val xf = x.toFloat()
                    val normX = xf / w
                    val sinOffset = kotlin.math.sin(normX * 6.28 + phase) * (h * 0.25f)
                    val yf = (h * 0.45f) + sinOffset.toFloat()
                    lineTo(xf, yf)
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, brush = gradientBrush, alpha = 0.85f)
        }
    }
}

// Allows the animation state to persist seamlessly across all settings pages!
