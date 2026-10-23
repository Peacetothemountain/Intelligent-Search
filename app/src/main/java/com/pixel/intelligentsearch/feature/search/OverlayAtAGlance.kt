package com.pixel.intelligentsearch.feature.search

import android.content.Context
import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.Intent
import android.os.SystemClock
import android.provider.AlarmClock
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.glance.AndroidGlanceSource
import com.pixel.intelligentsearch.core.glance.GlanceBattery
import com.pixel.intelligentsearch.core.glance.GlanceBirthday
import com.pixel.intelligentsearch.core.glance.GlanceItem
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.glance.GlanceSnapshot
import com.pixel.intelligentsearch.core.glance.GlanceSource
import com.pixel.intelligentsearch.core.glance.GlanceTimer
import com.pixel.intelligentsearch.core.glance.GlanceToggles
import com.pixel.intelligentsearch.core.glance.glanceItems
import com.pixel.intelligentsearch.core.system.openPendingOrApp
import com.pixel.intelligentsearch.core.glance.timerReading
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import kotlinx.coroutines.delay

/** Test tag on the At a Glance section's heading. */
internal const val GLANCE_HEADER_TAG = "glance_header"

/**
 * Where At a Glance reads from. Null (the app) reads the phone; screenshot and layout tests hand in fixed data so
 * nothing depends on the machine's battery, alarms or media.
 */
internal val LocalGlanceSource = staticCompositionLocalOf<GlanceSource?> { null }

/**
 * The clocks At a Glance reads: [now] the wall clock, [elapsed] the time since boot that media positions are kept
 * in; [ticks] false holds them still (screenshot tests pass fixed times).
 */
@Immutable
internal class GlanceClock(val now: () -> Long, val ticks: Boolean, val elapsed: () -> Long = SystemClock::elapsedRealtime)

internal val LocalGlanceClock = staticCompositionLocalOf { GlanceClock(System::currentTimeMillis, ticks = true) }

/** The time now, updated every [periodMs] (on the whole period) while composed and ticking. */
@Composable
private fun rememberGlanceNow(periodMs: Long, enabled: Boolean = true): Long {
    val clock = LocalGlanceClock.current
    return produceState(clock.now(), clock, periodMs, enabled) {
        value = clock.now()
        if (!clock.ticks || !enabled) return@produceState
        while (true) {
            delay(periodMs - clock.now() % periodMs)
            value = clock.now()
        }
    }.value
}

/** The At a Glance switches in Search Behavior, with Search Your Notifications for the timers and Contacts for birthdays. */
@Composable
private fun rememberGlanceToggles(): GlanceToggles {
    val enabled by rememberSetting(SettingsManager.GLANCE_ENABLED)
    val alarm by rememberSetting(SettingsManager.GLANCE_ALARM)
    val timers by rememberSetting(SettingsManager.GLANCE_TIMERS)
    val media by rememberSetting(SettingsManager.GLANCE_MEDIA)
    val birthdays by rememberSetting(SettingsManager.GLANCE_BIRTHDAYS)
    val battery by rememberSetting(SettingsManager.GLANCE_BATTERY)
    val notifications by rememberSetting(SettingsManager.SEARCH_NOTIFICATIONS)
    val contacts by rememberSetting(SettingsManager.SEARCH_CONTACTS)
    return remember(enabled, alarm, timers, media, birthdays, battery, notifications, contacts) {
        GlanceToggles(enabled, alarm, timers, media, birthdays, battery, notifications, contacts)
    }
}

/**
 * What At a Glance shows now. Its sources are read off the main thread and only while the search is on screen
 * (started) and [active] (the query is empty, the only time the section shows); typing or leaving removes every
 * receiver and listener, and the last cards wait for the query to be cleared. Alarms come into range on a coarse
 * clock; timers tick in their own rows.
 */
@Composable
internal fun rememberGlanceItems(hiddenApps: Set<String>, active: Boolean): List<GlanceItem> {
    val toggles = rememberGlanceToggles()
    val context = LocalContext.current
    val source = LocalGlanceSource.current ?: remember(context) {
        AndroidGlanceSource(context.applicationContext, context.appEntryPoint.notificationIndex())
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snapshot by produceState(GlanceSnapshot.EMPTY, source, toggles, hiddenApps, lifecycle, active) {
        if (!toggles.anyOn) {
            value = GlanceSnapshot.EMPTY
            return@produceState
        }
        if (!active) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            source.snapshots(toggles, hiddenApps).collect { value = it }
        }
    }
    val now = rememberGlanceNow(GLANCE_COARSE_TICK_MS, enabled = snapshot.alarm != null)
    return remember(snapshot, toggles, now) { glanceItems(snapshot, toggles, now) }
}

/** The At a Glance card's shape: the recent-search pill's 28 dp corners, on a squarer card. */
internal val GlanceCardShape = RoundedCornerShape(28.dp)

/** The shape of one item's row inside the card, which its press and the keyboard's ring follow. */
internal val GlanceRowShape = RoundedCornerShape(20.dp)

/** How often the alarm's "in" time moves on. */
private const val GLANCE_COARSE_TICK_MS = 30_000L

/** The card's share of the list's width, and the narrowest and widest it is drawn at ordinary text sizes. */
private const val GLANCE_WIDTH_FRACTION = 0.62f
private val GLANCE_MIN_WIDTH = 232.dp
private val GLANCE_MAX_WIDTH = 340.dp

/**
 * How wide the At a Glance card is in a list [available] wide (inside its margins) at [fontScale]: about 62% of it,
 * between 232 dp and 340 dp, so it sits as a squarish card in the top start corner. The narrowest width grows with
 * the text, and where even that doesn't fit (a narrow window, very large text) the card takes the whole width, so
 * nothing in it is clipped.
 */
internal fun glanceCardWidth(available: Dp, fontScale: Float): Dp {
    val min = GLANCE_MIN_WIDTH * fontScale.coerceAtLeast(1f)
    if (min >= available) return available
    return (available * GLANCE_WIDTH_FRACTION).coerceIn(min, maxOf(GLANCE_MAX_WIDTH, min))
}

/** Measures the card at [glanceCardWidth] of the width it is offered. */
private fun Modifier.glanceCardWidth(): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val width = glanceCardWidth(constraints.maxWidth.toDp(), fontScale).roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * The At a Glance section of the empty search: one card in the top start corner holding every item. It is one list
 * item ([ResultListKeys.GLANCE]), so the keyboard's stop on any of its items scrolls the card into view. [highlight]
 * is the keyboard's ring for the item under a key; [onOpened] runs as an item opens something.
 */
internal fun LazyListScope.atAGlanceItems(items: List<GlanceItem>, highlight: @Composable (key: String) -> Modifier, onOpened: () -> Unit) {
    if (items.isEmpty()) return
    item(key = ResultListKeys.GLANCE, contentType = "glance") {
        GlanceCard(
            items,
            onOpened = onOpened,
            modifier = Modifier.animateItem(
                placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
            ),
            highlight = highlight
        )
    }
}

/**
 * The At a Glance card: the recent-search pill's container (its surface at 70%, its hairline and 28 dp corners) as
 * one squarish card, "At a Glance" at its top in the theme's accent with a small clock in the top end corner, then a
 * row per item. Items coming and going
 * resize the card with the spatial spring (at once with animations off). [fullWidth] draws it across its space (the
 * detail pane's) rather than at [glanceCardWidth]. A tap on a row (or Enter on it, [highlight] being the keyboard's
 * ring) runs [onOpened], then opens what the row is about ([openGlanceItem]).
 */
@Composable
internal fun GlanceCard(
    items: List<GlanceItem>,
    onOpened: () -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false,
    highlight: @Composable (key: String) -> Modifier = { Modifier }
) {
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(
            modifier = Modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.glanceCardWidth())
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.70f), GlanceCardShape)
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.20f), GlanceCardShape)
                .clip(GlanceCardShape)
                .then(if (animate) Modifier.animateContentSize(ExpressiveMotionTokens.Spatial.ExpressiveDefault()) else Modifier)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            GlanceHeader()
            for (item in items) {
                key(item.key) { GlanceItemRow(item, onOpened, modifier = highlight(item.key)) }
            }
        }
    }
}

/**
 * "At a Glance" at the top of its card, in the theme's accent colour (a heading for TalkBack), with the clock in the
 * top end corner. The heading takes the room the clock leaves and wraps there at large text, so the two never overlap.
 */
@Composable
private fun GlanceHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "At a Glance",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(top = 6.dp, bottom = 2.dp, end = 8.dp)
                .semantics { heading() }
                .testTag(GLANCE_HEADER_TAG)
        )
        GlanceClockFace()
    }
}

/** One item's row in the card; [modifier] carries the keyboard's ring on it. */
@Composable
private fun GlanceItemRow(item: GlanceItem, onOpened: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val open = {
        onOpened()
        openGlanceItem(context, item)
    }
    when (item) {
        is GlanceItem.Timer -> TimerRow(item.timer, open, modifier)
        is GlanceItem.Alarm -> AlarmRow(item.alarm.triggerAt, open, modifier)
        is GlanceItem.Media -> MediaRow(item.media, open, modifier)
        is GlanceItem.Birthday -> BirthdayRow(item.birthday, open, modifier)
        is GlanceItem.Battery -> BatteryRow(item.battery, open, modifier)
    }
}

/**
 * What an At a Glance item opens, by tap or by Enter: a timer's own page (else the clock's timers, else its app),
 * the clock's alarms, the player's page, the contact, or Android's battery page.
 */
internal fun openGlanceItem(context: Context, item: GlanceItem) {
    when (item) {
        is GlanceItem.Timer -> openPendingOrApp(context, item.timer.contentIntent, item.timer.packageName, fallback = Intent(AlarmClock.ACTION_SHOW_TIMERS))
        is GlanceItem.Alarm -> launchSafeIntent(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        is GlanceItem.Media -> item.media.controls.open()
        is GlanceItem.Birthday -> item.birthday.lookupUri.takeIf { it.isNotEmpty() }
            ?.let { launchSafeIntent(context, Intent(Intent.ACTION_VIEW, it.toUri())) }
        is GlanceItem.Battery -> launchSafeIntent(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
    }
}

/**
 * The row all At a Glance items share inside the card: a 40 dp round badge or picture, a title and a line under it,
 * and any buttons of the item's own. It has no container of its own (the card is the container); its press and the
 * keyboard's ring follow the row's rounded shape.
 */
@Composable
private fun GlanceRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val shape = GlanceRowShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .clip(shape)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier)
            .bouncyClickable(shape = shape, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing()
    }
}

/** The round badge on a row: the item's icon on the secondary container, as the OneBox cards badge theirs. */
@Composable
private fun GlanceBadge(icon: ImageVector, size: Dp = 40.dp) {
    Box(
        modifier = Modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
    }
}

/** A running timer counting down each second, a timer that has run out, or a stopwatch counting up. */
@Composable
private fun TimerRow(timer: GlanceTimer, open: () -> Unit, modifier: Modifier) {
    val now = rememberGlanceNow(1_000L, enabled = timer.kind != GlanceTimer.Kind.TimesUp)
    val kindLabel = if (timer.kind == GlanceTimer.Kind.Stopwatch) "Stopwatch" else "Timer"
    val label = timer.label.takeIf { it.isNotBlank() && !it.equals(kindLabel, ignoreCase = true) }
    GlanceRow(
        leading = { GlanceBadge(Icons.Outlined.Timer) },
        title = "$kindLabel · ${timerReading(timer, now)}",
        subtitle = listOfNotNull(label, timer.appLabel.takeIf { it.isNotBlank() }).joinToString(" · "),
        onClick = open,
        modifier = modifier
    )
}

/** The next alarm, its time in the phone's 12 or 24 hour style; opens the clock's alarms. */
@Composable
private fun AlarmRow(triggerAt: Long, open: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val now = rememberGlanceNow(GLANCE_COARSE_TICK_MS)
    val time = remember(triggerAt) {
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(triggerAt))
    }
    GlanceRow(
        leading = { GlanceBadge(Icons.Outlined.Alarm) },
        title = "Alarm · $time",
        subtitle = Durations.until(triggerAt - now),
        onClick = open,
        modifier = modifier
    )
}

/**
 * What is playing: the cover in the matte circle, title and artist (a tap opens the player), then the seek bar and
 * the pill of Previous, Play or Pause and Next ([GlanceMediaControls]).
 */
@Composable
private fun MediaRow(media: GlanceMedia, open: () -> Unit, modifier: Modifier) {
    Column(Modifier.fillMaxWidth()) {
        MediaTitleRow(media, open, modifier)
        GlanceMediaControls(media)
    }
}

@Composable
private fun MediaTitleRow(media: GlanceMedia, open: () -> Unit, modifier: Modifier) {
    GlanceRow(
        leading = {
            val artwork = media.artwork
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .clip(CircleShape)
                        .matteRing()
                )
            } else {
                GlanceBadge(Icons.Outlined.MusicNote)
            }
        },
        title = media.title,
        subtitle = listOf(media.artist, media.appLabel).filter { it.isNotBlank() }.joinToString(" · "),
        onClick = open,
        modifier = modifier
    )
}

/** A contact's birthday this week, with their photo when they have one; opens the contact. */
@Composable
private fun BirthdayRow(birthday: GlanceBirthday, open: () -> Unit, modifier: Modifier) {
    val lookupUri = birthday.lookupUri
    val day = birthdayDayLabel(birthday)
    GlanceRow(
        leading = {
            val source = lookupUri.takeIf { it.startsWith("content://") }?.let { ThumbnailSource.ContactPhoto(it) }
            ResultThumbnail(source, 40.dp) { GlanceBadge(Icons.Outlined.Cake) }
        },
        title = "${birthday.name}'s Birthday",
        subtitle = listOfNotNull(day, birthday.turning?.let { "Turns $it" }).joinToString(" · "),
        onClick = open,
        modifier = modifier
    )
}

/** "Today", "Tomorrow", or the weekday it falls on. */
internal fun birthdayDayLabel(birthday: GlanceBirthday): String = when (birthday.daysAway) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> birthday.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
}

/** The battery when it is low or plugged in, with what the charger is doing; opens Android's battery page. */
@Composable
private fun BatteryRow(battery: GlanceBattery, open: () -> Unit, modifier: Modifier) {
    GlanceRow(
        leading = { GlanceBadge(if (battery.pluggedIn) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryAlert) },
        title = "Battery · ${battery.level}%",
        subtitle = if (battery.pluggedIn) battery.statusLabel ?: "Plugged In" else "Battery Low",
        onClick = open,
        modifier = modifier
    )
}


