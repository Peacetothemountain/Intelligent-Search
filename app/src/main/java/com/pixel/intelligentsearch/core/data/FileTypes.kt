package com.pixel.intelligentsearch.core.data

import java.util.Locale

/**
 * The kinds of file File Search tells apart: each can be left out of search, opened with its own app, and asked for
 * by name in a query ("pdf invoice"). [id] is what settings store, so it never changes; [label] is the Title Case
 * name settings and the results' filter chip show; [queryMimes] are the types an app must open to be offered for
 * the group (the first is the group's representative type).
 */
enum class FileTypeGroup(val id: String, val label: String, val queryMimes: List<String>) {
    PDF("pdf", "PDFs", listOf("application/pdf")),
    IMAGES("images", "Images", listOf("image/jpeg", "image/png")),
    VIDEO("video", "Videos", listOf("video/mp4")),
    AUDIO("audio", "Audio", listOf("audio/mpeg")),
    DOCUMENTS(
        "documents", "Documents",
        listOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.oasis.opendocument.text",
            "text/plain"
        )
    ),
    SPREADSHEETS(
        "spreadsheets", "Spreadsheets",
        listOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.oasis.opendocument.spreadsheet",
            "text/csv"
        )
    ),
    PRESENTATIONS(
        "presentations", "Presentations",
        listOf(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/vnd.oasis.opendocument.presentation"
        )
    ),
    ARCHIVES("archives", "Archives", listOf("application/zip")),
    APKS("apks", "APKs", listOf("application/vnd.android.package-archive")),
    OTHER("other", "Other Files", listOf("application/octet-stream"));

    companion object {
        fun fromId(id: String): FileTypeGroup? = entries.firstOrNull { it.id == id }
    }
}

/** Which [FileTypeGroup] a file belongs to, from its name's extension and its MIME type. */
object FileTypes {

    private val BY_EXTENSION: Map<String, FileTypeGroup> = buildMap {
        fun group(group: FileTypeGroup, vararg extensions: String) = extensions.forEach { put(it, group) }
        group(FileTypeGroup.PDF, "pdf")
        group(FileTypeGroup.IMAGES, "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "avif", "dng", "tif", "tiff")
        group(FileTypeGroup.VIDEO, "mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "wmv")
        group(FileTypeGroup.AUDIO, "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "amr", "mid", "midi", "wma")
        group(FileTypeGroup.DOCUMENTS, "doc", "docx", "odt", "txt", "md", "markdown", "rtf")
        group(FileTypeGroup.SPREADSHEETS, "xls", "xlsx", "ods", "csv", "tsv")
        group(FileTypeGroup.PRESENTATIONS, "ppt", "pptx", "odp", "key")
        group(FileTypeGroup.ARCHIVES, "zip", "7z", "rar", "gz", "tgz", "tar", "bz2", "xz")
        group(FileTypeGroup.APKS, "apk", "apks", "xapk", "apkm")
    }

    private val BY_MIME: Map<String, FileTypeGroup> = buildMap {
        fun group(group: FileTypeGroup, vararg mimes: String) = mimes.forEach { put(it, group) }
        group(FileTypeGroup.PDF, "application/pdf")
        group(
            FileTypeGroup.DOCUMENTS,
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text", "application/rtf", "text/rtf", "text/plain", "text/markdown",
            "application/vnd.google-apps.document"
        )
        group(
            FileTypeGroup.SPREADSHEETS,
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.spreadsheet", "text/csv", "text/comma-separated-values",
            "text/tab-separated-values", "application/vnd.google-apps.spreadsheet"
        )
        group(
            FileTypeGroup.PRESENTATIONS,
            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.presentation", "application/vnd.google-apps.presentation"
        )
        group(
            FileTypeGroup.ARCHIVES,
            "application/zip", "application/x-zip-compressed", "application/x-7z-compressed", "application/x-rar-compressed",
            "application/vnd.rar", "application/gzip", "application/x-gzip", "application/x-tar", "application/x-bzip2",
            "application/x-xz"
        )
        group(FileTypeGroup.APKS, "application/vnd.android.package-archive")
    }

    /** The type each known extension is opened as, for files whose provider only says "some bytes". */
    private val MIME_FOR_EXTENSION: Map<String, String> = mapOf(
        "pdf" to "application/pdf", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "gif" to "image/gif", "webp" to "image/webp", "heic" to "image/heic", "mp4" to "video/mp4",
        "mkv" to "video/x-matroska", "webm" to "video/webm", "mp3" to "audio/mpeg", "m4a" to "audio/mp4",
        "flac" to "audio/flac", "wav" to "audio/x-wav", "ogg" to "audio/ogg", "txt" to "text/plain",
        "md" to "text/markdown", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "odt" to "application/vnd.oasis.opendocument.text", "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet", "csv" to "text/csv",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odp" to "application/vnd.oasis.opendocument.presentation", "zip" to "application/zip",
        "apk" to "application/vnd.android.package-archive"
    )

    /** Types that say nothing about what a file is. */
    private fun isGenericMime(mime: String): Boolean =
        mime.isBlank() || mime == "*/*" || mime == "application/octet-stream" || mime == "application/x-unknown"

    /** The lower-case extension of [name] ("Report.PDF" is "pdf"), or "" when it has none. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        // A leading dot names a hidden file (".nomedia"), not an extension.
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    /**
     * The group of a file called [name] of type [mimeType]. A known extension decides, since it is what people go
     * by ("data.csv" is a spreadsheet even where a provider calls it text/plain); otherwise the MIME type does, by
     * its exact value or its image/video/audio family; anything else is [FileTypeGroup.OTHER].
     */
    fun groupOf(name: String, mimeType: String): FileTypeGroup {
        BY_EXTENSION[extensionOf(name)]?.let { return it }
        val mime = mimeType.lowercase(Locale.ROOT).substringBefore(';').trim()
        if (isGenericMime(mime)) return FileTypeGroup.OTHER
        BY_MIME[mime]?.let { return it }
        return when {
            mime.startsWith("image/") -> FileTypeGroup.IMAGES
            mime.startsWith("video/") -> FileTypeGroup.VIDEO
            mime.startsWith("audio/") -> FileTypeGroup.AUDIO
            else -> FileTypeGroup.OTHER
        }
    }

    fun groupOf(file: FileItem): FileTypeGroup = groupOf(file.name, file.mimeType)

    /**
     * The type to open a file with: its own, unless that says nothing ("application/octet-stream") and the
     * extension is known, so the app chosen for, say, PDFs is offered the file as a PDF.
     */
    fun mimeForOpening(name: String, mimeType: String): String {
        if (!isGenericMime(mimeType.lowercase(Locale.ROOT).trim())) return mimeType
        return MIME_FOR_EXTENSION[extensionOf(name)] ?: mimeType.ifBlank { "*/*" }
    }
}

/**
 * A file type named at the start of a query: "pdf invoice" asks for PDFs matching "invoice". [word] is the word as
 * typed (lower case), [rest] the query after it, which may be blank ("photos" alone lists the latest images).
 */
data class FileTypeQuery(val group: FileTypeGroup, val word: String, val rest: String) {
    companion object {
        private val WORDS: Map<String, FileTypeGroup> = buildMap {
            fun words(group: FileTypeGroup, vararg words: String) = words.forEach { put(it, group) }
            words(FileTypeGroup.PDF, "pdf", "pdfs")
            words(FileTypeGroup.IMAGES, "photo", "photos", "image", "images", "picture", "pictures")
            words(FileTypeGroup.VIDEO, "video", "videos")
            words(FileTypeGroup.AUDIO, "song", "songs", "audio", "music")
            words(FileTypeGroup.DOCUMENTS, "doc", "docs", "document", "documents")
            words(FileTypeGroup.SPREADSHEETS, "spreadsheet", "spreadsheets", "sheet", "sheets")
            words(FileTypeGroup.PRESENTATIONS, "slides", "presentation", "presentations")
        }

        /**
         * The file type [query] starts with, or null. Only a whole first word counts ("photography" and "pdf2"
         * don't), so bangs ("!pdf"), sums and the call/message/remind phrasings never match.
         */
        fun parse(query: String): FileTypeQuery? {
            val trimmed = query.trimStart()
            val end = trimmed.indexOfFirst { it.isWhitespace() }.let { if (it < 0) trimmed.length else it }
            val word = trimmed.substring(0, end).lowercase(Locale.ROOT)
            val group = WORDS[word] ?: return null
            return FileTypeQuery(group, word, trimmed.substring(end).trim())
        }
    }
}
