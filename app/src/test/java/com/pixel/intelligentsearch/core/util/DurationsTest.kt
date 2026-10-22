package com.pixel.intelligentsearch.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** One way of writing spans of time and times of day, everywhere the app shows one. */
class DurationsTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun spansNameEachUnitThatIsntZero() {
        assertEquals("15 min", Durations.short(15 * 60))
        assertEquals("1 hr 30 min", Durations.short(90 * 60))
        assertEquals("1 min 30 sec", Durations.short(90))
        assertEquals("45 sec", Durations.short(45))
        assertEquals("2 hr", Durations.short(2 * 3600))
        assertEquals("1 hr 5 sec", Durations.short(3605))
    }

    @Test
    fun screenTimeCountsWholeMinutes() {
        assertEquals("3 hr 12 min", Durations.minutes(192 * minute))
        assertEquals("2 hr", Durations.minutes(120 * minute + 20_000))
        assertEquals("45 min", Durations.minutes(45 * minute))
        assertEquals("Under 1 min", Durations.minutes(30_000))
    }

    @Test
    fun agoAndUntil() {
        assertEquals("Now", Durations.ago(30_000))
        assertEquals("5 min ago", Durations.ago(5 * minute))
        assertEquals("2 hr ago", Durations.ago(2 * hour + 10 * minute))
        assertEquals("Yesterday", Durations.ago(30 * hour))
        assertEquals("3 days ago", Durations.ago(3 * 24 * hour))
        assertEquals("In 1 min", Durations.until(10_000))
        assertEquals("In 6 hr 20 min", Durations.until(6 * hour + 20 * minute))
    }

    @Test
    fun aStopwatchReading() {
        assertEquals("0:05", Durations.clock(5_400, Locale.US))
        assertEquals("1:02:03", Durations.clock(3_723_000, Locale.US))
    }

    @Test
    fun timesOfDayFollowThePhonesClockAndLanguage() {
        assertEquals("7:30 AM", Durations.timeOfDay(7, 30, is24Hour = false, Locale.US))
        assertEquals("7:05 PM", Durations.timeOfDay(19, 5, is24Hour = false, Locale.US))
        assertEquals("12:00 AM", Durations.timeOfDay(0, 0, is24Hour = false, Locale.US))
        assertEquals("07:30", Durations.timeOfDay(7, 30, is24Hour = true, Locale.US))
        // A 24-hour language keeps its own pattern on a 24-hour clock.
        assertEquals("19:30", Durations.timeOfDay(19, 30, is24Hour = true, Locale.GERMANY))
    }
}
