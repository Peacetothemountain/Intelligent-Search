package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.EnterKeyAction
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Show Keyboard When Search Opens and Enter Key Action: their defaults, and that backups carry them both ways. */
@RunWith(RobolectricTestRunner::class)
class KeyboardOptionsBackupTest {

    @Test
    fun theDefaultsKeepTodaysBehaviour() {
        assertEquals(true, SettingsManager.SHOW_KEYBOARD_ON_OPEN.default)
        assertEquals(EnterKeyAction.OPEN_TOP_RESULT, EnterKeyAction.from(SettingsManager.ENTER_KEY_ACTION.default))
    }

    @Test
    fun theSettingsAreKnownWithTheirTypes() {
        assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(SettingsManager.SHOW_KEYBOARD_ON_OPEN.name))
        assertEquals("STRING", BackupPreferenceSchema.expectedType(SettingsManager.ENTER_KEY_ACTION.name))
        assertEquals(SettingsManager.SHOW_KEYBOARD_ON_OPEN, SettingsManager.keyFor(SettingsManager.SHOW_KEYBOARD_ON_OPEN.name))
        assertEquals(SettingsManager.ENTER_KEY_ACTION, SettingsManager.keyFor(SettingsManager.ENTER_KEY_ACTION.name))
    }

    @Test
    fun aBackupRestoresBoth() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backups = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "keyboard_options.json"))

        settings.updateSetting(SettingsManager.SHOW_KEYBOARD_ON_OPEN, false)
        settings.updateSetting(SettingsManager.ENTER_KEY_ACTION, EnterKeyAction.SEARCH_WEB.storedValue)
        backups.exportToFile(uri, "correct horse battery").getOrThrow()
        settings.updateSetting(SettingsManager.SHOW_KEYBOARD_ON_OPEN, true)
        settings.updateSetting(SettingsManager.ENTER_KEY_ACTION, EnterKeyAction.OPEN_TOP_RESULT.storedValue)

        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settings[SettingsManager.SHOW_KEYBOARD_ON_OPEN])
        assertEquals(EnterKeyAction.SEARCH_WEB.storedValue, settings[SettingsManager.ENTER_KEY_ACTION])
        db.close()
    }
}
