package com.pixel.intelligentsearch.feature.widget

import com.google.android.material.color.utilities.Hct
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Across the whole range of the Custom subtheme's Hue, Saturation and Lightness sliders, on both designs and with the
 * Material pill locked black or not, every colour the Accented and System G styles draw an icon in keeps at least 3:1
 * contrast with the pill it sits on: the accent (Accented G style) and the plain icons (System G style). The icons go
 * dark exactly where the pill counts as light. (The Material G style's icons are the search overlay's, in the
 * overlay's colours: see WidgetMaterialIconsMatchOverlayTest.)
 */
@RunWith(RobolectricTestRunner::class)
class WidgetCustomBarContrastTest {

    private fun settings(hue: Int, saturation: Int, lightness: Int, lockBlack: Boolean) = WidgetDesignSettings(
        subtheme = "Custom", gIconTheme = "Accented G Icon", elementColors = WidgetElementColors(0, 0, 0, 0, 0, 0),
        syncColors = false, keepSystemG = false, hue = hue.toFloat(), saturation = saturation.toFloat(),
        lightness = lightness.toFloat(), colorOpacity = 100f, transparency = 0f, showGIcon = true, showVoice = true,
        shortcut1 = "None", shortcut2 = "None", shortcut3 = "None", slotOrder = "mic", lockBlack = lockBlack,
        actionIcon = "Search"
    )

    @Test
    fun everyIconKeepsItsContrastOnThePill() {
        var checked = 0
        for (dark in listOf(true, false)) for (material in listOf(false, true)) for (lockBlack in listOf(false, true)) {
            if (lockBlack && !material) continue
            val theme = DynamicThemeDetector.resolveWidgetColors(ApplicationProvider.getApplicationContext(), dark)
            for (hue in 0..330 step 30) for (saturation in 0..100 step 25) for (lightness in 0..100 step 5) {
                val bar = WidgetColorRules.customBarColors(settings(hue, saturation, lightness, lockBlack), material, theme)
                val label = "dark=$dark material=$material locked=$lockBlack hue=$hue saturation=$saturation lightness=$lightness"
                // Locked with no color picked: the theme's own pill (#121212 in dark, its light twin in light).
                val pill = if (material && lockBlack) theme.lockedPill or 0xFF000000.toInt() else bar.seed
                assertEquals("$label background", pill, bar.background)
                val icons = mapOf(
                    "accent" to bar.accent,
                    "plain icon" to bar.plainIcon
                )
                val pillTone = Hct.fromInt(pill).tone
                for ((what, icon) in icons) {
                    val ratio = MaterialYouPaletteHelper.calculateContrastRatio(icon, pill)
                    assertTrue("$label $what #%06X on #%06X: $ratio".format(icon and 0xFFFFFF, pill and 0xFFFFFF), ratio >= 3.0)
                    assertEquals("$label $what is on the icons' side of the pill", bar.darkIcons, Hct.fromInt(icon).tone < pillTone)
                    checked++
                }
                // The accent is the strongest: at least Material's 4.5:1 everywhere, 7:1 wherever the pill allows.
                assertTrue("$label accent", MaterialYouPaletteHelper.calculateContrastRatio(bar.accent, pill) >= 4.5)
            }
        }
        assertTrue(checked > 0)
    }

    private val darkTheme get() = DynamicThemeDetector.resolveWidgetColors(ApplicationProvider.getApplicationContext(), true)

    @Test
    fun iconsFlipFromLightToDarkOnceAsThePillLightens() {
        for (hue in 0..330 step 30) for (saturation in listOf(0, 50, 100)) {
            val dark = (0..100).map { WidgetColorRules.customBarColors(settings(hue, saturation, it, lockBlack = false), false, darkTheme).darkIcons }
            val flip = dark.indexOf(true)
            assertTrue("hue=$hue saturation=$saturation flips near the middle at $flip", flip in 45..55)
            assertTrue("hue=$hue saturation=$saturation flips once: $dark", dark.drop(flip).all { it })
        }
    }
}
