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

    private class MeshState(
        val shader: RuntimeShader,
        var lastWidth: Float = -1f,
        var lastHeight: Float = -1f,
        var lastColorA: Int = 0,
        var lastColorB: Int = 0,
        var lastColorC: Int = 0
    )

    private val viewStateMap = java.util.WeakHashMap<View, MeshState>()

    private fun setUniformColor(shader: RuntimeShader, name: String, colorInt: Int) {
        val r = ((colorInt shr 16) and 0xFF) / 255f
        val g = ((colorInt shr 8) and 0xFF) / 255f
        val b = (colorInt and 0xFF) / 255f
        shader.setFloatUniform(name, r, g, b, 1.0f)
    }

    fun applyDynamicMesh(view: View, colorA: Int, colorB: Int, colorC: Int, timeSec: Float = 0f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                var state = viewStateMap[view]
                val width = view.width.toFloat().coerceAtLeast(1f)
                val height = view.height.toFloat().coerceAtLeast(1f)

                if (state == null) {
                    val shader = RuntimeShader(AGSL_MESH_GRADIENT_SHADER)
                    state = MeshState(shader)
                    viewStateMap[view] = state

                    shader.setFloatUniform("uResolution", width, height)
                    shader.setFloatUniform("uTime", timeSec)
                    setUniformColor(shader, "uColorA", colorA)
                    setUniformColor(shader, "uColorB", colorB)
                    setUniformColor(shader, "uColorC", colorC)
                    state.lastWidth = width
                    state.lastHeight = height
                    state.lastColorA = colorA
                    state.lastColorB = colorB
                    state.lastColorC = colorC

                    val effect = RenderEffect.createRuntimeShaderEffect(shader, "compositedImage")
                    view.setRenderEffect(effect)
                    return
                }

                val shader = state.shader
                if (state.lastWidth != width || state.lastHeight != height) {
                    shader.setFloatUniform("uResolution", width, height)
                    state.lastWidth = width
                    state.lastHeight = height
                }

                shader.setFloatUniform("uTime", timeSec)

                if (state.lastColorA != colorA) {
                    setUniformColor(shader, "uColorA", colorA)
                    state.lastColorA = colorA
                }
                if (state.lastColorB != colorB) {
                    setUniformColor(shader, "uColorB", colorB)
                    state.lastColorB = colorB
                }
                if (state.lastColorC != colorC) {
                    setUniformColor(shader, "uColorC", colorC)
                    state.lastColorC = colorC
                }

                view.invalidate()
            } catch (e: Exception) {
                // Ignore compilation fallback
            }
        }
    }
}
