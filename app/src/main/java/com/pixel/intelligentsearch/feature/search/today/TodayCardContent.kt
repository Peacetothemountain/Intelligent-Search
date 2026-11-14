package com.pixel.intelligentsearch.feature.search.today

import android.content.Context
import android.os.SystemClock
import com.pixel.intelligentsearch.core.answers.HighLow
import com.pixel.intelligentsearch.core.answers.WeatherCodes
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.glance.GlanceAlarm
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.glance.GlanceTimer
import com.pixel.intelligentsearch.core.glance.timerReading
import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.feature.search.today.cards.ShapedContent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * What Discover's built-in cards draw when the user gives them a shape: each reads the same data the card's full
 * design does, so a shaped card never says anything the page wouldn't.
 */

/** Hours of the weather ahead a shaped weather card rings itself with. */
private const val SHAPED_HOURS = 8

/** The chance of rain (percent) an hour must reach to count as rain on the ring and in the headline. */
private const val RAIN_CHANCE = 50

/**
 * The weather now and the hours ahead: the temperature, its icon, and a headline that names the first rainy hour ahead
 * ("Rain around 8 PM", or "Snow around 8 PM" when that hour's weather is snow) or else describes the sky now.
 */
internal fun shapedWeather(context: Context, report: WeatherReport, now: Long): ShapedContent.Weather {
    val zone = ZoneId.systemDefault()
    val hours = report.hours.filter { it.time.toInstant().toEpochMilli() + 3_600_000L > now }.take(SHAPED_HOURS)
    val rain = hours.map { (it.precipitation ?: 0) >= RAIN_CHANCE || WeatherCodes.kind(it.code).isWet() }
    val firstRain = hours.indices.firstOrNull { rain[it] }
    val headline = if (firstRain != null && firstRain > 0) {
        val at = hours[firstRain].time.withZoneSameInstant(zone)
        // The full card's words: the hour's own weather says whether it is rain or snow.
        val falling = if (WeatherCodes.kind(hours[firstRain].code) == WeatherCodes.Kind.SNOW) "Snow" else "Rain"
        "$falling around " + android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(at.toInstant().toEpochMilli()))
    } else {
        WeatherCodes.describe(report.code)
    }
    return ShapedContent.Weather(
        temperature = "${report.temperature.roundToInt()}°",
        icon = weatherIcon(report.code, report.isDay),
        headline = headline,
        hours = hours.map { it.temperature.roundToInt() },
        rain = rain
    )
}

/** Whether weather of this kind falls from the sky. */
private fun WeatherCodes.Kind.isWet(): Boolean = this in setOf(
    WeatherCodes.Kind.DRIZZLE, WeatherCodes.Kind.RAIN, WeatherCodes.Kind.FREEZING_RAIN,
    WeatherCodes.Kind.SNOW, WeatherCodes.Kind.SHOWERS, WeatherCodes.Kind.THUNDERSTORM
)

/** A running timer or stopwatch now, and how far through its minute it is (the ring's sweep). */
internal fun shapedTimer(timer: GlanceTimer, now: Long): ShapedContent.Timer {
    val kind = if (timer.kind == GlanceTimer.Kind.Stopwatch) "Stopwatch" else "Timer"
    val label = timer.label.takeIf { it.isNotBlank() && !it.equals(kind, ignoreCase = true) } ?: kind
    return ShapedContent.Timer(
        reading = timerReading(timer, now).removeSuffix(" left"),
        label = label,
        sweep = if (timer.kind == GlanceTimer.Kind.TimesUp) 0f else (now % 60_000L) / 60_000f
    )
}

/** The next alarm: its time, its day ("Today" or "Tomorrow" or the weekday), how long until it, and its place round a day's dial. */
internal fun shapedAlarm(context: Context, alarm: GlanceAlarm, now: Long): ShapedContent.Alarm {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(alarm.triggerAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = when (at.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> at.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, context.resources.configuration.locales[0])
    }
    val fullTime = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(alarm.triggerAt))
    val time = fullTime.replace(Regex("\\s?[AaPp]\\.?[Mm]\\.?$"), "")
    return ShapedContent.Alarm(
        time = time,
        day = day,
        until = Durations.until(alarm.triggerAt - now),
        fraction = ((alarm.triggerAt - now).toFloat() / (24 * 3_600_000f)).coerceIn(0f, 1f),
        spokenTime = fullTime
    )
}

/** What is playing, with how far through the track it is now. */
internal fun shapedMedia(media: GlanceMedia): ShapedContent.Media {
    val progress = media.progress?.let { p ->
        if (p.durationMs <= 0L) null else {
            val moved = ((SystemClock.elapsedRealtime() - p.updatedAtElapsed) * p.speed).toLong()
            ((p.positionMs + moved).toFloat() / p.durationMs).coerceIn(0f, 1f)
        }
    }
    return ShapedContent.Media(media.title, media.artist, media.artwork, progress, media.isPlaying)
}

/** The battery now, in the battery tile's own words. */
internal fun shapedBattery(battery: TodayBattery): ShapedContent.Battery {
    val detail = when {
        battery.full -> "Charged"
        battery.charging && battery.minutesToFull != null -> "Full in ${Durations.minutes(battery.minutesToFull * 60_000L)}"
        battery.charging -> "Charging"
        battery.pluggedNotCharging -> "Connected, not charging"
        battery.saver -> "Battery Saver on"
        else -> "Not charging"
    }
    return ShapedContent.Battery(battery.level, battery.charging, detail)
}

/** Days until the next holiday, as the countdown card's shape draws them. */
internal fun shapedCountdown(countdown: TodayCountdown): ShapedContent.Countdown = ShapedContent.Countdown(countdown.days, countdown.name)

/** The word of the day. */
internal fun shapedWord(word: TodayWord): ShapedContent.Word = ShapedContent.Word(word.word, word.kind, word.meaning)

/** A package on its way. */
internal fun shapedDelivery(delivery: TodayDelivery): ShapedContent.Delivery = ShapedContent.Delivery(delivery.headline, delivery.detail)

/** The next event: its time, its name, how long until it, and how much of the hour before it has gone. */
internal fun shapedEvent(context: Context, event: CalendarEvent, now: Long): ShapedContent.Event {
    val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(event.beginMillis))
    val left = (event.beginMillis - now).coerceAtLeast(0L)
    return ShapedContent.Event(
        title = event.title,
        time = time,
        until = Durations.until(left),
        fraction = 1f - (left / 3_600_000f).coerceIn(0f, 1f)
    )
}

/** The rest of today: how many things are left (sunset among them), the next one, and their hours of the day. */
internal fun shapedAgenda(context: Context, events: List<CalendarEvent>, sunset: Long?): ShapedContent.Agenda? {
    val zone = ZoneId.systemDefault()
    val times = (events.map { it.beginMillis to it.title } + listOfNotNull(sunset?.let { it to "Sunset" })).sortedBy { it.first }
    val first = times.firstOrNull() ?: return null
    val format = android.text.format.DateFormat.getTimeFormat(context)
    return ShapedContent.Agenda(
        count = times.size,
        next = format.format(java.util.Date(first.first)),
        nextTitle = first.second,
        hours = times.map { (at, _) ->
            val t = Instant.ofEpochMilli(at).atZone(zone)
            t.hour + t.minute / 60f
        }
    )
}

/** What there is to catch up on: how many, and who the latest is from. */
internal fun shapedCatchUp(entries: List<IndexedNotification>): ShapedContent.CatchUp? {
    val latest = entries.maxByOrNull { it.postedAt } ?: return null
    return ShapedContent.CatchUp(entries.size, latest.title.ifBlank { latest.appLabel })
}

/** Tomorrow: its day, its weather, and the first of the alarm and the first event. */
internal fun shapedTomorrow(
    context: Context,
    date: LocalDate,
    alarmAt: Long?,
    weather: WeatherReport?,
    firstEvent: CalendarEvent?
): ShapedContent.Tomorrow {
    val locale = context.resources.configuration.locales[0]
    val format = android.text.format.DateFormat.getTimeFormat(context)
    val detail = when {
        alarmAt != null -> "Alarm · " + format.format(java.util.Date(alarmAt))
        firstEvent != null -> firstEvent.title + " · " + format.format(java.util.Date(firstEvent.beginMillis))
        weather != null -> WeatherCodes.describe(weather.code)
        else -> ""
    }
    return ShapedContent.Tomorrow(
        day = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", locale)),
        icon = weather?.let { weatherIcon(it.code, true) },
        temperatures = weather?.let { HighLow.drawn(it.high, it.low) } ?: "",
        detail = detail,
        spokenTemperatures = weather?.let { HighLow.spoken(it.high, it.low) } ?: ""
    )
}
