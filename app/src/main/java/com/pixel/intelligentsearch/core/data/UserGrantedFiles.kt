package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Files the user explicitly shared with Intelligent Search: folders picked with the system folder
 * picker and items chosen in the Android photo picker.
 *
 * Both routes hand out per-item access through the Storage Access Framework, so file search needs
 * no storage or media permission. Google Play's Photo and Video Permissions policy reserves
 * READ_MEDIA_IMAGES / READ_MEDIA_VIDEO for apps whose core purpose is broad media access and points
 * everyone else to the system pickers, and MANAGE_EXTERNAL_STORAGE needs a separate Play declaration.
 */
object UserGrantedFiles {
    private const val TAG = "UserGrantedFiles"

    const val KEY_FOLDER_URIS = "search.files.tree_uris"
    const val KEY_MEDIA_URIS = "search.files.media_uris"
    private const val LEGACY_KEY_FOLDER_URI = "search.files.uri"
    private const val LEGACY_KEY_PHOTO_COUNT = "search.files.photo_count"

    /** Keys whose change means the file index has to be rebuilt. */
    val INDEX_KEYS = setOf(
        KEY_FOLDER_URIS, KEY_MEDIA_URIS, LEGACY_KEY_FOLDER_URI,
        "search.files", "search_files",
        "search.files.hidden.files", "search_files_hidden_files"
    )

    /** Shown as the location of photo-picker items, which have no folder path. */
    const val PICKED_MEDIA_LOCATION = "Chosen photos & videos"

    // Android keeps at most 512 persisted grants per app and silently drops the oldest beyond that,
    // which could cost a folder grant, so picked media stays well below the ceiling.
    const val MAX_PICKED_MEDIA = 300
    private const val MAX_FOLDER_FILES = 3000
    private const val MAX_FOLDER_DEPTH = 8

    private val CHILD_PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    fun isFileSearchEnabled(prefs: SharedPreferences): Boolean =
        if (prefs.contains("search.files")) prefs.getBoolean("search.files", false)
        else prefs.getBoolean("search_files", false)

    fun includeHiddenFiles(prefs: SharedPreferences): Boolean =
        if (prefs.contains("search.files.hidden.files")) prefs.getBoolean("search.files.hidden.files", false)
        else prefs.getBoolean("search_files_hidden_files", false)

    fun folderUris(prefs: SharedPreferences): Set<String> {
        prefs.getStringSet(KEY_FOLDER_URIS, null)?.let { return it.toSet() }
        // Before multi-folder support a single folder was stored under the legacy key.
        val legacy = prefs.getString(LEGACY_KEY_FOLDER_URI, null)
        return if (legacy.isNullOrBlank()) emptySet() else setOf(legacy)
    }

    fun mediaUris(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_MEDIA_URIS, null)?.toSet().orEmpty()

    /** Persists read access to a picked folder. Returns false if the grant could not be kept. */
    fun addFolder(context: Context, prefs: SharedPreferences, treeUri: Uri): Boolean {
        if (!takeReadGrant(context, treeUri)) return false
        val updated = folderUris(prefs) + treeUri.toString()
        prefs.edit {
            putStringSet(KEY_FOLDER_URIS, updated)
            remove(LEGACY_KEY_FOLDER_URI)
        }
        return true
    }

    fun removeFolder(context: Context, prefs: SharedPreferences, treeUri: String) {
        releaseReadGrant(context, treeUri)
        val updated = folderUris(prefs) - treeUri
        prefs.edit {
            putStringSet(KEY_FOLDER_URIS, updated)
            remove(LEGACY_KEY_FOLDER_URI)
        }
    }

    /** Persists read access to picked media, up to [MAX_PICKED_MEDIA] in total. Returns how many were added. */
    fun addMedia(context: Context, prefs: SharedPreferences, uris: List<Uri>): Int {
        val current = mediaUris(prefs)
        val room = (MAX_PICKED_MEDIA - current.size).coerceAtLeast(0)
        val added = uris.asSequence()
            .filter { it.toString() !in current }
            .take(room)
            .filter { takeReadGrant(context, it) }
            .map { it.toString() }
            .toList()
        prefs.edit {
            putStringSet(KEY_MEDIA_URIS, current + added)
            remove(LEGACY_KEY_PHOTO_COUNT)
        }
        return added.size
    }

    fun clearMedia(context: Context, prefs: SharedPreferences) {
        mediaUris(prefs).forEach { releaseReadGrant(context, it) }
        prefs.edit {
            remove(KEY_MEDIA_URIS)
            remove(LEGACY_KEY_PHOTO_COUNT)
        }
    }

    /** Human-readable folder name, e.g. "Documents". Does a provider query, so call off the main thread. */
    fun folderDisplayName(context: Context, treeUri: String): String {
        val uri = Uri.parse(treeUri)
        return try {
            val rootDoc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            context.contentResolver.query(rootDoc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Folder"
    }

    /** Whether the persisted grant for [uri] still exists (users can revoke it, or the folder can vanish). */
    fun hasReadGrant(context: Context, uri: String): Boolean =
        grantedUris(context).contains(uri)

    /** Every file in the user's chosen folders and photo-picker selections that is still accessible. */
    suspend fun loadAll(context: Context, prefs: SharedPreferences): List<FileItem> = withContext(Dispatchers.IO) {
        val granted = grantedUris(context)
        val includeHidden = includeHiddenFiles(prefs)
        val out = ArrayList<FileItem>()
        out += loadPickedMedia(context, mediaUris(prefs).filter { it in granted })
        for (tree in folderUris(prefs)) {
            if (tree !in granted) continue
            if (out.size >= MAX_FOLDER_FILES) break
            loadFolder(context, tree, includeHidden, MAX_FOLDER_FILES - out.size, out)
        }
        out
    }

    private suspend fun loadFolder(
        context: Context,
        treeUriString: String,
        includeHidden: Boolean,
        limit: Int,
        out: MutableList<FileItem>
    ) {
        val start = out.size
        try {
            val treeUri = Uri.parse(treeUriString)
            val rootName = folderDisplayName(context, treeUriString)
            // Breadth first, so a deep tree cannot use up the budget before shallower files are seen.
            val queue = ArrayDeque<Triple<String, String, Int>>()
            queue.add(Triple(DocumentsContract.getTreeDocumentId(treeUri), rootName, 0))
            while (queue.isNotEmpty() && out.size - start < limit) {
                currentCoroutineContext().ensureActive()
                val (docId, relativePath, depth) = queue.removeFirst()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                context.contentResolver.query(children, CHILD_PROJECTION, null, null, null)?.use { c ->
                    while (c.moveToNext() && out.size - start < limit) {
                        val childId = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        if (!includeHidden && name.startsWith(".")) continue
                        val mime = c.getString(2) ?: "application/octet-stream"
                        val childPath = "$relativePath/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MAX_FOLDER_DEPTH) queue.add(Triple(childId, childPath, depth + 1))
                        } else {
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            val modified = if (c.isNull(3)) 0L else c.getLong(3)
                            out.add(FileItem(name, childPath, mime, documentUri.toString(), modified))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A deleted folder or a provider error must not stop the remaining folders from indexing.
            Log.w(TAG, "Could not index folder", e)
        }
    }

    private fun loadPickedMedia(context: Context, uris: List<String>): List<FileItem> {
        val resolver = context.contentResolver
        return uris.mapNotNull { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?: return@mapNotNull null
                val mime = resolver.getType(uri) ?: "image/*"
                FileItem(name, PICKED_MEDIA_LOCATION, mime, uriString)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun grantedUris(context: Context): Set<String> =
        try {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .mapTo(HashSet()) { it.uri.toString() }
        } catch (e: Exception) {
            emptySet()
        }

    private fun takeReadGrant(context: Context, uri: Uri): Boolean =
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Persistable read access not offered for picked item", e)
            false
        }

    private fun releaseReadGrant(context: Context, uri: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Already gone (revoked or never persisted).
        }
    }
}
