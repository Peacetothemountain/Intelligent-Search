package com.pixel.intelligentsearch.core.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The settings pages' glow takes its colours from the theme: the primary hue and its neighbours, in deep tones. */
class PageGlowTest {

    private fun hsv(c: Color): FloatArray =
        java.awt.Color.RGBtoHSB((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), null)
            .let { floatArrayOf(it[0] * 360f, it[1], it[2]) }

    private fun hueGap(a: Float, b: Float) = ((b - a + 540f) % 360f) - 180f

    @Test
    fun thePrimaryHueAndTwentyDegreesEitherSideInDeepTones() {
        val primary = Color.hsv(328f, 0.27f, 0.95f)
        val (left, middle, right) = pageGlowTones(primary).map(::hsv)
        assertEquals(328f, middle[0], 2f)
        assertEquals(-20f, hueGap(middle[0], left[0]), 2f)
        assertEquals(20f, hueGap(middle[0], right[0]), 2f)
        for (tone in listOf(left, middle, right)) {
            assertEquals("much darker than the role", 0.46f, tone[2], 0.02f)
            assertTrue("richer than the role, never garish", tone[1] > 0.27f && tone[1] <= 0.59f)
        }
    }

    @Test
    fun aGreyThemeGetsAGreyGlow() {
        for (tone in pageGlowTones(Color(0xFFC4C4C4)).map(::hsv)) assertEquals(0f, tone[1], 0.01f)
    }

    @Test
    fun aLightPageGetsASofterGlow() {
        assertTrue(PAGE_GLOW_PEAK_LIGHT < PAGE_GLOW_PEAK_DARK)
    }
}
