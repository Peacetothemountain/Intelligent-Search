package com.pixel.intelligentsearch.core.data

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns

class GlobalSearchProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.pixel.intelligentsearch.globalsearch"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/search")
        private const val SEARCH_PATH = "search"
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA
        ))

        val queryTerm = extractQueryTerm(uri, selectionArgs)
        if (queryTerm.isNotBlank()) {
            val encodedQuery = Uri.encode(queryTerm)
            val row = arrayOf<Any?>(
                1L,
                "Search '$queryTerm'",
                "Intelligent Search",
                "android.intent.action.VIEW",
                "intelligentsearch://search?q=$encodedQuery"
            )
            cursor.addRow(row)
        }

        return cursor
    }

    private fun extractQueryTerm(uri: Uri, selectionArgs: Array<out String>?): String {
        // Check selectionArgs first (standard system QuickSearchBox convention)
        selectionArgs?.firstOrNull()?.let { if (it.isNotBlank()) return it.trim() }

        // Check query parameter
        uri.getQueryParameter("q")?.let { if (it.isNotBlank()) return it.trim() }
        uri.getQueryParameter(SearchManager.SUGGEST_COLUMN_QUERY)?.let { if (it.isNotBlank()) return it.trim() }

        // Fallback to path segment only if not matching root SEARCH_PATH
        val lastSegment = uri.lastPathSegment ?: ""
        return if (lastSegment.equals(SEARCH_PATH, ignoreCase = true)) "" else lastSegment.trim()
    }

    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.dir/vnd.com.pixel.intelligentsearch.search"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
