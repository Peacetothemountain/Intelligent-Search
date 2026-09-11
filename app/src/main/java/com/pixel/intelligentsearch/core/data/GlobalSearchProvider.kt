package com.pixel.intelligentsearch.core.data

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine
import com.pixel.intelligentsearch.core.search.UnifiedSearchCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GlobalSearchProviderEntryPoint {
    fun unifiedSearchCoordinator(): UnifiedSearchCoordinator
}

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
        val columns = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_QUERY,
            SearchManager.SUGGEST_COLUMN_SHORTCUT_ID
        )
        val cursor = MatrixCursor(columns)

        val queryTerm = extractQueryTerm(uri, selectionArgs)
        if (queryTerm.isBlank()) return cursor

        val coordinator = try {
            val ctx = context?.applicationContext
            if (ctx != null) {
                EntryPointAccessors.fromApplication(
                    ctx,
                    GlobalSearchProviderEntryPoint::class.java
                ).unifiedSearchCoordinator()
            } else null
        } catch (_: Exception) { null }

        var rowId = 1L

        if (coordinator != null) {
            val results = coordinator.executeSearchSync(queryTerm)

            // 1. Math calculation result if available
            results.mathResult?.let { math ->
                val display = when (math) {
                    is MathematicalExpressionEngine.MathEvaluationResult.Computation -> math.formattedResult
                    is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion -> math.formatted
                    is MathematicalExpressionEngine.MathEvaluationResult.Bitwise -> "${math.decimalValue} (0x${math.hexValue})"
                }
                cursor.addRow(arrayOf<Any?>(
                    rowId++,
                    display,
                    "Calculation = $display",
                    "android.resource://${context?.packageName}/drawable/ic_search_lens_expressive",
                    Intent.ACTION_VIEW,
                    "intelligentsearch://search?q=${Uri.encode(display)}",
                    display,
                    "math:$display"
                ))
            }

            // 2. Apps
            for (app in results.apps) {
                val launchIntentUri = context?.packageManager?.getLaunchIntentForPackage(app.packageName)?.toUri(Intent.URI_INTENT_SCHEME)
                    ?: "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=${app.packageName};end"

                cursor.addRow(arrayOf<Any?>(
                    rowId++,
                    app.name,
                    "Application",
                    "android.resource://${context?.packageName}/drawable/ic_search_lens_expressive",
                    Intent.ACTION_VIEW,
                    launchIntentUri,
                    app.name,
                    "app:${app.packageName}"
                ))
            }

            // 3. Contacts
            for (contact in results.contacts) {
                cursor.addRow(arrayOf<Any?>(
                    rowId++,
                    contact.name,
                    contact.phoneNumber.ifBlank { "Contact" },
                    "android.resource://${context?.packageName}/drawable/ic_search_lens_expressive",
                    Intent.ACTION_VIEW,
                    contact.lookupUri.ifBlank { "tel:${contact.phoneNumber}" },
                    contact.name,
                    "contact:${contact.name.hashCode()}"
                ))
            }

            // 4. App Shortcuts
            for (shortcut in results.shortcuts) {
                val shortcutLaunchUri = "intent:#Intent;action=android.intent.action.MAIN;package=${shortcut.packageName};end"
                cursor.addRow(arrayOf<Any?>(
                    rowId++,
                    shortcut.shortLabel,
                    shortcut.longLabel.ifBlank { "Shortcut" },
                    "android.resource://${context?.packageName}/drawable/ic_search_lens_expressive",
                    Intent.ACTION_VIEW,
                    shortcutLaunchUri,
                    shortcut.shortLabel,
                    "shortcut:${shortcut.packageName}/${shortcut.id}"
                ))
            }
        }

        // 5. Live Google Autocomplete Suggestions for Pixel Launcher
        val suggestions = WebSearchProvider.getWebSuggestionsSync(queryTerm, timeoutMs = 600)
        for (suggestion in suggestions.take(5)) {
            val encodedSuggest = Uri.encode(suggestion)
            cursor.addRow(arrayOf<Any?>(
                rowId++,
                suggestion,
                "Google Search",
                "android.resource://${context?.packageName}/drawable/ic_search_lens_expressive",
                Intent.ACTION_WEB_SEARCH,
                "https://www.google.com/search?q=$encodedSuggest",
                suggestion,
                "suggest:${suggestion.hashCode()}"
            ))
        }

        // 6. Web Search Fallback Row
        val encodedQuery = Uri.encode(queryTerm)
        cursor.addRow(arrayOf<Any?>(
            rowId++,
            "Search Google for '$queryTerm'",
            "Google Search",
            "android.resource://${context?.packageName}/drawable/ic_search_lens_expressive",
            Intent.ACTION_WEB_SEARCH,
            "https://www.google.com/search?q=$encodedQuery",
            queryTerm,
            "web:$encodedQuery"
        ))

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
