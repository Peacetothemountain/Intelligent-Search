package com.pixel.intelligentsearch.core.glance

import android.app.Notification
import android.media.session.PlaybackState
import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.core.system.MediaSessionAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** When each At a Glance item shows, and in what order. */
@RunWith(RobolectricTestRunner::class)
class AtAGlanceTest {

    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private object NoControls : MediaControls {
        override fun play() {}
        override fun pause() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) {}
        override fun open() {}
    }

    private val timer = GlanceTimer("t", GlanceTimer.Kind.Timer, "5 min timer", now + 120_000L, "Clock", "com.google.android.deskclock", null)
    private val media = GlanceMedia("Song", "Artist", "Music", "com.example.music", isPlaying = true, artwork = null, controls = NoControls)
    private val birthday = GlanceBirthday(1, "lk", "Ana", 0, LocalDate.of(2027, 1, 15), turning = 30)
    private val everything = GlanceSnapshot(
        alarm = GlanceAlarm(now + 2 * hour),
        timers = listOf(timer),
        media = media,
        birthdays = listOf(birthday)
    )
    private val allOn = GlanceToggles(notifications = true, contacts = true)

    private fun kinds(items: List<GlanceItem>) = items.map { it::class.simpleName }

    @Test
    fun itemsShowInOrderTimersAlarmMediaBirthdays() {
        assertEquals(
            listOf("Timer", "Alarm", "Media", "Birthday"),
            kinds(glanceItems(everything, allOn, now))
        )
    }

    @Test
    fun turningAtAGlanceOffShowsNothing() {
        assertTrue(glanceItems(everything, allOn.copy(enabled = false), now).isEmpty())
        assertFalse(allOn.copy(enabled = false).anyOn)
    }

    /** The items have no switches of their own: with At a Glance on, the alarm and media need nothing else. */
    @Test
    fun theAlarmAndMediaShowWithOnlyAtAGlanceOn() {
        val onlyGlance = GlanceToggles(enabled = true, notifications = false, contacts = false)
        assertTrue(onlyGlance.anyOn)
        assertEquals(listOf("Alarm", "Media"), kinds(glanceItems(everything, onlyGlance, now)))
    }

    @Test
    fun timersNeedSearchYourNotifications() {
        val withoutOptIn = GlanceToggles(notifications = false, contacts = true)
        assertEquals(listOf("Alarm", "Media", "Birthday"), kinds(glanceItems(everything, withoutOptIn, now)))
        assertFalse(withoutOptIn.timersOn)
        assertTrue(allOn.timersOn)
        assertFalse("nothing is read with At a Glance off", allOn.copy(enabled = false).timersOn)
    }

    @Test
    fun birthdaysNeedContactSearch() {
        assertEquals(listOf("Timer", "Alarm", "Media"), kinds(glanceItems(everything, allOn.copy(contacts = false), now)))
        assertFalse(allOn.copy(contacts = false).birthdaysOn)
        assertTrue(allOn.birthdaysOn)
        assertFalse("nothing is read with At a Glance off", allOn.copy(enabled = false).birthdaysOn)
    }

    @Test
    fun theAlarmShowsOnlyInTheNextTwelveHours() {
        fun shows(at: Long) = glanceItems(GlanceSnapshot(alarm = GlanceAlarm(at)), allOn, now).isNotEmpty()
        assertTrue(shows(now + 11 * hour + 59 * 60_000L))
        assertTrue(shows(now + 12 * hour))
        assertFalse(shows(now + 12 * hour + 60_000L))
        assertFalse("an alarm already past", shows(now - 60_000L))
    }

    @Test
    fun aPausedPlayerStaysForHalfAnHour() {
        assertEquals(true, MediaSessionAccess.playingState(PlaybackState.STATE_PLAYING, null, 0L))
        assertEquals(true, MediaSessionAccess.playingState(PlaybackState.STATE_BUFFERING, null, 0L))
        assertEquals(false, MediaSessionAccess.playingState(PlaybackState.STATE_PAUSED, 1_000L, 1_000L + MediaSessionAccess.PAUSED_MEDIA_MS))
        assertNull(MediaSessionAccess.playingState(PlaybackState.STATE_PAUSED, 1_000L, 1_001L + MediaSessionAccess.PAUSED_MEDIA_MS))
        assertNull(MediaSessionAccess.playingState(PlaybackState.STATE_STOPPED, 1_000L, 1_000L))
        assertNull(MediaSessionAccess.playingState(null, null, 0L))
    }

    /** At a Glance and the widget's media row pick the session to show by one rule. */
    @Test
    fun theSessionShownIsThePlayingOneElseOneRecentlyPaused() {
        val now = 10_000_000L
        val pausedRecently = MediaSessionAccess.SessionState("Podcast", PlaybackState.STATE_PAUSED, now - 60_000)
        val pausedLongAgo = MediaSessionAccess.SessionState("Old Song", PlaybackState.STATE_PAUSED, now - 2 * MediaSessionAccess.PAUSED_MEDIA_MS)
        val playing = MediaSessionAccess.SessionState("Song", PlaybackState.STATE_PLAYING, now)
        val untitled = MediaSessionAccess.SessionState(null, PlaybackState.STATE_PLAYING, now)
        assertEquals(2, MediaSessionAccess.pickIndex(listOf(pausedRecently, untitled, playing), now))
        assertEquals(1, MediaSessionAccess.pickIndex(listOf(pausedLongAgo, pausedRecently), now))
        assertNull(MediaSessionAccess.pickIndex(listOf(pausedLongAgo, untitled), now))
        assertNull(MediaSessionAccess.pickIndex(emptyList(), now))
    }

    private fun clockEntry(key: String, countDown: Boolean, category: String?, base: Long) = IndexedNotification(
        key, "com.google.android.deskclock", "Clock", "Timer", "", 0L, category = category,
        chronometer = IndexedNotification.Chronometer(countDown, base)
    )

    @Test
    fun timersComeFromTheClocksChronometers() {
        val timers = timersFrom(
            listOf(
                clockEntry("sw", countDown = false, category = Notification.CATEGORY_STOPWATCH, base = 10L),
                clockEntry("late", countDown = true, category = null, base = 900L),
                clockEntry("soon", countDown = true, category = null, base = 500L),
                clockEntry("up", countDown = false, category = Notification.CATEGORY_ALARM, base = 1L),
                IndexedNotification("plain", "com.google.android.deskclock", "Clock", "Bedtime", "", 0L)
            )
        )
        assertEquals(listOf("up", "soon", "late", "sw"), timers.map { it.key })
        assertEquals(
            listOf(GlanceTimer.Kind.TimesUp, GlanceTimer.Kind.Timer, GlanceTimer.Kind.Timer, GlanceTimer.Kind.Stopwatch),
            timers.map { it.kind }
        )
    }

    @Test
    fun timerReadings() {
        assertEquals("2:00 Left", timerReading(timer, now))
        assertEquals("1:59 Left", timerReading(timer, now + 1_000L))
        assertEquals("Time's Up", timerReading(timer, now + 120_000L))
        assertEquals("1:02:03", timerReading(timer.copy(kind = GlanceTimer.Kind.Stopwatch, baseTime = now - 3_723_000L), now))
        assertEquals("0:05", Durations.clock(5_400L))
        assertEquals("12:30", Durations.clock(750_000L))
    }

    @Test
    fun alarmDistances() {
        assertEquals("In 1 min", Durations.until(10_000L))
        assertEquals("In 45 min", Durations.until(45 * 60_000L))
        assertEquals("In 2 hr", Durations.until(2 * hour))
        assertEquals("In 6 hr 20 min", Durations.until(6 * hour + 20 * 60_000L))
    }
}
