package com.pixel.intelligentsearch.characterization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Users upgrading from older versions keep every setting: legacy spellings are read correctly before the
 * migration runs, and the migration folds them into the canonical names.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsLegacyMigrationTest {

    @Test
    fun legacySpellingsAreReadAndThenFoldedIntoCanonicalNames() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("theme", "Dark mode") // legacy name and legacy value
            .putBoolean("search_apps", false) // underscore spelling
            .putBoolean("show.wallpaper", false) // third spelling of the wallpaper switch
            .putInt("background.blur", 20) // short spelling
            .putString("search.pills", "com.a,com.b")
            .putString("widget_theme_style", "Material")
            .putStringSet("hidden_apps", setOf("com.example.hidden"))
            .putInt("web_results_count", 9)
            .putBoolean("app_fuzzy_search", false)
            .putInt("contact_results_count", -4) // out of range: clamped on read
            .putString("search_sections_config_json", "[]") // retired feature
            .commit()

        val settingsManager = SettingsManager(context)

        // Before the migration, legacy spellings are already honoured.
        val before = settingsManager.getInitialSettings()
        assertFalse(before.searchApps)
        assertEquals(20, before.backgroundBlur)
        assertEquals("com.a,com.b", before.searchPills)
        assertEquals(0, before.contactResultsCount)

        settingsManager.migrateToSingleStore()
        val after = settingsManager.settingsFlow.first()

        assertEquals("Material Dark", after.theme)
        assertFalse(after.searchApps)
        assertFalse(after.showWallpaper)
        assertEquals(20, after.backgroundBlur)
        assertEquals("com.a,com.b", after.searchPills)
        assertEquals("Material", after.widgetThemeStyle)
        assertEquals(setOf("com.example.hidden"), after.hiddenApps)
        assertEquals(9, after.webResultsCount)
        assertFalse(after.appFuzzySearch)

        // Only canonical names remain.
        for (legacy in listOf("theme", "search_apps", "show.wallpaper", "background.blur", "search.pills", "widget_theme_style", "app_fuzzy_search", "search_sections_config_json")) {
            assertFalse("$legacy should be gone", prefs.contains(legacy))
        }
        for (canonical in listOf("night.mode", "search.apps", "search.background.show.wall", "search.background.blur", "search_pills")) {
            assertTrue("$canonical should exist", prefs.contains(canonical))
        }
    }

    @Test
    fun writesUseTheCanonicalNameAndObserversSeeThem() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("search_contacts", false).commit()
        val settingsManager = SettingsManager(context)

        settingsManager.set(SettingsManager.SEARCH_CONTACTS, true)
        prefs.edit().commit() // flush the apply()

        assertTrue(prefs.getBoolean("search.contacts", false))
        assertFalse(prefs.contains("search_contacts"))
        assertTrue(settingsManager.observe(SettingsManager.SEARCH_CONTACTS).first())
        assertEquals(100, run {
            settingsManager.set(SettingsManager.BACKGROUND_BLUR, 250) // clamped to the slider range
            settingsManager[SettingsManager.BACKGROUND_BLUR]
        })
    }
}
