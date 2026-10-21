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
 * Backups made before Search Pill Opacity became linear stored 50 for an opaque pill. Restoring one keeps the pill
 * opaque; a 50 from a newer backup still means half opacity.
 */
@RunWith(RobolectricTestRunner::class)
class PillOpacityBackupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), IntelligentSearchDatabase::class.java
    ).allowMainThreadQueries().build()
    private lateinit var settings: SettingsManager
    private lateinit var backup: BackupManager
    private val uri get() = Uri.fromFile(File(context.cacheDir, "pill_opacity.json"))

    @Before fun setUp() {
        prefs.edit().clear().commit()
        settings = SettingsManager(context)
        backup = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
    }

    @After fun tearDown() = db.close()

    @Test
    fun anOldBackupsFiftyRestoresOpaque() = runBlocking {
        // Saved before this install's settings were updated: its 50 is the old opaque default.
        prefs.edit().putInt(SettingsManager.PILL_OPACITY.name, 50).commit()
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()

        settings.migrateToSingleStore()
        settings.set(SettingsManager.PILL_OPACITY, 30)
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(100, settings[SettingsManager.PILL_OPACITY])
        // The restore is final: running the migration again leaves the pill alone.
        settings.set(SettingsManager.PILL_OPACITY, 50)
        settings.migrateToSingleStore()
        assertEquals(50, settings[SettingsManager.PILL_OPACITY])
    }

    @Test
    fun anOldBackupWithOnlyTheSettingsSnapshotRestoresOpaque() = runBlocking {
        // An older version's file where the pill was never moved: only the snapshot names it, at the old default.
        writeBackup(
            schemaVersion = 2,
            preferencesMap = mapOf("pillOpacity" to "50", "backgroundBlur" to "20"),
            sharedPreferences = JSONObject().put("search.background.blur", JSONObject().put("type", "INT").put("value", 20))
        )
        settings.migrateToSingleStore()
        settings.set(SettingsManager.PILL_OPACITY, 30)

        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(100, settings[SettingsManager.PILL_OPACITY])
        assertEquals(20, settings[SettingsManager.BACKGROUND_BLUR])
    }

    @Test
    fun aSchemaOneBackupsFiftyRestoresOpaque() = runBlocking {
        writeBackup(schemaVersion = 1, preferencesMap = mapOf("pillOpacity" to "50"), sharedPreferences = null)
        settings.migrateToSingleStore()
        settings.set(SettingsManager.PILL_OPACITY, 30)

        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(100, settings[SettingsManager.PILL_OPACITY])
    }

    @Test
    fun anOldBackupsOtherValuesRestoreAsSaved() = runBlocking {
        prefs.edit().putInt(SettingsManager.PILL_OPACITY.name, 60).commit()
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()

        settings.migrateToSingleStore()
        settings.set(SettingsManager.PILL_OPACITY, 30)
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(60, settings[SettingsManager.PILL_OPACITY])
    }

    @Test
    fun aNewBackupsFiftyStaysHalfOpaque() = runBlocking {
        settings.migrateToSingleStore()
        settings.set(SettingsManager.PILL_OPACITY, 50)
        backup.exportToFile(uri, PASSPHRASE).getOrThrow()

        settings.set(SettingsManager.PILL_OPACITY, 80)
        backup.importFromFile(uri, PASSPHRASE).getOrThrow()

        assertEquals(50, settings[SettingsManager.PILL_OPACITY])
    }

    /** Writes a passphrase backup holding exactly what an older version would have put in it. */
    private fun writeBackup(schemaVersion: Int, preferencesMap: Map<String, String>, sharedPreferences: JSONObject?) {
        val payload = JSONObject()
            .put("schemaVersion", schemaVersion)
            .put("exportTimestampMs", 1L)
            .put("appVersionCode", 100)
            .put("preferencesMap", JSONObject(preferencesMap))
            .put("customBangsJson", "[]")
        if (sharedPreferences != null) payload.put("sharedPreferencesJson", sharedPreferences.toString())
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
        File(context.cacheDir, "pill_opacity.json").writeText(envelope.toString())
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery"
    }
}
