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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * A restore may switch privacy opt-ins off but never on, and never moves a phone off Tor: a file someone else made
 * must not be able to start web traffic, read other apps' media sessions, or route searches past Tor Browser.
 */
@RunWith(RobolectricTestRunner::class)
class RestorePrivacyGuardsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), IntelligentSearchDatabase::class.java
    ).allowMainThreadQueries().build()
    private lateinit var settings: SettingsManager
    private lateinit var backup: BackupManager
    private val file get() = File(context.cacheDir, "restore_privacy.json")
    private val uri get() = Uri.fromFile(file)

    private val optIns = listOf(SettingsManager.SEARCH_WEB, SettingsManager.SEARCH_NOTIFICATIONS)

    @Before fun setUp() {
        prefs.edit().clear().commit()
        settings = SettingsManager(context)
        backup = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
    }

    @After fun tearDown() = db.close()

    @Test
    fun webAndNotificationSearchCanBeSwitchedOffButNeverOn() = runBlocking {
        for (key in optIns) settings.updateSetting(key, true)
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()
        for (key in optIns) settings.updateSetting(key, false)
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()
        for (key in optIns) assertEquals("${key.name} restored on", false, settings[key])

        // Backed up off: restoring switches them off.
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()
        for (key in optIns) settings.updateSetting(key, true)
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()
        for (key in optIns) assertEquals(key.name, false, settings[key])
    }

    @Test
    fun aPhoneOnTorStaysOnTor() = runBlocking {
        settings.updateSetting(SettingsManager.SEARCH_ENGINE, "Google")
        settings.updateSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_URL, "https://search.example.com/?q=%s")
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()

        settings.updateSetting(SettingsManager.SEARCH_ENGINE, "Tor Project")
        settings.updateSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_URL, "")
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals("Tor Project", settings[SettingsManager.SEARCH_ENGINE])
        assertEquals("", settings[SettingsManager.CUSTOM_SEARCH_ENGINE_URL])
    }

    @Test
    fun anOldSnapshotOnlyBackupCantMoveAPhoneOffTorEither() = runBlocking {
        writeSchemaOneBackup(mapOf("searchEngine" to "Bing", "customSearchEngineUrl" to "https://search.example.com/?q=%s"))
        settings.updateSetting(SettingsManager.SEARCH_ENGINE, "Tor Browser")
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()
        assertEquals("Tor Browser", settings[SettingsManager.SEARCH_ENGINE])
        assertEquals("", settings[SettingsManager.CUSTOM_SEARCH_ENGINE_URL])
    }

    @Test
    fun torIsRestoredOntoAPhoneThatIsntOnItAndOtherEnginesStillRestore() = runBlocking {
        settings.updateSetting(SettingsManager.SEARCH_ENGINE, "Tor Project")
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()
        settings.updateSetting(SettingsManager.SEARCH_ENGINE, "Google")
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()
        assertEquals("Tor Project", settings[SettingsManager.SEARCH_ENGINE])

        settings.updateSetting(SettingsManager.SEARCH_ENGINE, "Bing")
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()
        settings.updateSetting(SettingsManager.SEARCH_ENGINE, "DuckDuckGo")
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()
        assertEquals("Bing", settings[SettingsManager.SEARCH_ENGINE])
    }

    /** A passphrase backup in the oldest format, which holds only the settings snapshot. */
    private fun writeSchemaOneBackup(preferencesMap: Map<String, String>) {
        val payload = JSONObject()
            .put("schemaVersion", 1)
            .put("exportTimestampMs", 1L)
            .put("appVersionCode", 100)
            .put("preferencesMap", JSONObject(preferencesMap))
            .put("customBangsJson", "[]")
        val salt = BackupCryptoEngine.generateRandomSalt()
        val iv = BackupCryptoEngine.generateRandomIv()
        val key = BackupCryptoEngine.deriveKeyFromPassphrase(PASSPHRASE.toCharArray(), salt)
        val cipherBytes = BackupCryptoEngine.encryptPayload(payload.toString(), key, iv)
        val envelope = JSONObject()
            .put("format", "INTELLIGENT_SEARCH_ENCRYPTED_BACKUP")
            .put("schemaVersion", 1)
            .put("isHardwareBacked", false)
            .put(
                "kdf",
                JSONObject().put("iterations", BackupCryptoEngine.PBKDF2_ITERATIONS)
                    .put("saltBase64", BackupCryptoEngine.encodeBase64(salt))
            )
            .put("cipher", JSONObject().put("ivBase64", BackupCryptoEngine.encodeBase64(iv)))
            .put("encryptedPayloadBase64", BackupCryptoEngine.encodeBase64(cipherBytes))
            .put("payloadSha256", BackupCryptoEngine.calculateSha256(cipherBytes))
        file.writeText(envelope.toString())
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery"
    }
}
