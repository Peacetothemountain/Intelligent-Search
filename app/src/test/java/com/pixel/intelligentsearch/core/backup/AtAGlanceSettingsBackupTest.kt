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
 * The At a Glance switches and Search Your Notifications are typed settings that backups carry; a backup may switch
 * notification search off but never on, as with the other privacy opt-ins.
 */
@RunWith(RobolectricTestRunner::class)
class AtAGlanceSettingsBackupTest {

    private val keys = listOf(
        SettingsManager.SEARCH_NOTIFICATIONS, SettingsManager.GLANCE_ENABLED, SettingsManager.GLANCE_ALARM,
        SettingsManager.GLANCE_TIMERS, SettingsManager.GLANCE_MEDIA,
        SettingsManager.GLANCE_BIRTHDAYS, SettingsManager.GLANCE_BATTERY
    )

    @Test
    fun theSwitchesAreKnownBooleans() {
        for (key in keys) {
            assertTrue("$key is declared", key in SettingsManager.ALL)
            assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(key.name))
        }
        // Notification search is an opt-in, off until the user turns it on; every At a Glance item starts on.
        assertFalse(SettingsManager.SEARCH_NOTIFICATIONS.default)
        assertTrue(keys.drop(1).all { it.default })
    }

    @Test
    fun theOneTimeCodesSwitchIsGone() {
        // Android 15 hides one-time codes from notification listeners, so the card and its switch were removed.
        assertNull(SettingsManager.keyFor("glance.codes"))
        assertNull(BackupPreferenceSchema.expectedType("glance.codes"))
    }

    @Test
    fun aBackupCanSwitchNotificationSearchOffButNeverOn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "notifications.json"))

        settingsManager.updateSetting(SettingsManager.SEARCH_NOTIFICATIONS, true)
        settingsManager.updateSetting(SettingsManager.GLANCE_BATTERY, false)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.updateSetting(SettingsManager.SEARCH_NOTIFICATIONS, false)
        settingsManager.updateSetting(SettingsManager.GLANCE_BATTERY, true)
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settingsManager[SettingsManager.SEARCH_NOTIFICATIONS])
        // Ordinary switches come back as they were.
        assertEquals(false, settingsManager[SettingsManager.GLANCE_BATTERY])

        settingsManager.updateSetting(SettingsManager.SEARCH_NOTIFICATIONS, false)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.updateSetting(SettingsManager.SEARCH_NOTIFICATIONS, true)
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settingsManager[SettingsManager.SEARCH_NOTIFICATIONS])

        db.close()
    }
}
