package com.pixel.intelligentsearch.core.backup

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.HistoryDao
import com.pixel.intelligentsearch.core.data.HistoryEntity
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.feature.search.SelectedTextSearchActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Search from Selected Text is a setting and a component state; restoring a backup restores both, so the entry in other
 * apps' menus matches the restored switch.
 */
@RunWith(RobolectricTestRunner::class)
class SelectedTextBackupRestoreTest {

    @Test
    fun restoringABackupReappliesTheSelectionEntry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val entry = ComponentName(context, SelectedTextSearchActivity::class.java)
        val pm = context.packageManager
        val uri = Uri.fromFile(File(context.cacheDir, "selected_text.json"))

        // Backed up with the entry off; then switched back on.
        settingsManager.updateSetting(SettingsManager.SEARCH_FROM_SELECTED_TEXT, false)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.updateSetting(SettingsManager.SEARCH_FROM_SELECTED_TEXT, true)
        SelectedTextSearchActivity.setEnabled(context, true)

        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settingsManager[SettingsManager.SEARCH_FROM_SELECTED_TEXT])
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(entry))

        // And a backup made with it on turns it back on.
        settingsManager.updateSetting(SettingsManager.SEARCH_FROM_SELECTED_TEXT, true)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.updateSetting(SettingsManager.SEARCH_FROM_SELECTED_TEXT, false)
        SelectedTextSearchActivity.setEnabled(context, false)

        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(true, settingsManager[SettingsManager.SEARCH_FROM_SELECTED_TEXT])
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(entry))

        db.close()
    }

    /** Search history that can be backed up but not restored. */
    private class UnwritableHistoryDao : HistoryDao() {
        override fun getSearchHistoryFlow(): Flow<List<HistoryEntity>> = flowOf(emptyList())
        override suspend fun getSearchHistory(limit: Int) = listOf(HistoryEntity("eiffel tower", 1L))
        override suspend fun insertSearch(search: HistoryEntity) = throw IllegalStateException("Database is full")
        override suspend fun insertSearches(searches: List<HistoryEntity>) = throw IllegalStateException("Database is full")
        override suspend fun deleteByQuery(query: String) = Unit
        override suspend fun clearHistory() = Unit
        override suspend fun pruneHistory(limit: Int) = Unit
    }

    @Test
    fun aFailedHistoryRestoreStillReappliesTheSelectionEntry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(context, settingsManager, UnwritableHistoryDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val entry = ComponentName(context, SelectedTextSearchActivity::class.java)
        val uri = Uri.fromFile(File(context.cacheDir, "selected_text_history.json"))

        settingsManager.updateSetting(SettingsManager.SEARCH_FROM_SELECTED_TEXT, false)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.updateSetting(SettingsManager.SEARCH_FROM_SELECTED_TEXT, true)
        SelectedTextSearchActivity.setEnabled(context, true)

        // The settings are restored before the history fails to be written back.
        assertTrue(backupManager.importFromFile(uri, "correct horse battery").isFailure)
        assertEquals(false, settingsManager[SettingsManager.SEARCH_FROM_SELECTED_TEXT])
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, context.packageManager.getComponentEnabledSetting(entry))
    }
}
