package com.pixel.intelligentsearch.core.data.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The countdown widget's maths: whole days on the phone's calendar, yearly events rolling round (a leap day too),
 * one-off dates counting up once passed, and the next redraw at local midnight whatever the time zone or daylight
 * saving does that night.
 */
@RunWith(RobolectricTestRunner::class)
class CountdownEventsTest {

    private val halloween = CountdownEvent("Halloween", LocalDate.of(2026, 10, 31), yearly = true)

    @Test
    fun daysAreCountedOnTheCalendar() {
        assertEquals(9, halloween.daysUntil(LocalDate.of(2026, 10, 22)))
        assertEquals(1, halloween.daysUntil(LocalDate.of(2026, 10, 30)))
        assertEquals(0, halloween.daysUntil(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun aYearlyEventCountsToItsNextOneOnceItHasPassed() {
        assertEquals(LocalDate.of(2027, 10, 31), halloween.target(LocalDate.of(2026, 11, 1)))
        assertEquals(364, halloween.daysUntil(LocalDate.of(2026, 11, 1)))
        // Set years ago, it still counts to this year's.
        val old = halloween.copy(date = LocalDate.of(2019, 10, 31))
        assertEquals(LocalDate.of(2026, 10, 31), old.target(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun aYearlyLeapDayFallsOnTheTwentyEighthInOtherYears() {
        val leap = CountdownEvent("Leap Day", LocalDate.of(2024, 2, 29), yearly = true)
        assertEquals(LocalDate.of(2027, 2, 28), leap.target(LocalDate.of(2027, 1, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), leap.target(LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun aOneOffDateCountsTheDaysSinceOnceItHasPassed() {
        val launch = CountdownEvent("Launch", LocalDate.of(2026, 10, 1), yearly = false)
        assertEquals(-21, launch.daysUntil(LocalDate.of(2026, 10, 22)))
        assertEquals(LocalDate.of(2026, 10, 1), launch.target(LocalDate.of(2027, 5, 5)))
    }

    @Test
    fun daylightSavingChangesNeverSkipOrRepeatADay() {
        // The night the clocks go back in New York (25 hours long) and forward in London (23 hours).
        for (zone in listOf("America/New_York", "Europe/London", "Australia/Lord_Howe", "America/Santiago")) {
            var now = ZonedDateTime.parse("2026-10-20T12:00Z").withZoneSameInstant(java.time.ZoneId.of(zone))
            val dates = mutableListOf<LocalDate>()
            repeat(20) {
                dates += now.toLocalDate()
                now = CountdownEvents.nextMidnight(now).plusSeconds(30)
            }
            dates.zipWithNext().forEach { (day, next) -> assertEquals(zone, day.plusDays(1), next) }
        }
        val newYork = ZonedDateTime.parse("2026-10-29T08:00-04:00[America/New_York]")
        val counts = generateSequence(newYork) { CountdownEvents.nextMidnight(it).plusSeconds(30) }
            .take(6).map { halloween.daysUntil(it.toLocalDate()) }.toList()
        assertEquals(listOf(2L, 1L, 0L, 364L, 363L, 362L), counts)
    }

    @Test
    fun theNextRedrawIsLocalMidnight() {
        val beforeFallBack = ZonedDateTime.parse("2026-11-01T00:30-04:00[America/New_York]")
        assertEquals(ZonedDateTime.parse("2026-11-02T00:00-05:00[America/New_York]"), CountdownEvents.nextMidnight(beforeFallBack))
        assertEquals(Duration.ofHours(24).plusMinutes(30), Duration.between(beforeFallBack, CountdownEvents.nextMidnight(beforeFallBack)))

        // Where daylight saving starts at midnight (Santiago, September 2026), the day starts at 1am instead.
        val santiago = ZonedDateTime.parse("2026-09-05T22:00-04:00[America/Santiago]")
        assertEquals(LocalDate.of(2026, 9, 6), CountdownEvents.nextMidnight(santiago).toLocalDate())

        // Across the date line, midnight is the phone's own.
        val tokyo = ZonedDateTime.parse("2026-10-30T23:00+09:00[Asia/Tokyo]")
        assertEquals(ZonedDateTime.parse("2026-10-31T00:00+09:00[Asia/Tokyo]"), CountdownEvents.nextMidnight(tokyo))
    }

    @Test
    fun eventsAreStoredPerWidgetInTheBackedUpSetting() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("countdown_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val events = CountdownEvents(prefs)
        assertNull(events.get(7))

        events.put(7, halloween)
        events.put(9, CountdownEvent("  Trip to Lisbon  ", LocalDate.of(2027, 3, 14), yearly = false))
        assertEquals(halloween, events.get(7))
        assertEquals("Trip to Lisbon", events.get(9)?.title)
        assertEquals(true, prefs[SettingsManager.COUNTDOWN_EVENTS].contains("2026-10-31"))

        events.remove(intArrayOf(7))
        assertNull(events.get(7))
        assertEquals(LocalDate.of(2027, 3, 14), events.get(9)?.date)
    }

    @Test
    fun anUnreadableSettingReadsAsNoEvents() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("countdown_test_bad", Context.MODE_PRIVATE)
        prefs.edit().putString(SettingsManager.COUNTDOWN_EVENTS.name, "{\"3\":{\"title\":\"X\",\"date\":\"not a date\"},\"x\":1, \"4\":{\"title\":\"\",\"date\":\"2026-01-01\"}}").commit()
        assertNull(CountdownEvents(prefs).get(3))
        assertNull(CountdownEvents(prefs).get(4))
        prefs.edit().putString(SettingsManager.COUNTDOWN_EVENTS.name, "not json").commit()
        assertNull(CountdownEvents(prefs).get(3))
    }
}
