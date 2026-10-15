package com.pixel.intelligentsearch.core.data.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The store must reproduce exactly what the widget settings screen used to load, including every fallback to
 * the keys older versions shared between designs.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetSettingsStoreTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var store: WidgetSettingsStore
    private val primary = 0xFF6750A4.toInt() // hue 256, saturation 51
    private val defaults = WidgetDefaults(primaryColor = primary, isDynamicColorActive = true)

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("widget_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = WidgetSettingsStore(prefs)
    }

    @Test
    fun unsavedDesignsUseTheirOwnDefaults() {
        val system = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals("System", system.subtheme)
        assertEquals("Material G Icon", system.gIconTheme) // dynamic colour active
        assertEquals(0f, system.transparency)
        assertEquals("Google Lens", system.shortcut1)
        assertEquals("None", system.shortcut2)
        assertEquals(WidgetSettingsStore.DEFAULT_SLOT_ORDER, system.slotOrder)
        assertTrue(system.showGIcon)
        assertTrue(system.showVoice)
        assertEquals(defaults.hue.toFloat(), system.hue)
        assertEquals(defaults.saturation.toFloat(), system.saturation)
        assertEquals(primary, system.elementColors.gIcon)

        val material = store.load(WidgetDesign.MATERIAL, defaults)
        assertEquals("Material", material.subtheme)
        assertEquals(28f, material.transparency)
        assertTrue(material.lockBlack)
        assertEquals("Search", material.actionIcon)

        val noDynamic = store.load(WidgetDesign.SYSTEM, defaults.copy(isDynamicColorActive = false))
        assertEquals("System G Icon", noDynamic.gIconTheme)
    }

    @Test
    fun legacySharedKeysCountOnlyOnceTheDesignWasSaved() {
        prefs.edit()
            .putString("widget_subtheme", "Dark")
            .putInt("widget_custom_hue", 120)
            .putString("widget_shortcut_1", "Weather")
            .putInt("widget_custom_mic_color", 0xFF00FF00.toInt())
            .commit()
        val unsaved = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals("System", unsaved.subtheme)
        assertEquals(defaults.hue.toFloat(), unsaved.hue)
        assertEquals("Google Lens", unsaved.shortcut1)
        // Element colours always fell back to the shared key.
        assertEquals(0xFF00FF00.toInt(), unsaved.elementColors.mic)

        prefs.edit().putBoolean("widget_system_design_saved", true).commit()
        val saved = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals("Dark", saved.subtheme)
        assertEquals(120f, saved.hue)
        assertEquals("Weather", saved.shortcut1)
    }

    @Test
    fun oldPlaceholdersMeanTheThemeColour() {
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putInt("widget_custom_hue", 277)
            .putInt("widget_custom_saturation", 51)
            .putInt("widget_custom_g_icon_color", 0xFF4285F4.toInt())
            .commit()
        val loaded = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals(defaults.hue.toFloat(), loaded.hue)
        assertEquals(defaults.saturation.toFloat(), loaded.saturation)
        assertEquals(primary, loaded.elementColors.gIcon)

        // Stored for the design itself, the same values are real choices.
        prefs.edit().putInt("widget_system_custom_hue", 277).putInt("widget_system_custom_g_icon_color", 0xFF4285F4.toInt()).commit()
        val explicit = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals(277f, explicit.hue)
        assertEquals(0xFF4285F4.toInt(), explicit.elementColors.gIcon)
    }

    @Test
    fun voiceSearchShortcutsBecomeNoneAndShowVoiceFollowsTheOldSetting() {
        prefs.edit()
            .putBoolean("widget_material_design_saved", true)
            .putString("widget_material_shortcut_2", "Voice Search")
            .putBoolean("widget_show_voice", false)
            .commit()
        val loaded = store.load(WidgetDesign.MATERIAL, defaults)
        assertEquals("None", loaded.shortcut2)
        assertFalse(loaded.showVoice)
    }

    @Test
    fun savedSettingsLoadBackUnchanged() {
        val edited = store.load(WidgetDesign.MATERIAL, defaults).copy(
            subtheme = "Custom",
            gIconTheme = "Custom G Icon",
            elementColors = WidgetElementColors(1, 2, 3, 4, 5, 6),
            syncColors = true,
            keepSystemG = true,
            hue = 200f,
            saturation = 40f,
            lightness = 70f,
            colorOpacity = 90f,
            transparency = 10f,
            showGIcon = false,
            showVoice = false,
            shortcut1 = "News",
            shortcut2 = "Finance",
            shortcut3 = "Saved",
            slotOrder = "mic,shortcut1,shortcut2,shortcut3",
            lockBlack = false,
            actionIcon = "Lens"
        )
        val editor = prefs.edit()
        store.save(WidgetDesign.MATERIAL, edited, editor)
        editor.commit()
        assertTrue(store.isSaved(WidgetDesign.MATERIAL))
        assertEquals(edited, store.load(WidgetDesign.MATERIAL, defaults))
        assertEquals(edited.customColorInt, prefs.getInt("widget_material_custom_color_int", 0))
        assertFalse(store.isSaved(WidgetDesign.SYSTEM))
    }
}
