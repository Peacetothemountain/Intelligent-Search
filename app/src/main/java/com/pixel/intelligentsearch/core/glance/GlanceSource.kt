package com.pixel.intelligentsearch.core.glance

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.os.SystemClock
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import com.pixel.intelligentsearch.core.system.MediaSessionAccess
import com.pixel.intelligentsearch.core.system.openPendingOrApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Where At a Glance reads from; the screen collects it only while the search is on screen. Tests hand in fixed data. */
fun interface GlanceSource {
    fun snapshots(toggles: GlanceToggles, hiddenApps: Set<String>): Flow<GlanceSnapshot>
}

/**
 * At a Glance from the phone: the next alarm (AlarmManager), media sessions (MediaSessionManager, which Android opens
 * only to an app with notification access), contacts' birthdays (with the contacts permission), and the timers in
 * the [index]. Only what [GlanceToggles] allows is read, every receiver and listener is removed when collection
 * stops, and nothing touches the network or the disk beyond the contacts provider.
 */
class AndroidGlanceSource(private val context: Context, private val index: NotificationIndex) : GlanceSource {

    /** Today's birthdays from the last look, shown at once while the contacts are read again. */
    private val birthdayCache = Birthdays.DayCache()

    override fun snapshots(toggles: GlanceToggles, hiddenApps: Set<String>): Flow<GlanceSnapshot> {
        if (!toggles.anyOn) return flowOf(GlanceSnapshot.EMPTY)
        val birthdays = if (toggles.birthdaysOn) {
            Birthdays.upcoming({ Birthdays.load(context) }, birthdayCache).flowOn(Dispatchers.IO)
        } else flowOf(emptyList())
        val timers = if (toggles.timersOn) timerFlow(hiddenApps) else flowOf(emptyList())
        val alarm = if (toggles.alarmOn) alarmFlow() else flowOf(null)
        val media = if (toggles.mediaOn) mediaFlow(hiddenApps) else flowOf(null)
        return combine(alarm, media, birthdays, timers) { a, m, days, t ->
            GlanceSnapshot(alarm = a, timers = t, media = m, birthdays = days)
        }
    }

    private fun timerFlow(hiddenApps: Set<String>): Flow<List<GlanceTimer>> = index.entries
        .onStart {
            // Access revoked since the last look: forget what was read before showing anything.
            NotificationAccess.syncIndex(context, index)
            // Granted: make sure Android has the listener connected (it can drop it, after an update say).
            NotificationAccess.start(context)
        }
        .map { all -> timersFrom(all.filterNot { it.packageName in hiddenApps }) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private fun alarmFlow(): Flow<GlanceAlarm?> = callbackFlow {
        val alarms = context.getSystemService(AlarmManager::class.java)
        fun read() = runCatching { alarms?.nextAlarmClock?.let { GlanceAlarm(it.triggerTime, it.showIntent) } }.getOrNull()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(read())
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        trySend(read())
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /** The media session At a Glance shows ([MediaSessionAccess.picked]), with its artwork and controls. */
    private fun mediaFlow(hiddenApps: Set<String>): Flow<GlanceMedia?> {
        val artworks = ArtworkCache()
        return MediaSessionAccess.picked(MediaSessionAccess.sessions(context).map { found -> found.filterNot { it.packageName in hiddenApps } })
            .map { picked -> picked?.let { glanceMedia(it, artworks) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    private fun glanceMedia(controller: MediaController, artworks: ArtworkCache): GlanceMedia? {
        val metadata = runCatching { controller.metadata }.getOrNull() ?: return null
        val title = MediaSessionAccess.titleOf(controller) ?: return null
        val playing = MediaSessionAccess.playingState(
            runCatching { controller.playbackState?.state }.getOrNull(),
            runCatching { controller.playbackState?.lastPositionUpdateTime }.getOrNull(),
            SystemClock.elapsedRealtime()
        ) == true
        val artist = (metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))?.trim().orEmpty()
        val appLabel = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(controller.packageName, 0)).toString()
        }.getOrDefault("")
        return GlanceMedia(
            title = title,
            artist = artist,
            appLabel = appLabel,
            packageName = controller.packageName,
            isPlaying = playing,
            artwork = artworks.artwork(controller.packageName, title, metadata),
            controls = SessionControls(context, controller),
            progress = MediaSessionAccess.progressOf(controller)
        )
    }

    /** The last artwork scaled down, so state changes (play, pause) don't scale the same picture again. */
    private class ArtworkCache {
        private var key: String? = null
        private var image: androidx.compose.ui.graphics.ImageBitmap? = null

        fun artwork(packageName: String, title: String, metadata: MediaMetadata): androidx.compose.ui.graphics.ImageBitmap? {
            val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val newKey = "$packageName|$title|${bitmap?.generationId}"
            if (newKey == key) return image
            key = newKey
            image = bitmap?.let { scaledArtwork(it) }?.asImageBitmap()
            return image
        }

        private fun scaledArtwork(bitmap: Bitmap): Bitmap? = runCatching {
            val side = minOf(bitmap.width, bitmap.height)
            if (side <= 0) return@runCatching null
            if (side <= ARTWORK_PX) bitmap else bitmap.scale(bitmap.width * ARTWORK_PX / side, bitmap.height * ARTWORK_PX / side)
        }.getOrNull()
    }

    /** A session's transport controls; equal for the same session. */
    private class SessionControls(private val context: Context, private val controller: MediaController) : MediaControls {
        override fun play() = controller.transportControls.play()
        override fun pause() = controller.transportControls.pause()
        override fun next() = controller.transportControls.skipToNext()
        override fun previous() = controller.transportControls.skipToPrevious()
        override fun seekTo(positionMs: Long) = MediaSessionAccess.seekTo(controller, positionMs)
        override fun open() {
            openPendingOrApp(context, controller.sessionActivity, controller.packageName)
        }
        override fun equals(other: Any?) = other is SessionControls && other.controller.sessionToken == controller.sessionToken
        override fun hashCode() = controller.sessionToken.hashCode()
    }

    companion object {
        /** Artwork is shown at 40 dp; this covers the densest screens without holding a full-size cover. */
        private const val ARTWORK_PX = 160
    }
}
