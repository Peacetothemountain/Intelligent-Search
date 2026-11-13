package com.pixel.intelligentsearch.feature.search.today

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.pixel.intelligentsearch.core.ui.icons.filled.Directions
import com.pixel.intelligentsearch.core.ui.icons.rounded.Pause
import com.pixel.intelligentsearch.core.ui.icons.rounded.PlayArrow
import com.pixel.intelligentsearch.core.ui.icons.outlined.Alarm
import com.pixel.intelligentsearch.core.ui.icons.outlined.Event
import com.pixel.intelligentsearch.core.ui.icons.outlined.LocationOn
import com.pixel.intelligentsearch.core.ui.icons.outlined.MusicNote
import com.pixel.intelligentsearch.core.ui.icons.outlined.Schedule
import com.pixel.intelligentsearch.core.ui.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.glance.GlanceItem
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.glance.GlanceTimer
import com.pixel.intelligentsearch.core.glance.timerReading
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.feature.search.SquigglySeekBar
import com.pixel.intelligentsearch.feature.search.calendarEventIntent
import com.pixel.intelligentsearch.feature.search.matteRing
import com.pixel.intelligentsearch.feature.search.openGlanceItem
import com.pixel.intelligentsearch.feature.search.rememberGlanceNow
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.core.ui.icons.filled.Call
import androidx.compose.ui.graphics.luminance

/*
 * The page's own card designs at their full size: Up Next, the rest of the day, timer and alarm tiles, Now Playing,
 * and the chips and details they share.
 *
 * NG Designs
 */

/** Up Next's corners, rounder than the other cards'. */
private val HeroShape = RoundedCornerShape(28.dp)

/** Sunset's label on Rest of Your Day. */
private const val SUNSET = "Sunset"

/**
 * A small one-line detail: [parts] (blank ones left out) drawn with dots between them. TalkBack hears them with pauses
 * instead, as it would otherwise be handed each dot to speak.
 */
@Composable
internal fun DetailLine(vararg parts: String?, modifier: Modifier = Modifier) {
    val shown = parts.filterNotNull().filter { it.isNotBlank() }
    Text(
        shown.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(shown.joinToString(", ")) }
    )
}

@Composable
internal fun TodayChip(
    icon: ImageVector?,
    text: String,
    detail: String?,
    /** What TalkBack hears for [detail], when its short drawn form would read as letters. */
    spokenDetail: String? = detail,
    spin: Boolean = false,
    leading: (@Composable (Color) -> Unit)? = null,
    /** A second line under [text] (the weather's moon); the pill grows a little to hold it. */
    below: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tall = below != null
    Row(
        Modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .bouncyClickable(shape = CircleShape, onClick = onClick)
            .heightIn(min = 38.dp)
            .padding(start = if (tall) 8.dp else 6.dp, end = if (tall) 18.dp else 12.dp, top = if (tall) 9.dp else 6.dp, bottom = if (tall) 9.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (tall) 34.dp else 26.dp).background(scheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            val turn = rememberSlowTurn(spin)
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(if (tall) 21.dp else 17.dp).graphicsLayer { rotationZ = turn() })
            } else {
                leading?.invoke(scheme.onTertiaryContainer)
            }
        }
        Spacer(Modifier.width(if (tall) 10.dp else 8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                if (detail != null) {
                    Text(
                        " · $detail",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        // TalkBack hears the detail, not the drawn separator before it.
                        modifier = Modifier.clearAndSetSemantics { this.text = AnnotatedString(spokenDetail ?: detail) }
                    )
                }
            }
            below?.invoke()
        }
    }
}

/** The next event: how long until it starts, when and where, and one tap to directions (when it has a place) or the event. */
@Composable
internal fun UpNextCard(event: CalendarEvent, now: Long, trip: TodayTrip?, askLocation: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val time = remember(event.beginMillis) {
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(event.beginMillis))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(scheme.primaryContainer)
            .sheen()
            .bouncyClickable(shape = HeroShape) { launchSafeIntent(context, calendarEventIntent(event)) }
            .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "UP NEXT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
                color = scheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                Durations.until(event.beginMillis - now).replaceFirstChar { it.lowercase() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onPrimary,
                modifier = Modifier.breathe().clip(CircleShape).background(scheme.primary).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Text(
            event.title,
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onPrimaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(17.dp))
            Text(" $time", style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
            if (event.location.isNotBlank()) {
                // Drawn only: TalkBack hears the time and the place with a pause between them.
                Text("  ·  ", style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.6f), modifier = Modifier.clearAndSetSemantics {})
                Icon(Icons.Outlined.LocationOn, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(17.dp))
                Text(
                    " ${event.location}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trip != null) {
            LeaveBy(trip, now)
        } else if (askLocation != null && event.location.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "See when to leave",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                PillButton("Allow Location", Icons.Outlined.LocationOn, filled = false, onClick = askLocation)
            }
        }
        FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (event.location.isNotBlank()) {
                PillButton("Directions", Icons.Filled.Directions, filled = true) {
                    launchSafeIntent(context, Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(event.location)}".toUri()))
                }
            }
            if (event.phone.isNotBlank()) {
                PillButton("Call", Icons.Filled.Call, filled = event.location.isBlank()) {
                    launchSafeIntent(context, Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(event.phone)}".toUri()))
                }
            }
            // With both Directions and Call there is no room for a third; the card itself opens the event.
            if (event.location.isBlank() || event.phone.isBlank()) {
                PillButton("Open Event", Icons.Outlined.Event, filled = event.location.isBlank() && event.phone.isBlank()) {
                    launchSafeIntent(context, calendarEventIntent(event))
                }
            }
        }
    }
}

/**
 * When to leave: the time, a bar filling over the hour before it, and about how long the trip takes. Past the time
 * it says to leave now.
 */
@Composable
private fun LeaveBy(trip: TodayTrip, now: Long) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val time = remember(trip.leaveAt) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(trip.leaveAt)) }
    val window = 60 * 60_000L
    val target = ((now - (trip.leaveAt - window)).toFloat() / window).coerceIn(0f, 1f)
    val animate by rememberAnimationsEnabled()
    // Read as the bar is laid out, so filling it never rebuilds the card.
    val fill = animateFloatAsState(target, if (animate) tween(800, delayMillis = 300) else snap(), label = "leave by")
    val about = "About ${Durations.minutes(trip.minutes * 60_000L)}"
    // Whether the drive's length had no room left on the line (the largest text on a narrow screen broke it a
    // letter to a line). Only then does it go under the line; wherever it fits, the row is as it always was.
    var squeezed by remember(time, about) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (trip.leaveAt <= now) "Leave now" else "Leave by $time",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer
            )
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(scheme.onPrimaryContainer.copy(alpha = 0.18f))
            ) {
                Box(Modifier.fillWidth { fill.value }.height(6.dp).clip(CircleShape).background(scheme.onPrimaryContainer))
            }
            if (!squeezed) {
                Text(
                    about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                    onTextLayout = { if (it.lineCount > 1) squeezed = true }
                )
            }
        }
        if (squeezed) Text(about, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
    }
}

@Composable
private fun PillButton(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = if (filled) scheme.primary else scheme.onPrimaryContainer.copy(alpha = 0.13f)
    val content = if (filled) scheme.onPrimary else scheme.onPrimaryContainer
    Row(
        Modifier
            .clip(CircleShape)
            .background(container)
            .bouncyClickable(shape = CircleShape, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(start = 13.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/**
 * The rest of today's events on one line from now: the part already gone filled in, a pin and label for each event
 * (labels taking turns above and below the line, a label with no room in either row left out so none collide), and a
 * dot for now.
 */
@Composable
internal fun RestOfDay(calendar: List<CalendarEvent>, now: Long, sunset: Long?) {
    // The next three of the day's events (the count says how many there are), with sunset among them as a warm pin
    // when it is still ahead: four labels at most, which fit two above the line and two below.
    val events = (calendar.take(3) + listOfNotNull(sunset?.let { CalendarEvent(SUNSET, "", -1L, it) })).sortedBy { it.beginMillis }
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val animate by rememberAnimationsEnabled()
    val shown by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (animate) tween(1000, delayMillis = 200, easing = EmphasizedDecelerate) else snap(),
        label = "rest of day"
    )
    // From a little before now to a little after the last event.
    val start = now - 20 * 60_000L
    val end = maxOf(events.last().beginMillis + 45 * 60_000L, now + 3 * 3_600_000L)
    fun at(ms: Long) = ((ms - start).toFloat() / (end - start)).coerceIn(0.03f, 0.97f)
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(Icons.Outlined.Schedule, "Rest of your day", detail = if (calendar.size == 1) "1 event" else "${calendar.size} events", modifier = Modifier.weight(1f))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp).height(96.dp)) {
            val width = maxWidth
            val line = scheme.surfaceContainerHighest
            val fill = scheme.primary
            val pin = scheme.onSurfaceVariant
            val pinFill = scheme.surfaceContainer
            val warm = if (scheme.surface.luminance() < 0.5f) Color(0xFFF2B872) else Color(0xFFB5651D)
            val nowAt = at(now)
            Canvas(Modifier.fillMaxSize()) {
                val y = 44.dp.toPx()
                val stroke = 4.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(fill, Offset(0f, y), Offset(size.width * nowAt * shown, y), strokeWidth = stroke, cap = StrokeCap.Round)
                for (event in events) {
                    val x = size.width * at(event.beginMillis)
                    drawCircle(pinFill, 5.dp.toPx(), Offset(x, y))
                    drawCircle(if (event.eventId == -1L) warm else pin, 5.dp.toPx() - 1.25.dp.toPx(), Offset(x, y), style = Stroke(2.5.dp.toPx()))
                }
                drawCircle(fill.copy(alpha = 0.25f), 12.dp.toPx(), Offset(size.width * nowAt * shown, y))
                drawCircle(fill, 8.dp.toPx(), Offset(size.width * nowAt * shown, y))
            }
            // Each label takes the row across from the one before it, or the other row when that one has no room left;
            // a label with no room in either keeps only its pin, so no two labels ever cover each other.
            val labelWidth = 96.dp
            var aboveEnd = (-1).dp
            var belowEnd = (-1).dp
            var preferAbove = true
            events.forEachIndexed { i, event ->
                val x = (width * at(event.beginMillis) - labelWidth / 2).coerceIn(0.dp, width - labelWidth)
                val above = when {
                    x >= (if (preferAbove) aboveEnd else belowEnd) -> preferAbove
                    x >= (if (preferAbove) belowEnd else aboveEnd) -> !preferAbove
                    else -> return@forEachIndexed
                }
                if (above) aboveEnd = x + labelWidth else belowEnd = x + labelWidth
                preferAbove = !above
                Column(
                    Modifier
                        .width(labelWidth)
                        .offset(x = x, y = if (above) 0.dp else 54.dp)
                        .graphicsLayer { alpha = ((shown - 0.3f - i * 0.08f) / 0.4f).coerceIn(0f, 1f) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(event.title, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(timeFormat.format(java.util.Date(event.beginMillis)), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A running timer (counting down each second, a ring sweeping each minute) and the next alarm, side by side at the taller one's height. */
@Composable
internal fun TimerAlarmTiles(timer: GlanceTimer?, alarm: GlanceItem.Alarm?, delivery: TodayDelivery?, now: Long) {
    val tiles = listOfNotNull<@Composable (Modifier) -> Unit>(
        timer?.let { { m: Modifier -> TimerTile(it, m) } },
        delivery?.let { { m: Modifier -> DeliveryTile(it, m) } },
        alarm?.let { { m: Modifier -> AlarmTile(it, now, m) } }
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (pair in tiles.chunked(2)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (tile in pair) tile(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier
            .heightIn(min = 108.dp)
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .bouncyClickable(shape = CardShape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(locale), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        content()
    }
}

@Composable
private fun TimerTile(timer: GlanceTimer, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val now = rememberGlanceNow(1_000L, enabled = timer.kind != GlanceTimer.Kind.TimesUp)
    val kind = if (timer.kind == GlanceTimer.Kind.Stopwatch) "Stopwatch" else "Timer"
    val label = timer.label.takeIf { it.isNotBlank() && !it.equals(kind, ignoreCase = true) } ?: kind
    val reading = timerReading(timer, now).removeSuffix(" Left")
    Tile(Icons.Outlined.Timer, label, onClick = { openGlanceItem(context, GlanceItem.Timer(timer)) }, modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(reading, style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, modifier = Modifier.padding(top = 6.dp))
                DetailLine(kind, timer.appLabel)
            }
            // A ring sweeping once a minute, so the tile is visibly running.
            val sweep = (now % 60_000L) / 60_000f
            val track = scheme.surfaceContainerHighest
            val fill = scheme.primary
            Canvas(Modifier.size(44.dp)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (timer.kind != GlanceTimer.Kind.TimesUp) {
                    drawArc(fill, -90f, 360f * sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
private fun AlarmTile(alarm: GlanceItem.Alarm, now: Long, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val time = remember(alarm.alarm.triggerAt) {
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(alarm.alarm.triggerAt))
    }
    Tile(Icons.Outlined.Alarm, "Alarm", onClick = { openGlanceItem(context, alarm) }, modifier = modifier) {
        Text(time, style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, modifier = Modifier.padding(top = 6.dp))
        Text(Durations.until(alarm.alarm.triggerAt - now), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

/** What is playing: the cover, title and artist (a tap opens the player), Play or Pause, and the flowing seek bar. */
@Composable
internal fun NowPlaying(media: GlanceMedia) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    val corner by animateDpAsState(
        if (media.isPlaying) 22.dp else 14.dp,
        if (animate) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium) else snap(),
        label = "play button shape"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = if (media.progress != null) 4.dp else 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh)
                    .bouncyClickable(shape = CircleShape) { media.controls.open(context) },
                contentAlignment = Alignment.Center
            ) {
                val artwork = media.artwork
                if (artwork != null) {
                    Image(artwork, null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(CircleShape).matteRing())
                } else {
                    Icon(Icons.Outlined.MusicNote, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .bouncyClickable { media.controls.open(context) }
            ) {
                Text(media.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                DetailLine(media.artist, media.appLabel)
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(scheme.primary)
                    .bouncyClickable(shape = RoundedCornerShape(corner)) {
                        if (media.isPlaying) media.controls.pause() else media.controls.play()
                    }
                    .semantics { contentDescription = if (media.isPlaying) "Pause" else "Play" },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = scheme.onPrimary)
            }
        }
        media.progress?.let { progress ->
            SquigglySeekBar(progress = progress, playing = media.isPlaying, onSeek = media.controls::seekTo, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
