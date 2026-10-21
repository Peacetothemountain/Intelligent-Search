package com.pixel.intelligentsearch.feature.widget

import com.google.android.material.color.utilities.Hct
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Across the whole range of the Custom subtheme's Hue, Saturation and Lightness sliders, on both designs and with the
 * Material pill locked black or not, every colour an icon is drawn in keeps at least 3:1 contrast with the pill it sits
 * on: the accent (Accented G style), the plain icons (System G style) and every part of the Material G style's G and
 * icons. The icons go dark exactly where the pill counts as light.
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
        for (material in listOf(false, true)) for (lockBlack in listOf(false, true)) {
            if (lockBlack && !material) continue
            for (hue in 0..330 step 30) for (saturation in 0..100 step 25) for (lightness in 0..100 step 5) {
                val bar = WidgetColorRules.customBarColors(settings(hue, saturation, lightness, lockBlack), material)
                val label = "material=$material locked=$lockBlack hue=$hue saturation=$saturation lightness=$lightness"
                val pill = if (material && lockBlack) WidgetColorRules.LOCKED_PILL else bar.seed
                assertEquals("$label background", pill, bar.background)
                val icons = mapOf(
                    "accent" to bar.accent,
                    "plain icon" to bar.plainIcon,
                    "G right arc" to bar.gPalette.rightArcAndBar,
                    "G bottom arc" to bar.gPalette.bottomArc,
                    "G left arc" to bar.gPalette.leftArc,
                    "G top arc" to bar.gPalette.topArc,
                    "icon first tone" to bar.iconTiers.primaryDarker,
                    "icon second tone" to bar.iconTiers.secondLighter,
                    "icon third tone" to bar.iconTiers.thirdLighter
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

    @Test
    fun iconsFlipFromLightToDarkOnceAsThePillLightens() {
        for (hue in 0..330 step 30) for (saturation in listOf(0, 50, 100)) {
            val dark = (0..100).map { WidgetColorRules.customBarColors(settings(hue, saturation, it, lockBlack = false), false).darkIcons }
            val flip = dark.indexOf(true)
            assertTrue("hue=$hue saturation=$saturation flips near the middle at $flip", flip in 45..55)
            assertTrue("hue=$hue saturation=$saturation flips once: $dark", dark.drop(flip).all { it })
        }
    }
}
