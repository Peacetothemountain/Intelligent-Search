package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.Hct
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
 * worked out here from what the sliders mean (the custom colour is an HCT seed: hue, saturation as the share of the
 * chroma that hue reaches at the tone, and lightness as tone; the Custom subtheme's accent is the seed's hue and
 * saturation at the tone keeping 7:1 with the pill, on the side Material's foreground rule picks; Color Opacity is the
 * custom colour's opacity; Transparency the whole bar's background, except a pill locked black, which stays solid), not from the widget's code. Each part of the
 * background is checked as it shows: the pill and circle are opaque within the backdrop (the group the widget fades at
 * once), so the rim under them never shows through and nothing is painted twice.
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

        /** The HCT seed: hue, the saturation's share of the most chroma that hue reaches at the tone, and tone. */
        val customColor: Int get() = colorAtShare(hue.toDouble(), saturation / 100.0, lightness.toDouble())
        val backgroundAlpha: Double get() = (100 - transparency) / 100.0
        val customAlpha: Double get() = opacity / 100.0 * backgroundAlpha
    }

    /** One painted layer of the widget: its colour without alpha, and its alpha as it shows (with the backdrop's). */
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
            .putBoolean("${p}_custom_color_hct", true)
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

    /** The layer [id] as it shows: its own alpha within the backdrop, times the backdrop's. */
    private fun View.layer(id: Int): Layer {
        val view = findViewById<ImageView>(id)
        val backdrop = findViewById<View>(R.id.widget_backdrop).alpha
        return Layer(view.imageTintList!!.defaultColor and 0xFFFFFF, Math.round(view.imageAlpha * backdrop))
    }

    private fun View.ownAlpha(id: Int): Int = findViewById<ImageView>(id).imageAlpha

    private fun View.tint(id: Int): Int = findViewById<ImageView>(id).imageTintList!!.defaultColor

    private fun widgetColors(isDark: Boolean) = DynamicThemeDetector.resolveWidgetColors(context, isDark)

    private fun alpha(value: Double) = (value * 255).toInt()

    private fun expectedPillLight(s: Sliders): Boolean {
        val customIsLight = iconsGoDarkOn(s.customColor)
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
        // The pill locked black stays solid whatever the Transparency; the rim around it fades.
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

    /**
     * The Accented G's colour. Custom: the seed's hue and saturation at the tone keeping 7:1 with the pill it sits on.
     * Otherwise the theme's accent for the pill's shade, darker on a light Material pill.
     */
    private fun expectedAccent(s: Sliders): Int {
        val light = expectedPillLight(s)
        if (bar.isCustom) {
            val pillTone = Hct.fromInt(if (bar.material && bar.lockBlack) 0xFF121212.toInt() else s.customColor).tone
            val tone = if (light) Contrast.darkerUnsafe(pillTone, 7.0) else Contrast.lighterUnsafe(pillTone, 7.0)
            return colorAtShare(s.hue.toDouble(), s.saturation / 100.0, tone)
        }
        val raw = widgetColors(!light).primary
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
            // Painted once: the pill and circle hide the rim under them, so it never shows through or tints them.
            assertEquals("$label pill is opaque in the backdrop", 255, view.ownAlpha(R.id.widget_pill_background))
            val rim = view.findViewById<ImageView>(R.id.widget_outer_background)
            if (bar.material) {
                assertClose("$label rim", expectedRim(s), view.layer(R.id.widget_outer_background))
                assertClose("$label circle", expectedPill(s), view.layer(R.id.widget_sound_background))
                assertEquals("$label circle is opaque in the backdrop", 255, view.ownAlpha(R.id.widget_sound_background))
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
                val hue = hct(layer!!).hue
                val distance = abs(hue - r.first % 360).let { minOf(it, 360 - it) }
                assertTrue("$bar hue ${r.first} paints HCT hue $hue", distance <= 3.0)
            }
            Slider.SATURATION -> if (bar.isCustom) assertIncreasing("chroma", customLayers.map { hct(it!!).chroma.toFloat() })
            Slider.LIGHTNESS -> if (bar.isCustom) assertIncreasing("lightness", customLayers.map { luminance(it!!) })
            Slider.COLOR_OPACITY -> if (bar.isCustom) assertIncreasing("opacity", customLayers.map { it!!.alpha.toFloat() })
            Slider.TRANSPARENCY -> {
                // The whole background fades, except a pill locked black, which stays solid.
                if (bar.material && bar.lockBlack) {
                    assertEquals("$bar locked pill stays solid", listOf(255), rendered.map { (_, pill, _) -> pill.alpha }.distinct())
                } else {
                    assertIncreasing("pill transparency", rendered.map { (_, pill, _) -> -pill.alpha.toFloat() })
                }
                if (bar.material) assertIncreasing("rim transparency", rendered.map { (_, _, rim) -> -rim!!.alpha.toFloat() })
            }
        }
    }

    private fun hct(layer: Layer) = Hct.fromInt(layer.rgb or 0xFF000000.toInt())

    private fun luminance(layer: Layer) = MaterialYouPaletteHelper.calculateLuminance(layer.rgb).toFloat()

    private fun assertIncreasing(what: String, values: List<Float>) {
        values.zipWithNext().forEach { (a, b) -> assertTrue("$bar $what should rise along the slider: $values", b > a) }
    }

    companion object {
        /** [hue] at [tone] with [share] of the most chroma that hue reaches there. */
        fun colorAtShare(hue: Double, share: Double, tone: Double): Int {
            val most = Hct.from(hue, 200.0, tone).chroma
            return Hct.from(hue % 360.0, share * most, tone).toInt() or 0xFF000000.toInt()
        }

        /** Whether icons go dark on [color]: Material's foreground rule picks a darker tone than its own. */
        fun iconsGoDarkOn(color: Int): Boolean {
            val tone = Hct.fromInt(color or 0xFF000000.toInt()).tone
            return DynamicColor.foregroundTone(tone, 7.0) < tone
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1} dark={2}")
        fun parameters(): List<Array<Any>> = Bar.entries.flatMap { bar ->
            Slider.entries.flatMap { slider -> listOf(arrayOf<Any>(bar, slider, false), arrayOf<Any>(bar, slider, true)) }
        }
    }
}
