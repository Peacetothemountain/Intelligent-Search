package com.pixel.intelligentsearch.feature.search.today

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.core.answers.ForecastDay
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
import com.pixel.intelligentsearch.core.ui.icons.filled.History
import com.pixel.intelligentsearch.core.ui.icons.filled.HourglassBottom
import com.pixel.intelligentsearch.core.ui.icons.filled.NightsStay
import com.pixel.intelligentsearch.core.ui.icons.filled.Person
import com.pixel.intelligentsearch.core.ui.icons.filled.Thunderstorm
import com.pixel.intelligentsearch.core.ui.icons.filled.Umbrella
import com.pixel.intelligentsearch.core.ui.icons.filled.WbCloudy
import com.pixel.intelligentsearch.core.ui.icons.filled.WbSunny
import com.pixel.intelligentsearch.core.ui.icons.outlined.BatterySaver
import com.pixel.intelligentsearch.core.ui.icons.outlined.BatteryStd
import com.pixel.intelligentsearch.core.ui.icons.outlined.DoNotDisturbOn
import com.pixel.intelligentsearch.core.ui.icons.outlined.FlashlightOn
import com.pixel.intelligentsearch.core.ui.icons.outlined.WifiTethering
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
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

/** A section's title over its card. */
@Composable
private fun SectionTitle(text: String, detail: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 0.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = scheme.onPage, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = scheme.accentOnPage)
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
    Column(
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
            Text("Weather ahead", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f))
            if (rainAt >= 0) {
                Text(
                    "$falling likely around ${hours[rainAt].time.withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h a", locale))}",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant
                )
            } else {
                Text(
                    HighLow.drawn(report.high, report.low),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(HighLow.spoken(report.high, report.low)) }
                )
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
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
                    val bottom = 60.dp.toPx()
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


/** Missed calls, unread messages and emails, each opening where it came from. */
@Composable
internal fun CatchUp(entries: List<IndexedNotification>, now: Long) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Column {
        SectionTitle("Catch up", "From your notifications")
        Spacer(Modifier.height(8.dp))
        Card(padding = 6) {
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
 * Do Not Disturb, the flashlight (on a phone that has one), the hotspot and Battery Saver. The flashlight and Do Not
 * Disturb (once allowed) switch here, their tiles turning round when on; Android lets only its own Settings switch the
 * hotspot and Battery Saver, so those open the page that does. Android doesn't tell apps whether the hotspot is on, so
 * its tile says neither on nor off.
 */
@Composable
internal fun QuickControls(controls: TodayControls) {
    val context = LocalContext.current
    // Live, so a switch flipped in the notification shade while the page is open shows here too.
    val dnd by remember(controls) { controls.doNotDisturb() }.collectAsState(initial = false)
    // A phone without a flashlight has no tile for it rather than one that does nothing.
    val flashlight = remember(controls) { controls.flashlight() }
    val torch = flashlight?.collectAsState(initial = false)?.value
    val saver by remember(controls) { controls.batterySaver() }.collectAsState(initial = false)
    Column {
        SectionTitle("Quick controls")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlTile("Do Not Disturb", Icons.Outlined.DoNotDisturbOn, dnd, Modifier.weight(1f)) {
                controls.setDoNotDisturb(!dnd)?.let { launchSafeIntent(context, it) }
            }
            if (torch != null) {
                ControlTile("Flashlight", Icons.Outlined.FlashlightOn, torch, Modifier.weight(1f)) { controls.setFlashlight(!torch) }
            }
            ControlTile("Hotspot", Icons.Outlined.WifiTethering, null, Modifier.weight(1f)) {
                launchSafeIntent(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
            ControlTile("Battery Saver", Icons.Outlined.BatterySaver, saver, Modifier.weight(1f)) {
                launchSafeIntent(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            }
        }
    }
}

/** A quick control; [state] is null for one the app can't read, which then claims neither on nor off. */
@Composable
private fun ControlTile(label: String, icon: ImageVector, state: Boolean?, modifier: Modifier, onClick: () -> Unit) {
    val on = state == true
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    val corner by androidx.compose.animation.core.animateDpAsState(
        if (on) 28.dp else 18.dp,
        if (animate) spring(dampingRatio = 0.6f, stiffness = 420f) else snap(),
        label = "control shape"
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The button TalkBack lands on says its name and, for a control the app can read, that it is a switch and
        // whether it is on; the label under it is the same name, so it isn't a second stop.
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(corner))
                .background(if (on) scheme.primary else scheme.surfaceContainerHigh)
                .bouncyClickable(shape = RoundedCornerShape(corner), role = if (state != null) Role.Switch else Role.Button, onClick = onClick)
                .semantics { if (state != null) toggleableState = ToggleableState(on) },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (on) scheme.onPrimary else scheme.onSurface, modifier = Modifier.size(24.dp))
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
    val screenTime = produceState<ScreenTimeReport?>(null, active) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { sources.screenTime() }
    }.value
    // The tile shows only with Usage Access, so a tap opens the full breakdown in Digital Wellbeing, as the search's
    // screen time answer does; a phone without it has nowhere better to go, so the tile isn't a button there.
    val wellbeing = remember(context) { digitalWellbeingIntent(context) }
    if (battery == null && screenTime == null) return
    Column(Modifier.onFirstSeen { phoneSeen.value = true }) {
        SectionTitle("Your phone today")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (battery != null) BatteryTile(battery, Modifier.weight(1f).fillMaxHeight(), phoneSeen.value)
            if (screenTime != null) {
                ScreenTimeTile(screenTime, Modifier.weight(1f).fillMaxHeight(), phoneSeen.value, wellbeing?.let { { launchSafeIntent(context, it) } })
            }
        }
    }
}

@Composable
private fun TileLabel(icon: ImageVector, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label.uppercase(LocalConfiguration.current.locales[0]),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = scheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun BatteryTile(battery: TodayBattery, modifier: Modifier, seen: Boolean) {
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
    Column(
        modifier
            .heightIn(min = 108.dp)
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .bouncyClickable(shape = CardShape) { launchSafeIntent(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
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
    Column {
        SectionTitle("Pick up where you left off")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (query in searches) {
                Column(
                    Modifier
                        .width(128.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
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

/** The days until the next big holiday, and how far through the year today is, side by side at the taller one's height. */
@Composable
internal fun YearTiles(today: LocalDate, countdown: TodayCountdown?, memory: TodayMemory? = null, askPhotos: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    var seen by remember { mutableStateOf(false) }
    val shown = rememberDrawIn(300, 1100, seen)
    val confetti = LocalConfetti.current
    var tileCenter by remember { mutableStateOf(Offset.Zero) }
    Row(Modifier.height(IntrinsicSize.Min).onFirstSeen { seen = true }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (countdown != null) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 120.dp)
                    .clip(CardShape)
                    .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
                    .onGloballyPositioned { tileCenter = it.boundsInWindow().center }
                    .bouncyClickable(shape = CardShape) { confetti.burst(tileCenter) }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                TileLabel(Icons.Filled.Event, "Countdown")
                CountUp(countdown.days, shown)
                Text(
                    if (countdown.days == 1) "day until ${countdown.name}" else "days until ${countdown.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        if (memory != null) {
            MemoryTile(memory, Modifier.weight(1f).fillMaxHeight())
            return@Row
        }
        if (askPhotos != null) {
            AskPhotosTile(askPhotos, Modifier.weight(1f).fillMaxHeight())
            return@Row
        }
        val dayOfYear = today.dayOfYear
        val daysInYear = today.lengthOfYear()
        val fraction = dayOfYear / daysInYear.toFloat()
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = 120.dp)
                .clip(CardShape)
                .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            TileLabel(Icons.Filled.HourglassBottom, today.year.toString())
            Text("${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.displaySmall, color = scheme.onSurface, modifier = Modifier.padding(top = 4.dp))
            Text("Day $dayOfYear of $daysInYear", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(scheme.surfaceContainerHighest)) {
                Box(Modifier.fillWidth { fraction * shown() }.height(6.dp).clip(CircleShape).background(scheme.tertiary))
            }
        }
    }
}

/** The countdown's number, counting up to [days] as [shown] draws in; only it recomposes as the number changes. */
@Composable
private fun CountUp(days: Int, shown: () -> Float) {
    val count by remember(days, shown) { derivedStateOf { (days * shown()).roundToInt() } }
    Text(
        "$count",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
        // TalkBack hears the real count, not the number partway through drawing in.
        modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics { text = AnnotatedString("$days") }
    )
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
            .background(scheme.surfaceContainerHigh)
            .padding(14.dp)
    ) {
        Text(
            "TOMORROW",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = scheme.onSurfaceVariant
        )
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


/** Photos from the past: the first as the tile's picture, its label, how long ago and how many. Tap opens it. */
@Composable
private fun MemoryTile(memory: TodayMemory, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // Where the label and the title sit, for the scrim behind them.
    var labelBox by remember { mutableStateOf(Rect.Zero) }
    var textBox by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier
            .heightIn(min = 120.dp)
            .clip(CardShape)
            .background(scheme.surfaceContainerHigh)
            .bouncyClickable(shape = CardShape) {
                launchSafeIntent(context, Intent(Intent.ACTION_VIEW, memory.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
    ) {
        memory.thumbnail?.let { picture ->
            androidx.compose.foundation.Image(
                picture,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize().kenBurns()
            )
            // Darker only where a bright photo would wash out the white text above it.
            Box(Modifier.matchParentSize().drawWithCache {
                val scrim = photoScrim(picture, size, labelBox, textBox)
                onDrawBehind { drawRect(scrim) }
            })
        }
        val text = if (memory.thumbnail != null) Color.White else scheme.onSurface
        Text(
            memory.label.uppercase(LocalConfiguration.current.locales[0]),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = text.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .onPlaced { labelBox = it.boundsInParent() }
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .onPlaced { textBox = it.boundsInParent() }
        ) {
            Text(memory.title, style = MaterialTheme.typography.titleMedium, color = text)
            Text(memory.detail, style = MaterialTheme.typography.bodySmall, color = text.copy(alpha = 0.85f))
        }
    }
}

/** On This Day before photos may be read: what it would show, and a button that asks for photo access. */
@Composable
private fun AskPhotosTile(onAllow: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = 120.dp)
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .bouncyClickable(shape = CardShape, onClick = onAllow)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        TileLabel(Icons.Filled.History, "On this day")
        Text(
            "A photo from your past, every day",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Allow Photos",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onPrimary,
            modifier = Modifier.clip(CircleShape).background(scheme.primary).padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
