package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.DEFAULT_LOCKED_PILL_COLOR
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Material design's Inner Pill & Circle forces the pill and circle to one color. With none picked it is the
 * theme's own pill: in the dark theme #121212, drawing the bar exactly as the old lock to black did (a dark pill,
 * icons as they are), and in the light theme its light twin, a light pill with the light theme's rim and darkened
 * icons. A color picked for it is drawn exactly as picked in either theme: a light one makes a light pill, with its
 * icons darkened for contrast as on the theme's own light pill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetLockedPillColorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_locked_pill_color_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }

    private val cream = 0xFFF5E6C8.toInt()
    private val navy = 0xFF1E3A5F.toInt()

    // ---- The rules ----

    /** The widget colors of the dark and the light theme. */
    private val darkTheme get() = DynamicThemeDetector.resolveWidgetColors(context, true)
    private val lightTheme get() = DynamicThemeDetector.resolveWidgetColors(context, false)

    /** The Material design's defaults, Inner Pill & Circle on. */
    private val locked: WidgetDesignSettings by lazy {
        val fresh = context.getSharedPreferences("widget_locked_pill_color_rules", Context.MODE_PRIVATE)
        fresh.edit().clear().commit()
        WidgetSettingsStore(fresh).load(WidgetDesign.MATERIAL, WidgetDefaults(primaryColor = 0xFF6750A4.toInt(), isDynamicColorActive = false))
            .copy(lockBlack = true)
    }

    @Test
    fun theDefaultLockIsDarkInTheDarkTheme() {
        assertEquals(DEFAULT_LOCKED_PILL_COLOR, WidgetColorRules.LOCKED_PILL)
        assertEquals(DEFAULT_LOCKED_PILL_COLOR, darkTheme.lockedPill)
        assertEquals(WidgetColorRules.LOCKED_PILL, WidgetColorRules.lockedPill(locked, darkTheme))
        assertTrue(WidgetColorRules.locksDark(locked, darkTheme))
        assertFalse(WidgetColorRules.locksLight(locked, darkTheme))
    }

    /** In the light theme the default lock is the light palette's surfaceBright: a light pill, never #121212. */
    @Test
    fun theDefaultLockIsTheLightThemesOwnPillInTheLightTheme() {
        val lightPill = androidx.compose.material3.dynamicLightColorScheme(context).surfaceBright
        assertEquals(hex(lightPill.toArgb()), hex(lightTheme.lockedPill))
        assertEquals(hex(lightTheme.lockedPill), hex(WidgetColorRules.lockedPill(locked, lightTheme)))
        assertTrue(WidgetColorRules.locksLight(locked, lightTheme))
        assertFalse(WidgetColorRules.locksDark(locked, lightTheme))
        assertTrue(WidgetColorRules.iconsGoDark(lightTheme.lockedPill))
    }

    @Test
    fun aLightColorLocksLightAndADarkOneDarkInEitherTheme() {
        for (theme in listOf(darkTheme, lightTheme)) {
            assertEquals(cream, WidgetColorRules.lockedPill(locked.copy(lockedPillColor = cream), theme))
            assertEquals(navy, WidgetColorRules.lockedPill(locked.copy(lockedPillColor = navy), theme))
            assertTrue(WidgetColorRules.locksLight(locked.copy(lockedPillColor = cream), theme))
            assertFalse(WidgetColorRules.locksDark(locked.copy(lockedPillColor = cream), theme))
            assertTrue(WidgetColorRules.locksDark(locked.copy(lockedPillColor = navy), theme))
            assertFalse(WidgetColorRules.locksLight(locked.copy(lockedPillColor = navy), theme))
        }
    }

    @Test
    fun unlockedNeitherLocksDarkNorLight() {
        for (theme in listOf(darkTheme, lightTheme)) for (color in listOf(DEFAULT_LOCKED_PILL_COLOR, cream, navy)) {
            val settings = locked.copy(lockBlack = false, lockedPillColor = color)
            assertFalse(WidgetColorRules.locksDark(settings, theme))
            assertFalse(WidgetColorRules.locksLight(settings, theme))
        }
    }

    @Test
    fun theLockedPillIsOpaque() {
        for (theme in listOf(darkTheme, lightTheme)) {
            assertEquals(cream, WidgetColorRules.lockedPill(locked.copy(lockedPillColor = cream and 0x00FFFFFF), theme))
            assertEquals(0xFF, WidgetColorRules.lockedPill(locked, theme) ushr 24)
        }
    }

    /** The Custom G style's own colors: as picked on the default lock, darkened on a light one. */
    @Test
    fun customIconsAreDarkenedOnlyOnALightLock() {
        val icons = WidgetElementColors(0xFFFFD54F.toInt(), 0xFF81D4FA.toInt(), 0xFFA5D6A7.toInt(), 0, 0, 0xFFF48FB1.toInt())
        val dark = WidgetColorRules.customIconColors(locked.copy(elementColors = icons), true, isPillLight = false, theme = darkTheme)
        assertEquals(icons.gIcon, dark.gIcon)
        assertEquals(icons.mic, dark.mic)

        val light = WidgetColorRules.customIconColors(locked.copy(elementColors = icons, lockedPillColor = cream), true, isPillLight = true, theme = darkTheme)
        assertEquals(MaterialYouPaletteHelper.darkenForContrast(icons.gIcon, 32.0), light.gIcon)
        assertEquals(MaterialYouPaletteHelper.darkenForContrast(icons.mic, 32.0), light.mic)
        assertNotEquals(icons.gIcon, light.gIcon)

        // The light theme's own pill is a light lock too: its icons are darkened the same way.
        val themeLight = WidgetColorRules.customIconColors(locked.copy(elementColors = icons), true, isPillLight = true, theme = lightTheme)
        assertEquals(MaterialYouPaletteHelper.darkenForContrast(icons.gIcon, 32.0), themeLight.gIcon)
    }

    /** The Custom subtheme's bar takes its icon side from the locked color it stands on. */
    @Test
    fun theCustomBarReadsItsIconsOffTheLockedColor() {
        val custom = locked.copy(subtheme = "Custom")
        assertFalse(WidgetColorRules.customBarColors(custom, isMaterialDesign = true, theme = darkTheme).darkIcons)
        assertTrue(WidgetColorRules.customBarColors(custom.copy(lockedPillColor = cream), isMaterialDesign = true, theme = darkTheme).darkIcons)
        // With no color picked, the light theme's own light pill.
        assertTrue(WidgetColorRules.customBarColors(custom, isMaterialDesign = true, theme = lightTheme).darkIcons)
        assertEquals(lightTheme.lockedPill, WidgetColorRules.customBarColors(custom, isMaterialDesign = true, theme = lightTheme).background)
    }

    // ---- The widget ----

    private fun render(appDark: Boolean, lockedColor: Int?, material: Boolean = true): View {
        val p = if (material) "widget_material" else "widget_system"
        val editor = prefs.edit().clear()
            .putString(SettingsManager.THEME.name, if (appDark) "Material Dark" else "Material Light")
            .putBoolean("${p}_design_saved", true)
            .putString("${p}_subtheme", if (material) "Material" else "System")
            .putString("${p}_material_g_icon", "Accented G Icon")
            .putBoolean("${p}_lock_black", true)
            .putString("${p}_action_icon", "Search")
            .putBoolean("${p}_show_doodles", false)
            .putBoolean("${p}_show_voice", true)
            .putString("${p}_shortcut_order", "mic,shortcut1,shortcut2,shortcut3")
            .putString("${p}_shortcut_1", "Weather")
            .putString("${p}_shortcut_2", "None")
            .putString("${p}_shortcut_3", "None")
        if (lockedColor != null) editor.putInt("${p}_lock_pill_color", lockedColor)
        editor.commit()
        return SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = material, prefs = prefs)
            .getValue(SearchWidgetProvider.EXPANDED_WIDTH)
            .apply(activity, FrameLayout(activity))
    }

    private fun View.tint(id: Int): Int = findViewById<ImageView>(id).imageTintList!!.defaultColor
    private fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)

    /** The theme's accent as the widget's icons are drawn in it for a [dark] pill. */
    private fun accent(dark: Boolean) = DynamicThemeDetector.resolveWidgetColors(context, dark).primary

    @Test
    fun theDefaultLockDrawsTheDarkPillAsBeforeInTheDarkTheme() {
        for (stored in listOf(null, DEFAULT_LOCKED_PILL_COLOR)) {
            val view = render(appDark = true, stored)
            val label = "stored=${stored?.let(::hex)}"
            assertEquals("$label pill", "#121212", hex(view.tint(R.id.widget_pill_background)))
            assertEquals("$label circle", "#121212", hex(view.tint(R.id.widget_sound_background)))
            // The dark theme's accent, as it is: never darkened on the dark pill.
            assertEquals("$label G", hex(accent(dark = true)), hex(view.tint(R.id.widget_g_logo)))
            assertEquals("$label mic", hex(accent(dark = true)), hex(view.tint(R.id.widget_voice_search)))
            assertEquals("$label rim", hex(darkTheme.primaryContainer), hex(view.tint(R.id.widget_outer_background)))
        }
    }

    /**
     * In the light theme the default lock is a proper light widget: the light theme's own pill and circle, its
     * primaryContainer rim, and icons darkened for contrast on it. It used to stay the dark theme's near-black pill.
     */
    @Test
    fun theDefaultLockDrawsTheLightThemesPillInTheLightTheme() {
        for (stored in listOf(null, DEFAULT_LOCKED_PILL_COLOR)) {
            val view = render(appDark = false, stored)
            val label = "stored=${stored?.let(::hex)}"
            assertEquals("$label pill", hex(lightTheme.lockedPill), hex(view.tint(R.id.widget_pill_background)))
            assertEquals("$label circle", hex(lightTheme.lockedPill), hex(view.tint(R.id.widget_sound_background)))
            assertEquals("$label rim", hex(lightTheme.primaryContainer), hex(view.tint(R.id.widget_outer_background)))
            val darkened = MaterialYouPaletteHelper.darkenForContrast(accent(dark = false), 32.0)
            assertEquals("$label G", hex(darkened), hex(view.tint(R.id.widget_g_logo)))
            assertEquals("$label circle icon", hex(darkened), hex(view.tint(R.id.widget_sound_icon)))
            // Readable: the icons keep at least Material's 4.5:1 on the light pill.
            val ratio = MaterialYouPaletteHelper.calculateContrastRatio(view.tint(R.id.widget_g_logo), lightTheme.lockedPill)
            assertTrue("$label icon contrast $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun aLightLockDrawsALightPillWithDarkenedIcons() {
        for (appDark in listOf(false, true)) {
            val view = render(appDark, cream)
            val label = "app dark=$appDark"
            assertEquals("$label pill", hex(cream), hex(view.tint(R.id.widget_pill_background)))
            assertEquals("$label circle", hex(cream), hex(view.tint(R.id.widget_sound_background)))
            val darkened = MaterialYouPaletteHelper.darkenForContrast(accent(dark = false), 32.0)
            assertEquals("$label G", hex(darkened), hex(view.tint(R.id.widget_g_logo)))
            assertEquals("$label circle icon", hex(darkened), hex(view.tint(R.id.widget_sound_icon)))
        }
    }

    @Test
    fun aDarkLockDrawsLikeTheDefault() {
        for (appDark in listOf(false, true)) {
            val view = render(appDark, navy)
            assertEquals(hex(navy), hex(view.tint(R.id.widget_pill_background)))
            assertEquals(hex(accent(dark = true)), hex(view.tint(R.id.widget_g_logo)))
        }
    }

    /** The System design has no Inner Pill & Circle: a color stored under its prefix changes nothing. */
    @Test
    fun theSystemDesignIgnoresALockedColor() {
        val plain = render(appDark = false, lockedColor = null, material = false).tint(R.id.widget_pill_background)
        val withColor = render(appDark = false, lockedColor = cream, material = false).tint(R.id.widget_pill_background)
        assertEquals(hex(plain), hex(withColor))
        assertNotEquals(hex(cream), hex(withColor))
    }
}
