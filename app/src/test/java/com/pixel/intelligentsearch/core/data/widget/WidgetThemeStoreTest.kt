package com.pixel.intelligentsearch.core.data.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Named widget themes keep every option of the design they were saved from (the extras, outline and corners
 * included), come back exactly, and can be renamed and deleted without touching another design's themes.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetThemeStoreTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var themes: WidgetThemeStore
    private val defaults = WidgetDefaults(primaryColor = 0xFF6750A4.toInt(), isDynamicColorActive = true)

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("widget_themes_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        themes = WidgetThemeStore(prefs)
    }

    /** A design far from the defaults in every field. */
    private fun custom(design: WidgetDesign): WidgetDesignSettings = WidgetSettingsStore(prefs).load(design, defaults).copy(
        subtheme = "Custom",
        gIconTheme = "Custom G Icon",
        elementColors = WidgetElementColors(0xFF112233.toInt(), 0xFF223344.toInt(), 0xFF334455.toInt(), 0xFF445566.toInt(), 0xFF556677.toInt(), 0xFF667788.toInt()),
        syncColors = true,
        keepSystemG = true,
        hue = 123f,
        saturation = 45f,
        lightness = 67f,
        colorOpacity = 89f,
        transparency = 12f,
        showGIcon = false,
        showVoice = false,
        shortcut1 = "Weather",
        shortcut2 = "Calculator",
        shortcut3 = "News",
        slotOrder = "mic,shortcut3,shortcut2,shortcut1",
        lockBlack = design != WidgetDesign.MATERIAL,
        actionIcon = if (design == WidgetDesign.MATERIAL) "Now Playing" else "Search",
        showDoodles = false,
        favouriteApps = listOf("com.example.mail", "com.example.maps"),
        showMedia = true,
        outlineThickness = 4,
        cornerRadius = 12
    )

    @Test
    fun aSavedThemeComesBackExactly() {
        for (design in WidgetDesign.entries) {
            val settings = custom(design)
            themes.save("Ocean", design, settings)
            val saved = themes.list(defaults).single { it.design == design }
            assertEquals("Ocean", saved.name)
            assertEquals(design.name, settings, saved.settings)
        }
        assertTrue(prefs.getString(SettingsManager.WIDGET_SAVED_THEMES.name, "")!!.contains("Ocean"))
    }

    @Test
    fun savingUnderTheSameNameReplacesThatThemeOnly() {
        themes.save("Mine", WidgetDesign.SYSTEM, custom(WidgetDesign.SYSTEM))
        themes.save("Mine", WidgetDesign.MATERIAL, custom(WidgetDesign.MATERIAL))
        themes.save("Mine", WidgetDesign.SYSTEM, custom(WidgetDesign.SYSTEM).copy(cornerRadius = 0))
        val list = themes.list(defaults)
        assertEquals(2, list.size)
        assertEquals(0, list.single { it.design == WidgetDesign.SYSTEM }.settings.cornerRadius)
    }

    @Test
    fun renameRefusesATakenOrBlankName() {
        themes.save("One", WidgetDesign.SYSTEM, custom(WidgetDesign.SYSTEM))
        themes.save("Two", WidgetDesign.SYSTEM, custom(WidgetDesign.SYSTEM))
        assertFalse(themes.rename("One", WidgetDesign.SYSTEM, "Two"))
        assertFalse(themes.rename("One", WidgetDesign.SYSTEM, "   "))
        assertTrue(themes.rename("One", WidgetDesign.SYSTEM, " Sunset "))
        assertEquals(listOf("Sunset", "Two"), themes.list(defaults).map { it.name })
    }

    @Test
    fun deleteRemovesOnlyThatDesignsTheme() {
        themes.save("Mine", WidgetDesign.SYSTEM, custom(WidgetDesign.SYSTEM))
        themes.save("Mine", WidgetDesign.MATERIAL, custom(WidgetDesign.MATERIAL))
        themes.delete("Mine", WidgetDesign.SYSTEM)
        assertEquals(listOf(WidgetDesign.MATERIAL), themes.list(defaults).map { it.design })
    }

    @Test
    fun theNewestThemesAreKeptUpToTheLimit() {
        repeat(WidgetThemeStore.MAX_THEMES + 3) { themes.save("Theme $it", WidgetDesign.MATERIAL, custom(WidgetDesign.MATERIAL)) }
        val names = themes.list(defaults).map { it.name }
        assertEquals(WidgetThemeStore.MAX_THEMES, names.size)
        assertEquals("Theme 3", names.first())
    }

    @Test
    fun anUnreadableSettingReadsAsNoThemes() {
        prefs.edit().putString(SettingsManager.WIDGET_SAVED_THEMES.name, "{ not a list").commit()
        assertEquals(emptyList<SavedWidgetTheme>(), themes.list(defaults))
        prefs.edit().putString(SettingsManager.WIDGET_SAVED_THEMES.name, "[{\"name\":\"X\",\"design\":\"NOPE\",\"values\":{}}, 3]").commit()
        assertEquals(emptyList<SavedWidgetTheme>(), themes.list(defaults))
    }
}
