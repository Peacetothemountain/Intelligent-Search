package com.pixel.intelligentsearch.core.data.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The widget's five design profiles work as the Application Search pill profiles do: the active one is the widget's
 * own design, each other one keeps its whole design (every option of both designs) while it's left, Reset returns to
 * a saved state, and the retired Saved Themes move into them once.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetProfilesTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var profiles: WidgetProfiles
    private val defaults = WidgetDefaults(primaryColor = 0xFF6750A4.toInt(), isDynamicColorActive = true)

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("widget_profiles_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        profiles = WidgetProfiles(prefs, defaults)
    }

    /** A design far from the defaults in every field. */
    private fun custom(design: WidgetDesign, hue: Float = 123f): WidgetDesignSettings = WidgetSettingsStore(prefs).load(design, defaults).copy(
        subtheme = "Custom",
        gIconTheme = "Custom G Icon",
        elementColors = WidgetElementColors(0xFF112233.toInt(), 0xFF223344.toInt(), 0xFF334455.toInt(), 0xFF445566.toInt(), 0xFF556677.toInt(), 0xFF667788.toInt()),
        syncColors = true,
        keepSystemG = true,
        hue = hue,
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
        // Only the Material design stores these; the System design always reads its defaults.
        lockBlack = design != WidgetDesign.MATERIAL,
        actionIcon = if (design == WidgetDesign.MATERIAL) "Now Playing" else "Search",
        showDoodles = false,
        outlineThickness = 4,
        cornerRadius = 12
    )

    private fun customProfile(design: WidgetDesign, hue: Float = 123f) =
        WidgetProfileDesign(design, custom(WidgetDesign.SYSTEM, hue), custom(WidgetDesign.MATERIAL, hue))

    /** What the widget shows in these tests: both designs' defaults. */
    private val live get() = profiles.defaultDesign()

    @Test
    fun aSavedStateComesBackExactly() {
        val design = customProfile(WidgetDesign.MATERIAL)
        profiles.saveState(2, design)
        assertEquals(design, profiles.savedState(2, live))
        assertTrue(prefs.getString(SettingsManager.WIDGET_PROFILE_SAVED_DESIGNS[1].name, "")!!.isNotEmpty())
    }

    @Test
    fun theActiveProfileIsTheWidgetsOwnDesign() {
        assertEquals(1, profiles.activeProfile)
        val widget = customProfile(WidgetDesign.SYSTEM)
        assertEquals(widget, profiles.design(1, widget))
    }

    @Test
    fun aProfileNeverUsedStartsWithTheDefaults() {
        assertEquals(profiles.defaultDesign(), profiles.design(4, customProfile(WidgetDesign.MATERIAL)))
        assertEquals(profiles.defaultDesign(), profiles.savedState(4, customProfile(WidgetDesign.MATERIAL)))
        assertEquals(WidgetDesign.SYSTEM, profiles.defaultDesign().design)
    }

    @Test
    fun aProfileWithoutItsOwnCopyShowsItsSavedState() {
        val saved = customProfile(WidgetDesign.MATERIAL)
        profiles.saveState(3, saved)
        assertEquals(saved, profiles.design(3, live))
    }

    @Test
    fun savingAnotherProfileToTheWidgetKeepsTheOneItReplaces() {
        val widget = customProfile(WidgetDesign.SYSTEM, hue = 10f)
        val edited = customProfile(WidgetDesign.MATERIAL, hue = 200f)
        prefs.edit().also { profiles.commit(it, active = 3, left = mapOf(4 to edited), previousLive = widget) }.commit()

        assertEquals(3, profiles.activeProfile)
        assertEquals(3, prefs.getInt(SettingsManager.WIDGET_ACTIVE_PROFILE.name, 0))
        // Profile 1 was on the widget: it keeps that design until it's picked again.
        assertEquals(widget, profiles.design(1, live))
        // A profile left with changes keeps them.
        assertEquals(edited, profiles.design(4, live))
        // Its saved state, what Reset returns to, is untouched.
        assertEquals(profiles.defaultDesign(), profiles.savedState(4, live))
    }

    @Test
    fun aNewerCopyOfTheReplacedProfileWins() {
        val widget = customProfile(WidgetDesign.SYSTEM, hue = 10f)
        val edited = customProfile(WidgetDesign.SYSTEM, hue = 60f)
        prefs.edit().also { profiles.commit(it, active = 2, left = mapOf(1 to edited), previousLive = widget) }.commit()
        assertEquals(edited, profiles.design(1, live))
    }

    @Test
    fun savingTheSameProfileWritesNoCopies() {
        prefs.edit().also { profiles.commit(it, active = 1, left = emptyMap(), previousLive = customProfile(WidgetDesign.SYSTEM)) }.commit()
        assertTrue(SettingsManager.WIDGET_PROFILE_DESIGNS.none { prefs.contains(it.name) })
    }

    @Test
    fun profileOneStartsWithTheWidgetsDesignAsItsSavedStateOnce() {
        val first = customProfile(WidgetDesign.MATERIAL, hue = 30f)
        profiles.ensureDefaultSaved(first)
        assertEquals(first, profiles.savedState(1, live))
        profiles.ensureDefaultSaved(customProfile(WidgetDesign.SYSTEM, hue = 90f))
        assertEquals(first, profiles.savedState(1, live))
    }

    @Test
    fun anUnreadableProfileReadsAsItsSavedStateOrTheDefaults() {
        prefs.edit()
            .putString(SettingsManager.WIDGET_PROFILE_DESIGNS[1].name, "{ not json")
            .putString(SettingsManager.WIDGET_PROFILE_SAVED_DESIGNS[1].name, "{\"design\":\"NOPE\"}")
            .commit()
        assertEquals(profiles.defaultDesign(), profiles.design(2, live))
    }

    @Test
    fun aStoredProfileWithOneDesignKeepsTheWidgetsOtherDesign() {
        val system = custom(WidgetDesign.SYSTEM)
        val json = "{\"design\":\"SYSTEM\",\"SYSTEM\":${WidgetProfiles.valuesOf(WidgetDesign.SYSTEM, system)}}"
        prefs.edit().putString(SettingsManager.WIDGET_PROFILE_DESIGNS[1].name, json).commit()
        val widget = customProfile(WidgetDesign.MATERIAL, hue = 300f)
        assertEquals(WidgetProfileDesign(WidgetDesign.SYSTEM, system, widget.material), profiles.design(2, widget))
    }

    @Test
    fun savedThemesMoveIntoTheProfilesOnce() {
        val themes = (1..7).joinToString(",", "[", "]") { n ->
            val design = if (n % 2 == 0) WidgetDesign.MATERIAL else WidgetDesign.SYSTEM
            val values = WidgetProfiles.valuesOf(design, custom(design, hue = n * 10f))
            // The third theme names no design this version has, so it's left out and the rest move up.
            val name = if (n == 3) "NOPE" else design.name
            "{\"name\":\"Theme $n\",\"design\":\"$name\",\"values\":$values}"
        }
        prefs.edit().putString(WidgetProfiles.SAVED_THEMES_KEY, themes).commit()

        WidgetProfiles.migrateSavedThemes(prefs)

        assertFalse(prefs.contains(WidgetProfiles.SAVED_THEMES_KEY))
        val widget = customProfile(WidgetDesign.SYSTEM, hue = 5f)
        val moved = listOf(1, 2, 4, 5, 6)
        moved.forEachIndexed { index, n ->
            val id = index + 1
            val design = if (n % 2 == 0) WidgetDesign.MATERIAL else WidgetDesign.SYSTEM
            val saved = profiles.savedState(id, widget)
            assertEquals("Theme $n", design, saved.design)
            assertEquals("Theme $n", custom(design, hue = n * 10f), saved.settings(design))
            // A theme held one design: the profile keeps the widget's own for the other.
            val other = WidgetDesign.entries.single { it != design }
            assertEquals("Theme $n", widget.settings(other), saved.settings(other))
            if (id != 1) assertEquals("Theme $n", saved, profiles.design(id, widget))
        }
        // Profile 1 stays on the widget with the design the widget has; its theme is what Reset returns to.
        assertEquals(1, profiles.activeProfile)
        assertEquals(widget, profiles.design(1, widget))
        // Nothing left to move: running it again changes nothing.
        val before = prefs.all.toMap()
        WidgetProfiles.migrateSavedThemes(prefs)
        assertEquals(before, prefs.all.toMap())
    }

    @Test
    fun aProfileAlreadyStoredIsNotReplacedByASavedTheme() {
        val mine = customProfile(WidgetDesign.MATERIAL, hue = 250f)
        profiles.saveState(1, mine)
        val values = WidgetProfiles.valuesOf(WidgetDesign.SYSTEM, custom(WidgetDesign.SYSTEM))
        prefs.edit().putString(WidgetProfiles.SAVED_THEMES_KEY, "[{\"name\":\"Old\",\"design\":\"SYSTEM\",\"values\":$values}]").commit()
        WidgetProfiles.migrateSavedThemes(prefs)
        assertEquals(mine, profiles.savedState(1, live))
        assertFalse(prefs.contains(WidgetProfiles.SAVED_THEMES_KEY))
    }

    @Test
    fun unreadableSavedThemesAreDroppedWithoutProfiles() {
        prefs.edit().putString(WidgetProfiles.SAVED_THEMES_KEY, "{ not a list").commit()
        WidgetProfiles.migrateSavedThemes(prefs)
        assertFalse(prefs.contains(WidgetProfiles.SAVED_THEMES_KEY))
        assertTrue((SettingsManager.WIDGET_PROFILE_DESIGNS + SettingsManager.WIDGET_PROFILE_SAVED_DESIGNS).none { prefs.contains(it.name) })
    }

    @Test
    fun theProfilesAreDeclaredSettingsAndSavedThemesAreRetired() {
        assertEquals(WidgetProfiles.PROFILE_COUNT, SettingsManager.WIDGET_PROFILE_DESIGNS.size)
        assertEquals(WidgetProfiles.PROFILE_COUNT, SettingsManager.WIDGET_PROFILE_SAVED_DESIGNS.size)
        for (key in SettingsManager.WIDGET_PROFILE_DESIGNS + SettingsManager.WIDGET_PROFILE_SAVED_DESIGNS + SettingsManager.WIDGET_ACTIVE_PROFILE) {
            assertEquals(key, SettingsManager.keyFor(key.name))
        }
        assertEquals(1..WidgetProfiles.PROFILE_COUNT, SettingsManager.WIDGET_ACTIVE_PROFILE.range)
        assertNull(SettingsManager.keyFor(WidgetProfiles.SAVED_THEMES_KEY))
        assertTrue(WidgetProfiles.SAVED_THEMES_KEY in SettingsManager.REMOVED_NAMES)
    }

    /** A profile an older build stored with the removed favourite apps and media rows still reads, without them. */
    @Test
    fun aProfileWithTheRemovedRowsStillReads() {
        val stored = """{"design":"MATERIAL","MATERIAL":{"design_saved":true,"show_media":true,"favourite_apps":"com.a","outline_thickness":4,"corner_radius":12}}"""
        prefs.edit().putString(SettingsManager.WIDGET_PROFILE_DESIGNS[1].name, stored).commit()
        val design = profiles.design(2, live)
        assertEquals(WidgetDesign.MATERIAL, design.design)
        assertEquals(4, design.material.outlineThickness)
        assertEquals(12, design.material.cornerRadius)
        assertFalse(WidgetProfiles.encode(design).contains("show_media"))
        assertFalse(WidgetProfiles.encode(design).contains("favourite_apps"))
    }
}
