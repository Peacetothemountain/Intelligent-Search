package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.Intent
import android.provider.AlarmClock
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import com.pixel.intelligentsearch.feature.settings.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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

/** The clock At a Glance reads; [ticks] false holds it still (screenshot tests pass a fixed time). */
@Immutable
internal class GlanceClock(val now: () -> Long, val ticks: Boolean)

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

/** How often the alarm's "in" time moves on. */
private const val GLANCE_COARSE_TICK_MS = 30_000L

/** The At a Glance section of the empty search: its heading, then one card per item. [onOpened] runs as a card opens something. */
internal fun LazyListScope.atAGlanceItems(items: List<GlanceItem>, onOpened: () -> Unit) {
    if (items.isEmpty()) return
    item(key = "glance_header", contentType = "recent_label") {
        GlanceHeader(Modifier.animateItem(
            placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
            fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
            fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
        ))
    }
    for (glance in items) {
        item(key = glance.key, contentType = "glance_card") {
            GlanceCard(
                glance,
                onOpened = onOpened,
                modifier = Modifier.animateItem(
                    placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                    fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                    fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                )
            )
        }
    }
}

/** "At a Glance", in the same small heading as "Recent". */
@Composable
internal fun GlanceHeader(modifier: Modifier = Modifier) {
    OverlaySectionLabel("At a Glance", modifier.testTag(GLANCE_HEADER_TAG).then(SectionLabelPadding))
}

/** One At a Glance card. */
@Composable
internal fun GlanceCard(item: GlanceItem, onOpened: () -> Unit, modifier: Modifier = Modifier) {
    when (item) {
        is GlanceItem.Timer -> TimerCard(item.timer, onOpened, modifier)
        is GlanceItem.Alarm -> AlarmCard(item.alarm.triggerAt, onOpened, modifier)
        is GlanceItem.Media -> MediaCard(item.media, onOpened, modifier)
        is GlanceItem.Birthday -> BirthdayCard(item.birthday, onOpened, modifier)
        is GlanceItem.Battery -> BatteryCard(item.battery, onOpened, modifier)
    }
}

/**
 * The card all At a Glance items share: the recent-search pill's container (its surface, hairline and 28 dp
 * shape), a 40 dp round badge or picture, a title and a line under it, and any buttons of the card's own.
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
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f), shape)
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

/** The round badge on a card: the item's icon on the secondary container, as the OneBox cards badge theirs. */
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
private fun TimerCard(timer: GlanceTimer, onOpened: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val now = rememberGlanceNow(1_000L, enabled = timer.kind != GlanceTimer.Kind.TimesUp)
    val kindLabel = if (timer.kind == GlanceTimer.Kind.Stopwatch) "Stopwatch" else "Timer"
    val label = timer.label.takeIf { it.isNotBlank() && !it.equals(kindLabel, ignoreCase = true) }
    GlanceRow(
        leading = { GlanceBadge(Icons.Outlined.Timer) },
        title = "$kindLabel · ${timerReading(timer, now)}",
        subtitle = listOfNotNull(label, timer.appLabel.takeIf { it.isNotBlank() }).joinToString(" · "),
        onClick = {
            onOpened()
            openPendingOrApp(context, timer.contentIntent, timer.packageName, fallback = Intent(AlarmClock.ACTION_SHOW_TIMERS))
        },
        modifier = modifier
    )
}

/** The next alarm, its time in the phone's 12 or 24 hour style; opens the clock's alarms. */
@Composable
private fun AlarmCard(triggerAt: Long, onOpened: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val now = rememberGlanceNow(GLANCE_COARSE_TICK_MS)
    val time = remember(triggerAt) {
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(triggerAt))
    }
    GlanceRow(
        leading = { GlanceBadge(Icons.Outlined.Alarm) },
        title = "Alarm · $time",
        subtitle = Durations.until(triggerAt - now),
        onClick = {
            onOpened()
            launchSafeIntent(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        },
        modifier = modifier
    )
}

/** What is playing: the cover in the matte circle, title and artist, and play or pause and next. */
@Composable
private fun MediaCard(media: GlanceMedia, onOpened: () -> Unit, modifier: Modifier) {
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
        onClick = {
            onOpened()
            media.controls.open()
        },
        modifier = modifier,
        trailing = {
            IconButton(onClick = { if (media.isPlaying) media.controls.pause() else media.controls.play() }) {
                Icon(
                    imageVector = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (media.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { media.controls.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next Track", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}

/** A contact's birthday this week, with their photo when they have one; opens the contact. */
@Composable
private fun BirthdayCard(birthday: GlanceBirthday, onOpened: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lookupUri = birthday.lookupUri
    val day = birthdayDayLabel(birthday)
    GlanceRow(
        leading = {
            val source = lookupUri.takeIf { it.startsWith("content://") }?.let { ThumbnailSource.ContactPhoto(it) }
            ResultThumbnail(source, 40.dp) { GlanceBadge(Icons.Outlined.Cake) }
        },
        title = "${birthday.name}'s Birthday",
        subtitle = listOfNotNull(day, birthday.turning?.let { "Turns $it" }).joinToString(" · "),
        onClick = {
            onOpened()
            if (lookupUri.isNotEmpty()) launchSafeIntent(context, Intent(Intent.ACTION_VIEW, lookupUri.toUri()))
        },
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
private fun BatteryCard(battery: GlanceBattery, onOpened: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    GlanceRow(
        leading = { GlanceBadge(if (battery.pluggedIn) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryAlert) },
        title = "Battery · ${battery.level}%",
        subtitle = if (battery.pluggedIn) battery.statusLabel ?: "Plugged In" else "Battery Low",
        onClick = {
            onOpened()
            launchSafeIntent(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        },
        modifier = modifier
    )
}


