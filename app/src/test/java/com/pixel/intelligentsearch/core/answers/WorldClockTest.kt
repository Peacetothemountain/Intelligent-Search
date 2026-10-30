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

    @Test
    fun everydayWordsAreZonesOnlyWhenAskedAsAPlace() {
        // Pacific/Wake, America/North_Dakota/Center, Pacific/Midway, Antarctica/Davis and the like.
        for (query in listOf(
            "wake time", "center time", "midway time", "davis time", "casey time", "palmer time", "troll time",
            "jersey time", "petersburg time", "stanley time", "victoria time", "reunion time", "knox time",
            "3pm wake time to london"
        )) {
            assertNull("\"$query\" should not be a time answer", answer(query))
        }
        assertEquals(WorldClock.TimeAnswer(time("Pacific/Wake"), "Current time in Wake • Tomorrow"), answer("time in wake"))
        assertEquals(time("America/North_Dakota/Center"), answer("what time is it in center")?.title)
        assertEquals(time("Antarctica/Davis"), answer("time in davis")?.title)
        // Other zone cities and the places named in the alias table still answer on their own.
        assertEquals(time("America/Phoenix"), answer("phoenix time")?.title)
        assertEquals(time("Europe/Moscow"), answer("st. petersburg time")?.title)
        assertEquals(time("America/Puerto_Rico"), answer("time in san juan")?.title)
    }

    @Test
    fun anAmbiguousPlaceAskedForIsTheOnePeopleMean() {
        // The zone database's Petersburg is in Indiana, its Jersey the Channel Island and its Cordoba in Argentina.
        assertEquals(WorldClock.TimeAnswer(time("Europe/Moscow"), "Current time in St Petersburg • Tomorrow"), answer("time in petersburg"))
        assertEquals(time("America/New_York"), answer("time in jersey")?.title)
        assertEquals(time("Europe/Madrid"), answer("time in cordoba")?.title)
    }

    @Test
    fun usStatesHaveTheirZoneAndArizonaItsOwn() {
        assertEquals(time("America/Phoenix"), answer("arizona time")?.title)
        assertEquals(time("America/Phoenix"), answer("time in arizona")?.title)
        assertEquals(time("America/Chicago"), answer("time in texas")?.title)
        assertEquals(time("America/New_York"), answer("florida time")?.title)
        assertEquals(time("America/New_York"), answer("new jersey time")?.title)
        assertEquals(time("America/Denver"), answer("time in colorado")?.title)
        // Georgia is a country as well as a state, so it isn't guessed.
        assertNull(answer("georgia time"))
    }

    @Test
    fun countriesSpanningZonesNameTheCityWhoseTimeIsShown() {
        val unitedStates = WorldClock.TimeAnswer(time("America/New_York"), "Current time in United States (New York)")
        for (query in listOf("time in usa", "time in the us", "time in united states", "time in america", "us time", "time in the USA")) {
            assertEquals(query, unitedStates, answer(query))
        }
        val cities = mapOf(
            "canada" to "Canada (Toronto)", "australia" to "Australia (Sydney) • Tomorrow", "brazil" to "Brazil (São Paulo)",
            "russia" to "Russia (Moscow) • Tomorrow", "mexico" to "Mexico (Mexico City)",
            "indonesia" to "Indonesia (Jakarta) • Tomorrow", "kazakhstan" to "Kazakhstan (Almaty) • Tomorrow"
        )
        for ((country, label) in cities) {
            assertEquals(country, "Current time in $label", answer("time in $country")?.subtitle)
        }
        assertEquals(time("Asia/Almaty"), answer("kazakhstan time")?.title)
        // A single-zone country keeps its plain name.
        assertEquals("Current time in Japan • Tomorrow", answer("time in japan")?.subtitle)
    }

    private fun time(zone: String): String {
        val there = Instant.parse("2026-10-20T22:30:00Z").atZone(ZoneId.of(zone))
        return com.pixel.intelligentsearch.core.util.Durations.timeOfDay(there.hour, there.minute, false, Locale.US)
    }

    @Test
    fun theTimeInAZoneAbbreviation() {
        // A zone abbreviation is its region's clock, daylight saving and all, labeled with the name of the time kept
        // there now. Each used to be a fixed offset, so in October "PST" was 2:30 PM while Los Angeles read 3:30 PM.
        assertEquals(WorldClock.TimeAnswer("3:30 PM", "Current time in PDT"), answer("time in PST"))
        assertEquals(WorldClock.TimeAnswer("3:30 PM", "Current time in PDT"), answer("pdt time"))
        assertEquals("6:30 PM", answer("time in est")?.title)
        assertEquals("6:30 PM", answer("time in edt")?.title)
        assertEquals("5:30 PM", answer("time in cst")?.title)
        assertEquals("4:30 PM", answer("time in mst")?.title)
        assertEquals("10:30 PM", answer("time in gmt")?.title)
        assertEquals("10:30 PM", answer("utc time")?.title)
        assertEquals(WorldClock.TimeAnswer("12:30 AM", "Current time in CEST • Tomorrow"), answer("time in cet"))
        assertEquals("12:30 AM", answer("time in cest")?.title)
        assertEquals("11:30 PM", answer("time in bst")?.title)
        // IST is India's.
        assertEquals(WorldClock.TimeAnswer("4:00 AM", "Current time in IST • Tomorrow"), answer("time in ist"))
        assertEquals("7:30 AM", answer("jst time")?.title)
        // Sydney is on summer time from October.
        assertEquals(WorldClock.TimeAnswer("9:30 AM", "Current time in AEDT • Tomorrow"), answer("time in aest"))
        assertEquals("9:30 AM", answer("time in aedt")?.title)
        // The bare PT is shown as typed.
        assertEquals(WorldClock.TimeAnswer("3:30 PM", "Current time in PT"), answer("time in pt"))
        assertEquals("3:30 PM", answer("pacific time")?.title)
    }

    @Test
    fun aZoneAbbreviationFollowsItsRegionThroughTheYear() {
        // Wednesday, January 14, 2026, 12:00 UTC: winter in the north, summer in Australia.
        val january = WorldClock(Clock.fixed(Instant.parse("2026-01-14T12:00:00Z"), ZoneOffset.UTC), Locale.US)
        fun winter(query: String) = january.evaluate(query, false, ZoneOffset.UTC)
        assertEquals(WorldClock.TimeAnswer("4:00 AM", "Current time in PST"), winter("time in pst"))
        assertEquals(WorldClock.TimeAnswer("4:00 AM", "Current time in PST"), winter("time in pdt"))
        assertEquals("7:00 AM", winter("time in est")?.title)
        assertEquals(WorldClock.TimeAnswer("1:00 PM", "Current time in CET"), winter("time in cet"))
        assertEquals(WorldClock.TimeAnswer("12:00 PM", "Current time in GMT"), winter("time in bst"))
        // AEST in January is Sydney's summer clock, UTC+11, not UTC+10.
        assertEquals(WorldClock.TimeAnswer("11:00 PM", "Current time in AEDT"), winter("time in aest"))
        // 3pm in Los Angeles in winter is UTC-8.
        assertEquals(WorldClock.TimeAnswer("4:30 AM", "In IST, next day • 3:00 PM PST"), winter("3pm pst in ist"))
    }

    @Test
    fun aTimeMovedToAnotherZone() {
        // 3pm in Los Angeles, on PDT in October (22:00 UTC), is 3:30am the next day in India. With PST as a fixed
        // UTC-8 it was 4:30am, an hour off.
        assertEquals(WorldClock.TimeAnswer("3:30 AM", "In IST, next day • 3:00 PM PDT"), answer("3pm PST in IST"))
        assertEquals(answer("3pm PST in IST"), answer("3 p.m. pst to ist"))
        assertEquals(WorldClock.TimeAnswer("11:00 AM", "In EDT • 3:00 PM UTC"), answer("15:00 utc to est"))
        assertEquals(WorldClock.TimeAnswer("6:00 PM", "In PDT, previous day • 1:00 AM UTC"), answer("1am utc to pst"))
        assertEquals(WorldClock.TimeAnswer("1:30 AM", "In London • 9:30 AM Tokyo"), answer("9:30 am tokyo time to london"))
        assertEquals(WorldClock.TimeAnswer("21:00", "In CEST • 12:00 PDT"), answer("noon pst in cet", is24Hour = true))
        assertEquals("12:00 AM", answer("midnight utc in utc")?.title)
    }

    @Test
    fun moreCountriesAndPlacesHaveTheirZone() {
        // Each had no answer.
        val zones = mapOf(
            "bali" to "Asia/Makassar", "sf" to "America/Los_Angeles", "croatia" to "Europe/Zagreb",
            "serbia" to "Europe/Belgrade", "jordan" to "Asia/Amman", "dominican republic" to "America/Santo_Domingo",
            "san jose" to "America/Los_Angeles", "lebanon" to "Asia/Beirut", "uruguay" to "America/Montevideo",
            "tanzania" to "Africa/Dar_es_Salaam", "st. louis" to "America/Chicago"
        )
        for ((place, zone) in zones) {
            assertEquals(place, time(zone), answer("time in $place")?.title)
        }
        assertEquals("Current time in SF", answer("sf time")?.subtitle)
        // Georgia is still a country as well as a state.
        assertNull(answer("time in georgia"))
    }

    @Test
    fun zoneAbbreviationsAreKnownToTheSpellChecker() {
        for (word in listOf("pst", "PST", "ist", "cest", "aedt", "pt")) {
            assertEquals(word, true, WorldClock.isZoneAbbreviation(word))
        }
        assertEquals(false, WorldClock.isZoneAbbreviation("post"))
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
