package com.pixel.intelligentsearch.feature.search

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import com.pixel.intelligentsearch.feature.widget.WidgetActivity

/**
 * "Intelligent Search" in other apps' text selection menu (PROCESS_TEXT) and share sheet (SEND, plain text). Any app
 * can start it, so it only reads the one text extra, cleans it, and opens the search overlay with it typed in; the
 * overlay never runs a search, opens a link or places a call on its own. Nothing else from the caller's intent is
 * passed on, no result is returned (the selected text is never replaced), and the activity shows no window and
 * finishes at once, leaving no task behind.
 */
class SelectedTextSearchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = queryFrom(intent)
        if (query != null) {
            try {
                startActivity(overlayIntent(this, query))
            } catch (e: Exception) {
                Log.w(TAG, "Could not open the search overlay", e)
            }
        }
        finish()
    }

    companion object {
        private const val TAG = "SelectedTextSearch"

        /** The longest query handed to the overlay, as for every other way of opening it with text. */
        const val MAX_QUERY_LENGTH = 512

        /**
         * The query in a selection or share [intent]: "" when it carries no usable text, null when it is not a
         * plain-text PROCESS_TEXT or SEND at all. Only that action's own text extra is read.
         */
        fun queryFrom(intent: Intent?): String? {
            val extra = when (intent?.action) {
                Intent.ACTION_PROCESS_TEXT -> Intent.EXTRA_PROCESS_TEXT
                Intent.ACTION_SEND -> Intent.EXTRA_TEXT
                else -> return null
            }
            if (!isPlainText(intent.type)) return null
            // A value of another type reads as null; a bundle that can't be unparcelled throws, and counts as empty.
            val raw = try {
                intent.getCharSequenceExtra(extra)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Unreadable text extra", e)
                null
            }
            return sanitize(raw)
        }

        private fun isPlainText(type: String?): Boolean =
            type?.substringBefore(';')?.trim()?.equals("text/plain", ignoreCase = true) == true

        /**
         * [raw] as a one-line query: line breaks, tabs and other spacing become single spaces, control and
         * bidirectional-override characters are dropped, and the result is trimmed and cut to [MAX_QUERY_LENGTH]
         * without splitting a surrogate pair. Spans and styling are discarded.
         */
        fun sanitize(raw: CharSequence?): String {
            if (raw == null) return ""
            val out = StringBuilder(minOf(raw.length, MAX_QUERY_LENGTH + 2))
            var pendingSpace = false
            for (c in raw) {
                when {
                    Character.isWhitespace(c) || Character.isSpaceChar(c) -> pendingSpace = out.isNotEmpty()
                    Character.getType(c) == Character.CONTROL.toInt() || isBidiControl(c) -> Unit
                    else -> {
                        if (pendingSpace) out.append(' ')
                        pendingSpace = false
                        out.append(c)
                        // One character past the cap shows whether the cut lands inside a surrogate pair.
                        if (out.length > MAX_QUERY_LENGTH) break
                    }
                }
            }
            var end = minOf(out.length, MAX_QUERY_LENGTH)
            if (end < out.length && Character.isHighSurrogate(out[end - 1])) end--
            return out.substring(0, end).trimEnd()
        }

        /** Characters that reorder how text is displayed, which could make the query look like something else. */
        private fun isBidiControl(c: Char): Boolean = when (c.code) {
            0x061C, 0x200E, 0x200F, in 0x202A..0x202E, in 0x2066..0x2069, 0xFEFF -> true
            else -> false
        }

        /** Opens the search overlay with [query] typed in, or empty when [query] is blank. */
        fun overlayIntent(context: Context, query: String): Intent =
            Intent(context, WidgetActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                if (query.isNotBlank()) putExtra(SearchManager.QUERY, query)
            }

        /**
         * Shows or hides "Intelligent Search" in other apps' selection menus and share sheets by enabling or disabling
         * this activity, following the Search From Selected Text setting.
         */
        fun setEnabled(context: Context, enabled: Boolean) {
            val pm = context.packageManager
            val component = ComponentName(context, SelectedTextSearchActivity::class.java)
            val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            try {
                if (pm.getComponentEnabledSetting(component) != state) {
                    pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not update the selected text entry", e)
            }
        }
    }
}
