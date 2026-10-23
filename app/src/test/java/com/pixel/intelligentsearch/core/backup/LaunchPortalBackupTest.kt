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
 * Launch Portal's Notification Shortcut, floating button and edge swipe are backed up, but each needs a permission and
 * the last two draw over other apps, so a restore can switch them off and never on. Where they sit comes back.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchPortalBackupTest {

    private val optIns = listOf(SettingsManager.NOTIFICATION_SHORTCUT, SettingsManager.FLOATING_SEARCH_BUTTON, SettingsManager.EDGE_SWIPE)

    @Test
    fun theSettingsAreKnownWithTheirTypesAndRanges() {
        for (key in optIns) {
            assertEquals(key.name, "BOOLEAN", BackupPreferenceSchema.expectedType(key.name))
            assertEquals(key, SettingsManager.keyFor(key.name))
        }
        for (key in listOf(SettingsManager.FLOATING_BUTTON_EDGE, SettingsManager.EDGE_SWIPE_EDGE)) {
            assertEquals(key.name, "STRING", BackupPreferenceSchema.expectedType(key.name))
        }
        for (key in listOf(SettingsManager.FLOATING_BUTTON_POSITION, SettingsManager.EDGE_SWIPE_POSITION, SettingsManager.EDGE_SWIPE_HEIGHT)) {
            assertEquals(key.name, "INT", BackupPreferenceSchema.expectedType(key.name))
        }
        assertEquals(0..1000, BackupPreferenceSchema.intRange(SettingsManager.FLOATING_BUTTON_POSITION.name))
        assertEquals(0..100, BackupPreferenceSchema.intRange(SettingsManager.EDGE_SWIPE_POSITION.name))
        assertEquals(10..60, BackupPreferenceSchema.intRange(SettingsManager.EDGE_SWIPE_HEIGHT.name))
    }

    @Test
    fun theyAreOffUntilTheUserSwitchesThemOn() {
        for (key in optIns) assertEquals(key.name, false, key.default)
    }

    @Test
    fun aBackupCanSwitchThemOffButNeverOnAndKeepsWhereTheySit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backups = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "launch_portal.json"))

        // Backed up on, with the button on the left and the strip low and tall.
        for (key in optIns) settings.updateSetting(key, true)
        settings.updateSetting(SettingsManager.FLOATING_BUTTON_EDGE, "left")
        settings.updateSetting(SettingsManager.FLOATING_BUTTON_POSITION, 250)
        settings.updateSetting(SettingsManager.EDGE_SWIPE_EDGE, "left")
        settings.updateSetting(SettingsManager.EDGE_SWIPE_POSITION, 80)
        settings.updateSetting(SettingsManager.EDGE_SWIPE_HEIGHT, 40)
        backups.exportToFile(uri, "correct horse battery").getOrThrow()

        // Restored onto a phone where they are off and placed elsewhere: they stay off, the places come back.
        for (key in optIns) settings.updateSetting(key, false)
        settings.updateSetting(SettingsManager.FLOATING_BUTTON_EDGE, "right")
        settings.updateSetting(SettingsManager.FLOATING_BUTTON_POSITION, 600)
        settings.updateSetting(SettingsManager.EDGE_SWIPE_EDGE, "right")
        settings.updateSetting(SettingsManager.EDGE_SWIPE_POSITION, 50)
        settings.updateSetting(SettingsManager.EDGE_SWIPE_HEIGHT, 25)
        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) assertEquals(key.name, false, settings[key])
        assertEquals("left", settings[SettingsManager.FLOATING_BUTTON_EDGE])
        assertEquals(250, settings[SettingsManager.FLOATING_BUTTON_POSITION])
        assertEquals("left", settings[SettingsManager.EDGE_SWIPE_EDGE])
        assertEquals(80, settings[SettingsManager.EDGE_SWIPE_POSITION])
        assertEquals(40, settings[SettingsManager.EDGE_SWIPE_HEIGHT])

        // Backed up off: restoring switches them off.
        backups.exportToFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) settings.updateSetting(key, true)
        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        for (key in optIns) assertEquals(key.name, false, settings[key])

        db.close()
    }
}
