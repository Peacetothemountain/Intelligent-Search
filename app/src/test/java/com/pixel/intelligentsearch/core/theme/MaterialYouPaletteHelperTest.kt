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

    @Test
    fun testMemoizationAndCacheClear() {
        MaterialYouPaletteHelper.clearCache()
        val c1 = MaterialYouPaletteHelper.getMaterialYouTonalColors(120f, 60f, true)
        val c2 = MaterialYouPaletteHelper.getMaterialYouTonalColors(120f, 60f, true)
        assertSame(c1, c2)

        MaterialYouPaletteHelper.clearCache()
        val c3 = MaterialYouPaletteHelper.getMaterialYouTonalColors(120f, 60f, true)
        assertEquals(c1.primary, c3.primary)
    }

    @Test
    fun testAmoledColorSchemePureBlackSurfaces() {
        val amoledScheme = MaterialYouPaletteHelper.createComposeColorScheme(
            seedColor = androidx.compose.ui.graphics.Color(0xFF4285F4),
            isDark = true,
            isAmoled = true
        )
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), amoledScheme.surface)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), amoledScheme.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), amoledScheme.surfaceDim)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), amoledScheme.surfaceContainerLowest)
    }

    @Test
    fun testAmoledDarkColorSchemeCanonicalTokens() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), AmoledDarkColorScheme.surface)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), AmoledDarkColorScheme.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), AmoledDarkColorScheme.surfaceDim)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), AmoledDarkColorScheme.surfaceContainerLowest)
        assertEquals(AmoledDarkPrimary, AmoledDarkColorScheme.primary)
    }

    @Test
    fun testRobinDarkColorSchemeCanonicalTokens() {
        assertEquals(GoogleDarkSurface, RobinDarkColorScheme.surface)
        assertEquals(GoogleDarkBackground, RobinDarkColorScheme.background)
        assertEquals(GoogleDarkPrimary, RobinDarkColorScheme.primary)
        assertEquals(GoogleDarkSurfaceVariant, RobinDarkColorScheme.surfaceVariant)
    }

    @Test
    fun testWcagContrastCalculations() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val ratio = MaterialYouPaletteHelper.calculateContrastRatio(white, black)
        assertEquals(21.0, ratio, 0.1)
        assertTrue(MaterialYouPaletteHelper.meetsWcagContrast(white, black, isAaa = true))
    }

    @Test
    fun testM3ExpressiveFixedRolesExtraction() {
        val tonalRoles = MaterialYouPaletteHelper.getMaterialYouTonalColors(
            hue = 210f,
            saturation = 50f,
            isDarkSurface = true
        )
        assertNotEquals(0, tonalRoles.primaryFixed)
        assertNotEquals(0, tonalRoles.secondaryFixed)
        assertNotEquals(0, tonalRoles.tertiaryFixed)
        assertNotEquals(0, tonalRoles.onPrimaryFixed)
        assertNotEquals(0, tonalRoles.onSecondaryFixed)
        assertNotEquals(0, tonalRoles.onTertiaryFixed)

        // Verify full opacity
        assertEquals(0xFF000000.toInt(), tonalRoles.primaryFixed and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), tonalRoles.secondaryFixed and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), tonalRoles.tertiaryFixed and 0xFF000000.toInt())
    }

    @Test
    fun testAmoledColorSchemeSurfaceBright() {
        val amoledScheme = MaterialYouPaletteHelper.createComposeColorScheme(
            seedColor = androidx.compose.ui.graphics.Color(0xFF4285F4),
            isDark = true,
            isAmoled = true
        )
        assertEquals(AmoledDarkSurfaceBright, amoledScheme.surfaceBright)

        val convertedAmoled = RobinDarkColorScheme.toAmoled()
        assertEquals(AmoledDarkSurfaceBright, convertedAmoled.surfaceBright)
    }

    @Test
    fun testFontCacheIdempotence() {
        val f1 = googleSansFlexVariable(weight = 500, width = 100f)
        val f2 = googleSansFlexVariable(weight = 500, width = 100f)
        assertSame("Variable font families must be cached to prevent recomposition allocations", f1, f2)
    }

    @Test
    fun testPixelHapticTypeCoverage() {
        val types = com.pixel.intelligentsearch.core.haptics.PixelHapticType.values()
        assertTrue(types.contains(com.pixel.intelligentsearch.core.haptics.PixelHapticType.SEARCH_RESULT_COMMIT))
        assertTrue(types.contains(com.pixel.intelligentsearch.core.haptics.PixelHapticType.SEGMENT_SWITCH))
        assertTrue(types.contains(com.pixel.intelligentsearch.core.haptics.PixelHapticType.EXPAND_COLLAPSE))
        assertTrue(types.contains(com.pixel.intelligentsearch.core.haptics.PixelHapticType.SLIDER_DETENT))
        assertTrue(types.contains(com.pixel.intelligentsearch.core.haptics.PixelHapticType.WARP_SPEED))
        assertTrue(types.contains(com.pixel.intelligentsearch.core.haptics.PixelHapticType.PREDICTIVE_BACK_PROGRESS))
    }

    @Test
    fun testDarkenForContrast_preservesHueAndReducesTone() {
        val lightColors = MaterialYouPaletteHelper.getMaterialYouTonalColors(
            hue = 277f,
            saturation = 51f,
            isDarkSurface = true // Tone 80
        )
        val darkenedPrimary = MaterialYouPaletteHelper.darkenForContrast(lightColors.primary, 24.0)
        val darkenedSecondary = MaterialYouPaletteHelper.darkenForContrast(lightColors.secondary, 26.0)
        val darkenedTertiary = MaterialYouPaletteHelper.darkenForContrast(lightColors.tertiary, 24.0)

        val hctOriginal = com.google.android.material.color.utilities.Hct.fromInt(lightColors.primary)
        val hctDark = com.google.android.material.color.utilities.Hct.fromInt(darkenedPrimary)

        // Hue must be preserved within 1 degree
        assertEquals(hctOriginal.hue, hctDark.hue, 1.0)

        // Tone must be reduced to target dark tone (<= 25.0)
        assertTrue("Darkened tone must be <= 25.0, was ${hctDark.tone}", hctDark.tone <= 25.0)

        // Full opacity preserved
        assertEquals(0xFF000000.toInt(), darkenedPrimary and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), darkenedSecondary and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), darkenedTertiary and 0xFF000000.toInt())

        // Luminance must be much lower than the light color
        val lumLight = MaterialYouPaletteHelper.calculateLuminance(lightColors.primary)
        val lumDark = MaterialYouPaletteHelper.calculateLuminance(darkenedPrimary)
        assertTrue("Darkened luminance ($lumDark) must be much lower than light ($lumLight)", lumDark < lumLight / 2.0)
    }
}
