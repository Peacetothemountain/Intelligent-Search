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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The instant-answer switches are backed up like other settings, but each reaches the network (or reads location),
 * so a backup can switch them off and never on. The home location comes back as it was.
 */
@RunWith(RobolectricTestRunner::class)
class InstantAnswersBackupTest {

    private val optIns = listOf(
        SettingsManager.DICTIONARY_ANSWERS, SettingsManager.WEATHER_ANSWERS,
        SettingsManager.WEATHER_USE_DEVICE_LOCATION, SettingsManager.ON_DEVICE_TRANSLATION
    )

    @Test
    fun theSettingsAreKnownWithTheirTypes() {
        for (key in optIns) {
            assertEquals(key.name, "BOOLEAN", BackupPreferenceSchema.expectedType(key.name))
            assertEquals(key, SettingsManager.keyFor(key.name))
        }
        assertEquals("STRING", BackupPreferenceSchema.expectedType(SettingsManager.WEATHER_HOME_LOCATION.name))
    }

    @Test
    fun aBackupCanSwitchThemOffButNeverOnAndKeepsTheHomeLocation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backups = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "instant_answers.json"))
        val home = "36.31,-82.35;Johnson City, Tennessee"

        // Backed up on: restoring onto a phone where they are off leaves them off.
        for (key in optIns) settings.updateSetting(key, true)
        settings.updateSetting(SettingsManager.WEATHER_HOME_LOCATION, home)
        backups.exportToFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) settings.updateSetting(key, false)
        settings.updateSetting(SettingsManager.WEATHER_HOME_LOCATION, "")
        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) assertEquals(key.name, false, settings[key])
        assertEquals(home, settings[SettingsManager.WEATHER_HOME_LOCATION])

        // Backed up off: restoring switches them off.
        backups.exportToFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) settings.updateSetting(key, true)
        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) assertEquals(key.name, false, settings[key])

        db.close()
    }
}
