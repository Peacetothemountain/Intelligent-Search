package com.pixel.intelligentsearch.core.data

import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
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

    /**
     * Keys that only mean something on this phone: Android grants access to picked folders and photos per
     * device, so another phone can't open them. Backups leave them out and a restore never replaces them.
     */
    val DEVICE_LOCAL_KEYS = setOf(
        KEY_FOLDER_URIS, KEY_MEDIA_URIS, LEGACY_KEY_FOLDER_URI, LEGACY_KEY_PHOTO_COUNT,
        SettingsManager.FILES_EXCLUDED_FOLDERS.name
    )

    /** Keys whose change means the file index has to be rebuilt. */
    val INDEX_KEYS = setOf(
        KEY_FOLDER_URIS, KEY_MEDIA_URIS, LEGACY_KEY_FOLDER_URI
    ) + SettingsManager.SEARCH_FILES.allNames + SettingsManager.FILES_HIDDEN_FILES.allNames +
        SettingsManager.FILES_EXCLUDED_TYPES.allNames + SettingsManager.FILES_EXCLUDED_FOLDERS.allNames

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

    fun isFileSearchEnabled(prefs: SharedPreferences): Boolean = prefs[SettingsManager.SEARCH_FILES]

    fun includeHiddenFiles(prefs: SharedPreferences): Boolean = prefs[SettingsManager.FILES_HIDDEN_FILES]

    fun folderUris(prefs: SharedPreferences): Set<String> {
        prefs.getStringSet(KEY_FOLDER_URIS, null)?.let { return it.toSet() }
        // Before multi-folder support a single folder was stored under the legacy key.
        val legacy = prefs.getString(LEGACY_KEY_FOLDER_URI, null)
        return if (legacy.isNullOrBlank()) emptySet() else setOf(legacy)
    }

    fun mediaUris(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_MEDIA_URIS, null)?.toSet().orEmpty()

    /** The file types file search leaves out (File Types in File Search settings). */
    fun excludedTypes(prefs: SharedPreferences): Set<FileTypeGroup> =
        prefs[SettingsManager.FILES_EXCLUDED_TYPES].mapNotNullTo(HashSet(), FileTypeGroup::fromId)

    /** The subfolders of the chosen folders that file search skips, as tree URIs. */
    fun excludedFolderUris(prefs: SharedPreferences): Set<String> = prefs[SettingsManager.FILES_EXCLUDED_FOLDERS]

    /** What adding an excluded subfolder came to. */
    enum class ExcludeResult { ADDED, ALREADY_EXCLUDED, NOT_INSIDE_A_CHOSEN_FOLDER }

    /**
     * Skips the folder [treeUri] (picked with the system folder picker) in file search, if it lies inside one of
     * the chosen folders. No access to it is kept: indexing reaches it through the chosen folder's own grant.
     */
    fun addExcludedFolder(prefs: SharedPreferences, treeUri: String): ExcludeResult {
        val folder = FolderExclusions.parseTreeUri(treeUri) ?: return ExcludeResult.NOT_INSIDE_A_CHOSEN_FOLDER
        val chosen = folderUris(prefs).mapNotNull(FolderExclusions::parseTreeUri)
        if (chosen.none { FolderExclusions.isInside(it, folder) }) return ExcludeResult.NOT_INSIDE_A_CHOSEN_FOLDER
        val current = excludedFolderUris(prefs)
        if (current.mapNotNull(FolderExclusions::parseTreeUri).any { it == folder }) return ExcludeResult.ALREADY_EXCLUDED
        prefs.edit { put(SettingsManager.FILES_EXCLUDED_FOLDERS, current + treeUri) }
        return ExcludeResult.ADDED
    }

    fun removeExcludedFolder(prefs: SharedPreferences, treeUri: String) {
        prefs.edit { put(SettingsManager.FILES_EXCLUDED_FOLDERS, excludedFolderUris(prefs) - treeUri) }
    }

    /** The excluded subfolders that lie in no chosen folder any more, once [chosen] are what is left. */
    private fun orphanedExclusions(prefs: SharedPreferences, chosen: Set<String>): Set<String> {
        val folders = chosen.mapNotNull(FolderExclusions::parseTreeUri)
        return excludedFolderUris(prefs).filterTo(HashSet()) { uri ->
            val excluded = FolderExclusions.parseTreeUri(uri)
            excluded == null || folders.none { FolderExclusions.isInside(it, excluded) }
        }
    }

    /** Persists read access to a picked folder. Returns false if the grant could not be kept. */
    fun addFolder(context: Context, prefs: SharedPreferences, treeUri: Uri): Boolean {
        if (!takeReadGrant(context, treeUri)) return false
        val updated = folderUris(prefs) + treeUri.toString()
        prefs.edit {
            putStringSet(KEY_FOLDER_URIS, updated)
            remove(LEGACY_KEY_FOLDER_URI)
            // Choosing a folder means wanting it searched.
            put(SettingsManager.SEARCH_FILES, true)
        }
        return true
    }

    fun removeFolder(context: Context, prefs: SharedPreferences, treeUri: String) {
        // The same folder may be the automatic backup folder, which shares this grant; it keeps its access then.
        if (com.pixel.intelligentsearch.core.backup.AutoBackup(context).folderUri?.toString() != treeUri) {
            releaseReadGrant(context, treeUri)
        }
        val updated = folderUris(prefs) - treeUri
        val orphaned = orphanedExclusions(prefs, updated)
        prefs.edit {
            putStringSet(KEY_FOLDER_URIS, updated)
            remove(LEGACY_KEY_FOLDER_URI)
            // Subfolders excluded from this folder alone go with it.
            if (orphaned.isNotEmpty()) put(SettingsManager.FILES_EXCLUDED_FOLDERS, excludedFolderUris(prefs) - orphaned)
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
        val uri = treeUri.toUri()
        return try {
            folderRootDocumentUri(treeUri)?.let { rootDoc ->
                context.contentResolver.query(rootDoc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            }
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Folder"
    }

    /** The document URI of the chosen folder [treeUri] itself, e.g. to open the folder picker there; null if invalid. */
    fun folderRootDocumentUri(treeUri: String): Uri? = try {
        val uri = treeUri.toUri()
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * A name for the excluded subfolder [treeUri]: its path on phone storage ("Documents/Old"), else the name its
     * provider gives through a chosen folder's access. May do a provider query, so call off the main thread.
     */
    fun excludedFolderName(context: Context, prefs: SharedPreferences, treeUri: String): String {
        val excluded = FolderExclusions.parseTreeUri(treeUri) ?: return "Folder"
        if (':' !in excluded.documentId) {
            for (tree in folderUris(prefs)) {
                val parent = tree.toUri()
                if (parent.authority != excluded.authority) continue
                val name = try {
                    val doc = DocumentsContract.buildDocumentUriUsingTree(parent, excluded.documentId)
                    context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                } catch (e: Exception) {
                    null
                }
                if (!name.isNullOrBlank()) return name
            }
        }
        return FolderExclusions.label(excluded)
    }

    /** Whether the persisted grant for [uri] still exists (users can revoke it, or the folder can vanish). */
    fun hasReadGrant(context: Context, uri: String): Boolean =
        grantedUris(context).contains(uri)

    /** Every file in the user's chosen folders and photo-picker selections that is still accessible. */
    suspend fun loadAll(context: Context, prefs: SharedPreferences): List<FileItem> = withContext(Dispatchers.IO) {
        val granted = grantedUris(context)
        val rules = FolderRules(
            includeHidden = includeHiddenFiles(prefs),
            excludedTypes = excludedTypes(prefs),
            excludedFolders = excludedFolderUris(prefs).mapNotNull(FolderExclusions::parseTreeUri)
        )
        val out = ArrayList<FileItem>()
        out += loadPickedMedia(context, mediaUris(prefs).filter { it in granted }).filter(rules::keepsType)
        for (tree in folderUris(prefs)) {
            if (tree !in granted) continue
            if (out.size >= MAX_FOLDER_FILES) break
            loadFolder(context, tree, rules, MAX_FOLDER_FILES - out.size, out)
        }
        out
    }

    /**
     * Whether a file found outside the chosen folders (this app's own MediaStore files) belongs in the index: not of
     * a type left out, and not in a folder file search always skips.
     */
    fun keepsOwnMedia(prefs: SharedPreferences): (FileItem) -> Boolean {
        val excluded = excludedTypes(prefs)
        return { file -> FileTypes.groupOf(file) !in excluded && !FolderExclusions.isInAlwaysSkippedFolder(file.path) }
    }

    /** What indexing a chosen folder leaves out. */
    private class FolderRules(
        val includeHidden: Boolean,
        val excludedTypes: Set<FileTypeGroup>,
        val excludedFolders: List<FolderExclusions.Excluded>
    ) {
        fun keepsType(file: FileItem): Boolean = FileTypes.groupOf(file) !in excludedTypes
    }

    private suspend fun loadFolder(
        context: Context,
        treeUriString: String,
        rules: FolderRules,
        limit: Int,
        out: MutableList<FileItem>
    ) {
        val start = out.size
        try {
            val treeUri = treeUriString.toUri()
            val authority = treeUri.authority.orEmpty()
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
                        val mime = c.getString(2) ?: "application/octet-stream"
                        val childPath = "$relativePath/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            // Hidden folders, caches and excluded subfolders are skipped with everything in them.
                            if (FolderExclusions.isAlwaysSkipped(relativePath, name)) continue
                            if (FolderExclusions.isExcluded(authority, childId, rules.excludedFolders)) continue
                            if (depth < MAX_FOLDER_DEPTH) queue.add(Triple(childId, childPath, depth + 1))
                        } else {
                            if (!rules.includeHidden && name.startsWith(".")) continue
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            val modified = if (c.isNull(3)) 0L else c.getLong(3)
                            val file = FileItem(name, childPath, mime, documentUri.toString(), modified)
                            if (rules.keepsType(file)) out.add(file)
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
                val uri = uriString.toUri()
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
            context.contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Already gone (revoked or never persisted).
        }
    }
}
