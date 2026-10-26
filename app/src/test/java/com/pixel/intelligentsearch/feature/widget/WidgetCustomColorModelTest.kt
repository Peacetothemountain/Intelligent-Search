package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.utilities.Hct
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.feature.widget.WidgetColorSlidersTest.Bar
import com.pixel.intelligentsearch.feature.widget.WidgetColorSlidersTest.Slider
import com.pixel.intelligentsearch.feature.widget.WidgetColorSlidersTest.Sliders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The Custom subtheme themes the whole bar from one seed, as Google's search bar editor does: Hue, Saturation and
 * Lightness pick an HCT colour (hue, a share of the chroma that hue reaches at that tone, tone), which fills the pill,
 * the circle and the rim, and every icon takes its colour from the same hue and saturation at a tone chosen for
 * contrast on the pill. So each slider moves the background and the icons, and nothing else: Color Opacity and
 * Transparency only make the background see-through. Checked on the widget the launcher draws, with the Accented G
 * style (one accent for the G, mic, shortcuts and circle icon) and the System G style (plain icons).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetCustomColorModelTest(private val bar: Bar, private val slider: Slider, private val dark: Boolean) {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_custom_color_model_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private val p get() = if (bar.material) "widget_material" else "widget_system"
    private val locked get() = bar.material && bar.lockBlack

    /** What one rendering of the bar painted. */
    data class Painted(val background: Int, val backgroundAlpha: Int, val pill: Int, val accent: Int, val plainIcon: Int)

    private fun render(s: Sliders, gStyle: String): View {
        prefs.edit().clear()
            .putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light")
            .putBoolean("${p}_design_saved", true)
            .putString("${p}_subtheme", "Custom")
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

    private fun View.image(id: Int): ImageView = findViewById(id)
    private fun View.tint(id: Int): Int = image(id).imageTintList!!.defaultColor or OPAQUE

    private fun paint(s: Sliders): Painted {
        val accented = render(s, "Accented G Icon")
        val label = "$bar ${if (dark) "dark" else "light"} $s"
        val accent = accented.tint(R.id.widget_g_logo)
        assertEquals("$label mic takes the accent", accent, accented.tint(R.id.widget_voice_search))
        assertEquals("$label shortcut takes the accent", accent, accented.tint(R.id.widget_lens_search))
        if (bar.material) assertEquals("$label circle icon takes the accent", accent, accented.tint(R.id.widget_sound_icon))
        val pill = accented.tint(R.id.widget_pill_background)
        // The custom colour fills the pill (the rim when the pill is locked); see-through as Color Opacity and
        // Transparency say, applied once.
        val backgroundId = if (locked) R.id.widget_outer_background else R.id.widget_pill_background
        val background = accented.image(backgroundId)
        assertEquals("$label background is the seed", seed(s), background.imageTintList!!.defaultColor or OPAQUE)
        // As it shows: its own alpha within the backdrop, times the backdrop's, which the widget fades at once.
        val shownAlpha = Math.round(background.imageAlpha * accented.findViewById<View>(R.id.widget_backdrop).alpha)
        assertTrue(
            "$label background alpha $shownAlpha, expected ${(s.customAlpha * 255).toInt()}",
            abs(shownAlpha - (s.customAlpha * 255).toInt()) <= 1
        )
        if (bar.material) {
            assertEquals("$label circle shares the pill", pill, accented.tint(R.id.widget_sound_background))
            assertEquals("$label rim is the seed", seed(s), accented.tint(R.id.widget_outer_background))
        }
        // Locked with no colour picked: the theme's own pill (#121212 in dark, its light twin in light).
        if (locked) assertEquals("$label locked pill is the theme's", WidgetColorSlidersTest.themePill(context, dark) or OPAQUE, pill)

        val plain = render(s, "System G Icon")
        val plainIcon = plain.tint(R.id.widget_voice_search)
        assertEquals("$label plain shortcut", plainIcon, plain.tint(R.id.widget_lens_search))
        if (bar.material) assertEquals("$label plain circle icon", plainIcon, plain.tint(R.id.widget_sound_icon))

        // Every icon keeps at least 3:1 against the pill it sits on.
        for ((what, icon) in listOf("accent" to accent, "plain icon" to plainIcon)) {
            val ratio = MaterialYouPaletteHelper.calculateContrastRatio(icon, pill)
            assertTrue("$label $what ${hex(icon)} on ${hex(pill)} has contrast $ratio", ratio >= 3.0)
        }
        return Painted(background.imageTintList!!.defaultColor or OPAQUE, shownAlpha, pill, accent, plainIcon)
    }

    @Test
    fun sliderThemesTheWholeBar() {
        val values = slider.values
        val bars = values.map { paint(Sliders().with(slider, it)) }
        val label = "$bar ${if (dark) "dark" else "light"} $slider over $values"
        val backgrounds = bars.map { Hct.fromInt(it.background) }
        val accents = bars.map { Hct.fromInt(it.accent) }

        when (slider) {
            Slider.HUE -> values.forEachIndexed { i, hue ->
                assertTrue("$label: background hue ${backgrounds[i].hue} for $hue", hueDistance(backgrounds[i].hue, hue.toDouble()) <= 3.0)
                assertTrue("$label: accent hue ${accents[i].hue} for $hue", hueDistance(accents[i].hue, hue.toDouble()) <= 3.0)
            }
            Slider.SATURATION -> {
                assertRising("$label background chroma", backgrounds.map { it.chroma })
                assertRising("$label accent chroma", accents.map { it.chroma })
                val grey = bars[0].accent
                assertTrue("$label: no saturation leaves the accent grey, ${hex(grey)}", (grey shr 16 and 0xFF) == (grey and 0xFF) && (grey shr 8 and 0xFF) == (grey and 0xFF))
            }
            Slider.LIGHTNESS -> {
                assertRising("$label background tone", backgrounds.map { it.tone })
                if (locked) {
                    // The pill stays black, so the icons on it stay as they are.
                    assertEquals("$label accent on the locked pill", 1, bars.map { it.accent }.distinct().size)
                } else {
                    // The icons are lighter than a dark pill and darker than a light one, flipping once on the way up,
                    // and follow the pill's tone on each side of the flip.
                    val iconsLight = bars.map { Hct.fromInt(it.accent).tone > Hct.fromInt(it.pill).tone }
                    val flip = iconsLight.indexOfFirst { !it }
                    assertTrue("$label: light icons on the darkest pill, dark on the lightest: $iconsLight", iconsLight.first() && !iconsLight.last())
                    assertTrue("$label: icons flip once: $iconsLight", iconsLight.drop(flip).none { it })
                    assertRising("$label light icons' tone", accents.take(flip).map { it.tone })
                    assertRising("$label dark icons' tone", accents.drop(flip).map { it.tone })
                    assertEquals("$label: every step changes the plain icons' tone or keeps it at white or black", true,
                        bars.map { Hct.fromInt(it.plainIcon).tone }.zipWithNext().all { (a, b) -> a != b || a < 1.0 || a > 99.0 })
                }
            }
            Slider.COLOR_OPACITY -> {
                assertRising("$label background alpha", bars.map { it.backgroundAlpha.toDouble() })
                assertEquals("$label leaves the icons alone", 1, bars.map { it.accent to it.plainIcon }.distinct().size)
            }
            Slider.TRANSPARENCY -> {
                assertRising("$label background opacity", bars.map { -it.backgroundAlpha.toDouble() })
                assertEquals("$label leaves the icons alone", 1, bars.map { it.accent to it.plainIcon }.distinct().size)
            }
        }
    }

    private fun assertRising(what: String, values: List<Double>) {
        values.zipWithNext().forEach { (a, b) -> assertTrue("$what should rise: $values", b > a) }
    }

    companion object {
        private const val OPAQUE = 0xFF000000.toInt()

        /** The seed the sliders pick: HCT hue, the saturation's share of the most chroma that hue has at that tone, tone. */
        fun seed(s: Sliders): Int {
            val most = Hct.from(s.hue.toDouble(), 200.0, s.lightness.toDouble()).chroma
            return Hct.from(s.hue.toDouble(), s.saturation / 100.0 * most, s.lightness.toDouble()).toInt() or OPAQUE
        }

        fun hueDistance(a: Double, b: Double): Double = abs((a - b) % 360.0).let { minOf(it, 360.0 - it) }

        fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1} dark={2}")
        fun parameters(): List<Array<Any>> = Bar.entries.filter { it.isCustom }.flatMap { bar ->
            Slider.entries.flatMap { slider -> listOf(arrayOf<Any>(bar, slider, false), arrayOf<Any>(bar, slider, true)) }
        }
    }
}
