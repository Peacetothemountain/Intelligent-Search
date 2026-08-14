package com.pixel.intelligentsearch.core.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View

object DynamicMeshShader {

    private const val AGSL_MESH_GRADIENT_SHADER = """
        uniform shader compositedImage;
        uniform vec2 uResolution;
        uniform float uTime;
        uniform vec4 uColorA;
        uniform vec4 uColorB;
        uniform vec4 uColorC;

        vec4 main(vec2 fragCoord) {
            vec2 st = fragCoord / uResolution;
            float wave1 = sin(st.x * 3.0 + uTime) * 0.5 + 0.5;
            float wave2 = cos(st.y * 3.0 + uTime * 0.8) * 0.5 + 0.5;
            
            vec4 color = mix(uColorA, uColorB, wave1);
            color = mix(color, uColorC, wave2);
            color.a = 0.15;
            
            vec4 original = compositedImage.eval(fragCoord);
            return mix(original, color, color.a);
        }
    """

    fun applyDynamicMesh(view: View, colorA: Int, colorB: Int, colorC: Int, timeSec: Float = 0f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val shader = RuntimeShader(AGSL_MESH_GRADIENT_SHADER)
                shader.setFloatUniform("uResolution", view.width.toFloat().coerceAtLeast(1f), view.height.toFloat().coerceAtLeast(1f))
                shader.setFloatUniform("uTime", timeSec)

                fun setUniformColor(name: String, colorInt: Int) {
                    val r = ((colorInt shr 16) and 0xFF) / 255f
                    val g = ((colorInt shr 8) and 0xFF) / 255f
                    val b = (colorInt and 0xFF) / 255f
                    shader.setFloatUniform(name, r, g, b, 1.0f)
                }

                setUniformColor("uColorA", colorA)
                setUniformColor("uColorB", colorB)
                setUniformColor("uColorC", colorC)

                val effect = RenderEffect.createRuntimeShaderEffect(shader, "compositedImage")
                view.setRenderEffect(effect)
            } catch (e: Exception) {
                // Ignore compilation fallback
            }
        }
    }
}
