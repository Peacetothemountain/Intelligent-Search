package com.pixel.intelligentsearch.core.glance

import android.app.Notification
import android.media.session.PlaybackState
import android.os.BatteryManager
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
        override fun open() {}
    }

    private val timer = GlanceTimer("t", GlanceTimer.Kind.Timer, "5 min timer", now + 120_000L, "Clock", "com.google.android.deskclock", null)
    private val media = GlanceMedia("Song", "Artist", "Music", "com.example.music", isPlaying = true, artwork = null, controls = NoControls)
    private val birthday = GlanceBirthday(1, "lk", "Ana", 0, LocalDate.of(2027, 1, 15), turning = 30)
    private val everything = GlanceSnapshot(
        alarm = GlanceAlarm(now + 2 * hour),
        timers = listOf(timer),
        media = media,
        birthdays = listOf(birthday),
        battery = GlanceBattery(15, pluggedIn = false, statusLabel = null)
    )
    private val allOn = GlanceToggles(notifications = true, contacts = true)

    private fun kinds(items: List<GlanceItem>) = items.map { it::class.simpleName }

    @Test
    fun itemsShowInOrderTimersAlarmMediaBirthdaysBattery() {
        assertEquals(
            listOf("Timer", "Alarm", "Media", "Birthday", "Battery"),
            kinds(glanceItems(everything, allOn, now))
        )
    }

    @Test
    fun turningAtAGlanceOffShowsNothing() {
        assertTrue(glanceItems(everything, allOn.copy(enabled = false), now).isEmpty())
        assertFalse(allOn.copy(enabled = false).anyOn)
    }

    @Test
    fun eachSwitchHidesItsOwnItem() {
        assertEquals(listOf("Alarm", "Media", "Birthday", "Battery"), kinds(glanceItems(everything, allOn.copy(timers = false), now)))
        assertEquals(listOf("Timer", "Media", "Birthday", "Battery"), kinds(glanceItems(everything, allOn.copy(alarm = false), now)))
        assertEquals(listOf("Timer", "Alarm", "Birthday", "Battery"), kinds(glanceItems(everything, allOn.copy(media = false), now)))
        assertEquals(listOf("Timer", "Alarm", "Media", "Battery"), kinds(glanceItems(everything, allOn.copy(birthdays = false), now)))
        assertEquals(listOf("Timer", "Alarm", "Media", "Birthday"), kinds(glanceItems(everything, allOn.copy(battery = false), now)))
    }

    @Test
    fun timersNeedSearchYourNotifications() {
        val withoutOptIn = GlanceToggles(notifications = false, contacts = true)
        assertEquals(listOf("Alarm", "Media", "Birthday", "Battery"), kinds(glanceItems(everything, withoutOptIn, now)))
        // With only notification items switched on and no opt-in, there is nothing to read at all.
        assertFalse(GlanceToggles(alarm = false, media = false, birthdays = false, battery = false, notifications = false).anyOn)
        assertTrue(GlanceToggles(alarm = false, media = false, birthdays = false, battery = false, notifications = true).anyOn)
    }

    @Test
    fun birthdaysNeedContactSearch() {
        assertEquals(listOf("Timer", "Alarm", "Media", "Battery"), kinds(glanceItems(everything, allOn.copy(contacts = false), now)))
        // With only birthdays switched on and contact search off, nothing is read at all.
        assertFalse(GlanceToggles(alarm = false, media = false, battery = false, birthdays = true, contacts = false).anyOn)
        assertTrue(GlanceToggles(alarm = false, media = false, battery = false, birthdays = true, contacts = true).anyOn)
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
    fun theBatteryShowsWhenLowOrPluggedIn() {
        fun shows(battery: GlanceBattery) = glanceItems(GlanceSnapshot(battery = battery), allOn, now).isNotEmpty()
        assertTrue(shows(GlanceBattery(20, pluggedIn = false, statusLabel = null)))
        assertFalse(shows(GlanceBattery(21, pluggedIn = false, statusLabel = null)))
        assertTrue(shows(GlanceBattery(80, pluggedIn = true, statusLabel = "Charging")))
    }

    @Test
    fun theBatteryReadsTheChargingStatus() {
        val charging = batteryFrom(level = 64, scale = 100, plugged = BatteryManager.BATTERY_PLUGGED_AC, status = BatteryManager.BATTERY_STATUS_CHARGING)
        assertEquals(GlanceBattery(64, pluggedIn = true, statusLabel = "Charging"), charging)
        val full = batteryFrom(level = 100, scale = 100, plugged = BatteryManager.BATTERY_PLUGGED_USB, status = BatteryManager.BATTERY_STATUS_FULL)
        assertEquals("Fully Charged", full?.statusLabel)
        val protecting = batteryFrom(80, 100, BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_STATUS_NOT_CHARGING, chargingStatus = 4)
        assertEquals("Paused to Protect Battery", protecting?.statusLabel)
        val onBattery = batteryFrom(level = 12, scale = 100, plugged = 0, status = BatteryManager.BATTERY_STATUS_DISCHARGING)
        assertEquals(GlanceBattery(12, pluggedIn = false, statusLabel = null), onBattery)
        assertNull(batteryFrom(level = -1, scale = 100, plugged = 0, status = 0))
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
