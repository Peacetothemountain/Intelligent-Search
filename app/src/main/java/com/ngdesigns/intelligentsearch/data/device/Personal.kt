package com.ngdesigns.intelligentsearch.data.device

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import com.ngdesigns.intelligentsearch.engine.Usage
import java.util.concurrent.TimeUnit

/** How each app has been used lately, by package: days used in the last month and the last time it was open. */
interface UsageSource {
    fun hasAccess(): Boolean
    fun usage(): Map<String, Usage>
}

class SystemUsageSource(private val context: Context) : UsageSource {
    override fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    override fun usage(): Map<String, Usage> {
        if (!hasAccess()) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val stats = runCatching { usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - TimeUnit.DAYS.toMillis(30), end) }.getOrNull().orEmpty()
        return stats.filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { (_, days) -> Usage(count = days.size, lastUsedMillis = days.maxOf { it.lastTimeUsed }) }
    }
}

interface ContactSource {
    fun hasAccess(): Boolean
    fun contacts(): List<ContactEntry>
}

class SystemContactSource(private val context: Context) : ContactSource {
    override fun hasAccess() = granted(context, Manifest.permission.READ_CONTACTS)

    override fun contacts(): List<ContactEntry> {
        if (!hasAccess()) return emptyList()
        val phones = HashMap<Long, MutableList<String>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1) ?: continue
                phones.getOrPut(c.getLong(0)) { mutableListOf() }.let { if (number !in it) it += number }
            }
        }
        val out = ArrayList<ContactEntry>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.STARRED,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                val lookup = ContactsContract.Contacts.getLookupUri(id, c.getString(1)).toString()
                val numbers = phones[id].orEmpty()
                out += ContactEntry(name, numbers, c.getString(3), c.getInt(4) == 1, Target.Contact(lookup, numbers.firstOrNull()))
            }
        }
        return out
    }
}

interface CalendarSource {
    fun hasAccess(): Boolean
    fun upcoming(days: Int = 14): List<EventEntry>
}

class SystemCalendarSource(private val context: Context) : CalendarSource {
    override fun hasAccess() = granted(context, Manifest.permission.READ_CALENDAR)

    override fun upcoming(days: Int): List<EventEntry> {
        if (!hasAccess()) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - TimeUnit.HOURS.toMillis(1))
            ContentUris.appendId(it, now + TimeUnit.DAYS.toMillis(days.toLong()))
        }.build()
        val out = ArrayList<EventEntry>()
        context.contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY,
            ),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 200) {
                val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                out += EventEntry(title, c.getString(4)?.takeIf { it.isNotBlank() }, c.getInt(5) == 1, Target.Event(c.getLong(0), c.getLong(2), c.getLong(3)))
            }
        }
        return out
    }
}

/**
 * Files the user explicitly shared: folders chosen in the system folder picker (walked to a bounded depth and
 * count) and photos or videos chosen in the photo picker. No storage permission is involved.
 */
interface FileSource {
    fun files(folders: List<String>, media: List<String>, includeHidden: Boolean): List<FileEntry>
}

class SharedFileSource(private val context: Context) : FileSource {
    override fun files(folders: List<String>, media: List<String>, includeHidden: Boolean): List<FileEntry> {
        val out = ArrayList<FileEntry>()
        for (tree in folders) {
            val treeUri = Uri.parse(tree)
            val root = runCatching { DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)) }.getOrNull() ?: continue
            walk(treeUri, root, depth = 0, includeHidden, out)
            if (out.size >= MAX_FILES) break
        }
        for (item in media) {
            val uri = Uri.parse(item)
            runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) out += FileEntry(c.getString(0) ?: "Photo", c.getLong(1), 0L, Target.File(item, context.contentResolver.getType(uri)))
                }
            }
        }
        return out
    }

    private fun walk(tree: Uri, children: Uri, depth: Int, includeHidden: Boolean, out: MutableList<FileEntry>) {
        if (depth > 6 || out.size >= MAX_FILES) return
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val folders = ArrayList<String>()
        runCatching {
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < MAX_FILES) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (!includeHidden && name.startsWith(".")) continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) folders += id
                    else out += FileEntry(name, c.getLong(3), c.getLong(4), Target.File(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), mime))
                }
            }
        }
        for (id in folders) walk(tree, DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), depth + 1, includeHidden, out)
    }

    companion object { const val MAX_FILES = 5000 }
}

internal fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
