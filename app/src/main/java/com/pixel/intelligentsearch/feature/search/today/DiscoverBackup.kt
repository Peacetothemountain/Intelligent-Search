package com.pixel.intelligentsearch.feature.search.today

import android.content.Context
import com.pixel.intelligentsearch.feature.search.today.cards.grid.layoutForBackup
import com.pixel.intelligentsearch.feature.search.today.cards.grid.restoreLayout
import org.json.JSONObject

/*
 * Discover's part of the app's own backup: the page as the user arranged it (cards, places, shapes, hidden parts,
 * website addresses) and the quick controls chosen. What cards read from apps and websites, and the app cards' widgets,
 * stay on the phone.
 *
 * NG Designs
 */

internal object DiscoverBackup {
    private const val LAYOUT = "layout"
    private const val CONTROLS = "controls"

    /** Discover's choices as one JSON object for the backup file; empty when the user never changed any. Off the main thread. */
    fun export(context: Context): String {
        val root = JSONObject()
        layoutForBackup(context)?.let { root.put(LAYOUT, it) }
        savedQuickControls(context)?.let { root.put(CONTROLS, it) }
        return if (root.length() == 0) "" else root.toString()
    }

    /**
     * Restores what [export] wrote: each part the file has replaces this phone's, a part it doesn't have is left as it
     * is. Returns how many parts were restored; 0 for a file from before Discover was backed up, or one that can't be read.
     */
    fun restore(context: Context, json: String): Int {
        if (json.isBlank()) return 0
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return 0
        var restored = 0
        root.optString(LAYOUT).takeIf { it.isNotEmpty() }?.let { if (restoreLayout(context, it)) restored++ }
        root.optString(CONTROLS).takeIf { it.isNotEmpty() }?.let { if (restoreQuickControls(context, it)) restored++ }
        return restored
    }
}
