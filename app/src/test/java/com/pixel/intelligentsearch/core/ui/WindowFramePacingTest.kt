package com.pixel.intelligentsearch.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowFramePacingTest {

    @Test
    fun testFrameRateCategoryConstants() {
        // Must match View.REQUESTED_FRAME_RATE_CATEGORY_* on API 35+.
        assertEquals(4, WindowFramePacing.FRAME_RATE_CATEGORY_HIGH)
        assertEquals(2, WindowFramePacing.FRAME_RATE_CATEGORY_NORMAL)
        assertEquals(1, WindowFramePacing.FRAME_RATE_CATEGORY_LOW)
    }

    @Test
    fun progressiveBlurShaderDeclaresEveryUniformTheEdgeBlurSets() {
        // RuntimeShader.setXUniform throws for a name the shader doesn't declare, and the edge blur swallows
        // that, silently showing no blur; keep the shader and the setter in step.
        val shader = AGSLBackdropShader.AGSL_PROGRESSIVE_BLUR_SHADER
        assertTrue(shader.contains("layout(color) uniform half4 uMonetTint;"))
        for (uniform in listOf("float2 uResolution", "float uMaxBlurPx", "float uStartFraction", "float uEndFraction", "int uDirection")) {
            assertTrue(uniform, shader.contains("uniform $uniform;"))
        }
        assertTrue(shader.contains("uniform shader compositedImage;"))
    }
}
