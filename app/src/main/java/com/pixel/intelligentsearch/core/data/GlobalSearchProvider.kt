package com.pixel.intelligentsearch.core.data

import com.pixel.intelligentsearch.core.util.storageContext
import com.pixel.intelligentsearch.core.data.settings.get
import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Process
import android.os.UserManager
import android.provider.BaseColumns
import android.util.Log
import com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine
import com.pixel.intelligentsearch.di.appEntryPoint

class GlobalSearchProvider : ContentProvider() {

    companion object {
        private const val TAG = "GlobalSearchProvider"
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
        val queryArgs = Bundle().apply {
            if (selection != null) putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            if (selectionArgs != null) putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            if (sortOrder != null) putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
        }
        return query(uri, projection, queryArgs, null)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?
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
        val rawQuery = extractQueryTerm(uri, selectionArgs)
        val queryTerm = rawQuery.replace("\u0000", "").trim().take(128)
        if (queryTerm.isBlank()) return cursor

        val safeCtx = context?.storageContext()
        val entryPoint = try {
            val appCtx = safeCtx?.applicationContext ?: safeCtx
            appCtx?.appEntryPoint
        } catch (e: Exception) {
            Log.w(TAG, "GlobalSearchProvider entry point unavailable: ${e.message}")
            null
        }
        val coordinator = entryPoint?.unifiedSearchCoordinator()
        val corpusIndexManager = entryPoint?.corpusIndexManager()
        val multiProfileManager = entryPoint?.multiProfileManager()
        if (corpusIndexManager != null && !corpusIndexManager.isIndexReady.value) {
            corpusIndexManager.initialize()
        }
        val isPrivateSpaceLocked = multiProfileManager?.isPrivateSpaceLocked() == true
        val privateUsers = if (isPrivateSpaceLocked) {
            multiProfileManager.profilesState.value
                .filter { it.profileType == ProfileType.PRIVATE }
                .mapTo(HashSet()) { it.userHandle }
        } else emptySet()

        var rowId = 1L

        val prefs = safeCtx?.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        val suggestionsEnabled = prefs?.get(SettingsManager.SEARCH_WEB_SUGGESTIONS) ?: true
        val engine = prefs?.get(SettingsManager.SEARCH_ENGINE) ?: "Google"
        val customUrl = prefs?.get(SettingsManager.CUSTOM_SEARCH_ENGINE_URL) ?: ""
        // Apps the user hid in-app must not resurface through system global search.
        val hiddenApps = prefs?.get(SettingsManager.HIDDEN_APPS).orEmpty()
        // The overlay's Search Sources switches apply here too.
        val appsOn = prefs?.get(SettingsManager.SEARCH_APPS) ?: true
        val calculatorOn = prefs?.get(SettingsManager.SEARCH_CALCULATOR) ?: true
        val webOn = prefs?.get(SettingsManager.SEARCH_WEB) ?: true
        val me = Process.myUserHandle()

        val isTorEngine = com.pixel.intelligentsearch.core.security.NetworkPolicy.isTorEngine(engine)
        val isCustomEngine = engine == com.pixel.intelligentsearch.core.security.NetworkPolicy.CUSTOM_ENGINE
        val providerName = when {
            isTorEngine -> "Tor"
            engine == "DuckDuckGo" -> "DuckDuckGo"
            engine == "Bing" -> "Bing"
            isCustomEngine -> "Web"
            else -> "Google"
        }

        fun buildWebSearchUrl(q: String): String {
            val encoded = Uri.encode(q)
            return when {
                isTorEngine -> "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/?q=$encoded"
                engine == "DuckDuckGo" -> "https://duckduckgo.com/?q=$encoded"
                engine == "Bing" -> "https://www.bing.com/search?q=$encoded"
                // An invalid or empty custom address falls back to Google rather than being opened.
                isCustomEngine -> com.pixel.intelligentsearch.core.security.CustomSearchUrl.build(customUrl, q)?.toString()
                    ?: "https://www.google.com/search?q=$encoded"
                else -> "https://www.google.com/search?q=$encoded"
            }
        }

        try {
            if (coordinator != null && cancellationSignal?.isCanceled != true) {
                val results = coordinator.executeSearchSync(queryTerm)

                // 1. Math calculation result if available (its row searches the web, so not for Tor)
                results.mathResult?.takeUnless { isTorEngine || !calculatorOn }?.let { math ->
                    val display = when (math) {
                        is MathematicalExpressionEngine.MathEvaluationResult.Computation -> math.formattedResult
                        is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion -> math.formatted
                        is MathematicalExpressionEngine.MathEvaluationResult.Bitwise -> "${math.decimalValue} (${math.hexValue})"
                    }
                    cursor.addRow(arrayOf<Any?>(
                        rowId++,
                        display,
                        "Calculation = $display",
                        "android.resource://${safeCtx?.packageName}/drawable/ic_search_lens_expressive",
                        Intent.ACTION_VIEW,
                        buildWebSearchUrl(display),
                        display,
                        "math:$display"
                    ))
                }

                // 2. Apps - In-memory intent synthesis
                for (app in results.apps) {
                    if (cancellationSignal?.isCanceled == true) break
                    if (!appsOn) break
                    if (app.userHandle != null && app.userHandle != me) continue
                    if (app.packageName in hiddenApps) continue
                    if ((app.isPrivateProfile || app.profileType == ProfileType.PRIVATE) && isPrivateSpaceLocked) continue
                    val launchIntentUri = if (app.activityName != null) {
                        "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=${app.packageName}/${app.activityName};end"
                    } else {
                        "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=${app.packageName};end"
                    }

                    cursor.addRow(arrayOf<Any?>(
                        rowId++,
                        app.name,
                        "Application",
                        "android.resource://${safeCtx?.packageName}/drawable/ic_search_lens_expressive",
                        Intent.ACTION_VIEW,
                        launchIntentUri,
                        app.name,
                        "app:${app.packageName}"
                    ))
                }

                // 3. Contacts (Strict caller validation preventing unauthorized contact leakage)
                val callingUid = android.os.Binder.getCallingUid()
                val myUid = Process.myUid()
                val isAuthorized = if (safeCtx == null || safeCtx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    false
                } else if (callingUid == myUid || callingUid == Process.SYSTEM_UID) {
                    true
                } else {
                    val callPkg = try { callingPackage } catch (_: Exception) { null }
                    if (callPkg != null) {
                        androidx.core.content.PermissionChecker.checkCallingPermission(
                            safeCtx,
                            android.Manifest.permission.READ_CONTACTS,
                            callPkg
                        ) == androidx.core.content.PermissionChecker.PERMISSION_GRANTED
                    } else {
                        safeCtx.checkCallingPermission(android.Manifest.permission.READ_CONTACTS) == 
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                }

                // Contacts show only when the user has contact search on, as in the app itself.
                val contactsOn = prefs?.get(SettingsManager.SEARCH_CONTACTS) ?: false
                if (isAuthorized && contactsOn) {
                    for (contact in results.contacts) {
                        if (cancellationSignal?.isCanceled == true) break
                        cursor.addRow(arrayOf<Any?>(
                            rowId++,
                            contact.name,
                            contact.phoneNumber.ifBlank { "Contact" },
                            "android.resource://${safeCtx?.packageName}/drawable/ic_search_lens_expressive",
                            Intent.ACTION_VIEW,
                            // The system parses this as a link: an encoded number keeps a '#' and what follows it.
                            contact.lookupUri.ifBlank { Uri.fromParts("tel", contact.phoneNumber, null).toString() },
                            contact.name,
                            "contact:${contact.name.hashCode()}"
                        ))
                    }
                }
            }

            // 5. Dynamic Autocomplete Suggestions
            val suggestions = if (prefs == null || !com.pixel.intelligentsearch.core.security.NetworkPolicy.webSuggestionsAllowed(prefs)) {
                emptyList()
            } else {
                WebSearchProvider.getCachedSuggestions(queryTerm, engine)?.map { it.query } ?: emptyList()
            }
            for (suggestion in suggestions.take(5)) {
                val suggestUrl = buildWebSearchUrl(suggestion)
                cursor.addRow(arrayOf<Any?>(
                    rowId++,
                    suggestion,
                    "$providerName Search",
                    "android.resource://${safeCtx?.packageName}/drawable/ic_search_lens_expressive",
                    Intent.ACTION_VIEW,
                    suggestUrl,
                    suggestion,
                    "suggest:${suggestion.hashCode()}"
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating search suggestions in GlobalSearchProvider", e)
        }

        // 6. Web Search Fallback Row (always present, except for Tor: a suggestion row opens in the default browser,
        // which must never be handed an onion address).
        if (isTorEngine || !webOn) return cursor
        val searchUrl = buildWebSearchUrl(queryTerm)
        val encodedQuery = Uri.encode(queryTerm)
        cursor.addRow(arrayOf<Any?>(
            rowId++,
            "Search $providerName for '$queryTerm'",
            "$providerName Search",
            "android.resource://${safeCtx?.packageName}/drawable/ic_search_lens_expressive",
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

        // Fallback to path segment only if not matching root SEARCH_PATH or standard suggestion subpaths
        val lastSegment = uri.lastPathSegment ?: ""
        val ignoredSegments = setOf(SEARCH_PATH.lowercase(), "search_suggest_query")
        return if (lastSegment.lowercase() in ignoredSegments) "" else lastSegment.trim()
    }

    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.dir/vnd.com.pixel.intelligentsearch.search"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
