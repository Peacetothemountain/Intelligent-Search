package com.pixel.intelligentsearch.core.occasion

import com.pixel.intelligentsearch.core.occasion.DoodleCatalog.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** What the doodle gallery says about each doodle: its group, where it's shown and when it next shows. */
class DoodleCatalogTest {

    private val newYork = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 10, 20)
    private val us by lazy { DoodleCatalog.entries(today, newYork, "US").associateBy { it.occasion } }

    @Test
    fun everyDoodleIsListedInCalendarOrder() {
        assertEquals(Occasion.entries.toList(), DoodleCatalog.entries(today, newYork, "US").map { it.occasion })
    }

    /** Also checks the calendar's region list: a doodle kept only in a region it lacks would be found nowhere. */
    @Test
    fun everyDoodleShowsSomewhere() {
        val nowhere = us.values.filter { it.nextHere == null && it.nextElsewhere == null }.map { it.occasion }
        assertTrue("Never shown: $nowhere", nowhere.isEmpty())
        val unplaced = us.values.filter { !it.everywhere && it.regions.isEmpty() }
        assertTrue(unplaced.isEmpty())
    }

    @Test
    fun theNextDayHereIsTheCalendarsOwn() {
        for (entry in us.values) {
            val next = entry.nextHere ?: continue
            assertEquals(entry.occasion, OccasionCalendar.on(next, newYork, "US"))
            var day = today
            while (day < next) {
                assertTrue("${entry.occasion} shows earlier, on $day", OccasionCalendar.on(day, newYork, "US") != entry.occasion)
                day = day.plusDays(1)
            }
        }
    }

    @Test
    fun usDaysAreDatedAndPlaced() {
        val thanksgiving = us.getValue(Occasion.THANKSGIVING)
        assertEquals(LocalDate.of(2026, 11, 26), thanksgiving.nextHere)
        assertEquals(setOf("US", "CA"), thanksgiving.regions)
        assertNull(thanksgiving.nextElsewhere)
        assertEquals(setOf("US"), us.getValue(Occasion.VETERANS_DAY).regions)
        assertEquals(LocalDate.of(2026, 10, 31), us.getValue(Occasion.HALLOWEEN).nextHere)
        assertTrue(us.getValue(Occasion.HALLOWEEN).everywhere)
        assertEquals(LocalDate.of(2028, 2, 29), us.getValue(Occasion.LEAP_DAY).nextHere)
    }

    @Test
    fun aDayKeptElsewhereSaysWhereAndWhen() {
        val diwali = us.getValue(Occasion.DIWALI)
        assertNull(diwali.nextHere)
        assertEquals(setOf("IN"), diwali.regions)
        assertEquals(LocalDate.of(2026, 11, 8) to "IN", diwali.nextElsewhere)
        val india = DoodleCatalog.entries(today, ZoneId.of("Asia/Kolkata"), "IN").associateBy { it.occasion }
        assertEquals(LocalDate.of(2026, 11, 8), india.getValue(Occasion.DIWALI).nextHere)
        assertNull(india.getValue(Occasion.THANKSGIVING).nextHere)
        assertNotNull(india.getValue(Occasion.THANKSGIVING).nextElsewhere)
    }

    /**
     * In London the March equinox falls on March 20 every year in the horizon, so the first day of spring always takes
     * the International Day of Happiness's place: it shows nowhere in that time zone, and isn't shown everywhere.
     */
    @Test
    fun aDayShownNowhereInThisTimeZoneIsNotShownEverywhere() {
        val london = DoodleCatalog.entries(today, ZoneId.of("Europe/London"), "GB").associateBy { it.occasion }
        val everywhereButNowhere = london.values.filter { it.everywhere && it.nextHere == null && it.nextElsewhere == null }
        assertTrue("Shown everywhere but never shown: ${everywhereButNowhere.map { it.occasion }}", everywhereButNowhere.isEmpty())
        val happiness = london.getValue(Occasion.HAPPINESS_DAY)
        assertNull(happiness.nextHere)
        assertTrue(happiness.regions.isEmpty())
    }

    @Test
    fun groups() {
        for (holiday in listOf(Occasion.MLK_DAY, Occasion.PRESIDENTS_DAY, Occasion.MEMORIAL_DAY, Occasion.JUNETEENTH,
            Occasion.INDEPENDENCE_DAY, Occasion.LABOR_DAY, Occasion.VETERANS_DAY, Occasion.THANKSGIVING)) {
            assertEquals(holiday.name, Kind.US_HOLIDAY, DoodleCatalog.kind(holiday))
        }
        assertEquals(Kind.SEASON, DoodleCatalog.kind(Occasion.FIRST_DAY_OF_WINTER))
        assertEquals(Kind.WORLD, DoodleCatalog.kind(Occasion.DIWALI))
        assertEquals(Kind.HOLIDAY, DoodleCatalog.kind(Occasion.HALLOWEEN))
        assertEquals(Kind.FUN_DAY, DoodleCatalog.kind(Occasion.PI_DAY))
        // Every day kept in only a few regions abroad is grouped as one of the world's, not a fun day.
        for (entry in us.values.filter { it.nextHere == null }) {
            assertEquals(entry.occasion.name, Kind.WORLD, entry.kind)
        }
    }
}
