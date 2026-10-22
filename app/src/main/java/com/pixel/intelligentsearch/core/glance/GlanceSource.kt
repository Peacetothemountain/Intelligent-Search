package com.pixel.intelligentsearch.core.glance

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import com.pixel.intelligentsearch.feature.settings.ChargingStatus
import com.pixel.intelligentsearch.feature.settings.DiagnosticsReadings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate

/** Where At a Glance reads from; the screen collects it only while the search is on screen. Tests hand in fixed data. */
fun interface GlanceSource {
    fun snapshots(toggles: GlanceToggles, hiddenApps: Set<String>): Flow<GlanceSnapshot>
}

/**
 * At a Glance from the phone: the next alarm (AlarmManager), the battery (its sticky broadcast), media sessions
 * (MediaSessionManager, which Android opens only to an app with notification access), contacts' birthdays, and the
 * timers in the [index]. Only what [GlanceToggles] switches on is read, every receiver and listener is
 * removed when collection stops, and nothing touches the network or the disk beyond the contacts provider.
 */
class AndroidGlanceSource(private val context: Context, private val index: NotificationIndex) : GlanceSource {

    override fun snapshots(toggles: GlanceToggles, hiddenApps: Set<String>): Flow<GlanceSnapshot> {
        if (!toggles.anyOn) return flowOf(GlanceSnapshot.EMPTY)
        val alarm = if (toggles.alarm) alarmFlow() else flowOf(null)
        val battery = if (toggles.battery) batteryFlow() else flowOf(null)
        val media = if (toggles.media) mediaFlow(hiddenApps) else flowOf(null)
        val birthdays = if (toggles.birthdays) {
            flow { emit(Birthdays.inWindow(Birthdays.load(context), LocalDate.now())) }.flowOn(Dispatchers.IO)
        } else flowOf(emptyList())
        val timers = if (toggles.notifications && toggles.timers) timerFlow(hiddenApps) else flowOf(emptyList())
        return combine(alarm, battery, media, birthdays, timers) { a, b, m, days, t ->
            GlanceSnapshot(alarm = a, timers = t, media = m, birthdays = days, battery = b)
        }
    }

    private fun timerFlow(hiddenApps: Set<String>): Flow<List<GlanceTimer>> = index.entries
        // Access revoked or the switch off since the last look: forget what was read before showing anything.
        .onStart { NotificationAccess.syncIndex(context, index) }
        .map { all -> timersFrom(all.filterNot { it.packageName in hiddenApps }) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private fun alarmFlow(): Flow<GlanceAlarm?> = callbackFlow {
        val alarms = context.getSystemService(AlarmManager::class.java)
        fun read() = runCatching { alarms?.nextAlarmClock?.let { GlanceAlarm(it.triggerTime) } }.getOrNull()
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

    private fun batteryFlow(): Flow<GlanceBattery?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let { trySend(batteryFrom(it)) }
            }
        }
        // The battery broadcast is sticky: registering hands back the current reading at once.
        val current = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        trySend(current?.let(::batteryFrom))
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /** Android opens media sessions only to an app with notification access; without it there is nothing to show. */
    private fun mediaFlow(hiddenApps: Set<String>): Flow<GlanceMedia?> = flow {
        if (NotificationAccess.isGranted(context)) emitAll(sessionsFlow(hiddenApps)) else emit(null)
    }.flowOn(Dispatchers.Default)

    private fun sessionsFlow(hiddenApps: Set<String>): Flow<GlanceMedia?> {
        val artworks = ArtworkCache()
        return callbackFlow {
            val sessions = context.getSystemService(MediaSessionManager::class.java)
            if (sessions == null) {
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
                    sessions.addOnActiveSessionsChangedListener(listener, component, handler)
                    attach(sessions.getActiveSessions(component))
                } catch (_: SecurityException) {
                    // Notification access was revoked in between.
                    trySend(emptyList())
                }
            }
            awaitClose {
                handler.post {
                    runCatching { sessions.removeOnActiveSessionsChangedListener(listener) }
                    controllers.forEach { it.unregisterCallback(callback) }
                    controllers = emptyList()
                }
            }
        }
            .map { found -> pickMedia(found.filterNot { it.packageName in hiddenApps }, artworks) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    /** The session to show: the first one playing, else one paused a short while ago. */
    private fun pickMedia(controllers: List<MediaController>, artworks: ArtworkCache): GlanceMedia? {
        val now = SystemClock.elapsedRealtime()
        val shown = controllers.mapNotNull { controller ->
            val metadata = runCatching { controller.metadata }.getOrNull() ?: return@mapNotNull null
            val state = runCatching { controller.playbackState }.getOrNull()
            val playing = mediaPlayingState(state?.state, state?.lastPositionUpdateTime, now) ?: return@mapNotNull null
            val title = (metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            Triple(controller, metadata, playing) to title
        }
        val (picked, title) = shown.firstOrNull { it.first.third } ?: shown.firstOrNull() ?: return null
        val (controller, metadata, playing) = picked
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
            controls = SessionControls(context, controller)
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
        override fun open() {
            val session = controller.sessionActivity
            if (session == null || !sendPendingIntent(context, session)) launchApp(context, controller.packageName)
        }
        override fun equals(other: Any?) = other is SessionControls && other.controller.sessionToken == controller.sessionToken
        override fun hashCode() = controller.sessionToken.hashCode()
    }

    companion object {
        /** Artwork is shown at 40 dp; this covers the densest screens without holding a full-size cover. */
        private const val ARTWORK_PX = 160
    }
}

/** How long a paused player stays: long enough to come back to, not hours later. */
const val GLANCE_PAUSED_MEDIA_MS = 30 * 60 * 1000L

/**
 * Whether a session in playback [state] shows, and as playing: true while playing or buffering, false while paused
 * for less than [GLANCE_PAUSED_MEDIA_MS] (by [lastUpdateElapsed], when its position last changed), null otherwise.
 */
fun mediaPlayingState(state: Int?, lastUpdateElapsed: Long?, nowElapsed: Long): Boolean? = when (state) {
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_FAST_FORWARDING,
    PlaybackState.STATE_REWINDING -> true
    PlaybackState.STATE_PAUSED ->
        if (lastUpdateElapsed != null && nowElapsed - lastUpdateElapsed in 0..GLANCE_PAUSED_MEDIA_MS) false else null
    else -> null
}

/** The battery from its broadcast: level, whether plugged in, and the charging status the diagnostics page shows. */
fun batteryFrom(intent: Intent): GlanceBattery? = batteryFrom(
    level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
    scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100),
    plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
    status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
    chargingStatus = intent.getIntExtra(ChargingStatus.EXTRA_CHARGING_STATUS, 0)
)

fun batteryFrom(level: Int, scale: Int, plugged: Int, status: Int, chargingStatus: Int = 0): GlanceBattery? {
    if (level < 0 || scale <= 0) return null
    val percent = (level * 100 / scale).coerceIn(0, 100)
    val chargeState = DiagnosticsReadings.chargeState(status, plugged)
    // What the charger is doing ("Charging", "Fully Charged", "Paused to Protect Battery"); the cable's USB modes,
    // which the diagnostics page adds, don't belong here.
    val label = ChargingStatus.status(plugged, chargeState, usb = null, adbEnabled = false, chargingState = chargingStatus)?.shortLabel
    return GlanceBattery(percent, pluggedIn = chargeState != DiagnosticsReadings.ChargeState.Discharging, statusLabel = label)
}

/**
 * Sends another app's [intent] (a notification's tap action, a player's activity) from the search on screen. From
 * Android 14 the sender has to say it lets the intent open an activity; the search is in front, so it may.
 */
fun sendPendingIntent(context: Context, intent: PendingIntent): Boolean = try {
    val options = when {
        Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
            .toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
        else -> null
    }
    intent.send(context, 0, null, null, null, null, options)
    true
} catch (_: PendingIntent.CanceledException) {
    false
} catch (_: RuntimeException) {
    false
}

/** Opens [packageName]'s launcher activity; false if it has none. */
fun launchApp(context: Context, packageName: String): Boolean {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
