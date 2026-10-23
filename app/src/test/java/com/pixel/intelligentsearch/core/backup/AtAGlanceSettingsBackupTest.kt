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
 * At a Glance's switches (the section, each item, Show Clock) and its Clock Style are typed settings that backups
 * carry both ways. The battery item is gone.
 */
@RunWith(RobolectricTestRunner::class)
class AtAGlanceSettingsBackupTest {

    private val switches = listOf(
        SettingsManager.GLANCE_ENABLED, SettingsManager.GLANCE_TIMERS, SettingsManager.GLANCE_ALARM,
        SettingsManager.GLANCE_MEDIA, SettingsManager.GLANCE_BIRTHDAYS, SettingsManager.GLANCE_CLOCK
    )

    @Test
    fun theSwitchesAreKnownBooleansThatStartOn() {
        for (key in switches) {
            assertTrue("$key is declared", key in SettingsManager.ALL)
            assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(key.name))
            assertTrue("$key starts on", key.default)
        }
        assertTrue(SettingsManager.GLANCE_CLOCK_STYLE in SettingsManager.ALL)
        assertEquals("STRING", BackupPreferenceSchema.expectedType(SettingsManager.GLANCE_CLOCK_STYLE.name))
        assertEquals("cookie", SettingsManager.GLANCE_CLOCK_STYLE.default)
    }

    @Test
    fun theOneTimeCodesSwitchIsGone() {
        // Android 15 hides one-time codes from notification listeners, so the card and its switch were removed.
        assertNull(SettingsManager.keyFor("glance.codes"))
        assertNull(BackupPreferenceSchema.expectedType("glance.codes"))
    }

    @Test
    fun theBatteryIsGone() {
        assertNull(SettingsManager.keyFor("glance.battery"))
        assertTrue("glance.battery" in SettingsManager.REMOVED_NAMES)
        // The item switches are settings again, not dropped at start.
        for (key in switches) assertFalse("${key.name} is kept", key.name in SettingsManager.REMOVED_NAMES)
        assertFalse(SettingsManager.GLANCE_CLOCK_STYLE.name in SettingsManager.REMOVED_NAMES)
    }

    @Test
    fun aBackupCarriesTheSwitchesAndTheClockStyle() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "glance.json"))

        for (key in switches) settingsManager.updateSetting(key, false)
        settingsManager.updateSetting(SettingsManager.GLANCE_CLOCK_STYLE, "digital")
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        for (key in switches) settingsManager.updateSetting(key, true)
        settingsManager.updateSetting(SettingsManager.GLANCE_CLOCK_STYLE, "cookie")
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        for (key in switches) assertEquals("${key.name} restored", false, settingsManager[key])
        assertEquals("digital", settingsManager[SettingsManager.GLANCE_CLOCK_STYLE])

        db.close()
    }
}
