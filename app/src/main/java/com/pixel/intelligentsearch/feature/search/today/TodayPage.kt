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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
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
import com.pixel.intelligentsearch.core.glance.GLANCE_EVENT_WINDOW_MS
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
import com.pixel.intelligentsearch.feature.settings.rememberTodayLocationRequest
import com.pixel.intelligentsearch.feature.settings.rememberPhotosRequest
import com.pixel.intelligentsearch.feature.settings.rememberPermissionState
import com.pixel.intelligentsearch.feature.settings.photosAllowed
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.CornerRadius
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.feature.settings.drawSearchPill
import com.pixel.intelligentsearch.feature.settings.SearchEasing
import com.pixel.intelligentsearch.feature.settings.SEARCH_MILLIS
import com.pixel.intelligentsearch.feature.settings.PILL_TEXT
import com.pixel.intelligentsearch.feature.settings.PILL_SIDE
import com.pixel.intelligentsearch.feature.settings.PILL_MARGIN
import com.pixel.intelligentsearch.feature.settings.PILL_HEIGHT
import com.pixel.intelligentsearch.feature.settings.FlashDecay
import com.pixel.intelligentsearch.feature.settings.FLASH_MILLIS
import com.pixel.intelligentsearch.core.ui.icons.filled.Call
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.answers.WeatherCodes
import com.pixel.intelligentsearch.core.answers.HighLow
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

/** Sunset's label on Rest of Your Day. */
private const val SUNSET = "Sunset"
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

    // The weather and the day's photo are read while the search is open with the Discover Overlay on, so the page has
    // them as it opens; read again on opening once they are a quarter of an hour old.
    val context = LocalContext.current
    val sources = LocalTodaySources.current ?: remember(context) { AndroidTodaySources(context) }
    val discoverOn by rememberSetting(SettingsManager.TODAY_BUTTON)
    val weatherOn by rememberSetting(SettingsManager.TODAY_WEATHER)
    val memoriesOn by rememberSetting(SettingsManager.TODAY_MEMORIES)
    val photosOk = rememberPermissionState(::photosAllowed)
    var weather by remember { mutableStateOf<Pair<WeatherReport, WeatherReport?>?>(null) }
    var weatherAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(discoverOn, weatherOn, open) {
        val now = System.currentTimeMillis()
        if (discoverOn && weatherOn && (weather == null || now - weatherAt > REFRESH_MS)) {
            sources.weather()?.let {
                weather = it
                weatherAt = now
            }
        }
    }
    var memory by remember { mutableStateOf<TodayMemory?>(null) }
    LaunchedEffect(discoverOn, memoriesOn, photosOk, open) {
        if (discoverOn && memoriesOn && photosOk && (memory == null || open)) {
            memory = sources.memory(java.time.LocalDate.now())
        }
    }
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
                    onOpenSettings = onOpenSettings,
                    preloadedWeather = weather,
                    preloadedMemory = memory
                )
            }
        }
    }
}

/** How old the weather may get before opening the page reads it again. */
private const val REFRESH_MS = 15 * 60_000L

/** A circle of [radius] around [center], the Today page's edge as it opens out of its button. */
private class CircleReveal(private val center: Offset, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(center, radius)) })
}

/**
 * Today's button in the search bar, to the left of the lens: the sun on its arc ([TodayIcon]), placed by the time of
 * day and rising to it as the search opens, at 30 dp (a little larger than the lens beside it) in the bar's icon weight and color.
 */
@Composable
internal fun TodayBarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberGlanceNow(60_000L)
    val animate by rememberAnimationsEnabled()
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "Discover Overlay" }
            .bouncyClickable(shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        TodayIcon(
            hour = hourOfDay(now),
            color = MaterialTheme.colorScheme.onSurface,
            rise = animate,
            modifier = Modifier.size(30.dp)
        )
    }
}

/** The page's look: Up Next on the primary container, other cards on the container colors, round 24 to 28 dp corners. */
internal val CardShape = RoundedCornerShape(24.dp)
private val HeroShape = RoundedCornerShape(28.dp)

/**
 * The Today page: the day's greeting and what is coming, the weather ahead, what is running and playing, what to catch
 * up on, quick controls, the phone's day, where you left off, a word and a countdown, and tomorrow. Each part shows
 * only when it has something to show and its switch (Appearance > Discover Overlay) is on. Read while
 * [active] and on screen. The bar at the bottom goes back to the search or to Today's settings.
 */
@Composable
internal fun TodayPage(
    active: Boolean,
    hiddenApps: Set<String>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    recentSearches: List<String> = emptyList(),
    onSearch: (String) -> Unit = {},
    preloadedWeather: Pair<WeatherReport, WeatherReport?>? = null,
    preloadedMemory: TodayMemory? = null
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
    val leaveByOn by rememberSetting(SettingsManager.TODAY_LEAVE_BY)
    val memoriesOn by rememberSetting(SettingsManager.TODAY_MEMORIES)
    // Weather with no saved place, and the leave-by time, need the phone's location, so allowing it reads both again.
    val locationOk = rememberPermissionState { com.pixel.intelligentsearch.core.answers.AndroidDeviceLocation(it).permitted() }
    // What the layer read wins whenever it has it, including when it arrives or refreshes after the page has opened;
    // the page reads for itself only while it has nothing. With Weather off there is none, so no part shows it.
    val ownWeather = produceState<Pair<WeatherReport, WeatherReport?>?>(null, preloadedWeather == null, active, weatherOn, locationOk) {
        if (preloadedWeather == null && active && weatherOn) value = sources.weather()
    }.value
    val loadedWeather = (preloadedWeather ?: ownWeather)?.takeIf { weatherOn }
    val weather = loadedWeather?.first
    val tomorrowWeather = loadedWeather?.second
    val notifications = rememberNotifications(sources, active && catchUpOn)
    val catchUp = remember(notifications, now) { catchUpFrom(notifications, now) }

    val zone = ZoneId.systemDefault()
    val delivery = remember(notifications, now) { deliveryFrom(notifications, now, zone) }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val events = snapshot.events.filter { !it.allDay && it.beginMillis > now }
    val upNext = events.firstOrNull()?.takeIf { upNextOn && it.beginMillis - now < GLANCE_EVENT_WINDOW_MS }
    val laterToday = events.filter { Instant.ofEpochMilli(it.beginMillis).atZone(zone).toLocalDate() == today }
    val tomorrowEvent = events.firstOrNull { Instant.ofEpochMilli(it.beginMillis).atZone(zone).toLocalDate() == today.plusDays(1) }
        ?.takeIf { upNextOn }
    val birthdaysToday = snapshot.birthdays.filter { it.daysAway == 0 }
    val timer = snapshot.timers.firstOrNull()
    val alarm = snapshot.alarm?.takeIf { it.triggerAt > now && it.triggerAt - now < 24 * 3_600_000L }
    val alarmTomorrow = snapshot.alarm?.takeIf {
        Instant.ofEpochMilli(it.triggerAt).atZone(zone).toLocalDate() == today.plusDays(1)
    }
    val trip = produceState<TodayTrip?>(null, upNext?.eventId, upNext?.beginMillis, upNext?.location, leaveByOn, active, locationOk) {
        value = if (upNext != null && leaveByOn && active) sources.trip(upNext) else null
    }.value
    val photosOk = rememberPermissionState(::photosAllowed)
    val ownMemory = produceState<TodayMemory?>(null, preloadedMemory == null, today, memoriesOn, active, photosOk) {
        if (preloadedMemory == null && memoriesOn && active && photosOk) value = sources.memory(today)
    }.value
    val memory = (preloadedMemory ?: ownMemory)?.takeIf { memoriesOn && photosOk }
    val photosRequest = rememberPhotosRequest { }
    val askPhotos = if (memoriesOn && extrasOn && !photosOk) ({ photosRequest.request() }) else null
    val locationRequest = rememberTodayLocationRequest { }
    val askLocation = if (leaveByOn && !locationOk) ({ locationRequest.request() }) else null
    val sunset = weather?.sunset?.toEpochMilli()?.takeIf { it > now }
    val warm = if (scheme.surface.luminance() < 0.5f) Color(0xFFF2B872) else Color(0xFFB5651D)
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            // Taps on the page stay on it rather than reaching the search beneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag(TODAY_PAGE_TAG)
    ) {
      LivingLight(warm)
      ConfettiHost {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            var index = 0
            // The greeting drifts away slower than the page and fades as it scrolls (parallax).
            Box(Modifier.graphicsLayer {
                translationY = scroll.value * 0.35f
                alpha = (1f - scroll.value / with(density) { 260.dp.toPx() }).coerceIn(0f, 1f)
            }) {
                Enter(index++) { Greeting(now, weather, sunset, birthdaysToday, warm) }
            }
            if (upNext != null) Enter(index++) { UpNextCard(upNext, now, trip, askLocation) }
            if (upNextOn && laterToday.size + (if (sunset != null) 1 else 0) >= 2) Enter(index++) { RestOfDay(laterToday, now, sunset) }
            if (weather != null && weather.hours.size >= 3) Enter(index++) { WeatherAhead(weather) }
            if (timer != null || alarm != null || delivery != null) {
                Enter(index++) { TimerAlarmTiles(timer, alarm?.let { GlanceItem.Alarm(it) }, delivery, now) }
            }
            snapshot.media?.let { media -> Enter(index++) { NowPlaying(media) } }
            if (catchUpOn && catchUp.isNotEmpty()) Enter(index++) { CatchUp(catchUp, now) }
            if (controlsOn) Enter(index++) { QuickControls(sources.controls) }
            if (phoneOn) Enter(index++) { PhoneToday(sources, active) }
            if (leftOffOn && recentSearches.isNotEmpty()) Enter(index++) { LeftOff(recentSearches.take(6), onSearch) }
            if (extrasOn) {
                Enter(index++) { WordOfTheDay(wordOfTheDay(today), onSearch) }
                val countdown = remember(today) { sources.countdown(today) }
                Enter(index++) { YearTiles(today, countdown, memory, askPhotos) }
            } else if (memory != null) {
                // On This Day has its own switch, so its photo shows without Word of the Day and Countdown.
                Enter(index++) { YearTiles(today, null, memory) }
            }
            if (alarmTomorrow != null || tomorrowWeather != null || tomorrowEvent != null) {
                Enter(index) { Tomorrow(today.plusDays(1), alarmTomorrow?.triggerAt, tomorrowWeather, tomorrowEvent) }
            }
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

/**
 * Each part springs up into place as it comes into view: those on screen as the page opens one after another, 70 ms
 * apart, the rest as the user scrolls to them (at once with animations off).
 */
@Composable
private fun Enter(index: Int, content: @Composable () -> Unit) {
    val animate by rememberAnimationsEnabled()
    val shown = remember { Animatable(if (animate) 0f else 1f) }
    var seenAt by remember { mutableLongStateOf(0L) }
    val opened = remember { System.currentTimeMillis() }
    LaunchedEffect(seenAt) {
        if (!animate || seenAt == 0L) return@LaunchedEffect
        // On screen from the start: in turn. Scrolled to later: at once.
        if (seenAt - opened < 400) delay(80L + index * 70L)
        shown.animateTo(1f, arriveSpring())
    }
    val density = LocalDensity.current
    Box(
        Modifier
            .onFirstSeen { seenAt = System.currentTimeMillis() }
            .graphicsLayer {
                val p = shown.value
                alpha = (p * 3.3f).coerceIn(0f, 1f)
                translationY = (1f - p) * with(density) { 26.dp.toPx() }
                val s = 0.96f + 0.04f * p
                scaleX = s
                scaleY = s
            }
    ) { content() }
}

/** The date, a greeting for the time of day, and a chip for each birthday today. */
@Composable
private fun Greeting(now: Long, weather: WeatherReport?, sunset: Long?, birthdays: List<GlanceBirthday>, warm: Color) {
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
    val animate by rememberAnimationsEnabled()
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
      Row(verticalAlignment = Alignment.Bottom) {
       Column(Modifier.weight(1f)) {
        Text(date, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = scheme.onSurfaceVariant)
        SettleText(
            greeting,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Normal),
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp).semantics { heading() }
        )
       }
        // The Discover Overlay's own sun, large, rising to now as the page opens, its glow breathing.
        TodayIcon(
            hour = hourOfDay(now),
            color = scheme.onSurfaceVariant,
            sunColor = warm,
            rise = animate,
            pulse = animate,
            modifier = Modifier.padding(bottom = 6.dp).size(88.dp)
        )
      }
        if (birthdays.isNotEmpty() || weather != null || sunset != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (weather != null) {
                    TodayChip(
                        icon = weatherIcon(weather.code, weather.isDay),
                        text = "${weather.temperature.roundToInt()}° ${WeatherCodes.describe(weather.code)}",
                        detail = HighLow.drawn(weather.high),
                        spokenDetail = HighLow.spoken(weather.high),
                        spin = WeatherCodes.kind(weather.code) == WeatherCodes.Kind.CLEAR && weather.isDay,
                        onClick = { openWeather(context) }
                    )
                }
                if (sunset != null) {
                    val time = remember(sunset) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(sunset)) }
                    TodayChip(
                        icon = null,
                        leading = { color -> TodayIcon(hour = 17.6f, color = color, modifier = Modifier.size(18.dp)) },
                        text = "Sunset $time",
                        detail = null,
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
    onClick: () -> Unit
) {
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
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = turn() })
            } else {
                leading?.invoke(scheme.onTertiaryContainer)
            }
        }
        Spacer(Modifier.width(8.dp))
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
}

/** The next event: how long until it starts, when and where, and one tap to directions (when it has a place) or the event. */
@Composable
private fun UpNextCard(event: CalendarEvent, now: Long, trip: TodayTrip?, askLocation: (() -> Unit)?) {
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
    val fill by animateFloatAsState(target, if (animate) tween(800, delayMillis = 300) else snap(), label = "leave by")
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Box(Modifier.fillMaxWidth(fill).height(6.dp).clip(CircleShape).background(scheme.onPrimaryContainer))
        }
        Text("About ${Durations.minutes(trip.minutes * 60_000L)}", style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
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
private fun RestOfDay(calendar: List<CalendarEvent>, now: Long, sunset: Long?) {
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
            Text("Rest of your day", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(if (calendar.size == 1) "1 event" else "${calendar.size} events", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
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
private fun TimerAlarmTiles(timer: GlanceTimer?, alarm: GlanceItem.Alarm?, delivery: TodayDelivery?, now: Long) {
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
            .background(scheme.surfaceContainer.copy(alpha = CARD_ALPHA))
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

/** How long the bar stays full before it goes down to Back and Settings. */
private const val BAR_SHRINK_DELAY_MS = 1_500L

/** The compact bar: Back and Settings, each a 48 dp button 12 dp in from its end. */
private val BAR_COMPACT_WIDTH = 120.dp
private val BAR_BUTTON_INSET = 12.dp
private val BAR_BUTTON = 48.dp

/**
 * Today's bar, in the look of the search pill on Manage Hidden Apps and Add Applications ([BottomSearchPill]): the
 * same body color, 72 dp, 14 dp from the sides, 20 sp text, no shadow. It is there as the page opens ("Back to
 * Search", the back arrow and the Settings bars), then after a moment goes down the way that pill closes: its right end dissolves back toward the left, the light leading it and
 * Settings riding it, until only Back and Settings are left, where it flashes again. Same timing and curve as the
 * search pill; at once with animations off. A tap on it while full, or on Back, returns to the search; Settings opens
 * Today's settings.
 */
@Composable
private fun TodayBar(onBack: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    // The bar is there as the page opens; only its going down to Back and Settings is animated.
    val reveal = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    val shrink = remember { Animatable(0f) }
    val landing = remember { Animatable(0f) }
    LaunchedEffect(animate) {
        delay(BAR_SHRINK_DELAY_MS)
        if (!animate) {
            shrink.snapTo(1f)
            return@LaunchedEffect
        }
        shrink.animateTo(1f, tween(SEARCH_MILLIS, easing = SearchEasing))
        landing.snapTo(1f)
        landing.animateTo(0f, tween(FLASH_MILLIS, easing = FlashDecay))
    }
    val body = scheme.surfaceVariant
    val light = scheme.primary
    val onPill = scheme.onSurfaceVariant
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(start = PILL_SIDE, end = PILL_SIDE, bottom = PILL_MARGIN)
            .height(PILL_HEIGHT)
    ) {
        val fullPx = with(density) { maxWidth.toPx() }
        val compactPx = with(density) { BAR_COMPACT_WIDTH.toPx() }
        // How far the pill reaches from its start (the left), as it shrinks.
        fun edge(s: Float) = fullPx - s * (fullPx - compactPx)
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val s = shrink.value
                    when {
                        s <= 0f -> drawSearchPill(size, reveal.value, fade.value, flash.value, body, light, mirrored = false)
                        s < 1f -> {
                            // The compact pill firming up as the edge comes back to it.
                            drawRoundRect(
                                body,
                                size = Size(compactPx, size.height),
                                cornerRadius = CornerRadius(size.height / 2f),
                                alpha = smoothStep(0.6f, 1f, s)
                            )
                            drawSearchPill(size, edge(s) / fullPx, 1f, 0f, body, light, mirrored = true)
                        }
                        else -> drawSearchPill(Size(compactPx, size.height), 1f, 1f, landing.value, body, light, mirrored = true)
                    }
                }
                .then(
                    if (shrink.value < 0.5f) {
                        Modifier.pointerInput(Unit) { detectTapGestures { onBack() } }
                    } else Modifier
                )
        )
        val formed = { fade.value * smoothStep(0.82f, 1f, reveal.value) }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = BAR_BUTTON_INSET)
                .size(BAR_BUTTON)
                .graphicsLayer { alpha = formed() }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Search", tint = onPill)
        }
        Text(
            "Back to Search",
            color = onPill,
            fontSize = PILL_TEXT,
            fontFamily = GoogleSansFlex,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = BAR_BUTTON_INSET + BAR_BUTTON + 4.dp)
                .graphicsLayer { alpha = formed() * (1f - smoothStep(0f, 0.35f, shrink.value)) }
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    // At the pill's right end, riding its edge as it shrinks.
                    val x = edge(shrink.value) - (BAR_BUTTON_INSET + BAR_BUTTON).toPx()
                    IntOffset(x.roundToInt(), 0)
                }
                .size(BAR_BUTTON)
                .graphicsLayer { alpha = formed() }
        ) {
            Icon(painterResource(R.drawable.ic_three_bars), contentDescription = "Discover Overlay Settings", tint = onPill)
        }
    }
}

/** Hermite smoothing of [x] between [edge0] and [edge1], as the search pill's own. */
private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
