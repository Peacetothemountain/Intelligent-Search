package com.pixel.intelligentsearch.core.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app's light and dark color roles. The dark theme is the dynamic palette exactly as it comes, with its page and
 * containers as they always were. The light theme draws as Pixel's own light pages do: a tinted surfaceContainer page
 * with near-white containers on it, so cards, lists and tracks stand out from the page instead of graying into it,
 * and all text on them keeps WCAG AA contrast.
 */
@RunWith(RobolectricTestRunner::class)
class AppColorSchemeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dark get() = appColorScheme(context, darkTheme = true)
    private val light get() = appColorScheme(context, darkTheme = false)

    private fun hex(color: Color) = "#%08X".format(color.toArgb())

    @Test
    fun theDarkThemeIsThePaletteAsItComes() {
        val palette = dynamicDarkColorScheme(context)
        for ((role, ours, theirs) in roles(dark, palette)) assertEquals("dark $role", hex(theirs), hex(ours))
        assertTrue(dark.isDark)
    }

    @Test
    fun theDarkThemesContainersAreUnchanged() {
        assertEquals(hex(dark.surfaceContainer), hex(dark.pageContainer))
        assertEquals(hex(dark.surfaceContainerHigh), hex(dark.raisedContainer))
        for (alpha in listOf(0.4f, 0.5f, 0.6f)) {
            assertEquals(hex(dark.surfaceVariant.copy(alpha = alpha)), hex(dark.pageTrack(alpha)))
        }
    }

    /** The light page is the palette's surfaceContainer; every other role is the palette's own. */
    @Test
    fun theLightPageIsTheTintedSurfaceContainer() {
        val palette = dynamicLightColorScheme(context)
        assertEquals(hex(palette.surfaceContainer), hex(light.background))
        for ((role, ours, theirs) in roles(light, palette)) {
            if (role != "background") assertEquals("light $role", hex(theirs), hex(ours))
        }
        assertFalse(light.isDark)
    }

    @Test
    fun theLightThemesContainersAreItsNearWhiteSurface() {
        assertEquals(hex(light.surfaceContainerLowest), hex(light.pageContainer))
        assertEquals(hex(light.surfaceContainerLowest), hex(light.raisedContainer))
        for (alpha in listOf(0.4f, 0.5f, 0.6f)) assertEquals(hex(light.surfaceContainerLowest), hex(light.pageTrack(alpha)))
    }

    /**
     * The light containers stand out from the light page at least as clearly as the dark ones do from the dark page.
     * (Before, the light card was a surfaceContainer on a near-white page: almost no step at all.)
     */
    @Test
    fun lightContainersStandOutFromThePageAsDarkOnesDo() {
        val darkStep = contrast(dark.pageContainer, dark.background)
        val lightStep = contrast(light.pageContainer, light.background)
        assertTrue("light card on page $lightStep, dark $darkStep", lightStep >= darkStep)
        val oldLightStep = contrast(dynamicLightColorScheme(context).surfaceContainer, dynamicLightColorScheme(context).background)
        assertTrue("light card on page $lightStep, before $oldLightStep", lightStep > oldLightStep)
    }

    /** Body and secondary text keep WCAG AA (4.5:1) on the page and on every container, in both themes. */
    @Test
    fun textKeepsAaContrastOnThePageAndItsContainers() {
        for (scheme in listOf(light, dark)) {
            val surfaces = mapOf(
                "page" to scheme.background,
                "card" to scheme.pageContainer,
                "raised" to scheme.raisedContainer,
                "track" to scheme.pageTrack(0.6f).compositeOver(scheme.background)
            )
            for ((name, surface) in surfaces) for ((text, color) in mapOf("onSurface" to scheme.onSurface, "onSurfaceVariant" to scheme.onSurfaceVariant)) {
                val ratio = contrast(color, surface)
                assertTrue("dark=${scheme.isDark} $text on $name: $ratio", ratio >= 4.5)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double = MaterialYouPaletteHelper.calculateContrastRatio(a.toArgb(), b.toArgb())

    private fun roles(ours: ColorScheme, theirs: ColorScheme) = listOf(
        Triple("background", ours.background, theirs.background),
        Triple("surface", ours.surface, theirs.surface),
        Triple("surfaceContainerLowest", ours.surfaceContainerLowest, theirs.surfaceContainerLowest),
        Triple("surfaceContainerLow", ours.surfaceContainerLow, theirs.surfaceContainerLow),
        Triple("surfaceContainer", ours.surfaceContainer, theirs.surfaceContainer),
        Triple("surfaceContainerHigh", ours.surfaceContainerHigh, theirs.surfaceContainerHigh),
        Triple("surfaceContainerHighest", ours.surfaceContainerHighest, theirs.surfaceContainerHighest),
        Triple("surfaceVariant", ours.surfaceVariant, theirs.surfaceVariant),
        Triple("primary", ours.primary, theirs.primary),
        Triple("primaryContainer", ours.primaryContainer, theirs.primaryContainer),
        Triple("onSurface", ours.onSurface, theirs.onSurface),
        Triple("onSurfaceVariant", ours.onSurfaceVariant, theirs.onSurfaceVariant),
        Triple("outlineVariant", ours.outlineVariant, theirs.outlineVariant),
        Triple("scrim", ours.scrim, theirs.scrim)
    )
}
