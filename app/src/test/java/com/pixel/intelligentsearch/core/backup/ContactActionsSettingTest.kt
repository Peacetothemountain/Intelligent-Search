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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The Contact Actions switch: on out of the box, read into the settings snapshot, and carried by backups. */
@RunWith(RobolectricTestRunner::class)
class ContactActionsSettingTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun onByDefaultAndReadIntoTheSnapshot() {
        val settings = SettingsManager(context)
        assertTrue(settings.getInitialSettings().contactActions)
        settings.set(SettingsManager.CONTACT_ACTIONS, false)
        assertFalse(settings.getInitialSettings().contactActions)
    }

    @Test
    fun registeredWithTheBackupSchema() {
        assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(SettingsManager.CONTACT_ACTIONS.name))
        assertTrue(SettingsManager.CONTACT_ACTIONS in SettingsManager.ALL)
        assertEquals(SettingsManager.CONTACT_ACTIONS, SettingsManager.keyFor("contact_actions"))
    }

    @Test
    fun aBackupRestoresTheSwitch() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backup = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "contact_actions.json"))

        settings.set(SettingsManager.CONTACT_ACTIONS, false)
        backup.exportToFile(uri, "correct horse battery").getOrThrow()
        settings.set(SettingsManager.CONTACT_ACTIONS, true)
        backup.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settings[SettingsManager.CONTACT_ACTIONS])

        db.close()
    }
}
