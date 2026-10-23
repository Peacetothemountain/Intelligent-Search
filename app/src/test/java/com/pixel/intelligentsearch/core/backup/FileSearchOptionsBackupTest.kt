package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.OpenWithChoice
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Open Files With is backed up; the skipped subfolders, like the chosen folders, stay on this phone. File search covers
 * every file type: the old File Types choice is dropped and never restored.
 */
@RunWith(RobolectricTestRunner::class)
class FileSearchOptionsBackupTest {

    @Test
    fun defaultsOpenEveryTypeInItsDefaultApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsManager(context)
        settings.prefs.edit().clear().commit()
        for (group in FileTypeGroup.entries) {
            assertEquals(OpenWithChoice.DefaultApp, OpenWithChoice.parse(settings[SettingsManager.FILES_OPEN_WITH.getValue(group)]))
        }
        assertTrue(settings[SettingsManager.FILES_EXCLUDED_FOLDERS].isEmpty())
    }

    @Test
    fun theNewSettingsAreDeclaredWithTheTypesTheAppReads() {
        for (key in SettingsManager.FILES_OPEN_WITH.values) {
            assertTrue(key in SettingsManager.ALL)
            assertEquals("STRING", BackupPreferenceSchema.expectedType(key.name))
        }
        assertEquals(FileTypeGroup.entries.size, SettingsManager.FILES_OPEN_WITH.size)
        assertNull(SettingsManager.keyFor("search.files.excluded_types"))
        assertTrue("search.files.excluded_types" in SettingsManager.REMOVED_NAMES)
        assertTrue(SettingsManager.FILES_EXCLUDED_FOLDERS.name in UserGrantedFiles.DEVICE_LOCAL_KEYS)
        // Changing what is searched rebuilds the file index.
        assertTrue(SettingsManager.FILES_EXCLUDED_FOLDERS.name in UserGrantedFiles.INDEX_KEYS)
    }

    @Test
    fun aBackupRestoresTheAppsButNotTheSkippedFoldersOrTheOldFileTypes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backupManager = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "file_options.json"))
        val pdf = SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.PDF)
        val video = SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.VIDEO)
        val oldPhoneFolder = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FOld"
        val thisPhoneFolder = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FJunk"

        settings.prefs.edit().clear().commit()
        settings.set(pdf, OpenWithChoice.App("com.adobe.reader").stored)
        settings.set(video, OpenWithChoice.AskEveryTime.stored)
        // A file from an older build may still carry the removed File Types choice.
        settings.prefs.edit().putStringSet("search.files.excluded_types", setOf(FileTypeGroup.APKS.id)).commit()
        settings.set(SettingsManager.FILES_EXCLUDED_FOLDERS, setOf(oldPhoneFolder))
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()

        settings.prefs.edit().clear().commit()
        settings.set(SettingsManager.FILES_EXCLUDED_FOLDERS, setOf(thisPhoneFolder))
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()

        assertEquals(OpenWithChoice.App("com.adobe.reader"), OpenWithChoice.parse(settings[pdf]))
        assertEquals(OpenWithChoice.AskEveryTime, OpenWithChoice.parse(settings[video]))
        assertFalse(settings.prefs.contains("search.files.excluded_types"))
        assertEquals(setOf(thisPhoneFolder), settings[SettingsManager.FILES_EXCLUDED_FOLDERS])
        db.close()
    }
}
