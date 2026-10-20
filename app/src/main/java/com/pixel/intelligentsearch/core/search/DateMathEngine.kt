package com.pixel.intelligentsearch.core.search

import java.text.NumberFormat
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Date arithmetic answers, worked out on the device:
 * - "days until Dec 25", "days until christmas", "how many days until 2027-01-01"
 * - "days since Jan 1"
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
            countFromToday(query) ?: between(query) ?: offset(query)
        } catch (_: DateTimeException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // QUESTIONS
    // ---------------------------------------------------------------------------------------------

    /** "days until <date>" and "days since <date>". */
    private fun countFromToday(query: String): DateAnswer? {
        val match = COUNT_REGEX.matchEntire(query) ?: return null
        val forward = match.groupValues[1] != "since"
        val today = today()
        val date = parseDate(match.groupValues[2], today, if (forward) Lean.FORWARD else Lean.BACKWARD) ?: return null
        val days = ChronoUnit.DAYS.between(today, date)
        val full = full(date)
        return when {
            days == 0L -> DateAnswer("Today", full, "$full is today")
            days > 0 -> dayCount(days).let { DateAnswer(it, "Until $full", "$it until $full") }
            else -> dayCount(-days).let { DateAnswer(it, "Since $full", "$it since $full") }
        }
    }

    /** "days between <date> and <date>". A second date without a year that falls before the first is next year's. */
    private fun between(query: String): DateAnswer? {
        val match = BETWEEN_REGEX.matchEntire(query) ?: return null
        val today = today()
        val first = parseDate(match.groupValues[1], today, Lean.THIS_YEAR) ?: return null
        var second = parseDate(match.groupValues[2], today, Lean.THIS_YEAR) ?: return null
        if (second < first && !hasYear(match.groupValues[2])) second = second.plusYears(1)
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

    private fun parseDate(text: String, today: LocalDate, lean: Lean): LocalDate? {
        when (text) {
            "today", "now" -> return today
            "tomorrow" -> return today.plusDays(1)
            "yesterday" -> return today.minusDays(1)
        }
        NAMED_DAYS[text]?.let { return resolve(it, null, today, lean) }
        ISO_DATE_REGEX.matchEntire(text)?.let { match ->
            val (year, month, day) = match.destructured
            return date(year.toInt(), month.toInt(), day.toInt())
        }
        MONTH_DAY_REGEX.matchEntire(text)?.let { match ->
            val month = MONTHS[match.groupValues[1]] ?: return null
            return monthDay(month, match.groupValues[2].toInt())?.let { resolve(it, match.groupValues[3].toIntOrNull(), today, lean) }
        }
        DAY_MONTH_REGEX.matchEntire(text)?.let { match ->
            val month = MONTHS[match.groupValues[2]] ?: return null
            return monthDay(month, match.groupValues[1].toInt())?.let { resolve(it, match.groupValues[3].toIntOrNull(), today, lean) }
        }
        return null
    }

    private fun hasYear(text: String): Boolean = YEAR_REGEX.containsMatchIn(text)

    /**
     * [monthDay] in [year], or without one, the year [lean] picks. Feb 29 without a year is the nearest leap
     * year's in that direction.
     */
    private fun resolve(monthDay: MonthDay, year: Int?, today: LocalDate, lean: Lean): LocalDate? {
        if (year != null) return if (monthDay.isValidYear(year) && year in 1..9999) monthDay.atYear(year) else null
        val years = when (lean) {
            Lean.FORWARD -> today.year..today.year + 8
            Lean.BACKWARD -> today.year downTo today.year - 8
            Lean.THIS_YEAR -> today.year..today.year + 8
        }
        for (candidate in years) {
            if (!monthDay.isValidYear(candidate)) continue
            val date = monthDay.atYear(candidate)
            when (lean) {
                Lean.FORWARD -> if (date >= today) return date
                Lean.BACKWARD -> if (date <= today) return date
                Lean.THIS_YEAR -> return date
            }
        }
        return null
    }

    private fun monthDay(month: Int, day: Int): MonthDay? = runCatching { MonthDay.of(month, day) }.getOrNull()

    private fun date(year: Int, month: Int, day: Int): LocalDate? =
        if (year in 1..9999) runCatching { LocalDate.of(year, month, day) }.getOrNull() else null

    // ---------------------------------------------------------------------------------------------
    // WORDS
    // ---------------------------------------------------------------------------------------------

    private fun today(): LocalDate = LocalDate.now(clock ?: Clock.systemDefaultZone())

    private fun locale(): Locale = locale ?: Locale.getDefault()

    private fun full(date: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale()).format(date)

    private fun number(value: Long): String = NumberFormat.getIntegerInstance(locale()).format(value)

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

        val ISO_DATE_REGEX = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
        val MONTH_DAY_REGEX = Regex("^($MONTH)\\.? (\\d{1,2})$ORDINAL(?:,? (\\d{4}))?$")
        val DAY_MONTH_REGEX = Regex("^(?:the )?(\\d{1,2})$ORDINAL(?: of)? ($MONTH)\\.?(?:,? (\\d{4}))?$")
        val YEAR_REGEX = Regex("\\d{4}")

        val COUNT_REGEX = Regex("^(?:how many )?days (?:left |are there )?(until|till|til|to|before|since) (.+)$")
        val BETWEEN_REGEX = Regex("^(?:how many )?days (?:are there )?(?:between|from) (.+?) (?:and|to|until|till) (.+)$")

        private const val AMOUNT = "(\\d{1,6}|a|an|one)"
        private const val UNIT = "(days?|weeks?|months?|years?)"
        val PLUS_REGEX = Regex("^(?:today|now) ?(\\+|-|plus|minus) ?$AMOUNT $UNIT$")
        val TODAY_ANCHORS = setOf("from now", "from today", "after today", "ago", "before today")
        val RELATIVE_REGEX = Regex("^$AMOUNT $UNIT (from now|from today|after today|ago|before today|(?:from|after|before) .+)$")

        val UNIT_NAMES = mapOf(ChronoUnit.DAYS to "day", ChronoUnit.WEEKS to "week", ChronoUnit.MONTHS to "month", ChronoUnit.YEARS to "year")
    }
}
