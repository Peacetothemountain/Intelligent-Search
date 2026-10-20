package com.pixel.intelligentsearch.feature.search

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.util.storageContext
import com.pixel.intelligentsearch.feature.widget.WidgetActivity
import com.pixel.intelligentsearch.feature.widget.resolveActivityCompat

/**
 * "Intelligent Search" in other apps' text selection menu (PROCESS_TEXT) and share sheet (SEND, plain text). Any app
 * can start it, so it only reads the one text extra, cleans it, and opens the search overlay with it typed in; the
 * overlay never runs a search, opens a link or places a call on its own. Nothing else from the caller's intent is
 * passed on, no result is returned (the selected text is never replaced), and the activity shows no window and
 * finishes at once, leaving no task behind.
 *
 * Selected or shared text is never searched without the user confirming it. With the search overlay turned off it goes
 * to the Google app's search, which only types it in; without that app nothing opens and a toast explains why (the
 * overlay's own fallback, a web search, would run the search at once).
 */
class SelectedTextSearchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = queryFrom(intent)
        when {
            query == null -> Unit
            // A shared file (only a stream) or a share with no text: there is nothing to type in.
            query.isEmpty() && intent?.action == Intent.ACTION_SEND -> toast(NO_TEXT_MESSAGE)
            else -> {
                val target = searchIntent(this, query)
                if (target == null) {
                    toast(OVERLAY_OFF_MESSAGE)
                } else {
                    try {
                        startActivity(target)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not open search", e)
                    }
                }
            }
        }
        finish()
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "SelectedTextSearch"

        /** Shown when a share carries no text, such as a shared file. */
        const val NO_TEXT_MESSAGE = "Only text can be searched"

        /** Shown when the search overlay is off and no app can take the text without searching it straight away. */
        const val OVERLAY_OFF_MESSAGE = "Turn on the search overlay to search selected text"

        /** The longest query handed to the overlay, as for every other way of opening it with text. */
        const val MAX_QUERY_LENGTH = 512

        /** Joiners change how the letters or emoji around them are drawn, so they are kept. */
        private const val ZERO_WIDTH_NON_JOINER = 0x200C
        private const val ZERO_WIDTH_JOINER = 0x200D

        /** Guards the component state, so a caller holding an older value can't apply it after a newer one. */
        @VisibleForTesting
        internal val componentLock = Any()

        /**
         * The query in a selection or share [intent]: "" when it carries no usable text, null when it is not a
         * plain-text PROCESS_TEXT or SEND at all. Only that action's own text extra is read, and only as text, so a
         * shared file (a stream) reads as "".
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

        /** Plain text, or a wildcard type the plain-text filter matches too (an app sharing text as any text type). */
        private fun isPlainText(type: String?): Boolean =
            when (type?.substringBefore(';')?.trim()?.lowercase()) {
                "text/plain", "text/*", "*/*" -> true
                else -> false
            }

        /**
         * [raw] as a one-line query: line breaks, tabs and other spacing become single spaces; control characters,
         * invisible formatting characters (zero-width spaces, soft hyphens, tag characters, bidirectional overrides)
         * and unpaired surrogates are dropped, keeping the joiners emoji sequences and some scripts need inside the
         * text; and the result is trimmed of spacing and joiners at both ends and cut to [MAX_QUERY_LENGTH] without
         * splitting a surrogate pair. Spans and styling are discarded.
         */
        fun sanitize(raw: CharSequence?): String {
            if (raw == null) return ""
            val out = StringBuilder(minOf(raw.length, MAX_QUERY_LENGTH + 2))
            var pendingSpace = false
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                val codePoint = when {
                    Character.isHighSurrogate(c) && i + 1 < raw.length && Character.isLowSurrogate(raw[i + 1]) ->
                        Character.toCodePoint(c, raw[i + 1])
                    // Half of a surrogate pair on its own is not a character.
                    Character.isSurrogate(c) -> {
                        i++
                        continue
                    }
                    else -> c.code
                }
                i += Character.charCount(codePoint)
                when {
                    Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) -> pendingSpace = out.isNotEmpty()
                    isInvisible(codePoint) -> Unit
                    // A joiner at the start joins nothing.
                    isJoiner(codePoint) && out.isEmpty() -> Unit
                    else -> {
                        if (pendingSpace) out.append(' ')
                        pendingSpace = false
                        out.appendCodePoint(codePoint)
                        // One character past the cap shows whether the cut lands inside a surrogate pair.
                        if (out.length > MAX_QUERY_LENGTH) break
                    }
                }
            }
            var end = minOf(out.length, MAX_QUERY_LENGTH)
            if (end < out.length && Character.isHighSurrogate(out[end - 1])) end--
            // Nor does one at the end, so text of only joiners and spacing is empty.
            return out.substring(0, end).trimEnd { it.isWhitespace() || isJoiner(it.code) }
        }

        private fun isJoiner(codePoint: Int): Boolean = codePoint == ZERO_WIDTH_JOINER || codePoint == ZERO_WIDTH_NON_JOINER

        /**
         * Control characters, and formatting characters that draw nothing: they could hide text in the query or, like
         * the bidirectional overrides, make it read as something else.
         */
        private fun isInvisible(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
            Character.CONTROL.toInt() -> true
            Character.FORMAT.toInt() -> codePoint != ZERO_WIDTH_JOINER && codePoint != ZERO_WIDTH_NON_JOINER
            else -> false
        }

        /** Opens the search overlay with [query] typed in, or empty when [query] is blank. */
        fun overlayIntent(context: Context, query: String): Intent =
            Intent(context, WidgetActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                if (query.isNotBlank()) putExtra(SearchManager.QUERY, query)
            }

        /**
         * Where [query] goes: the search overlay, or while the overlay is off the Google app's search with it typed
         * in; null when neither can open. Never a web search, which would send the text off the device at once.
         */
        fun searchIntent(context: Context, query: String): Intent? {
            if (settings(context)[SettingsManager.SEARCH_OVERLAY_ENABLED]) return overlayIntent(context, query)
            val google = Intent("android.search.action.GLOBAL_SEARCH").apply {
                setPackage("com.google.android.googlequicksearchbox")
                if (query.isNotBlank()) putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return google.takeIf { context.packageManager.resolveActivityCompat(it) != null }
        }

        private fun settings(context: Context): SharedPreferences =
            context.storageContext().getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * Shows or hides "Intelligent Search" in other apps' selection menus and share sheets by enabling or disabling
         * this activity. Callers following the Search from Selected Text setting use [syncWithSetting].
         */
        fun setEnabled(context: Context, enabled: Boolean) {
            synchronized(componentLock) { applyEnabled(context, enabled) }
        }

        /**
         * Matches this activity's state to the Search from Selected Text setting. Android keeps component states
         * outside the app's data, so this runs at start-up, after a restore and when the switch changes. The setting
         * is read under the lock the state is written with, so a slower caller can't apply a value that a newer
         * change has replaced.
         */
        fun syncWithSetting(context: Context) {
            synchronized(componentLock) {
                applyEnabled(context, settings(context)[SettingsManager.SEARCH_FROM_SELECTED_TEXT])
            }
        }

        private fun applyEnabled(context: Context, enabled: Boolean) {
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
