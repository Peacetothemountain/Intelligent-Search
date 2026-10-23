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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Search Sources' Calendar, Notifications and Android Settings switches are gone: the calendar is searched whenever
 * Android allows it, notifications whenever notification access is granted, and Android Settings pages always. What
 * an older build stored is dropped at start, and a backup never brings it back.
 */
@RunWith(RobolectricTestRunner::class)
class RetiredSourceSwitchesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val retired = listOf("search.calendar", "search_calendar", "search.notifications", "search.android.settings")

    @Test
    fun theSwitchesAreGone() {
        for (name in retired) {
            assertNull("$name is not declared", SettingsManager.keyFor(name))
            assertNull("$name has no backup type", BackupPreferenceSchema.expectedType(name))
            assertTrue("$name is dropped and never restored", name in SettingsManager.REMOVED_NAMES)
        }
    }

    @Test
    fun anOlderBuildsValuesAreDroppedAtStart() = runBlocking {
        val settings = SettingsManager(context)
        val edit = settings.prefs.edit()
        for (name in retired) edit.putBoolean(name, false)
        edit.commit()
        settings.migrateToSingleStore()
        for (name in retired) assertFalse("$name is dropped", settings.prefs.contains(name))
    }

    @Test
    fun aBackupNeverRestoresThem() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backup = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "retired_sources.json"))

        // A file made by an older build still carries the switches, off.
        val edit = settings.prefs.edit()
        for (name in retired) edit.putBoolean(name, false)
        edit.commit()
        backup.exportToFile(uri, "correct horse battery").getOrThrow()
        val clear = settings.prefs.edit()
        for (name in retired) clear.remove(name)
        clear.commit()
        backup.importFromFile(uri, "correct horse battery").getOrThrow()
        for (name in retired) assertFalse("$name is not restored", settings.prefs.contains(name))

        db.close()
    }
}
