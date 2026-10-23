package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Definitions and forecasts have no switches of their own any more: they follow Web Suggestions, which a backup can
 * switch off but never on. The old Dictionary Answers, Weather Answers and Use Approximate Location switches are
 * dropped and never restored; the weather's location comes back as it was.
 */
@RunWith(RobolectricTestRunner::class)
class InstantAnswersBackupTest {

    private val removed = listOf("dictionary_answers", "weather_answers", "weather_use_device_location")

    @Test
    fun theOldSwitchesAreGoneAndTheLocationStays() {
        for (name in removed) {
            assertNull(name, SettingsManager.keyFor(name))
            assertTrue(name, name in SettingsManager.REMOVED_NAMES)
        }
        assertEquals("STRING", BackupPreferenceSchema.expectedType(SettingsManager.WEATHER_HOME_LOCATION.name))
    }

    @Test
    fun aBackupKeepsTheLocationAndNeverRestoresTheOldSwitchesOrTurnsSuggestionsOn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backups = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "instant_answers.json"))
        val home = "36.31,-82.35;Johnson City, Tennessee"

        // A file from an older build may still carry the old switches.
        settings.prefs.edit().apply { removed.forEach { putBoolean(it, true) } }.commit()
        settings.updateSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS, true)
        settings.updateSetting(SettingsManager.WEATHER_HOME_LOCATION, home)
        backups.exportToFile(uri, "correct horse battery").getOrThrow()
        settings.prefs.edit().apply { removed.forEach { remove(it) } }.commit()
        settings.updateSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS, false)
        settings.updateSetting(SettingsManager.WEATHER_HOME_LOCATION, "")
        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        for (name in removed) assertFalse(name, settings.prefs.contains(name))
        assertEquals(false, settings[SettingsManager.SEARCH_WEB_SUGGESTIONS])
        assertEquals(home, settings[SettingsManager.WEATHER_HOME_LOCATION])

        db.close()
    }

    @Test
    fun someoneWhoUsedTheApproximateLocationKeepsUsingIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsManager(context)
        // The set location now wins over the phone's, so the migration clears it for someone who had chosen the phone's.
        settings.prefs.edit()
            .putBoolean("weather_use_device_location", true)
            .putString(SettingsManager.WEATHER_HOME_LOCATION.name, "36.31,-82.35;Johnson City, Tennessee")
            .commit()
        settings.migrateToSingleStore()
        assertEquals("", settings[SettingsManager.WEATHER_HOME_LOCATION])
        assertFalse(settings.prefs.contains("weather_use_device_location"))

        // Without it, the set location stays.
        settings.prefs.edit().putString(SettingsManager.WEATHER_HOME_LOCATION.name, "36.31,-82.35;Johnson City, Tennessee").commit()
        settings.migrateToSingleStore()
        assertEquals("36.31,-82.35;Johnson City, Tennessee", settings[SettingsManager.WEATHER_HOME_LOCATION])
    }
}
