package com.pixel.intelligentsearch.core.system

import android.content.Context
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Keeps the widgets' media row up to date: follows the media sessions ([MediaSessionAccess.sessions]) and redraws the
 * widgets when what the row shows changes, once a burst of changes settles. Driven by the app's one notification
 * listener ([com.pixel.intelligentsearch.core.glance.NotificationAccessListener]) while it is connected and a widget
 * design shows the row; it never reads notifications, only the media sessions Android shares with that listener.
 */
class WidgetMediaWatcher(
    private val context: Context,
    private val widgetUpdater: WidgetUpdater,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    /** Starts watching; from the listener. Does nothing if already watching. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            MediaSessionAccess.sessions(context)
                // A track change sends several updates; redraw once they settle.
                .debounce(SETTLE_MS)
                .map { found ->
                    val hidden = MediaSessionAccess.hiddenApps(context)
                    // The app's page for the session changes identity on every update; it isn't drawn.
                    MediaSessionAccess.pick(found.filterNot { it.packageName in hidden })
                        ?.let(MediaSessionAccess::nowPlayingOf)?.copy(sessionActivity = null)
                }
                // Starting from "nothing playing", as the widgets drew without the sessions: a first nothing changes
                // nothing, a first song redraws them.
                .onStart { emit(null) }
                .distinctUntilChanged()
                .drop(1)
                .flowOn(Dispatchers.IO)
                .collect { if (anyWidgetShowsMedia(context)) widgetUpdater.updateAll() }
        }
    }

    /** Stops watching (the listener disconnected, or no widget shows the row any more). */
    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val SETTLE_MS = 300L

        /** Whether any widget design shows the media row (Widget Customization -> Media Controls). */
        fun anyWidgetShowsMedia(context: Context): Boolean {
            val prefs = SettingsManager.storedPrefs(context)
            return WidgetDesign.entries.any { prefs[it.showMediaKey] }
        }
    }
}
