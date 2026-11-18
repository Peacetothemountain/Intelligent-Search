package com.pixel.intelligentsearch.feature.tabs.wellbeing.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** How the page writes numbers, spans and times: Intelligent Search's one style (Durations.minutes and friends). */
object Fmt {
    /** "3 hr 12 min", "2 hr", "45 min", "Under 1 min": whole minutes, as screen time is counted. */
    fun dur(seconds: Number): String {
        val m = (seconds.toDouble() / 60.0).toLong().coerceAtLeast(0)
        if (m == 0L) return "Under 1 min"
        val h = m / 60
        val r = m % 60
        return when {
            h == 0L -> "$r min"
            r == 0L -> "$h hr"
            else -> "$h hr $r min"
        }
    }

    /** [dur] for a number of minutes (rounded). */
    fun durMin(minutes: Double): String = dur(minutes.roundToInt() * 60)

    /** "1,234". */
    fun num(n: Int): String = String.format(Locale.US, "%,d", n)

    /** "1 unlock", "62 unlocks". */
    fun pl(n: Int, word: String): String = "${num(n)} $word${if (n == 1) "" else "s"}"

    /** A time of day from minutes after midnight: "7:14 AM", or "07:14" on a 24-hour clock. */
    fun clock(minutes: Int, is24: Boolean): String {
        val t = ((minutes % 1440) + 1440) % 1440
        val h = t / 60
        val m = t % 60
        return if (is24) String.format(Locale.US, "%02d:%02d", h, m)
        else String.format(Locale.US, "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
    }

    /** "2:10 to 4:15 PM", "11:52 PM to 7:14 AM". */
    fun clockRange(a: Int, b: Int, is24: Boolean): String {
        val x = clock(a, is24)
        val y = clock(b, is24)
        return if (!is24 && x.takeLast(2) == y.takeLast(2)) "${x.dropLast(3)} to $y" else "$x to $y"
    }

    private fun h12(h: Int) = if (h % 12 == 0) 12 else h % 12
    private fun ap(h: Int) = if (h % 24 < 12) "AM" else "PM"

    /** An hour or a few: "7–8 PM", "11 AM–12 PM", or "19–20" on a 24-hour clock. */
    fun span(h: Int, n: Int = 1, is24: Boolean): String {
        val a = h % 24
        val b = (h + n) % 24
        if (is24) return String.format(Locale.US, "%02d–%02d", a, b)
        return if (ap(a) == ap(b)) "${h12(a)}–${h12(b)} ${ap(b)}" else "${h12(a)} ${ap(a)}–${h12(b)} ${ap(b)}"
    }

    /** [span] in words for sentences: "10 AM to noon", "8 to 10 PM". */
    fun spanWords(h: Int, n: Int, is24: Boolean): String {
        val b = (h + n) % 24
        if (is24) return String.format(Locale.US, "%02d:00 to %02d:00", h, b)
        fun w(x: Int) = when (x) { 12 -> "noon"; 0 -> "midnight"; else -> "${h12(x)} ${ap(x)}" }
        val same = (h < 12) == (b < 12) && b != 12 && b != 0 && h != 12 && h != 0
        return if (same) "${h12(h)} to ${w(b)}" else "${w(h)} to ${w(b)}"
    }

    fun weekday(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    fun weekday(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, Locale.US)
    fun weekdayShort(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    fun weekdayLetter(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW, Locale.US)
    fun month(d: LocalDate): String = d.month.getDisplayName(TextStyle.FULL, Locale.US)
    fun monthShort(d: LocalDate): String = d.month.getDisplayName(TextStyle.SHORT, Locale.US)

    /** "Nov 9". */
    fun shortDate(d: LocalDate): String = "${monthShort(d)} ${d.dayOfMonth}"

    /** Words a screen reader says for the page's units: "3 hours 42 minutes", "7 to 9 PM". */
    fun say(s: String): String = s
        .replace(Regex("\\b(\\d+) hr\\b")) { "${it.groupValues[1]} hour${if (it.groupValues[1] == "1") "" else "s"}" }
        .replace(Regex("\\b(\\d+) min\\b")) { "${it.groupValues[1]} minute${if (it.groupValues[1] == "1") "" else "s"}" }
        .replace("–", " to ")
        .replace(" · ", ", ")
}
