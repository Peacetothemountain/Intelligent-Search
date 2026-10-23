package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.ResultShortcutStore
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.search.ResultShortcuts
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Pinned results travel in backups like every other setting. */
@RunWith(RobolectricTestRunner::class)
class ResultShortcutsBackupTest {

    @Test
    fun theSettingIsAKnownString() {
        assertEquals("STRING", BackupPreferenceSchema.expectedType(SettingsManager.RESULT_SHORTCUTS.name))
        assertEquals(SettingsManager.RESULT_SHORTCUTS, SettingsManager.keyFor("result_shortcuts_json"))
        assertTrue(SettingsManager.RESULT_SHORTCUTS in SettingsManager.ALL)
    }

    @Test
    fun pinsAreBackedUpAndRestored() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(context)
        val store = ResultShortcutStore(settingsManager)
        val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "shortcuts.json"))

        val spotify = "app:com.spotify.music/com.spotify.music.Main@0"
        store.update {
            it.withPinned(spotify, "Spotify", true).withPinned("contact:john", "John", true)
        }
        val saved = store.current
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()

        store.update { ResultShortcuts.EMPTY }
        assertTrue(store.current.entries.isEmpty())

        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(saved, store.current)
        assertEquals(listOf(spotify, "contact:john"), store.current.pinned.map { it.key })
        db.close()
    }
}
