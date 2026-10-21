package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Every Color Studio slider, set to values across its range, changes what its label says on the widget the launcher
 * draws, and only that: for each design, subtheme and inner pill state, in light and dark. The expected colours are
 * worked out here from what the sliders mean (the custom colour is HSV: hue, saturation, and lightness as value;
 * Color Opacity is the custom colour's opacity; Transparency the whole bar's background), not from the widget's code.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetColorSlidersTest(private val bar: Bar, private val slider: Slider, private val dark: Boolean) {

    /** A design with one subtheme and, for the Material design, its Inner Pill & Circle State (lock black). */
    enum class Bar(val material: Boolean, val subtheme: String, val lockBlack: Boolean = false) {
        SYSTEM_DEFAULT(false, "System"),
        SYSTEM_LIGHT(false, "Light"),
        SYSTEM_DARK(false, "Dark"),
        SYSTEM_CUSTOM(false, "Custom"),
        MATERIAL(true, "Material"),
        MATERIAL_LOCKED(true, "Material", lockBlack = true),
        MATERIAL_CUSTOM(true, "Custom"),
        MATERIAL_CUSTOM_LOCKED(true, "Custom", lockBlack = true);

        val isCustom get() = subtheme == "Custom"
    }

    enum class Slider(val values: List<Int>) {
        HUE(listOf(0, 90, 180, 270, 360)),
        SATURATION(listOf(0, 25, 50, 75, 100)),
        LIGHTNESS(listOf(0, 25, 50, 75, 100)),
        COLOR_OPACITY(listOf(0, 25, 50, 75, 100)),
        TRANSPARENCY(listOf(0, 25, 50, 75, 100))
    }

    /** The Color Studio values; the one under test is replaced by each of its values in turn. */
    data class Sliders(val hue: Int = 200, val saturation: Int = 60, val lightness: Int = 90, val opacity: Int = 80, val transparency: Int = 10) {
        fun with(slider: Slider, value: Int) = when (slider) {
            Slider.HUE -> copy(hue = value)
            Slider.SATURATION -> copy(saturation = value)
            Slider.LIGHTNESS -> copy(lightness = value)
            Slider.COLOR_OPACITY -> copy(opacity = value)
            Slider.TRANSPARENCY -> copy(transparency = value)
        }

        val customColor: Int get() = Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation / 100f, lightness / 100f))
        val backgroundAlpha: Double get() = (100 - transparency) / 100.0
        val customAlpha: Double get() = opacity / 100.0 * backgroundAlpha
    }

    /** One painted layer of the widget: its colour without alpha, and its alpha. */
    data class Layer(val rgb: Int, val alpha: Int) {
        override fun toString() = "#%06X@%d".format(rgb and 0xFFFFFF, alpha)
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_color_sliders_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private val p get() = if (bar.material) "widget_material" else "widget_system"

    private fun render(s: Sliders, gStyle: String): View {
        prefs.edit().clear()
            .putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light")
            .putBoolean("${p}_design_saved", true)
            .putString("${p}_subtheme", bar.subtheme)
            .putString("${p}_material_g_icon", gStyle)
            .putInt("${p}_custom_hue", s.hue)
            .putInt("${p}_custom_saturation", s.saturation)
            .putInt("${p}_custom_lightness", s.lightness)
            .putInt("${p}_custom_color_opacity", s.opacity)
            .putInt("${p}_background_transparency", s.transparency)
            .putBoolean("${p}_lock_black", bar.lockBlack)
            .putString("${p}_action_icon", "Search")
            .putBoolean("${p}_show_doodles", false)
            .putBoolean("${p}_show_voice", true)
            .putString("${p}_shortcut_order", "mic,shortcut1,shortcut2,shortcut3")
            .putString("${p}_shortcut_1", "Weather")
            .putString("${p}_shortcut_2", "None")
            .putString("${p}_shortcut_3", "None")
            .commit()
        return SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = bar.material, prefs = prefs)
            .getValue(SearchWidgetProvider.EXPANDED_WIDTH)
            .apply(activity, FrameLayout(activity))
    }

    private fun View.layer(id: Int): Layer {
        val view = findViewById<ImageView>(id)
        return Layer(view.imageTintList!!.defaultColor and 0xFFFFFF, view.imageAlpha)
    }

    private fun View.tint(id: Int): Int = findViewById<ImageView>(id).imageTintList!!.defaultColor

    private fun widgetColors(isDark: Boolean) = DynamicThemeDetector.resolveWidgetColors(context, isDark)

    private fun alpha(value: Double) = (value * 255).toInt()

    private fun expectedPillLight(s: Sliders): Boolean {
        val c = s.customColor
        val customIsLight = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0 > 0.5
        return when {
            bar.material && bar.lockBlack -> false
            bar.isCustom -> customIsLight
            bar.subtheme == "Light" -> true
            bar.subtheme == "Dark" -> false
            else -> !dark
        }
    }

    private fun expectedPill(s: Sliders): Layer {
        val rgb = when {
            bar.material && bar.lockBlack -> 0x121212
            bar.isCustom -> s.customColor
            bar.subtheme == "Light" -> 0xF1F3F4
            bar.subtheme == "Dark" -> 0x303134
            else -> widgetColors(dark).surfaceContainer
        } and 0xFFFFFF
        val a = when {
            bar.material && bar.lockBlack -> 255
            bar.isCustom -> alpha(s.customAlpha)
            else -> alpha(s.backgroundAlpha)
        }
        return Layer(rgb, a)
    }

    /** The Material design's outer rim: the custom colour at its opacity, or the theme's at the bar's transparency. */
    private fun expectedRim(s: Sliders): Layer =
        if (bar.isCustom) Layer(s.customColor and 0xFFFFFF, alpha(s.customAlpha))
        else Layer(widgetColors(dark || bar.lockBlack).primaryContainer and 0xFFFFFF, alpha(s.backgroundAlpha))

    /** The Accented G's colour: the theme's (or the custom hue's) accent for the pill's shade, darker on a light Material pill. */
    private fun expectedAccent(s: Sliders): Int {
        val light = expectedPillLight(s)
        val raw = if (bar.isCustom) {
            MaterialYouPaletteHelper.getMaterialYouTonalColors(s.hue.toFloat(), s.saturation.toFloat(), isDarkSurface = !light).primary
        } else {
            widgetColors(!light).primary
        }
        return if (bar.material && !bar.lockBlack && light) MaterialYouPaletteHelper.darkenForContrast(raw, 32.0) else raw
    }

    private fun assertClose(message: String, expected: Layer, actual: Layer) {
        assertEquals("$message colour", expected.rgb, actual.rgb)
        assertTrue("$message alpha: expected ${expected.alpha}, got ${actual.alpha}", abs(expected.alpha - actual.alpha) <= 1)
    }

    @Test
    fun sliderPaintsTheWidget() {
        val rendered = slider.values.map { value ->
            val s = Sliders().with(slider, value)
            val view = render(s, gStyle = "Accented G Icon")
            val label = "$bar ${if (dark) "dark" else "light"} $slider=$value"

            val pill = view.layer(R.id.widget_pill_background)
            assertClose("$label pill", expectedPill(s), pill)
            val rim = view.findViewById<ImageView>(R.id.widget_outer_background)
            if (bar.material) {
                assertClose("$label rim", expectedRim(s), view.layer(R.id.widget_outer_background))
                assertClose("$label circle", expectedPill(s), view.layer(R.id.widget_sound_background))
                assertEquals("$label action icon", expectedAccent(s), view.tint(R.id.widget_sound_icon))
            } else {
                assertEquals("$label has no rim", View.GONE, rim.visibility)
            }
            assertEquals("$label G", expectedAccent(s), view.tint(R.id.widget_g_logo))
            assertEquals("$label mic", expectedAccent(s), view.tint(R.id.widget_voice_search))
            assertEquals("$label shortcut", expectedAccent(s), view.tint(R.id.widget_lens_search))
            Triple(value, pill, if (bar.material) view.layer(R.id.widget_outer_background) else null)
        }

        // And each slider moves what it controls the way its label says, over its whole range.
        val customLayers = rendered.map { (_, pill, rim) -> if (bar.isCustom && !(bar.material && bar.lockBlack)) pill else rim }
        when (slider) {
            Slider.HUE -> if (bar.isCustom) rendered.zip(customLayers).forEach { (r, layer) ->
                val hsv = FloatArray(3).also { Color.colorToHSV(layer!!.rgb, it) }
                val distance = abs(hsv[0] - r.first % 360).let { minOf(it, 360 - it) }
                assertTrue("$bar hue ${r.first} paints hue ${hsv[0]}", distance <= 2f)
            }
            Slider.SATURATION -> if (bar.isCustom) assertIncreasing("saturation", customLayers.map { hsv(it!!)[1] })
            Slider.LIGHTNESS -> if (bar.isCustom) assertIncreasing("lightness", customLayers.map { luminance(it!!) })
            Slider.COLOR_OPACITY -> if (bar.isCustom) assertIncreasing("opacity", customLayers.map { it!!.alpha.toFloat() })
            Slider.TRANSPARENCY -> {
                val background = rendered.map { (_, pill, rim) -> if (bar.material && bar.lockBlack) rim!! else pill }
                assertIncreasing("transparency", background.map { -it.alpha.toFloat() })
            }
        }
    }

    private fun hsv(layer: Layer) = FloatArray(3).also { Color.colorToHSV(layer.rgb, it) }

    private fun luminance(layer: Layer) = MaterialYouPaletteHelper.calculateLuminance(layer.rgb).toFloat()

    private fun assertIncreasing(what: String, values: List<Float>) {
        values.zipWithNext().forEach { (a, b) -> assertTrue("$bar $what should rise along the slider: $values", b > a) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1} dark={2}")
        fun parameters(): List<Array<Any>> = Bar.entries.flatMap { bar ->
            Slider.entries.flatMap { slider -> listOf(arrayOf<Any>(bar, slider, false), arrayOf<Any>(bar, slider, true)) }
        }
    }
}
