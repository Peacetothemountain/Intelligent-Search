package com.pixel.intelligentsearch.core.glance

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.feature.search.birthdayDayLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.MonthDay

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

    @Test
    fun withoutContactsAccessThereAreNone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertTrue(Birthdays.load(context).isEmpty())
    }
}
