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
 * The Quick App Panel switch and Max Quick Panel Apps keep their keys (quick_search_horizontal and
 * shortcut_results_count), so they round-trip through a backup. A backup from before the max capped chosen apps
 * restores with every chosen app still showing.
 */
@RunWith(RobolectricTestRunner::class)
class QuickPanelBackupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), IntelligentSearchDatabase::class.java
    ).allowMainThreadQueries().build()
    private lateinit var settings: SettingsManager
    private lateinit var backup: BackupManager
    private val file get() = File(context.cacheDir, "quick_panel.json")
    private val uri get() = Uri.fromFile(file)
    private val eightApps = (1..8).joinToString(",") { "com.example.app$it" }

    @Before fun setUp() {
        prefs.edit().clear().commit()
        settings = SettingsManager(context)
        backup = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
    }

    @After fun tearDown() = db.close()

    @Test
    fun theSwitchAndTheMaxRoundTrip() = runBlocking {
        settings.migrateToSingleStore()
        settings.set(SettingsManager.QUICK_SEARCH_HORIZONTAL, true)
        settings.set(SettingsManager.SHORTCUT_RESULTS_COUNT, 3)
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()

        settings.set(SettingsManager.QUICK_SEARCH_HORIZONTAL, false)
        settings.set(SettingsManager.SHORTCUT_RESULTS_COUNT, 11)
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(true, settings[SettingsManager.QUICK_SEARCH_HORIZONTAL])
        // Below the six chosen apps, and kept: the file is from a version where the max already capped them.
        assertEquals(3, settings[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    @Test
    fun theDefaultsRoundTrip() = runBlocking {
        settings.migrateToSingleStore()
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()

        settings.set(SettingsManager.QUICK_SEARCH_HORIZONTAL, true)
        settings.set(SettingsManager.SHORTCUT_RESULTS_COUNT, 11)
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(false, settings[SettingsManager.QUICK_SEARCH_HORIZONTAL])
        assertEquals(6, settings[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    @Test
    fun anOldBackupWithMoreChosenAppsThanItsMaxShowsThemAll() = runBlocking {
        writeOldBackup(contextAware = false)
        settings.migrateToSingleStore()

        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(eightApps, settings[SettingsManager.SEARCH_PILLS])
        assertEquals(true, settings[SettingsManager.QUICK_SEARCH_HORIZONTAL])
        assertEquals(8, settings[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    @Test
    fun anOldContextAwareBackupKeepsItsMax() = runBlocking {
        writeOldBackup(contextAware = true)
        settings.migrateToSingleStore()

        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(6, settings[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }

    /** A passphrase backup as an older version wrote it: eight chosen apps, the default max of six. */
    private fun writeOldBackup(contextAware: Boolean) {
        fun typed(type: String, value: Any) = JSONObject().put("type", type).put("value", value)
        val sharedPreferences = JSONObject()
            .put("search_pills", typed("STRING", eightApps))
            .put("shortcut_results_count", typed("INT", 6))
            .put("quick_search_horizontal", typed("BOOLEAN", true))
            .put("context_aware_quick_apps", typed("BOOLEAN", contextAware))
            .put("search.shortcuts", typed("BOOLEAN", true))
        val preferencesMap = mapOf(
            "searchPills" to eightApps, "shortcutResultsCount" to "6", "quickSearchHorizontal" to "true", "searchShortcuts" to "true"
        )
        val payload = JSONObject()
            .put("schemaVersion", 2)
            .put("exportTimestampMs", 1L)
            .put("appVersionCode", 100)
            .put("preferencesMap", JSONObject(preferencesMap))
            .put("customBangsJson", "[]")
            .put("sharedPreferencesJson", sharedPreferences.toString())
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
