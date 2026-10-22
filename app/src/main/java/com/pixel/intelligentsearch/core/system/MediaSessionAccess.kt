package com.pixel.intelligentsearch.core.system

import android.app.PendingIntent
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The one owner of other apps' media sessions (what's playing, play/pause, next), for At a Glance's Media Controls and
 * the widget's media row. Android only shares them with an app whose notification listener the user has granted, so
 * this asks through the app's one listener and its one grant ([NotificationAccess]), the same that Search Your
 * Notifications uses. Nothing is read from notifications themselves; only the active media sessions. Hidden apps'
 * sessions are never shown.
 *
 * One rule decides which session shows everywhere ([pick]): the first one playing, else one paused less than
 * [PAUSED_MEDIA_MS] ago.
 */
object MediaSessionAccess {

    private const val TAG = "MediaSessionAccess"

    /** How long a paused player stays: long enough to come back to, not hours later. */
    const val PAUSED_MEDIA_MS = 30 * 60 * 1000L

    /** The active media sessions, most relevant first (as Android orders them); empty without access. */
    fun activeControllers(context: Context): List<MediaController> {
        if (!NotificationAccess.isGranted(context)) return emptyList()
        return try {
            context.getSystemService(MediaSessionManager::class.java)?.getActiveSessions(NotificationAccess.component(context)).orEmpty()
        } catch (e: SecurityException) {
            Log.w(TAG, "Media sessions aren't readable", e)
            emptyList()
        }
    }

    /**
     * The active sessions now and every time they change: a session starting or ending, or one's playback state or
     * track changing. Emits an empty list without access. Every listener and callback is removed when collection stops.
     */
    fun sessions(context: Context): Flow<List<MediaController>> = flow {
        if (NotificationAccess.isGranted(context)) emitAll(watch(context)) else emit(emptyList())
    }

    private fun watch(context: Context): Flow<List<MediaController>> = callbackFlow {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        if (manager == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val component = NotificationAccess.component(context)
        val handler = Handler(Looper.getMainLooper())
        // Touched only on the main thread, where every callback below arrives.
        var controllers: List<MediaController> = emptyList()
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { trySend(controllers) }
            override fun onMetadataChanged(metadata: MediaMetadata?) { trySend(controllers) }
            override fun onSessionDestroyed() { trySend(controllers) }
        }
        fun attach(found: List<MediaController>?) {
            controllers.forEach { it.unregisterCallback(callback) }
            controllers = found.orEmpty()
            controllers.forEach { it.registerCallback(callback, handler) }
            trySend(controllers)
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }
        handler.post {
            try {
                manager.addOnActiveSessionsChangedListener(listener, component, handler)
                attach(manager.getActiveSessions(component))
            } catch (e: SecurityException) {
                // Notification access was revoked in between.
                Log.w(TAG, "Media sessions aren't readable", e)
                trySend(emptyList())
            }
        }
        awaitClose {
            handler.post {
                runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
                controllers.forEach { it.unregisterCallback(callback) }
                controllers = emptyList()
            }
        }
    }

    /** The session's track title, or null when it has none to show. */
    fun titleOf(controller: MediaController): String? {
        val metadata = runCatching { controller.metadata }.getOrNull() ?: return null
        return (metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
            ?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** What [pick] looks at in a session: its title and playback state, and when its position last changed. */
    data class SessionState(val title: String?, val state: Int?, val lastUpdateElapsed: Long?)

    private fun stateOf(controller: MediaController): SessionState {
        val playback = runCatching { controller.playbackState }.getOrNull()
        return SessionState(titleOf(controller), playback?.state, playback?.lastPositionUpdateTime)
    }

    /** Which of [sessions] shows ([pick]'s rule), by index; null when none does. */
    fun pickIndex(sessions: List<SessionState>, nowElapsed: Long): Int? {
        val shown = sessions.withIndex().mapNotNull { (index, session) ->
            if (session.title == null) return@mapNotNull null
            val playing = playingState(session.state, session.lastUpdateElapsed, nowElapsed) ?: return@mapNotNull null
            index to playing
        }
        return (shown.firstOrNull { it.second } ?: shown.firstOrNull())?.first
    }

    /** The session to show out of [controllers]: the first one playing, else one paused a short while ago. */
    fun pick(controllers: List<MediaController>, nowElapsed: Long = SystemClock.elapsedRealtime()): MediaController? =
        pickIndex(controllers.map(::stateOf), nowElapsed)?.let(controllers::get)

    /**
     * Whether a session in playback [state] shows, and as playing: true while playing or buffering, false while paused
     * for less than [PAUSED_MEDIA_MS] (by [lastUpdateElapsed], when its position last changed), null otherwise.
     */
    fun playingState(state: Int?, lastUpdateElapsed: Long?, nowElapsed: Long): Boolean? = when (state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING -> true
        PlaybackState.STATE_PAUSED ->
            if (lastUpdateElapsed != null && nowElapsed - lastUpdateElapsed in 0..PAUSED_MEDIA_MS) false else null
        else -> null
    }

    /** The apps the user hid, whose sessions the widget doesn't show either. */
    fun hiddenApps(context: Context): Set<String> = SettingsManager.storedPrefs(context)[SettingsManager.HIDDEN_APPS]

    /** The session the widget's media row shows and its buttons control; null when nothing does or there is no access. */
    fun current(context: Context, hiddenApps: Set<String> = hiddenApps(context)): MediaController? =
        pick(activeControllers(context).filterNot { it.packageName in hiddenApps })

    /** [current] as the widget shows it. */
    fun nowPlaying(context: Context, hiddenApps: Set<String> = hiddenApps(context)): NowPlaying? =
        current(context, hiddenApps)?.let(::nowPlayingOf)

    /** [controller] as the widget shows it, or null when it has no title. */
    fun nowPlayingOf(controller: MediaController): NowPlaying? = NowPlaying(
        title = titleOf(controller) ?: return null,
        artist = runCatching { controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) }.getOrNull()?.takeIf { it.isNotBlank() },
        isPlaying = runCatching { controller.playbackState?.state }.getOrNull() == PlaybackState.STATE_PLAYING,
        packageName = controller.packageName,
        sessionActivity = controller.sessionActivity
    )

    /** One media session's title, artist and state; [sessionActivity] is the app's own page for it, if it has one. */
    data class NowPlaying(
        val title: String,
        val artist: String?,
        val isPlaying: Boolean,
        val packageName: String,
        val sessionActivity: PendingIntent? = null
    ) {
        /** "Title · Artist", or the title alone. */
        val line: String get() = if (artist == null) title else "$title · $artist"
    }
}
