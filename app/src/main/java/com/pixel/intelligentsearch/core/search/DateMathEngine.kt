package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.occasion.OccasionCalendar
import java.text.NumberFormat
import java.time.Clock
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

/**
 * Date arithmetic answers, worked out on the device:
 * - "today", "what's the date", "tomorrow", "yesterday" (the date itself)
 * - "days until Dec 25", "days until christmas", "days until thanksgiving", "how many days until 2027-01-01"
 * - "days until friday" (the next Friday after today), "days since Jan 1"
 * - "what day is 2027-03-14", "what day of the week was christmas" (the weekday)
 * - "week number", "what week is it", "week number of mar 14" (the ISO week)
 * - "today + 90 days", "90 days from now", "2 weeks from today", "3 months ago", "2 weeks before christmas"
 * - "how many days between Mar 1 and Jun 30"
 *
 * A query becomes an answer only when the whole of it has one of these shapes and names a real date, so ordinary
 * searches ("28 days later", "days of our lives") are left alone. Dates are written out in [locale]'s full style;
 * [clock] says what today is. Both default to the phone's, read at each call, and are fixed in tests.
 */
class DateMathEngine(private val clock: Clock? = null, private val locale: Locale? = null) {

    /** An answer: [title] for the card's large line, [subtitle] under it, and [text], the same in one sentence. */
    data class DateAnswer(val title: String, val subtitle: String, val text: String)

    /** The answer to [rawQuery], or null when it isn't a date question. */
    fun evaluate(rawQuery: String): DateAnswer? {
        val query = normalize(rawQuery) ?: return null
        return try {
            plainDate(query) ?: countFromToday(query) ?: between(query) ?: offset(query) ?: weekday(query) ?: weekNumber(query)
        } catch (_: DateTimeException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // QUESTIONS
    // ---------------------------------------------------------------------------------------------

    /** "today", "date", "what's the date", "tomorrow", "yesterday's date": the date itself. */
    private fun plainDate(query: String): DateAnswer? {
        val day = when (query) {
            in TODAY_PHRASES -> "Today"
            in TOMORROW_PHRASES -> "Tomorrow"
            in YESTERDAY_PHRASES -> "Yesterday"
            else -> return null
        }
        val date = when (day) {
            "Tomorrow" -> today().plusDays(1)
            "Yesterday" -> today().minusDays(1)
            else -> today()
        }
        val full = full(date)
        return DateAnswer(full, day, "$day ${if (day == "Yesterday") "was" else "is"} $full")
    }

    /** "days until <date>" and "days since <date>". */
    private fun countFromToday(query: String): DateAnswer? {
        val match = COUNT_REGEX.matchEntire(query) ?: return null
        val forward = match.groupValues[1] != "since"
        val today = today()
        val date = nearestWeekday(match.groupValues[2], today, forward)
            ?: parseDate(match.groupValues[2], today, if (forward) Lean.FORWARD else Lean.BACKWARD)
            ?: return null
        val days = ChronoUnit.DAYS.between(today, date)
        val full = full(date)
        return when {
            days == 0L -> DateAnswer("Today", full, "$full is today")
            days > 0 -> dayCount(days).let { DateAnswer(it, "Until $full", "$it until $full") }
            else -> dayCount(-days).let { DateAnswer(it, "Since $full", "$it since $full") }
        }
    }

    /**
     * "days between <date> and <date>". Today, tomorrow and yesterday are always those days. A second date without a
     * year is the first time it comes round on or after the first date. A first date without a year is this year's,
     * unless the second has a year written: then it is the last time it came round on or before the second. When
     * neither has a year and only a leap year has the second (Feb 29), both are that leap year's.
     */
    private fun between(query: String): DateAnswer? {
        val match = BETWEEN_REGEX.matchEntire(query) ?: return null
        val today = today()
        val firstWritten = read(match.groupValues[1], today) ?: return null
        val secondWritten = read(match.groupValues[2], today) ?: return null
        var first = when (firstWritten) {
            is Written.Fixed -> firstWritten.date
            is Written.Yearless -> when {
                // "jan 1 and dec 25 2027": the year written with the second date is the one meant for both.
                secondWritten is Written.Fixed && secondWritten.yearWritten -> onOrBefore(firstWritten.monthDay, secondWritten.date)
                else -> resolve(firstWritten.monthDay, today, Lean.THIS_YEAR)
            } ?: return null
            is Written.Movable -> when {
                secondWritten is Written.Fixed && secondWritten.yearWritten -> movableOnOrBefore(firstWritten, secondWritten.date)
                else -> firstWritten.inYear(today.year)
            } ?: return null
        }
        val second = when (secondWritten) {
            is Written.Fixed -> secondWritten.date
            is Written.Movable -> movableOnOrAfter(secondWritten, first) ?: return null
            is Written.Yearless -> {
                val next = onOrAfter(secondWritten.monthDay, first) ?: return null
                // Only Feb 29 can be more than a year on; then the first date is read in the same leap year.
                if (firstWritten is Written.Yearless && ChronoUnit.DAYS.between(first, next) > DAYS_IN_LEAP_YEAR) {
                    first = onOrBefore(firstWritten.monthDay, next) ?: return null
                }
                next
            }
        }
        val count = dayCount(abs(ChronoUnit.DAYS.between(first, second)))
        val span = "${full(first)} and ${full(second)}"
        return DateAnswer(count, "Between $span", "$count between $span")
    }

    /** "today + 90 days", "90 days from now", "3 months ago", "2 weeks before christmas". */
    private fun offset(query: String): DateAnswer? {
        val today = today()
        PLUS_REGEX.matchEntire(query)?.let { match ->
            val amount = amount(match.groupValues[2]) ?: return null
            val unit = unit(match.groupValues[3]) ?: return null
            val back = match.groupValues[1] == "-" || match.groupValues[1] == "minus"
            return relativeToToday(today, amount, unit, back)
        }
        RELATIVE_REGEX.matchEntire(query)?.let { match ->
            val amount = amount(match.groupValues[1]) ?: return null
            val unit = unit(match.groupValues[2]) ?: return null
            val anchor = match.groupValues[3]
            if (anchor in TODAY_ANCHORS) return relativeToToday(today, amount, unit, back = anchor.endsWith("ago") || anchor.startsWith("before"))
            val (direction, dateText) = anchor.split(' ', limit = 2).let { it[0] to it[1] }
            val base = parseDate(dateText, today, Lean.FORWARD) ?: return null
            val back = direction == "before"
            val result = shift(base, amount, unit, back) ?: return null
            val description = "${quantity(amount, unit)} ${if (back) "before" else "after"} ${full(base)}"
            return DateAnswer(full(result), description.replaceFirstChar { it.titlecase(Locale.ROOT) }, "$description is ${full(result)}")
        }
        return null
    }

    /**
     * "what day is 2027-03-14", "what day of the week is christmas", "what day was jan 1 2000", "what day is it". A date
     * without a year is the next time it comes round, or the last time when asked with "was" or "did".
     */
    private fun weekday(query: String): DateAnswer? {
        val match = WEEKDAY_REGEX.matchEntire(query) ?: return null
        val past = match.groupValues[1] == "was" || match.groupValues[1] == "did"
        val today = today()
        val text = match.groupValues[2].let { if (it == "it") "today" else it }
        val date = parseDate(text, today, if (past) Lean.BACKWARD else Lean.FORWARD) ?: return null
        val day = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale())
        val full = full(date)
        val sentence = when {
            date < today -> "$full was a $day"
            date > today -> "$full is a $day"
            else -> "Today is $full"
        }
        return DateAnswer(day, full, sentence)
    }

    /** "week number", "what week is it", "week number of mar 14", "what week is 2027-01-01": the ISO 8601 week. */
    private fun weekNumber(query: String): DateAnswer? {
        val today = today()
        val date = if (query in THIS_WEEK_PHRASES) {
            today
        } else {
            val text = (WEEK_OF_REGEX.matchEntire(query) ?: return null).groupValues[1]
            parseDate(if (text == "it") "today" else text, today, Lean.THIS_YEAR) ?: return null
        }
        val week = number(date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toLong())
        val weekYear = date.get(IsoFields.WEEK_BASED_YEAR)
        // Late December can fall in next year's week 1, early January in last year's week 52 or 53.
        val ofYear = if (weekYear == date.year) "" else " of ${year(weekYear)}"
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return DateAnswer("Week $week$ofYear", "Starting ${full(monday)}", "${full(date)} is in week $week$ofYear")
    }

    private fun relativeToToday(today: LocalDate, amount: Long, unit: ChronoUnit, back: Boolean): DateAnswer? {
        val result = shift(today, amount, unit, back) ?: return null
        val description = "${quantity(amount, unit)} ${if (back) "ago" else "from today"}"
        return DateAnswer(full(result), description, "$description ${if (back) "was" else "is"} ${full(result)}")
    }

    /** [base] moved [amount] [unit]s, or null outside years 1 to 9999. Month and year steps end on a real day. */
    private fun shift(base: LocalDate, amount: Long, unit: ChronoUnit, back: Boolean): LocalDate? {
        val result = if (back) base.minus(amount, unit) else base.plus(amount, unit)
        return result.takeIf { it.year in 1..9999 }
    }

    // ---------------------------------------------------------------------------------------------
    // DATES
    // ---------------------------------------------------------------------------------------------

    /** Which year a date written without one means: the next or last time it comes round, or this year's. */
    private enum class Lean { FORWARD, BACKWARD, THIS_YEAR }

    /**
     * A date as written: a day fixed by its year ([Fixed.yearWritten]) or by being today, tomorrow or yesterday, or a
     * month and day alone.
     */
    private sealed interface Written {
        data class Fixed(val date: LocalDate, val yearWritten: Boolean = true) : Written
        data class Yearless(val monthDay: MonthDay) : Written
        /** A day whose date moves from year to year, such as Easter: [inYear] is its date in a year. */
        class Movable(val inYear: (Int) -> LocalDate) : Written
    }

    /**
     * The next [text] weekday after today ("days until friday"), or the last one before it when not [forward]; naming
     * today's weekday means a week away. Null when [text] isn't a weekday.
     */
    private fun nearestWeekday(text: String, today: LocalDate, forward: Boolean): LocalDate? {
        val day = WEEKDAYS[text] ?: return null
        return today.with(if (forward) TemporalAdjusters.next(day) else TemporalAdjusters.previous(day))
    }

    private fun parseDate(text: String, today: LocalDate, lean: Lean): LocalDate? = when (val written = read(text, today)) {
        null -> null
        is Written.Fixed -> written.date
        is Written.Yearless -> resolve(written.monthDay, today, lean)
        is Written.Movable -> when (lean) {
            Lean.FORWARD -> movableOnOrAfter(written, today)
            Lean.BACKWARD -> movableOnOrBefore(written, today)
            Lean.THIS_YEAR -> written.inYear(today.year)
        }
    }

    /** The names of the days [occasionOnOrAfter] knows: "christmas", "new year's eve", "thanksgiving", "easter". */
    internal val occasionNames: Set<String> get() = NAMED_DAYS.keys + MOVABLE_DAYS.keys

    /** The first day named [name] ("christmas", "thanksgiving") on or after [from], or null when it names none. */
    internal fun occasionOnOrAfter(name: String, from: LocalDate): LocalDate? {
        NAMED_DAYS[name]?.let { return onOrAfter(it, from) }
        return MOVABLE_DAYS[name]?.let { movableOnOrAfter(Written.Movable(it), from) }
    }

    /** The first [day] on or after [from]. */
    private fun movableOnOrAfter(day: Written.Movable, from: LocalDate): LocalDate? =
        (from.year..from.year + 1).asSequence().map(day.inYear).firstOrNull { it >= from }?.takeIf { it.year <= 9999 }

    /** The last [day] on or before [until]. */
    private fun movableOnOrBefore(day: Written.Movable, until: LocalDate): LocalDate? =
        (until.year downTo until.year - 1).asSequence().map(day.inYear).firstOrNull { it <= until }?.takeIf { it.year >= 1 }

    private fun read(text: String, today: LocalDate): Written? {
        when (text) {
            "today", "now" -> return Written.Fixed(today, yearWritten = false)
            "tomorrow" -> return Written.Fixed(today.plusDays(1), yearWritten = false)
            "yesterday" -> return Written.Fixed(today.minusDays(1), yearWritten = false)
        }
        NAMED_DAYS[text]?.let { return Written.Yearless(it) }
        MOVABLE_DAYS[text]?.let { return Written.Movable(it) }
        // "christmas 2027", "easter, 2030": the day in that year.
        OCCASION_YEAR_REGEX.matchEntire(text)?.let { match ->
            val year = match.groupValues[2].toInt()
            NAMED_DAYS[match.groupValues[1]]?.let { return written(it, year) }
            MOVABLE_DAYS[match.groupValues[1]]?.let { inYear ->
                return if (year in 1..9999) Written.Fixed(inYear(year)) else null
            }
        }
        ISO_DATE_REGEX.matchEntire(text)?.let { match ->
            val (year, month, day) = match.destructured
            return date(year.toInt(), month.toInt(), day.toInt())?.let(Written::Fixed)
        }
        MONTH_DAY_REGEX.matchEntire(text)?.let { match ->
            val month = MONTHS[match.groupValues[1]] ?: return null
            return monthDay(month, match.groupValues[2].toInt())?.let { written(it, match.groupValues[3].toIntOrNull()) }
        }
        DAY_MONTH_REGEX.matchEntire(text)?.let { match ->
            val month = MONTHS[match.groupValues[2]] ?: return null
            return monthDay(month, match.groupValues[1].toInt())?.let { written(it, match.groupValues[3].toIntOrNull()) }
        }
        return null
    }

    /** [monthDay] in [year] when one was written (null when that year hasn't the day), else the month and day alone. */
    private fun written(monthDay: MonthDay, year: Int?): Written? = when {
        year == null -> Written.Yearless(monthDay)
        monthDay.isValidYear(year) && year in 1..9999 -> Written.Fixed(monthDay.atYear(year))
        else -> null
    }

    /** [monthDay] in the year [lean] picks. Feb 29 is the nearest leap year's in that direction. */
    private fun resolve(monthDay: MonthDay, today: LocalDate, lean: Lean): LocalDate? = when (lean) {
        Lean.FORWARD -> onOrAfter(monthDay, today)
        Lean.BACKWARD -> onOrBefore(monthDay, today)
        Lean.THIS_YEAR -> (today.year..today.year + 8).firstOrNull(monthDay::isValidYear)?.let(monthDay::atYear)
    }

    /** The first [monthDay] on or after [from]. */
    private fun onOrAfter(monthDay: MonthDay, from: LocalDate): LocalDate? =
        (from.year..from.year + 8).asSequence()
            .filter(monthDay::isValidYear)
            .map(monthDay::atYear)
            .firstOrNull { it >= from }
            ?.takeIf { it.year <= 9999 }

    /** The last [monthDay] on or before [until]. */
    private fun onOrBefore(monthDay: MonthDay, until: LocalDate): LocalDate? =
        (until.year downTo until.year - 8).asSequence()
            .filter(monthDay::isValidYear)
            .map(monthDay::atYear)
            .firstOrNull { it <= until }
            ?.takeIf { it.year >= 1 }

    private fun monthDay(month: Int, day: Int): MonthDay? = runCatching { MonthDay.of(month, day) }.getOrNull()

    private fun date(year: Int, month: Int, day: Int): LocalDate? =
        if (year in 1..9999) runCatching { LocalDate.of(year, month, day) }.getOrNull() else null

    // ---------------------------------------------------------------------------------------------
    // WORDS
    // ---------------------------------------------------------------------------------------------

    private fun today(): LocalDate = LocalDate.now(clock ?: Clock.systemDefaultZone())

    private fun locale(): Locale = locale ?: Locale.getDefault()

    /** [date] in the locale's full style, with the locale's digits, the same ones [number] uses. */
    private fun full(date: LocalDate): String = locale().let { locale ->
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).withDecimalStyle(DecimalStyle.of(locale)).format(date)
    }

    private fun number(value: Long): String = NumberFormat.getIntegerInstance(locale()).format(value)

    /** A year in the locale's digits, without the grouping a count gets ("2026", not "2,026"). */
    private fun year(value: Int): String =
        NumberFormat.getIntegerInstance(locale()).apply { isGroupingUsed = false }.format(value.toLong())

    private fun dayCount(days: Long): String = "${number(days)} ${if (days == 1L) "day" else "days"}"

    private fun quantity(amount: Long, unit: ChronoUnit): String {
        val name = UNIT_NAMES.getValue(unit)
        return "${number(amount)} ${if (amount == 1L) name else name + "s"}"
    }

    private fun amount(text: String): Long? = when (text) {
        "a", "an", "one" -> 1L
        else -> text.toLongOrNull()
    }

    private fun unit(text: String): ChronoUnit? = when (text.removeSuffix("s")) {
        "day" -> ChronoUnit.DAYS
        "week" -> ChronoUnit.WEEKS
        "month" -> ChronoUnit.MONTHS
        "year" -> ChronoUnit.YEARS
        else -> null
    }

    private fun normalize(raw: String): String? {
        val text = raw.trim().trimEnd('?', '.', '!').trim()
        if (text.isEmpty() || text.length > MAX_QUERY_LENGTH) return null
        return text.lowercase(Locale.ROOT).replace('’', '\'').replace(WHITESPACE, " ")
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 80
        const val DAYS_IN_LEAP_YEAR = 366L
        val WHITESPACE = Regex("\\s+")

        val MONTHS: Map<String, Int> = buildMap {
            val names = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
            names.forEachIndexed { index, name ->
                put(name, index + 1)
                put(name.take(3), index + 1)
            }
            put("sept", 9)
        }
        private val MONTH = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
        private const val ORDINAL = "(?:st|nd|rd|th)?"

        /** Fixed-date days people count down to. */
        val NAMED_DAYS: Map<String, MonthDay> = buildMap {
            for (name in listOf("christmas", "christmas day", "xmas")) put(name, MonthDay.of(12, 25))
            put("christmas eve", MonthDay.of(12, 24))
            for (name in listOf("new year", "new years", "new year's", "new years day", "new year's day")) put(name, MonthDay.of(1, 1))
            for (name in listOf("new years eve", "new year's eve")) put(name, MonthDay.of(12, 31))
            put("halloween", MonthDay.of(10, 31))
            for (name in listOf("valentines", "valentine's", "valentines day", "valentine's day")) put(name, MonthDay.of(2, 14))
            for (name in listOf("st patricks day", "st. patrick's day", "st patrick's day")) put(name, MonthDay.of(3, 17))
        }

        /** Days whose date moves: US Thanksgiving (the fourth Thursday of November) and Western Easter. */
        val MOVABLE_DAYS: Map<String, (Int) -> LocalDate> = buildMap {
            val thanksgiving = { year: Int -> OccasionCalendar.nth(year, 11, DayOfWeek.THURSDAY, 4) }
            for (name in listOf("thanksgiving", "thanksgiving day")) put(name, thanksgiving)
            val easter = { year: Int -> OccasionCalendar.easter(year) }
            for (name in listOf("easter", "easter sunday", "easter day")) put(name, easter)
        }

        val WEEKDAYS: Map<String, DayOfWeek> = DayOfWeek.entries.associateBy { it.name.lowercase(Locale.ROOT) }

        /** Asking for the date itself, today's, tomorrow's or yesterday's; the word alone asks too. */
        val TODAY_PHRASES = setOf(
            "today", "date", "the date", "today's date", "todays date", "date today", "current date", "the date today",
            "what's the date", "whats the date", "what is the date", "what's the date today", "whats the date today",
            "what is the date today", "what's today's date", "whats todays date", "what is today's date",
            "what date is it", "what date is it today", "what is today", "what's today", "whats today"
        )
        val TOMORROW_PHRASES = setOf(
            "tomorrow", "tomorrow's date", "tomorrows date", "date tomorrow", "what's the date tomorrow",
            "whats the date tomorrow", "what is the date tomorrow", "what date is tomorrow", "what is tomorrow's date",
            "what's tomorrow's date", "whats tomorrows date"
        )
        val YESTERDAY_PHRASES = setOf(
            "yesterday", "yesterday's date", "yesterdays date", "date yesterday", "what was the date yesterday",
            "what date was yesterday", "what was yesterday's date", "what was yesterdays date"
        )

        val ISO_DATE_REGEX = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
        val OCCASION_YEAR_REGEX = Regex("^(.+?),? (\\d{4})$")
        val MONTH_DAY_REGEX = Regex("^($MONTH)\\.? (\\d{1,2})$ORDINAL(?:,? (\\d{4}))?$")
        val DAY_MONTH_REGEX = Regex("^(?:the )?(\\d{1,2})$ORDINAL(?: of)? ($MONTH)\\.?(?:,? (\\d{4}))?$")

        val COUNT_REGEX = Regex("^(?:how many )?days (?:left |are there )?(until|till|til|to|before|since) (.+)$")
        val WEEKDAY_REGEX = Regex(
            "^(?:what|which) day(?: of the week)? (is|was|will|does|did) (.+?)(?: (?:be on|be|fall on|land on|fall|land|on))?$"
        )
        val THIS_WEEK_PHRASES = setOf(
            "week number", "week no", "current week", "current week number", "week number today", "this week number",
            "iso week", "iso week number", "what week is it", "what week is this", "which week is it", "what week number is it",
            "what week of the year is it", "what is the week number", "what's the week number", "whats the week number",
            "what is the current week", "what's the current week", "whats the current week", "what week are we in",
            "week of the year", "today's week number", "todays week number"
        )
        val WEEK_OF_REGEX = Regex("^(?:what |which )?(?:iso )?week(?: number| no)?(?: of the year)? (?:of|for|on|is|was) (.+)$")
        val BETWEEN_REGEX = Regex("^(?:how many )?days (?:are there )?(?:between|from) (.+?) (?:and|to|until|till) (.+)$")

        private const val AMOUNT = "(\\d{1,6}|a|an|one)"
        private const val UNIT = "(days?|weeks?|months?|years?)"
        val PLUS_REGEX = Regex("^(?:today|now) ?(\\+|-|plus|minus) ?$AMOUNT $UNIT$")
        val TODAY_ANCHORS = setOf("from now", "from today", "after today", "ago", "before today")
        val RELATIVE_REGEX = Regex("^$AMOUNT $UNIT (from now|from today|after today|ago|before today|(?:from|after|before) .+)$")

        val UNIT_NAMES = mapOf(ChronoUnit.DAYS to "day", ChronoUnit.WEEKS to "week", ChronoUnit.MONTHS to "month", ChronoUnit.YEARS to "year")
    }
}
