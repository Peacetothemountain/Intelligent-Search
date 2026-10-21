package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.SearchPillProfiles
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** A backup can't plant a value of the wrong type under a key the app names at runtime and reads as a string. */
@RunWith(RobolectricTestRunner::class)
class BackupRuntimeKeyTypesTest {

    @Test
    fun aPillProfileOfTheWrongTypeIsNotRestored() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val backupManager = BackupManager(context, SettingsManager(context), db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "backup.json"))

        prefs.edit()
            .putInt("app_search_profile_2", 7)
            .putInt("Google Lens_custom_type", 3)
            .commit()
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        prefs.edit().remove("app_search_profile_2").remove("Google Lens_custom_type").commit()

        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()

        assertFalse(prefs.contains("app_search_profile_2"))
        assertFalse(prefs.contains("Google Lens_custom_type"))
        // Switching to the profile reads its pills with getString, which an int would crash.
        assertEquals(emptyList<String>(), SearchPillProfiles(prefs).switchTo(2))
        db.close()
    }
}
