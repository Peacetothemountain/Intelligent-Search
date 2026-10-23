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
 * Show Keyboard When Search Opens: its default, and that backups carry it both ways. The removed Enter Key Action is
 * dropped at start and never restored.
 */
@RunWith(RobolectricTestRunner::class)
class KeyboardOptionsBackupTest {

    @Test
    fun theDefaultsKeepTodaysBehaviour() {
        assertEquals(true, SettingsManager.SHOW_KEYBOARD_ON_OPEN.default)
    }

    @Test
    fun theSettingsAreKnownWithTheirTypes() {
        assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(SettingsManager.SHOW_KEYBOARD_ON_OPEN.name))
        assertEquals(SettingsManager.SHOW_KEYBOARD_ON_OPEN, SettingsManager.keyFor(SettingsManager.SHOW_KEYBOARD_ON_OPEN.name))
        assertEquals(null, SettingsManager.keyFor("enter_key_action"))
        assertEquals(true, "enter_key_action" in SettingsManager.REMOVED_NAMES)
    }

    @Test
    fun aBackupRestoresIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backups = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "keyboard_options.json"))

        settings.updateSetting(SettingsManager.SHOW_KEYBOARD_ON_OPEN, false)
        backups.exportToFile(uri, "correct horse battery").getOrThrow()
        settings.updateSetting(SettingsManager.SHOW_KEYBOARD_ON_OPEN, true)

        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settings[SettingsManager.SHOW_KEYBOARD_ON_OPEN])
        db.close()
    }
}
