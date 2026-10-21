package com.pixel.intelligentsearch.feature.settings

import com.google.android.material.color.utilities.Hct
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The Color Studio's hex field and its Hue, Saturation and Lightness sliders stay in step both ways: a typed colour
 * sets sliders that pick that colour again (as near as whole-number sliders allow), and the colour the sliders pick
 * sets them back to the same values. Lightness is the seed's tone and Saturation its share of the chroma available.
 */
class WidgetHexSyncTest {

    private fun channels(color: Int) = listOf(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF)

    @Test
    fun aTypedColourComesBackFromTheSliders() {
        val typed = listOf(
            0xFF4285F4, 0xFFEA4335, 0xFFFBBC05, 0xFF34A853, 0xFF6750A4, 0xFF000000, 0xFFFFFFFF, 0xFF808080,
            0xFF1E6FB3, 0xFFB3261E, 0xFFF1F3F4, 0xFF303134, 0xFFFF00FF, 0xFF00FFFF, 0xFF123456, 0xFFFEDCBA
        ).map { it.toInt() }
        for (color in typed) {
            val sliders = slidersForHex(color, currentHue = 123f)
            val back = MaterialYouPaletteHelper.seedColor(sliders.hue, sliders.saturation, sliders.lightness)
            val drift = channels(color).zip(channels(back)).maxOf { (a, b) -> abs(a - b) }
            assertTrue("#%06X comes back as #%06X via $sliders".format(color and 0xFFFFFF, back and 0xFFFFFF), drift <= 4)
            assertEquals("lightness is the tone of #%06X".format(color and 0xFFFFFF), Hct.fromInt(color).tone, sliders.lightness.toDouble(), 1.0)
        }
        // A grey has no hue: the hue slider stays where it was.
        assertEquals(123f, slidersForHex(0xFF808080.toInt(), currentHue = 123f).hue)
    }

    @Test
    fun theSlidersComeBackFromTheirColour() {
        for (hue in 0 until 360 step 15) for (saturation in 20..100 step 20) for (lightness in 20..80 step 15) {
            val color = MaterialYouPaletteHelper.seedColor(hue.toFloat(), saturation.toFloat(), lightness.toFloat())
            val back = slidersForHex(color, currentHue = 0f)
            val label = "hue=$hue saturation=$saturation lightness=$lightness -> $back"
            // Greyish colours show their hue less precisely than they hold it; the colour itself comes back below.
            if (Hct.fromInt(color).chroma >= 15.0) assertTrue(label, abs(back.hue - hue).let { minOf(it, 360 - it) } <= 4f)
            assertTrue(label, abs(back.saturation - saturation) <= 3f)
            val again = MaterialYouPaletteHelper.seedColor(back.hue, back.saturation, back.lightness)
            assertTrue(label, channels(color).zip(channels(again)).all { (a, b) -> abs(a - b) <= 2 })
            assertTrue(label, abs(back.lightness - lightness) <= 1f)
        }
    }

    @Test
    fun saturationAndLightnessMoveChromaAndToneOnly() {
        for (hue in 0 until 360 step 30) {
            val chromas = (0..100 step 10).map { Hct.fromInt(MaterialYouPaletteHelper.seedColor(hue.toFloat(), it.toFloat(), 60f)).chroma }
            chromas.zipWithNext().forEach { (a, b) -> assertTrue("hue $hue chroma rises with saturation: $chromas", b > a) }
            val tones = (0..100 step 10).map { Hct.fromInt(MaterialYouPaletteHelper.seedColor(hue.toFloat(), 60f, it.toFloat())).tone }
            tones.forEachIndexed { i, tone -> assertEquals("hue $hue lightness ${i * 10}", i * 10.0, tone, 0.6) }
        }
    }
}
