package com.pixel.intelligentsearch.core.system

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Reading other apps' media sessions (what's playing, play/pause, next). Android only lets an app with notification
 * access ask for them, so this is gated on the user granting it to [MediaSessionListenerService] in Android settings
 * (Special app access). Nothing is read from notifications themselves; only the active media sessions.
 */
object MediaSessionAccess {

    private const val TAG = "MediaSessionAccess"

    fun listenerComponent(context: Context): ComponentName = ComponentName(context, MediaSessionListenerService::class.java)

    /** Whether the user has given this app notification access, which is what lets it see media sessions. */
    fun isGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Android settings' page for granting it: this app's own notification access page. */
    fun grantIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The list of apps with notification access, for phones without the per-app page. */
    fun grantListIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The active media sessions, most relevant first (as Android orders them); empty without access. */
    fun activeControllers(context: Context): List<MediaController> {
        if (!isGranted(context)) return emptyList()
        return try {
            context.getSystemService(MediaSessionManager::class.java)?.getActiveSessions(listenerComponent(context)).orEmpty()
        } catch (e: SecurityException) {
            Log.w(TAG, "Media sessions aren't readable", e)
            emptyList()
        }
    }

    /** What's playing, or paused and ready to resume: the session that is playing, else the first with a title. */
    fun current(context: Context): MediaController? {
        val controllers = activeControllers(context).filter { titleOf(it) != null }
        return controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
    }

    /** [current] as the widget shows it; null when nothing is playing or there is no access. */
    fun nowPlaying(context: Context): NowPlaying? = current(context)?.let { controller ->
        NowPlaying(
            title = titleOf(controller) ?: return@let null,
            artist = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
            isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = controller.packageName,
            sessionActivity = controller.sessionActivity
        )
    }

    private fun titleOf(controller: MediaController): String? {
        val metadata = controller.metadata ?: return null
        return (metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
            ?.takeIf { it.isNotBlank() }
    }

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
