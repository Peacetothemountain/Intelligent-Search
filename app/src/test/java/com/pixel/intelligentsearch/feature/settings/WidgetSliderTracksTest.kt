package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The widget editor's colour slider tracks show what each slider does at every point, and the Custom G style's HSV
 * sliders always show the colour being edited.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetSliderTracksTest {

    private fun hex(color: Color) = "#%08X".format(color.toArgb())

    private fun hsv(hue: Float, saturation: Float, value: Float) =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    /** Transparency fades the bar's background to nothing; the track went from the colour to black instead. */
    @Test
    fun transparencyTrackFadesTheBackgroundOut() {
        val background = Color(0xCC4A90E2)
        val track = transparencyTrack(background)
        assertEquals("starts at the background as drawn at Transparency 0", hex(background), hex(track.first()))
        assertEquals("ends fully see-through", 0f, track.last().alpha)
        for (stop in track) assertEquals("keeps the background's colour", hex(background.copy(alpha = 1f)), hex(stop.copy(alpha = 1f)))
    }

    /** Color Opacity fades the custom colour in from nothing; from transparent black, the track's left half was muddy. */
    @Test
    fun colorOpacityTrackFadesTheColourIn() {
        val custom = Color(0x804A90E2)
        val track = colorOpacityTrack(custom)
        assertEquals(0f, track.first().alpha)
        assertEquals(hex(custom.copy(alpha = 1f)), hex(track.last()))
        for (stop in track) assertEquals("keeps the custom colour", hex(custom.copy(alpha = 1f)), hex(stop.copy(alpha = 1f)))
    }

    /** The Saturation track is at the Brightness set; it ran from white to the brightest colour whatever the Brightness. */
    @Test
    fun elementSaturationTrackFollowsBrightness() {
        val track = elementSaturationTrack(hue = 120f, brightness = 40f)
        assertEquals("grey at that brightness", hex(hsv(120f, 0f, 0.4f)), hex(track.first()))
        assertEquals("the most colourful at that brightness", hex(hsv(120f, 1f, 0.4f)), hex(track.last()))
    }

    @Test
    fun elementBrightnessTrackFollowsSaturation() {
        val track = elementBrightnessTrack(hue = 300f, saturation = 25f)
        assertEquals(hex(Color.Black), hex(track.first()))
        assertEquals(hex(hsv(300f, 0.25f, 1f)), hex(track.last()))
    }

    /** The HSV sliders move to a colour set elsewhere, and stay put for one they picked. */
    @Test
    fun customIconSlidersShowTheColourBeingEdited() {
        val sliders = WidgetColorSelection()
        sliders.show(0xFF0000FF.toInt())
        assertEquals(240f, sliders.customIconHue)
        assertEquals(100f, sliders.customIconSat)
        assertEquals(100f, sliders.customIconVal)

        // Brightness 0 picks black, which has no hue or saturation; the sliders keep theirs.
        val black = sliders.pick(brightness = 0f)
        sliders.show(black)
        assertEquals(240f, sliders.customIconHue)
        assertEquals(100f, sliders.customIconSat)
        assertEquals(0f, sliders.customIconVal)

        // Another profile's colour, or Reset's, moves them.
        sliders.show(0xFF00FF00.toInt())
        assertEquals(120f, sliders.customIconHue)
        assertEquals(100f, sliders.customIconVal)

        // A grey from the hex field has no hue of its own, so the sliders keep theirs.
        sliders.show(0xFF808080.toInt())
        assertEquals(120f, sliders.customIconHue)
        assertEquals(0f, sliders.customIconSat)
    }
}
