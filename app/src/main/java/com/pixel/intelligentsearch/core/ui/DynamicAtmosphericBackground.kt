package com.pixel.intelligentsearch.core.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.intelligentsearch.core.performance.ADPFThermalManager
import kotlinx.coroutines.isActive
import org.intellij.lang.annotations.Language

// ==============================================================================
// 1. GLOBAL THEME MODELS & STRICT COLOR RESOLVER
// ==============================================================================

enum class AppDesignTheme { SYSTEM, MATERIAL }
enum class AppColorTheme { SYSTEM, LIGHT, DARK, MATERIAL, CUSTOM }

@Immutable
data class GlobalCustomColor(
    val hue: Float,       
    val saturation: Float,
    val opacity: Float    
)

@Composable
fun resolveGlobalStardustColor(
    design: AppDesignTheme,
    theme: AppColorTheme,
    customSettings: GlobalCustomColor?
): Color {
    val materialDynamicColor = MaterialTheme.colorScheme.primary 
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (theme) {
        AppColorTheme.DARK -> true
        AppColorTheme.LIGHT -> false
        else -> isSystemDark
    }
    
    return remember(design, theme, customSettings, materialDynamicColor, isDark) {
        if (theme == AppColorTheme.CUSTOM && customSettings != null) {
            Color.hsv(
                hue = customSettings.hue,
                saturation = customSettings.saturation,
                value = 1f,
                alpha = customSettings.opacity
            )
        } else if (design == AppDesignTheme.SYSTEM) {
            if (isDark) {
                Color(0xFFE8EAED).copy(alpha = 0.45f) 
            } else {
                materialDynamicColor.copy(alpha = 0.45f)
            }
        } else {
            materialDynamicColor.copy(alpha = 0.55f)
        }
    }
}

// ==============================================================================
// 2. FULL-SCREEN OPTIMIZED AGSL GPU SHADER (Android 17 / API 37)
// ==============================================================================
// AGSL full-screen atmospheric stardust particle shader scaled to viewport dimensions.

@Language("AGSL")
internal const val APP_WIDE_STARDUST_SHADER = """
    uniform float2 resolution;
    uniform float time;
    layout(color) uniform half4 targetColor;

    float hash(float2 p) {
        float3 p3 = fract(float3(p.xyx) * 0.1313);
        p3 += float3(dot(p3, p3.yzx + 3.333));
        return fract((p3.x + p3.y) * p3.z);
    }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
                   mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / max(resolution.xy, float2(1.0, 1.0));
        float y = 1.0 - uv.y; 
        
        // --- AMBIENT WAVE (Multi-octave progressive atmospheric depth) ---
        float edgeCurve = abs(uv.x - 0.5) * 1.2;
        float waveDistance = max(0.0, 1.0 - (y * 2.5) - edgeCurve);
        
        float waveFlow = noise(uv * 2.8 + float2(time * 0.35, -time * 0.18));
        float waveSecondary = noise(uv * 5.2 - float2(time * 0.22, time * 0.12));
        float breath = 0.25 + 0.45 * sin(time * 1.4);
        
        float ambientWave = smoothstep(0.0, 1.0, waveDistance) * (waveFlow * 0.7 + waveSecondary * 0.3) * breath * 0.28;

        // --- STARDUST PARTICLES ---
        float aspect = resolution.x / max(resolution.y, 1.0);
        float2 dustUv = uv * float2(aspect, 1.0) * 80.0;
        
        dustUv.y -= time * 2.5; 
        dustUv.x += sin(time * 0.5 + uv.y * 3.0) * 2.0 + noise(uv * 4.0 + time * 0.5) * 1.5;
        
        float2 gridId = floor(dustUv);
        float2 gridUv = fract(dustUv);
        
        float stardust = 0.0;
        
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float2 offset = float2(float(j), float(i));
                float2 cellId = gridId + offset;
                
                float starRandom = hash(cellId);
                
                if (starRandom > 0.7) {
                    float2 starPos = offset + float2(hash(cellId + 13.5), hash(cellId + 42.1));
                    starPos += 0.2 * float2(sin(time * 2.0 + starRandom * 10.0), cos(time * 2.0 + starRandom * 10.0));
                    
                    float distToStar = length(gridUv - starPos);
                    
                    float speck = 1.0 - smoothstep(0.02, 0.12, distToStar);
                    float twinkle = 0.3 + 0.7 * sin(time * 3.0 + starRandom * 100.0);
                    stardust += speck * twinkle;
                }
            }
        }
        
        // --- BLENDING ---
        float stardustMask = 1.0 - smoothstep(0.0, 1.0, y * 1.5); 
        
        float alphaFactor = clamp(ambientWave + (stardust * stardustMask * 0.8), 0.0, 1.0);
        half effectiveAlpha = clamp(targetColor.a * half(alphaFactor), half(0.0), half(1.0));
        
        // Skia AGSL requires premultiplied alpha output for correct Canvas SrcOver blending
        return half4(targetColor.rgb * effectiveAlpha, effectiveAlpha);
    }
"""

// ==============================================================================
// 3. ZERO-OVERHEAD RENDER ENGINE (For Full App Background)
// ==============================================================================

/**
 * Custom modifier applying the Lifecycle-Aware GPU Shader loop to the backdrop container,
 * dynamically gated by ADPF thermal headroom to prevent GPU overheating.
 *
 * Architecture Improvements:
 * 1. Android 17 / API 37 AGSL single-pass GPU offloading via [drawWithCache].
 * 2. Unconditional Composable execution ensuring Compose runtime slot table stability across thermal transitions.
 * 3. Lifecycle-aware frame clock with zero idle redraw battery consumption when interacting or backgrounded.
 * 4. Zero heap allocations per frame via primitive [@ColorInt] uniform passing.
 * 5. Periodic float modulo wrapping to prevent floating-point precision drift.
 */
@Composable
private fun Modifier.appWideStardustShader(
    colorProducer: () -> Color,
    isInteracting: Boolean = false
): Modifier {
    val context = androidx.compose.ui.platform.LocalContext.current
    val adpfThermalManager = remember(context) { ADPFThermalManager.getInstance(context) }
    val throttleLevelState = adpfThermalManager.thermalThrottleLevel.collectAsStateWithLifecycle()
    val throttleLevel by throttleLevelState

    val shouldThrottleShader = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            throttleLevel >= ADPFThermalManager.ThermalThrottleLevel.MODERATE

    val lifecycleOwner = LocalLifecycleOwner.current
    val timeState = remember { mutableFloatStateOf(0f) }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(APP_WIDE_STARDUST_SHADER) }.getOrNull()
        } else {
            null
        }
    }

    // Pause frame animation loop while user is actively typing, scrolling, throttled, or backgrounded
    LaunchedEffect(lifecycleOwner, shouldThrottleShader, isInteracting) {
        if (!isInteracting && !shouldThrottleShader && shader != null) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrame = 0L
                val period = (2000.0 * Math.PI).toFloat()
                while (isActive) {
                    withFrameNanos { frameTime ->
                        if (lastFrame == 0L) lastFrame = frameTime
                        val dt = ((frameTime - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
                        timeState.floatValue = (timeState.floatValue + dt) % period
                        lastFrame = frameTime
                    }
                }
            }
        }
    }

    val shaderBrush = remember(shader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) shader?.let { ShaderBrush(it) } else null
    }

    return this.drawWithCache {
        val currentThrottle = throttleLevelState.value
        val shouldThrottle = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                currentThrottle >= ADPFThermalManager.ThermalThrottleLevel.MODERATE
        val w = if (size.width > 0) size.width else 1f
        val h = if (size.height > 0) size.height else 1f

        if (shouldThrottle || shader == null || shaderBrush == null) {
            var lastColor = Color.Unspecified
            var cachedFallbackBrush: androidx.compose.ui.graphics.Brush? = null

            onDrawBehind {
                val currentColor = colorProducer()
                var brush = cachedFallbackBrush
                if (currentColor != lastColor || brush == null) {
                    lastColor = currentColor
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            currentColor.copy(alpha = 0.14f),
                            currentColor.copy(alpha = 0.05f),
                            androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                    cachedFallbackBrush = brush
                }
                drawRect(brush = brush)
            }
        } else {
            shader.setFloatUniform("resolution", w, h)

            onDrawBehind {
                // Zero-heap allocation color uniform binding with Skia color space management
                val currentColor = colorProducer()
                shader.setColorUniform("targetColor", currentColor.toArgb())
                shader.setFloatUniform("time", timeState.floatValue)

                drawRect(brush = shaderBrush)
            }
        }
    }
}

@Composable
private fun Modifier.appWideStardustShader(color: Color, isInteracting: Boolean = false): Modifier =
    this.appWideStardustShader(colorProducer = { color }, isInteracting = isInteracting)

// ==============================================================================
// 4. BACKGROUND CONTAINER SCAFFOLD (With M3 Expressive Transition Curves)
// ==============================================================================

@Composable
fun DynamicAtmosphericBackgroundContainer(
    appDesign: AppDesignTheme,
    appTheme: AppColorTheme,
    customColor: GlobalCustomColor?,
    modifier: Modifier = Modifier,
    isInteracting: Boolean = false,
    content: @Composable () -> Unit
) {
    val activeColor = resolveGlobalStardustColor(appDesign, appTheme, customColor)

    // Material 3 Expressive motion curve for smooth, non-popping dark/light & dynamic color transitions
    val animatedStardustColor = animateColorAsState(
        targetValue = activeColor,
        animationSpec = tween(
            durationMillis = 600,
            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        ),
        label = "stardust_color_transition"
    )

    val animatedBgColor = animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background,
        animationSpec = tween(
            durationMillis = 600,
            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        ),
        label = "bg_color_transition"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(animatedBgColor.value)
            }
            .materialYouMeshGradientBackground(
                alpha = 0.08f,
                isInteracting = isInteracting
            )
            .appWideStardustShader(
                colorProducer = { animatedStardustColor.value },
                isInteracting = isInteracting
            )
    ) {
        content()
    }
}
