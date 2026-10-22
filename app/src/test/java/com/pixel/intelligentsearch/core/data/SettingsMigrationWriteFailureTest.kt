package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.backup.BackupManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Nothing irreversible happens after a settings write that didn't reach storage. */
@RunWith(RobolectricTestRunner::class)
class SettingsMigrationWriteFailureTest {

    private val context = FailingCommitContext(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun theOldSettingsStoreIsKeptWhenTheMigratedSettingsCantBeSaved() = runBlocking {
        val legacyStore = context.preferencesDataStoreFile(SettingsManager.LEGACY_DATASTORE_NAME)
        legacyStore.parentFile?.mkdirs()
        legacyStore.writeBytes(ByteArray(0))

        context.failCommits = true
        SettingsManager(context).migrateToSingleStore()

        // Deleting it now would lose the only copy of what it held; the migration runs again next start.
        assertTrue(legacyStore.exists())
    }

    @Test
    fun aRestoreWhoseSettingsCantBeSavedDoesNotCountThem() = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(appContext, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(appContext)
        val working = BackupManager(appContext, settingsManager, db.historyDao(), StrongBoxSecurityManager(appContext), WidgetUpdater { })
        val failing = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(appContext.cacheDir, "backup.json"))
        settingsManager.updateSetting(SettingsManager.BACKGROUND_BLUR, 33)
        appContext.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("widget_system_shortcut_1", "maps")
            .commit()
        working.exportToFile(uri, "correct horse battery").getOrThrow()

        val restored = working.importFromFile(uri, "correct horse battery").getOrThrow()
        context.failCommits = true
        val restoredWithoutSaving = failing.importFromFile(uri, "correct horse battery").getOrThrow()

        assertTrue("$restoredWithoutSaving < $restored", restoredWithoutSaving < restored)
        db.close()
    }
}
