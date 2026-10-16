package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.core.data.FileItem
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.pixel.intelligentsearch.core.data.*

@Suppress("DEPRECATION")
internal fun finishWithoutTransition(activity: android.app.Activity?) {
    if (activity != null && !activity.isFinishing) {
        activity.finishAndRemoveTask()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }
}

internal fun isMediaStoreUri(uri: Uri): Boolean =
    uri.authority == android.provider.MediaStore.AUTHORITY && uri.pathSegments.firstOrNull() == "external"

/**
 * Opens a file result. MediaStore entries prefer Files by Google; documents from the user's chosen
 * folders or the photo picker go to whichever app handles their type, carrying the read grant along.
 */
internal fun buildOpenFileIntent(uri: String, mimeType: String): Intent {
    val parsed = Uri.parse(uri)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(parsed, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (isMediaStoreUri(parsed)) setPackage("com.google.android.apps.nbu.files")
    }
}

/** Where a file result lives, e.g. "Documents/Taxes", or the picker label for chosen photos. */
internal fun fileLocationLabel(file: FileItem): String {
    val parent = file.path.substringBeforeLast('/', missingDelimiterValue = file.path)
        .removePrefix("/storage/emulated/0/")
        .removePrefix("/storage/emulated/0")
    return parent.ifBlank { file.mimeType }
}

/** Starts [intent]; returns false when nothing could handle it so callers can fall back. */
internal fun launchSafeIntent(context: Context, intent: Intent, options: android.os.Bundle? = null): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        if (options != null) {
            context.startActivity(intent, options)
        } else {
            context.startActivity(intent)
        }
        true
    } catch (e: Exception) {
        try {
            val activity = context.findActivity() ?: return false
            activity.startActivity(intent, options)
            true
        } catch (e2: Exception) {
            android.util.Log.w("IntelligentSearch", "Unexpected error", e2)
            false
        }
    }
}

/** The system's own web search for [query], answered by whichever app handles it. */
internal fun systemWebSearchIntent(query: String): Intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
    putExtra(android.app.SearchManager.QUERY, query)
    putExtra("query", query)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

/**
 * How to search the web for [query] with the chosen engine. Named engines open in an app that can show them and
 * fall back to the system web search; a custom address is re-validated on every search (https only, browsable
 * activities only); Tor never falls back to a normal browser, it asks for Tor Browser instead.
 */
internal fun webSearchLaunch(context: Context, engine: String, customUrl: String, query: String): com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch {
    val safe = com.pixel.intelligentsearch.core.security.SafeWebLaunch
    if (safe.isTorEngine(engine)) return safe.torSearch(context, query)
    fun viewOrFallback(url: String): Intent {
        val view = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return if (context.packageManager.resolveActivity(view, 0) != null) view else systemWebSearchIntent(query)
    }
    val encoded = Uri.encode(query)
    val intent = when (engine) {
        "Custom" -> com.pixel.intelligentsearch.core.security.CustomSearchUrl.build(customUrl, query)?.let { uri ->
            com.pixel.intelligentsearch.core.security.CustomSearchUrl.browsableViewIntent(uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } ?: systemWebSearchIntent(query)
        "DuckDuckGo" -> viewOrFallback("https://duckduckgo.com/?q=$encoded")
        "Bing" -> viewOrFallback("https://www.bing.com/search?q=$encoded")
        else -> {
            val google = systemWebSearchIntent(query).setPackage("com.google.android.googlequicksearchbox")
            if (context.packageManager.resolveActivity(google, 0) != null) google else systemWebSearchIntent(query)
        }
    }
    return com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.Open(intent)
}
