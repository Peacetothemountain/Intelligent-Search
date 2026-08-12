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

    fun applyAgslGlassBackdrop(view: View, monetColorInt: Int, blurRadius: Float = 24f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val shader = RuntimeShader(AGSL_GLASS_SHADER)
                shader.setFloatUniform("uResolution", view.width.toFloat().coerceAtLeast(1f), view.height.toFloat().coerceAtLeast(1f))
                shader.setFloatUniform("uBlurRadius", blurRadius)

                val a = ((monetColorInt shr 24) and 0xFF) / 255f
                val r = ((monetColorInt shr 16) and 0xFF) / 255f
                val g = ((monetColorInt shr 8) and 0xFF) / 255f
                val b = (monetColorInt and 0xFF) / 255f
                shader.setFloatUniform("uMonetTint", r, g, b, 0.2f)

                val blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                val agslEffect = RenderEffect.createRuntimeShaderEffect(shader, "compositedImage")
                val chainEffect = RenderEffect.createChainEffect(agslEffect, blurEffect)

                view.setRenderEffect(chainEffect)
            } catch (e: Exception) {
                // Fallback to standard blur if shader compile fails
                val blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                view.setRenderEffect(blurEffect)
            }
        }
    }
}
