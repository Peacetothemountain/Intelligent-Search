package com.pixel.intelligentsearch.feature.search.today

import android.content.Context
import android.os.SystemClock
import com.pixel.intelligentsearch.core.answers.WeatherCodes
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.glance.GlanceAlarm
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
 * ("Rain at 8 PM") or else describes the sky now.
 */
internal fun shapedWeather(context: Context, report: WeatherReport, now: Long): ShapedContent.Weather {
    val zone = ZoneId.systemDefault()
    val hours = report.hours.filter { it.time.toInstant().toEpochMilli() + 3_600_000L > now }.take(SHAPED_HOURS)
    val rain = hours.map { (it.precipitation ?: 0) >= RAIN_CHANCE || WeatherCodes.kind(it.code).isWet() }
    val firstRain = hours.indices.firstOrNull { rain[it] }
    val headline = if (firstRain != null && firstRain > 0) {
        val at = hours[firstRain].time.withZoneSameInstant(zone)
        "Rain at " + android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(at.toInstant().toEpochMilli()))
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
        reading = timerReading(timer, now).removeSuffix(" Left"),
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
    val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(alarm.triggerAt))
        .replace(Regex("\\s?[AaPp]\\.?[Mm]\\.?$"), "")
    return ShapedContent.Alarm(
        time = time,
        day = day,
        until = Durations.until(alarm.triggerAt - now).replaceFirstChar { it.lowercase() },
        fraction = ((alarm.triggerAt - now).toFloat() / (24 * 3_600_000f)).coerceIn(0f, 1f)
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
        battery.pluggedNotCharging -> "Not charging"
        battery.saver -> "Battery Saver"
        else -> "Not charging"
    }
    return ShapedContent.Battery(battery.level, battery.charging, detail)
}

/** The year card's shaped design: the day's photo when there is one, else the countdown, else how far through the year. */
internal fun shapedYear(today: LocalDate, countdown: TodayCountdown?, memory: TodayMemory?): ShapedContent = when {
    memory != null -> ShapedContent.Memory(memory.thumbnail, memory.title, memory.detail)
    countdown != null -> ShapedContent.Countdown(countdown.days, countdown.name)
    else -> ShapedContent.Year(today.year, (today.dayOfYear * 100f / today.lengthOfYear()).roundToInt(), today.dayOfYear, today.lengthOfYear())
}

/** The word of the day. */
internal fun shapedWord(word: TodayWord): ShapedContent.Word = ShapedContent.Word(word.word, word.kind, word.meaning)

/** A package on its way. */
internal fun shapedDelivery(delivery: TodayDelivery): ShapedContent.Delivery = ShapedContent.Delivery(delivery.headline, delivery.detail)
