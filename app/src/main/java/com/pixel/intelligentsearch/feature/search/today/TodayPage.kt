package com.pixel.intelligentsearch.feature.search.today

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.pixel.intelligentsearch.core.ui.icons.automirrored.filled.ArrowBack
import com.pixel.intelligentsearch.core.ui.icons.filled.Directions
import com.pixel.intelligentsearch.core.ui.icons.rounded.Pause
import com.pixel.intelligentsearch.core.ui.icons.rounded.PlayArrow
import com.pixel.intelligentsearch.core.ui.icons.outlined.Alarm
import com.pixel.intelligentsearch.core.ui.icons.outlined.Cake
import com.pixel.intelligentsearch.core.ui.icons.outlined.Event
import com.pixel.intelligentsearch.core.ui.icons.outlined.LocationOn
import com.pixel.intelligentsearch.core.ui.icons.outlined.MusicNote
import com.pixel.intelligentsearch.core.ui.icons.outlined.Schedule
import com.pixel.intelligentsearch.core.ui.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.glance.AndroidGlanceSource
import com.pixel.intelligentsearch.core.glance.GlanceBirthday
import com.pixel.intelligentsearch.core.glance.GlanceItem
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.glance.GlanceSnapshot
import com.pixel.intelligentsearch.core.glance.GlanceTimer
import com.pixel.intelligentsearch.core.glance.GlanceToggles
import com.pixel.intelligentsearch.core.glance.timerReading
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.feature.search.LocalGlanceSource
import com.pixel.intelligentsearch.feature.search.SquigglySeekBar
import com.pixel.intelligentsearch.feature.search.calendarEventIntent
import com.pixel.intelligentsearch.feature.search.matteRing
import com.pixel.intelligentsearch.feature.search.openGlanceItem
import com.pixel.intelligentsearch.feature.search.rememberCalendarAllowed
import com.pixel.intelligentsearch.feature.search.rememberGlanceNow
import com.pixel.intelligentsearch.feature.search.rememberNotificationAccessGranted
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.hypot
import kotlin.math.roundToInt
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.answers.WeatherCodes
import com.pixel.intelligentsearch.core.answers.ForecastDay
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.positionInRoot

/** Test tags for the Today button, page and bar. */
internal const val TODAY_BUTTON_TAG = "today_button"
internal const val TODAY_PAGE_TAG = "today_page"

/** Material's emphasized curves, for the page opening out of its button and closing back into it. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val OPEN_MS = 560
private const val CLOSE_MS = 380

/**
 * The Today page over the search panel, opening out of its button in the search bar ([origin], in the window's
 * coordinates) as a growing circle and closing back into it (at once with animations off). Back closes it.
 * [recentSearches] feed Pick Up Where You Left Off, whose cards search again with [onSearch].
 */
@Composable
internal fun TodayLayer(
    open: Boolean,
    origin: Offset,
    hiddenApps: Set<String>,
    recentSearches: List<String>,
    onClose: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val animate by rememberAnimationsEnabled()
    val reveal = remember { Animatable(if (open) 1f else 0f) }
    var layerTopLeft by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(open, animate) {
        val spec = when {
            !animate -> snap()
            open -> tween(OPEN_MS, easing = EmphasizedDecelerate)
            else -> tween<Float>(CLOSE_MS, easing = EmphasizedAccelerate)
        }
        reveal.animateTo(if (open) 1f else 0f, spec)
    }
    BackHandler(enabled = open) { onClose() }

    Box(Modifier.fillMaxSize().onGloballyPositioned { layerTopLeft = it.positionInRoot() }) {
        if (open || reveal.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val center = if (origin != Offset.Unspecified) origin - layerTopLeft else Offset(size.width * 0.1f, size.height - 40.dp.toPx())
                        val far = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y))
                        shape = CircleReveal(center, far * reveal.value)
                        clip = true
                    }
            ) {
                TodayPage(
                    active = open,
                    hiddenApps = hiddenApps,
                    recentSearches = recentSearches,
                    onBack = onClose,
                    onSearch = onSearch,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

/** A circle of [radius] around [center], the Today page's edge as it opens out of its button. */
private class CircleReveal(private val center: Offset, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(center, radius)) })
}

/**
 * Today's button in the search bar, to the left of the lens: the sun on its arc ([TodayIcon]), placed by the time of
 * day and rising to it as the search opens, drawn at the bar's own icon size (24 dp) and weight, in its icon color.
 */
@Composable
internal fun TodayBarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberGlanceNow(60_000L)
    val animate by rememberAnimationsEnabled()
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "Today" }
            .bouncyClickable(shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        TodayIcon(
            hour = hourOfDay(now),
            color = MaterialTheme.colorScheme.onSurface,
            rise = animate,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** The page's look: Up Next on the primary container, other cards on the container colors, round 24 to 28 dp corners. */
internal val CardShape = RoundedCornerShape(24.dp)
private val HeroShape = RoundedCornerShape(28.dp)

/**
 * The Today page: the day's greeting and what is coming, the weather ahead, what is running and playing, what to catch
 * up on, quick controls, the phone's day, where you left off, a word and a countdown, and tomorrow. Each part shows
 * only when it has something to show and its switch (Appearance > Search Overlay > Today Button) is on. Read while
 * [active] and on screen. The bar at the bottom goes back to the search or to Today's settings.
 */
@Composable
internal fun TodayPage(
    active: Boolean,
    hiddenApps: Set<String>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    recentSearches: List<String> = emptyList(),
    onSearch: (String) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val sources = LocalTodaySources.current ?: remember(context) { AndroidTodaySources(context) }
    val now = rememberGlanceNow(30_000L)
    val snapshot = rememberTodaySnapshot(hiddenApps, active)
    val upNextOn by rememberSetting(SettingsManager.TODAY_UP_NEXT)
    val weatherOn by rememberSetting(SettingsManager.TODAY_WEATHER)
    val catchUpOn by rememberSetting(SettingsManager.TODAY_CATCH_UP)
    val controlsOn by rememberSetting(SettingsManager.TODAY_CONTROLS)
    val phoneOn by rememberSetting(SettingsManager.TODAY_PHONE)
    val leftOffOn by rememberSetting(SettingsManager.TODAY_LEFT_OFF)
    val extrasOn by rememberSetting(SettingsManager.TODAY_EXTRAS)
    val weather = rememberWeather(sources, ForecastDay.TODAY, active && weatherOn)
    val tomorrowWeather = rememberWeather(sources, ForecastDay.TOMORROW, active && weatherOn)
    val catchUp = rememberCatchUp(sources, active && catchUpOn, now)

    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val events = snapshot.events.filter { !it.allDay && it.beginMillis > now }
    val upNext = events.firstOrNull()?.takeIf { upNextOn }
    val laterToday = events.filter { Instant.ofEpochMilli(it.beginMillis).atZone(zone).toLocalDate() == today }
    val tomorrowEvent = events.firstOrNull { Instant.ofEpochMilli(it.beginMillis).atZone(zone).toLocalDate() == today.plusDays(1) }
        ?.takeIf { upNextOn }
    val birthdaysToday = snapshot.birthdays.filter { it.daysAway == 0 }
    val timer = snapshot.timers.firstOrNull()
    val alarm = snapshot.alarm?.takeIf { it.triggerAt > now && it.triggerAt - now < 24 * 3_600_000L }
    val alarmTomorrow = snapshot.alarm?.takeIf {
        Instant.ofEpochMilli(it.triggerAt).atZone(zone).toLocalDate() == today.plusDays(1)
    }
    val primary = scheme.primaryContainer

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset.Zero,
                        radius = size.maxDimension * 0.6f
                    )
                )
            }
            // Taps on the page stay on it rather than reaching the search beneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag(TODAY_PAGE_TAG)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            var index = 0
            Enter(index++) { Greeting(now, weather.takeIf { weatherOn }, birthdaysToday) }
            if (upNext != null) Enter(index++) { UpNextCard(upNext, now) }
            if (upNextOn && laterToday.size >= 2) Enter(index++) { RestOfDay(laterToday, now) }
            if (weatherOn && weather != null && weather.hours.size >= 3) Enter(index++) { WeatherAhead(weather) }
            if (timer != null || alarm != null) Enter(index++) { TimerAlarmTiles(timer, alarm?.let { GlanceItem.Alarm(it) }, now) }
            snapshot.media?.let { media -> Enter(index++) { NowPlaying(media) } }
            if (catchUpOn && catchUp.isNotEmpty()) Enter(index++) { CatchUp(catchUp, now) }
            if (controlsOn) Enter(index++) { QuickControls(sources.controls) }
            if (phoneOn) Enter(index++) { PhoneToday(sources, active) }
            if (leftOffOn && recentSearches.isNotEmpty()) Enter(index++) { LeftOff(recentSearches.take(6), onSearch) }
            if (extrasOn) {
                Enter(index++) { WordOfTheDay(wordOfTheDay(today), onSearch) }
                val countdown = remember(today) { sources.countdown(today) }
                Enter(index++) { YearTiles(today, countdown) }
            }
            if (alarmTomorrow != null || tomorrowWeather != null || tomorrowEvent != null) {
                Enter(index) { Tomorrow(today.plusDays(1), alarmTomorrow?.triggerAt, tomorrowWeather?.takeIf { weatherOn }, tomorrowEvent) }
            }
        }
        // The page fades under the bar as it scrolls.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, scheme.surface), startY = 0f, endY = 180f))
        )
        TodayBar(
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding()
        )
    }
}

/** At a Glance's sources, read for Today: all of its items, with Up Next deciding whether the calendar is read. */
@Composable
private fun rememberTodaySnapshot(hiddenApps: Set<String>, active: Boolean): GlanceSnapshot {
    val context = LocalContext.current
    val upNext by rememberSetting(SettingsManager.TODAY_UP_NEXT)
    val contacts by rememberSetting(SettingsManager.SEARCH_CONTACTS)
    val notifications = rememberNotificationAccessGranted()
    val calendarAccess = rememberCalendarAllowed()
    val toggles = remember(upNext, contacts, notifications, calendarAccess) {
        GlanceToggles(
            enabled = true, alarm = true, timers = true, media = true, calendar = upNext, birthdays = true,
            notifications = notifications, contacts = contacts, calendarAccess = calendarAccess
        )
    }
    val source = LocalGlanceSource.current ?: remember(context) {
        AndroidGlanceSource(context.applicationContext, context.appEntryPoint.notificationIndex())
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(GlanceSnapshot.EMPTY, source, toggles, hiddenApps, lifecycle, active) {
        if (!active) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            source.snapshots(toggles, hiddenApps).collect { value = it }
        }
    }.value
}

/** Each part rises into place in turn as the page opens, 48 ms apart, with the expressive spring (at once with animations off). */
@Composable
private fun Enter(index: Int, content: @Composable () -> Unit) {
    val animate by rememberAnimationsEnabled()
    val shown = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(120L + index * 48L)
        shown.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f))
    }
    val density = LocalDensity.current
    Box(
        Modifier.graphicsLayer {
            val p = shown.value
            alpha = (p * 2.8f).coerceIn(0f, 1f)
            translationY = (1f - p) * with(density) { 22.dp.toPx() }
            val s = 0.985f + 0.015f * p
            scaleX = s
            scaleY = s
        }
    ) { content() }
}

/** The date, a greeting for the time of day, and a chip for each birthday today. */
@Composable
private fun Greeting(now: Long, weather: WeatherReport?, birthdays: List<GlanceBirthday>) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val date = remember(time.toLocalDate(), locale) {
        time.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)).uppercase(locale)
    }
    val greeting = when (time.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
        Text(date, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = scheme.onSurfaceVariant)
        Text(
            greeting,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Normal,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp).semantics { heading() }
        )
        if (birthdays.isNotEmpty() || weather != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (weather != null) {
                    TodayChip(
                        icon = weatherIcon(weather.code, weather.isDay),
                        text = "${weather.temperature.roundToInt()}° ${WeatherCodes.describe(weather.code)}",
                        detail = "H ${weather.high.roundToInt()}°",
                        spin = WeatherCodes.kind(weather.code) == WeatherCodes.Kind.CLEAR && weather.isDay,
                        onClick = { openWeather(context) }
                    )
                }
                for (birthday in birthdays) {
                    val first = birthday.name.substringBefore(' ')
                    TodayChip(
                        icon = Icons.Outlined.Cake,
                        text = birthday.turning?.let { "$first turns $it" } ?: "$first's birthday",
                        detail = "Say hi",
                        onClick = { openGlanceItem(context, GlanceItem.Birthday(birthday)) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun TodayChip(icon: ImageVector, text: String, detail: String?, spin: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .bouncyClickable(shape = CircleShape, onClick = onClick)
            .heightIn(min = 38.dp)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(26.dp).background(scheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            val turn = rememberSlowTurn(spin)
            Icon(icon, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = turn() })
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        if (detail != null) {
            Text(" · $detail", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** The next event: how long until it starts, when and where, and one tap to directions (when it has a place) or the event. */
@Composable
private fun UpNextCard(event: CalendarEvent, now: Long) {
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
                modifier = Modifier.clip(CircleShape).background(scheme.primary).padding(horizontal = 10.dp, vertical = 4.dp)
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
                Text("  ·  ", style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.6f))
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
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (event.location.isNotBlank()) {
                PillButton("Directions", Icons.Filled.Directions, filled = true) {
                    launchSafeIntent(context, Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(event.location)}".toUri()))
                }
            }
            PillButton("Open Event", Icons.Outlined.Event, filled = event.location.isBlank()) {
                launchSafeIntent(context, calendarEventIntent(event))
            }
        }
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
 * (labels taking turns above and below the line so neighbors don't collide), and a dot for now.
 */
@Composable
private fun RestOfDay(events: List<CalendarEvent>, now: Long) {
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
            .background(scheme.surfaceContainer)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rest of your day", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${events.size} events", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp).height(96.dp)) {
            val width = maxWidth
            val line = scheme.surfaceContainerHighest
            val fill = scheme.primary
            val pin = scheme.onSurfaceVariant
            val pinFill = scheme.surfaceContainer
            val nowAt = at(now)
            Canvas(Modifier.fillMaxSize()) {
                val y = 44.dp.toPx()
                val stroke = 4.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(fill, Offset(0f, y), Offset(size.width * nowAt * shown, y), strokeWidth = stroke, cap = StrokeCap.Round)
                for (event in events) {
                    val x = size.width * at(event.beginMillis)
                    drawCircle(pinFill, 5.dp.toPx(), Offset(x, y))
                    drawCircle(pin, 5.dp.toPx() - 1.25.dp.toPx(), Offset(x, y), style = Stroke(2.5.dp.toPx()))
                }
                drawCircle(fill.copy(alpha = 0.25f), 12.dp.toPx(), Offset(size.width * nowAt * shown, y))
                drawCircle(fill, 8.dp.toPx(), Offset(size.width * nowAt * shown, y))
            }
            events.forEachIndexed { i, event ->
                val above = i % 2 == 0
                val labelWidth = 96.dp
                val x = (width * at(event.beginMillis) - labelWidth / 2).coerceIn(0.dp, width - labelWidth)
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

/** A running timer (counting down each second, a ring sweeping each minute) and the next alarm, side by side. */
@Composable
private fun TimerAlarmTiles(timer: GlanceTimer?, alarm: GlanceItem.Alarm?, now: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (timer != null) TimerTile(timer, Modifier.weight(1f))
        if (alarm != null) AlarmTile(alarm, now, Modifier.weight(1f))
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
            .background(scheme.surfaceContainer)
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
                Text(listOf(kind, timer.appLabel).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun NowPlaying(media: GlanceMedia) {
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
            .background(scheme.surfaceContainer)
            .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = if (media.progress != null) 4.dp else 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh)
                    .bouncyClickable(shape = CircleShape) { media.controls.open() },
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
                    .bouncyClickable { media.controls.open() }
            ) {
                Text(media.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(media.artist, media.appLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

/** How long the bar stays full before it springs down to Back and Settings. */
private const val BAR_SHRINK_DELAY_MS = 1_500L

/**
 * Today's bar: flat, in its own look rather than the search bar's. It opens full ("Back to Search", the back arrow and
 * the Settings bars), then springs down to just Back and Settings at the start. A tap on it while full, or on Back,
 * returns to the search; Settings opens Today's settings.
 */
@Composable
private fun TodayBar(onBack: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    var compact by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BAR_SHRINK_DELAY_MS)
        compact = true
    }
    val labelAlpha by animateFloatAsState(if (compact) 0f else 1f, if (animate) tween(220) else snap(), label = "bar label")
    BoxWithConstraints(modifier.fillMaxWidth().padding(14.dp)) {
        val width by animateDpAsState(
            if (compact) 104.dp else maxWidth,
            if (animate) spring(dampingRatio = 0.72f, stiffness = 260f) else snap(),
            label = "bar width"
        )
        Surface(
            onClick = { if (!compact) onBack() },
            shape = CircleShape,
            color = scheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
            modifier = Modifier.width(width).height(60.dp)
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Search", tint = scheme.onSurface)
                }
                Text(
                    "Back to Search",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .graphicsLayer {
                            alpha = labelAlpha
                            translationX = (1f - labelAlpha) * -12.dp.toPx()
                        }
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_three_bars), contentDescription = "Today Settings", tint = scheme.onSurface)
                }
            }
        }
    }
}
