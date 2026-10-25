package com.pixel.intelligentsearch.core.system

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The one owner of other apps' media sessions (what's playing, play/pause, skip, seek), for At a Glance's Media Controls.
 * Android only shares them with an app whose notification listener the user has granted, so
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
     * [pick]'s session out of each list [sessions] gives, picked again as a paused session's [PAUSED_MEDIA_MS] runs
     * out, so a player left paused leaves on time even though nothing about it changes.
     */
    fun picked(sessions: Flow<List<MediaController>>, nowElapsed: () -> Long = SystemClock::elapsedRealtime): Flow<MediaController?> =
        pickedBy(sessions, ::stateOf, nowElapsed)

    /** [picked] for sessions read through [stateOf], so tests can hand in plain states. */
    internal fun <T> pickedBy(sessions: Flow<List<T>>, stateOf: (T) -> SessionState, nowElapsed: () -> Long): Flow<T?> = channelFlow {
        sessions.collectLatest { found ->
            while (true) {
                val now = nowElapsed()
                val states = found.map(stateOf)
                send(pickIndex(states, now)?.let(found::get))
                val until = pausedShownUntil(states, now) ?: break
                delay(until - now)
            }
        }
    }

    /** When the first of [sessions] showing only because it was paused a short while ago stops showing; null when none is. */
    fun pausedShownUntil(sessions: List<SessionState>, nowElapsed: Long): Long? = sessions.mapNotNull { session ->
        val since = session.lastUpdateElapsed ?: return@mapNotNull null
        val pausedAndShown = session.title != null && playingState(session.state, since, nowElapsed) == false
        if (pausedAndShown) since + PAUSED_MEDIA_MS + 1 else null
    }.minOrNull()

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

    /**
     * Where a session's track is, for At a Glance's seek bar: its length, its position at [updatedAtElapsed]
     * (elapsedRealtime) moving at [speed] (0 while paused or buffering), and whether the player takes a seek.
     */
    data class Progress(
        val durationMs: Long,
        val positionMs: Long,
        val updatedAtElapsed: Long,
        val speed: Float,
        val canSeek: Boolean
    )

    /** The track's length from its metadata ([MediaMetadata.METADATA_KEY_DURATION]); null when it has none, as a live stream. */
    fun durationOf(controller: MediaController): Long? =
        runCatching { controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) }.getOrNull()?.takeIf { it > 0L }

    /** [controller]'s [Progress], or null without a length or a known position. */
    fun progressOf(controller: MediaController): Progress? {
        val duration = durationOf(controller) ?: return null
        val playback = runCatching { controller.playbackState }.getOrNull() ?: return null
        return progressOf(duration, playback.state, playback.position, playback.lastPositionUpdateTime, playback.playbackSpeed, playback.actions)
    }

    /** [Progress] from a session's [durationMs] and playback state; null when the position is unknown (negative). */
    fun progressOf(durationMs: Long, state: Int, positionMs: Long, updatedAtElapsed: Long, speed: Float, actions: Long): Progress? {
        if (durationMs <= 0L || positionMs < 0L) return null
        val moving = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING
        return Progress(durationMs, positionMs.coerceAtMost(durationMs), updatedAtElapsed, if (moving) speed else 0f, canSeek(actions))
    }

    /** Whether a session's [actions] (PlaybackState.getActions) include seeking. */
    fun canSeek(actions: Long): Boolean = actions and PlaybackState.ACTION_SEEK_TO != 0L

    /** Where [progress]'s track is at [nowElapsed]: its position moved on at its speed since then, within the track. */
    fun livePositionMs(progress: Progress, nowElapsed: Long): Long {
        val since = (nowElapsed - progress.updatedAtElapsed).coerceAtLeast(0L)
        return (progress.positionMs + (since * progress.speed).toLong()).coerceIn(0L, progress.durationMs)
    }

    /** Seeks [controller] to [positionMs] when its player takes a seek; nothing otherwise. */
    fun seekTo(controller: MediaController, positionMs: Long) {
        val actions = runCatching { controller.playbackState?.actions }.getOrNull() ?: return
        if (!canSeek(actions)) return
        val length = durationOf(controller)
        controller.transportControls.seekTo(if (length != null) positionMs.coerceIn(0L, length) else positionMs.coerceAtLeast(0L))
    }
}
