package com.pixel.intelligentsearch.core.data

import java.net.URLDecoder
import java.util.Locale

/**
 * Which folders inside the user's chosen folders file search skips: the subfolders they excluded, and always hidden
 * (dot) folders, `.thumbnails` and `Android/data`, which hold app caches rather than the user's files. A folder the
 * user picked itself is always searched, even a hidden one; these rules only apply to what is found inside it.
 *
 * Folders are told apart by their Storage Access Framework document ID within a provider (the authority). Phone
 * storage IDs are paths ("primary:Documents/Old"), so an excluded folder also covers everything under it and
 * matches whatever the letter case, as the storage itself does; other providers' IDs are opaque and match exactly.
 */
object FolderExclusions {

    /** An excluded folder: the provider's [authority] and the folder's [documentId] there. */
    data class Excluded(val authority: String, val documentId: String)

    /**
     * The folder a tree URI ("content://authority/tree/primary%3ADocuments%2FOld") points at, or null when [treeUri]
     * isn't one.
     */
    fun parseTreeUri(treeUri: String): Excluded? {
        val rest = treeUri.trim().removePrefix("content://").takeIf { it != treeUri.trim() } ?: return null
        val parts = rest.split('/')
        if (parts.size < 3 || parts[1] != "tree" || parts[0].isBlank() || parts[2].isBlank()) return null
        // URI escapes, not form encoding: a literal "+" stays a plus.
        val documentId = runCatching { URLDecoder.decode(parts[2].replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return null
        return Excluded(parts[0], normalize(documentId))
    }

    /** Drops trailing slashes, so "primary:Documents/Old/" and "primary:Documents/Old" are the same folder. */
    internal fun normalize(documentId: String): String = documentId.trimEnd('/')

    /** Phone-storage style IDs are "<volume>:<path>"; their paths nest, and storage ignores letter case. */
    private fun isPathLike(documentId: String): Boolean = ':' in documentId

    /** Whether the folder [documentId] in [authority] is one of [excluded], or (for path IDs) inside one. */
    fun isExcluded(authority: String, documentId: String, excluded: Collection<Excluded>): Boolean {
        if (excluded.isEmpty()) return false
        val id = normalize(documentId)
        return excluded.any { ex ->
            if (ex.authority != authority) return@any false
            if (!isPathLike(id) || !isPathLike(ex.documentId)) return@any id == ex.documentId
            val a = id.lowercase(Locale.ROOT)
            val b = ex.documentId.lowercase(Locale.ROOT)
            // "…/Old" covers "…/Old/2019" but not "…/Older". A volume root ("primary:") covers the whole volume.
            a == b || a.startsWith(if (b.endsWith(':')) b else "$b/")
        }
    }

    /**
     * Whether [child] can be excluded from the chosen folder [parent]: it is in the same provider and, for path IDs
     * (phone storage, SD cards), below it. Opaque IDs can't be placed, so any other folder of that provider counts.
     */
    fun isInside(parent: Excluded, child: Excluded): Boolean {
        if (parent.authority != child.authority) return false
        if (!isPathLike(parent.documentId) || !isPathLike(child.documentId)) return child.documentId != parent.documentId
        if (normalize(child.documentId).equals(normalize(parent.documentId), ignoreCase = true)) return false
        return isExcluded(child.authority, child.documentId, listOf(parent))
    }

    /**
     * Whether file search always passes over the subfolder [name] found in the folder at [parentPath] (the path of
     * names from the chosen folder down, "Phone/Android"): hidden folders, thumbnail caches and apps' private data.
     */
    fun isAlwaysSkipped(parentPath: String, name: String): Boolean {
        if (name.startsWith(".")) return true
        val parentName = parentPath.trimEnd('/').substringAfterLast('/')
        return name.equals("data", ignoreCase = true) && parentName.equals("Android", ignoreCase = true)
    }

    /**
     * Whether a file at [path] (a full storage path, as MediaStore gives) lies in a folder [isAlwaysSkipped] passes
     * over, e.g. "/storage/emulated/0/DCIM/.thumbnails/1.jpg".
     */
    fun isInAlwaysSkippedFolder(path: String): Boolean {
        val folders = path.trimEnd('/').split('/').dropLast(1)
        return folders.indices.any { i -> isAlwaysSkipped(folders.subList(0, i).joinToString("/"), folders[i]) }
    }

    /** A short name for an excluded folder: its path within its volume ("Documents/Old"), else the last part of its ID. */
    fun label(excluded: Excluded): String {
        val id = excluded.documentId
        return if (isPathLike(id)) id.substringAfter(':').ifBlank { id } else id.substringAfterLast('/')
    }
}
