package com.pixel.intelligentsearch.core.glance

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/** A contact's birthday in the coming week: [daysAway] 0 is today; [turning] is the age reached, when the year is known. */
data class GlanceBirthday(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val daysAway: Int,
    val date: LocalDate,
    val turning: Int?
) {
    /** The contact's lookup address, which opens the contact and serves its photo. */
    val lookupUri: String
        get() = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)?.toString().orEmpty()
}

/**
 * Contacts' birthdays from their Birthday events (ContactsContract Event TYPE_BIRTHDAY). Reading them needs the
 * contacts permission the app already asks for contact search; without it there are none.
 */
object Birthdays {

    /** Today and the six days after: the week At a Glance looks ahead. */
    const val WINDOW_DAYS = 7
    const val MAX_SHOWN = 3

    /** A stored birthday: its month and day, and its year when the contact has one. */
    data class Parsed(val monthDay: MonthDay, val year: Int?)

    private val FULL = Regex("""^(\d{4})-?(\d{2})-?(\d{2})""")
    private val NO_YEAR = Regex("""^--(\d{2})-?(\d{2})""")

    /**
     * Reads the Event START_DATE formats contacts apps write: "1990-05-17" (also with a time after it), "19900517"
     * and "--05-17" for a birthday without a year. Anything else, or an impossible date, is null.
     */
    fun parse(raw: String?): Parsed? {
        val value = raw?.trim().orEmpty()
        NO_YEAR.find(value)?.let { m ->
            return monthDay(m.groupValues[1], m.groupValues[2])?.let { Parsed(it, null) }
        }
        FULL.find(value)?.let { m ->
            val year = m.groupValues[1].toInt()
            return monthDay(m.groupValues[2], m.groupValues[3])?.let { Parsed(it, year.takeIf { y -> y in 1850..2200 }) }
        }
        return null
    }

    private fun monthDay(month: String, day: String): MonthDay? = runCatching { MonthDay.of(month.toInt(), day.toInt()) }.getOrNull()

    /** The next date [birthday] falls on from [today] (today itself included); Feb 29 is kept on Feb 28 in other years. */
    fun nextOccurrence(birthday: MonthDay, today: LocalDate): LocalDate {
        fun inYear(year: Int) = if (birthday.isValidYear(year)) birthday.atYear(year) else LocalDate.of(year, 2, 28)
        val thisYear = inYear(today.year)
        return if (thisYear.isBefore(today)) inYear(today.year + 1) else thisYear
    }

    /** One stored birthday event. */
    data class Event(val contactId: Long, val lookupKey: String, val name: String, val startDate: String?)

    /** The birthdays among [events] from [today] through the next [WINDOW_DAYS] - 1 days, soonest first, one per contact. */
    fun inWindow(events: List<Event>, today: LocalDate): List<GlanceBirthday> =
        events.distinctBy { it.contactId }.mapNotNull { event ->
            val parsed = parse(event.startDate) ?: return@mapNotNull null
            val date = nextOccurrence(parsed.monthDay, today)
            val days = ChronoUnit.DAYS.between(today, date).toInt()
            if (days !in 0 until WINDOW_DAYS) return@mapNotNull null
            GlanceBirthday(
                contactId = event.contactId,
                lookupKey = event.lookupKey,
                name = event.name,
                daysAway = days,
                date = date,
                turning = parsed.year?.let { date.year - it }?.takeIf { it in 1..150 }
            )
        }.sortedWith(compareBy<GlanceBirthday> { it.daysAway }.thenBy { it.name.lowercase() }).take(MAX_SHOWN)

    /** Every Birthday event on the phone, or none without the contacts permission. Reads the provider: call off the main thread. */
    fun load(context: Context): List<Event> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val events = mutableListOf<Event>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.LOOKUP_KEY,
                    ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Event.START_DATE
                ),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
                ),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    events += Event(cursor.getLong(0), cursor.getString(1).orEmpty(), name, cursor.getString(3))
                }
            }
        }
        return events
    }
}
