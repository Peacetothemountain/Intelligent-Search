package com.pixel.intelligentsearch.core.ai

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.backup.BackupCryptoEngine
import com.pixel.intelligentsearch.core.backup.BackupManager
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.security.AiKeyVault
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
 * The API keys stay on the phone: encrypted at rest, never in the settings a backup exports (checked in the decrypted
 * backup itself), shown masked, and gone after Remove Key. The AI Answers choices travel in backups, but a backup can
 * never switch the feature on.
 */
@RunWith(RobolectricTestRunner::class)
class AiKeyStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val keys = SoftwareAiKeys()
    private val vault = AiKeyVault(context, keys)
    private val apiKey = "sk-ant-api03-SECRET-0123456789abcdef"

    @Test
    fun theKeyIsStoredEncryptedAndRemovable() {
        assertTrue(vault.save(AiProvider.ANTHROPIC, apiKey))
        assertTrue(vault.has(AiProvider.ANTHROPIC))
        assertFalse(vault.has(AiProvider.OPENAI))
        assertEquals(apiKey, vault.get(AiProvider.ANTHROPIC))

        // Nothing readable on disk: the stored value is ciphertext.
        val stored = context.getSharedPreferences(AiKeyVault.PREFS_FILE, Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(apiKey in stored)
        assertFalse("SECRET" in stored)

        assertTrue(vault.remove(AiProvider.ANTHROPIC))
        assertFalse(vault.has(AiProvider.ANTHROPIC))
        assertNull(vault.get(AiProvider.ANTHROPIC))
        // With no key left, the Keystore key goes too.
        assertNull(keys.existing())
    }

    @Test
    fun theKeyIsShownMasked() {
        assertEquals("••••••••cdef", AiKeyVault.mask(apiKey))
        // A short key shows nothing of itself.
        assertEquals("••••••••", AiKeyVault.mask("short"))
        vault.save(AiProvider.OPENAI, apiKey)
        assertEquals("••••••••cdef", vault.masked(AiProvider.OPENAI))
    }

    @Test
    fun theExportedBackupHoldsTheChoicesButNeverTheKey() = runBlocking {
        val settings = SettingsManager(context)
        settings.prefs.edit()
            .put(SettingsManager.AI_ANSWERS_ENABLED, true)
            .put(SettingsManager.AI_PROVIDER, "openai")
            .put(SettingsManager.AI_MODEL, "gpt-5-mini")
            .commit()
        vault.save(AiProvider.OPENAI, apiKey)

        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val backups = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "ai.json"))
        val passphrase = "correct horse battery"
        backups.exportToFile(uri, passphrase).getOrThrow()

        // Decrypt the file as a restore would and read what it holds.
        val envelope = backups.inspectBackupEnvelope(uri).getOrThrow()
        val key = BackupCryptoEngine.deriveKeyFromPassphrase(
            passphrase.toCharArray(), BackupCryptoEngine.decodeBase64(envelope.kdf!!.saltBase64), envelope.kdf!!.iterations
        )
        val json = BackupCryptoEngine.decryptPayload(
            BackupCryptoEngine.decodeBase64(envelope.encryptedPayloadBase64), key, BackupCryptoEngine.decodeBase64(envelope.cipher.ivBase64)
        )
        assertTrue(json, json.contains("ai.answers.provider"))
        assertTrue(json.contains("gpt-5-mini"))
        assertFalse("the key is in the backup", json.contains(apiKey))
        assertFalse(json.contains("SECRET"))
        assertFalse(json.contains("ai_key_"))

        // Restoring never switches AI Answers on: it is a privacy opt-in.
        settings.prefs.edit().put(SettingsManager.AI_ANSWERS_ENABLED, false).put(SettingsManager.AI_MODEL, "").commit()
        backups.importFromFile(uri, passphrase).getOrThrow()
        assertFalse(settings[SettingsManager.AI_ANSWERS_ENABLED])
        assertEquals("gpt-5-mini", settings[SettingsManager.AI_MODEL])
        db.close()
    }
}
