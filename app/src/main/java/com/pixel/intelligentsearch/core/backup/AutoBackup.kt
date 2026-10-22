package com.pixel.intelligentsearch.core.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pixel.intelligentsearch.core.security.MemorySanitizer
import com.pixel.intelligentsearch.di.appEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Weekly automatic backups into a folder the user picked, encrypted with a passphrase they chose once, so a
 * backup restores on any phone (a backup tied to this phone's hardware key would be lost with the phone).
 *
 * The passphrase itself is never stored. The key derived from it is kept wrapped by a non-exportable key in this
 * phone's Keystore, so the weekly job can encrypt without asking; restoring still needs the passphrase. All of
 * this lives in its own preferences file, which backups never include.
 */
class AutoBackup internal constructor(
    private val context: Context,
    /** The Keystore key the derived key is wrapped with; tests pass a software key. */
    private val wrapKey: () -> SecretKey
) {

    // Never replaced silently: a new key could not unwrap the stored one, and the job reports that instead.
    constructor(context: Context) : this(context, {
        context.appEntryPoint.strongBoxSecurityManager().getOrCreateSymmetricKey(WRAP_KEY_ALIAS, purgeIfUnrecoverable = false).first
    })

    private val state = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)

    val isEnabled: Boolean get() = state.getBoolean(KEY_ENABLED, false) && folderUri != null && state.contains(KEY_WRAPPED)
    val folderUri: Uri? get() = state.getString(KEY_FOLDER, null)?.let(Uri::parse)
    val lastSuccessMs: Long get() = state.getLong(KEY_LAST_SUCCESS, 0L)
    /** A short reason the last run failed, or null when it succeeded (or never ran). */
    val lastFailure: String? get() = state.getString(KEY_LAST_FAILURE, null)

    /** Name of the chosen folder as the user knows it, e.g. "Backups". */
    fun folderName(): String? = folderUri?.let { uri ->
        runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast(':')
    }

    /**
     * Turns automatic backups on: keeps access to [folder], derives the key from [passphrase] (which is wiped
     * here), stores it wrapped by the Keystore and schedules the weekly job. Returns false if the key could not
     * be protected, in which case nothing is changed.
     */
    fun enable(folder: Uri, passphrase: CharArray): Boolean {
        // A backup no one can restore is worse than none: an empty or short passphrase is refused here, whatever the UI did.
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) {
            MemorySanitizer.wipe(passphrase)
            return false
        }
        val salt = BackupCryptoEngine.generateRandomSalt()
        val derived = try {
            BackupCryptoEngine.deriveKeyFromPassphrase(passphrase, salt)
        } finally {
            MemorySanitizer.wipe(passphrase)
        }
        val raw = derived.encoded
        val wrapped = try {
            wrap(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Could not protect the automatic backup key: ${e.javaClass.simpleName}")
            return false
        } finally {
            raw.fill(0)
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        state.edit {
            putBoolean(KEY_ENABLED, true)
            putString(KEY_FOLDER, folder.toString())
            putString(KEY_SALT, BackupCryptoEngine.encodeBase64(salt))
            putString(KEY_WRAPPED, BackupCryptoEngine.encodeBase64(wrapped.second))
            putString(KEY_WRAP_IV, BackupCryptoEngine.encodeBase64(wrapped.first))
            remove(KEY_LAST_FAILURE)
        }
        schedule(context)
        return true
    }

    /** Turns automatic backups off and forgets the key and folder access. Backups already written stay. */
    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        folderUri?.let { uri ->
            val settings = context.getSharedPreferences(com.pixel.intelligentsearch.core.data.SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
            val stillSearched = uri.toString() in com.pixel.intelligentsearch.core.data.UserGrantedFiles.folderUris(settings)
            val release = if (stillSearched) Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            else Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, release) }
        }
        state.edit { clear() }
    }

    /**
     * Writes one backup now and removes the oldest beyond [KEEP]. Runs on the IO dispatcher, since the Keystore and
     * the folder's provider can both block. A file that isn't written in full is deleted again, and a cancelled run
     * is passed on to the caller rather than recorded as a failure.
     */
    suspend fun runOnce(backupManager: BackupManager): Result<Unit> = withContext(Dispatchers.IO) {
        val folder = folderUri ?: return@withContext Result.failure(IllegalStateException("No backup folder"))
        val salt = state.getString(KEY_SALT, null)?.let(BackupCryptoEngine::decodeBase64)
        val wrapped = state.getString(KEY_WRAPPED, null)?.let(BackupCryptoEngine::decodeBase64)
        val iv = state.getString(KEY_WRAP_IV, null)?.let(BackupCryptoEngine::decodeBase64)
        if (salt == null || wrapped == null || iv == null) {
            return@withContext Result.failure(IllegalStateException("Automatic backup is not set up"))
        }

        var unfinished: Uri? = null
        val result = try {
            val raw = unwrap(iv, wrapped)
            val key: SecretKey = SecretKeySpec(raw, "AES")
            val writtenId = try {
                val treeDoc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
                val name = FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + FILE_SUFFIX
                val file = DocumentsContract.createDocument(context.contentResolver, treeDoc, "application/json", name)
                    ?: throw IllegalStateException("The backup folder can't be written to")
                unfinished = file
                val id = DocumentsContract.getDocumentId(file)
                backupManager.exportWithDerivedKey(file, key, salt).getOrThrow()
                unfinished = null
                id
            } finally {
                raw.fill(0)
            }
            prune(folder, writtenId)
            Result.success(Unit)
        } catch (e: Throwable) {
            // An empty or partial file can't be restored; pruning leaves empty ones alone, and a partial one would take
            // one of the KEEP places from a real backup.
            unfinished?.let { file ->
                withContext(NonCancellable) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, file) }
                }
            }
            if (e is CancellationException) throw e
            Result.failure(e)
        }
        state.edit {
            if (result.isSuccess) {
                putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
                remove(KEY_LAST_FAILURE)
            } else {
                putString(KEY_LAST_FAILURE, failureText(result.exceptionOrNull()))
            }
        }
        result
    }

    /**
     * Keeps the newest [KEEP] automatic backups in the folder; files the user saved by hand are never touched.
     *
     * An automatic file listed as empty is left alone and doesn't take one of the places: it may be another run's
     * file still being written, or a finished one whose provider hasn't caught up on its size yet. The file this
     * run just wrote ([writtenId]) always counts, since some cloud providers list it as empty until the upload ends.
     */
    private fun prune(folder: Uri, writtenId: String) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val ours = mutableListOf<Pair<String, String>>() // document id, name
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (!isAutomaticBackupName(name)) continue
                // Providers may leave the size unknown (null); only a known 0 marks a file as empty.
                val listedEmpty = !c.isNull(2) && c.getLong(2) == 0L
                if (id == writtenId || !listedEmpty) ours += id to name
            }
        }
        for ((id, _) in oldestBeyond(ours, KEEP)) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(folder, id)) }
        }
    }

    private fun wrap(raw: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        return cipher.iv to cipher.doFinal(raw)
    }

    private fun unwrap(iv: ByteArray, wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(wrapped)
    }

    private fun failureText(e: Throwable?): String = when (e) {
        is SecurityException -> "Lost access to the backup folder. Choose it again."
        is java.io.FileNotFoundException -> "The backup folder was moved or deleted. Choose it again."
        is javax.crypto.AEADBadTagException, is java.security.GeneralSecurityException -> "The backup key is no longer available. Turn automatic backup off and on again."
        else -> "The last automatic backup didn't finish. It will try again."
    }

    companion object {
        private const val TAG = "AutoBackup"
        const val KEEP = 4
        const val MIN_PASSPHRASE_LENGTH = 10
        const val FILE_PREFIX = "IntelligentSearch-auto-"
        const val FILE_SUFFIX = ".json"
        private const val WORK_NAME = "automatic_encrypted_backup"
        private const val STATE_FILE = "auto_backup_state"
        private const val WRAP_KEY_ALIAS = "auto_backup_wrap_key"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FOLDER = "folder_uri"
        private const val KEY_SALT = "salt"
        private const val KEY_WRAPPED = "wrapped_key"
        private const val KEY_WRAP_IV = "wrapped_key_iv"
        private const val KEY_LAST_SUCCESS = "last_success_ms"
        private const val KEY_LAST_FAILURE = "last_failure"

        fun isAutomaticBackupName(name: String): Boolean = name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)

        /** The entries to delete so only the newest [keep] remain; names sort by their timestamp. */
        fun <T> oldestBeyond(entries: List<Pair<T, String>>, keep: Int): List<Pair<T, String>> =
            entries.sortedByDescending { it.second }.drop(keep)

        /** (Re)schedules the weekly job; it runs when the battery and storage aren't low. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** The weekly job. A failure is recorded for the Encrypted Backup page and retried at the next run. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val autoBackup = AutoBackup(applicationContext)
        if (!autoBackup.isEnabled) return Result.success()
        val backupManager = applicationContext.appEntryPoint.backupManager()
        return if (autoBackup.runOnce(backupManager).isSuccess) Result.success() else Result.failure()
    }
}
