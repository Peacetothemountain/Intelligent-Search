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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Open Files With and File Types are backed up; the skipped subfolders, like the chosen folders, stay on this phone. */
@RunWith(RobolectricTestRunner::class)
class FileSearchOptionsBackupTest {

    @Test
    fun defaultsOpenEveryTypeInItsDefaultAppAndSearchEveryType() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsManager(context)
        settings.prefs.edit().clear().commit()
        for (group in FileTypeGroup.entries) {
            assertEquals(OpenWithChoice.DefaultApp, OpenWithChoice.parse(settings[SettingsManager.FILES_OPEN_WITH.getValue(group)]))
        }
        assertTrue(settings[SettingsManager.FILES_EXCLUDED_TYPES].isEmpty())
        assertTrue(UserGrantedFiles.excludedTypes(settings.prefs).isEmpty())
        assertTrue(settings[SettingsManager.FILES_EXCLUDED_FOLDERS].isEmpty())
    }

    @Test
    fun theNewSettingsAreDeclaredWithTheTypesTheAppReads() {
        for (key in SettingsManager.FILES_OPEN_WITH.values) {
            assertTrue(key in SettingsManager.ALL)
            assertEquals("STRING", BackupPreferenceSchema.expectedType(key.name))
        }
        assertEquals(FileTypeGroup.entries.size, SettingsManager.FILES_OPEN_WITH.size)
        assertTrue(SettingsManager.FILES_EXCLUDED_TYPES in SettingsManager.ALL)
        assertEquals("STRING_SET", BackupPreferenceSchema.expectedType(SettingsManager.FILES_EXCLUDED_TYPES.name))
        assertTrue(SettingsManager.FILES_EXCLUDED_FOLDERS.name in UserGrantedFiles.DEVICE_LOCAL_KEYS)
        // Changing what is searched rebuilds the file index.
        assertTrue(SettingsManager.FILES_EXCLUDED_TYPES.name in UserGrantedFiles.INDEX_KEYS)
        assertTrue(SettingsManager.FILES_EXCLUDED_FOLDERS.name in UserGrantedFiles.INDEX_KEYS)
    }

    @Test
    fun aBackupRestoresTheAppsAndTypesButNotTheSkippedFolders() = runBlocking {
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
        settings.set(SettingsManager.FILES_EXCLUDED_TYPES, setOf(FileTypeGroup.APKS.id, FileTypeGroup.ARCHIVES.id))
        settings.set(SettingsManager.FILES_EXCLUDED_FOLDERS, setOf(oldPhoneFolder))
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()

        settings.prefs.edit().clear().commit()
        settings.set(SettingsManager.FILES_EXCLUDED_FOLDERS, setOf(thisPhoneFolder))
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()

        assertEquals(OpenWithChoice.App("com.adobe.reader"), OpenWithChoice.parse(settings[pdf]))
        assertEquals(OpenWithChoice.AskEveryTime, OpenWithChoice.parse(settings[video]))
        assertEquals(setOf(FileTypeGroup.APKS, FileTypeGroup.ARCHIVES), UserGrantedFiles.excludedTypes(settings.prefs))
        assertEquals(setOf(thisPhoneFolder), settings[SettingsManager.FILES_EXCLUDED_FOLDERS])
        db.close()
    }
}
