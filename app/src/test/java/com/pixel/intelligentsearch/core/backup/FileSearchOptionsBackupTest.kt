package com.pixel.intelligentsearch.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.OpenWithChoice
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Open Files With's defaults and how File Search's settings are declared. */
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
}
