package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The Android Settings search source is backed up and restored like the other sources. */
@RunWith(RobolectricTestRunner::class)
class AndroidSettingsSourceBackupTest {

    @Test
    fun theAndroidSettingsSourceSurvivesABackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "android_settings_source.json"))

        assertEquals(true, settingsManager.settingsFlow.first().searchAndroidSettings)
        settingsManager.updateSetting(SettingsManager.SEARCH_ANDROID_SETTINGS, false)
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()

        settingsManager.updateSetting(SettingsManager.SEARCH_ANDROID_SETTINGS, true)
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()

        assertEquals(false, settingsManager.settingsFlow.first().searchAndroidSettings)
        db.close()
    }
}
