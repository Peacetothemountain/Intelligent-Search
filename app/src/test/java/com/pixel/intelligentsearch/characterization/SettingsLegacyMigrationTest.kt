package com.pixel.intelligentsearch.characterization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins how legacy SharedPreferences spellings reach DataStore today, so the settings rewrite can prove it
 * keeps every user's existing values.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsLegacyMigrationTest {

    @Test
    fun legacySpellingsMigrateIntoDataStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE).edit()
            .putString("night.mode", "Dark")
            .putBoolean("search_apps", false) // underscore spelling
            .putBoolean("show.wallpaper", false) // third spelling of the wallpaper switch
            .putInt("background.blur", 20) // short spelling
            .putString("search_pills", "com.a,com.b")
            .putString("widget_theme_style", "Material")
            .putStringSet("hidden_apps", setOf("com.example.hidden"))
            .putInt("web_results_count", 9)
            .putBoolean("app_fuzzy_search", false)
            .commit()

        val settingsManager = SettingsManager(context)
        settingsManager.syncSharedPreferencesToDataStore(overwrite = true)
        val settings = settingsManager.settingsFlow.first()

        assertEquals("Dark", settings.theme)
        assertFalse(settings.searchApps)
        assertFalse(settings.showWallpaper)
        assertEquals(20, settings.backgroundBlur)
        assertEquals("com.a,com.b", settings.searchPills)
        assertEquals("Material", settings.widgetThemeStyle)
        assertEquals(setOf("com.example.hidden"), settings.hiddenApps)
        assertEquals(9, settings.webResultsCount)
        assertFalse(settings.appFuzzySearch)
    }
}
