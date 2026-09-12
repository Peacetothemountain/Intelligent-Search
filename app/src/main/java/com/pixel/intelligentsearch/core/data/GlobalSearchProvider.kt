package com.pixel.intelligentsearch.core.data

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
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
        return query(uri, projection, null, null)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: android.os.Bundle?,
        cancellationSignal: android.os.CancellationSignal?
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

        if (cancellationSignal?.isCanceled == true) return cursor

        val selectionArgs = queryArgs?.getStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)
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

        if (coordinator != null && cancellationSignal?.isCanceled != true) {
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
                if (cancellationSignal?.isCanceled == true) break
                val launchIntent = context?.packageManager?.getLaunchIntentForPackage(app.packageName)
                    ?: if (app.activityName != null) {
                        Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            setClassName(app.packageName, app.activityName)
                        }
                    } else null
                val launchIntentUri = launchIntent?.toUri(Intent.URI_INTENT_SCHEME)
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

            // 3. Contacts (Restricted to callers with READ_CONTACTS or self/system)
            val caller = callingPackage
            val isAuthorized = caller == null || 
                caller == context?.packageName || 
                context?.packageManager?.checkPermission(android.Manifest.permission.READ_CONTACTS, caller) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (isAuthorized) {
                for (contact in results.contacts) {
                    if (cancellationSignal?.isCanceled == true) break
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
            }

            // 4. App Shortcuts
            for (shortcut in results.shortcuts) {
                if (cancellationSignal?.isCanceled == true) break
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

        // 5. Dynamic Autocomplete Suggestions & Web Search for Pixel Launcher
        val prefs = context?.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val engine = prefs?.getString("search.engine", "Google") ?: "Google"
        val customUrl = prefs?.getString("custom_search_engine_url", "") ?: ""

        val providerName = when (engine) {
            "DuckDuckGo" -> "DuckDuckGo"
            "Bing" -> "Bing"
            "Custom" -> if (customUrl.isNotBlank()) "Web" else "Web"
            else -> "Google"
        }

        fun buildWebSearchUrl(q: String): String {
            val encoded = Uri.encode(q)
            return when (engine) {
                "DuckDuckGo" -> "https://duckduckgo.com/?q=$encoded"
                "Bing" -> "https://www.bing.com/search?q=$encoded"
                "Custom" -> {
                    if (customUrl.isNotBlank()) {
                        val rawUrl = if (customUrl.contains("%s")) {
                            customUrl.replace("%s", encoded)
                        } else {
                            "$customUrl$encoded"
                        }
                        if (rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)) {
                            rawUrl
                        } else {
                            "https://$rawUrl"
                        }
                    } else "https://www.google.com/search?q=$encoded"
                }
                else -> "https://www.google.com/search?q=$encoded"
            }
        }

        // Check in-memory cache first (< 0.1ms). If uncached, fetch with strict 80ms timeout to avoid Binder stall.
        val cached = WebSearchProvider.getCachedSuggestions(queryTerm, engine)
        val suggestions = cached ?: WebSearchProvider.getWebSuggestionsSync(queryTerm, engine, timeoutMs = 80)
        for (suggestion in suggestions.take(5)) {
            val suggestUrl = buildWebSearchUrl(suggestion)
            cursor.addRow(arrayOf<Any?>(
                rowId++,
                suggestion,
                "$providerName Search",
                "android.resource://${context?.packageName}/drawable/ic_search_lens_expressive",
                Intent.ACTION_VIEW,
                suggestUrl,
                suggestion,
                "suggest:${suggestion.hashCode()}"
            ))
        }

        // 6. Web Search Fallback Row
        val searchUrl = buildWebSearchUrl(queryTerm)
        val encodedQuery = Uri.encode(queryTerm)
        cursor.addRow(arrayOf<Any?>(
            rowId++,
            "Search $providerName for '$queryTerm'",
            "$providerName Search",
            "android.resource://${context?.packageName}/drawable/ic_search_lens_expressive",
            Intent.ACTION_VIEW,
            searchUrl,
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
