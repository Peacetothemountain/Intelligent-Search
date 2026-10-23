package com.pixel.intelligentsearch.core.backup

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.feature.search.SelectedTextSearchActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Search from Selected Text used to be a switch, saved in backups. It is always on now: a backup made with the switch
 * off still restores, and neither brings the switch back nor turns the entry in other apps' menus off.
 */
@RunWith(RobolectricTestRunner::class)
class SelectedTextBackupRestoreTest {

    @Test
    fun aBackupMadeWithTheOldSwitchOffRestoresAndLeavesTheEntryOn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val entry = ComponentName(context, SelectedTextSearchActivity::class.java)
        val uri = Uri.fromFile(File(context.cacheDir, "selected_text.json"))

        // A file whose settings carry the old switch, turned off, next to a setting that still exists.
        settingsManager.prefs.edit().putBoolean("search_from_selected_text", false).commit()
        settingsManager.updateSetting(SettingsManager.SEARCH_CALCULATOR, false)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        settingsManager.prefs.edit().clear().commit()

        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertFalse(settingsManager.prefs.contains("search_from_selected_text"))
        assertEquals(false, settingsManager[SettingsManager.SEARCH_CALCULATOR])
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(entry))

        db.close()
    }

    @Test
    fun theOldSwitchIsDroppedAtStart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsManager = SettingsManager(context)
        settingsManager.prefs.edit().putBoolean("search_from_selected_text", false).commit()

        settingsManager.migrateToSingleStore()
        assertFalse(settingsManager.prefs.contains("search_from_selected_text"))
        // Running it again with nothing left to drop is fine.
        settingsManager.migrateToSingleStore()
        assertTrue(settingsManager.prefs.all.keys.none { it in SettingsManager.REMOVED_NAMES })
    }
}
