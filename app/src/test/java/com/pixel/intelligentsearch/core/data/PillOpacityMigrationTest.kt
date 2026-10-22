package com.pixel.intelligentsearch.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Search Pill Opacity used to draw 50 (its old default) fully opaque. Now the value is the pill's opacity, so a
 * stored 50 becomes 100 once, and the pill looks the same after the update.
 */
@RunWith(RobolectricTestRunner::class)
class PillOpacityMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun theDefaultIsOpaque() {
        assertEquals(100, SettingsManager.PILL_OPACITY.default)
        assertEquals(100, SettingsManager(context).getInitialSettings().pillOpacity)
    }

    @Test
    fun aStoredFiftyBecomesOpaque() = runBlocking {
        // Already on the single store (every current install), so only the pill migration is left to run.
        prefs.edit().putInt("settings_storage_version", 1).putInt(SettingsManager.PILL_OPACITY.name, 50).commit()

        SettingsManager(context).migrateToSingleStore()

        assertEquals(100, prefs.getInt(SettingsManager.PILL_OPACITY.name, -1))
    }

    @Test
    fun aFiftyUnderTheOldSpellingBecomesOpaque() = runBlocking {
        prefs.edit().putInt("pill.opacity", 50).commit()

        SettingsManager(context).migrateToSingleStore()

        assertEquals(100, prefs.getInt(SettingsManager.PILL_OPACITY.name, -1))
        assertFalse(prefs.contains("pill.opacity"))
    }

    @Test
    fun otherValuesAreUntouched() = runBlocking {
        for (value in listOf(0, 10, 40, 60, 90, 100)) {
            prefs.edit().clear().putInt(SettingsManager.PILL_OPACITY.name, value).commit()
            SettingsManager(context).migrateToSingleStore()
            assertEquals(value, prefs.getInt(SettingsManager.PILL_OPACITY.name, -1))
        }
    }

    @Test
    fun nothingIsWrittenWhenThePillWasNeverChanged() = runBlocking {
        SettingsManager(context).migrateToSingleStore()

        assertFalse(prefs.contains(SettingsManager.PILL_OPACITY.name))
        assertEquals(100, SettingsManager(context)[SettingsManager.PILL_OPACITY])
    }

    @Test
    fun itRunsOnlyOnce() = runBlocking {
        prefs.edit().putInt(SettingsManager.PILL_OPACITY.name, 50).commit()
        val settings = SettingsManager(context)
        settings.migrateToSingleStore()
        settings.migrateToSingleStore()
        assertEquals(100, settings[SettingsManager.PILL_OPACITY])

        // 50 chosen after the update means half opacity and stays.
        settings.set(SettingsManager.PILL_OPACITY, 50)
        prefs.edit().commit() // flush the apply()
        settings.migrateToSingleStore()
        SettingsManager(context).migrateToSingleStore()

        assertEquals(50, settings[SettingsManager.PILL_OPACITY])
    }
}
