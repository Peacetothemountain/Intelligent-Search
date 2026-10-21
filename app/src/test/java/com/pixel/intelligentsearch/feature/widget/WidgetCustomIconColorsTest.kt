package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.feature.widget.WidgetColorSlidersTest.Bar
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * The Custom G style's own colours for the G, the mic, each shortcut and the circle's icon. On a light Material pill
 * that isn't locked black they're drawn darker for contrast, as the editor's preview shows them; everywhere else
 * they're drawn as picked.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetCustomIconColorsTest(private val bar: Bar, private val dark: Boolean, private val sync: Boolean, private val lightCustom: Boolean) {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_custom_icon_colors_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private val p get() = if (bar.material) "widget_material" else "widget_system"

    private val gColor = 0xFFFFD54F.toInt()
    private val micColor = 0xFF81D4FA.toInt()
    private val shortcutColor = 0xFFA5D6A7.toInt()
    private val actionColor = 0xFFF48FB1.toInt()
    // A light custom colour makes a light pill, a dark one a dark pill.
    private val lightness = if (lightCustom) 90 else 30

    private fun render(): View {
        prefs.edit().clear()
            .putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light")
            .putBoolean("${p}_design_saved", true)
            .putString("${p}_subtheme", bar.subtheme)
            .putString("${p}_material_g_icon", "Custom G Icon")
            .putBoolean("${p}_custom_color_hct", true)
            .putInt("${p}_custom_hue", 200)
            .putInt("${p}_custom_saturation", 60)
            .putInt("${p}_custom_lightness", lightness)
            .putBoolean("${p}_lock_black", bar.lockBlack)
            .putBoolean("${p}_custom_sync_colors", sync)
            .putInt("${p}_custom_g_icon_color", gColor)
            .putInt("${p}_custom_mic_color", micColor)
            .putInt("${p}_custom_shortcut1_color", shortcutColor)
            .putInt("${p}_custom_action_icon_color", actionColor)
            .putString("${p}_action_icon", "Now Playing")
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

    private val pillIsLight: Boolean
        get() {
            val customIsLight = WidgetColorSlidersTest.iconsGoDarkOn(WidgetColorSlidersTest.colorAtShare(200.0, 0.6, lightness.toDouble()))
            return when {
                bar.material && bar.lockBlack -> false
                bar.isCustom -> customIsLight
                bar.subtheme == "Light" -> true
                bar.subtheme == "Dark" -> false
                else -> !dark
            }
        }

    private fun drawn(color: Int): Int =
        if (bar.material && !bar.lockBlack && pillIsLight) MaterialYouPaletteHelper.darkenForContrast(color, 32.0) else color

    private fun View.tint(id: Int): Int = findViewById<ImageView>(id).imageTintList!!.defaultColor

    @Test
    fun customIconColours() {
        val view = render()
        val label = "$bar ${if (dark) "dark" else "light"} sync=$sync lightCustom=$lightCustom (pill light: $pillIsLight)"
        fun picked(own: Int) = if (sync) gColor else own
        assertEquals("$label G", drawn(gColor), view.tint(R.id.widget_g_logo))
        assertEquals("$label mic", drawn(picked(micColor)), view.tint(R.id.widget_voice_search))
        assertEquals("$label shortcut", drawn(picked(shortcutColor)), view.tint(R.id.widget_lens_search))
        if (bar.material) assertEquals("$label circle icon", drawn(picked(actionColor)), view.tint(R.id.widget_sound_icon))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1} sync={2} lightCustom={3}")
        fun parameters(): List<Array<Any>> = Bar.entries.flatMap { bar ->
            listOf(false, true).flatMap { dark ->
                listOf(false, true).flatMap { sync ->
                    // The custom colour's shade only matters to the Custom subtheme.
                    (if (bar.isCustom) listOf(true, false) else listOf(true)).map { arrayOf<Any>(bar, dark, sync, it) }
                }
            }
        }
    }
}
