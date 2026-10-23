package com.pixel.intelligentsearch.core.glance

import com.pixel.intelligentsearch.core.util.Durations
import android.app.Notification
import android.app.PendingIntent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.pixel.intelligentsearch.core.system.MediaSessionAccess

/** The next alarm set in the clock app, ringing at [triggerAt] (wall clock). */
@Immutable
data class GlanceAlarm(val triggerAt: Long)

/** A clock app's running timer or stopwatch, read from its notification. */
@Immutable
data class GlanceTimer(
    val key: String,
    val kind: Kind,
    val label: String,
    /** The timer's end, or the stopwatch's start (wall clock). */
    val baseTime: Long,
    val appLabel: String,
    val packageName: String,
    val contentIntent: PendingIntent?
) {
    enum class Kind { Timer, TimesUp, Stopwatch }
}

/** What a media session is playing, and its controls. */
@Immutable
data class GlanceMedia(
    val title: String,
    val artist: String,
    val appLabel: String,
    val packageName: String,
    val isPlaying: Boolean,
    val artwork: ImageBitmap?,
    val controls: MediaControls,
    /** The track's length and position, for the seek bar; null when the session has no length (a live stream). */
    val progress: MediaSessionAccess.Progress? = null
)

/** Play, pause, skip and seek for a [GlanceMedia]; equal for the same session, so a fresh snapshot doesn't redraw the card. */
interface MediaControls {
    fun play()
    fun pause()
    fun next()
    fun previous()
    /** Moves to [positionMs] into the track, when the player takes a seek. */
    fun seekTo(positionMs: Long)
    /** Opens the player: the session's own activity, or the app. */
    fun open()
}

/** Everything At a Glance may show, as last read; [glanceItems] decides what does show. */
@Immutable
data class GlanceSnapshot(
    val alarm: GlanceAlarm? = null,
    val timers: List<GlanceTimer> = emptyList(),
    val media: GlanceMedia? = null,
    val birthdays: List<GlanceBirthday> = emptyList()
) {
    companion object {
        val EMPTY = GlanceSnapshot()
    }
}

/**
 * What decides which At a Glance items can show: its own switch in Search Behavior, and the sources the items read
 * from. Items have no switches of their own: the next alarm always shows, timers need Search Your Notifications,
 * birthdays contact search, and media notification access (checked where the sessions are read).
 */
@Immutable
data class GlanceToggles(
    val enabled: Boolean = true,
    /** Search Sources -> Notifications (Search Your Notifications): timers are read from the clock's notifications. */
    val notifications: Boolean = false,
    /** Search Sources -> Contacts: birthdays come from the contacts, so they show only while contact search is on. */
    val contacts: Boolean = false
) {
    /** Whether timers are read: Search Your Notifications on. */
    val timersOn: Boolean get() = enabled && notifications

    /** Whether birthdays are read: contact search on. */
    val birthdaysOn: Boolean get() = enabled && contacts

    /** Whether anything at all can show (the alarm always can), so nothing is read while the section is off. */
    val anyOn: Boolean get() = enabled
}

/** One card in At a Glance; [key] keeps each in place in the list. */
sealed interface GlanceItem {
    val key: String

    data class Timer(val timer: GlanceTimer) : GlanceItem {
        override val key get() = "glance_timer_${timer.key}"
    }
    data class Alarm(val alarm: GlanceAlarm) : GlanceItem {
        override val key get() = "glance_alarm"
    }
    data class Media(val media: GlanceMedia) : GlanceItem {
        override val key get() = "glance_media_${media.packageName}"
    }
    data class Birthday(val birthday: GlanceBirthday) : GlanceItem {
        override val key get() = "glance_birthday_${birthday.contactId}"
    }
}

/** How far ahead the next alarm shows: half a day, as the Pixel's own At a Glance does. */
const val GLANCE_ALARM_WINDOW_MS = 12 * 60 * 60 * 1000L
const val GLANCE_MAX_TIMERS = 3

/**
 * What At a Glance shows at [now], in order: running timers and stopwatches, the next alarm within
 * [GLANCE_ALARM_WINDOW_MS], what is playing and birthdays this week. Timers need Search Your Notifications and
 * birthdays contact search ([GlanceToggles]).
 */
fun glanceItems(snapshot: GlanceSnapshot, toggles: GlanceToggles, now: Long): List<GlanceItem> {
    if (!toggles.enabled) return emptyList()
    return buildList {
        if (toggles.timersOn) snapshot.timers.take(GLANCE_MAX_TIMERS).forEach { add(GlanceItem.Timer(it)) }
        snapshot.alarm?.takeIf { it.triggerAt - now in 0..GLANCE_ALARM_WINDOW_MS }?.let { add(GlanceItem.Alarm(it)) }
        snapshot.media?.let { add(GlanceItem.Media(it)) }
        if (toggles.birthdaysOn) snapshot.birthdays.forEach { add(GlanceItem.Birthday(it)) }
    }
}

/** The running timers and stopwatches among the indexed notifications, soonest-ending timers first. */
fun timersFrom(entries: List<IndexedNotification>): List<GlanceTimer> = entries.mapNotNull { entry ->
    val chronometer = entry.chronometer ?: return@mapNotNull null
    val kind = when {
        chronometer.countDown -> GlanceTimer.Kind.Timer
        entry.category == Notification.CATEGORY_STOPWATCH -> GlanceTimer.Kind.Stopwatch
        // A clock's alarm-category notification counting up is a timer that has run out.
        entry.category == Notification.CATEGORY_ALARM -> GlanceTimer.Kind.TimesUp
        else -> GlanceTimer.Kind.Stopwatch
    }
    GlanceTimer(entry.key, kind, entry.title, chronometer.baseTime, entry.appLabel, entry.packageName, entry.contentIntent)
}.sortedWith(compareBy<GlanceTimer> { it.kind != GlanceTimer.Kind.TimesUp }.thenBy { it.kind }.thenBy { it.baseTime })

/** What a timer card reads at [now]: the time left, "Time's Up", or a stopwatch's time so far. */
fun timerReading(timer: GlanceTimer, now: Long): String = when (timer.kind) {
    GlanceTimer.Kind.Timer -> if (timer.baseTime > now) "${Durations.clock(timer.baseTime - now + 999)} Left" else "Time's Up"
    GlanceTimer.Kind.TimesUp -> "Time's Up"
    GlanceTimer.Kind.Stopwatch -> Durations.clock(now - timer.baseTime)
}


