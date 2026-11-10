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
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
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
import com.pixel.intelligentsearch.core.system.ComesBackContext
import com.pixel.intelligentsearch.core.system.WaitsForPermissionGrant
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.core.util.findActivity
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
import com.pixel.intelligentsearch.feature.search.today.cards.grid.EditIcons
import com.pixel.intelligentsearch.feature.search.today.cards.widget.LiveWidget
import com.pixel.intelligentsearch.feature.search.today.cards.AppCardData
import com.pixel.intelligentsearch.feature.search.today.cards.CardForm
import com.pixel.intelligentsearch.core.system.openPendingOrApp
import com.pixel.intelligentsearch.feature.search.today.cards.grid.DiscoverGrid
import com.pixel.intelligentsearch.feature.search.today.cards.grid.EditHaptic
import com.pixel.intelligentsearch.feature.search.today.cards.grid.cardLayoutStore
import com.pixel.intelligentsearch.feature.search.today.cards.grid.rememberDiscoverEditState
import com.pixel.intelligentsearch.feature.search.today.cards.shape.AppCardBox
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
    LaunchedEffect(discoverOn, weatherOn, open) {
        val now = System.currentTimeMillis()
        if (discoverOn && weatherOn && (weather == null || now - weatherAt > REFRESH_MS)) {
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
    BackHandler(enabled = open) { onClose() }

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
    preloadedWeather: Pair<WeatherReport, WeatherReport?>? = null
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
    val locationRequest = rememberTodayLocationRequest { }
    val askLocation = if (leaveByOn && !locationOk) ({ locationRequest.request() }) else null
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
    // The battery, read here only for a shaped phone card (the phone card's own design reads it for itself).
    val phoneShaped = cardLayout.cards.any { it.kind == CardKind.PHONE && it.mode == CardMode.SHAPED }
    val battery = remember(sources, active && phoneShaped) {
        if (active && phoneShaped) sources.battery() else kotlinx.coroutines.flow.flowOf(null)
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
            cardStore.edit { now -> now.copy(cards = now.cards + newAppCard(id, provider, info, density.density)) }
            editState.haptic(EditHaptic.CONFIRM)
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
            // The greeting drifts away slower than the page and fades as it scrolls (parallax).
            Box(Modifier.graphicsLayer {
                translationY = scroll.value * 0.35f
                alpha = (1f - scroll.value / with(density) { 260.dp.toPx() }).coerceIn(0f, 1f)
            }) {
                Enter(index++, parts) { Greeting(now, weather, sunset, birthdaysToday, warm) }
            }
            // Each part is a card the user can move, resize, shape and hide (long-press). Shown in the layout's order,
            // the ones with something to show; the default layout is the page as it always was.
            val countdown = remember(today, extrasOn) { if (extrasOn) sources.countdown(today) else null }
            val word = remember(today) { wordOfTheDay(today) }
            fun shown(spec: CardSpec): Boolean = when (spec.kind) {
                CardKind.UP_NEXT -> upNext != null
                CardKind.REST_OF_DAY -> upNextOn && laterToday.size + (if (sunset != null) 1 else 0) >= 2
                CardKind.WEATHER -> weather != null && weather.hours.size >= 3
                CardKind.TILES -> timer != null || alarm != null || delivery != null
                CardKind.NOW_PLAYING -> snapshot.media != null
                CardKind.CATCH_UP -> catchUpOn && catchUp.isNotEmpty()
                CardKind.CONTROLS -> controlsOn
                CardKind.PHONE -> phoneOn && (spec.mode != CardMode.SHAPED || battery != null)
                CardKind.LEFT_OFF -> leftOffOn && recentSearches.isNotEmpty()
                CardKind.WORD -> extrasOn
                CardKind.YEAR -> extrasOn && countdown != null
                CardKind.TOMORROW -> alarmTomorrow != null || tomorrowWeather != null || tomorrowEvent != null
                CardKind.APP -> spec.form == CardForm.CARD || spec.widgetId in apps
                CardKind.SITE -> spec.url in sites
            }
            // Each shown card's turn to be built and spring in, after the greeting's.
            val turns = remember(cardLayout, upNext, weather, timer, alarm, delivery, snapshot.media, catchUp, recentSearches, countdown, tomorrowWeather, battery, apps, sites) {
                cardLayout.cards.filter(::shown).withIndex().associate { (i, spec) -> spec.id to index + i }
            }

            /** The design of a built-in card at its full, natural size: the page's own parts. */
            @Composable
            fun natural(kind: CardKind) {
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
            fun shapedContent(spec: CardSpec): ShapedContent? = when (spec.kind) {
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
            DiscoverGrid(
                state = editState,
                layout = cardLayout,
                onLayoutChange = cardStore::update,
                shown = ::shown,
                minShape = { spec -> leastShapeDp(shapedContent(spec), density.fontScale) },
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
                                natural(spec.kind)
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
                            else -> Box(cardModifier) { natural(spec.kind) }
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
                                MoonPhaseIcon(moon.age, color, Modifier.size(13.dp))
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
    // Read as the bar is laid out, so filling it never rebuilds the card.
    val fill = animateFloatAsState(target, if (animate) tween(800, delayMillis = 300) else snap(), label = "leave by")
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
            Box(Modifier.fillWidth { fill.value }.height(6.dp).clip(CircleShape).background(scheme.onPrimaryContainer))
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
    // Whether the full bar still takes a tap: read here, the shrink would rebuild the bar on every frame of it.
    val tappable by remember { derivedStateOf { shrink.value < 0.5f } }
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
                    if (tappable) {
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

/**
 * Edit Discover: a round button at the bottom right, in the bar's body color, that comes in as the bar goes down to
 * Back and Settings (at once with animations off) and steps aside while editing.
 */
@Composable
private fun EditButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    var ready by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) {
        if (!ready) {
            delay(BAR_SHRINK_DELAY_MS + SEARCH_MILLIS / 2)
            ready = true
        }
    }
    val shown by animateFloatAsState(
        if (visible && ready) 1f else 0f,
        if (animate) spring(dampingRatio = 0.7f, stiffness = 500f) else snap(),
        label = "edit button"
    )
    if (shown <= 0f && !(visible && ready)) return
    Box(
        modifier.padding(end = PILL_SIDE, bottom = PILL_MARGIN).height(PILL_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(56.dp)
                .graphicsLayer {
                    alpha = shown.coerceIn(0f, 1f)
                    val s = 0.7f + 0.3f * shown
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(scheme.surfaceVariant)
                .bouncyClickable(shape = CircleShape, onClick = onClick)
                .semantics { contentDescription = "Edit Discover" },
            contentAlignment = Alignment.Center
        ) {
            Icon(EditIcons.Edit, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}

/** Hermite smoothing of [x] between [edge0] and [edge1], as the search pill's own. */
private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
