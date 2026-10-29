package com.pixel.intelligentsearch.core.answers

import com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Times in places and zones, and a time moved between zones, for a fixed "now": Tuesday, October 20, 2026, 22:30 UTC. */
class WorldClockTest {

    private val clock = WorldClock(Clock.fixed(Instant.parse("2026-10-20T22:30:00Z"), ZoneOffset.UTC), Locale.US)

    private fun answer(query: String, is24Hour: Boolean = false, here: ZoneId = ZoneOffset.UTC) =
        clock.evaluate(query, is24Hour, here)

    @Test
    fun theTimeHere() {
        for (query in listOf("time", "what time is it", "What time is it?", "current time", "what's the time")) {
            assertEquals(query, WorldClock.TimeAnswer("10:30 PM", "Current time in your location"), answer(query))
        }
        assertEquals("3:30 PM", answer("time", here = ZoneId.of("America/Los_Angeles"))?.title)
        assertEquals("22:30", answer("time", is24Hour = true)?.title)
    }

    @Test
    fun theTimeInAPlace() {
        val tokyo = WorldClock.TimeAnswer("7:30 AM", "Current time in Tokyo • Tomorrow")
        for (query in listOf("time in tokyo", "what time is it in Tokyo?", "tokyo time", "Tokyo local time", "what's the time in tokyo", "time now in tokyo")) {
            assertEquals(query, tokyo, answer(query))
        }
        assertEquals(WorldClock.TimeAnswer("6:30 PM", "Current time in New York"), answer("new york time"))
        assertEquals(WorldClock.TimeAnswer("4:00 AM", "Current time in India • Tomorrow"), answer("time in india"))
        // "Yesterday" and "tomorrow" are from where the phone is.
        assertEquals("Current time in London", answer("time in london", here = ZoneId.of("Europe/London"))?.subtitle)
        assertEquals("Current time in Honolulu • Yesterday", answer("honolulu time", here = ZoneId.of("Asia/Tokyo"))?.subtitle)
    }

    @Test
    fun countriesAndBigCitiesTheZoneDatabaseDoesntNameHaveTheirZone() {
        // The zone database names them only in old one-word ids ("Israel", "Japan") or not at all.
        val zones = mapOf(
            "israel" to "Asia/Jerusalem", "turkey" to "Europe/Istanbul", "egypt" to "Africa/Cairo",
            "iceland" to "Atlantic/Reykjavik", "japan" to "Asia/Tokyo", "singapore" to "Asia/Singapore",
            "hong kong" to "Asia/Hong_Kong", "brazil" to "America/Sao_Paulo", "australia" to "Australia/Sydney",
            "south africa" to "Africa/Johannesburg", "mexico" to "America/Mexico_City", "canada" to "America/Toronto",
            "argentina" to "America/Argentina/Buenos_Aires", "barcelona" to "Europe/Madrid",
            "washington dc" to "America/New_York", "munich" to "Europe/Berlin", "shenzhen" to "Asia/Shanghai",
            "osaka" to "Asia/Tokyo", "rio" to "America/Sao_Paulo", "são paulo" to "America/Sao_Paulo",
            "bogotá" to "America/Bogota", "mexico city" to "America/Mexico_City", "seoul" to "Asia/Seoul",
            "buenos aires" to "America/Argentina/Buenos_Aires", "toronto" to "America/Toronto"
        )
        for ((place, zone) in zones) {
            val expected = time(zone)
            assertEquals(place, expected, answer("time in $place")?.title)
        }
        assertEquals("Current time in Washington DC", answer("what time is it in washington d.c.")?.subtitle?.substringBefore(" •"))
    }

    @Test
    fun oldZoneAliasesDontMakeOrdinaryWordsPlaces() {
        // "Mexico/General", "Brazil/East" and "Australia/North" made these ordinary words zones.
        for (query in listOf("time in general", "east time", "time in north", "time in act")) {
            assertNull(query, answer(query))
        }
        assertEquals(time("America/Los_Angeles"), answer("pacific time")?.title)
    }

    private fun time(zone: String): String {
        val there = Instant.parse("2026-10-20T22:30:00Z").atZone(ZoneId.of(zone))
        return com.pixel.intelligentsearch.core.util.Durations.timeOfDay(there.hour, there.minute, false, Locale.US)
    }

    @Test
    fun theTimeInAZoneAbbreviation() {
        assertEquals(WorldClock.TimeAnswer("2:30 PM", "Current time in PST"), answer("time in PST"))
        assertEquals(WorldClock.TimeAnswer("3:30 PM", "Current time in PDT"), answer("pdt time"))
        assertEquals("5:30 PM", answer("time in est")?.title)
        assertEquals("6:30 PM", answer("time in edt")?.title)
        assertEquals("4:30 PM", answer("time in cst")?.title)
        assertEquals("3:30 PM", answer("time in mst")?.title)
        assertEquals("10:30 PM", answer("time in gmt")?.title)
        assertEquals("10:30 PM", answer("utc time")?.title)
        assertEquals("11:30 PM", answer("time in cet")?.title)
        assertEquals("12:30 AM", answer("time in cest")?.title)
        assertEquals("11:30 PM", answer("time in bst")?.title)
        // IST is India's.
        assertEquals(WorldClock.TimeAnswer("4:00 AM", "Current time in IST • Tomorrow"), answer("time in ist"))
        assertEquals("7:30 AM", answer("jst time")?.title)
        assertEquals("8:30 AM", answer("time in aest")?.title)
        assertEquals("9:30 AM", answer("time in aedt")?.title)
        // The bare PT follows daylight saving: Los Angeles is on PDT in October.
        assertEquals(WorldClock.TimeAnswer("3:30 PM", "Current time in PT"), answer("time in pt"))
        assertEquals("3:30 PM", answer("pacific time")?.title)
    }

    @Test
    fun aTimeMovedToAnotherZone() {
        // 3pm PST (23:00 UTC) is 4:30am the next day in India.
        assertEquals(WorldClock.TimeAnswer("4:30 AM", "In IST, next day • 3:00 PM PST"), answer("3pm PST in IST"))
        assertEquals(answer("3pm PST in IST"), answer("3 p.m. pst to ist"))
        assertEquals(WorldClock.TimeAnswer("10:00 AM", "In EST • 3:00 PM UTC"), answer("15:00 utc to est"))
        assertEquals(WorldClock.TimeAnswer("5:00 PM", "In PST, previous day • 1:00 AM UTC"), answer("1am utc to pst"))
        assertEquals(WorldClock.TimeAnswer("1:30 AM", "In London • 9:30 AM Tokyo"), answer("9:30 am tokyo time to london"))
        assertEquals(WorldClock.TimeAnswer("21:00", "In CET • 12:00 PST"), answer("noon pst in cet", is24Hour = true))
        assertEquals("12:00 AM", answer("midnight utc in utc")?.title)
    }

    @Test
    fun unknownPlacesAndOtherSearchesAreLeftAlone() {
        for (query in listOf(
            "time in narnia", "screen time", "bed time", "christmas time", "easter time", "prime time", "time zone",
            "time in london at 5pm", "3pm", "3 pst in ist", "25:00 utc to est", "13pm utc to est", "3pm pst in narnia",
            "5 days in weeks", "timer 5m", "times square", "  "
        )) {
            assertNull("\"$query\" should not be a time answer", answer(query))
        }
    }

    @Test
    fun conversionsAreNotTakenByTheCalculator() {
        for (query in listOf("3pm PST in IST", "15:00 utc to est", "time in pst", "tokyo time")) {
            assertNull(query, MathematicalExpressionEngine.evaluate(query))
        }
    }
}
