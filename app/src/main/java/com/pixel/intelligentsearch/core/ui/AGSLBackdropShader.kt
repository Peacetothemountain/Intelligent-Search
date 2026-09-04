package com.pixel.intelligentsearch.core.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

object AGSLBackdropShader {

    private const val AGSL_GLASS_SHADER = """
        uniform shader compositedImage;
        uniform vec2 uResolution;
        uniform float uBlurRadius;
        uniform vec4 uMonetTint;

        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
        }

        vec4 main(vec2 fragCoord) {
            vec2 uv = fragCoord / uResolution;
            vec4 color = compositedImage.eval(fragCoord);
            float noise = (hash(uv) - 0.5) * 0.03;
            color.rgb += vec3(noise);
            color.rgb = mix(color.rgb, uMonetTint.rgb, uMonetTint.a);
            return color;
        }
    """

    private class BackdropState(
        val shader: RuntimeShader,
        var lastWidth: Float = -1f,
        var lastHeight: Float = -1f,
        var lastBlurRadius: Float = -1f,
        var lastMonetColor: Int = 0
    )

    private val viewStateMap = java.util.WeakHashMap<View, BackdropState>()

    private fun setUniformTint(shader: RuntimeShader, monetColorInt: Int) {
        val r = ((monetColorInt shr 16) and 0xFF) / 255f
        val g = ((monetColorInt shr 8) and 0xFF) / 255f
        val b = (monetColorInt and 0xFF) / 255f
        shader.setFloatUniform("uMonetTint", r, g, b, 0.2f)
    }

    fun applyAgslGlassBackdrop(view: View, monetColorInt: Int, blurRadius: Float = 24f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                var state = viewStateMap[view]
                val width = view.width.toFloat().coerceAtLeast(1f)
                val height = view.height.toFloat().coerceAtLeast(1f)

                if (state == null || state.lastBlurRadius != blurRadius) {
                    val shader = state?.shader ?: RuntimeShader(AGSL_GLASS_SHADER)
                    state = BackdropState(shader, lastBlurRadius = blurRadius)
                    viewStateMap[view] = state

                    shader.setFloatUniform("uResolution", width, height)
                    shader.setFloatUniform("uBlurRadius", blurRadius)
                    setUniformTint(shader, monetColorInt)
                    state.lastWidth = width
                    state.lastHeight = height
                    state.lastMonetColor = monetColorInt

                    val blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                    val agslEffect = RenderEffect.createRuntimeShaderEffect(shader, "compositedImage")
                    val chainEffect = RenderEffect.createChainEffect(agslEffect, blurEffect)
                    view.setRenderEffect(chainEffect)
                    return
                }

                val shader = state.shader
                if (state.lastWidth != width || state.lastHeight != height) {
                    shader.setFloatUniform("uResolution", width, height)
                    state.lastWidth = width
                    state.lastHeight = height
                }
                if (state.lastMonetColor != monetColorInt) {
                    setUniformTint(shader, monetColorInt)
                    state.lastMonetColor = monetColorInt
                }
                view.invalidate()
            } catch (e: Exception) {
                // Fallback to standard blur if shader compile fails
                val blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                view.setRenderEffect(blurEffect)
            }
        }
    }
}
