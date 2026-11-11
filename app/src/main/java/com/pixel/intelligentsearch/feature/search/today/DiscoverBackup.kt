package com.pixel.intelligentsearch.feature.search.today

import android.content.Context
import com.pixel.intelligentsearch.core.backup.DiscoverBackupPart
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.pixel.intelligentsearch.feature.search.today.cards.grid.layoutForBackup
import com.pixel.intelligentsearch.feature.search.today.cards.grid.restoreLayout
import org.json.JSONObject
import javax.inject.Inject

/*
 * Discover's part of the app's own backup: the page as the user arranged it (cards, places, shapes, hidden parts,
 * website addresses) and the quick controls chosen. What cards read from apps and websites, and the app cards' widgets,
 * stay on the phone.
 *
 * NG Designs
 */

class DiscoverBackup @Inject constructor(@param:ApplicationContext private val context: Context) : DiscoverBackupPart {
    private companion object {
        const val LAYOUT = "layout"
        const val CONTROLS = "controls"
    }

    override fun export(): String {
        val root = JSONObject()
        layoutForBackup(context)?.let { root.put(LAYOUT, it) }
        savedQuickControls(context)?.let { root.put(CONTROLS, it) }
        return if (root.length() == 0) "" else root.toString()
    }

    /**
     * Restores what [export] wrote: each part the file has replaces this phone's, a part it doesn't have is left as it
     * is. Returns how many parts were restored; 0 for a file from before Discover was backed up, or one that can't be read.
     */
    override fun restore(json: String): Int {
        if (json.isBlank()) return 0
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return 0
        var restored = 0
        root.optString(LAYOUT).takeIf { it.isNotEmpty() }?.let { if (restoreLayout(context, it)) restored++ }
        root.optString(CONTROLS).takeIf { it.isNotEmpty() }?.let { if (restoreQuickControls(context, it)) restored++ }
        return restored
    }
}

/** Gives core's backup Discover's part, in a module of its own so tests that replace the app's module keep it. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DiscoverBackupModule {
    @Binds
    abstract fun bindDiscoverBackup(impl: DiscoverBackup): DiscoverBackupPart
}
