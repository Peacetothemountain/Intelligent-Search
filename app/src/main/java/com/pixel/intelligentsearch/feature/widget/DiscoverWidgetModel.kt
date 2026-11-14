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
import kotlin.math.ceil
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
 * and has a place to ask for, so a missing forecast is one that couldn't be read, not one to turn on. [blocked] says
 * what keeps a switch that is on from showing anything, when there is nothing else to show.
 */
internal data class DiscoverInput(
    val weather: DiscoverWeather?,
    val events: List<CalendarEvent>?,
    val leaveAt: Long? = null,
    val weatherDue: Boolean = false,
    val blocked: DiscoverBlock? = null
)

/** What keeps Up Next or Weather from showing anything while its switch is on, so the widget can say what to change. */
internal enum class DiscoverBlock {
    /** Weather is on, and Web Suggestions is off. */
    WEB_SUGGESTIONS_OFF,
    /** Weather is on, and Web is off in Search Sources. */
    WEB_OFF,
    /** Weather is on, and Tor is the search engine. */
    TOR,
    /** Weather is on, with no place set in Discover and no location access. */
    NO_PLACE,
    /** Up Next is on, without calendar access. */
    NO_CALENDAR
}

/** What the small dot before the temperature says: sun, rain or snow on the way, or neither. */
internal enum class DiscoverDot { SUN, RAIN, PLAIN }

/**
 * One row high: the temperature, then the next event ("Dentist ·" and " 3:30 PM") or, without one, the conditions.
 * The dot ends the name, so a long name cut short ends at its "…" and the time follows, with no gap before a dot.
 */
internal data class DiscoverPill(val temperature: String?, val lead: String, val time: String?)

/** What the wide size has room for: the line under the event ([meta]) and the weather strip ([weather]). */
internal data class DiscoverWideRoom(val meta: Boolean, val weather: Boolean)

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
    const val NEEDS_WEB_SUGGESTIONS = "Turn on Web Suggestions for weather"
    const val NEEDS_WEB = "Turn on Web for weather"
    const val NO_WEATHER_WITH_TOR = "No weather while Tor is your engine"
    const val NEEDS_PLACE = "Set a weather place in Discover"
    const val NEEDS_CALENDAR = "Allow calendar access for Up Next"
    private const val SEPARATOR = " · "

    /** How soon an event starts is shown only this close to it, where a still picture can keep it true. */
    val BADGE_WINDOW_MS: Long = Duration.ofMinutes(60).toMillis()

    /**
     * The widget is drawn again as the next event comes this close, and as it starts. Between those redraws how soon
     * it starts is said as the step it is within ("within 30 min" from 30 minutes before until 15 before), which stays
     * true for the whole time the still picture is shown; its time is beside it for the exact moment.
     */
    private val BADGE_STEPS_MS: List<Long> = listOf(5L, 15L, 30L, 60L).map { Duration.ofMinutes(it).toMillis() }

    /** The step an event [untilMs] away is within: the first of [BADGE_STEPS_MS] at or above it; null past the hour. */
    private fun badgeStep(untilMs: Long): Long? = BADGE_STEPS_MS.firstOrNull { untilMs <= it }

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

    /** Whether [input] has nothing to show, so the widget says what to turn on and nothing changes with time. */
    fun isSetUp(input: DiscoverInput): Boolean = input.weather == null && input.events == null && !input.weatherDue

    /**
     * How long from [now] until the widget next has something new to say for [input], or null when nothing it shows
     * changes with time (it says what to turn on). With the weather shown, or due, an hour at most, so a newer
     * forecast is read and an old one is taken down. With events shown, midnight, when today's events become
     * tomorrow's. Sooner for the next event: an hour, 30, 15 and 5 minutes before it, when how soon it starts moves to
     * the next step, and as it starts; and when it is time to leave for it.
     */
    fun nextRedrawDelayMs(now: ZonedDateTime, input: DiscoverInput): Long? {
        if (isSetUp(input)) return null
        val nowMs = now.toInstant().toEpochMilli()
        val waits = mutableListOf<Long>()
        if (input.weather != null || input.weatherDue) waits += REDRAW_MS
        val events = input.events
        if (events != null) {
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
            waits += midnight - nowMs + AFTER_MS
            val next = nextEvent(events, now)
            if (next != null) {
                // The first step still ahead; at the step itself how soon is already the step, so no wait past it.
                BADGE_STEPS_MS.asReversed().map { next.beginMillis - it }.firstOrNull { it > nowMs }?.let { waits += it - nowMs }
                waits += next.beginMillis - nowMs + AFTER_MS
                input.leaveAt?.takeIf { it > nowMs }?.let { waits += it - nowMs + AFTER_MS }
            }
        }
        return waits.minOrNull()?.coerceAtLeast(AFTER_MS)
    }

    /** The widget is drawn again this often while it shows the weather. */
    val REDRAW_MS: Long = Duration.ofMinutes(60).toMillis()
    /** Just past the moment waited for, so the redraw finds it passed. */
    private const val AFTER_MS = 1_000L

    /**
     * The wide size's rows, in dp at 100% text, as widget_discover_wide.xml lays them out: the label row (its text,
     * and the padding of the badge beside it), the margins around the event's name, the name's smallest text, the
     * line under it, and the weather strip (its margin and padding, and the dot).
     */
    private const val LABEL_SP = 12f
    private const val BADGE_PADDING = 4f
    private const val NAME_MARGINS = 8f
    private const val NAME_MIN_SP = 16f
    private const val META_SP = 14f
    private const val STRIP_SP = 14f
    private const val STRIP_SPACE = 10f + 16f
    private const val STRIP_DOT = 16f
    /** One line of the app's font, as a multiple of its size. */
    private const val LINE = 1.25f

    /**
     * The height the wide size needs, in dp, with [ends] dp above and below its words, at [fontScale]: the label and
     * two lines of the event's name at its smallest, and the line under it ([meta]) and the weather strip
     * ([weather]) when asked.
     */
    private fun wideNeeds(fontScale: Float, ends: Int, meta: Boolean, weather: Boolean): Float {
        fun line(sp: Float) = sp * LINE * fontScale
        var need = 2f * ends + line(LABEL_SP) + BADGE_PADDING + NAME_MARGINS + 2 * line(NAME_MIN_SP)
        if (meta) need += line(META_SP)
        if (weather) need += STRIP_SPACE + maxOf(STRIP_DOT, line(STRIP_SP))
        return need
    }

    /**
     * What the wide size keeps at [heightDp] tall: the event's name always keeps two lines' room, so on a short cell
     * or with large text the weather strip gives way first ([hasWeather]: there is one to show), then the line under
     * the name ([hasMeta]).
     */
    fun wideRoom(heightDp: Float, fontScale: Float, ends: Int, hasMeta: Boolean, hasWeather: Boolean) = DiscoverWideRoom(
        meta = hasMeta && heightDp >= wideNeeds(fontScale, ends, meta = true, weather = false),
        weather = hasWeather && heightDp >= wideNeeds(fontScale, ends, meta = hasMeta, weather = true)
    )

    /**
     * The heights, from [from] up, at which the wide size has room for more ([wideRoom]): [from] itself, then where
     * the line under the name fits, then where the weather strip does. One layout is drawn for each, and the launcher
     * shows the tallest that fits the cell.
     */
    fun wideHeights(from: Float, fontScale: Float, ends: Int, hasMeta: Boolean, hasWeather: Boolean): List<Float> {
        val more = listOfNotNull(
            wideNeeds(fontScale, ends, meta = true, weather = false).takeIf { hasMeta },
            wideNeeds(fontScale, ends, meta = hasMeta, weather = true).takeIf { hasWeather }
        ).map { ceil(it) }.filter { it > from }
        return (listOf(from) + more).distinct().sorted()
    }

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
        val tomorrowLine = tomorrow?.let { "Tomorrow$SEPARATOR${it.title}$SEPARATOR${clock(it.beginMillis)}" }

        if (weather == null && events == null) {
            // Nothing to show: with Weather on and waiting, that it has none yet; else what keeps a switch that is on
            // from showing anything, or, with both off, where to turn one on.
            val line = if (input.weatherDue) NO_WEATHER else when (input.blocked) {
                DiscoverBlock.WEB_SUGGESTIONS_OFF -> NEEDS_WEB_SUGGESTIONS
                DiscoverBlock.WEB_OFF -> NEEDS_WEB
                DiscoverBlock.TOR -> NO_WEATHER_WITH_TOR
                DiscoverBlock.NO_PLACE -> NEEDS_PLACE
                DiscoverBlock.NO_CALENDAR -> NEEDS_CALENDAR
                null -> TURN_ON
            }
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
        // How soon it starts, in its last hour: the step it is within, which the next redraw moves on.
        val step = next?.let { badgeStep(it.beginMillis - nowMs) }
        val badge = step?.let { "within ${Durations.short(Duration.ofMillis(it).seconds)}" }
        val leaveBy = input.leaveAt?.takeIf { next != null && it > nowMs }?.let { "Leave by ${clock(it)}" }
        // The event's own line, where there is one: its name, or that the day is clear.
        val eventLead = when {
            events == null -> null
            next != null -> next.title
            else -> NOTHING_ELSE
        }
        // The name ends with the dot before its time, so a name cut short ends at its "…", and the time follows it.
        val leadBeforeTime = eventLead?.let { if (time != null) "$it$SEPARATOR".trimEnd() else it }
        val timeAfterLead = time?.let { " $it" }

        val pill = DiscoverPill(
            temperature = temperature,
            lead = leadBeforeTime ?: conditions.orEmpty(),
            time = timeAfterLead
        )

        val tile = if (weather != null) {
            DiscoverTile(
                top = conditions.orEmpty(),
                hero = temperature,
                headline = null,
                // Without events, the line under the temperature is the rain ahead, when there is some.
                lead = leadBeforeTime ?: rain,
                time = timeAfterLead,
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
            // Each part its own phrase, how soon in words: "Up next, Dentist at 3:30 PM, within 1 hour, at 1200 Main St".
            next != null -> listOfNotNull(
                "Up next, ${next.title} at $time", step?.let { spokenSoon(it) }, next.location.trim().takeIf { it.isNotEmpty() }?.let { "at $it" },
                leaveBy?.replaceFirstChar { it.lowercase(locale) }
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

    /** How soon, within [stepMs], as TalkBack says it: "within 1 hour", "within 30 minutes". */
    private fun spokenSoon(stepMs: Long): String {
        val minutes = Duration.ofMillis(stepMs).toMinutes()
        return if (minutes % 60 == 0L) {
            val hours = minutes / 60
            "within $hours ${if (hours == 1L) "hour" else "hours"}"
        } else {
            "within $minutes ${if (minutes == 1L) "minute" else "minutes"}"
        }
    }
}
