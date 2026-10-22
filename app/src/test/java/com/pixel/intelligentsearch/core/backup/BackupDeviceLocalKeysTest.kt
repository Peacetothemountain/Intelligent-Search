package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Folders and photos the user picked are granted to this phone only: a backup leaves them out, and restoring one
 * (even from a version that still included them) keeps this phone's own lists.
 */
@RunWith(RobolectricTestRunner::class)
class BackupDeviceLocalKeysTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
    private val settingsManager = SettingsManager(context)
    private val backupManager = BackupManager(context, settingsManager, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
    private val file = File(context.cacheDir, "backup.json")

    @After
    fun closeDatabase() = db.close()

    @Test
    fun pickedFoldersAndPhotosStayOnThisPhone() = runBlocking {
        prefs.edit()
            .putStringSet(UserGrantedFiles.KEY_FOLDER_URIS, setOf(FOLDER_ON_THE_OLD_PHONE))
            .putStringSet(UserGrantedFiles.KEY_MEDIA_URIS, setOf(PHOTO_ON_THE_OLD_PHONE))
            .putString(LEGACY_FOLDER_KEY, FOLDER_ON_THE_OLD_PHONE)
            .putInt(LEGACY_PHOTO_COUNT_KEY, 1)
            .commit()

        val exported = backupManager.createBackupPayload().sharedPreferencesJson
        for (key in listOf(UserGrantedFiles.KEY_FOLDER_URIS, UserGrantedFiles.KEY_MEDIA_URIS, LEGACY_FOLDER_KEY, LEGACY_PHOTO_COUNT_KEY)) {
            assertFalse("$key is left out of the backup", JSONObject(exported).has(key))
        }
        backupManager.exportToFile(Uri.fromFile(file), PASSPHRASE).getOrThrow()

        prefs.edit()
            .putStringSet(UserGrantedFiles.KEY_FOLDER_URIS, setOf(FOLDER_ON_THIS_PHONE))
            .putStringSet(UserGrantedFiles.KEY_MEDIA_URIS, setOf(PHOTO_ON_THIS_PHONE))
            .remove(LEGACY_FOLDER_KEY)
            .remove(LEGACY_PHOTO_COUNT_KEY)
            .commit()
        backupManager.importFromFile(Uri.fromFile(file), PASSPHRASE).getOrThrow()

        assertEquals(setOf(FOLDER_ON_THIS_PHONE), UserGrantedFiles.folderUris(prefs))
        assertEquals(setOf(PHOTO_ON_THIS_PHONE), UserGrantedFiles.mediaUris(prefs))
    }

    @Test
    fun aBackupFromAVersionThatIncludedThemDoesNotReplaceThem() = runBlocking {
        prefs.edit()
            .putStringSet(UserGrantedFiles.KEY_FOLDER_URIS, setOf(FOLDER_ON_THIS_PHONE))
            .putStringSet(UserGrantedFiles.KEY_MEDIA_URIS, setOf(PHOTO_ON_THIS_PHONE))
            .commit()
        writeBackup(
            JSONObject()
                .put(UserGrantedFiles.KEY_FOLDER_URIS, typed("STRING_SET", JSONArray(listOf(FOLDER_ON_THE_OLD_PHONE))))
                .put(UserGrantedFiles.KEY_MEDIA_URIS, typed("STRING_SET", JSONArray(listOf(PHOTO_ON_THE_OLD_PHONE))))
                .put(LEGACY_FOLDER_KEY, typed("STRING", FOLDER_ON_THE_OLD_PHONE))
                .put(LEGACY_PHOTO_COUNT_KEY, typed("INT", 1))
                .put(SettingsManager.BACKGROUND_BLUR.name, typed("INT", 33))
        )

        backupManager.importFromFile(Uri.fromFile(file), PASSPHRASE).getOrThrow()

        assertEquals("the rest of the file was restored", 33, settingsManager[SettingsManager.BACKGROUND_BLUR])
        assertEquals(setOf(FOLDER_ON_THIS_PHONE), UserGrantedFiles.folderUris(prefs))
        assertEquals(setOf(PHOTO_ON_THIS_PHONE), UserGrantedFiles.mediaUris(prefs))
        assertFalse(prefs.contains(LEGACY_FOLDER_KEY))
        assertFalse(prefs.contains(LEGACY_PHOTO_COUNT_KEY))
    }

    private fun typed(type: String, value: Any): JSONObject = JSONObject().put("type", type).put("value", value)

    /** A passphrase backup with [sharedPreferences] as its typed settings, as an older version wrote them. */
    private fun writeBackup(sharedPreferences: JSONObject) {
        val payload = JSONObject()
            .put("schemaVersion", 2)
            .put("preferencesMap", JSONObject())
            .put("customBangsJson", "[]")
            .put("searchHistory", JSONArray())
            .put("hiddenApps", JSONArray())
            .put("sharedPreferencesJson", sharedPreferences.toString())
        val salt = BackupCryptoEngine.generateRandomSalt()
        val key = BackupCryptoEngine.deriveKeyFromPassphrase(PASSPHRASE.toCharArray(), salt, KDF_ITERATIONS)
        val iv = BackupCryptoEngine.generateRandomIv()
        val encrypted = BackupCryptoEngine.encryptPayload(payload.toString(), key, iv)
        val envelope = JSONObject()
            .put("format", "INTELLIGENT_SEARCH_ENCRYPTED_BACKUP")
            .put("schemaVersion", 2)
            .put("isHardwareBacked", false)
            .put("kdf", JSONObject().put("iterations", KDF_ITERATIONS).put("saltBase64", BackupCryptoEngine.encodeBase64(salt)))
            .put("cipher", JSONObject().put("ivBase64", BackupCryptoEngine.encodeBase64(iv)))
            .put("encryptedPayloadBase64", BackupCryptoEngine.encodeBase64(encrypted))
            .put("payloadSha256", BackupCryptoEngine.calculateSha256(encrypted))
        file.writeText(envelope.toString())
    }

    private companion object {
        const val PASSPHRASE = "correct horse battery"
        const val KDF_ITERATIONS = 10_000
        const val LEGACY_FOLDER_KEY = "search.files.uri"
        const val LEGACY_PHOTO_COUNT_KEY = "search.files.photo_count"
        const val FOLDER_ON_THE_OLD_PHONE = "content://com.android.externalstorage.documents/tree/primary%3AOld"
        const val PHOTO_ON_THE_OLD_PHONE = "content://media/picker/0/com.android.providers.media.photopicker/media/1"
        const val FOLDER_ON_THIS_PHONE = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
        const val PHOTO_ON_THIS_PHONE = "content://media/picker/0/com.android.providers.media.photopicker/media/2"
    }
}
