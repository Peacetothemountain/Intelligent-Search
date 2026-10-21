package com.pixel.intelligentsearch.feature.settings

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.feature.widget.WidgetColorSlidersTest.Bar
import com.pixel.intelligentsearch.feature.widget.WidgetColorSlidersTest.Sliders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The editor's live preview paints what the widget draws for the same settings: the rim, the pill, the circle, the G
 * and every icon, for each design, subtheme, inner pill state and G style, with the Color Studio sliders at several
 * values. The preview is drawn with the colour scheme of the theme the widget follows.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetPreviewMatchesWidgetTest(private val bar: Bar, private val gStyle: String, private val dark: Boolean) {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_preview_matches_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private val design get() = if (bar.material) WidgetDesign.MATERIAL else WidgetDesign.SYSTEM

    private fun store(s: Sliders) {
        val p = design.prefix
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
            .putInt("${p}_custom_g_icon_color", 0xFFFFD54F.toInt())
            .putInt("${p}_custom_mic_color", 0xFF81D4FA.toInt())
            .putInt("${p}_custom_shortcut1_color", 0xFF2E7D32.toInt())
            .putInt("${p}_custom_action_icon_color", 0xFFF48FB1.toInt())
            .putString("${p}_action_icon", "Assistant")
            .putBoolean("${p}_show_doodles", false)
            .putBoolean("${p}_show_voice", true)
            .putString("${p}_shortcut_order", "mic,shortcut1,shortcut2,shortcut3")
            .putString("${p}_shortcut_1", "Weather")
            .putString("${p}_shortcut_2", "None")
            .putString("${p}_shortcut_3", "None")
            .commit()
    }

    private fun widget(): View =
        SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = bar.material, prefs = prefs)
            .getValue(SearchWidgetProvider.EXPANDED_WIDTH)
            .apply(activity, FrameLayout(activity))

    private fun preview(): WidgetPreviewColors {
        val theme = DynamicThemeDetector.resolveWidgetColors(context, dark)
        val dynamic = DynamicThemeDetector.isDynamicColorActive(context)
        val draft = WidgetSettingsStore(prefs).load(design, WidgetDefaults(theme.primary, dynamic))
        return widgetPreviewColors(
            draft = draft,
            isMaterialDesign = bar.material,
            theme = PreviewTheme(
                primary = Color(theme.primary),
                primaryContainer = Color(theme.primaryContainer),
                surfaceContainer = Color(theme.surfaceContainer),
                appDark = dark,
                systemDark = dark
            ),
            isDynamicColorActive = dynamic
        ) { DynamicThemeDetector.resolveWidgetColors(context, it) }
    }

    private fun hex(argb: Int) = "#%08X".format(argb)

    private fun assertLayer(what: String, preview: Color, widget: View, id: Int) {
        val view = widget.findViewById<ImageView>(id)
        val expected = preview.toArgb()
        assertEquals("$what colour (preview ${hex(expected)})", expected and 0xFFFFFF, view.imageTintList!!.defaultColor and 0xFFFFFF)
        val alpha = expected ushr 24
        assertTrue("$what alpha: preview $alpha, widget ${view.imageAlpha}", abs(alpha - view.imageAlpha) <= 1)
    }

    private fun assertTint(what: String, preview: Color, widget: View, id: Int) {
        assertEquals("$what", hex(preview.toArgb()), hex(widget.findViewById<ImageView>(id).imageTintList!!.defaultColor))
    }

    @Test
    fun previewMatchesWidget() {
        val settings = listOf(
            Sliders(),
            Sliders(lightness = 30, opacity = 100, transparency = 0),
            Sliders(hue = 40, saturation = 20, lightness = 100, opacity = 45, transparency = 35),
            Sliders(hue = 300, saturation = 100, lightness = 60, opacity = 100, transparency = 70)
        )
        for (s in settings) {
            store(s)
            val widget = widget()
            val preview = preview()
            val label = "$bar $gStyle ${if (dark) "dark" else "light"} $s"

            assertLayer("$label pill", preview.pill.fill, widget, R.id.widget_pill_background)
            val mic = Shadows.shadowOf(widget.findViewById<ImageView>(R.id.widget_voice_search).drawable).createdFromResId
            assertEquals(
                "$label mic drawing",
                context.resources.getResourceEntryName(if (preview.usesMaterialYouIcons) R.drawable.ic_mic else R.drawable.ic_mic_original),
                context.resources.getResourceEntryName(mic)
            )
            if (bar.material) {
                assertLayer("$label rim", preview.rim, widget, R.id.widget_outer_background)
                assertLayer("$label circle", preview.pill.fill, widget, R.id.widget_sound_background)
            }
            when (gStyle) {
                "Accented G Icon" -> {
                    assertTint("$label G", preview.accentPrimary, widget, R.id.widget_g_logo)
                    assertTint("$label mic", preview.accentPrimary, widget, R.id.widget_voice_search)
                    assertTint("$label shortcut", preview.accentPrimary, widget, R.id.widget_lens_search)
                    if (bar.material) assertTint("$label circle icon", preview.accentPrimary, widget, R.id.widget_sound_icon)
                }
                "Custom G Icon" -> {
                    assertTint("$label G", Color(preview.customIcons.gIcon), widget, R.id.widget_g_logo)
                    assertTint("$label mic", Color(preview.customIcons.mic), widget, R.id.widget_voice_search)
                    assertTint("$label shortcut", Color(preview.customIcons.shortcut1), widget, R.id.widget_lens_search)
                    if (bar.material) assertTint("$label circle icon", Color(preview.customIcons.action), widget, R.id.widget_sound_icon)
                }
                "System G Icon" -> {
                    assertTint("$label mic", preview.systemIconTint, widget, R.id.widget_voice_search)
                    assertTint("$label shortcut", preview.systemIconTint, widget, R.id.widget_lens_search)
                    if (bar.material) assertTint("$label circle icon", preview.systemIconTint, widget, R.id.widget_sound_icon)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1} dark={2}")
        fun parameters(): List<Array<Any>> = Bar.entries.flatMap { bar ->
            listOf("Accented G Icon", "Custom G Icon", "System G Icon").flatMap { style ->
                listOf(arrayOf<Any>(bar, style, false), arrayOf<Any>(bar, style, true))
            }
        }
    }
}
