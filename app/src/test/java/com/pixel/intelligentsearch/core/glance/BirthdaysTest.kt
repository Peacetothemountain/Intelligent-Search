package com.pixel.intelligentsearch.core.glance

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.feature.search.birthdayDayLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Contacts' birthdays: the date formats contacts apps store, the week At a Glance looks ahead, and the permission. */
@RunWith(RobolectricTestRunner::class)
class BirthdaysTest {

    @Test
    fun readsTheFormatsContactsAppsStore() {
        assertEquals(Birthdays.Parsed(MonthDay.of(5, 17), 1990), Birthdays.parse("1990-05-17"))
        assertEquals(Birthdays.Parsed(MonthDay.of(5, 17), 1990), Birthdays.parse("19900517"))
        assertEquals(Birthdays.Parsed(MonthDay.of(5, 17), 1990), Birthdays.parse("1990-05-17T00:00:00.000Z"))
        assertEquals(Birthdays.Parsed(MonthDay.of(5, 17), null), Birthdays.parse("--05-17"))
        assertEquals(Birthdays.Parsed(MonthDay.of(2, 29), null), Birthdays.parse("--0229"))
        assertNull(Birthdays.parse("1990-13-01"))
        assertNull(Birthdays.parse("May 17"))
        assertNull(Birthdays.parse(null))
    }

    private fun event(id: Long, name: String, date: String) = Birthdays.Event(id, "lk$id", name, date)

    @Test
    fun theWeekRunsFromTodayThroughSixDaysAhead() {
        val today = LocalDate.of(2026, 10, 22)
        val shown = Birthdays.inWindow(
            listOf(
                event(1, "Today", "1996-10-22"),
                event(2, "Sixth Day", "--10-28"),
                event(3, "Seventh Day", "--10-29"),
                event(4, "Yesterday", "--10-21"),
                event(5, "Tomorrow", "2000-10-23")
            ),
            today
        )
        assertEquals(listOf("Today", "Tomorrow", "Sixth Day"), shown.map { it.name })
        assertEquals(listOf(0, 1, 6), shown.map { it.daysAway })
        assertEquals(30, shown[0].turning)
        assertEquals(26, shown[1].turning)
        assertNull("no year, no age", shown[2].turning)
        assertEquals(listOf("Today", "Tomorrow", "Wednesday"), shown.map(::birthdayDayLabel))
    }

    @Test
    fun theWeekCarriesOverNewYear() {
        val shown = Birthdays.inWindow(listOf(event(1, "Ana", "1990-01-02")), LocalDate.of(2026, 12, 30))
        assertEquals(3, shown.single().daysAway)
        assertEquals(LocalDate.of(2027, 1, 2), shown.single().date)
        assertEquals(37, shown.single().turning)
    }

    @Test
    fun aLeapDayBirthdayFallsOnFebruary28thInOtherYears() {
        assertEquals(LocalDate.of(2027, 2, 28), Birthdays.nextOccurrence(MonthDay.of(2, 29), LocalDate.of(2027, 2, 25)))
        assertEquals(LocalDate.of(2028, 2, 29), Birthdays.nextOccurrence(MonthDay.of(2, 29), LocalDate.of(2028, 2, 25)))
    }

    @Test
    fun oneBirthdayPerContactAndAtMostThree() {
        val today = LocalDate.of(2026, 10, 22)
        val events = listOf(
            event(1, "Ana", "--10-22"), event(1, "Ana", "--10-23"),
            event(2, "Bob", "--10-24"), event(3, "Cy", "--10-24"), event(4, "Di", "--10-25")
        )
        val shown = Birthdays.inWindow(events, today)
        assertEquals(listOf("Ana", "Bob", "Cy"), shown.map { it.name })
        assertEquals(Birthdays.MAX_SHOWN, shown.size)
    }

    /** The provider returns a contact's rows in no set order: an unreadable or far-off one first mustn't hide the real one. */
    @Test
    fun aContactsOtherBirthdayRowDoesNotHideTheirBirthday() {
        val today = LocalDate.of(2026, 10, 22)
        val shown = Birthdays.inWindow(
            listOf(
                event(1, "Ana", "May 17"), event(1, "Ana", "--10-23"),
                event(2, "Bob", "--03-01"), event(2, "Bob", "1990-10-24")
            ),
            today
        )
        assertEquals(listOf("Ana", "Bob"), shown.map { it.name })
        assertEquals(listOf(1, 2), shown.map { it.daysAway })
    }

    private val lateEvening = ZonedDateTime.of(2026, 10, 22, 23, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun theWeekMovesOnAtMidnightWhileTheSearchStaysOpen() = runTest {
        val shown = mutableListOf<List<String>>()
        val now = { lateEvening.plus(Duration.ofMillis(testScheduler.currentTime)) }
        val job = launch {
            Birthdays.upcoming({ listOf(event(1, "Ana", "--10-23")) }, Birthdays.DayCache(), now).collect { list ->
                shown += list.map { "${it.name} ${birthdayDayLabel(it)}" }
            }
        }
        runCurrent()
        assertEquals(listOf(emptyList(), listOf("Ana Tomorrow")), shown)
        advanceTimeBy(60 * 60 * 1000L - 1)
        runCurrent()
        assertEquals("not yet midnight", 2, shown.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("Ana Today"), shown.last())
        job.cancel()
    }

    @Test
    fun theDaysNextLookStartsWithWhatTheLastOneRead() = runTest {
        val cache = Birthdays.DayCache()
        val now = { lateEvening }
        Birthdays.upcoming({ listOf(event(1, "Ana", "--10-23")) }, cache, now).take(2).toList()
        // Shown before the contacts are read again, so a slow provider holds up nothing.
        val first = Birthdays.upcoming({ error("the contacts were read before anything showed") }, cache, now).first()
        assertEquals(listOf("Ana"), first.map { it.name })
        // Yesterday's are never shown for today.
        val tomorrow = { lateEvening.plusDays(1) }
        assertTrue(Birthdays.upcoming({ error("the contacts were read before anything showed") }, cache, tomorrow).first().isEmpty())
    }

    @Test
    fun theNextDayStartsAtMidnightWhereThePhoneIs() {
        assertEquals(60 * 60 * 1000L, Birthdays.untilNextDay(lateEvening))
        val tokyo = ZonedDateTime.of(2026, 10, 22, 23, 30, 0, 0, ZoneId.of("Asia/Tokyo"))
        assertEquals(30 * 60 * 1000L, Birthdays.untilNextDay(tokyo))
    }

    @Test
    fun withoutContactsAccessThereAreNone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertTrue(Birthdays.load(context).isEmpty())
    }
}
