package com.pixel.intelligentsearch.core.data

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
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf(
            BaseColumns._ID,
            "suggest_text_1",
            "suggest_text_2",
            "suggest_intent_action",
            "suggest_intent_data"
        ))

        val queryTerm = uri.lastPathSegment ?: ""
        if (queryTerm.isNotBlank()) {
            val row = arrayOf<Any?>(
                1L,
                "Search '$queryTerm'",
                "Intelligent Search",
                "android.intent.action.VIEW",
                "intelligentsearch://search?q=$queryTerm"
            )
            cursor.addRow(row)
        }

        return cursor
    }

    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.dir/vnd.com.pixel.intelligentsearch.search"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
