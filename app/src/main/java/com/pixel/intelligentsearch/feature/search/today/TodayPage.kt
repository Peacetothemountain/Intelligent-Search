package com.pixel.intelligentsearch.feature.search.today

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.pixel.intelligentsearch.core.ui.icons.outlined.Alarm
import com.pixel.intelligentsearch.core.ui.icons.outlined.Cake
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.pixel.intelligentsearch.core.glance.NotificationAccess
import com.pixel.intelligentsearch.feature.search.today.cards.grid.PartStatus
import com.pixel.intelligentsearch.feature.search.today.cards.grid.CardAccess
import com.pixel.intelligentsearch.feature.settings.NotificationAccessDialog
import com.pixel.intelligentsearch.feature.settings.rememberCalendarRequest
import com.pixel.intelligentsearch.feature.settings.rememberNotificationAccessRequest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.glance.AndroidGlanceSource
import com.pixel.intelligentsearch.core.glance.GlanceBirthday
import com.pixel.intelligentsearch.core.glance.GlanceItem
import com.pixel.intelligentsearch.core.glance.GlanceSnapshot
import com.pixel.intelligentsearch.core.glance.GlanceToggles
import com.pixel.intelligentsearch.core.glance.GLANCE_EVENT_WINDOW_MS
import com.pixel.intelligentsearch.core.system.ComesBackContext
import com.pixel.intelligentsearch.core.system.WaitsForPermissionGrant
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.feature.search.LocalGlanceSource
import com.pixel.intelligentsearch.feature.search.openGlanceItem
import com.pixel.intelligentsearch.feature.search.rememberCalendarAllowed
import com.pixel.intelligentsearch.feature.search.rememberGlanceNow
import com.pixel.intelligentsearch.feature.search.rememberNotificationAccessGranted
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardState
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardMode
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.ShapedContent
import com.pixel.intelligentsearch.feature.search.today.cards.grid.DiscoverEditChrome
import com.pixel.intelligentsearch.feature.search.today.cards.widget.LiveWidget
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardData
import com.pixel.intelligentsearch.feature.search.today.cards.CardForm
import com.pixel.intelligentsearch.core.system.openPendingOrApp
import com.pixel.intelligentsearch.feature.search.today.cards.grid.DiscoverGrid
import com.pixel.intelligentsearch.feature.search.today.cards.grid.cardLayoutStore
import com.pixel.intelligentsearch.feature.search.today.cards.grid.rememberDiscoverEditState
import com.pixel.intelligentsearch.feature.search.today.cards.shape.AppCardBox
import com.pixel.intelligentsearch.feature.search.today.cards.shape.CardOutlineShape
import com.pixel.intelligentsearch.feature.search.today.cards.shape.CoveredBox
import com.pixel.intelligentsearch.feature.search.today.cards.shape.CoveredCard
import com.pixel.intelligentsearch.feature.search.today.cards.shape.ProvideAmbientBudget
import com.pixel.intelligentsearch.feature.search.today.cards.shape.ShapedCard
import com.pixel.intelligentsearch.feature.search.today.cards.shape.SiteCardBox
import com.pixel.intelligentsearch.feature.search.today.cards.site.siteCards
import com.pixel.intelligentsearch.feature.search.today.cards.widget.appCardIcon
import com.pixel.intelligentsearch.feature.search.today.cards.widget.rememberWidgetAdder
import com.pixel.intelligentsearch.feature.search.today.cards.widget.rememberWidgetSetup
import com.pixel.intelligentsearch.feature.search.today.cards.widget.widgetCards
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.hypot
import com.pixel.intelligentsearch.feature.settings.rememberTodayLocationRequest
import com.pixel.intelligentsearch.feature.settings.rememberPermissionState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.answers.WeatherCodes
import com.pixel.intelligentsearch.core.answers.HighLow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.positionInRoot

/** Test tags for the Today button, page and bar. */
internal const val TODAY_BUTTON_TAG = "today_button"
internal const val TODAY_PAGE_TAG = "today_page"

/** Material's emphasized curves, for the page opening out of its button and closing back into it (and its cards' motion). */
internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val OPEN_MS = 560

private const val CLOSE_MS = 380

/**
 * The Today page over the search panel, open while [isOpen], opening out of its button in the search bar ([origin], in
 * the window's coordinates) as a growing circle and closing back into it (at once with animations off). Back closes it.
 * [recentSearches] feed Pick Up Where You Left Off, whose cards search again with [onSearch]. [isOpen] and [origin] are
 * read here, the origin only as the circle is drawn, so the button moving doesn't recompose the screen around it.
 */
@Composable
internal fun TodayLayer(
    isOpen: () -> Boolean,
    /** Whether a close is at once, with no circle closing in (a new search opened while the page was up). */
    closesAtOnce: () -> Boolean,
    origin: () -> Offset,
    hiddenApps: Set<String>,
    recentSearches: List<String>,
    onClose: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val open = isOpen()
    val animate by rememberAnimationsEnabled()
    val reveal = remember { Animatable(if (open) 1f else 0f) }
    // Whether any of the circle shows, so closing recomposes once at its end rather than on every frame.
    val revealed by remember { derivedStateOf { reveal.value > 0f } }
    var layerTopLeft by remember { mutableStateOf(Offset.Zero) }

    // The weather is read while the search is open with the Discover Overlay on, so the page has
    // it as it opens; read again on opening once it is a quarter of an hour old.
    val context = LocalContext.current
    val sources = LocalTodaySources.current ?: remember(context) { AndroidTodaySources(context) }
    val comesBack = remember(context) {
        (context.findActivity() as? WaitsForPermissionGrant)?.let { ComesBackContext(context, it) } ?: context
    }
    val discoverOn by rememberSetting(SettingsManager.TODAY_BUTTON)
    val weatherOn by rememberSetting(SettingsManager.TODAY_WEATHER)
    var weather by remember { mutableStateOf<Pair<WeatherReport, WeatherReport?>?>(null) }
    var weatherAt by remember { mutableLongStateOf(0L) }
    // A forecast in the other scale than Settings' Temperature asks for is read again at once.
    val temperatureChoice by rememberSetting(SettingsManager.TEMPERATURE_UNIT)
    LaunchedEffect(discoverOn, weatherOn, open, temperatureChoice) {
        val now = System.currentTimeMillis()
        val unit = com.pixel.intelligentsearch.core.answers.TemperatureUnit.chosen(temperatureChoice, java.util.Locale.getDefault())
        if (discoverOn && weatherOn && (weather == null || now - weatherAt > REFRESH_MS || weather?.first?.unit != unit)) {
            // A report in the other scale goes first: if the new one can't be read, no weather beats old numbers under the new scale.
            if (weather?.first?.unit != unit) weather = null
            sources.weather()?.let {
                weather = it
                weatherAt = now
            }
        }
    }
    LaunchedEffect(open, animate) {
        val spec = when {
            !animate || (!open && closesAtOnce()) -> snap()
            open -> tween(OPEN_MS, easing = EmphasizedDecelerate)
            else -> tween<Float>(CLOSE_MS, easing = EmphasizedAccelerate)
        }
        reveal.animateTo(if (open) 1f else 0f, spec)
    }
    val view = LocalView.current
    BackHandler(enabled = open) {
        // The back gesture's haptic, as the search's own back plays it.
        PixelHapticEngine.playButtonTap(view.context, view)
        onClose()
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { layerTopLeft = it.positionInRoot() }) {
        if (open || revealed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val from = origin()
                        val center = if (from != Offset.Unspecified) from - layerTopLeft else Offset(size.width * 0.1f, size.height - 40.dp.toPx())
                        val far = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y))
                        shape = CircleReveal(center, far * reveal.value)
                        clip = true
                    }
            ) {
                // The page reads from the layer's sources, so what they keep lasts while the search is open. Whatever
                // it opens (the weather, an event, a notification, a player, one of Android's pages) comes Back to it:
                // the search waits behind instead of closing.
                CompositionLocalProvider(LocalTodaySources provides sources, LocalContext provides comesBack) {
                    // One frame loop drives all of the page's endless motion.
                    SharedLoops {
                        TodayPage(
                            active = open,
                            hiddenApps = hiddenApps,
                            recentSearches = recentSearches,
                            onBack = onClose,
                            onSearch = onSearch,
                            onOpenSettings = onOpenSettings,
                            preloadedWeather = weather
                        )
                    }
                }
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

/**
 * The Today page: the day's greeting and what is coming, the weather ahead, what is running and playing, what to catch
 * up on, quick controls, the phone's day, where you left off, a word and a countdown, and tomorrow. Each part is there
 * while its switch (Appearance > Discover Overlay) is on, playing the idle light until it has something to show. Read while
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
    preloadedWeather: Pair<WeatherReport, WeatherReport?>? = null
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val sources = LocalTodaySources.current ?: remember(context) { AndroidTodaySources(context) }
    val now = rememberGlanceNow(30_000L)
    val snapshot = rememberTodaySnapshot(hiddenApps, active)
    var upNextOn by rememberSetting(SettingsManager.TODAY_UP_NEXT)
    var weatherOn by rememberSetting(SettingsManager.TODAY_WEATHER)
    var catchUpOn by rememberSetting(SettingsManager.TODAY_CATCH_UP)
    var controlsOn by rememberSetting(SettingsManager.TODAY_CONTROLS)
    var phoneOn by rememberSetting(SettingsManager.TODAY_PHONE)
    var leftOffOn by rememberSetting(SettingsManager.TODAY_LEFT_OFF)
    var extrasOn by rememberSetting(SettingsManager.TODAY_EXTRAS)
    val leaveByOn by rememberSetting(SettingsManager.TODAY_LEAVE_BY)
    // A saved home place gives the weather without the phone's location.
    val homePlace by rememberSetting(SettingsManager.WEATHER_HOME_LOCATION)
    // The forecast is asked for only while web answers may go out (Web and Web Suggestions on, Tor not the engine).
    // Until then Weather waits for that, and doesn't ask for a location it couldn't use.
    val web by rememberSetting(SettingsManager.SEARCH_WEB)
    val webSuggestions by rememberSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS)
    val engine by rememberSetting(SettingsManager.SEARCH_ENGINE)
    val weatherBlock = com.pixel.intelligentsearch.core.security.NetworkPolicy.webAnswersBlock(web, webSuggestions, engine)
    // Weather with no saved place, and the leave-by time, need the phone's location, so allowing it reads both again.
    val locationOk = rememberPermissionState { com.pixel.intelligentsearch.core.answers.AndroidDeviceLocation(it).permitted() }
    val locationRequest = rememberTodayLocationRequest { }
    // What the layer read wins whenever it has it, including when it arrives or refreshes after the page has opened;
    // the page reads for itself only while it has nothing. With Weather off there is none, so no part shows it.
    val temperatureChoice by rememberSetting(SettingsManager.TEMPERATURE_UNIT)
    val ownWeather = produceState<Pair<WeatherReport, WeatherReport?>?>(null, preloadedWeather == null, active, weatherOn, locationOk, temperatureChoice) {
        if (preloadedWeather == null && active && weatherOn) value = sources.weather()
    }.value
    val loadedWeather = (preloadedWeather ?: ownWeather)?.takeIf { weatherOn }
    val weather = loadedWeather?.first
    val tomorrowWeather = loadedWeather?.second
    val allNotifications = rememberNotifications(sources, active, catchUpOn)
    // Hidden apps stay out of Catch Up and the package tile, as they do from search and At a Glance.
    val notifications = remember(allNotifications, hiddenApps) { allNotifications.filterNot { it.packageName in hiddenApps } }
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
    // Kept while the page closes, so Up Next doesn't lose its leave-by time as it leaves.
    val trip = produceState<TodayTrip?>(null, upNext?.eventId, upNext?.beginMillis, upNext?.location, leaveByOn, active, locationOk) {
        if (!active) return@produceState
        value = if (upNext != null && leaveByOn) sources.trip(upNext) else null
    }.value
    // Leave-by looks up the event's place only with Web on and Tor not the engine; without that, no location is asked.
    val leaveByBlocked = com.pixel.intelligentsearch.core.security.NetworkPolicy.aiBlock(web, engine) != null
    val askLocation = if (leaveByOn && !locationOk && !leaveByBlocked) ({ locationRequest.request() }) else null
    val sunset = weather?.sunset?.toEpochMilli()?.takeIf { it > now }
    val warm = if (scheme.surface.luminance() < 0.5f) Color(0xFFF2B872) else Color(0xFFB5651D)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val animate by rememberAnimationsEnabled()
    val parts = remember { PartsInTurn(all = !animate) }
    LaunchedEffect(parts) { parts.composeInTurn() }

    // The cards: the layout the user made, the app cards read from widgets and the website cards.
    val cardStore = remember(context) { cardLayoutStore(context.applicationContext) }
    val cardLayout by cardStore.layout.collectAsState()
    val editState = rememberDiscoverEditState()
    // The light every empty card plays, one clock for all of them, moving only while the page is open.
    val idleTime = rememberIdleTime(moving = active)
    // Where each of the page's own parts stands when it has nothing to show: switched off on Appearance's Discover
    // Overlay card (Add to Discover's tile turns it on), waiting for what it reads (its empty card's button, or a tap on
    // its tile, asks the app's usual way, and switches it on once allowed), or waiting for something to show.
    val calendarOk = rememberCalendarAllowed()
    val calendarRequest = rememberCalendarRequest { upNextOn = true }
    val notificationsOk = rememberNotificationAccessGranted()
    var notificationsFor by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationsRequest = rememberNotificationAccessRequest { granted ->
        if (granted) {
            if (notificationsFor == CardKind.CATCH_UP.key) catchUpOn = true
            NotificationAccess.start(context)
        }
        notificationsFor = null
    }
    NotificationAccessDialog(notificationsRequest)
    fun turnOn(on: () -> Unit) = PartStatus("Turned off", "Tap to turn on", "Turn on", on)
    fun askNotifications(kind: CardKind) = PartStatus("Needs notification access", "Tap to allow", "Allow notification access", access = CardAccess.NOTIFICATIONS, fix = {
        notificationsFor = kind.key
        notificationsRequest.request()
    })
    val calendarNeeded = PartStatus("Needs calendar access", "Tap to allow", "Allow calendar access", access = CardAccess.CALENDAR, fix = { calendarRequest.request() })
    fun partStatus(kind: CardKind): PartStatus = when (kind) {
        CardKind.UP_NEXT -> when {
            !calendarOk -> calendarNeeded
            !upNextOn -> turnOn { upNextOn = true }
            else -> PartStatus("Shows in the hours before your next event")
        }
        CardKind.REST_OF_DAY -> when {
            !calendarOk -> calendarNeeded
            !upNextOn -> turnOn { upNextOn = true }
            else -> PartStatus("Shows when two or more things are left today")
        }
        CardKind.WEATHER -> when {
            !weatherOn -> turnOn { weatherOn = true }
            weatherBlock != null -> PartStatus(
                when (weatherBlock) {
                    com.pixel.intelligentsearch.core.security.NetworkPolicy.Block.TOR -> "Off while Tor is your search engine"
                    com.pixel.intelligentsearch.core.security.NetworkPolicy.Block.WEB_OFF -> "Needs Web on in Search Sources"
                    com.pixel.intelligentsearch.core.security.NetworkPolicy.Block.WEB_SUGGESTIONS_OFF -> "Needs Web Suggestions on"
                }
            )
            !locationOk && homePlace.isBlank() -> PartStatus("Needs your location", "Tap to allow", "Use your location", access = CardAccess.LOCATION, fix = { locationRequest.request() })
            else -> PartStatus("Shows once the forecast is in")
        }
        // Alarms need nothing; timers and packages are read from their apps' notifications.
        CardKind.TILES -> if (!notificationsOk) askNotifications(kind) else PartStatus("Shows with a timer, an alarm within a day, or a package on its way")
        CardKind.NOW_PLAYING -> if (!notificationsOk) askNotifications(kind) else PartStatus("Shows while something is playing")
        CardKind.CATCH_UP -> when {
            !notificationsOk -> askNotifications(kind)
            !catchUpOn -> turnOn { catchUpOn = true }
            else -> PartStatus("Shows when you have missed calls or messages")
        }
        CardKind.CONTROLS -> if (!controlsOn) turnOn { controlsOn = true } else PartStatus("Shows your quick controls")
        CardKind.PHONE -> if (!phoneOn) turnOn { phoneOn = true } else PartStatus("Shows once the battery is read")
        CardKind.LEFT_OFF -> if (!leftOffOn) turnOn { leftOffOn = true } else PartStatus("Shows after your first search")
        CardKind.WORD -> if (!extrasOn) turnOn { extrasOn = true } else PartStatus("Shows today's word")
        CardKind.YEAR -> if (!extrasOn) turnOn { extrasOn = true } else PartStatus("Shows when a holiday is coming up")
        CardKind.TOMORROW -> PartStatus("Shows when tomorrow has an alarm, an event or a forecast")
        CardKind.APP, CardKind.SITE -> PartStatus("Has nothing to show right now")
    }
    SideEffect { editState.partStatus = ::partStatus }
    // On the page an empty card says what it waits for, but asks for none of it: the access card above the cards asks,
    // once for each thing, however many cards wait for it. Add to Discover's tiles still ask, one card at a time.
    fun pageStatus(kind: CardKind): PartStatus = partStatus(kind).let { if (it.access != null) PartStatus(it.note, access = it.access) else it }
    val widgets = remember(context) { widgetCards(context.applicationContext) }
    val apps by widgets.cards.collectAsState()
    val siteSource = remember(context) { siteCards(context.applicationContext) }
    val sites by siteSource.sites.collectAsState()
    // Read again when the page opens or a card is added, not on every move or resize: each read wakes the widgets' apps.
    val widgetIds = remember(cardLayout.cards) { cardLayout.cards.filter { it.kind == CardKind.APP }.map { it.widgetId }.toSet() }
    val siteUrls = remember(cardLayout.cards) { cardLayout.cards.mapNotNull { it.url }.toSet() }
    LaunchedEffect(active, widgetIds, siteUrls) {
        if (!active) return@LaunchedEffect
        if (widgetIds.isNotEmpty()) widgets.refresh(widgetIds)
        if (siteUrls.isNotEmpty()) siteSource.refresh(siteUrls)
    }
    // Widgets bound for cards that no longer exist (an add left halfway, a card removed while the app was closing) are
    // let go, with what was read from them, once the saved layout is known. Judged by the store's own layout: the one
    // this screen shows can be a frame behind it, still the default with no app cards just after the saved one is
    // read, and judging by that unbound every app card's widget when the app started.
    LaunchedEffect(cardStore.isLoaded, widgetIds) {
        if (!cardStore.isLoaded) return@LaunchedEffect
        val saved = cardStore.layout.value.cards.filter { it.kind == CardKind.APP }.map { it.widgetId }.toSet()
        widgets.keepOnly(saved + widgetIds + listOfNotNull(editState.pendingWidget))
    }
    val appPackages = remember(apps) { apps.values.map { it.packageName }.toSet() }
    val appIcons by produceState(emptyMap<String, ImageBitmap?>(), appPackages) {
        value = appPackages.associateWith { appCardIcon(context, it) }
    }
    // The battery, read here only for a shaped phone card (the phone card's own design reads it for itself). The last
    // reading stays while the page closes, so the card doesn't fall back to its empty light as it leaves.
    val phoneShaped = cardLayout.cards.any { it.kind == CardKind.PHONE && it.mode == CardMode.SHAPED }
    val battery = remember(sources, active && phoneShaped) {
        if (active && phoneShaped) sources.battery() else kotlinx.coroutines.flow.emptyFlow()
    }.collectAsState(initial = null).value
    // Private cards show for a few seconds after a tap, then cover again.
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()
    val accessibility = LocalAccessibilityManager.current
    fun reveal(spec: CardSpec) {
        revealed = revealed + spec.id
        scope.launch {
            // Longer for people who have asked Android for more time to read (Accessibility, Time to take action).
            delay(accessibility?.calculateRecommendedTimeoutMillis(PRIVATE_SHOWN_MS, containsIcons = true, containsText = true, containsControls = true) ?: PRIVATE_SHOWN_MS)
            revealed = revealed - spec.id
        }
    }
    fun removeCard(spec: CardSpec) {
        cardStore.edit { now -> now.copy(cards = now.cards.filterNot { it.id == spec.id }) }
        forgetCard(spec, cardStore.layout.value, widgets, siteSource)
    }
    val setUpWidget = rememberWidgetSetup()
    val addWidget = rememberWidgetAdder(
        onAdded = { id, provider ->
            val info = runCatching { android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetInfo(id) }.getOrNull()
            // No haptic of its own: the widget's tile played the tap's, often only moments before.
            cardStore.edit { now -> now.copy(cards = now.cards + newAppCard(id, provider, info, density.density)) }
            editState.announce("Card added")
        },
        onFailed = { message -> android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show() }
    )
    // A card that is one thing to TalkBack says itself: only "Private card" while it is covered; once shown, its name
    // and, for a shape (whose drawing TalkBack can't see), what it shows. Editing can start from it, as from any card.
    fun Modifier.named(spec: CardSpec, content: ShapedContent? = null): Modifier = semantics {
        val covered = spec.private && spec.id !in revealed
        val name = cardLabel(if (covered) spec else spec.copy(private = false), apps, sites)
        contentDescription = if (covered || content == null) name else listOfNotNull(name, shapedSpoken(content)).joinToString(", ")
        customActions = listOf(
            CustomAccessibilityAction("Edit Discover") {
                editState.enter(spec.id)
                true
            }
        )
    }
    // A tap on a shaped or box card: a private card shows itself first; an app card that needs a choice opens it. A
    // card with nowhere to go (a countdown, tomorrow, a package) is no button: nothing would answer the tap.
    // [name] unless the card is already named over it (a full app card whose app is gone).
    fun Modifier.cardTap(spec: CardSpec, content: ShapedContent, onSearch: (String) -> Unit, reveal: (CardSpec) -> Unit, remove: (CardSpec) -> Unit, name: Boolean = true): Modifier {
        val said = if (name) named(spec, content) else this
        val covered = spec.private && spec.id !in revealed
        val state = (content as? ShapedContent.App)?.data?.state
        if (!covered && !opensSomething(content) && state != AppCardState.REMOVED && state != AppCardState.SETUP) return said
        return said.bouncyClickable {
            when {
                spec.private && spec.id !in revealed -> reveal(spec)
                content is ShapedContent.App && content.data.state == AppCardState.REMOVED -> remove(spec)
                content is ShapedContent.App && content.data.state == AppCardState.SETUP -> setUpWidget(content.data.widgetId)
                else -> openShapedCard(context, content, snapshot.media, timer, alarm, upNext, { catchUp.firstOrNull()?.let { openPendingOrApp(context, it.contentIntent, it.packageName) } }, onSearch)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            // Taps on the page stay on it rather than reaching the search beneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag(TODAY_PAGE_TAG)
    ) {
      LivingLight(warm)
      ConfettiHost { ProvideAmbientBudget {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            var index = 0
            // The greeting fades as it scrolls away. It moves with the page, so the cards under it never slide over its pills.
            Box(Modifier.graphicsLayer {
                alpha = (1f - scroll.value / with(density) { 260.dp.toPx() }).coerceIn(0f, 1f)
            }) {
                Enter(index++, parts) { Greeting(now, weather, sunset, birthdaysToday, warm) }
            }
            // Each part is a card the user can move, resize, shape and hide (long-press), shown in the layout's order. One
            // of the page's own parts is always there while its switch is on; with nothing to show yet it plays the idle
            // light, and asks for what it reads if it needs something.
            val countdown = remember(today, extrasOn) { if (extrasOn) sources.countdown(today) else null }
            val word = remember(today) { wordOfTheDay(today) }
            fun shown(spec: CardSpec): Boolean = when (spec.kind) {
                CardKind.UP_NEXT, CardKind.REST_OF_DAY -> upNextOn
                CardKind.WEATHER -> weatherOn
                CardKind.TILES, CardKind.NOW_PLAYING, CardKind.TOMORROW -> true
                CardKind.CATCH_UP -> catchUpOn
                CardKind.CONTROLS -> controlsOn
                CardKind.PHONE -> phoneOn
                CardKind.LEFT_OFF -> leftOffOn
                CardKind.WORD, CardKind.YEAR -> extrasOn
                CardKind.APP -> spec.form == CardForm.CARD || spec.widgetId in apps
                CardKind.SITE -> spec.url in sites
            }
            // Whether one of the page's own parts has something to show now; without, its card plays the idle light.
            fun hasContent(spec: CardSpec): Boolean = when (spec.kind) {
                CardKind.UP_NEXT -> upNext != null
                CardKind.REST_OF_DAY -> laterToday.size + (if (sunset != null) 1 else 0) >= 2
                CardKind.WEATHER -> weather != null && weather.hours.size >= 3
                CardKind.TILES -> timer != null || alarm != null || delivery != null
                CardKind.NOW_PLAYING -> snapshot.media != null
                CardKind.CATCH_UP -> catchUp.isNotEmpty()
                CardKind.CONTROLS, CardKind.WORD -> true
                CardKind.PHONE -> spec.mode != CardMode.SHAPED || battery != null
                CardKind.LEFT_OFF -> recentSearches.isNotEmpty()
                CardKind.YEAR -> countdown != null
                CardKind.TOMORROW -> alarmTomorrow != null || tomorrowWeather != null || tomorrowEvent != null
                CardKind.APP, CardKind.SITE -> true
            }
            // Each shown card's turn to be built and spring in, after the greeting's.
            val turns = remember(cardLayout, upNextOn, weatherOn, catchUpOn, controlsOn, phoneOn, leftOffOn, extrasOn, apps, sites) {
                cardLayout.cards.filter(::shown).withIndex().associate { (i, spec) -> spec.id to index + i }
            }

            /** The design of a built-in card at its full, natural size: the page's own parts, or its empty card. */
            @Composable
            fun natural(spec: CardSpec) {
                val kind = spec.kind
                if (kind.builtIn && !hasContent(spec)) {
                    IdleCard(kind, pageStatus(kind), idleTime)
                    return
                }
                when (kind) {
                    CardKind.UP_NEXT -> upNext?.let { UpNextCard(it, now, trip, askLocation) }
                    CardKind.REST_OF_DAY -> RestOfDay(laterToday, now, sunset)
                    CardKind.WEATHER -> weather?.let { WeatherAhead(it) }
                    CardKind.TILES -> TimerAlarmTiles(timer, alarm?.let { GlanceItem.Alarm(it) }, delivery, now)
                    CardKind.NOW_PLAYING -> snapshot.media?.let { NowPlaying(it) }
                    CardKind.CATCH_UP -> CatchUp(catchUp, now)
                    CardKind.CONTROLS -> QuickControls(sources.controls)
                    CardKind.PHONE -> PhoneToday(sources, active)
                    CardKind.LEFT_OFF -> LeftOff(recentSearches.take(6), onSearch)
                    CardKind.WORD -> WordOfTheDay(word, onSearch)
                    CardKind.YEAR -> countdown?.let { CountdownCard(it) }
                    CardKind.TOMORROW -> Tomorrow(today.plusDays(1), alarmTomorrow?.triggerAt, tomorrowWeather, tomorrowEvent)
                    CardKind.APP, CardKind.SITE -> Unit
                }
            }

            /** What a card draws in its shape, or null when it has nothing to draw one from. */
            fun shapedContent(spec: CardSpec): ShapedContent? = if (!hasContent(spec)) null else when (spec.kind) {
                CardKind.UP_NEXT -> upNext?.let { shapedEvent(context, it, now) }
                CardKind.REST_OF_DAY -> shapedAgenda(context, laterToday, sunset)
                CardKind.CATCH_UP -> shapedCatchUp(catchUp)
                CardKind.LEFT_OFF -> recentSearches.firstOrNull()?.let { ShapedContent.Search(it) }
                CardKind.TOMORROW -> shapedTomorrow(context, today.plusDays(1), alarmTomorrow?.triggerAt, tomorrowWeather, tomorrowEvent)
                CardKind.WEATHER -> weather?.let { shapedWeather(context, it, now) }
                CardKind.TILES -> timer?.let { shapedTimer(it, now) } ?: alarm?.let { shapedAlarm(context, it, now) } ?: delivery?.let(::shapedDelivery)
                CardKind.NOW_PLAYING -> snapshot.media?.let(::shapedMedia)
                CardKind.PHONE -> battery?.let(::shapedBattery)
                CardKind.WORD -> shapedWord(word)
                CardKind.YEAR -> countdown?.let(::shapedCountdown)
                CardKind.APP -> apps[spec.widgetId]?.let { ShapedContent.App(it, appIcons[it.packageName]) }
                CardKind.SITE -> sites[spec.url]?.let { ShapedContent.Site(it) }
                else -> null
            }
            // What the empty cards wait for the user to allow, asked in one place, one thing at a time.
            val asks = cardLayout.cards
                .filter { it.kind.builtIn && shown(it) && !hasContent(it) }
                .map { partStatus(it.kind) }
                .filter { it.access != null && it.fix != null }
                .distinctBy { it.access }
            if (asks.isNotEmpty() && !editState.editing) Enter(index, parts) { AccessCard(asks) }
            DiscoverGrid(
                state = editState,
                layout = cardLayout,
                onLayoutChange = cardStore::update,
                shown = ::shown,
                minShape = { spec -> leastShapeDp(shapedContent(spec), density.fontScale, emptyKind = spec.kind) },
                label = { spec -> cardLabel(spec, apps, sites) },
                scroll = scroll
            ) { spec, cardModifier ->
                Enter(turns[spec.id] ?: index, parts) {
                    when (spec.mode) {
                        // A private card is covered whole until tapped, as shapes and app cards are.
                        CardMode.NATURAL -> {
                            val covered = spec.private && spec.id !in revealed
                            // A private card says itself here, covered or shown: the grid names only cards that aren't private.
                            val own = if (spec.private) cardModifier.named(spec) else cardModifier
                            CoveredCard(covered, CardShape, if (covered) own.bouncyClickable(shape = CardShape) { reveal(spec) } else own) {
                                natural(spec)
                            }
                        }
                        CardMode.SHAPED -> {
                            val content = shapedContent(spec)
                            if (content != null) {
                                ShapedCard(
                                    content = content,
                                    shape = spec.shape,
                                    private = spec.private,
                                    revealed = spec.id in revealed,
                                    modifier = cardModifier.cardTap(spec, content, onSearch, ::reveal, ::removeCard)
                                )
                            } else if (spec.kind.builtIn) {
                                // Empty: the idle light in its shape. One that needs something asks on a tap; a private
                                // one is covered like any other until tapped.
                                val status = pageStatus(spec.kind)
                                val covered = spec.private && spec.id !in revealed
                                val fix = status.fix?.takeIf { status.button != null }
                                val said = cardModifier.named(spec).let { if (covered) it else it.semantics { stateDescription = status.note } }
                                CoveredBox(
                                    covered = covered,
                                    shape = CardOutlineShape(spec.shape),
                                    modifier = when {
                                        covered -> said.bouncyClickable { reveal(spec) }
                                        fix != null -> said.bouncyClickable(onClickLabel = status.button) { fix() }
                                        else -> said
                                    }
                                ) {
                                    IdleShapedCard(spec.shape, spec.kind, idleTime, action = if (fix != null) status.button else null, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        // A full app card is the app's own widget, live: it updates, and its buttons work.
                        CardMode.BOX -> if (spec.kind == CardKind.APP) {
                            val covered = spec.private && spec.id !in revealed
                            LiveWidget(
                                widgetId = spec.widgetId,
                                modifier = (if (spec.private) cardModifier.named(spec) else cardModifier).let { own ->
                                    if (covered) own.bouncyClickable { reveal(spec) } else own
                                },
                                private = spec.private,
                                revealed = spec.id in revealed,
                                onGone = { m ->
                                    val gone = apps[spec.widgetId]?.copy(state = AppCardState.REMOVED)
                                        ?: AppCardData(spec.widgetId, "", "App", "", 0xFF8A8A8A.toInt(), AppCardState.REMOVED, null, 0L)
                                    val content = ShapedContent.App(gone, null)
                                    // Covered like any private card: its app's name is still on it.
                                    AppCardBox(
                                        gone, null,
                                        modifier = m.cardTap(spec, content, onSearch, ::reveal, ::removeCard, name = spec.private),
                                        private = spec.private, revealed = spec.id in revealed
                                    )
                                }
                            )
                        } else when (val content = shapedContent(spec)) {
                            is ShapedContent.App -> AppCardBox(
                                content.data, content.icon,
                                modifier = cardModifier.cardTap(spec, content, onSearch, ::reveal, ::removeCard),
                                private = spec.private, revealed = spec.id in revealed
                            )
                            is ShapedContent.Site -> SiteCardBox(
                                content.data,
                                modifier = cardModifier.cardTap(spec, content, onSearch, ::reveal, ::removeCard),
                                private = spec.private, revealed = spec.id in revealed
                            )
                            else -> Box(cardModifier, propagateMinConstraints = true) { natural(spec) }
                        }
                    }
                }
            }
        }
      } }
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
        EditButton(
            visible = !editState.editing,
            onClick = { editState.enter(null) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
        )
        // Edit mode's bar, its drop target and its sheets, over the page.
        DiscoverEditChrome(
            state = editState,
            layout = cardLayout,
            onLayoutChange = cardStore::update,
            widgetChoices = { widgets.choices() },
            onAddWidget = addWidget,
            onAddSite = { input ->
                siteSource.add(input).mapCatching { data ->
                    // The same page twice would share one reading, and removing either would blank the other.
                    if (cardStore.layout.value.cards.any { it.url == data.url }) throw IllegalStateException(SITE_ALREADY_ADDED)
                    cardStore.edit { now -> now.copy(cards = now.cards + newSiteCard(data.url)) }
                }
            },
            onRemoveCustom = { forgetCard(it, cardStore.layout.value, widgets, siteSource) },
            modifier = Modifier.fillMaxSize()
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
 * Which of the page's parts have been built so far: one more on each frame as the page opens, the greeting first, so
 * no single frame builds the whole page. Each part shows only after its turn in [Enter] (80 ms and more), so none is
 * seen late. With animations off, [all] of them are built at once, as the page shows at once.
 */
@Stable
private class PartsInTurn(all: Boolean) {
    var built by mutableIntStateOf(if (all) Int.MAX_VALUE else 1)
        private set

    suspend fun composeInTurn() {
        while (built < IN_TURN) {
            withFrameNanos { }
            built++
        }
        built = Int.MAX_VALUE
    }

    private companion object {
        /** Frames after which any parts left are built together; the page has 13 parts at most. */
        const val IN_TURN = 14
    }
}

/**
 * Each part springs up into place as it comes into view: those on screen as the page opens one after another, 70 ms
 * apart, the rest as the user scrolls to them (at once with animations off). Its content is built on its turn in
 * [parts]; until then it takes no space.
 */
@Composable
private fun Enter(index: Int, parts: PartsInTurn, content: @Composable () -> Unit) {
    val animate by rememberAnimationsEnabled()
    val shown = remember { Animatable(if (animate) 0f else 1f) }
    var seenAt by remember { mutableLongStateOf(0L) }
    val opened = remember { System.currentTimeMillis() }
    val built by remember(index) { derivedStateOf { index < parts.built } }
    // How long after the page opened this part was built, which its turn below doesn't wait again.
    val late = remember(built) { if (built) System.currentTimeMillis() - opened else 0L }
    LaunchedEffect(seenAt) {
        if (!animate || seenAt == 0L) return@LaunchedEffect
        // On screen from the start: in turn. Scrolled to later: at once.
        if (seenAt - opened - late < 400) delay((80L + index * 70L - late).coerceAtLeast(0L))
        shown.animateTo(1f, arriveSpring())
    }
    if (!built) return
    val density = LocalDensity.current
    Box(
        propagateMinConstraints = true,
        modifier = Modifier
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

/** The page's greeting at [hour] of the day (0 to 23). */
internal fun dayGreeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
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
    val greeting = dayGreeting(time.hour)
    val animate by rememberAnimationsEnabled()
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
      Row(verticalAlignment = Alignment.Bottom) {
       Column(Modifier.weight(1f)) {
        Text(date, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = scheme.onPage)
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // The weather pill is taller, with the moon under it; the pills beside it center on it.
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                if (weather != null) {
                    TodayChip(
                        icon = weatherIcon(weather.code, weather.isDay),
                        text = "${weather.temperature.roundToInt()}° ${WeatherCodes.describe(weather.code)}",
                        detail = HighLow.drawn(weather.high),
                        spokenDetail = HighLow.spoken(weather.high),
                        spin = WeatherCodes.kind(weather.code) == WeatherCodes.Kind.CLEAR && weather.isDay,
                        // Tonight's moon under the weather, as an almanac words the day.
                        below = {
                            val moon = remember(time.toLocalDate()) { moonPhase(time.toLocalDate(), time.zone) }
                            val color = MaterialTheme.colorScheme.onSurfaceVariant
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MoonPhaseIcon(moon.age, com.pixel.intelligentsearch.core.occasion.OccasionCalendar.isSouthern(time.zone), color, Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(moon.name, style = MaterialTheme.typography.bodySmall, color = color)
                            }
                        },
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
