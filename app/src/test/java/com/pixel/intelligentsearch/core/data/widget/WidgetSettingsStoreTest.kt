package com.pixel.intelligentsearch.core.data.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

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
        assertEquals(shown(defaults.hue, defaults.saturation), system.seed)
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
            .putString("widget_shortcut_1", "Weather")
            .putInt("widget_custom_mic_color", 0xFF00FF00.toInt())
            .commit()
        val unsaved = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals("System", unsaved.subtheme)
        assertEquals("Google Lens", unsaved.shortcut1)
        assertFalse(store.isSaved(WidgetDesign.SYSTEM))
        // Element colors always fell back to the shared key.
        assertEquals(0xFF00FF00.toInt(), unsaved.elementColors.mic)

        prefs.edit().putBoolean("widget_system_design_saved", true).commit()
        val saved = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals("Dark", saved.subtheme)
        assertEquals("Weather", saved.shortcut1)
    }

    @Test
    fun anOlderBuildsDesignKeysCountAsSaved() {
        // Older builds never wrote the saved flag; the widget honoured their values all the same.
        prefs.edit().putString("widget_subtheme", "Dark").putInt("widget_custom_hue", 120).putString("widget_shortcut_1", "Weather").commit()
        assertTrue(store.isSaved(WidgetDesign.SYSTEM))
        val system = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals("Dark", system.subtheme)
        assertEquals(shown(120, defaults.saturation), system.seed)
        assertEquals("Weather", system.shortcut1)
        // The shared hue says nothing about the Material design; its own keys do.
        assertFalse(store.isSaved(WidgetDesign.MATERIAL))
        prefs.edit().putBoolean("widget_material_lock_black", false).commit()
        assertTrue(store.isSaved(WidgetDesign.MATERIAL))
        assertEquals("Dark", store.load(WidgetDesign.MATERIAL, defaults).subtheme)
    }

    @Test
    fun theOldAllShortcutsColourIsTheFallbackForSlots() {
        prefs.edit()
            .putInt("widget_custom_shortcuts_color", 0xFF123456.toInt())
            .putInt("widget_custom_shortcut2_color", 0xFF654321.toInt())
            .putInt("widget_custom_shortcut3_color", 0xFF4285F4.toInt()) // placeholder
            .commit()
        val loaded = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals(0xFF123456.toInt(), loaded.elementColors.mic)
        assertEquals(0xFF123456.toInt(), loaded.elementColors.shortcut1)
        assertEquals(0xFF654321.toInt(), loaded.elementColors.shortcut2)
        assertEquals(0xFF123456.toInt(), loaded.elementColors.shortcut3)
        assertEquals(primary, loaded.elementColors.gIcon)
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
        assertEquals(shown(defaults.hue, defaults.saturation), loaded.seed)
        assertEquals(primary, loaded.elementColors.gIcon)

        // Stored for the design itself, the same values are real choices.
        prefs.edit().putInt("widget_system_custom_hue", 277).putInt("widget_system_custom_g_icon_color", 0xFF4285F4.toInt()).commit()
        val explicit = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals(shown(277, defaults.saturation), explicit.seed)
        assertEquals(0xFF4285F4.toInt(), explicit.elementColors.gIcon)
    }

    @Test
    fun anOlderVersionsHsvColourIsReadAsTheSameColour() {
        // Older versions read Hue, Saturation and Lightness as HSV; this one reads them as an HCT seed.
        prefs.edit()
            .putBoolean("widget_material_design_saved", true)
            .putInt("widget_material_custom_hue", 200)
            .putInt("widget_material_custom_saturation", 60)
            .putInt("widget_material_custom_lightness", 90)
            .commit()
        val old = Color.HSVToColor(floatArrayOf(200f, 0.6f, 0.9f))
        val loaded = store.load(WidgetDesign.MATERIAL, defaults)
        assertEquals(shown(200, 60, 90), loaded.seed)
        val now = loaded.customColorInt
        for (shift in listOf(16, 8, 0)) {
            val diff = abs((old shr shift and 0xFF) - (now shr shift and 0xFF))
            assertTrue("#%06X shows as #%06X".format(old and 0xFFFFFF, now and 0xFFFFFF), diff <= 3)
        }

        // Saved by this version, the values are the seed itself, and stay as they are.
        val editor = prefs.edit()
        store.save(WidgetDesign.MATERIAL, loaded.copy(hue = 200f, saturation = 60f, lightness = 90f), editor)
        editor.commit()
        assertTrue(prefs.getBoolean(WidgetSettingsStore.hctFlagKey(WidgetDesign.MATERIAL), false))
        assertEquals(MaterialYouPaletteHelper.SeedSliders(200f, 60f, 90f), store.load(WidgetDesign.MATERIAL, defaults).seed)
        // The other design still has its old values, read as before.
        assertFalse(prefs.contains(WidgetSettingsStore.hctFlagKey(WidgetDesign.SYSTEM)))
    }

    /** The slider values an older version's HSV hue, saturation and value are read as: the color they showed. */
    private fun shown(hue: Int, saturation: Int, value: Int = 100) = MaterialYouPaletteHelper.wholeSeedSliders(
        Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation / 100f, value / 100f)), greyHue = hue.toFloat()
    )

    private val WidgetDesignSettings.seed get() = MaterialYouPaletteHelper.SeedSliders(hue, saturation, lightness)

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

    @Test
    fun doodlesAreOnByDefaultAndSavedPerDesign() {
        assertTrue(store.load(WidgetDesign.SYSTEM, defaults).showDoodles)
        assertTrue(store.showsDoodle(WidgetDesign.SYSTEM))
        assertTrue(store.showsDoodle(WidgetDesign.MATERIAL))

        val material = store.load(WidgetDesign.MATERIAL, defaults).copy(showDoodles = false)
        val editor = prefs.edit()
        store.save(WidgetDesign.MATERIAL, material, editor)
        editor.commit()

        assertFalse(store.load(WidgetDesign.MATERIAL, defaults).showDoodles)
        assertFalse(store.showsDoodle(WidgetDesign.MATERIAL))
        assertTrue(store.showsDoodle(WidgetDesign.SYSTEM))
    }

    @Test
    fun noDoodleWhileTheGIsHidden() {
        val system = store.load(WidgetDesign.SYSTEM, defaults).copy(showGIcon = false)
        val editor = prefs.edit()
        store.save(WidgetDesign.SYSTEM, system, editor)
        editor.commit()

        assertTrue(store.load(WidgetDesign.SYSTEM, defaults).showDoodles)
        assertFalse(store.showsDoodle(WidgetDesign.SYSTEM))
    }

    @Test
    fun theOutlineAndCornersDefaultToTheWidgetsOwnLook() {
        val system = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals(0, system.outlineThickness)
        assertEquals(28, system.cornerRadius)

        val material = store.load(WidgetDesign.MATERIAL, defaults)
        assertEquals(8, material.outlineThickness)
        assertEquals(32, material.cornerRadius)
    }

    @Test
    fun theOutlineAndCornersAreSavedPerDesign() {
        val system = store.load(WidgetDesign.SYSTEM, defaults).copy(
            outlineThickness = 4,
            cornerRadius = 10
        )
        val editor = prefs.edit()
        store.save(WidgetDesign.SYSTEM, system, editor)
        editor.commit()

        val loaded = store.load(WidgetDesign.SYSTEM, defaults)
        assertEquals(4, loaded.outlineThickness)
        assertEquals(10, loaded.cornerRadius)
        // The other design keeps its own.
        assertEquals(8, store.load(WidgetDesign.MATERIAL, defaults).outlineThickness)
        assertEquals(32, store.load(WidgetDesign.MATERIAL, defaults).cornerRadius)
    }

    @Test
    fun anOutlineOrCornerOutOfRangeIsReadWithinIt() {
        prefs.edit()
            .putInt("widget_material_outline_thickness", 99)
            .putInt("widget_material_corner_radius", -5)
            .commit()
        val material = store.load(WidgetDesign.MATERIAL, defaults)
        assertEquals(MAX_OUTLINE_THICKNESS, material.outlineThickness)
        assertEquals(0, material.cornerRadius)
    }
}
