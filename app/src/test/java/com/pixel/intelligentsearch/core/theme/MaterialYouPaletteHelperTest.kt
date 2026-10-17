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
    fun testThreeTierAccentedPaletteProgression() {
        val cyanPrimary = 0xFF00BCD4.toInt() // Cyan primary
        val paletteDark = MaterialYouPaletteHelper.getThreeTierAccentedPalette(cyanPrimary, isPillLight = false)

        val pLum = MaterialYouPaletteHelper.calculateLuminance(paletteDark.primaryDarker)
        val sLum = MaterialYouPaletteHelper.calculateLuminance(paletteDark.secondLighter)
        val tLum = MaterialYouPaletteHelper.calculateLuminance(paletteDark.thirdLighter)

        // 1. Primary must be darker than second
        assertTrue("Primary must be darker than second: pLum=$pLum, sLum=$sLum", pLum < sLum)
        // 2. Second must be darker than third (third is a little lighter than second)
        assertTrue("Second must be darker than third: sLum=$sLum, tLum=$tLum", sLum < tLum)

        // 3. Must NOT be pure white (0xFFFFFFFF) or washed-out bright (> 0.80 luminance)
        assertNotEquals(0xFFFFFFFF.toInt(), paletteDark.thirdLighter)
        assertTrue("Third must focus on darker accented, not bright washed-out: tLum=$tLum", tLum < 0.75)

        // 4. Primary must be focused on darker accented
        assertTrue("Primary must be darker accented: pLum=$pLum", pLum < 0.35)

        // 5. Must be fully opaque
        assertEquals(0xFF000000.toInt(), paletteDark.primaryDarker and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), paletteDark.secondLighter and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), paletteDark.thirdLighter and 0xFF000000.toInt())

        // 6. Light surface palette
        val paletteLight = MaterialYouPaletteHelper.getThreeTierAccentedPalette(cyanPrimary, isPillLight = true)
        val lumLight1 = MaterialYouPaletteHelper.calculateLuminance(paletteLight.primaryDarker)
        val lumLight2 = MaterialYouPaletteHelper.calculateLuminance(paletteLight.secondLighter)
        val lumLight3 = MaterialYouPaletteHelper.calculateLuminance(paletteLight.thirdLighter)

        assertTrue("Light pill: Second ($lumLight2) must be lighter than primary ($lumLight1)", lumLight2 > lumLight1)
        assertTrue("Light pill: Third ($lumLight3) must be lighter than second ($lumLight2)", lumLight3 > lumLight2)
        assertNotEquals("Light pill: Third must never be pure white", 0xFFFFFFFF.toInt(), paletteLight.thirdLighter)
        assertTrue("Light pill: Third must be darker accented (< 0.50 luminance), was $lumLight3", lumLight3 < 0.50)
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
    fun testWcagContrastCalculations() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val ratio = MaterialYouPaletteHelper.calculateContrastRatio(white, black)
        assertEquals(21.0, ratio, 0.1)
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

    @Test
    fun testFourColorMaterialGPalette_strictlyPreservesUserThemePalette() {
        // Test with a user's theme color: Purple primary (0xFF7950F2), Secondary (0xFF845EF7), Tertiary (0xFF9775FA)
        val p = 0xFF7950F2.toInt()
        val s = 0xFF845EF7.toInt()
        val t = 0xFF9775FA.toInt()

        val paletteDark = MaterialYouPaletteHelper.getFourColorMaterialGPalette(p, s, t, isPillLight = false)
        val paletteLight = MaterialYouPaletteHelper.getFourColorMaterialGPalette(p, s, t, isPillLight = true)

        // 1. All colors must have full opacity
        assertEquals(0xFF000000.toInt(), paletteDark.rightArcAndBar and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), paletteDark.bottomArc and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), paletteDark.leftArc and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), paletteDark.topArc and 0xFF000000.toInt())

        assertEquals(0xFF000000.toInt(), paletteLight.rightArcAndBar and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), paletteLight.bottomArc and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), paletteLight.leftArc and 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), paletteLight.topArc and 0xFF000000.toInt())

        // 2. Quadrants must strictly share the user's theme hues (no arbitrary +135° or +220° rainbow rotation)
        val hctP = com.google.android.material.color.utilities.Hct.fromInt(p)
        val hctC1 = com.google.android.material.color.utilities.Hct.fromInt(paletteDark.rightArcAndBar)
        val hctC4 = com.google.android.material.color.utilities.Hct.fromInt(paletteDark.topArc)

        assertEquals("Right arc anchor must match primary hue", hctP.hue, hctC1.hue, 1.0)
        assertEquals("Top arc must share user's primary hue family", hctP.hue, hctC4.hue, 1.0)

        // 3. Tonal progression in dark mode: tones should be readable on dark background
        assertTrue(hctC1.tone >= 50.0)
        assertTrue(hctC4.tone >= hctC1.tone)
    }
}

