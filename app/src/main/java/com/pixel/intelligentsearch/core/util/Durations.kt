package com.pixel.intelligentsearch.core.util

import android.icu.text.RelativeDateTimeFormatter
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * How the app writes spans of time and times of day, one style everywhere: "1 hr 30 min", "45 sec", "In 6 hr 20 min",
 * "5 min ago", a stopwatch's "1:02:03", the phone's own time format ("7:30 AM", or "07:30" on a 24-hour clock), and
 * when an action is set for ("Tomorrow, 7:00 AM").
 */
object Durations {

    /** "15 min", "1 hr 30 min", "1 min 30 sec", "45 sec": each unit only when it isn't zero. */
    fun short(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return buildList {
            if (h > 0) add("$h hr")
            if (m > 0) add("$m min")
            if (s > 0 || (h == 0L && m == 0L)) add("$s sec")
        }.joinToString(" ")
    }

    /** A span counted in whole minutes, as screen time is: "3 hr 12 min", "2 hr", "45 min", "Under 1 min". */
    fun minutes(ms: Long): String {
        val minutes = ms.coerceAtLeast(0) / 60_000L
        return if (minutes == 0L) "Under 1 min" else short(minutes * 60)
    }

    /** A stopwatch-style reading: "0:05", "12:30", "1:02:03". */
    fun clock(ms: Long, locale: Locale = Locale.getDefault()): String {
        val total = ms.coerceAtLeast(0) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%d:%02d", m, s)
    }

    /** How long until something [ms] away, rounded up to the minute: "In 45 min", "In 2 hr", "In 6 hr 20 min". */
    fun until(ms: Long): String {
        val minutes = (ms.coerceAtLeast(0) + 59_999) / 60_000
        return "In ${short(minutes.coerceAtLeast(1) * 60)}"
    }

    /** How long ago something [ms] old came: "Now", "5 min ago", "2 hr ago", "Yesterday", "3 days ago". */
    fun ago(ms: Long): String {
        val minutes = ms.coerceAtLeast(0) / 60_000
        val days = minutes / (24 * 60)
        return when {
            minutes < 1 -> "Now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} hr ago"
            days == 1L -> "Yesterday"
            else -> "$days days ago"
        }
    }

    /**
     * A time of day as the phone writes it: the locale's short time ("7:30 AM", "19:30" in German) on a 12- or 24-hour
     * clock as the phone is set ([is24Hour], from android.text.format.DateFormat.is24HourFormat).
     */
    fun timeOfDay(hour24: Int, minute: Int, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String {
        val time = LocalTime.of(hour24.coerceIn(0, 23), minute.coerceIn(0, 59))
        return formatter(is24Hour, locale).format(time)
    }

    /**
     * When something set for [atMs] happens, as seen at [nowMs] in [zone]: "Today, 7:00 AM", "Tomorrow, 7:00 AM", "Fri,
     * Nov 13, 2:00 PM", and the year too when it isn't this one. An action's row says it, so the user sees what Enter
     * will set before setting it. The day is written as the phone's region writes it ("Fr., 13. Nov." in Germany,
     * "Heute" and "Morgen" for today and tomorrow), the time on its 12- or 24-hour clock ([is24Hour]).
     */
    fun dayAndTime(atMs: Long, nowMs: Long, is24Hour: Boolean, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(atMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val day = when (at.toLocalDate()) {
            today -> relativeDay(tomorrow = false, locale) ?: "Today"
            today.plusDays(1) -> relativeDay(tomorrow = true, locale) ?: "Tomorrow"
            else -> dateFormatter(withYear = at.year != today.year, locale).format(at)
        }
        return "$day, ${timeOfDay(at.hour, at.minute, is24Hour, locale)}"
    }

    /**
     * "Today" or "Tomorrow" in [locale]'s words, capitalized as a row starts. Typed nullable: in plain JVM tests the
     * stubbed formatter gives null, and the English words stand in.
     */
    private fun relativeDay(tomorrow: Boolean, locale: Locale): String? {
        val formatter: RelativeDateTimeFormatter? = RelativeDateTimeFormatter.getInstance(locale)
        val word: String? = formatter?.format(
            if (tomorrow) RelativeDateTimeFormatter.Direction.NEXT else RelativeDateTimeFormatter.Direction.THIS,
            RelativeDateTimeFormatter.AbsoluteUnit.DAY
        )
        return word?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.titlecase(locale) }
    }

    /** A weekday, month and day (and [withYear], the year) in the order and words [locale]'s region uses. */
    private fun dateFormatter(withYear: Boolean, locale: Locale): DateTimeFormatter {
        val pattern: String? = android.text.format.DateFormat.getBestDateTimePattern(locale, if (withYear) "EEEMMMdy" else "EEEMMMd")
        return pattern?.let { runCatching { DateTimeFormatter.ofPattern(it, locale) }.getOrNull() }
            ?: DateTimeFormatter.ofPattern(if (withYear) "EEE, MMM d, yyyy" else "EEE, MMM d", locale)
    }

    private fun formatter(is24Hour: Boolean, locale: Locale): DateTimeFormatter {
        val localized = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale)
        val usesTwelveHours = localized.contains('h') || localized.contains('K')
        if (is24Hour != usesTwelveHours) return DateTimeFormatter.ofPattern(localized, locale)
        return otherClockFormatter(is24Hour, locale)
            ?: DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)
    }

    /**
     * The time on the clock [locale] doesn't use by default, as its region writes it: Japanese 12-hour time puts the
     * period first ("午後11:30"), where a fixed "h:mm a" wrote "11:30 午後". Android's narrow space before the period
     * becomes a plain one, as on every other time the app writes. Null where Android has no pattern (plain JVM tests) or
     * java.time can't read it.
     */
    private fun otherClockFormatter(is24Hour: Boolean, locale: Locale): DateTimeFormatter? {
        val pattern: String? = android.text.format.DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm")
        return pattern?.let { runCatching { DateTimeFormatter.ofPattern(it.replace(' ', ' '), locale) }.getOrNull() }
    }
}
