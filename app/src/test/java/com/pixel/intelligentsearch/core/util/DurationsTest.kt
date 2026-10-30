package com.pixel.intelligentsearch.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * One way of writing spans of time and times of day, everywhere the app shows one. Run on Robolectric, so a day is
 * written with the phone's own date patterns and words.
 */
@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun whenAnActionIsSetSaysItsDayAndTime() {
        val zone = java.time.ZoneId.of("America/Los_Angeles")
        fun at(month: Int, day: Int, hour: Int, minute: Int = 0, year: Int = 2026) =
            java.time.LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
        val now = at(10, 29, 22)
        assertEquals("Today, 11:30 PM", Durations.dayAndTime(at(10, 29, 23, 30), now, is24Hour = false, Locale.US, zone))
        assertEquals("Tomorrow, 7:00 AM", Durations.dayAndTime(at(10, 30, 7), now, is24Hour = false, Locale.US, zone))
        assertEquals("Fri, Nov 13, 2:00 PM", Durations.dayAndTime(at(11, 13, 14), now, is24Hour = false, Locale.US, zone))
        assertEquals("Fri, Jan 1, 2027, 09:00", Durations.dayAndTime(at(1, 1, 9, year = 2027), now, is24Hour = true, Locale.US, zone))
    }

    @Test
    fun theDayIsWrittenAsThePhonesRegionWritesIt() {
        val zone = java.time.ZoneId.of("Europe/Berlin")
        fun at(month: Int, day: Int, hour: Int, minute: Int = 0, year: Int = 2026) =
            java.time.LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
        val now = at(10, 29, 10)
        // Every region had "Fri, Nov 13", and English "Today" and "Tomorrow".
        assertEquals("Heute, 23:30", Durations.dayAndTime(at(10, 29, 23, 30), now, is24Hour = true, Locale.GERMANY, zone))
        assertEquals("Morgen, 07:00", Durations.dayAndTime(at(10, 30, 7), now, is24Hour = true, Locale.GERMANY, zone))
        assertEquals("Fr., 13. Nov., 14:00", Durations.dayAndTime(at(11, 13, 14), now, is24Hour = true, Locale.GERMANY, zone))
        assertEquals("Fri, 13 Nov, 14:00", Durations.dayAndTime(at(11, 13, 14), now, is24Hour = true, Locale.UK, zone))
        assertEquals("Fri, 13 Nov, 2:00 pm", Durations.dayAndTime(at(11, 13, 14), now, is24Hour = false, Locale.UK, zone))
    }

    @Test
    fun aClockTheLanguageDoesntUseByDefaultIsWrittenItsWay() {
        // Japanese puts the period first; the English order wrote "11:30 午後".
        assertEquals("午後11:30", Durations.timeOfDay(23, 30, is24Hour = false, Locale.JAPAN))
        assertEquals("午前7:05", Durations.timeOfDay(7, 5, is24Hour = false, Locale.JAPAN))
        assertEquals("11:30 PM", Durations.timeOfDay(23, 30, is24Hour = false, Locale.GERMANY))
    }
}
