package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.core.answers.RainAhead
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.answers.WeatherCodes
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.util.Durations
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The weather the Discover widget shows: the temperature now, the conditions, the day's high and low, and when rain
 * or snow is first likely ([rainAt], 0 when it isn't). Plain values, so the last reading can be kept ([encode]) and
 * shown again by a later redraw that can't reach the forecast.
 */
internal data class DiscoverWeather(
    /** When it was read. */
    val at: Long,
    val unit: TemperatureUnit,
    val temperature: Double,
    val code: Int,
    val isDay: Boolean,
    val high: Double,
    val low: Double,
    val rainAt: Long = 0L,
    /** Whether what falls at [rainAt] is snow. */
    val snow: Boolean = false
) {
    /** One line of plain values, for the widget's own store. */
    fun encode(): String = listOf(at, unit.name, temperature, code, isDay, high, low, rainAt, snow).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "|"

        /** [report] read at [at]. */
        fun from(report: WeatherReport, at: Long): DiscoverWeather {
            val wet = RainAhead.hour(report.hours)
            return DiscoverWeather(
                at = at,
                unit = report.unit,
                temperature = report.temperature,
                code = report.code,
                isDay = report.isDay,
                high = report.high,
                low = report.low,
                rainAt = wet?.time?.toInstant()?.toEpochMilli() ?: 0L,
                snow = wet != null && RainAhead.isSnow(wet)
            )
        }

        /** The reading [stored] holds, or null when it holds none (or something this version can't read). */
        fun decode(stored: String?): DiscoverWeather? = runCatching {
            val parts = stored.orEmpty().split(SEPARATOR)
            if (parts.size != 9) return null
            DiscoverWeather(
                at = parts[0].toLong(),
                unit = TemperatureUnit.valueOf(parts[1]),
                temperature = parts[2].toDouble(),
                code = parts[3].toInt(),
                isDay = parts[4].toBooleanStrict(),
                high = parts[5].toDouble(),
                low = parts[6].toDouble(),
                rainAt = parts[7].toLong(),
                snow = parts[8].toBooleanStrict()
            )
        }.getOrNull()
    }
}

/**
 * Everything the Discover widget is drawn from. [weather] is null with Weather off or no forecast to show, and
 * [events] is null with Up Next off or no calendar access; otherwise it holds what the calendar has from now to the
 * end of tomorrow. [leaveAt] is when to leave for the next event, when the app knows. [weatherDue] says Weather is on
 * and has a place to ask for, so a missing forecast is one that couldn't be read, not one to turn on.
 */
internal data class DiscoverInput(
    val weather: DiscoverWeather?,
    val events: List<CalendarEvent>?,
    val leaveAt: Long? = null,
    val weatherDue: Boolean = false
)

/** What the small dot before the temperature says: sun, rain or snow on the way, or neither. */
internal enum class DiscoverDot { SUN, RAIN, PLAIN }

/** One row high: the temperature, then the next event ("Dentist" and " · 3:30 PM") or, without one, the conditions. */
internal data class DiscoverPill(val temperature: String?, val lead: String, val time: String?)

/**
 * Two rows by two. With weather, the conditions over the temperature ([hero]) over the next event; without it, the
 * event leads: its label, its name large ([headline]), and when it is.
 */
internal data class DiscoverTile(
    val top: String,
    val hero: String?,
    val headline: String?,
    val lead: String?,
    val time: String?,
    /** Whether the bottom line is the event's time, drawn in the accent color. */
    val accentBottom: Boolean
)

/**
 * Wide: a label and how soon, the next event large, the line under it, and the weather in a strip underneath. The
 * line is when ([before]), where ([place], which gives way when the line is too long) and when to leave ([after]).
 */
internal data class DiscoverWide(
    val label: String,
    val badge: String?,
    val title: String,
    val before: String? = null,
    val place: String? = null,
    val after: String? = null,
    val weather: String?
) {
    /** The line under the event, in order. */
    val meta: List<String> get() = listOfNotNull(before, place, after)
}

/** What the Discover widget says at each size, and all of it as TalkBack reads it. */
internal data class DiscoverTexts(
    val pill: DiscoverPill,
    val tile: DiscoverTile,
    val wide: DiscoverWide,
    val dot: DiscoverDot?,
    /** Whether there is nothing to show yet, and the widget says how to turn something on. */
    val setUp: Boolean,
    val spoken: String
)

internal object DiscoverWidgetModel {
    const val UP_NEXT = "UP NEXT"
    const val TODAY = "TODAY"
    const val NOTHING_ELSE = "Nothing else today"
    const val TURN_ON = "Turn on Up Next or Weather in Discover"
    const val NO_WEATHER = "No weather right now"
    private const val SEPARATOR = " · "

    /** How soon an event starts is shown only this close to it, where a still picture can keep it true. */
    val BADGE_WINDOW_MS: Long = Duration.ofMinutes(60).toMillis()

    /** The next event today: the first that hasn't started and has a time, as Discover's Up Next picks it. */
    fun nextEvent(events: List<CalendarEvent>, now: ZonedDateTime): CalendarEvent? =
        timedAhead(events, now).firstOrNull { dateOf(it, now) == now.toLocalDate() }

    /** Tomorrow's first event with a time. */
    private fun tomorrowsFirst(events: List<CalendarEvent>, now: ZonedDateTime): CalendarEvent? =
        timedAhead(events, now).firstOrNull { dateOf(it, now) == now.toLocalDate().plusDays(1) }

    private fun timedAhead(events: List<CalendarEvent>, now: ZonedDateTime): List<CalendarEvent> {
        val nowMs = now.toInstant().toEpochMilli()
        return events.filter { !it.allDay && it.beginMillis > nowMs }.sortedBy { it.beginMillis }
    }

    private fun dateOf(event: CalendarEvent, now: ZonedDateTime) = Instant.ofEpochMilli(event.beginMillis).atZone(now.zone).toLocalDate()

    /**
     * How long from [now] until the widget next has something new to say: an hour at most, and sooner for the next
     * event: when it comes within the hour (how soon it starts appears), every five minutes from then on, and as
     * it starts. Never past midnight, when today's events become tomorrow's.
     */
    fun nextRedrawDelayMs(now: ZonedDateTime, events: List<CalendarEvent>?): Long {
        val nowMs = now.toInstant().toEpochMilli()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        var delay = minOf(REDRAW_MS, midnight - nowMs + AFTER_MS)
        val next = events?.let { nextEvent(it, now) }
        if (next != null) {
            val until = next.beginMillis - nowMs
            delay = minOf(delay, if (until > BADGE_WINDOW_MS) until - BADGE_WINDOW_MS else minOf(BADGE_STEP_MS, until + AFTER_MS))
        }
        return delay.coerceAtLeast(AFTER_MS)
    }

    /** The widget is drawn again this often with nothing else to wait for. */
    val REDRAW_MS: Long = Duration.ofMinutes(60).toMillis()
    private val BADGE_STEP_MS: Long = Duration.ofMinutes(5).toMillis()
    /** Just past the moment waited for, so the redraw finds it passed. */
    private const val AFTER_MS = 1_000L

    /** What the widget says for [input] at [now], with times on a 12 or 24 hour clock ([is24Hour]). */
    fun texts(input: DiscoverInput, now: ZonedDateTime, is24Hour: Boolean, locale: Locale = Locale.getDefault()): DiscoverTexts {
        val nowMs = now.toInstant().toEpochMilli()
        fun clock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(now.zone)
            .format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale))

        val weather = input.weather
        val temperature = weather?.let { degrees(it.temperature) }
        val conditions = weather?.let { WeatherCodes.describe(it.code) + SEPARATOR + "H " + degrees(it.high) }
        // Rain or snow still ahead; once its hour has passed, an old reading no longer says so.
        val rain = weather?.takeIf { it.rainAt > nowMs }?.let {
            RainAhead.words(it.snow, Instant.ofEpochMilli(it.rainAt).atZone(now.zone), is24Hour, locale)
        }
        val dot = weather?.let {
            when (WeatherCodes.kind(it.code)) {
                WeatherCodes.Kind.CLEAR, WeatherCodes.Kind.PARTLY_CLOUDY -> if (it.isDay) DiscoverDot.SUN else DiscoverDot.PLAIN
                WeatherCodes.Kind.CLOUDY, WeatherCodes.Kind.FOG -> DiscoverDot.PLAIN
                else -> DiscoverDot.RAIN
            }
        }

        val events = input.events
        val next = events?.let { nextEvent(it, now) }
        val tomorrow = events?.takeIf { next == null }?.let { tomorrowsFirst(it, now) }
        val tomorrowLine = tomorrow?.let { "Tomorrow$SEPARATOR${it.title} ${clock(it.beginMillis)}" }

        if (weather == null && events == null) {
            // Nothing to show: where to turn something on, or, with Weather on and waiting, that it has none yet.
            val line = if (input.weatherDue) NO_WEATHER else TURN_ON
            return DiscoverTexts(
                pill = DiscoverPill(null, line, null),
                tile = DiscoverTile(top = "DISCOVER", hero = null, headline = line, lead = null, time = null, accentBottom = false),
                wide = DiscoverWide(label = "DISCOVER", badge = null, title = line, weather = null),
                dot = null,
                setUp = !input.weatherDue,
                spoken = "Discover. $line."
            )
        }

        val time = next?.let { clock(it.beginMillis) }
        val until = next?.let { it.beginMillis - nowMs }
        val badge = until?.takeIf { it <= BADGE_WINDOW_MS }?.let { Durations.until(it).replaceFirstChar { c -> c.lowercase(locale) } }
        val leaveBy = input.leaveAt?.takeIf { next != null && it > nowMs }?.let { "Leave by ${clock(it)}" }
        // The event's own line, where there is one: its name, or that the day is clear.
        val eventLead = when {
            events == null -> null
            next != null -> next.title
            else -> NOTHING_ELSE
        }

        val pill = DiscoverPill(
            temperature = temperature,
            lead = eventLead ?: conditions.orEmpty(),
            time = time?.let { SEPARATOR + it }
        )

        val tile = if (weather != null) {
            DiscoverTile(
                top = conditions.orEmpty(),
                hero = temperature,
                headline = null,
                // Without events, the line under the temperature is the rain ahead, when there is some.
                lead = eventLead ?: rain,
                time = time?.let { SEPARATOR + it },
                accentBottom = false
            )
        } else {
            DiscoverTile(
                top = if (next != null) UP_NEXT else TODAY,
                hero = null,
                headline = eventLead,
                lead = if (next != null) listOfNotNull(time, badge).joinToString(SEPARATOR) else tomorrowLine,
                time = null,
                accentBottom = next != null
            )
        }

        val wide = if (events != null) {
            DiscoverWide(
                label = if (next != null) UP_NEXT else TODAY,
                badge = badge,
                title = eventLead.orEmpty(),
                before = time,
                place = if (next != null) next.location.trim().takeIf { it.isNotEmpty() } else tomorrowLine,
                after = leaveBy,
                weather = weather?.let { listOfNotNull("$temperature ${WeatherCodes.describe(it.code)}", "H " + degrees(it.high), rain).joinToString(SEPARATOR) }
            )
        } else {
            // Weather alone leads the wide size: no strip repeating it underneath.
            DiscoverWide(
                label = TODAY,
                badge = null,
                title = "$temperature$SEPARATOR${WeatherCodes.describe(weather!!.code)}",
                before = "H " + degrees(weather.high) + SEPARATOR + "L " + degrees(weather.low),
                after = rain,
                weather = null
            )
        }

        val spokenWeather = weather?.let {
            listOfNotNull("$temperature and ${WeatherCodes.describe(it.code)}, high ${degrees(it.high)}", rain).joinToString(", ")
        }
        val spokenEvent = when {
            events == null -> null
            next != null -> listOfNotNull(
                "Up next, ${next.title} at $time", badge, next.location.trim().takeIf { it.isNotEmpty() }?.let { "at $it" }, leaveBy?.replaceFirstChar { it.lowercase(locale) }
            ).joinToString(", ")
            else -> listOfNotNull(NOTHING_ELSE, tomorrow?.let { "tomorrow, ${it.title} at ${clock(it.beginMillis)}" }).joinToString(", ")
        }
        return DiscoverTexts(
            pill = pill,
            tile = tile,
            wide = wide,
            dot = dot,
            setUp = false,
            spoken = "Discover. " + listOfNotNull(spokenWeather, spokenEvent).joinToString(". ") + "."
        )
    }

    private fun degrees(value: Double): String = "${value.roundToInt()}°"
}
