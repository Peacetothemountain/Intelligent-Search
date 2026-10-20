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

    private fun on(date: String, zone: ZoneId = newYork, region: String = "") = OccasionCalendar.on(LocalDate.parse(date), zone, region)

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
    fun funDaysAreTheSameEverywhere() {
        for (region in listOf("US", "GB", "JP", "")) {
            assertEquals(Occasion.WOMENS_DAY, on("2027-03-08", region = region))
            assertEquals(Occasion.PI_DAY, on("2027-03-14", region = region))
            assertEquals(Occasion.ST_PATRICKS_DAY, on("2027-03-17", region = region))
            assertEquals(Occasion.APRIL_FOOLS_DAY, on("2027-04-01", region = region))
            assertEquals(Occasion.BEE_DAY, on("2027-05-20", region = region))
            assertEquals(Occasion.OCEANS_DAY, on("2027-06-08", region = region))
            assertEquals(Occasion.CHOCOLATE_DAY, on("2027-07-07", region = region))
            assertEquals(Occasion.EMOJI_DAY, on("2027-07-17", region = region))
            assertEquals(Occasion.MOON_DAY, on("2027-07-20", region = region))
            assertEquals(Occasion.CAT_DAY, on("2027-08-08", region = region))
            assertEquals(Occasion.DOG_DAY, on("2027-08-26", region = region))
            assertEquals(Occasion.PIRATE_DAY, on("2027-09-19", region = region))
            assertEquals(Occasion.COFFEE_DAY, on("2027-10-01", region = region))
            assertEquals(Occasion.TEACHERS_DAY, on("2027-10-05", region = region))
            assertEquals(Occasion.KINDNESS_DAY, on("2027-11-13", region = region))
        }
    }

    @Test
    fun easterFollowsTheComputus() {
        mapOf(2026 to "2026-04-05", 2027 to "2027-03-28", 2028 to "2028-04-16", 2029 to "2029-04-01", 2030 to "2030-04-21",
            2038 to "2038-04-25", 2285 to "2285-03-22")
            .forEach { (year, date) -> assertEquals(LocalDate.parse(date), OccasionCalendar.easter(year)) }
        assertEquals(Occasion.EASTER, on("2026-04-05"))
        // Easter on April 1 2029 wins over April Fools' Day.
        assertEquals(Occasion.EASTER, on("2029-04-01"))
    }

    @Test
    fun lunarNewYearComesFromItsTable() {
        assertEquals(Occasion.LUNAR_NEW_YEAR, on("2027-02-06"))
        assertEquals(Occasion.LUNAR_NEW_YEAR, on("2028-01-26"))
        assertNull(on("2027-02-07"))
    }

    @Test
    fun regionalDaysOnlyWhereTheyreCelebrated() {
        assertEquals(Occasion.INDEPENDENCE_DAY, on("2027-07-04", region = "US"))
        assertNull(on("2027-07-04", region = "GB"))
        assertEquals(Occasion.CANADA_DAY, on("2027-07-01", region = "CA"))
        assertNull(on("2027-07-01", region = "US"))
        assertEquals(Occasion.GROUNDHOG_DAY, on("2027-02-02", region = "US"))
        assertEquals(Occasion.GROUNDHOG_DAY, on("2027-02-02", region = "CA"))
        assertNull(on("2027-02-02", region = "FR"))
    }

    @Test
    fun familyAndHarvestDaysFollowTheRegionsCalendar() {
        // Thanksgiving: the fourth Thursday of November in the US, the second Monday of October in Canada.
        assertEquals(Occasion.THANKSGIVING, on("2026-11-26", region = "US"))
        assertEquals(Occasion.THANKSGIVING, on("2026-10-12", region = "CA"))
        assertNull(on("2026-11-26", region = "CA"))
        // Mother's Day: the second Sunday of May, or Mothering Sunday (three weeks before Easter) in the UK.
        assertEquals(Occasion.MOTHERS_DAY, on("2026-05-10", region = "US"))
        assertEquals(Occasion.MOTHERS_DAY, on("2026-03-15", region = "GB"))
        assertNull(on("2026-05-10", region = "GB"))
        assertNull(on("2026-05-10", region = "FR")) // France keeps it on another day
        // Father's Day: the third Sunday of June, or the first Sunday of September in Australia. In 2026 it's also
        // the June solstice; Father's Day wins.
        assertEquals(Occasion.FATHERS_DAY, on("2026-06-21", region = "US"))
        assertEquals(Occasion.FATHERS_DAY, on("2026-09-06", zone = sydney, region = "AU"))
        assertEquals(Occasion.FIRST_DAY_OF_SUMMER, on("2026-06-21", region = "XX"))
    }

    @Test
    fun everyDoodleHasItsOwnArt() {
        assertEquals(Occasion.entries.size, Occasion.entries.map { it.layout }.toSet().size)
    }
}
