package com.pixel.intelligentsearch.core.occasion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OccasionCalendarTest {

    private val newYork = ZoneId.of("America/New_York")
    private val london = ZoneId.of("Europe/London")
    private val sydney = ZoneId.of("Australia/Sydney")

    private fun on(date: String, zone: ZoneId = newYork) = OccasionCalendar.on(LocalDate.parse(date), zone)

    @Test
    fun fixedDaysAreTheSameEverywhere() {
        for (zone in listOf(newYork, london, sydney)) {
            assertEquals(Occasion.NEW_YEARS_DAY, on("2027-01-01", zone))
            assertEquals(Occasion.VALENTINES_DAY, on("2027-02-14", zone))
            assertEquals(Occasion.EARTH_DAY, on("2027-04-22", zone))
            assertEquals(Occasion.HALLOWEEN, on("2026-10-31", zone))
            assertEquals(Occasion.HOLIDAYS, on("2026-12-24", zone))
            assertEquals(Occasion.HOLIDAYS, on("2026-12-25", zone))
            assertEquals(Occasion.NEW_YEARS_EVE, on("2026-12-31", zone))
        }
    }

    @Test
    fun leapDayOnlyInLeapYears() {
        assertEquals(Occasion.LEAP_DAY, on("2028-02-29"))
        assertNull(on("2027-02-28"))
        assertNull(on("2027-03-01"))
    }

    @Test
    fun ordinaryDaysHaveNoDoodle() {
        assertNull(on("2026-10-20"))
        assertNull(on("2026-03-03")) // the date unit tests pin (app/build.gradle.kts)
        assertNull(on("2026-12-26"))
        assertNull(on("2026-03-19"))
    }

    /** Published times (UTC) of the 2024 to 2026 equinoxes and solstices; the formula is good to a few minutes. */
    @Test
    fun seasonsStartWhenTheSunSaysSo() {
        val published = mapOf(
            (2024 to 3) to "2024-12-21T09:20:00Z",
            (2025 to 0) to "2025-03-20T09:01:00Z",
            (2025 to 1) to "2025-06-21T02:42:00Z",
            (2025 to 2) to "2025-09-22T18:19:00Z",
            (2025 to 3) to "2025-12-21T15:03:00Z",
            (2026 to 0) to "2026-03-20T14:46:00Z",
            (2026 to 1) to "2026-06-21T08:24:00Z",
            (2026 to 2) to "2026-09-23T00:05:00Z",
            (2026 to 3) to "2026-12-21T20:50:00Z"
        )
        for ((key, time) in published) {
            val (year, event) = key
            val error = Duration.between(Instant.parse(time), OccasionCalendar.seasonStart(year, event)).abs()
            assertTrue("$year event $event is off by $error", error <= Duration.ofMinutes(5))
        }
    }

    @Test
    fun theFirstDayOfASeasonIsTheLocalDateOfItsStart() {
        assertEquals(Occasion.FIRST_DAY_OF_SPRING, on("2026-03-20"))
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2026-06-21"))
        assertEquals(Occasion.FIRST_DAY_OF_WINTER, on("2026-12-21"))
        // The September 2026 equinox is just after midnight UTC: still the 22nd in New York, the 23rd in London.
        assertEquals(Occasion.FIRST_DAY_OF_AUTUMN, on("2026-09-22", newYork))
        assertNull(on("2026-09-23", newYork))
        assertEquals(Occasion.FIRST_DAY_OF_AUTUMN, on("2026-09-23", london))
        assertNull(on("2026-09-22", london))
    }

    @Test
    fun theSouthernHemisphereHasTheOtherSeason() {
        assertEquals(Occasion.FIRST_DAY_OF_AUTUMN, on("2026-03-21", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_WINTER, on("2026-06-21", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_SPRING, on("2026-09-23", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2026-12-22", sydney))
        assertEquals(Occasion.FIRST_DAY_OF_SPRING, on("2026-09-22", ZoneId.of("America/Argentina/Buenos_Aires")))
    }

    @Test
    fun hemisphereFollowsTheTimeZone() {
        assertTrue(OccasionCalendar.isSouthern(ZoneId.of("Australia/Perth")))
        assertTrue(OccasionCalendar.isSouthern(ZoneId.of("Pacific/Auckland")))
        assertTrue(OccasionCalendar.isSouthern(ZoneId.of("America/Sao_Paulo")))
        assertTrue(OccasionCalendar.isSouthern(ZoneId.of("Africa/Johannesburg")))
        assertFalse(OccasionCalendar.isSouthern(newYork))
        assertFalse(OccasionCalendar.isSouthern(london))
        assertFalse(OccasionCalendar.isSouthern(ZoneId.of("Asia/Tokyo")))
        assertFalse(OccasionCalendar.isSouthern(ZoneId.of("UTC")))
    }

    @Test
    fun theDoodleNextChangesAtTheMidnightADoodleDayStartsOrEnds() {
        fun next(at: String) = OccasionCalendar.nextChange(LocalDate.parse(at).atTime(15, 30).atZone(newYork)).toString()
        assertEquals("2026-10-31T00:00-04:00[America/New_York]", next("2026-10-20"))
        assertEquals("2026-11-01T00:00-04:00[America/New_York]", next("2026-10-31"))
        // Christmas Eve and Day share a doodle, so it doesn't change in between.
        assertEquals("2026-12-26T00:00-05:00[America/New_York]", next("2026-12-24"))
        assertEquals("2027-01-02T00:00-05:00[America/New_York]", next("2027-01-01"))
    }

    @Test
    fun everyDoodleHasItsOwnArt() {
        assertEquals(Occasion.entries.size, Occasion.entries.map { it.layout }.toSet().size)
    }
}
