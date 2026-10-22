package com.pixel.intelligentsearch.core.system

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.util.storageContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Keeps the widgets' media row up to date: watches the active media sessions and redraws the widgets when what's
 * playing changes, once a burst of changes settles. Driven by the app's one notification listener
 * ([com.pixel.intelligentsearch.core.glance.NotificationAccessListener]) while it is connected and a widget design
 * shows the row; it never reads notifications, only the media sessions Android shares with that listener.
 */
class WidgetMediaWatcher(
    private val context: Context,
    private val listener: ComponentName,
    private val widgetUpdater: WidgetUpdater,
    private val scope: CoroutineScope
) {
    private val handler = Handler(Looper.getMainLooper())
    private var watched: List<MediaController> = emptyList()
    private var started = false
    /** What the widgets last showed, so a session reporting the same thing again doesn't redraw them. */
    @Volatile private var lastShown: MediaSessionAccess.NowPlaying? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleRedraw()
        override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleRedraw()
        override fun onSessionDestroyed() = scheduleRedraw()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers.orEmpty())
        scheduleRedraw()
    }

    private val redraw = Runnable {
        scope.launch(Dispatchers.IO) {
            if (!anyWidgetShowsMedia(context)) return@launch
            val now = MediaSessionAccess.nowPlaying(context)?.copy(sessionActivity = null)
            if (now == lastShown) return@launch
            lastShown = now
            widgetUpdater.updateAll()
        }
    }

    /** Starts watching; on the main thread, from the listener. Does nothing if already watching. */
    fun start() {
        if (started) return
        try {
            val manager = context.getSystemService(MediaSessionManager::class.java) ?: return
            manager.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            started = true
            watch(manager.getActiveSessions(listener))
            scheduleRedraw()
        } catch (e: SecurityException) {
            Log.w(TAG, "Media sessions aren't readable", e)
        }
    }

    /** Stops watching (the listener disconnected, or no widget shows the row any more). */
    fun stop() {
        if (started) {
            runCatching { context.getSystemService(MediaSessionManager::class.java)?.removeOnActiveSessionsChangedListener(sessionsListener) }
            started = false
        }
        watch(emptyList())
        handler.removeCallbacks(redraw)
    }

    private fun watch(controllers: List<MediaController>) {
        watched.forEach { it.unregisterCallback(controllerCallback) }
        watched = controllers
        controllers.forEach { it.registerCallback(controllerCallback, handler) }
    }

    /** Waits for a burst of session changes (a track change sends several) to settle before redrawing once. */
    private fun scheduleRedraw() {
        handler.removeCallbacks(redraw)
        handler.postDelayed(redraw, SETTLE_MS)
    }

    companion object {
        private const val TAG = "WidgetMediaWatcher"
        private const val SETTLE_MS = 300L

        /** Whether any widget design shows the media row (Widget Customization -> Media Controls). */
        fun anyWidgetShowsMedia(context: Context): Boolean {
            val prefs = context.storageContext().getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
            return WidgetDesign.entries.any { prefs[it.showMediaKey] }
        }
    }
}
