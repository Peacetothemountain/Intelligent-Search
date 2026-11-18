package com.pixel.intelligentsearch.feature.tabs.wellbeing.model

import com.pixel.intelligentsearch.core.wellbeing.AiApps
import com.pixel.intelligentsearch.core.wellbeing.AppDay
import com.pixel.intelligentsearch.core.wellbeing.DaySummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class Range { Day, Week, Month }

/** What the page shows: a [range] and a day inside it. */
data class Period(val range: Range, val day: LocalDate)

/** Which way a number went against its usual. [None] when there is nothing to compare with yet; [New] for an app with no usual time at all. */
enum class Dir { Less, More, Same, None, New }

class Comparison(val dir: Dir, val diffMin: Int, val text: String, val perDay: Boolean = false)

/** The days the page knows, and the moment it is. */
class History(
    val days: Map<LocalDate, DaySummary>,
    val today: LocalDate,
    /** Minutes since today's midnight. */
    val nowMin: Double,
    val labels: Map<String, String>,
    val is24: Boolean = false,
    /** The phone's first day of the week (its region, or its own first-day setting): weeks and the recap start on it. */
    val weekStart: DayOfWeek = DayOfWeek.MONDAY
) {
    val first: LocalDate = days.keys.minOrNull() ?: today
    fun has(d: LocalDate) = !d.isAfter(today) && days.containsKey(d)
    fun day(d: LocalDate): DaySummary? = days[d]
    fun label(key: String) = labels[key] ?: AiApps.assistants[key] ?: key.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

/** The gauge's marks and captions. */
enum class Glyph { Mark, Stop }
class Caption(val label: String, val value: String, val glyph: Glyph?, val note: String = "")

/**
 * The usual day: the average whole day over the 4 weeks before a date, and what it was worked out from, in words.
 * [short]: there aren't 4 weeks of days yet, so the basis is worth saying where the figure is shown.
 */
class Usual(val days: List<LocalDate>, val sec: Double, val basis: String, val short: Boolean = false)

class HeroModel(
    val label: String,
    val valueSec: Int,
    val verdict: String,
    val dir: Dir,
    val markSec: Double?,
    val stopSec: Double?,
    val maxSec: Double,
    val wavy: Boolean,
    val captions: List<Caption>,
    /** False for a past period with nothing to compare with: it has no pace to show, only its own figure. */
    val gauge: Boolean = true
)

/**
 * An app in a list: [sec] is its time, or with [perDay] its average full day in the week or month (the same days the
 * hero's average day is from), [totalSec] its time in all.
 */
class AppRow(val key: String, val sec: Int, val delta: Comparison? = null, val perDay: Boolean = false, val totalSec: Int = sec)
class AppsModel(val head: String, val rows: List<AppRow>, val scoped: Boolean) {
    val max: Int get() = rows.firstOrNull()?.sec ?: 1
}

class Moment(val label: String, val value: String, val note: String = "")
class Sender(val key: String, val received: Int, val followed: Int)

/**
 * Everything the page says and draws, worked out from [History]: the same rules as the approved mockup
 * (parts/wellbeing.js), so the page, its comparisons and its recap can't disagree.
 */
class WellbeingModel(val h: History) {
    val today get() = h.today

    // ------------------------------------------------------------------ calendar
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(h.weekStart))
    fun weekDays(d: LocalDate) = (0L until 7L).map { weekStart(d).plusDays(it) }
    fun monthDays(d: LocalDate) = (1..d.lengthOfMonth()).map { d.withDayOfMonth(it) }
    fun allDays(p: Period) = when (p.range) { Range.Day -> listOf(p.day); Range.Week -> weekDays(p.day); Range.Month -> monthDays(p.day) }
    fun periodDays(p: Period) = allDays(p).filter { h.has(it) }
    private fun complete(ids: List<LocalDate>) = ids.filter { h.days[it]?.complete == true }
    // A day the records don't have reads as an empty day, never a crash (today has no record until the first read).
    private fun d(day: LocalDate) = h.days[day] ?: DaySummary.empty(day)
    private fun avg(xs: List<Double>) = if (xs.isEmpty()) 0.0 else xs.sum() / xs.size
    private fun dayAvg(ids: List<LocalDate>): Double? = complete(ids.filter { h.has(it) }).takeIf { it.isNotEmpty() }?.let { f -> avg(f.map { d(it).totalSec.toDouble() }) }

    fun isCurrent(p: Period) = when (p.range) {
        Range.Day -> p.day == today
        Range.Week -> weekStart(p.day) == weekStart(today)
        Range.Month -> p.day.withDayOfMonth(1) == today.withDayOfMonth(1)
    }

    fun title(p: Period): String = when (p.range) {
        Range.Day -> when (p.day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> "${Fmt.weekday(p.day)}, ${Fmt.shortDate(p.day)}" }
        Range.Month -> Fmt.month(p.day) + if (p.day.year != today.year) " ${p.day.year}" else ""
        Range.Week -> {
            val s = weekStart(p.day)
            val t = weekStart(today)
            when (s) {
                t -> "This Week"
                t.minusDays(7) -> "Last Week"
                else -> { val e = s.plusDays(6); if (s.month == e.month) "${Fmt.shortDate(s)} – ${e.dayOfMonth}" else "${Fmt.shortDate(s)} – ${Fmt.shortDate(e)}" }
            }
        }
    }

    /** The period in words, for sentences: "today", "on Thursday", "this week", "in October". */
    fun whenWords(p: Period): String = when (p.range) {
        Range.Day -> when (p.day) { today -> "today"; today.minusDays(1) -> "yesterday"; else -> "on ${Fmt.weekday(p.day)}" }
        Range.Week -> if (isCurrent(p)) "this week" else if (title(p) == "Last Week") "last week" else "that week"
        Range.Month -> if (isCurrent(p)) "this month" else "in ${Fmt.month(p.day)}"
    }

    /** Where the previous or next stepper lands, or null when nothing is kept there. */
    fun stepTarget(p: Period, dir: Int): LocalDate? = when (p.range) {
        Range.Day -> generateSequence(p.day.plusDays(dir.toLong())) { it.plusDays(dir.toLong()) }
            .takeWhile { !it.isAfter(today) && !it.isBefore(h.first) }.firstOrNull { h.has(it) }
        Range.Week -> {
            val s = weekStart(p.day).plusDays(7L * dir)
            if (s.isAfter(today) || s.plusDays(6).isBefore(h.first)) null
            else p.day.plusDays(7L * dir).coerceIn(h.first, today)
        }
        Range.Month -> {
            val m = p.day.withDayOfMonth(1).plusMonths(dir.toLong())
            if (m.isAfter(today) || m.plusMonths(1).minusDays(1).isBefore(h.first)) null
            else m.withDayOfMonth(minOf(p.day.dayOfMonth, m.lengthOfMonth())).coerceIn(h.first, today)
        }
    }

    // ------------------------------------------------------------------ "your usual day"
    /**
     * Your usual day, the one figure the page compares with everywhere: the average whole day over the 4 weeks before
     * [before] (it needs 3 whole days), with its basis in words: "From 3 days so far", or "The 4 weeks before today".
     */
    fun usual(before: LocalDate): Usual? {
        val pool = (1L..28L).map { before.minusDays(it) }.filter { !it.isBefore(h.first) && h.days[it]?.complete == true }
        if (pool.size < 3) return null
        val short = h.first.isAfter(before.minusDays(28))
        val basis = when {
            short && !before.isBefore(today.minusDays(1)) -> "From ${Fmt.pl(pool.size, "day")} so far"
            short -> "From ${Fmt.pl(pool.size, "day")} before ${beforeWords(before)}"
            else -> "The 4 weeks before ${beforeWords(before)}"
        }
        return Usual(pool, avg(pool.map { d(it).totalSec.toDouble() }), basis, short)
    }

    /** The days your usual day before [day] is worked out from. */
    fun usualDays(day: LocalDate): List<LocalDate>? = usual(day)?.days

    private fun beforeWords(d: LocalDate) = if (d == today) "today" else Fmt.shortDate(d)

    /**
     * Where a period's usual is measured up to: today for the period running now, so today, this week and this month
     * all compare with the one usual day; for a past period, the day itself or the first day of the week or month.
     */
    fun usualFrom(p: Period): LocalDate = if (isCurrent(p)) today else when (p.range) {
        Range.Day -> p.day
        Range.Week -> weekStart(p.day)
        Range.Month -> p.day.withDayOfMonth(1)
    }

    /**
     * The month running now is not measured against your usual day: the 4 weeks that day is worked out from are mostly
     * the month's own days, so the two can't differ by much and the comparison would say nothing. It still shows it.
     */
    private fun ownUsual(p: Period) = p.range == Range.Month && isCurrent(p)

    /** Seconds on screen by this time of day, for a usual day's running total. */
    fun byNow(s: DaySummary): Double {
        val hr = (h.nowMin / 60).toInt().coerceIn(0, 23)
        val frac = h.nowMin / 60 - hr
        return (0 until hr).sumOf { s.hours[it] }.toDouble() + s.hours[hr] * frac
    }

    /** For the period running now: days it takes before "your usual day" exists, in words. */
    fun usualSoon(p: Period): String {
        val from = usualFrom(p)
        val have = (1L..28L).map { from.minusDays(it) }.count { !it.isBefore(h.first) && h.days[it]?.complete == true }
        val need = 3 - have
        return when {
            isCurrent(p) && need == 1 -> "Your usual day shows up here tomorrow."
            isCurrent(p) && need > 1 -> "Your usual day shows up here in $need days."
            else -> "Too few full days before ${whenWords(p).removePrefix("on ").removePrefix("in ")} to compare with."
        }
    }

    /** A past day with nothing to compare with: where it falls in the record ("Your first day on record."). */
    private fun onRecord(day: LocalDate): String = when (h.days.keys.count { it.isBefore(day) }) {
        0 -> "Your first day on record."
        1 -> "Your second day on record."
        2 -> "Your third day on record."
        else -> "Too few full days before it to compare with."
    }

    /**
     * Three states, by the larger of 10% or 10 minutes. Never praise or blame. The difference is taken between the
     * whole minutes the page shows ([Fmt.dur] counts whole minutes), so "more" can never be more than the time shown.
     */
    fun compare(valSec: Double, baseSec: Double, less: (String) -> String, more: (String) -> String, same: String): Comparison {
        val diff = wholeMin(valSec) - wholeMin(baseSec)
        val big = abs(diff) > max(10.0, baseSec / 60 * 0.1)
        val dir = if (!big) Dir.Same else if (diff < 0) Dir.Less else Dir.More
        val text = if (!big) same else if (diff < 0) less(Fmt.durMin(abs(diff).toDouble())) else more(Fmt.durMin(abs(diff).toDouble()))
        return Comparison(dir, diff, text)
    }

    private fun wholeMin(sec: Double): Int = kotlin.math.floor(max(0.0, sec) / 60).toInt()

    // ------------------------------------------------------------------ hero
    /** The basis of the usual day, said only while there aren't 4 weeks of days yet ("From 10 days so far"). */
    private fun basisNote(u: Usual) = if (u.short) u.basis else ""

    /**
     * The hero: the period's figure and one sentence about it. Against your usual day once there is one; before then,
     * for the period running now, when the usual day arrives, and for a past period, a fact about the period itself.
     */
    fun hero(p: Period): HeroModel {
        val wavy = isCurrent(p)
        fun none(label: String, v: Int, text: String, mark: Double? = null, caps: List<Caption> = emptyList(), gauge: Boolean = true): HeroModel {
            val maxS = if (mark != null) max(mark, v.toDouble()) * 1.1 else max(3600.0, ceil((v + 60) / 3600.0) * 3600.0)
            return HeroModel(label, v, text, Dir.None, mark, null, maxS, wavy, caps, gauge)
        }
        val u = usual(usualFrom(p))
        if (p.range == Range.Day) {
            val s = d(p.day)
            val isToday = p.day == today
            val label = if (isToday) "So Far Today" else Fmt.weekday(p.day)
            if (isToday && s.totalSec < 60) return none(label, s.totalSec, "Nothing yet today.")
            if (u == null) return if (isToday) none(label, s.totalSec, usualSoon(p)) else none(label, s.totalSec, onRecord(p.day), gauge = false)
            val full = u.sec
            val now = if (isToday) avg(u.days.map { byNow(d(it)) }) else full
            val tail = if (isToday) " by now" else ""
            val c = compare(s.totalSec.toDouble(), now, { "$it less than your usual day$tail." }, { "$it more than your usual day$tail." }, "About your usual day$tail.")
            val maxS = if (isToday) (if (full >= s.totalSec) full else s.totalSec * 1.1) else max(full, s.totalSec.toDouble()) * 1.1
            val caps = if (isToday) listOf(Caption("Usual by Now", Fmt.dur(now), Glyph.Mark), Caption("Your Usual Day", Fmt.dur(full), Glyph.Stop, basisNote(u)))
            else listOf(Caption("Your Usual Day", Fmt.dur(full), Glyph.Mark, basisNote(u)))
            return HeroModel(label, s.totalSec, c.text, c.dir, now, if (isToday) full else null, max(maxS, 60.0), wavy, caps)
        }
        val unit = p.range.name.lowercase()
        val cur = isCurrent(p)
        val ids = periodDays(p)
        val fullDays = complete(ids)
        val full = fullDays.size
        val usualCap = u?.let { listOf(Caption("Your Usual Day", Fmt.dur(it.sec), Glyph.Mark, basisNote(it))) }.orEmpty()
        if (cur && full == 0) {
            val v = ids.sumOf { d(it).totalSec }
            return none("So Far", v, "Your $unit has just started.", u?.sec, usualCap)
        }
        val v = (dayAvg(ids) ?: 0.0).roundToInt()
        if (u == null) {
            if (cur) return none("Average Day", v, usualSoon(p))
            // A past week or month from before there was a usual day: what it holds, not how the comparison works.
            if (full == 0) return none("Average Day", 0, "No full days on record ${whenWords(p)}.", gauge = false)
            if (full == 1) {
                val one = fullDays[0]
                return none(Fmt.weekday(one) + if (p.range == Range.Month) ", ${Fmt.shortDate(one)}" else "", v, "The only full day on record ${whenWords(p)}.", gauge = false)
            }
            val total = fullDays.sumOf { d(it).totalSec }
            val text = if (full == allDays(p).size) "${Fmt.dur(total)} in all." else "${Fmt.dur(total)} over ${Fmt.pl(full, "full day")} on record."
            return none("Average Day", v, text, gauge = false)
        }
        val caps = usualCap + listOfNotNull(if (cur) Caption("So Far", Fmt.pl(full, "full day"), null) else null)
        if (ownUsual(p)) {
            // What the month holds so far, with your usual day beside it.
            val total = fullDays.sumOf { d(it).totalSec }
            return HeroModel("Average Day", v, "${Fmt.dur(total)} in all so far.", Dir.None, u.sec, null, max(u.sec, v.toDouble()) * 1.1, wavy, caps)
        }
        val c = compare(v.toDouble(), u.sec, { "$it a day less than your usual day." }, { "$it a day more than your usual day." }, "About your usual day.")
        return HeroModel("Average Day", v, c.text, c.dir, u.sec, null, max(u.sec, v.toDouble()) * 1.1, wavy, caps)
    }

    // ------------------------------------------------------------------ chart
    /** The 2-hour window with the most time: its first hour. */
    fun busiest2(a: IntArray): Int { var b = 0; for (x in 0 until 23) if (a[x] + a[x + 1] > a[b] + a[b + 1]) b = x; return b }
    fun busiest(a: IntArray): Int = a.indices.maxByOrNull { a[it] } ?: 0

    /** The phone-down night before [day] when it lasted at least 3 hours: start (minutes, may be negative) and end. */
    fun night(day: LocalDate): Pair<Int, Int>? {
        val s = h.days[day] ?: return null
        val pd = s.phoneDownStartMin ?: return null
        val fu = s.firstUseMin ?: return null
        return if ((s.phoneDownLenMin ?: (fu - pd)) >= 180) pd to fu else null
    }

    /** How long that night was, in real minutes: on a night the clocks change it isn't the difference on the clock. */
    fun nightLength(day: LocalDate): Int? = night(day)?.let { (pd, fu) -> h.days[day]?.phoneDownLenMin ?: (fu - pd) }

    /** Median time the phone went down over the 14 nights before [day] (needs 5). */
    fun usualDown(day: LocalDate): Int? {
        val v = generateSequence(day.minusDays(1)) { it.minusDays(1) }.takeWhile { !it.isBefore(h.first) }
            .mapNotNull { h.days[it]?.phoneDownStartMin }.take(14).toList().sorted()
        if (v.size < 5) return null
        val m = v.size / 2
        return if (v.size % 2 == 1) v[m] else (v[m - 1] + v[m]) / 2
    }

    /** The week chart's dashed line: your usual day, the same figure the hero compares the week with. */
    fun weekUsual(p: Period): Double? = usual(usualFrom(p))?.sec

    fun lightest4(lo: LocalDate): Boolean {
        if (lo.toEpochDay() - h.first.toEpochDay() < 21) return false
        val t = d(lo).totalSec
        return (1L until 28L).map { lo.minusDays(it) }.filter { h.has(it) }.none { d(it).totalSec <= t }
    }

    fun weekendGap(ids: List<LocalDate>): Double? {
        val f = complete(ids)
        val we = f.filter { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
        val wk = f - we.toSet()
        if (we.size < 4 || wk.isEmpty()) return null
        val gap = avg(we.map { d(it).totalSec / 60.0 }) - avg(wk.map { d(it).totalSec / 60.0 })
        return if (gap >= 30) gap else null
    }

    /** The readout's resting line: the day's facts, or the period's. */
    fun restingLine(p: Period): String = when (p.range) {
        Range.Day -> {
            val s = d(p.day)
            // After a night, its end is when the phone was picked up again: the day may have begun well before it (up past
            // midnight), so it isn't called the first use.
            val start = when {
                night(p.day) != null -> s.firstUseMin?.let { "Picked up ${Fmt.clock(it, h.is24)}" }
                s.inUseAtMidnight -> "In use since before midnight"
                else -> s.firstUseMin?.let { "First use ${Fmt.clock(it, h.is24)}" }
            }
            listOfNotNull(start, if (p.day == today) s.firstApp?.let { h.label(it) } else null)
                .joinToString(" · ").ifEmpty { if (p.day == today) "Nothing on screen yet." else if (s.totalSec < 60) "Nothing on screen." else "" }
        }
        Range.Week -> {
            val f = complete(periodDays(p))
            // A lightest day needs a few days to be lighter than.
            if (f.size >= 3) {
                val lo = f.minBy { d(it).totalSec }
                "Lightest day${if (lightest4(lo)) " in 4 weeks" else ""}: ${Fmt.weekday(lo)}, ${Fmt.dur(d(lo).totalSec)}"
            } else if (f.isEmpty() && isCurrent(p)) "Today is the first day of this week." else ""
        }
        Range.Month -> {
            val g = weekendGap(periodDays(p))
            if (g != null) "Weekends run about ${if (g in 45.0..75.0) "an hour" else Fmt.durMin((g / 5).roundToInt() * 5.0)} longer than weekdays."
            else "${Fmt.pl(complete(periodDays(p)).size, "full day")} recorded."
        }
    }

    fun moments(p: Period): List<Moment> {
        val out = ArrayList<Moment>()
        val ids = periodDays(p)
        val f = complete(ids)
        when (p.range) {
            Range.Day -> {
                val s = d(p.day)
                val isToday = p.day == today
                val n = night(p.day)
                // The night, said once: when, how long, and today against when the phone usually goes down.
                if (n != null) {
                    val us = if (isToday) usualDown(p.day) else null
                    val df = if (us == null) 0 else n.first - us
                    val vs = if (abs(df) >= 10) " · ${Fmt.durMin(abs(df).toDouble())} ${if (df > 0) "later" else "earlier"} than usual" else ""
                    out += Moment("Phone Down", Fmt.clockRange(n.first, n.second, h.is24), Fmt.durMin((nightLength(p.day) ?: 0).toDouble()) + vs)
                }
                // A look or two at the phone inside the night: said, not hidden in a night that reads as unbroken.
                if (n != null && s.nightPickups > 0) out += Moment(
                    "Picked Up Overnight", when (s.nightPickups) { 1 -> "Once"; 2 -> "Twice"; else -> Fmt.pl(s.nightPickups, "time") },
                    "${Fmt.dur(s.nightUseSec)} on screen"
                )
                if (!isToday && s.totalSec > 0) { val b = busiest(s.hours); out += Moment("Busiest Hour", Fmt.span(b, 1, h.is24), Fmt.dur(s.hours[b])) }
                if (s.longestOnStartMin != null && s.longestOnLenMin != null && s.longestOnLenMin >= 10) out += Moment("Longest Stretch on Screen", Fmt.durMin(s.longestOnLenMin.toDouble()), Fmt.clockRange(s.longestOnStartMin, s.longestOnStartMin + s.longestOnLenMin, h.is24))
                if (s.awayStartMin != null && s.awayLenMin != null && s.awayLenMin >= 15) out += Moment("Longest Stretch Away", Fmt.durMin(s.awayLenMin.toDouble()), Fmt.clockRange(s.awayStartMin, s.awayStartMin + s.awayLenMin, h.is24))
                // A day still in use as it ended has no last use to name: the next day begins "in use since before midnight".
                if (!isToday && s.lastUseMin != null) out += if (s.lastUseMin >= 1440) Moment("Last Use", "Past Midnight", "Still on screen as the day ended") else Moment("Last Use", Fmt.clock(s.lastUseMin, h.is24))
            }
            Range.Week -> {
                val we = f.filter { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
                val wk = f - we.toSet()
                if (wk.isNotEmpty()) out += Moment("Weekdays", Fmt.dur(avg(wk.map { d(it).totalSec.toDouble() })), "a day")
                if (we.isNotEmpty()) out += Moment("Weekend", Fmt.dur(avg(we.map { d(it).totalSec.toDouble() })), if (we.size < 2) "${Fmt.weekday(we[0])} only${if (isCurrent(p)) " so far" else ""}" else "a day")
                if (f.size == 7) { val hrs = IntArray(24) { x -> f.sumOf { d(it).hours[x] } }; out += Moment("Busiest Hours", Fmt.span(busiest2(hrs), 2, h.is24)) }
            }
            Range.Month -> if (f.isNotEmpty()) {
                val lo = f.minBy { d(it).totalSec }
                val hi = f.maxBy { d(it).totalSec }
                out += Moment("Lightest Day", Fmt.dur(d(lo).totalSec), "${Fmt.weekdayShort(lo)}, ${Fmt.shortDate(lo)}")
                out += Moment("Longest Day", Fmt.dur(d(hi).totalSec), "${Fmt.weekdayShort(hi)}, ${Fmt.shortDate(hi)}")
                out += Moment("Unlocks", "${avg(f.map { d(it).unlockTotal.toDouble() }).roundToInt()} a day")
                out += Moment("Notifications", "${avg(f.map { d(it).notificationTotal.toDouble() }).roundToInt()} a day")
            }
        }
        return out
    }

    // ------------------------------------------------------------------ apps
    private fun appOf(day: LocalDate, k: String): AppDay? = h.days[day]?.apps?.get(k)
    private fun appSec(ids: List<LocalDate>, k: String) = ids.sumOf { appOf(it, k)?.totalSec ?: 0 }

    /**
     * One app against its own usual, over the same days as the hero's usual day. An app with no time at all on those
     * days is [Dir.New] ("New today"), not "more than usual" by its whole time.
     */
    fun appDelta(p: Period, k: String): Comparison {
        fun secOf(day: LocalDate, now: Boolean): Double = appOf(day, k)?.let { a ->
            if (now) { val hr = (h.nowMin / 60).toInt().coerceIn(0, 23); (0 until hr).sumOf { a.hours[it] } + a.hours[hr] * (h.nowMin / 60 - hr) } else a.totalSec.toDouble()
        } ?: 0.0
        val u = usual(usualFrom(p)) ?: return Comparison(Dir.None, 0, usualSoon(p))
        if (ownUsual(p)) return Comparison(Dir.None, 0, "")
        val newText = "New ${whenWords(p)}."
        if (p.range == Range.Day) {
            val isToday = p.day == today
            val tail = if (isToday) " by now" else ""
            val v = secOf(p.day, false)
            if (v >= 60 && u.days.all { (appOf(it, k)?.totalSec ?: 0) < 60 }) return Comparison(Dir.New, 0, newText)
            return compare(v, avg(u.days.map { secOf(it, isToday) }), { "$it less than your usual day$tail." }, { "$it more than your usual day$tail." }, "About your usual day$tail.")
        }
        val ids = complete(periodDays(p))
        if (ids.isEmpty()) return Comparison(Dir.None, 0, usualSoon(p))
        val a = avg(ids.map { secOf(it, false) / 60 })
        val b = avg(u.days.map { secOf(it, false) / 60 })
        if (b < 1 && a >= 1) return Comparison(Dir.New, 0, newText, perDay = true)
        // Whole minutes, as shown; an app's day has to move by 30 minutes a week and a quarter before it is worth a word.
        val diff = kotlin.math.floor(a).toInt() - kotlin.math.floor(b).toInt()
        val big = abs(diff) * 7 >= 30 && abs(a - b) >= 0.25 * max(a, b)
        val dir = if (!big) Dir.Same else if (diff < 0) Dir.Less else Dir.More
        return Comparison(dir, diff, if (!big) "About your usual day." else "${Fmt.durMin(abs(diff).toDouble())} a day ${if (diff < 0) "less" else "more"} than your usual day.", perDay = true)
    }

    fun selectedDay(p: Period, sel: Int): LocalDate = if (p.range == Range.Week) weekDays(p.day)[sel] else p.day.withDayOfMonth(sel + 1)

    /** Most used: the period's apps, or with a bar selected, that hour's or that day's. */
    fun apps(p: Period, sel: Int?): AppsModel {
        val rows: List<AppRow>
        var head = "Most Used"
        if (sel != null && p.range == Range.Day) {
            rows = d(p.day).apps.map { (k, a) -> AppRow(k, a.hours[sel]) }
            head += " · ${Fmt.span(sel, 1, h.is24)}"
        } else if (sel != null) {
            val day = selectedDay(p, sel)
            rows = h.days[day]?.apps?.map { (k, a) -> AppRow(k, a.totalSec) }.orEmpty()
            head += " · ${Fmt.weekday(day)}"
        } else {
            val ids = periodDays(p)
            val keys = ids.flatMap { d(it).apps.keys }.toSet()
            val full = complete(ids)
            rows = if (p.range == Range.Day || full.isEmpty()) keys.map { AppRow(it, appSec(ids, it), appDelta(p, it)) }
            // A week or a month: each app's average full day, in the same unit as the hero's and its change.
            else keys.map { k -> AppRow(k, (appSec(full, k).toDouble() / full.size).roundToInt(), appDelta(p, k), perDay = true, totalSec = appSec(ids, k)) }
        }
        return AppsModel(head, rows.filter { it.sec >= 60 }.sortedWith(compareByDescending<AppRow> { it.sec }.thenBy { h.label(it.key) }), sel != null)
    }

    class Panel(
        val sentence: String, val series: IntArray, val axis: List<String>, val described: String, val opens: Int, val longestSec: Int, val received: Int, val followed: Int,
        /** Time in the app for each time it was opened, or 0 when it wasn't opened. */
        val averageVisitSec: Int = 0,
        /** The app's time in the whole period so far. */
        val totalSec: Int = 0,
        /** Whether [sentence] says something the row doesn't already (it is about your usual day, and the row shows no change). */
        val sentenceShown: Boolean = true
    ) {
        /** The chart's top: the largest bar rounded up to a clean value ("60 min", not "58 min"). */
        val topSec: Int get() = niceTop(series.maxOrNull() ?: 0)
    }

    fun panel(p: Period, k: String): Panel {
        val ids = periodDays(p)
        val name = h.label(k)
        val series: IntArray
        val axis: List<String>
        val what: String
        when (p.range) {
            Range.Day -> {
                series = appOf(p.day, k)?.hours?.copyOf() ?: IntArray(24)
                axis = if (h.is24) listOf("00", "12") else listOf("12 AM", "12 PM")
                what = "$name by hour: mostly ${Fmt.spanWords(busiest2(series), 2, h.is24)}."
            }
            Range.Week -> {
                series = weekDays(p.day).map { appOf(it, k)?.totalSec ?: 0 }.toIntArray()
                axis = weekDays(p.day).map { Fmt.weekdayLetter(it.dayOfWeek) }
                what = "$name by day."
            }
            Range.Month -> {
                series = monthDays(p.day).map { appOf(it, k)?.totalSec ?: 0 }.toIntArray()
                axis = listOf("${Fmt.monthShort(p.day)} 1", "${Fmt.monthShort(p.day)} ${p.day.lengthOfMonth() / 2 + 1}")
                what = "$name by day."
            }
        }
        val opens = ids.sumOf { appOf(it, k)?.opens ?: 0 }
        val delta = appDelta(p, k)
        return Panel(
            delta.text, series, axis, what,
            opens, ids.maxOfOrNull { appOf(it, k)?.longestSec ?: 0 } ?: 0,
            ids.sumOf { appOf(it, k)?.notifications ?: 0 }, ids.sumOf { appOf(it, k)?.followed ?: 0 },
            averageVisitSec = if (opens > 0) appSec(ids, k) / opens else 0,
            totalSec = appSec(ids, k),
            sentenceShown = delta.dir == Dir.Same
        )
    }

    // ------------------------------------------------------------------ AI apps
    class Ai(
        val totalSec: Int, val pct: Int, val rows: List<AppRow>, val othersSec: Int, val strip: IntArray?, val facts: List<String>, val stripDescribed: String
    )

    fun ai(p: Period): Ai? {
        val ids = periodDays(p)
        val total = ids.sumOf { d(it).totalSec }
        val rows = ids.flatMap { d(it).apps.keys }.toSet().filter { AiApps.isAi(it) }
            .map { AppRow(it, appSec(ids, it)) }.filter { it.sec >= 60 }.sortedByDescending { it.sec }
        val ai = rows.sumOf { it.sec }
        if (ai < 60) return null
        // Rounded as it is: a share under half a percent is 0, which the page says as "under 1%".
        val pct = (ai * 100.0 / max(1, total)).roundToInt().coerceIn(0, 100)
        val top = rows.take(4)
        val visits = ids.sumOf { day -> d(day).apps.filterKeys { AiApps.isAi(it) }.values.sumOf { it.opens } }
        val long = ids.sumOf { day -> d(day).apps.filterKeys { AiApps.isAi(it) }.values.sumOf { it.over15 } }
        val hrs = IntArray(24) { x -> ids.sumOf { day -> d(day).apps.filterKeys { AiApps.isAi(it) }.values.sumOf { it.hours[x] } } }
        val facts = ArrayList<String>()
        if (visits > 0) facts += "${Fmt.pl(visits, "visit")}, about ${max(1, (ai / 60.0 / visits).roundToInt())} min each${if (long > 0) " · ${Fmt.num(long)} over 15 min" else ""}"
        var strip: IntArray? = null
        var described = ""
        when (p.range) {
            Range.Day -> { strip = hrs; described = "AI apps by hour. Mostly ${Fmt.spanWords(busiest2(hrs), 2, h.is24)}." }
            Range.Week -> { strip = weekDays(p.day).map { day -> h.days[day]?.apps?.filterKeys { AiApps.isAi(it) }?.values?.sumOf { it.totalSec } ?: 0 }.toIntArray(); described = "AI apps by day." }
            Range.Month -> {}
        }
        if (p.range != Range.Day) {
            // "Mostly" only when one two-hour window holds at least 35% of at least 30 minutes.
            val b = busiest2(hrs)
            if (ai >= 1800 && hrs[b] + hrs[b + 1] >= 0.35 * ai) facts += "Mostly ${Fmt.spanWords(b, 2, h.is24)}"
        }
        if (p.range == Range.Week && !isCurrent(p)) {
            val prev = (1..4).map { n -> weekDays(p.day.minusWeeks(n.toLong())) }.filter { w -> w.all { h.days[it]?.complete == true } }
                .map { w -> w.sumOf { day -> d(day).apps.filterKeys { AiApps.isAi(it) }.values.sumOf { it.totalSec } } / 60.0 }
            if (prev.size >= 2) {
                val base = avg(prev)
                val df = ai / 60.0 - base
                if (abs(df) >= 20 && abs(df) >= 0.3 * base) facts += "${Fmt.durMin(abs(df))} ${if (df > 0) "more" else "less"} than your usual week"
            }
        }
        return Ai(ai, pct, top, rows.drop(4).sumOf { it.sec }, strip, facts, described)
    }

    // ------------------------------------------------------------------ interruptions
    fun senders(ids: List<LocalDate>): Map<String, Sender> {
        val r = HashMap<String, IntArray>()
        for (day in ids) for ((k, a) in h.days[day]?.apps.orEmpty()) if (a.notifications > 0) {
            val x = r.getOrPut(k) { IntArray(2) }; x[0] += a.notifications; x[1] += a.followed
        }
        return r.mapValues { (k, v) -> Sender(k, v[0], v[1]) }
    }

    class Interruptions(
        val received: Int, val followed: Int, val whileDown: Int, val top: List<Sender>, val insight: Sender?,
        val unlocks: Int, val after: Int, val firstApp: String?, val firstIn10: Int,
        /** The notifications and the unlocks against your usual day's ("" until there is a usual day). */
        val receivedNote: String = "", val unlockNote: String = "",
        /** Where the day's unlocks were, or how many a day the period had ("" when there is nothing to say). */
        val unlockWhen: String = ""
    )

    /**
     * A count (unlocks, notifications) against your usual day's, by the larger of a fifth or [floor]: today's against
     * your usual day by this time, a week's or a month's full days against your usual day. "" until there is a usual day.
     */
    fun countNote(p: Period, floor: Int, of: (DaySummary) -> IntArray): String {
        val u = usual(usualFrom(p)) ?: return ""
        if (ownUsual(p)) return ""
        val isToday = p.range == Range.Day && p.day == today
        fun count(day: LocalDate, byNow: Boolean): Double {
            val a = of(d(day))
            if (!byNow) return a.sum().toDouble()
            val hr = (h.nowMin / 60).toInt().coerceIn(0, 23)
            return (0 until hr).sumOf { a[it] } + a[hr] * (h.nowMin / 60 - hr)
        }
        val perDay = p.range != Range.Day
        val value: Double
        if (perDay) {
            val ids = complete(periodDays(p))
            if (ids.isEmpty()) return ""
            value = avg(ids.map { count(it, false) })
        } else value = count(p.day, false)
        val base = avg(u.days.map { count(it, isToday) })
        val diff = value.roundToInt() - base.roundToInt()
        val tail = if (isToday) " by now" else ""
        if (abs(diff) < max(floor.toDouble(), base * 0.2)) return "About your usual day$tail."
        return "${Fmt.num(abs(diff))}${if (perDay) " a day" else ""} ${if (diff < 0) "fewer" else "more"} than your usual day$tail."
    }

    fun interruptions(p: Period, leastFollowed: Boolean): Interruptions {
        val ids = periodDays(p)
        val all = senders(ids).values.toList()
        val minR = if (p.range == Range.Day) 5 else 20
        val sorted = if (leastFollowed) all.filter { it.received >= minR }.sortedWith(compareBy<Sender> { it.followed.toDouble() / it.received }.thenByDescending { it.received })
        else all.sortedByDescending { it.received }
        val unlocks = ids.sumOf { d(it).unlockTotal }
        val after = ids.sumOf { d(it).unlocksAfterNotification }
        val tally = HashMap<String, Int>()
        ids.forEach { day -> d(day).firstAfterOwnUnlock.forEach { (k, v) -> tally.merge(k, v, Int::plus) } }
        val ownWithApp = tally.values.sum()
        val lead = tally.maxByOrNull { it.value }
        val n10 = if (lead != null && ownWithApp > 0) (lead.value * 10.0 / ownWithApp).roundToInt() else 0
        // The one insight here with a fix: 20 or more in the last 7 days and the app never opened after any.
        var insight: Sender? = null
        if (isCurrent(p) && !today.minusDays(6).isBefore(h.first)) {
            insight = senders((0L until 7L).map { today.minusDays(it) }).values.filter { it.received >= 20 && it.followed == 0 }.maxByOrNull { it.received }
        }
        val whileDown = if (p.range == Range.Day && night(p.day) != null) d(p.day).notificationsWhileDown else 0
        // Where the unlocks were: the day's busiest hour once it stands out, or a week's or a month's number a day.
        val unlockWhen = if (p.range == Range.Day) {
            val u = d(p.day).unlocks
            val b = busiest(u)
            if (unlocks >= 10 && u[b] >= 4) "Most in one hour: ${Fmt.num(u[b])}, ${Fmt.span(b, 1, h.is24)}." else ""
        } else complete(ids).takeIf { it.isNotEmpty() }?.let { f -> "${Fmt.num(avg(f.map { d(it).unlockTotal.toDouble() }).roundToInt())} a day on full days." }.orEmpty()
        return Interruptions(
            all.sumOf { it.received }, all.sumOf { it.followed }, whileDown, sorted.take(3), insight, unlocks, after,
            if (ownWithApp >= 10 && n10 >= 3) lead?.key else null, n10,
            receivedNote = countNote(p, 10) { it.notifications }, unlockNote = countNote(p, 5) { it.unlocks }, unlockWhen = unlockWhen
        )
    }

    // ------------------------------------------------------------------ weekly recap
    /** The last full week, from the phone's first day of the week. */
    val recapWeek: List<LocalDate> get() = weekDays(weekStart(today).minusDays(7))
    val recapReady: Boolean get() = recapWeek.all { h.days[it]?.complete == true }

    /** When the first recap comes, for a short history. */
    fun recapReadyOn(): LocalDate {
        val ws = weekStart(h.first)
        val firstFull = if (ws == h.first) h.first else ws.plusDays(7)
        return firstFull.plusDays(7)
    }

    fun recapName(): String { val w = recapWeek; return title(Period(Range.Week, w[0])).let { if (it == "Last Week") "${Fmt.shortDate(w[0])} – ${w[6].dayOfMonth}".let { s -> if (w[0].month == w[6].month) s else "${Fmt.shortDate(w[0])} – ${Fmt.shortDate(w[6])}" } else it } }

    class Recap(
        val name: String, val avgSec: Int, val totalSec: Int, val cmp: Comparison, val daysSec: List<Int>, val lightest: LocalDate,
        val lightest4: Boolean, val lightestDay: DaySummary, val topOpen: String?, val opens: Map<String, Int>, val time: Map<String, Int>, val topTime: String?,
        val aiSec: Int, val aiPct: Int, val aiRows: List<AppRow>, val aiVisits: Int, val aiMostly: String, val aiVsUsual: String,
        val received: Int, val followed: Int, val senders: List<Sender>, val never: Sender?, val nextOn: LocalDate
    )

    fun recap(): Recap {
        val w = recapWeek
        val avgW = dayAvg(w) ?: 0.0
        val avgP = dayAvg(weekDays(w[0].minusDays(7))).takeIf { complete(weekDays(w[0].minusDays(7))).size >= 3 }
        val cmp = if (avgP == null) Comparison(Dir.None, 0, "Your first full week.")
        else compare(avgW, avgP, { "$it a day less than the week before." }, { "$it a day more than the week before." }, "About the same as the week before.")
        val lo = w.minBy { d(it).totalSec }
        val opens = HashMap<String, Int>()
        val time = HashMap<String, Int>()
        w.forEach { day -> d(day).apps.forEach { (k, a) -> opens.merge(k, a.opens, Int::plus); time.merge(k, a.totalSec, Int::plus) } }
        val total = w.sumOf { d(it).totalSec }
        val aiRows = time.filterKeys { AiApps.isAi(it) }.map { AppRow(it.key, it.value) }.filter { it.sec >= 60 }.sortedByDescending { it.sec }
        val ai = aiRows.sumOf { it.sec }
        val visits = w.sumOf { day -> d(day).apps.filterKeys { AiApps.isAi(it) }.values.sumOf { it.opens } }
        val hrs = IntArray(24) { x -> w.sumOf { day -> d(day).apps.filterKeys { AiApps.isAi(it) }.values.sumOf { it.hours[x] } } }
        val b = busiest2(hrs)
        val mostly = if (ai >= 1800 && hrs[b] + hrs[b + 1] >= 0.35 * ai) "Mostly ${Fmt.spanWords(b, 2, h.is24)}." else ""
        val prev = (1..4).map { n -> weekDays(w[0].minusWeeks(n.toLong())) }.filter { wk -> wk.all { h.days[it]?.complete == true } }
            .map { wk -> wk.sumOf { day -> d(day).apps.filterKeys { AiApps.isAi(it) }.values.sumOf { it.totalSec } } / 60.0 }
        var vs = ""
        if (prev.size >= 2) { val base = avg(prev); val df = ai / 60.0 - base; vs = if (abs(df) >= 20 && abs(df) >= 0.3 * base) "${Fmt.durMin(abs(df))} ${if (df > 0) "more" else "less"} than your usual week." else "About your usual week." }
        val send = senders(w).values.sortedByDescending { it.received }
        return Recap(
            recapName(), avgW.roundToInt(), total, cmp, w.map { d(it).totalSec }, lo, lightest4(lo), d(lo),
            opens.filterValues { it > 0 }.maxByOrNull { it.value }?.key, opens, time, time.maxByOrNull { it.value }?.key,
            ai, (ai * 100.0 / max(1, total)).roundToInt().coerceIn(1, 100), aiRows.take(3), visits, mostly, vs,
            send.sumOf { it.received }, send.sumOf { it.followed }, send.take(3), send.firstOrNull { it.received >= 20 && it.followed == 0 },
            weekStart(today).plusDays(7)
        )
    }

    companion object {
        private val CLEAN_MIN = intArrayOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 300, 360, 480, 600, 720, 960, 1200, 1440)

        /** [sec] rounded up to a clean chart top: 5, 10, 15, 20, 30 or 45 min, 1 hr, 1 hr 30 min, 2, 3, 4 hr and so on. */
        fun niceTop(sec: Int): Int {
            val m = ceil(sec / 60.0).toInt()
            return (CLEAN_MIN.firstOrNull { it >= m } ?: (ceil(m / 240.0).toInt() * 240)) * 60
        }
    }
}
