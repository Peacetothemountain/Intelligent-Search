package com.pixel.intelligentsearch.core.theme

import org.junit.Test
import org.junit.Assert.*

class MaterialYouPaletteHelperTest {

    @Test
    fun testUserConfiguredHue308Saturation42() {
        val darkColors = MaterialYouPaletteHelper.getMaterialYouTonalColors(
            hue = 308f,
            saturation = 42f,
            isDarkSurface = true
        )

        val lightColors = MaterialYouPaletteHelper.getMaterialYouTonalColors(
            hue = 308f,
            saturation = 42f,
            isDarkSurface = false
        )

        // Verify full opacity
        assertEquals(0xFF000000.toInt(), darkColors.primary and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), darkColors.secondary and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), darkColors.tertiary and 0xFF000000.toInt())

        assertEquals(0xFF000000.toInt(), lightColors.primary and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), lightColors.secondary and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), lightColors.tertiary and 0xFF000000.toInt())

        // Verify tones are distinct and harmonized
        assertNotEquals(darkColors.primary, darkColors.secondary)
        assertNotEquals(darkColors.primary, darkColors.tertiary)
        assertNotEquals(darkColors.secondary, darkColors.tertiary)

        assertNotEquals(lightColors.primary, lightColors.secondary)
        assertNotEquals(lightColors.primary, lightColors.tertiary)
        assertNotEquals(lightColors.secondary, lightColors.tertiary)

        // Dark tones should be lighter (Tone 80) than light tones (Tone 40)
        // Red channel of primary in dark mode should be higher than in light mode
        val darkPrimaryRed = (darkColors.primary shr 16) and 0xFF
        val lightPrimaryRed = (lightColors.primary shr 16) and 0xFF
        assertTrue(darkPrimaryRed > lightPrimaryRed)
    }
}
