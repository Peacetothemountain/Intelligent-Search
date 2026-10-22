package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.HardwareSecurityDetector
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild
import java.io.File

/**
 * The security hardware a passphrase or automatic backup names in its header, which the restore dialog shows: where
 * this phone keeps its keys, never a separate security chip it doesn't use. (The test phones have no StrongBox.)
 */
@RunWith(RobolectricTestRunner::class)
class BackupEnvelopeHeaderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
    private val backupManager = BackupManager(
        context, SettingsManager(context), db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { }
    )
    private val file = File(context.cacheDir, "backup.json")

    @After
    fun closeDatabase() = db.close()

    /** The chip named by a backup made with the passphrase on the Backup page. */
    private suspend fun passphraseBackupChip(): String {
        backupManager.exportToFile(Uri.fromFile(file), "long passphrase 1").getOrThrow()
        return backupManager.inspectBackupEnvelope(Uri.fromFile(file)).getOrThrow().hardwareChip.orEmpty()
    }

    /** The chip named by an automatic backup, made with the key saved from the passphrase. */
    private suspend fun automaticBackupChip(): String {
        val salt = BackupCryptoEngine.generateRandomSalt()
        val key = BackupCryptoEngine.deriveKeyFromPassphrase("long passphrase 1".toCharArray(), salt)
        backupManager.exportWithDerivedKey(Uri.fromFile(file), key, salt).getOrThrow()
        return backupManager.inspectBackupEnvelope(Uri.fromFile(file)).getOrThrow().hardwareChip.orEmpty()
    }

    @Test
    fun aQualcommPhoneWithoutStrongBoxNamesItsTee() = runBlocking {
        ShadowBuild.setManufacturer("Example")
        ShadowBuild.setSystemOnChipManufacturer("QTI")
        ShadowBuild.setSystemOnChipModel("SM8650")
        val info = HardwareSecurityDetector.detect(context)

        for (chip in listOf(passphraseBackupChip(), automaticBackupChip())) {
            assertEquals(info.keyHolderName, chip)
            assertEquals("Qualcomm Secure Execution Environment (QTEE)", chip)
            assertFalse(chip, chip.contains("SPU"))
        }
    }

    @Test
    fun aOnePlusPhoneWithoutStrongBoxNamesItsTee() = runBlocking {
        ShadowBuild.setManufacturer("OnePlus")
        ShadowBuild.setBrand("OnePlus")
        val info = HardwareSecurityDetector.detect(context)

        for (chip in listOf(passphraseBackupChip(), automaticBackupChip())) {
            assertEquals(info.keyHolderName, chip)
            assertFalse(chip, chip.contains("StrongBox"))
            assertFalse(chip, chip.contains("Security Element"))
        }
    }
}
