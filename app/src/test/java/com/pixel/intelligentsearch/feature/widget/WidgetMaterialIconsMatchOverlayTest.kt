package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.OverlayIconRecipe
import com.pixel.intelligentsearch.core.theme.SearchIconColors
import com.pixel.intelligentsearch.feature.settings.PreviewTheme
import com.pixel.intelligentsearch.feature.settings.WidgetPreviewColors
import com.pixel.intelligentsearch.feature.settings.widgetPreviewColors
import com.pixel.intelligentsearch.feature.widget.WidgetColorSlidersTest.Bar
import com.pixel.intelligentsearch.feature.widget.WidgetColorSlidersTest.Sliders
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget's Material icons (the Material G style's G, mic, shortcuts and circle button) are the search overlay's
 * icons in the overlay's colours ([OverlayIconRecipe]): the lens and the sparkle mic drawn as the overlay draws them,
 * every part in the colour the overlay gives it, for each design, subtheme and inner pill state, in light and dark,
 * picking the overlay's light or dark colours by the pill's lightness. The Custom subtheme's icons are the overlay's
 * as a wallpaper of the custom colour's hue would theme it. The editor's preview works out the same colours.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetMaterialIconsMatchOverlayTest(private val bar: Bar, private val dark: Boolean) {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_material_icons_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private val p get() = if (bar.material) "widget_material" else "widget_system"

    private fun render(s: Sliders): View {
        clearBitmapCache()
        prefs.edit().clear()
            .putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light")
            .putBoolean("${p}_design_saved", true)
            .putString("${p}_subtheme", bar.subtheme)
            .putString("${p}_material_g_icon", "Material G Icon")
            .putBoolean("${p}_custom_color_hct", true)
            .putInt("${p}_custom_hue", s.hue)
            .putInt("${p}_custom_saturation", s.saturation)
            .putInt("${p}_custom_lightness", s.lightness)
            .putInt("${p}_custom_color_opacity", s.opacity)
            .putInt("${p}_background_transparency", s.transparency)
            .putBoolean("${p}_lock_black", bar.lockBlack)
            .putString("${p}_action_icon", "Search")
            .putBoolean("${p}_show_doodles", false)
            .putBoolean("${p}_show_g_icon", true)
            .putBoolean("${p}_show_voice", true)
            .putString("${p}_shortcut_order", "mic,shortcut1,shortcut2,shortcut3")
            .putString("${p}_shortcut_1", "Live")
            .putString("${p}_shortcut_2", "Google Lens")
            .putString("${p}_shortcut_3", "None")
            .commit()
        return SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = bar.material, prefs = prefs)
            .getValue(SearchWidgetProvider.EXPANDED_WIDTH)
            .apply(activity, FrameLayout(activity))
    }

    /** Whether the pill counts as light, which picks the overlay's light or dark colours. */
    private fun pillIsLight(s: Sliders): Boolean = when {
        bar.material && bar.lockBlack -> false
        bar.isCustom -> WidgetColorSlidersTest.iconsGoDarkOn(s.customColor)
        bar.subtheme == "Light" -> true
        bar.subtheme == "Dark" -> false
        else -> !dark
    }

    private fun overlayColors(s: Sliders): OverlayIconRecipe.Colors {
        val light = pillIsLight(s)
        return if (bar.isCustom) OverlayIconRecipe.seeded(s.hue.toFloat(), s.saturation.toFloat(), light)
        else OverlayIconRecipe.theme(context, light)
    }

    private fun View.bitmap(id: Int): Bitmap = (findViewById<ImageView>(id).drawable as BitmapDrawable).bitmap

    @Test
    fun materialIconsAreTheOverlays() {
        val sliders = if (bar.isCustom) {
            listOf(Sliders(), Sliders(lightness = 20), Sliders(hue = 30, saturation = 100, lightness = 55), Sliders(hue = 300, saturation = 0, lightness = 70))
        } else listOf(Sliders())
        for (s in sliders) {
            val widget = render(s)
            val colors = overlayColors(s)
            val label = "$bar ${if (dark) "dark" else "light"} $s"
            OverlayIconRecipe.assertDrawn("$label G", widget.bitmap(R.id.widget_g_logo), OverlayIconRecipe.g(colors))
            OverlayIconRecipe.assertDrawn("$label mic", widget.bitmap(R.id.widget_voice_search), OverlayIconRecipe.mic(colors))
            OverlayIconRecipe.assertDrawn("$label Live shortcut", widget.bitmap(R.id.widget_lens_search), OverlayIconRecipe.lens(colors))
            OverlayIconRecipe.assertDrawn("$label Lens shortcut", widget.bitmap(R.id.widget_shortcut_2), OverlayIconRecipe.camera(colors), across = 100f)
            if (bar.material) {
                OverlayIconRecipe.assertDrawn("$label circle button", widget.bitmap(R.id.widget_sound_icon), OverlayIconRecipe.lens(colors))
            }

            // The editor's preview draws its Material icons (see WidgetPreviewIconsDrawingTest) from the same colours.
            val preview = preview()
            assertEquals("$label preview pill counts as light", pillIsLight(s), preview.pill.isLight)
            val a = preview.materialIconAccents
            assertEquals("$label preview icon colours", colors, OverlayIconRecipe.colors(a.primary, a.secondary, a.tertiary, preview.pill.isLight))
        }
    }

    /** The editor's live preview of the settings [render] saved, drawn in the theme the widget follows. */
    private fun preview(): WidgetPreviewColors {
        val theme = DynamicThemeDetector.resolveWidgetColors(context, dark)
        val dynamic = DynamicThemeDetector.isDynamicColorActive(context)
        val design = if (bar.material) WidgetDesign.MATERIAL else WidgetDesign.SYSTEM
        return widgetPreviewColors(
            draft = WidgetSettingsStore(prefs).load(design, WidgetDefaults(theme.primary, dynamic)),
            isMaterialDesign = bar.material,
            theme = PreviewTheme(
                primary = Color(theme.primary),
                primaryContainer = Color(theme.primaryContainer),
                surfaceContainer = Color(theme.surfaceContainer),
                appDark = dark,
                systemDark = dark
            ),
            isDynamicColorActive = dynamic,
            iconAccents = { SearchIconColors.themeAccents(context, it) }
        ) { DynamicThemeDetector.resolveWidgetColors(context, it) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> = Bar.entries.flatMap { bar -> listOf(arrayOf<Any>(bar, false), arrayOf<Any>(bar, true)) }
    }
}
