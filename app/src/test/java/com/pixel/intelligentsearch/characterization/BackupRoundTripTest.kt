package com.pixel.intelligentsearch.characterization

import com.pixel.intelligentsearch.core.system.WidgetUpdater
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.backup.BackupManager
import com.pixel.intelligentsearch.core.data.HistoryEntity
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Export then import of a passphrase backup restores settings, hidden apps, widget preferences and
 * history, and rejects values the app could not read back (wrong type, out of range).
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    @Test
    fun passphraseBackupRoundTripsAndRejectsUnreadableValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.historyDao()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(
            context,
            settingsManager,
            dao,
            StrongBoxSecurityManager(context),
            WidgetUpdater { }
        )

        // State to back up, including two values the app can't read back safely.
        settingsManager.updateSetting(SettingsManager.BACKGROUND_BLUR, 33)
        settingsManager.updateSetting(SettingsManager.HIDDEN_APPS, setOf("com.example.secret"))
        prefs.edit()
            .putString("widget_system_shortcut_1", "maps")
            .putInt("contact_results_count", -1) // would reach take(-1)
            .putString("search.web.suggestions", "yes") // read with getBoolean
            .commit()
        dao.insertSearches(
            listOf(HistoryEntity(query = "hello", timestamp = 2L), HistoryEntity(query = "world", timestamp = 1L))
        )

        val file = File(context.cacheDir, "backup.json")
        val uri = Uri.fromFile(file)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        assertTrue("backup file written", file.length() > 0)
        assertTrue("backup is not plaintext", !file.readText().contains("com.example.secret"))

        // Change everything, then restore.
        settingsManager.updateSetting(SettingsManager.BACKGROUND_BLUR, 80)
        settingsManager.updateSetting(SettingsManager.HIDDEN_APPS, emptySet())
        prefs.edit()
            .putString("widget_system_shortcut_1", "youtube")
            .putInt("contact_results_count", 7)
            .putBoolean("search.web.suggestions", true)
            .commit()
        dao.clearHistory()

        val wrong = backupManager.importFromFile(uri, "wrong passphrase").exceptionOrNull()?.message
        assertTrue("wrong passphrase is reported: $wrong", wrong != null && wrong.contains("passphrase", ignoreCase = true))

        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()

        val restored = settingsManager.settingsFlow.first()
        assertEquals(33, restored.backgroundBlur)
        assertEquals(setOf("com.example.secret"), restored.hiddenApps)
        assertEquals("maps", prefs.getString("widget_system_shortcut_1", null))
        assertEquals(listOf("hello", "world"), dao.getSearchHistory().map { it.query })

        // Unreadable values from the file were not applied.
        // -1 never comes back; at worst it is clamped to the slider range on export.
        assertTrue(prefs.getInt("contact_results_count", 5) in 0..20)
        assertEquals(true, prefs.getBoolean("search.web.suggestions", false))

        db.close()
    }

    @Test
    fun aBackupCanSwitchSharingOptInsOffButNeverOn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val file = File(context.cacheDir, "optins.json")
        val uri = Uri.fromFile(file)

        // A file with every sharing opt-in switched on doesn't switch them on here.
        settingsManager.updateSetting(SettingsManager.APP_FUNCTIONS_SHARE_CONTACTS, true)
        settingsManager.updateSetting(SettingsManager.SMART_CLIPBOARD_SUGGESTIONS, true)
        settingsManager.updateSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS, true)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.updateSetting(SettingsManager.APP_FUNCTIONS_SHARE_CONTACTS, false)
        settingsManager.updateSetting(SettingsManager.SMART_CLIPBOARD_SUGGESTIONS, false)
        settingsManager.updateSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS, false)
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settingsManager[SettingsManager.APP_FUNCTIONS_SHARE_CONTACTS])
        assertEquals(false, settingsManager[SettingsManager.SMART_CLIPBOARD_SUGGESTIONS])
        assertEquals(false, settingsManager[SettingsManager.SEARCH_WEB_SUGGESTIONS])

        // A file with them off does switch them off.
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.updateSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS, true)
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settingsManager[SettingsManager.SEARCH_WEB_SUGGESTIONS])

        db.close()
    }
}
