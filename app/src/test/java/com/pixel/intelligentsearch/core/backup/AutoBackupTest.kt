package com.pixel.intelligentsearch.core.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator

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

    @Test
    fun aBackupThatCantBeWrittenLeavesNoFileBehind() = runBlocking {
        val setup = AutoBackupSetup()
        setup.folder.onOpen = { throw IOException("No space left on device") }

        val result = setup.autoBackup.runOnce(setup.backupManager)

        assertTrue(result.isFailure)
        assertEquals(emptyList<String>(), setup.folder.automaticBackupNames())
        assertNotNull(setup.autoBackup.lastFailure)
        setup.close()
    }

    @Test
    fun aCancelledBackupLeavesNoFileAndIsNotAFailure() = runBlocking {
        val setup = AutoBackupSetup()
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        setup.folder.onOpen = {
            writing.countDown()
            release.await(10, TimeUnit.SECONDS)
        }

        var thrown: Throwable? = null
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                setup.autoBackup.runOnce(setup.backupManager)
            } catch (e: Throwable) {
                thrown = e
                throw e
            }
        }
        assertTrue("the backup started writing", writing.await(10, TimeUnit.SECONDS))
        job.cancel()
        release.countDown()
        job.join()

        assertTrue("cancellation reached the caller: $thrown", thrown is CancellationException)
        assertEquals(emptyList<String>(), setup.folder.automaticBackupNames())
        assertNull(setup.autoBackup.lastFailure)
        setup.close()
    }

    @Test
    fun theFolderIsNeverTouchedOnTheMainThread() = runBlocking {
        val setup = AutoBackupSetup()
        assertTrue(Looper.myLooper() == Looper.getMainLooper())

        setup.autoBackup.runOnce(setup.backupManager).getOrThrow()

        assertEquals(1, setup.folder.automaticBackupNames().size)
        assertEquals(emptyList<String>(), setup.folder.calledOnMainThread.toList())
        setup.close()
    }

    @Test
    fun anEmptyLeftoverFileDoesNotTakeThePlaceOfARealBackup() = runBlocking {
        val setup = AutoBackupSetup()
        for (day in 1..4) setup.folder.add("IntelligentSearch-auto-2020-01-0$day-0900.json", "{\"backup\":$day}")
        setup.folder.add("IntelligentSearch-auto-2099-01-01-0900.json", "") // left by a run that never wrote
        setup.folder.add("intelligent_search_backup_1.json", "{}") // saved by hand

        setup.autoBackup.runOnce(setup.backupManager).getOrThrow()

        val autoBackups = setup.folder.automaticBackupNames()
        assertEquals(4, autoBackups.size)
        assertTrue("the newest real backups are kept: $autoBackups", autoBackups.containsAll(
            listOf("IntelligentSearch-auto-2020-01-02-0900.json", "IntelligentSearch-auto-2020-01-03-0900.json", "IntelligentSearch-auto-2020-01-04-0900.json")
        ))
        assertFalse(autoBackups.contains("IntelligentSearch-auto-2099-01-01-0900.json"))
        assertTrue(setup.folder.names().contains("intelligent_search_backup_1.json"))
        setup.close()
    }

    /** Automatic backup switched on for a folder in [FakeBackupFolderProvider], with a software wrap key. */
    private class AutoBackupSetup {
        val context: Context = ApplicationProvider.getApplicationContext()
        val folder: FakeBackupFolderProvider = Robolectric.setupContentProvider(FakeBackupFolderProvider::class.java, FakeBackupFolderProvider.AUTHORITY)
        private val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val backupManager = BackupManager(context, SettingsManager(context), db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        private val wrapKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val autoBackup = AutoBackup(context) { wrapKey }

        init {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            val tree = DocumentsContract.buildTreeDocumentUri(FakeBackupFolderProvider.AUTHORITY, FakeBackupFolderProvider.ROOT_ID)
            assertTrue(autoBackup.enable(tree, "correct horse battery".toCharArray()))
            folder.calledOnMainThread.clear()
        }

        fun close() = db.close()
    }
}

/**
 * A folder the user picked, as the Storage Access Framework presents it: its documents can be listed, created,
 * written and deleted. Records which of those calls arrived on the main thread.
 */
class FakeBackupFolderProvider : ContentProvider() {

    private class Doc(val name: String, val file: File)

    private val docs = ConcurrentHashMap<String, Doc>()
    private val nextId = AtomicInteger()
    private lateinit var dir: File

    /** Runs whenever a document is opened, before it is handed out. */
    @Volatile
    var onOpen: () -> Unit = {}

    val calledOnMainThread: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun onCreate(): Boolean {
        dir = File(context!!.cacheDir, "picked-folder").apply { mkdirs() }
        return true
    }

    fun add(name: String, content: String): String {
        val id = "doc" + nextId.incrementAndGet()
        val file = File(dir, id).apply { writeText(content) }
        docs[id] = Doc(name, file)
        return id
    }

    fun names(): List<String> = docs.values.map { it.name }.sorted()

    fun automaticBackupNames(): List<String> = names().filter(AutoBackup::isAutomaticBackupName)

    private fun note(call: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) calledOnMainThread += call
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val bundle = extras ?: return super.call(method, arg, null)
        val target = bundle.getParcelable("uri", Uri::class.java) ?: return super.call(method, arg, bundle)
        return when (method) {
            "android:createDocument" -> {
                note("createDocument")
                val id = add(bundle.getString(Document.COLUMN_DISPLAY_NAME) ?: "untitled", "")
                Bundle().apply { putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(target, id)) }
            }
            "android:deleteDocument" -> {
                note("deleteDocument")
                docs.remove(DocumentsContract.getDocumentId(target))?.file?.delete()
                Bundle()
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE)
        val cursor = MatrixCursor(columns)
        if (uri.lastPathSegment == "children") {
            note("queryChildDocuments")
            for ((id, doc) in docs) {
                cursor.addRow(columns.map { column ->
                    when (column) {
                        Document.COLUMN_DOCUMENT_ID -> id
                        Document.COLUMN_DISPLAY_NAME -> doc.name
                        Document.COLUMN_SIZE -> doc.file.length()
                        Document.COLUMN_MIME_TYPE -> "application/json"
                        else -> null
                    }
                })
            }
        } else {
            note("queryDocument")
            cursor.addRow(columns.map { column ->
                when (column) {
                    Document.COLUMN_DOCUMENT_ID -> ROOT_ID
                    Document.COLUMN_DISPLAY_NAME -> "Backups"
                    Document.COLUMN_MIME_TYPE -> Document.MIME_TYPE_DIR
                    else -> null
                }
            })
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        note("openDocument")
        val doc = docs[DocumentsContract.getDocumentId(uri)] ?: throw java.io.FileNotFoundException(uri.toString())
        onOpen()
        return ParcelFileDescriptor.open(doc.file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.pixel.intelligentsearch.test.backupfolder"
        const val ROOT_ID = "root"
    }
}
