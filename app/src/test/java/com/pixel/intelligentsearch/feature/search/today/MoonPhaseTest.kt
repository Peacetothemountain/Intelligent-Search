package com.pixel.intelligentsearch.feature.search.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** The moon phase under Discover's weather: true phase times to within minutes, and the day worded as an almanac words it. */
class MoonPhaseTest {

    @Test
    fun principalPhasesFallWithinMinutesOfTheAlmanac() {
        // k counts lunations from January 2000's new moon; the times are UTC (1977's is Meeus's worked example, in TT).
        mapOf(
            -283.0 to "1977-02-18T03:37:42Z",
            297.0 to "2024-01-11T11:57:00Z",
            296.75 to "2024-01-04T03:30:00Z",
            297.25 to "2024-01-18T03:53:00Z",
            297.5 to "2024-01-25T17:54:00Z",
            300.0 to "2024-04-08T18:21:00Z",
            309.5 to "2025-01-13T22:27:00Z"
        ).forEach { (k, expected) ->
            val off = abs(principalPhaseMillis(k) - Instant.parse(expected).toEpochMilli())
            assertTrue("k=$k is ${off / 1000} s off", off < 3 * 60_000L)
        }
    }

    @Test
    fun theDayIsWordedByWhatFallsOnIt() {
        val newYork = ZoneId.of("America/New_York")
        mapOf(
            LocalDate.of(2024, 1, 3) to "Last quarter",
            LocalDate.of(2024, 1, 8) to "Waning crescent",
            LocalDate.of(2024, 1, 11) to "New moon",
            LocalDate.of(2024, 1, 14) to "Waxing crescent",
            LocalDate.of(2024, 1, 17) to "First quarter",
            LocalDate.of(2024, 1, 21) to "Waxing gibbous",
            LocalDate.of(2024, 1, 25) to "Full moon",
            LocalDate.of(2024, 1, 29) to "Waning gibbous"
        ).forEach { (date, name) -> assertEquals("$date", name, moonPhase(date, newYork).name) }
    }

    @Test
    fun aPhaseNearMidnightFallsOnEachPlacesOwnDay() {
        // Full moon at 17:54 UTC on 25 January 2024 is 02:54 on the 26th in Tokyo.
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals("Waxing gibbous", moonPhase(LocalDate.of(2024, 1, 25), tokyo).name)
        assertEquals("Full moon", moonPhase(LocalDate.of(2024, 1, 26), tokyo).name)
    }

    @Test
    fun theAgeRunsFromNewToFull() {
        val utc = ZoneId.of("UTC")
        assertTrue(moonPhase(LocalDate.of(2024, 1, 11), utc).age < 0.03)
        assertEquals(0.5, moonPhase(LocalDate.of(2024, 1, 25), utc).age, 0.03)
    }
}
