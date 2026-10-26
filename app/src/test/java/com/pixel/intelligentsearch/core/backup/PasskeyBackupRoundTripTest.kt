package com.pixel.intelligentsearch.core.backup

import android.app.Activity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import javax.crypto.KeyGenerator

/**
 * Backups made with a passkey set up (schema 2): one file opens with the passkey, or with the passphrase when one was
 * given, and with nothing else. The passkey is a fake with a fixed PRF output, as a password manager would give.
 */
@RunWith(RobolectricTestRunner::class)
class PasskeyBackupRoundTripTest {

    /** A password manager whose passkey gives [output] for any salt (enough to tell two passkeys apart). */
    private class FakePrf(val output: ByteArray, val credentialId: String = "cred-1") : PasskeyPrf {
        var asked = 0
        override suspend fun create(activity: Activity, userId: ByteArray, prfSalt: ByteArray) =
            PasskeyPrfResult(credentialId, output.copyOf())

        override suspend fun evaluate(activity: Activity, credentialIds: List<String>, prfSalt: ByteArray): PasskeyPrfResult {
            asked++
            if (credentialId !in credentialIds) throw PasskeyException("not here")
            return PasskeyPrfResult(credentialId, output.copyOf())
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }
    private val wrapKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private lateinit var settingsManager: SettingsManager
    private lateinit var backupManager: BackupManager
    private lateinit var prf: FakePrf
    private lateinit var passkey: BackupPasskey
    private val file get() = File(context.cacheDir, "passkey-backup.json")
    private val uri get() = Uri.fromFile(file)

    @Before
    fun setUp() = runBlocking {
        context.getSharedPreferences("backup_passkey_state", Context.MODE_PRIVATE).edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        settingsManager = SettingsManager(context)
        backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        prf = FakePrf(ByteArray(32) { 42 })
        passkey = BackupPasskey(context, { wrapKey }, prf)
        backupManager.backupPasskey = passkey
        passkey.create(activity)
        settingsManager.updateSetting(SettingsManager.HIDDEN_APPS, setOf("com.example.secret"))
    }

    private fun hidden(): Set<String> = runBlocking { settingsManager.settingsFlow.first().hiddenApps }

    @Test
    fun aPasskeyBackupOpensWithThePasskeyAlone() = runBlocking {
        backupManager.exportToFile(uri, null).getOrThrow()
        assertFalse("not plaintext", file.readText().contains("com.example.secret"))
        val envelope = backupManager.inspectBackupEnvelope(uri).getOrThrow()
        assertEquals(2, envelope.schemaVersion)
        assertNotNull(envelope.passkeySlot)
        assertNull(envelope.passphraseSlot)
        assertFalse(envelope.opensWithPassphrase)
        assertEquals("cred-1", envelope.passkeySlot?.credentialId)
        assertEquals(BackupPasskey.RP_ID, envelope.passkeySlot?.rpId)

        settingsManager.updateSetting(SettingsManager.HIDDEN_APPS, emptySet())
        // Restoring on another phone, which has never kept this passkey's key: the passkey is asked for.
        context.getSharedPreferences("backup_passkey_state", Context.MODE_PRIVATE).edit().clear().commit()
        val key = BackupPasskey(context, { wrapKey }, prf).unlock(activity, envelope.passkeySlot!!)
        assertEquals(1, prf.asked)
        backupManager.importWithPasskeyKey(uri, key).getOrThrow()
        assertEquals(setOf("com.example.secret"), hidden())

        // Typing a passphrase can't open it.
        val error = backupManager.importFromFile(uri, "correct horse battery").exceptionOrNull()?.message.orEmpty()
        assertTrue(error, error.contains("passkey"))
    }

    @Test
    fun thisPhonesOwnPasskeyOpensItWithoutAsking() = runBlocking {
        backupManager.exportToFile(uri, null).getOrThrow()
        val slot = backupManager.inspectBackupEnvelope(uri).getOrThrow().passkeySlot!!
        val key = passkey.unlock(activity, slot)
        assertEquals("the key kept on this phone is used", 0, prf.asked)
        backupManager.importWithPasskeyKey(uri, key).getOrThrow()
        Unit
    }

    @Test
    fun anotherPasskeyCannotOpenIt() = runBlocking {
        backupManager.exportToFile(uri, null).getOrThrow()
        val wrongKey = BackupKeySlots.passkeyKek(ByteArray(32) { 1 })
        assertEquals("That passkey doesn't open this backup.", backupManager.importWithPasskeyKey(uri, wrongKey).exceptionOrNull()?.message)
    }

    @Test
    fun withAPassphraseTooEitherOpensIt() = runBlocking {
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        val envelope = backupManager.inspectBackupEnvelope(uri).getOrThrow()
        assertNotNull(envelope.passkeySlot)
        assertNotNull(envelope.passphraseSlot)
        assertTrue(envelope.opensWithPassphrase)

        val wrong = backupManager.importFromFile(uri, "wrong passphrase").exceptionOrNull()?.message.orEmpty()
        assertTrue(wrong, wrong.contains("Incorrect passphrase"))

        settingsManager.updateSetting(SettingsManager.HIDDEN_APPS, emptySet())
        backupManager.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(setOf("com.example.secret"), hidden())
    }

    @Test
    fun automaticBackupsUseThePasskeyWithoutAPassphrase() = runBlocking {
        backupManager.exportAutomatic(uri, null).getOrThrow()
        val envelope = backupManager.inspectBackupEnvelope(uri).getOrThrow()
        assertNotNull(envelope.passkeySlot)
        assertNull(envelope.passphraseSlot)
    }

    @Test
    fun forgettingThePasskeyGoesBackToPassphraseBackups() = runBlocking {
        passkey.forget()
        backupManager.exportToFile(uri, "correct horse battery").getOrThrow()
        val envelope = backupManager.inspectBackupEnvelope(uri).getOrThrow()
        assertEquals("a schema 1 passphrase file, as before passkeys", 1, envelope.schemaVersion)
        assertTrue(envelope.keySlots.isEmpty())
        assertNotNull(envelope.kdf)
    }
}
