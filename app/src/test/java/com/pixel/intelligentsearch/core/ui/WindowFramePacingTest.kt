package com.pixel.intelligentsearch.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowFramePacingTest {

    @Test
    fun testFrameRateCategoryConstants() {
        assertEquals(4, WindowFramePacing.FRAME_RATE_CATEGORY_HIGH)
        assertEquals(2, WindowFramePacing.FRAME_RATE_CATEGORY_NORMAL)
        assertEquals(1, WindowFramePacing.FRAME_RATE_CATEGORY_LOW)
        assertEquals(0, WindowFramePacing.FRAME_RATE_CATEGORY_NO_PREFERENCE)
    }

    @Test
    fun testFrameIntervalNanosCalculation() {
        // Without an activity, defaults to 60Hz fallback (16.66ms = 16_666_666 ns)
        val interval = WindowFramePacing.getFrameIntervalNanos(null)
        assertEquals(16_666_666L, interval)
    }

    @Test
    fun testFrameBudgetRemainingNanos() {
        val now = System.nanoTime()
        val futureDeadline = now + 8_333_333L // 8.33ms ahead (120Hz budget)
        val budget = WindowFramePacing.getFrameBudgetRemainingNanos(futureDeadline)
        assertTrue(budget > 0L)
        assertTrue(budget <= 8_333_333L)

        val pastDeadline = now - 1_000_000L
        val expiredBudget = WindowFramePacing.getFrameBudgetRemainingNanos(pastDeadline)
        assertEquals(0L, expiredBudget)
    }

    @Test
    fun testIsWithinFrameBudget() {
        val now = System.nanoTime()
        val comfortableDeadline = now + 5_000_000L // 5ms ahead
        assertTrue(WindowFramePacing.isWithinFrameBudget(comfortableDeadline, safetyMarginNanos = 1_000_000L))

        val tightDeadline = now + 500_000L // 0.5ms ahead, within 1ms safety margin
        assertFalse(WindowFramePacing.isWithinFrameBudget(tightDeadline, safetyMarginNanos = 1_000_000L))
    }

    @Test
    fun testAGSLBackdropShaderSafety() {
        val shader = AGSLBackdropShader.AGSL_GLASS_SHADER
        // Must NOT contain scalar-to-vector addition (causes SkSL compiler error on Android 13-17)
        assertFalse(shader.contains("color.rgb += noise;"))
        assertTrue(shader.contains("color.rgb += half3(noise);"))
        assertTrue(shader.contains("layout(color) uniform half4 uMonetTint;"))
        assertTrue(shader.contains("compositedImage.eval(fragCoord);"))
    }

    @Test
    fun testDynamicMeshShaderSafety() {
        val effectShader = DynamicMeshShader.AGSL_MESH_GRADIENT_EFFECT
        val brushShader = DynamicMeshShader.AGSL_MESH_GRADIENT_BRUSH

        for (uniform in listOf("uColorTL", "uColorTR", "uColorBL", "uColorBR", "uColorCenter")) {
            assertTrue(effectShader.contains("layout(color) uniform half4 $uniform;"))
        }
        assertTrue(effectShader.contains("compositedImage.eval(fragCoord);"))

        for (uniform in listOf("uColorTL", "uColorTR", "uColorBL", "uColorBR", "uColorCenter")) {
            assertTrue(brushShader.contains("layout(color) uniform half4 $uniform;"))
        }
    }

    @Test
    fun testMultiRefreshRateIntervalCalculations() {
        // Test theoretical frame intervals for 60Hz, 90Hz, 120Hz, 144Hz
        val interval60 = 1_000_000_000L / 60L
        val interval90 = 1_000_000_000L / 90L
        val interval120 = 1_000_000_000L / 120L
        val interval144 = 1_000_000_000L / 144L

        assertEquals(16_666_666L, interval60)
        assertEquals(11_111_111L, interval90)
        assertEquals(8_333_333L, interval120)
        assertEquals(6_944_444L, interval144)
    }
}
