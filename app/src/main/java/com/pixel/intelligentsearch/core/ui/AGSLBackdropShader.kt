package com.pixel.intelligentsearch.core.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.lang.annotations.Language

/**
 * Android 18 Progressive Blur Direction.
 */
enum class ProgressiveBlurDirection(val id: Int) {
    TopToBottom(0),
    BottomToTop(1),
    Radial(2),
    /** The same strength everywhere (a swiped-away row dissolving as a whole). */
    Uniform(3)
}

/**
 * AGSL progressive blur: a variable-radius blur (golden-angle spiral sampling) whose strength ramps along
 * an axis, used for the search results edge blur. Below API 33 the modifier is a no-op.
 */
object AGSLBackdropShader {

    @Language("AGSL")
    const val AGSL_PROGRESSIVE_BLUR_SHADER = """
        uniform shader compositedImage;
        uniform float2 uResolution;
        uniform float uMaxBlurPx;
        uniform float uStartFraction;
        uniform float uEndFraction;
        uniform int uDirection; // 0: TopToBottom, 1: BottomToTop, 2: Radial, 3: Uniform
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
            } else if (uDirection == 2) {
                // Radial from center
                float d = distance(uv, float2(0.5, 0.5)) * 2.0;
                progress = smoothstep(uStartFraction, uEndFraction, d);
            } else {
                // Uniform: the whole layer at full strength (uMaxBlurPx sets how strong)
                progress = 1.0;
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
     * Factory function to compile and instantiate an [AGSL_PROGRESSIVE_BLUR_SHADER] [RuntimeShader].
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createProgressiveShader(): RuntimeShader? {
        return runCatching { RuntimeShader(AGSL_PROGRESSIVE_BLUR_SHADER) }.getOrNull()
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
 * The scroll edge's blur and grain ([AGSL_PROGRESSIVE_BLUR_SHADER]) at one strength over a whole layer, for a row
 * that dissolves as it is swiped away. Strength changes on every frame of a drag, while Skia snapshots uniforms when
 * an effect is made, so the effect is rebuilt only when the strength moves by a quarter pixel or the size changes.
 * Below API 33, or at no strength, there is no effect (the row still fades).
 */
class DissolveEffectCache {
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var lastBlurPx = -1f
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun effectFor(width: Float, height: Float, blurPx: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || blurPx < 0.5f) return null
        val step = Math.round(blurPx * 4f) / 4f
        if (step == lastBlurPx && width == lastWidth && height == lastHeight) return effect
        effect = DissolveShader.build(width, height, step)
        lastWidth = width
        lastHeight = height
        lastBlurPx = step
        return effect
    }
}

/** One shader shared by every dissolving row: each effect copies the uniforms it was made with. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object DissolveShader {
    private val shader: RuntimeShader? by lazy { AGSLBackdropShader.createProgressiveShader() }

    fun build(width: Float, height: Float, blurPx: Float): androidx.compose.ui.graphics.RenderEffect? {
        val shader = shader ?: return null
        return runCatching {
            shader.setColorUniform("uMonetTint", 0)
            shader.setFloatUniform("uResolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
            shader.setFloatUniform("uMaxBlurPx", blurPx)
            shader.setFloatUniform("uStartFraction", 0f)
            shader.setFloatUniform("uEndFraction", 1f)
            shader.setIntUniform("uDirection", ProgressiveBlurDirection.Uniform.id)
            RenderEffect.createRuntimeShaderEffect(shader, "compositedImage").asComposeRenderEffect()
        }.getOrNull()
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
