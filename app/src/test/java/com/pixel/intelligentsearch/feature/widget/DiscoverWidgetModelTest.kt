package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.core.answers.ForecastDay
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.answers.WeatherHour
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.data.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale

/**
 * What the Discover widget says at each of its three sizes for the weather, the calendar and the time it is drawn at:
 * the next event today and its time, "Nothing else today" once the day is clear, the weather alone or the event alone
 * when the other is off, and where to turn one on when both are.
 */
class DiscoverWidgetModelTest {

    private val now: ZonedDateTime = ZonedDateTime.parse("2026-11-14T14:38-05:00[America/New_York]")
    private val nowMs = now.toInstant().toEpochMilli()

    private fun at(time: String): Long = ZonedDateTime.parse("${time}-05:00[America/New_York]").toInstant().toEpochMilli()

    private fun event(title: String, time: String, place: String = "", allDay: Boolean = false, id: Long = 1) =
        CalendarEvent(title = title, startTime = "", eventId = id, beginMillis = at(time), allDay = allDay, location = place)

    private val dentist = event("Dentist", "2026-11-14T15:30", place = "1200 Main St")
    private val clear = DiscoverWeather(at = nowMs, unit = TemperatureUnit.FAHRENHEIT, temperature = 63.2, code = 0, isDay = true, high = 67.6, low = 51.0)

    private fun texts(weather: DiscoverWeather? = clear, events: List<CalendarEvent>? = listOf(dentist), leaveAt: Long? = null, is24Hour: Boolean = false) =
        DiscoverWidgetModel.texts(DiscoverInput(weather, events, leaveAt), now, is24Hour, Locale.US)

    @Test
    fun theNextEventAndTheWeatherAtEachSize() {
        val shown = texts(weather = clear.copy(rainAt = at("2026-11-14T20:00")), leaveAt = at("2026-11-14T15:08"))

        assertEquals(DiscoverPill(temperature = "63°", lead = "Dentist", time = " · 3:30 PM"), shown.pill)
        assertEquals(DiscoverTile(top = "Clear · H 68°", hero = "63°", headline = null, lead = "Dentist", time = " · 3:30 PM", accentBottom = false), shown.tile)
        assertEquals("UP NEXT", shown.wide.label)
        assertEquals("in 52 min", shown.wide.badge)
        assertEquals("Dentist", shown.wide.title)
        assertEquals(listOf("3:30 PM", "1200 Main St", "Leave by 3:08 PM"), shown.wide.meta)
        assertEquals("63° Clear · H 68° · Rain around 8 PM", shown.wide.weather)
        assertEquals(DiscoverDot.SUN, shown.dot)
        assertFalse(shown.setUp)
        assertEquals(
            "Discover. 63° and Clear, high 68°, Rain around 8 PM. Up next, Dentist at 3:30 PM, in 52 min, at 1200 Main St, leave by 3:08 PM.",
            shown.spoken
        )
    }

    @Test
    fun onlyThePartsThatExistAreShown() {
        val shown = texts(events = listOf(event("Standup", "2026-11-14T15:30")))
        assertEquals(listOf("3:30 PM"), shown.wide.meta)
        assertEquals("63° Clear · H 68°", shown.wide.weather)
    }

    @Test
    fun withNoEventLeftTodayTheDayIsClear() {
        val shown = texts(events = emptyList())
        assertEquals(DiscoverPill("63°", "Nothing else today", null), shown.pill)
        assertEquals("Nothing else today", shown.tile.lead)
        assertNull(shown.tile.time)
        assertEquals("TODAY", shown.wide.label)
        assertEquals("Nothing else today", shown.wide.title)
        assertNull(shown.wide.badge)
        assertTrue(shown.wide.meta.isEmpty())
        assertEquals("63° Clear · H 68°", shown.wide.weather)
    }

    @Test
    fun tomorrowsFirstEventIsNotTodaysNextButTheWideSizeNamesIt() {
        val shown = texts(events = listOf(event("Breakfast", "2026-11-15T08:00"), event("Lunch", "2026-11-15T12:00", id = 2)))
        assertEquals("Nothing else today", shown.pill.lead)
        assertEquals("TODAY", shown.wide.label)
        assertEquals(listOf("Tomorrow · Breakfast 8:00 AM"), shown.wide.meta)
    }

    @Test
    fun anAllDayEventIsNotUpNext() {
        // An all-day event begins at midnight UTC, which is still ahead of the afternoon here.
        val allDay = event("Conference", "2026-11-14T19:00", allDay = true)
        assertEquals("Nothing else today", texts(events = listOf(allDay)).pill.lead)
        assertEquals("Dentist", texts(events = listOf(allDay, dentist)).pill.lead)
    }

    @Test
    fun anEventThatHasStartedGivesWayToTheOneAfterIt() {
        val started = event("Lunch", "2026-11-14T14:00")
        val shown = texts(events = listOf(started, dentist))
        assertEquals("Dentist", shown.wide.title)
        assertEquals("Nothing else today", texts(events = listOf(started)).wide.title)
    }

    @Test
    fun howSoonIsShownOnlyInTheLastHour() {
        val later = event("Dinner", "2026-11-14T19:00")
        assertNull(texts(events = listOf(later)).wide.badge)
        assertEquals("in 1 hr", texts(events = listOf(event("Call", "2026-11-14T15:38"))).wide.badge)
        assertEquals("in 1 min", texts(events = listOf(event("Call", "2026-11-14T14:38:30"))).wide.badge)
    }

    @Test
    fun withoutWeatherTheEventLeads() {
        val shown = texts(weather = null)
        assertEquals(DiscoverPill(temperature = null, lead = "Dentist", time = " · 3:30 PM"), shown.pill)
        assertEquals(
            DiscoverTile(top = "UP NEXT", hero = null, headline = "Dentist", lead = "3:30 PM · in 52 min", time = null, accentBottom = true),
            shown.tile
        )
        assertNull(shown.wide.weather)
        assertNull(shown.dot)
        assertEquals("Discover. Up next, Dentist at 3:30 PM, in 52 min, at 1200 Main St.", shown.spoken)

        val nothing = texts(weather = null, events = listOf(event("Breakfast", "2026-11-15T08:00")))
        assertEquals(
            DiscoverTile(top = "TODAY", hero = null, headline = "Nothing else today", lead = "Tomorrow · Breakfast 8:00 AM", time = null, accentBottom = false),
            nothing.tile
        )
    }

    @Test
    fun withoutEventsTheWeatherStandsAlone() {
        val shown = texts(weather = clear.copy(rainAt = at("2026-11-14T20:00")), events = null)
        assertEquals(DiscoverPill("63°", "Clear · H 68°", null), shown.pill)
        assertEquals(DiscoverTile(top = "Clear · H 68°", hero = "63°", headline = null, lead = "Rain around 8 PM", time = null, accentBottom = false), shown.tile)
        assertEquals("TODAY", shown.wide.label)
        assertEquals("63° · Clear", shown.wide.title)
        assertEquals(listOf("H 68° · L 51°", "Rain around 8 PM"), shown.wide.meta)
        assertNull(shown.wide.weather)
        assertEquals("Discover. 63° and Clear, high 68°, Rain around 8 PM.", shown.spoken)

        // Nothing under the temperature when no rain is on the way.
        assertNull(texts(events = null).tile.lead)
    }

    @Test
    fun withNeitherItSaysWhereToTurnOneOn() {
        val shown = texts(weather = null, events = null)
        assertTrue(shown.setUp)
        assertEquals("Turn on Up Next or Weather in Discover", shown.pill.lead)
        assertNull(shown.pill.temperature)
        assertEquals("Turn on Up Next or Weather in Discover", shown.tile.headline)
        assertEquals("Turn on Up Next or Weather in Discover", shown.wide.title)
        assertNull(shown.wide.weather)
        assertEquals("Discover. Turn on Up Next or Weather in Discover.", shown.spoken)
    }

    @Test
    fun weatherThatIsOnButCouldNotBeReadIsNotSomethingToTurnOn() {
        val shown = DiscoverWidgetModel.texts(DiscoverInput(null, null, weatherDue = true), now, false, Locale.US)
        assertFalse(shown.setUp)
        assertEquals("No weather right now", shown.pill.lead)
        assertEquals("No weather right now", shown.tile.headline)
        assertEquals("No weather right now", shown.wide.title)
        assertEquals("Discover. No weather right now.", shown.spoken)
        // With events to show, the weather is simply absent.
        val withEvents = DiscoverWidgetModel.texts(DiscoverInput(null, listOf(dentist), weatherDue = true), now, false, Locale.US)
        assertEquals(texts(weather = null), withEvents)
    }

    @Test
    fun aLongNameKeepsItsTimeApartSoTheTimeIsNeverCut() {
        val long = event("Quarterly planning review with the whole leadership team and guests", "2026-11-14T15:30")
        val shown = texts(events = listOf(long))
        // The layouts cut the name short at the end and draw the time whole after it.
        assertEquals(long.title, shown.pill.lead)
        assertEquals(" · 3:30 PM", shown.pill.time)
        assertEquals(long.title, shown.tile.lead)
        assertEquals(" · 3:30 PM", shown.tile.time)
        assertEquals(long.title, shown.wide.title)
    }

    @Test
    fun timesFollowThePhonesClock() {
        val shown = texts(weather = clear.copy(rainAt = at("2026-11-14T20:00")), leaveAt = at("2026-11-14T15:08"), is24Hour = true)
        assertEquals(" · 15:30", shown.pill.time)
        assertEquals(listOf("15:30", "1200 Main St", "Leave by 15:08"), shown.wide.meta)
        assertEquals("63° Clear · H 68° · Rain around 20:00", shown.wide.weather)
        val tomorrow = texts(events = listOf(event("Breakfast", "2026-11-15T08:00")), is24Hour = true)
        assertEquals(listOf("Tomorrow · Breakfast 08:00"), tomorrow.wide.meta)
    }

    @Test
    fun temperaturesAreShownAsReadInEitherScale() {
        val celsius = clear.copy(unit = TemperatureUnit.CELSIUS, temperature = 17.4, high = 19.5, low = -0.4)
        val shown = texts(weather = celsius, events = null)
        assertEquals("17°", shown.pill.temperature)
        assertEquals("Clear · H 20°", shown.tile.top)
        assertEquals("H 20° · L 0°", shown.wide.before)
    }

    @Test
    fun theDotSaysWhatTheSkyIsDoing() {
        fun dot(code: Int, isDay: Boolean = true) = texts(weather = clear.copy(code = code, isDay = isDay)).dot
        assertEquals(DiscoverDot.SUN, dot(0))
        assertEquals(DiscoverDot.SUN, dot(2))
        assertEquals(DiscoverDot.PLAIN, dot(0, isDay = false))
        assertEquals(DiscoverDot.PLAIN, dot(3))
        assertEquals(DiscoverDot.RAIN, dot(63))
        assertEquals(DiscoverDot.RAIN, dot(73))
    }

    @Test
    fun rainThatHasPassedAndALeaveByThatHasPassedAreNoLongerSaid() {
        val shown = texts(weather = clear.copy(rainAt = at("2026-11-14T14:00"), snow = true), leaveAt = at("2026-11-14T14:30"))
        assertEquals("63° Clear · H 68°", shown.wide.weather)
        assertEquals(listOf("3:30 PM", "1200 Main St"), shown.wide.meta)
        assertEquals("63° Clear · H 68° · Snow around 9 PM", texts(weather = clear.copy(rainAt = at("2026-11-14T21:00"), snow = true)).wide.weather)
    }

    @Test
    fun aReadingIsKeptAndReadBackWhole() {
        val reading = clear.copy(rainAt = at("2026-11-14T20:00"), snow = true)
        assertEquals(reading, DiscoverWeather.decode(reading.encode()))
        assertNull(DiscoverWeather.decode(null))
        assertNull(DiscoverWeather.decode(""))
        assertNull(DiscoverWeather.decode("not|a|reading"))
    }

    @Test
    fun aReadingTakesTheFirstHourRainOrSnowIsLikely() {
        fun hour(time: String, chance: Int, code: Int) =
            WeatherHour(ZonedDateTime.parse("${time}-05:00[America/New_York]"), 60.0, code, isDay = true, precipitation = chance)
        val report = WeatherReport(
            placeName = "Here", unit = TemperatureUnit.FAHRENHEIT, day = ForecastDay.TODAY, temperature = 63.0, feelsLike = null, code = 2,
            isDay = true, high = 68.0, low = 51.0,
            hours = listOf(hour("2026-11-14T16:00", 20, 2), hour("2026-11-14T17:00", 60, 71), hour("2026-11-14T18:00", 90, 63))
        )
        val reading = DiscoverWeather.from(report, nowMs)
        assertEquals(at("2026-11-14T17:00"), reading.rainAt)
        assertTrue(reading.snow)
        assertEquals(0L, DiscoverWeather.from(report.copy(hours = emptyList()), nowMs).rainAt)
    }

    @Test
    fun theNextRedrawComesWhenThereIsSomethingNewToSay() {
        fun delay(vararg events: CalendarEvent) = Duration.ofMillis(DiscoverWidgetModel.nextRedrawDelayMs(now, events.toList()))
        // Nothing ahead: in an hour.
        assertEquals(Duration.ofMinutes(60), delay())
        assertEquals(Duration.ofMinutes(60), Duration.ofMillis(DiscoverWidgetModel.nextRedrawDelayMs(now, null)))
        // An event later on: when it comes within the hour, if that is sooner.
        assertEquals(Duration.ofMinutes(22), delay(event("Dinner", "2026-11-14T16:00")))
        assertEquals(Duration.ofMinutes(60), delay(event("Dinner", "2026-11-14T19:00")))
        // Within the hour: every five minutes, and just as it starts.
        assertEquals(Duration.ofMinutes(5), delay(dentist))
        assertEquals(Duration.ofSeconds(121), delay(event("Call", "2026-11-14T14:40")))
        // Never past midnight, when tomorrow's events become today's.
        val lateEvening = ZonedDateTime.parse("2026-11-14T23:30-05:00[America/New_York]")
        assertEquals(Duration.ofSeconds(30 * 60 + 1), Duration.ofMillis(DiscoverWidgetModel.nextRedrawDelayMs(lateEvening, emptyList())))
    }
}
