package com.pixel.intelligentsearch.core.system

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.util.storageContext
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The notification listener Android asks for before it shares media sessions ([MediaSessionAccess]). Android binds it
 * only while the user has granted notification access. It ignores notifications entirely: it watches the media
 * sessions and redraws the widgets when what's playing changes, so a widget's media row keeps up, and only while a
 * widget design shows that row. (Play policy: notification access is used solely for the media controls the user
 * turned on; nothing is read from, stored from or sent from notifications.)
 */
@AndroidEntryPoint
class MediaSessionListenerService : NotificationListenerService() {

    @Inject lateinit var widgetUpdater: WidgetUpdater

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    private val handler = Handler(Looper.getMainLooper())
    private var watched: List<MediaController> = emptyList()
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
            val prefs = storageContext().getSharedPreferences(SettingsManager.PREFS_NAME, MODE_PRIVATE)
            if (WidgetDesign.entries.none { prefs[it.showMediaKey] }) return@launch
            val now = MediaSessionAccess.nowPlaying(this@MediaSessionListenerService)?.copy(sessionActivity = null)
            if (now == lastShown) return@launch
            lastShown = now
            widgetUpdater.updateAll()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val manager = getSystemService(MediaSessionManager::class.java) ?: return
            val component = MediaSessionAccess.listenerComponent(this)
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            watch(manager.getActiveSessions(component))
            scheduleRedraw()
        } catch (e: SecurityException) {
            Log.w(TAG, "Media sessions aren't readable", e)
        }
    }

    override fun onListenerDisconnected() {
        runCatching { getSystemService(MediaSessionManager::class.java)?.removeOnActiveSessionsChangedListener(sessionsListener) }
        watch(emptyList())
        handler.removeCallbacks(redraw)
        super.onListenerDisconnected()
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

    private companion object {
        const val TAG = "MediaSessionListener"
        const val SETTLE_MS = 300L
    }
}
