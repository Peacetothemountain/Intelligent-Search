package com.pixel.intelligentsearch.characterization

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.legacySettingsFile
import com.pixel.intelligentsearch.core.data.readLegacySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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

    /** The old settings file is written by DataStore itself, so the app's own reader is checked against the real format. */
    @Test
    fun valuesOnlyTheOldDataStoreFileHeldAreImported() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("search.web", true).commit() // written since, so it wins over the old file
        val file = context.preferencesDataStoreFile(SettingsManager.LEGACY_DATASTORE_NAME)
        assertEquals(file, legacySettingsFile(context))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        PreferenceDataStoreFactory.create(scope = scope) { file }.edit {
            it[booleanPreferencesKey("search_contacts")] = true // legacy spelling
            it[booleanPreferencesKey("search.web")] = false
            it[intPreferencesKey("search.background.blur")] = 7
            it[intPreferencesKey("not_a_setting_int")] = -12345
            it[stringPreferencesKey("search.engine")] = "DuckDuckGo"
            it[stringPreferencesKey("weather_home_location")] = "Zürich, 東京"
            it[stringSetPreferencesKey("hidden_apps")] = setOf("com.a", "com.b")
            it[stringSetPreferencesKey("search.files.excluded_folders")] = emptySet()
            it[floatPreferencesKey("search.background.transparency")] = 0.5f // not a type settings used
            it[longPreferencesKey("not_a_setting_long")] = Long.MIN_VALUE
            it[doublePreferencesKey("not_a_setting_double")] = 1.5
            it[byteArrayPreferencesKey("not_a_setting_bytes")] = byteArrayOf(1, 2, 3)
        }
        scope.cancel()

        assertEquals(
            mapOf(
                "search_contacts" to true,
                "search.web" to false,
                "search.background.blur" to 7,
                "not_a_setting_int" to -12345,
                "search.engine" to "DuckDuckGo",
                "weather_home_location" to "Zürich, 東京",
                "hidden_apps" to setOf("com.a", "com.b"),
                "search.files.excluded_folders" to emptySet<String>(),
            ),
            readLegacySettings(file.readBytes()),
        )

        val settingsManager = SettingsManager(context)
        settingsManager.migrateToSingleStore()

        assertTrue(settingsManager[SettingsManager.SEARCH_CONTACTS])
        assertTrue(settingsManager[SettingsManager.SEARCH_WEB])
        assertEquals(7, settingsManager[SettingsManager.BACKGROUND_BLUR])
        assertEquals("DuckDuckGo", settingsManager[SettingsManager.SEARCH_ENGINE])
        assertEquals("Zürich, 東京", settingsManager[SettingsManager.WEATHER_HOME_LOCATION])
        assertEquals(setOf("com.a", "com.b"), settingsManager[SettingsManager.HIDDEN_APPS])
        assertEquals(SettingsManager.BACKGROUND_TRANSPARENCY.default, settingsManager[SettingsManager.BACKGROUND_TRANSPARENCY])
        assertFalse(file.exists())
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
