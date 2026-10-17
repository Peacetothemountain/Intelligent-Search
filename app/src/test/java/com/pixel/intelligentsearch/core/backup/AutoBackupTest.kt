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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AutoBackupTest {

    @Test
    fun anAutomaticBackupRestoresWithJustThePassphrase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settingsManager = SettingsManager(context)
        val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })

        settingsManager.updateSetting(SettingsManager.BACKGROUND_BLUR, 41)
        // What the weekly job does: encrypt with the key derived once from the passphrase and its salt.
        val salt = BackupCryptoEngine.generateRandomSalt()
        val key = BackupCryptoEngine.deriveKeyFromPassphrase("correct horse battery".toCharArray(), salt)
        val file = File(context.cacheDir, "auto.json")
        backupManager.exportWithDerivedKey(Uri.fromFile(file), key, salt).getOrThrow()
        assertTrue(file.length() > 0)

        // Restoring on any phone needs only the passphrase.
        settingsManager.updateSetting(SettingsManager.BACKGROUND_BLUR, 90)
        assertTrue(backupManager.importFromFile(Uri.fromFile(file), "wrong passphrase").isFailure)
        backupManager.importFromFile(Uri.fromFile(file), "correct horse battery").getOrThrow()
        assertEquals(41, settingsManager.settingsFlow.first().backgroundBlur)
        db.close()
    }

    @Test
    fun onlyTheOldestAutomaticBackupsArePruned() {
        val names = listOf(
            "IntelligentSearch-auto-2026-09-01-0900.json",
            "IntelligentSearch-auto-2026-10-06-0900.json",
            "intelligent_search_backup_1760000000000.json", // saved by hand: never an automatic backup
            "IntelligentSearch-auto-2026-09-22-0900.json",
            "IntelligentSearch-auto-2026-10-13-0900.json",
            "IntelligentSearch-auto-2026-09-29-0900.json",
        )
        val ours = names.filter(AutoBackup::isAutomaticBackupName).map { it to it }
        assertFalse(AutoBackup.isAutomaticBackupName("intelligent_search_backup_1760000000000.json"))
        val deleted = AutoBackup.oldestBeyond(ours, keep = 4).map { it.second }
        assertEquals(listOf("IntelligentSearch-auto-2026-09-01-0900.json"), deleted)
    }

    @Test
    fun turnedOffUntilSetUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val autoBackup = AutoBackup(context)
        assertFalse(autoBackup.isEnabled)
        assertEquals(null, autoBackup.lastFailure)
    }
}
