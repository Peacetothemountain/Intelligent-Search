package com.pixel.intelligentsearch.core.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.lang.annotations.Language

/**
 * Android 18 Progressive Blur Direction.
 */
enum class ProgressiveBlurDirection(val id: Int) {
    TopToBottom(0),
    BottomToTop(1),
    Radial(2)
}

/**
 * Modern AGSL Backdrop & Glassmorphism Shader Engine for Android 17 / 18 / API 37.
 *
 * Architecture Improvements:
 * 1. AGSL/SkSL half4/float2 register optimization for mobile GPUs.
 * 2. Color-space managed layout(color) uniform binding via RuntimeShader.setColorUniform.
 * 3. Skia RenderEffect pipeline caching: the shader and effect are cached per view / modifier and
 *    rebuilt only when a uniform input really changes (Skia snapshots uniforms when the effect is
 *    created), so steady-state frames never touch the GPU pipeline.
 * 4. First-class Jetpack Compose Material 3 Expressive Modifier extension [agslGlassBackdrop].
 * 5. Android 18 Progressive Blur Shader: Variable-kernel Poisson-Golden-Angle spiral sampling
 *    modulating blur radius along an axis or radially for scroll scrims and floating headers.
 */
object AGSLBackdropShader {

    @Language("AGSL")
    const val AGSL_GLASS_SHADER = """
        uniform shader compositedImage;
        uniform float2 uResolution;
        layout(color) uniform half4 uMonetTint;

        float hash(float2 p) {
            return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
            half4 color = compositedImage.eval(fragCoord);
            half noise = half((hash(uv) - 0.5) * 0.03);
            color.rgb += half3(noise);
            color.rgb = clamp(color.rgb, half3(0.0), half3(color.a));
            color.rgb = mix(color.rgb, uMonetTint.rgb * color.a, uMonetTint.a);
            return color;
        }
    """

    @Language("AGSL")
    const val AGSL_PROGRESSIVE_BLUR_SHADER = """
        uniform shader compositedImage;
        uniform float2 uResolution;
        uniform float uMaxBlurPx;
        uniform float uStartFraction;
        uniform float uEndFraction;
        uniform int uDirection; // 0: TopToBottom, 1: BottomToTop, 2: Radial
        layout(color) uniform half4 uMonetTint;

        float hash(float2 p) {
            return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
            float progress = 0.0;
            if (uDirection == 0) {
                // Top to bottom: top has max blur, fading to zero at bottom
                progress = 1.0 - smoothstep(uStartFraction, uEndFraction, uv.y);
            } else if (uDirection == 1) {
                // Bottom to top: bottom has max blur, fading to zero at top
                progress = smoothstep(uStartFraction, uEndFraction, uv.y);
            } else {
                // Radial from center
                float d = distance(uv, float2(0.5, 0.5)) * 2.0;
                progress = smoothstep(uStartFraction, uEndFraction, d);
            }
            
            float effectiveRadius = uMaxBlurPx * progress;
            if (effectiveRadius < 0.5) {
                half4 original = compositedImage.eval(fragCoord);
                if (uMonetTint.a > 0.0 && progress > 0.01) {
                    original.rgb = mix(original.rgb, uMonetTint.rgb * original.a, uMonetTint.a * half(progress));
                }
                return original;
            }
            
            // 12-tap Poisson-Golden-Angle spiral sampling
            half4 accum = half4(0.0);
            float totalWeight = 0.0;
            const float goldenAngle = 2.39996323;
            
            for (int i = 0; i < 12; i++) {
                float fi = float(i) + 0.5;
                float r = sqrt(fi / 12.0) * effectiveRadius;
                float theta = fi * goldenAngle;
                float2 offset = float2(cos(theta), sin(theta)) * r;
                float weight = 1.0 - (r / (effectiveRadius + 0.001));
                accum += compositedImage.eval(fragCoord + offset) * half(weight);
                totalWeight += weight;
            }
            
            half4 blurred = accum / half(totalWeight);
            half noise = half((hash(uv) - 0.5) * 0.02);
            blurred.rgb += half3(noise);
            blurred.rgb = clamp(blurred.rgb, half3(0.0), half3(blurred.a));
            
            if (uMonetTint.a > 0.0) {
                half tint = uMonetTint.a * half(progress);
                blurred.rgb = mix(blurred.rgb, uMonetTint.rgb * blurred.a, tint);
            }
            return blurred;
        }
    """

    /**
     * Factory function to compile and instantiate an [AGSL_GLASS_SHADER] [RuntimeShader].
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createShader(): RuntimeShader? {
        return runCatching { RuntimeShader(AGSL_GLASS_SHADER) }.getOrNull()
    }

    /**
     * Factory function to compile and instantiate an [AGSL_PROGRESSIVE_BLUR_SHADER] [RuntimeShader].
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createProgressiveShader(): RuntimeShader? {
        return runCatching { RuntimeShader(AGSL_PROGRESSIVE_BLUR_SHADER) }.getOrNull()
    }

    private class BackdropState(
        val shader: RuntimeShader,
        var lastWidth: Float = -1f,
        var lastHeight: Float = -1f,
        var lastBlurRadius: Float = -1f,
        var lastMonetColor: Int = 0,
        var lastTintAlpha: Float = -1f
    )

    private val viewStateMap = java.util.WeakHashMap<View, BackdropState>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setUniformTint(shader: RuntimeShader, monetColorInt: Int, tintAlpha: Float) {
        val r = (monetColorInt shr 16) and 0xFF
        val g = (monetColorInt shr 8) and 0xFF
        val b = monetColorInt and 0xFF
        val explicitAlpha = ((monetColorInt ushr 24) and 0xFF) / 255f
        val effectiveAlpha = if (explicitAlpha in 0.001f..0.999f) explicitAlpha else tintAlpha
        val alphaInt = (effectiveAlpha * 255f).toInt().coerceIn(0, 255)
        val packedArgb = (alphaInt shl 24) or (r shl 16) or (g shl 8) or b

        // Zero-heap allocation color uniform binding with Skia color space management
        shader.setColorUniform("uMonetTint", packedArgb)
    }

    /**
     * Builds the blur -> AGSL glass chain for [shader].
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    internal fun buildChainEffect(shader: RuntimeShader, blurRadius: Float): RenderEffect {
        val blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        val agslEffect = RenderEffect.createRuntimeShaderEffect(shader, "compositedImage")
        return RenderEffect.createChainEffect(agslEffect, blurEffect)
    }

    /**
     * Applies AGSL frosted glass backdrop with dynamic Monet color tinting to an Android [View].
     */
    fun applyAgslGlassBackdrop(
        view: View,
        monetColorInt: Int,
        blurRadius: Float = 24f,
        tintAlpha: Float = 0.20f
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val safeBlurRadius = blurRadius.coerceAtLeast(1f)
            try {
                val width = view.width.toFloat().coerceAtLeast(1f)
                val height = view.height.toFloat().coerceAtLeast(1f)
                val state = viewStateMap[view]
                    ?: BackdropState(RuntimeShader(AGSL_GLASS_SHADER)).also { viewStateMap[view] = it }

                val unchanged = state.lastBlurRadius == safeBlurRadius &&
                    state.lastWidth == width &&
                    state.lastHeight == height &&
                    state.lastMonetColor == monetColorInt &&
                    state.lastTintAlpha == tintAlpha
                if (unchanged) return

                state.shader.setFloatUniform("uResolution", width, height)
                setUniformTint(state.shader, monetColorInt, tintAlpha)
                view.setRenderEffect(buildChainEffect(state.shader, safeBlurRadius))

                state.lastBlurRadius = safeBlurRadius
                state.lastWidth = width
                state.lastHeight = height
                state.lastMonetColor = monetColorInt
                state.lastTintAlpha = tintAlpha
            } catch (_: Throwable) {
                val blurEffect = RenderEffect.createBlurEffect(safeBlurRadius, safeBlurRadius, Shader.TileMode.CLAMP)
                view.setRenderEffect(blurEffect)
            }
        }
    }
}

/**
 * Per-modifier cache for the Compose glass backdrop. Holds the last uniform inputs together with the
 * [androidx.compose.ui.graphics.RenderEffect] built from them, so the effect is recreated only when a
 * uniform really changed.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassEffectCache(
    private val shader: RuntimeShader,
    private val blurPx: Float
) {
    private var lastTintArgb = 0
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun effectFor(tintArgb: Int, width: Float, height: Float): androidx.compose.ui.graphics.RenderEffect {
        val cached = effect
        if (cached != null && tintArgb == lastTintArgb && width == lastWidth && height == lastHeight) {
            return cached
        }
        val rebuilt = runCatching {
            shader.setColorUniform("uMonetTint", tintArgb)
            shader.setFloatUniform("uResolution", width, height)
            AGSLBackdropShader.buildChainEffect(shader, blurPx).asComposeRenderEffect()
        }.getOrElse {
            BlurEffect(blurPx, blurPx, TileMode.Clamp)
        }
        effect = rebuilt
        lastTintArgb = tintArgb
        lastWidth = width
        lastHeight = height
        return rebuilt
    }
}

/**
 * Per-modifier cache for Android 18 AGSL progressive blur.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ProgressiveEffectCache(
    private val shader: RuntimeShader,
    private val maxBlurPx: Float,
    private val direction: ProgressiveBlurDirection,
    private val startFraction: Float,
    private val endFraction: Float
) {
    private var lastTintArgb = 0
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun effectFor(tintArgb: Int, width: Float, height: Float): androidx.compose.ui.graphics.RenderEffect {
        val cached = effect
        if (cached != null && tintArgb == lastTintArgb && width == lastWidth && height == lastHeight) {
            return cached
        }
        val rebuilt = runCatching {
            shader.setColorUniform("uMonetTint", tintArgb)
            shader.setFloatUniform("uResolution", width, height)
            shader.setFloatUniform("uMaxBlurPx", maxBlurPx)
            shader.setFloatUniform("uStartFraction", startFraction)
            shader.setFloatUniform("uEndFraction", endFraction)
            shader.setIntUniform("uDirection", direction.id)
            val renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "compositedImage")
            renderEffect.asComposeRenderEffect()
        }.getOrElse {
            BlurEffect(maxBlurPx / 2f, maxBlurPx / 2f, TileMode.Clamp)
        }
        effect = rebuilt
        lastTintArgb = tintArgb
        lastWidth = width
        lastHeight = height
        return rebuilt
    }
}

/**
 * Modern Jetpack Compose Modifier providing GPU-accelerated AGSL glassmorphism backdrop.
 */
@Composable
fun Modifier.agslGlassBackdrop(
    monetColor: Color,
    blurRadius: Dp = 24.dp,
    tintAlpha: Float = 0.20f
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.blur(blurRadius)
    }

    val animatedMonetColorState = androidx.compose.animation.animateColorAsState(
        targetValue = monetColor,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 600,
            easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        ),
        label = "agsl_glass_monet_color"
    )

    return this.agslGlassBackdrop(
        monetColorProducer = { animatedMonetColorState.value },
        blurRadius = blurRadius,
        tintAlpha = tintAlpha
    )
}

/**
 * Zero-recomposition overload of [agslGlassBackdrop] deferring state reads to the graphicsLayer draw phase.
 */
@Composable
fun Modifier.agslGlassBackdrop(
    monetColorProducer: () -> Color,
    blurRadius: Dp = 24.dp,
    tintAlpha: Float = 0.20f
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.blur(blurRadius)
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val blurPx = with(density) { blurRadius.toPx().coerceAtLeast(1f) }

    val shader = remember {
        AGSLBackdropShader.createShader()
    }

    if (shader == null) {
        return this.blur(blurRadius)
    }

    val effectCache = remember(shader, blurPx) { GlassEffectCache(shader, blurPx) }

    return this.graphicsLayer {
        val color = monetColorProducer()
        val effectiveAlpha = if (color.alpha in 0.001f..0.999f) color.alpha else tintAlpha
        val tintedColor = color.copy(alpha = effectiveAlpha)

        renderEffect = effectCache.effectFor(
            tintArgb = tintedColor.toArgb(),
            width = size.width.coerceAtLeast(1f),
            height = size.height.coerceAtLeast(1f)
        )
    }
}

/**
 * Modern Jetpack Compose Modifier providing GPU-accelerated Android 18 AGSL progressive glass backdrop.
 * Smoothly varies blur intensity from 0 to [maxBlur] across [direction], tinted with [monetColor].
 */
@Composable
fun Modifier.progressiveGlassBackdrop(
    monetColor: Color = Color.Transparent,
    maxBlur: Dp = 24.dp,
    direction: ProgressiveBlurDirection = ProgressiveBlurDirection.TopToBottom,
    startFraction: Float = 0.0f,
    endFraction: Float = 1.0f,
    tintAlpha: Float = 0.20f
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.blur(maxBlur / 2f)
    }

    val animatedMonetColorState = androidx.compose.animation.animateColorAsState(
        targetValue = monetColor,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 600,
            easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        ),
        label = "agsl_prog_glass_monet_color"
    )

    return this.progressiveGlassBackdrop(
        monetColorProducer = { animatedMonetColorState.value },
        maxBlur = maxBlur,
        direction = direction,
        startFraction = startFraction,
        endFraction = endFraction,
        tintAlpha = tintAlpha
    )
}

/**
 * Modern Jetpack Compose Modifier providing GPU-accelerated Android 18 progressive blur.
 * Modulates blur intensity along [direction] without color tinting (ideal for scroll edge scrims).
 */
@Composable
fun Modifier.progressiveBlur(
    maxBlur: Dp = 24.dp,
    direction: ProgressiveBlurDirection = ProgressiveBlurDirection.TopToBottom,
    startFraction: Float = 0.0f,
    endFraction: Float = 1.0f
): Modifier = progressiveGlassBackdrop(
    monetColor = Color.Transparent,
    maxBlur = maxBlur,
    direction = direction,
    startFraction = startFraction,
    endFraction = endFraction,
    tintAlpha = 0f
)

/**
 * Zero-recomposition overload of [progressiveGlassBackdrop] deferring state reads to the graphicsLayer draw phase.
 */
@Composable
fun Modifier.progressiveGlassBackdrop(
    monetColorProducer: () -> Color,
    maxBlur: Dp = 24.dp,
    direction: ProgressiveBlurDirection = ProgressiveBlurDirection.TopToBottom,
    startFraction: Float = 0.0f,
    endFraction: Float = 1.0f,
    tintAlpha: Float = 0.20f
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.blur(maxBlur / 2f)
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val blurPx = with(density) { maxBlur.toPx().coerceAtLeast(1f) }

    val shader = remember {
        AGSLBackdropShader.createProgressiveShader()
    }

    if (shader == null) {
        return this.blur(maxBlur / 2f)
    }

    val effectCache = remember(shader, blurPx, direction, startFraction, endFraction) {
        ProgressiveEffectCache(shader, blurPx, direction, startFraction, endFraction)
    }

    return this.graphicsLayer {
        val color = monetColorProducer()
        val effectiveAlpha = if (color.alpha in 0.001f..0.999f) color.alpha else tintAlpha
        val tintedColor = color.copy(alpha = effectiveAlpha)

        renderEffect = effectCache.effectFor(
            tintArgb = tintedColor.toArgb(),
            width = size.width.coerceAtLeast(1f),
            height = size.height.coerceAtLeast(1f)
        )
    }
}

/**
 * Per-modifier cache for [progressiveEdgeBlur]. The blur band is a fixed height in px, so the
 * normalized fractions depend on the layer size; the effect is rebuilt only when the size changes
 * (Skia snapshots uniforms at effect creation).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class EdgeBlurEffectCache(
    private val shader: RuntimeShader,
    private val maxBlurPx: Float,
    private val edgePx: Float,
    private val edge: ProgressiveBlurDirection
) {
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun effectFor(width: Float, height: Float): androidx.compose.ui.graphics.RenderEffect? {
        val cached = effect
        if (cached != null && width == lastWidth && height == lastHeight) return cached
        val frac = (edgePx / height).coerceIn(0.001f, 1f)
        val (start, end) = when (edge) {
            ProgressiveBlurDirection.BottomToTop -> (1f - frac) to 1f
            else -> 0f to frac
        }
        val rebuilt = runCatching {
            shader.setColorUniform("uMonetTint", 0)
            shader.setFloatUniform("uResolution", width, height)
            shader.setFloatUniform("uMaxBlurPx", maxBlurPx)
            shader.setFloatUniform("uStartFraction", start)
            shader.setFloatUniform("uEndFraction", end)
            shader.setIntUniform("uDirection", edge.id)
            RenderEffect.createRuntimeShaderEffect(shader, "compositedImage").asComposeRenderEffect()
        }.getOrNull()
        effect = rebuilt
        lastWidth = width
        lastHeight = height
        return rebuilt
    }
}

/**
 * Android 18-style progressive edge blur for scrolling content.
 *
 * Apply to the *container of the scrolling content* (this is a content blur, matching the semantics
 * of Compose 1.13's `Modifier.blur(BlurRadiusSpec.verticalGradient(...))`). Content passing under
 * the [edge] dissolves progressively from sharp to [maxBlur] across [edgeHeight], while the rest of
 * the content stays pixel-sharp. Below API 33 this is a no-op (content stays sharp).
 */
@Composable
fun Modifier.progressiveEdgeBlur(
    edge: ProgressiveBlurDirection = ProgressiveBlurDirection.TopToBottom,
    edgeHeight: Dp = 36.dp,
    maxBlur: Dp = 18.dp
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this

    val density = androidx.compose.ui.platform.LocalDensity.current
    val blurPx = with(density) { maxBlur.toPx().coerceAtLeast(1f) }
    val edgePx = with(density) { edgeHeight.toPx().coerceAtLeast(1f) }

    val shader = remember { AGSLBackdropShader.createProgressiveShader() } ?: return this
    val effectCache = remember(shader, blurPx, edgePx, edge) {
        EdgeBlurEffectCache(shader, blurPx, edgePx, edge)
    }

    return this.graphicsLayer {
        renderEffect = effectCache.effectFor(
            width = size.width.coerceAtLeast(1f),
            height = size.height.coerceAtLeast(1f)
        )
    }
}
