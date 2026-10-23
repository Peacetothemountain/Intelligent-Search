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
 * Contact actions have no switch: contact results always offer Call, Message, Email and the chat apps. The old
 * Contact Actions switch an older build stored is dropped at start, and a backup never brings it back.
 */
@RunWith(RobolectricTestRunner::class)
class ContactActionsSettingTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theSwitchIsGone() {
        assertNull(SettingsManager.keyFor("contact_actions"))
        assertTrue("contact_actions" in SettingsManager.REMOVED_NAMES)
    }

    @Test
    fun anOldStoredValueIsDroppedAtStart() = runBlocking {
        val settings = SettingsManager(context)
        settings.prefs.edit().putBoolean("contact_actions", false).commit()
        settings.migrateToSingleStore()
        assertFalse(settings.prefs.contains("contact_actions"))
    }

    @Test
    fun aBackupNeverRestoresTheOldSwitch() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backup = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "contact_actions.json"))

        // A file made by an older build still carries the switch, off.
        settings.prefs.edit().putBoolean("contact_actions", false).commit()
        backup.exportToFile(uri, "correct horse battery").getOrThrow()
        settings.prefs.edit().remove("contact_actions").commit()
        backup.importFromFile(uri, "correct horse battery").getOrThrow()
        assertFalse(settings.prefs.contains("contact_actions"))

        db.close()
    }
}
