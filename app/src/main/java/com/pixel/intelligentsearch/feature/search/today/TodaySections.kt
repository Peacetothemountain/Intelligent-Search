package com.pixel.intelligentsearch.feature.search.today

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.answers.HighLow
import com.pixel.intelligentsearch.core.answers.ScreenTimeReport
import com.pixel.intelligentsearch.core.answers.WeatherCodes
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.core.system.GoogleApps
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.core.system.openPendingOrApp
import com.pixel.intelligentsearch.core.ui.icons.automirrored.filled.MenuBook
import com.pixel.intelligentsearch.core.ui.icons.automirrored.filled.Message
import com.pixel.intelligentsearch.core.ui.icons.filled.AcUnit
import com.pixel.intelligentsearch.core.ui.icons.filled.Alarm
import com.pixel.intelligentsearch.core.ui.icons.filled.Call
import com.pixel.intelligentsearch.core.ui.icons.filled.Cloud
import com.pixel.intelligentsearch.core.ui.icons.filled.Email
import com.pixel.intelligentsearch.core.ui.icons.filled.Event
import com.pixel.intelligentsearch.core.ui.icons.filled.Forum
import com.pixel.intelligentsearch.core.ui.icons.outlined.CalendarMonth
import com.pixel.intelligentsearch.core.ui.icons.outlined.AutoAwesome
import com.pixel.intelligentsearch.feature.settings.Button
import com.pixel.intelligentsearch.feature.search.today.cards.grid.PartStatus
import com.pixel.intelligentsearch.core.ui.icons.outlined.ChevronRight
import com.pixel.intelligentsearch.core.ui.icons.filled.History
import com.pixel.intelligentsearch.core.ui.icons.filled.HourglassBottom
import com.pixel.intelligentsearch.core.ui.icons.filled.NightsStay
import com.pixel.intelligentsearch.core.ui.icons.filled.Person
import com.pixel.intelligentsearch.core.ui.icons.filled.Thunderstorm
import com.pixel.intelligentsearch.core.ui.icons.filled.Umbrella
import com.pixel.intelligentsearch.core.ui.icons.filled.WbCloudy
import com.pixel.intelligentsearch.core.ui.icons.filled.WbSunny
import com.pixel.intelligentsearch.core.ui.icons.outlined.BatteryStd
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.core.util.initialOf
import com.pixel.intelligentsearch.feature.search.digitalWellbeingIntent
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.runtime.mutableIntStateOf
import com.pixel.intelligentsearch.core.ui.icons.filled.LocalShipping
import com.pixel.intelligentsearch.core.ui.icons.automirrored.outlined.VolumeUp
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable

/** The notification index while [enabled]: Catch Up and the delivery tile read it. */
@Composable
internal fun rememberNotifications(sources: TodaySources, enabled: Boolean): List<IndexedNotification> {
    val flow = remember(sources, enabled) { if (enabled) sources.notifications() else kotlinx.coroutines.flow.flowOf(emptyList()) }
    val entries by flow.collectAsState(initial = emptyList())
    return entries
}

/** A package on its way, read from a shipping notification: how far along it is (1 on the way, 2 out for delivery, 3 delivered) and what it said. */
@Immutable
internal data class TodayDelivery(val step: Int, val headline: String, val detail: String, val entry: IndexedNotification)

/** "Delivered" said of the past only: "will be delivered" and "could not be delivered" are taken out first. */
private val DELIVERED = Regex("\\bdelivered\\b", RegexOption.IGNORE_CASE)
/** A delivery that didn't happen, in the carriers' words: not delivered, attempted, unable to deliver, an exception, sent back. */
private val NOT_DELIVERED = Regex(
    "(?:\\bnot|n['’]t)\\s+(?:yet\\s+)?(?:been\\s+|be\\s+)?delivered\\b" +
        "|\\b(?:could\\s*not|couldn['’]t|unable\\s+to|cannot|can['’]t)\\s+(?:be\\s+)?deliver" +
        "|\\bdelivery\\s+(?:was\\s+)?(?:attempted|failed|unsuccessful|exception)\\b|\\battempted\\s+(?:to\\s+)?deliver" +
        "|\\b(?:failed|missed|unsuccessful)\\s+delivery\\b|\\bundeliverable\\b|\\breturn(?:ed|ing)?\\s+to\\s+(?:the\\s+)?sender\\b",
    RegexOption.IGNORE_CASE
)
private val TO_BE_DELIVERED = Regex("\\b(?:will|to|should)\\s+be\\s+delivered\\b", RegexOption.IGNORE_CASE)
private val OUT_FOR_DELIVERY = Regex("out for delivery|arriving today|arrives today|will arrive today|delivery today|be delivered today", RegexOption.IGNORE_CASE)
/** A package held up: still coming, but not when it said. Read after [OUT_FOR_DELIVERY], so "delayed, now arriving today" stays today. */
private val DELAYED = Regex("\\bdelayed\\b|\\brunning\\s+late\\b|\\bbehind\\s+schedule\\b|\\brescheduled\\b", RegexOption.IGNORE_CASE)
private val ON_THE_WAY = Regex("\\bshipped\\b|on (its|the) way|in transit|arriving (tomorrow|on)|has left|departed|be delivered", RegexOption.IGNORE_CASE)
private val PACKAGE = Regex("package|parcel|order|shipment|delivery", RegexOption.IGNORE_CASE)

/**
 * The newest package news among the notifications from the last two days, if any: delivered, out for delivery,
 * delayed, or on its way. Only notifications that speak of a package, order or delivery count. When the newest says it
 * couldn't be delivered (in any of [NOT_DELIVERED]'s words), no step says that, so there is no tile rather than an older, happier one.
 * Its "today" is the day it was posted in [zone]: out for delivery on an earlier day, with no news since, says nothing
 * true about today, so there is no tile either. A conversation is read by its newest message alone: an older one in the
 * same thread (yesterday's attempt, last week's delivery, a chat's earlier words) is not news about this package.
 */
internal fun deliveryFrom(entries: List<IndexedNotification>, now: Long, zone: ZoneId): TodayDelivery? {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    for (entry in entries.filter { now - it.postedAt in 0..DELIVERY_WINDOW_MS }.sortedByDescending { it.postedAt }) {
        val text = "${entry.title} ${entry.newest}"
        if (!PACKAGE.containsMatchIn(text)) continue
        if (NOT_DELIVERED.containsMatchIn(text)) return null
        return when {
            DELIVERED.containsMatchIn(TO_BE_DELIVERED.replace(text, "")) -> TodayDelivery(3, "Delivered", entry.appLabel, entry)
            OUT_FOR_DELIVERY.containsMatchIn(text) ->
                if (Instant.ofEpochMilli(entry.postedAt).atZone(zone).toLocalDate() == today) {
                    TodayDelivery(2, "Arriving today", "Out for delivery", entry)
                } else null
            DELAYED.containsMatchIn(text) -> TodayDelivery(1, "Delayed", entry.appLabel, entry)
            ON_THE_WAY.containsMatchIn(text) -> TodayDelivery(1, "On the way", entry.appLabel, entry)
            else -> continue
        }
    }
    return null
}

private const val DELIVERY_WINDOW_MS = 48 * 3_600_000L

/** A package's tile: where it is, and its steps from shipped to here with the current one pulsing. Tap opens its notification. */
@Composable
internal fun DeliveryTile(delivery: TodayDelivery, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val animate by rememberAnimationsEnabled()
    // Read only where the dot is drawn, so the pulse redraws the dots and never rebuilds the tile.
    val pulse: LoopReader? = if (animate && delivery.step < 3) rememberLoop(1_800) else null
    Column(
        modifier
            .heightIn(min = 108.dp)
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .bouncyClickable(shape = CardShape) { openPendingOrApp(context, delivery.entry.contentIntent, delivery.entry.packageName) }
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        TileLabel(Icons.Filled.LocalShipping, "Package")
        Text(delivery.headline, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, modifier = Modifier.padding(top = 4.dp))
        Text(delivery.detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f, fill = false).height(10.dp))
        val on = scheme.primary
        val off = scheme.surfaceContainerHighest
        // One dot per label below it: Shipped (step 1), Today (2), Here (3).
        val current = delivery.step - 1
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val r = 5.dp.toPx()
            val y = size.height / 2
            val xs = (0..2).map { r + it * (size.width - 2 * r) / 2f }
            val beat = if (pulse != null) pulse() else 0f
            for (i in 0 until 2) drawLine(if (i < current) on else off, Offset(xs[i], y), Offset(xs[i + 1], y), strokeWidth = 3.dp.toPx())
            xs.forEachIndexed { i, x ->
                if (i == current && beat > 0f) drawCircle(on.copy(alpha = 0.5f * (1 - beat)), r + 9.dp.toPx() * beat, Offset(x, y))
                drawCircle(if (i <= current) on else off, r, Offset(x, y))
            }
        }
        // The headline already names the step; read here, the three names would sound like three steps done.
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {}) {
            Text("Shipped", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterStart))
            Text("Today", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            Text("Here", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** The notifications worth catching up on: missed calls, messages and emails from the last 12 hours, newest first. */
internal fun catchUpFrom(entries: List<IndexedNotification>, now: Long): List<IndexedNotification> = entries
    .filter { it.category in CATCH_UP_CATEGORIES && now - it.postedAt in 0..CATCH_UP_WINDOW_MS && it.title.isNotBlank() }
    .sortedByDescending { it.postedAt }
    .distinctBy { it.packageName to it.title }
    .take(3)

/**
 * The one line of [entry] a Catch Up row has room for: the first line of a conversation's newest message, or of an
 * email or call, its subject. All of it on one line would show only the first.
 */
private fun previewLine(entry: IndexedNotification): String? =
    entry.newest.lineSequence().firstOrNull { it.isNotBlank() }?.trim()

/**
 * [title]'s initial when it starts with a name, a letter or an emoji, else null: an unsaved number ("(650) 555-1234",
 * "+1 650...") has no initial, and its circle shows a person as the phone's own apps do.
 */
private fun nameInitial(title: String, locale: java.util.Locale): String? {
    val initial = initialOf(title, locale).ifEmpty { return null }
    val first = initial.codePointAt(0)
    return initial.takeIf { Character.isLetter(first) || Character.getType(first) == Character.OTHER_SYMBOL.toInt() }
}

private val CATCH_UP_CATEGORIES = setOf(Notification.CATEGORY_MISSED_CALL, Notification.CATEGORY_MESSAGE, Notification.CATEGORY_EMAIL)
private const val CATCH_UP_WINDOW_MS = 12 * 3_600_000L

/** A slow full turn every 14 seconds while [on] and animations are on, read as a rotation in degrees. */
@Composable
internal fun rememberSlowTurn(on: Boolean): LoopReader {
    val animate by rememberAnimationsEnabled()
    if (!on || !animate) return LoopReader { 0f }
    val turn = rememberLoop(14_000, LinearEasing)
    return remember(turn) { LoopReader { 360f * turn() } }
}

/** The weather's icon for a WMO [code], by day or night. */
internal fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (WeatherCodes.kind(code)) {
    WeatherCodes.Kind.CLEAR -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    WeatherCodes.Kind.PARTLY_CLOUDY -> if (isDay) Icons.Filled.WbCloudy else Icons.Filled.NightsStay
    WeatherCodes.Kind.CLOUDY, WeatherCodes.Kind.FOG -> Icons.Filled.Cloud
    WeatherCodes.Kind.DRIZZLE, WeatherCodes.Kind.RAIN, WeatherCodes.Kind.SHOWERS, WeatherCodes.Kind.FREEZING_RAIN -> Icons.Filled.Umbrella
    WeatherCodes.Kind.SNOW -> Icons.Filled.AcUnit
    WeatherCodes.Kind.THUNDERSTORM -> Icons.Filled.Thunderstorm
}

/** The weather app, else a web search for the weather. */
internal fun openWeather(context: Context) {
    val app = GoogleApps.weatherIntent(context)
    if (app == null || !launchSafeIntent(context, app)) {
        launchSafeIntent(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, "weather"))
    }
}

/** A plain card on the container color. */
@Composable
private fun Card(modifier: Modifier = Modifier, padding: Int = 14, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .padding(padding.dp)
    ) { content() }
}

/**
 * Grows from 0 to 1 once [start] is true, after [delayMs] (at once with animations off). Read as the part is drawn or
 * laid out, so drawing in redraws it and never rebuilds the card around it.
 */
@Composable
private fun rememberDrawIn(
    delayMs: Int = 200,
    durationMs: Int = 1000,
    start: Boolean = true,
    easing: Easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
): () -> Float {
    val animate by rememberAnimationsEnabled()
    val shown = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(start, animate) {
        if (animate && start) shown.animateTo(1f, tween(durationMs, delayMillis = delayMs, easing = easing))
    }
    return remember(shown) { { shown.value } }
}

/** [fillMaxWidth] at a [fraction] read as the layout is measured, so a bar drawing in measures itself again and nothing else. */
internal fun Modifier.fillWidth(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val inner = if (constraints.hasBoundedWidth) {
        val width = (constraints.maxWidth * fraction()).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        constraints.copy(minWidth = width, maxWidth = width)
    } else constraints
    val placeable = measurable.measure(inner)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** How many hours each rain bar in [WeatherAhead] starts behind its hour's dot. */
private const val RAIN_LAG = 1.5f

/** An overshooting ease for things that pop in: past full size a little, then back. */
private fun popIn(t: Float): Float {
    if (t <= 0f) return 0f
    if (t >= 1f) return 1f
    val c = 1.9f
    val u = t - 1f
    return 1f + (c + 1f) * u * u * u + c * u * u
}

/**
 * The next hours' temperatures as a smooth curve that draws itself in from the left, a dot and a temperature at each
 * hour and the hour under it, over a soft fill that fades in behind it. Tap opens the weather app.
 */
@Composable
internal fun WeatherAhead(report: WeatherReport) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    var seen by remember { mutableStateOf(false) }
    val drawIn = rememberDrawIn(delayMs = 250, durationMs = 1300, start = seen)
    // The fill under the line fades in on its own while the line is still drawing, as in the approved design.
    val fillIn = rememberDrawIn(delayMs = 700, durationMs = 800, start = seen, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))
    val drawn by remember(drawIn) { derivedStateOf { drawIn() >= 1f } }
    val animate by rememberAnimationsEnabled()
    // Once drawn, the line floats: each hour rises and falls a little in turn, a slow wave every 5 seconds. Read only
    // in the drawing, so the wave redraws the curve and never rebuilds the card.
    val wave: LoopReader? = if (animate && drawn) rememberLoop(5_000, LinearEasing) else null
    val measurer = rememberTextMeasurer()
    val dark = scheme.surface.luminance() < 0.5f
    val warm = if (dark) Color(0xFFF2B872) else Color(0xFFB5651D)
    val rain = if (dark) Color(0xFF7FB0F0) else Color(0xFF3A6FB8)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurface)
    val hourStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val hours = report.hours
    // The page is the phone's day, so its hours read on the phone's clock, as sunset and the events do
    val zone = java.time.ZoneId.systemDefault()
    val temps = hours.map { it.temperature }
    val lo = (temps.minOrNull() ?: 0.0) - 2
    val hi = (temps.maxOrNull() ?: 0.0) + 2
    val labels = hours.mapIndexed { i, h ->
        val pattern = if (is24) "HH" else if (i == 0) "h a" else "h"
        h.time.withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    // Laid out once per set of hours: the floating line redraws every frame, and the measurer keeps only a few
    // layouts, fewer than a forecast's labels, so measuring while drawing laid every label out again each frame.
    val tempTexts = remember(temps, labelStyle, measurer) { temps.map { measurer.measure("${it.roundToInt()}°", labelStyle) } }
    val hourTexts = remember(labels, hourStyle, measurer) { labels.map { measurer.measure(it, hourStyle) } }
    // The chart takes any height the card is given beyond its own.
    StretchColumn(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .bouncyClickable(shape = CardShape) { openWeather(context) }
            .onFirstSeen { seen = true }
            .padding(14.dp)
    ) {
        // The chance is of rain or snow alike, so the hour's own weather says which to name.
        val rainAt = hours.indexOfFirst { (it.precipitation ?: 0) >= 50 }
        val falling = if (rainAt >= 0 && WeatherCodes.kind(hours[rainAt].code) == WeatherCodes.Kind.SNOW) "Snow" else "Rain"
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rainAt >= 0) {
                CardLabel(
                    weatherIcon(report.code, report.isDay),
                    "Weather ahead",
                    detail = "$falling around ${hours[rainAt].time.withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h a", locale))}"
                )
            } else {
                CardLabel(weatherIcon(report.code, report.isDay), "Weather ahead", detail = HighLow.drawn(report.high, report.low), spokenDetail = HighLow.spoken(report.high, report.low))
            }
        }
        // A Box, not a Spacer: a Spacer is only as tall as a fixed height, and this one is at least 118 dp.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 118.dp)
                .padding(top = 6.dp)
                // TalkBack hears each hour in full and its chance of rain or snow, which the bars show only by height.
                .semantics {
                    contentDescription = hours.joinToString { hour ->
                        val time = hour.time.withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h a", locale))
                        val chance = hour.precipitation?.takeIf { it > 0 }?.let { percent ->
                            val kind = if (WeatherCodes.kind(hour.code) == WeatherCodes.Kind.SNOW) "snow" else "rain"
                            ", $percent% chance of $kind"
                        }.orEmpty()
                        "$time ${hour.temperature.roundToInt()}°$chance"
                    }
                }
                // The paths, the fill's gradient and the stroke are made once per size and refilled each frame: the
                // floating line redraws every frame, and new ones each time kept the collector busy at rest.
                .drawWithCache {
                    val left = 16.dp.toPx()
                    val right = size.width - 16.dp.toPx()
                    val top = 26.dp.toPx()
                    // The curve keeps its place over the rain bars and hours, and grows with a taller chart.
                    val bottom = size.height - 52.dp.toPx()
                    val step = if (hours.size > 1) (right - left) / (hours.size - 1) else 0f
                    val lift = 3.dp.toPx()
                    val xs = FloatArray(temps.size) { left + it * step }
                    val ys = FloatArray(temps.size)
                    val line = Path()
                    val area = Path()
                    val fill = Brush.verticalGradient(listOf(warm.copy(alpha = 0.32f), Color.Transparent), startY = top, endY = bottom + 12.dp.toPx())
                    val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                    onDrawBehind {
                        val shown = drawIn()
                        val filled = fillIn()
                        val phase = if (wave != null) wave() * (2 * Math.PI).toFloat() else 0f
                        for (i in temps.indices) {
                            val float = if (wave != null) kotlin.math.sin(phase + i * 0.8f) * lift else 0f
                            ys[i] = bottom - ((temps[i] - lo) / (hi - lo)).toFloat() * (bottom - top) + float
                        }
                        val last = xs.size - 1
                        line.rewind()
                        line.moveTo(xs[0], ys[0])
                        for (i in 0 until last) {
                            val i0 = maxOf(0, i - 1)
                            val i3 = minOf(last, i + 2)
                            line.cubicTo(
                                xs[i] + (xs[i + 1] - xs[i0]) / 6f, ys[i] + (ys[i + 1] - ys[i0]) / 6f,
                                xs[i + 1] - (xs[i3] - xs[i]) / 6f, ys[i + 1] - (ys[i3] - ys[i]) / 6f,
                                xs[i + 1], ys[i + 1]
                            )
                        }
                        area.rewind()
                        area.addPath(line)
                        area.lineTo(xs[last], bottom + 12.dp.toPx())
                        area.lineTo(xs[0], bottom + 12.dp.toPx())
                        area.close()
                        drawPath(area, fill, alpha = filled)
                        clipRect(right = size.width * shown) {
                            drawPath(line, warm, style = stroke)
                        }
                        // Rain chances as short bars along the bottom, growing in after the line. The lag behind the dots is inside
                        // the draw-in, so every bar, the last one too, is at its full height once the line is drawn.
                        val rainTop = bottom + 10.dp.toPx()
                        val rainBottom = size.height - 18.dp.toPx()
                        for (i in hours.indices) {
                            val chance = hours[i].precipitation ?: continue
                            if (chance <= 0) continue
                            val grow = popIn((shown * (xs.size + RAIN_LAG) - i - RAIN_LAG).coerceIn(0f, 1f))
                            val h = (rainBottom - rainTop) * chance / 100f * grow
                            drawRoundRect(
                                rain.copy(alpha = 0.75f),
                                topLeft = Offset(xs[i] - 5.dp.toPx(), rainBottom - h),
                                size = Size(10.dp.toPx(), h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                            )
                        }
                        for (i in xs.indices) {
                            val visible = ((shown * xs.size) - i).coerceIn(0f, 1f)
                            if (visible <= 0f) continue
                            val p = Offset(xs[i], ys[i])
                            val pop = popIn(visible)
                            drawCircle(warm, 3.6.dp.toPx() * pop, p)
                            drawCircle(scheme.surfaceContainer, 2.dp.toPx() * pop, p)
                            val temp = tempTexts[i]
                            drawText(temp, topLeft = Offset(p.x - temp.size.width / 2f, p.y - 8.dp.toPx() - temp.size.height), alpha = visible)
                            val hour = hourTexts[i]
                            drawText(hour, topLeft = Offset(p.x - hour.size.width / 2f, size.height - hour.size.height), alpha = visible)
                        }
                    }
                }
        )
    }
}


/**
 * A card's parts one under another, the last one taking any height the card was given beyond its content's own, so a
 * card made taller shows a taller picture rather than an empty band under it.
 */
@Composable
private fun StretchColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val heads = measurables.dropLast(1).map { it.measure(loose) }
        val used = heads.sumOf { it.height }
        val last = measurables.lastOrNull()?.measure(loose.copy(minHeight = (constraints.minHeight - used).coerceAtLeast(0)))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else maxOf(heads.maxOfOrNull { it.width } ?: 0, last?.width ?: 0, constraints.minWidth)
        layout(width, (used + (last?.height ?: 0)).coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            for (head in heads) {
                head.place(0, y)
                y += head.height
            }
            last?.place(0, y)
        }
    }
}

/** Missed calls, unread messages and emails, each opening where it came from. */
@Composable
internal fun CatchUp(entries: List<IndexedNotification>, now: Long) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Card(padding = 6) {
            CardLabel(Icons.Filled.Forum, "Catch up", detail = "${entries.size} new", modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp))
            entries.forEachIndexed { i, entry ->
                val (icon, label) = when (entry.category) {
                    Notification.CATEGORY_MISSED_CALL -> Icons.Filled.Call to "Call back"
                    Notification.CATEGORY_EMAIL -> Icons.Filled.Email to "Open"
                    else -> Icons.AutoMirrored.Filled.Message to "Reply"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .bouncyClickable(shape = RoundedCornerShape(18.dp)) { openPendingOrApp(context, entry.contentIntent, entry.packageName) }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Only a picture: the title beside it says who, so TalkBack reads that once.
                    Box(
                        Modifier.size(40.dp).background(scheme.secondaryContainer, CircleShape).clearAndSetSemantics {},
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = nameInitial(entry.title, locale)
                        if (initial != null) {
                            Text(initial, style = MaterialTheme.typography.titleMedium, color = scheme.onSecondaryContainer)
                        } else {
                            Icon(Icons.Filled.Person, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        DetailLine(previewLine(entry), Durations.ago(now - entry.postedAt))
                    }
                    Box(
                        Modifier.size(40.dp).background(scheme.primaryContainer, CircleShape).semantics { contentDescription = label },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                if (i < entries.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(start = 60.dp, end = 8.dp).height(1.dp).background(scheme.outlineVariant.copy(alpha = 0.5f)))
                }
            }
        }
    }
}

/**
 * A quick control's shape: an oval, 1.8 times as wide as it is tall (Android 17's Quick Settings tile's proportions,
 * measured on a Pixel 11 at 130 by 72 dp), its ends fully round.
 */
internal val ControlShape = RoundedCornerShape(percent = 50)
internal const val CONTROL_ASPECT = 1.8f

/**
 * A quick control, shaped as Android's own Quick Settings tiles are, on or off; [state] is null for one the app can't
 * read, which then claims neither on nor off.
 */
@Composable
internal fun ControlTile(label: String, icon: ImageVector, state: Boolean?, opens: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val on = state == true
    val scheme = MaterialTheme.colorScheme
    val ink = if (on) scheme.onPrimary else scheme.onSurface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The button TalkBack lands on says its name and what a tap does: a switch the app flips says it is a switch
        // and whether it is on; one that [opens] Android's panel or page for it is a button that says so, with whether
        // it is on where the app can read it. The label under it is the same name, so it isn't a second stop.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CONTROL_ASPECT)
                .clip(ControlShape)
                .background(if (on) scheme.primary else scheme.surfaceContainerHigh)
                .bouncyClickable(
                    shape = ControlShape,
                    role = if (state != null && !opens) Role.Switch else Role.Button,
                    onClickLabel = if (opens) "Open $label" else null,
                    onClick = onClick
                )
                .semantics {
                    if (state != null) {
                        if (opens) stateDescription = if (on) "On" else "Off" else toggleableState = ToggleableState(on)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = ink, modifier = Modifier.size(24.dp))
            // A control that opens a page shows it as Quick Settings does: a chevron at its end.
            if (opens) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = ink.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(16.dp)
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onPage,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics {}
        )
    }
}

/** The battery, as a ring that fills to its level, and today's screen time with the apps that took most of it, side by side at the taller one's height. */
@Composable
internal fun PhoneToday(sources: TodaySources, active: Boolean) {
    val context = LocalContext.current
    val phoneSeen = remember { mutableStateOf(false) }
    val battery = remember(sources, active) { if (active) sources.battery() else kotlinx.coroutines.flow.emptyFlow() }
        .collectAsState(initial = null).value
    // Whether Usage Access is granted, a quick check read first, then today's screen time, which takes longer. Battery
    // is slim only where Screen time can't join it: with access it starts as the full tile, not slim and then twice
    // the height once the screen time comes in.
    val usage = produceState<Pair<Boolean, ScreenTimeReport?>?>(null, active) {
        val allowed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { sources.screenTimeAllowed() }
        value = allowed to null
        if (allowed) value = true to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { sources.screenTime() }
    }.value ?: return
    val screenTime = usage.second
    // The tile shows only with Usage Access, so a tap opens the full breakdown in Digital Wellbeing, as the search's
    // screen time answer does; a phone without it has nowhere better to go, so the tile isn't a button there.
    val wellbeing = remember(context) { digitalWellbeingIntent(context) }
    if (battery == null && screenTime == null) return
    Column(Modifier.onFirstSeen { phoneSeen.value = true }) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (battery != null) BatteryTile(battery, Modifier.weight(1f).fillMaxHeight(), phoneSeen.value, compact = !usage.first)
            if (screenTime != null) {
                ScreenTimeTile(screenTime, Modifier.weight(1f).fillMaxHeight(), phoneSeen.value, wellbeing?.let { { launchSafeIntent(context, it) } })
            }
        }
    }
}

@Composable
private fun TileLabel(icon: ImageVector, label: String) = CardLabel(icon, label)

/**
 * Every Discover card's label, at its top inside it: a small icon in the accent color and the card's name in small
 * capitals, with an optional [detail] at the far end ([spokenDetail] is what TalkBack hears for it, when the drawn form
 * would read badly).
 */
@Composable
internal fun CardLabel(icon: ImageVector, label: String, modifier: Modifier = Modifier, detail: String? = null, spokenDetail: String? = detail) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            label.uppercase(LocalConfiguration.current.locales[0]),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }
        )
        if (detail != null) {
            Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(spokenDetail ?: detail) }
            )
        }
    }
}

@Composable
private fun BatteryTile(battery: TodayBattery, modifier: Modifier, seen: Boolean, compact: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val shown = rememberDrawIn(300, 1100, seen)
    val status = when {
        battery.full -> "Charged"
        battery.charging && battery.minutesToFull != null -> "Full in ${Durations.minutes(battery.minutesToFull * 60_000L)}"
        battery.charging -> "Charging"
        // Android's own words for each, so a phone held on its charger doesn't read as one off it.
        battery.pluggedNotCharging -> "Connected, but not charging"
        battery.saver -> "Battery Saver on"
        else -> "Not charging"
    }
    val box = modifier
        .clip(CardShape)
        .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
        .bouncyClickable(shape = CardShape) { launchSafeIntent(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
    if (compact) {
        val track = scheme.surfaceContainerHighest
        val fill = scheme.primary
        Column(box.padding(horizontal = 14.dp, vertical = 13.dp)) {
            CardLabel(Icons.Outlined.BatteryStd, "Battery", detail = "${battery.level}% · $status", spokenDetail = "${battery.level} percent, $status")
            Canvas(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp)) {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(track, cornerRadius = r)
                val w = size.width * battery.level / 100f * shown()
                // From the start edge: the right in a right-to-left language.
                val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - w else 0f
                drawRoundRect(fill, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = r)
            }
        }
        return
    }
    Column(
        box
            .heightIn(min = 108.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        TileLabel(Icons.Outlined.BatteryStd, "Battery")
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val track = scheme.surfaceContainerHighest
            val fill = scheme.primary
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(56.dp)) {
                    val stroke = 6.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(fill, -90f, 360f * battery.level / 100f * shown(), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${battery.level}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun ScreenTimeTile(report: ScreenTimeReport, modifier: Modifier, seen: Boolean, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val shown = rememberDrawIn(300, 900, seen)
    val top = report.topApps.take(3)
    val longest = top.maxOfOrNull { it.durationMs }?.coerceAtLeast(1L) ?: 1L
    Column(
        modifier
            .heightIn(min = 108.dp)
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            // Without a page to open it is still one stop for TalkBack, as the Battery tile beside it is.
            .then(if (onClick != null) Modifier.bouncyClickable(shape = CardShape, onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        TileLabel(Icons.Filled.HourglassBottom, "Screen time")
        Text(Durations.minutes(report.totalMs), style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, modifier = Modifier.padding(top = 4.dp))
        // The names share one column, as wide as the longest needs (at least 62 dp, at most two thirds of the row, so
        // the bars keep a third), so "Intelligent Search" reads whole and the bars still start together.
        val labelStyle = MaterialTheme.typography.labelSmall
        val measurer = rememberTextMeasurer()
        val widestName = remember(top, labelStyle) {
            top.maxOfOrNull { measurer.measure(it.label, labelStyle, maxLines = 1).size.width } ?: 0
        }
        val nameColumn = Modifier.layout { measurable, constraints ->
            val least = 62.dp.roundToPx()
            val most = maxOf(least, (constraints.maxWidth * 0.65f).toInt())
            val width = (widestName + 6.dp.roundToPx()).coerceIn(least, most)
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(width, placeable.height) { placeable.place(0, 0) }
        }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (app in top) {
                // The bar is drawn only, so TalkBack hears each app's time beside its name.
                val heard = "${app.label}, ${Durations.minutes(app.durationMs)}"
                Row(Modifier.clearAndSetSemantics { contentDescription = heard }, verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, style = labelStyle, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = nameColumn)
                    Box(Modifier.weight(1f).height(8.dp)) {
                        Box(
                            Modifier
                                .fillWidth { app.durationMs.toFloat() / longest * shown() }
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(scheme.primary)
                        )
                    }
                }
            }
        }
    }
}

/** The last searches, side by side; a tap searches again. */
@Composable
internal fun LeftOff(searches: List<String>, onSearch: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(padding = 0) {
        CardLabel(Icons.Filled.History, "Pick up where you left off", modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (query in searches) {
                Column(
                    Modifier
                        .width(128.dp)
                        .height(96.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(scheme.surfaceContainerHigh)
                        .bouncyClickable(shape = RoundedCornerShape(20.dp)) { onSearch(query) }
                        .padding(11.dp)
                ) {
                    Box(Modifier.size(30.dp).background(scheme.secondaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.History, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(query, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Search again", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Today's word, what kind of word it is and what it means, on the page's own card color with the word in the accent
 * color. Hear It says it aloud (Android's text to speech), Look it up searches its definition, and a tap anywhere else
 * on the card makes the letters hop.
 */
@Composable
internal fun WordOfTheDay(word: TodayWord, onSearch: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var speech by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var speaking by remember { mutableStateOf(false) }
    var hops by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { speech?.shutdown() } }
    // The ring turns only while the engine has the word: a speak it refuses never calls back to stop it.
    fun say(tts: android.speech.tts.TextToSpeech) {
        speaking = true
        if (tts.speak(word.word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "word") != android.speech.tts.TextToSpeech.SUCCESS) {
            speaking = false
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .bouncyClickable(shape = CardShape) { hops++ }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "WORD OF THE DAY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        color = scheme.onSurfaceVariant
                    )
                }
                HopText(word.word, hops, MaterialTheme.typography.headlineMedium, scheme.primary, Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(word.kind, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    Text(
                        "  ·  Look it up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.primary,
                        // TalkBack hears the link's words, not the drawn separator before them.
                        modifier = Modifier
                            .bouncyClickable { onSearch("define ${word.word}") }
                            .clearAndSetSemantics { text = AnnotatedString("Look it up") }
                    )
                }
            }
            // Read only where the ring is drawn, so it redraws the ring and never rebuilds the card.
            val ripple: LoopReader? = if (speaking) rememberLoop(1_000) else null
            val ring = scheme.primary
            Box(
                Modifier
                    .size(44.dp)
                    .drawBehind {
                        if (ripple != null) {
                            val r = ripple()
                            drawCircle(ring.copy(alpha = 1f - r), radius = size.minDimension / 2f * (1f + 0.35f * r), style = Stroke(2.dp.toPx()))
                        }
                    }
                    .clip(CircleShape)
                    .background(scheme.primaryContainer)
                    .bouncyClickable(shape = CircleShape) {
                        val ready = speech
                        if (ready != null) {
                            say(ready)
                        } else {
                            // A failed start can call back inside the constructor, before `made` is set: keep only an
                            // engine that started, so the next tap tries again.
                            var made: android.speech.tts.TextToSpeech? = null
                            var failed = false
                            made = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                                val tts = made
                                if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                                    failed = true
                                    if (tts != null) {
                                        tts.shutdown()
                                        if (speech === tts) speech = null
                                    }
                                    return@TextToSpeech
                                }
                                if (tts == null) return@TextToSpeech
                                tts.language = java.util.Locale.US
                                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}
                                    override fun onDone(utteranceId: String?) { speaking = false }
                                    @Deprecated("Deprecated in Java")
                                    override fun onError(utteranceId: String?) { speaking = false }
                                })
                                say(tts)
                            }
                            if (failed) made.shutdown() else speech = made
                        }
                        hops++
                    }
                    .semantics { contentDescription = "Hear It" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
        }
        Text(word.meaning, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * The one place the page asks for access: what the empty cards below would show once allowed, and a button for the
 * first thing to allow ([asks] holds one status for each thing, in the page's order). Allowed, it asks for the next,
 * and goes once nothing is left to allow.
 */
@Composable
internal fun AccessCard(asks: List<PartStatus>) {
    val scheme = MaterialTheme.colorScheme
    val first = asks.first()
    val shows = asks.mapNotNull { it.access?.shows }
    val list = when (shows.size) {
        1 -> shows[0]
        2 -> "${shows[0]} and ${shows[1]}"
        else -> shows.dropLast(1).joinToString(", ") + ", and " + shows.last()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        CardLabel(
            Icons.Outlined.AutoAwesome, "Show more here",
            detail = if (asks.size > 1) "${asks.size} to allow" else null
        )
        Text(
            "Discover can show $list.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = { first.fix?.invoke() },
            modifier = Modifier.align(Alignment.End).padding(top = 10.dp)
        ) {
            Text(first.button ?: "Allow", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Days until the next holiday or day to look forward to, counting up as it comes into view; a tap throws confetti. */
@Composable
internal fun CountdownCard(countdown: TodayCountdown) {
    val scheme = MaterialTheme.colorScheme
    var seen by remember { mutableStateOf(false) }
    val shown = rememberDrawIn(300, 1100, seen)
    val confetti = LocalConfetti.current
    var center by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier
            .fillMaxWidth()
            .onFirstSeen { seen = true }
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .onGloballyPositioned { center = it.boundsInWindow().center }
            .bouncyClickable(shape = CardShape) { confetti.burst(center) }
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        TileLabel(Icons.Filled.Event, "Countdown")
        Row(Modifier.padding(top = 2.dp)) {
            CountUp(countdown.days, shown, Modifier.alignByBaseline())
            Text(
                if (countdown.days == 1) "day until ${countdown.name}" else "days until ${countdown.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline().padding(start = 10.dp)
            )
        }
    }
}

/**
 * The countdown's number, counting up to [days] as [shown] draws in; only it recomposes as the number changes. It
 * keeps the final number's room from the start, in even-width digits, so the words beside it stay still as it counts.
 */
@Composable
private fun CountUp(days: Int, shown: () -> Float, modifier: Modifier = Modifier) {
    val count by remember(days, shown) { derivedStateOf { (days * shown()).roundToInt() } }
    val style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum")
    // TalkBack hears the real count, not the number partway through drawing in.
    Box(modifier.clearAndSetSemantics { text = AnnotatedString("$days") }, contentAlignment = Alignment.CenterEnd) {
        Text("$days", style = style, color = Color.Transparent)
        Text("$count", style = style, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Tomorrow: the alarm, the forecast and the first event, those it has. */
@Composable
internal fun Tomorrow(date: LocalDate, alarmAt: Long?, weather: WeatherReport?, firstEvent: CalendarEvent?) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .padding(14.dp)
    ) {
        CardLabel(Icons.Outlined.CalendarMonth, "Tomorrow")
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )
        if (alarmAt != null) TomorrowRow(Icons.Filled.Alarm, "Alarm", timeFormat.format(java.util.Date(alarmAt)))
        if (weather != null) {
            val kind = WeatherCodes.describe(weather.code).lowercase(locale)
            TomorrowRow(
                weatherIcon(weather.code, true),
                "${weather.high.roundToInt()}° / ${weather.low.roundToInt()}°, $kind",
                null,
                // Read aloud, "53° / 43°" doesn't say which is the high.
                spoken = "${HighLow.spoken(weather.high, weather.low)}, $kind"
            )
        }
        if (firstEvent != null) TomorrowRow(Icons.Filled.Event, firstEvent.title, timeFormat.format(java.util.Date(firstEvent.beginMillis)))
    }
}

@Composable
private fun TomorrowRow(icon: ImageVector, text: String, detail: String?, spoken: String = text) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp).clearAndSetSemantics { this.text = AnnotatedString(spoken) }
        )
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}


