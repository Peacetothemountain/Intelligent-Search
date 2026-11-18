package com.pixel.intelligentsearch.feature.tabs.gallery

import com.pixel.intelligentsearch.feature.tabs.ui.*
import android.view.RoundedCorner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.max
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** What a card draws in its window besides the bar, which the full preview carries while it grows and shrinks. */
internal enum class Decor { None, Badge, SmallBadge, Hero, HeroOn }

/** Where the preview shrinks back to: the card's window and its bar (page coordinates), and what that window holds. */
internal class PreviewTarget(val scene: Rect, val bar: Rect?, val decor: Decor)

/** The full preview's state: which looks it swipes through, and its motion. */
@Stable
internal class PreviewController {
    var open by mutableStateOf(false)
    var closing by mutableStateOf(false)
    var list by mutableStateOf(emptyList<Look>())
    var index by mutableIntStateOf(0)
    var context by mutableStateOf("rack")
    var search by mutableStateOf(false)
    var bare by mutableStateOf(false)
    var applied by mutableStateOf(false)
    var noHint by mutableStateOf(false)
    /** Where the window grows from (and shrinks back to), in the page's coordinates, and its corner radius in px. */
    var from by mutableStateOf(Rect.Zero)
    var fromRadius by mutableFloatStateOf(0f)
    /** Where the bar sits in that card (top start and width), so it can travel to its row. */
    var fromBar by mutableStateOf<Rect?>(null)
    /** What else that card's window shows (the look of the week's label and search page, the check badge). */
    var decor by mutableStateOf(Decor.None)
    val bounds = Animatable(0f)
    val barTravel = Animatable(0f)
    val chrome = Animatable(0f)
    val searchReveal = Animatable(0f)
    /** The buttons going away while the wallpaper is tapped bare: 0 shown, 1 gone (top ones up, bottom ones down). */
    val bareHide = Animatable(0f)
    /** How far the lowest row's bar has moved down with the bottom buttons while bare (px). */
    val bareShift = Animatable(0f)
    /**
     * The pager: how far (px) the look shown is moved from its place, the looks on either side following one screen
     * away. Wallpaper, bar, search page and name all sit on it, so they follow the finger together.
     */
    val pageOffset = Animatable(0f)
    /** Where the top row's bottom edge is (px, page coordinates): the snackbar shows just under it. */
    var topEnd by mutableFloatStateOf(0f)
    /**
     * Where the bar rests and where the notes and buttons at the bottom begin (px, page coordinates), and how far up
     * from the page's bottom edge those reach (px): a snackbar shows on whichever side of the bar has more room.
     */
    var barTop by mutableFloatStateOf(0f)
    var barBottom by mutableFloatStateOf(0f)
    var notesTop by mutableFloatStateOf(0f)
    var lowReach by mutableFloatStateOf(0f)
    /** How strongly the backdrop is blurred right now (px), for Colors only, where the app draws the backdrop. */
    var backdropBlur by mutableFloatStateOf(0f)
    /** The same for a look's own wallpaper, which the preview draws itself. */
    var wallBlur by mutableFloatStateOf(0f)

    /**
     * Previous and next by a side tap (or the arrows): the looks as a stack of big index cards ([FpCard]) while one is
     * filed over the other; empty at rest, when the pager draws the one look shown.
     */
    val fpClock = MotionClock()
    val stack = androidx.compose.runtime.mutableStateListOf<FpCard>()
    val fpFrame = mutableIntStateOf(0)
    var fpSeq = 0

    val look: Look? get() = list.getOrNull(index)
    /** Fully open: the page under it needn't draw. */
    val covering: Boolean get() = open && !closing && bounds.value >= 0.999f

    /** Drops the side-tap cards (the pager draws the look shown). */
    fun endStack() {
        stack.forEach { it.kill() }
        stack.clear()
    }

    /** Snaps everything to fully open (a screen recreated with the preview open). */
    suspend fun snapOpen() {
        bounds.snapTo(1f); barTravel.snapTo(1f); chrome.snapTo(1f); pageOffset.snapTo(0f)
        bareHide.snapTo(0f); bareShift.snapTo(0f); bare = false
        searchReveal.snapTo(if (search) 1f else 0f)
    }

    /** Snaps everything to where an opening starts. */
    suspend fun snapClosed() {
        endStack()
        bounds.snapTo(0f); barTravel.snapTo(0f); chrome.snapTo(0f); searchReveal.snapTo(0f); pageOffset.snapTo(0f)
        bareHide.snapTo(0f); bareShift.snapTo(0f)
    }
}

/**
 * A full-preview card is a swatch too: it turns on a pivot far below the screen, as the strips turn on their rivet.
 * Going on, the current look swings off to the right and the next one is there under it; going back, the look before
 * swings in from the right and lands on the current one. [A] is its angle: 0 upright, [FP_SWING] wholly off the screen.
 */
internal class FpCard(val look: Look, val seq: Int, clock: MotionClock) {
    val A = Spr(0f, SP.spatial, clock, 0.01f)
    var leaving = false
    var dead = false

    fun kill() {
        dead = true
        A.stop()
    }
}

/** What the preview asks of the page. */
internal interface PreviewHost {
    val state: GalleryState
    val phoneDark: Boolean
    val backdrop: Backdrop
    /** A menu, dialog or sheet is open over the preview: back is theirs. */
    val popOpen: Boolean
    fun apply(look: Look, asBefore: Boolean, onDone: (Commit) -> Unit)
    /** Asks where to set [look]'s wallpaper on the phone, and sets it on a yes. */
    fun setWallpaper(look: Look)
    fun save(look: Look)
    fun customize()
    fun snack(s: Snack)
    /** The card [look] shrinks back into (scrolled into view first), in page coordinates. */
    suspend fun target(look: Look, context: String): PreviewTarget?
}

/** A rounded rectangle at [rect] (page coordinates) with [radius] px corners: the window as it grows. */
private class WindowShape(val rect: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

/**
 * Try before apply: the card's window grows until the whole screen is the home screen with the look's bar on it.
 * The owner's own wallpaper never moves; a look's own wallpaper grows with the window, out of the band its card showed.
 * Swiping between looks is one pager: each look's wallpaper, bar (or search page) and name sit on the same page and
 * follow the finger together, settling on the spatial spring. Nothing changes until Apply, and the phone's wallpaper
 * only through Set wallpaper.
 */
@Composable
internal fun GalleryPreview(pv: PreviewController, host: PreviewHost, blurAvailable: Boolean, pageWidth: Float, pageHeight: Float) {
    if (!pv.open) return
    val look = pv.look ?: return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current
    // The phone's colors for the controls; the look's for its bar and search page (its own wallpaper's, on that).
    val phoneSchemes = rememberGallerySchemes()
    val store = LocalWallpaperStore.current
    val wallpaper = if (store != null) wallpaperOf(look, look.appDark(host.phoneDark)) else null
    // A hole to Android's wallpaper: that is the backdrop, or this look has no wallpaper of its own.
    val realWallpaper = host.backdrop != Backdrop.ColorsOnly && wallpaper == null
    if (store != null) {
        // This look's wallpaper and its neighbors', so a swipe never waits on one.
        val size = pv.list.size
        val near = listOfNotNull(look, pv.list.getOrNull((pv.index - 1 + size) % size.coerceAtLeast(1)), pv.list.getOrNull((pv.index + 1) % size.coerceAtLeast(1)))
        for (w in near.mapNotNull { wallpaperOf(it, it.appDark(host.phoneDark)) }.distinctBy { it.id }) {
            key(w.id) {
                KeepWallpaper(store, w, "full")
                KeepWallpaper(store, w, "card")
            }
        }
    }
    val state = host.state
    val tick = rememberTick()
    val fontScale = density.fontScale
    val lg = fontScale >= 1.3f
    val screenRadius = remember(view) {
        view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: with(density) { 28.dp.toPx() }
    }
    val full = Rect(0f, 0f, pageWidth, pageHeight)

    fun close() {
        if (pv.closing) return
        pv.closing = true
        pv.endStack()
        scope.launch {
            pv.search = false
            pv.searchReveal.snapTo(0f)
            pv.bare = false
            pv.pageOffset.snapTo(0f)
            val t = host.target(pv.look ?: look, pv.context)
            if (t != null) {
                pv.from = t.scene
                pv.fromBar = t.bar
                pv.decor = t.decor
                pv.fromRadius = with(density) { 28.dp.toPx() }
            } else {
                pv.from = Rect(full.center, full.center)
                pv.fromBar = null
                pv.decor = Decor.None
            }
            launch { pv.chrome.animateTo(0f, Springs.spatial()) }
            launch { pv.barTravel.animateTo(0f, Springs.damped()) }
            pv.bounds.animateTo(0f, Springs.damped())
            pv.open = false
            pv.closing = false
            pv.applied = false
        }
    }

    // The pager settles on the look [dir] steps away (or back on this one), carrying the finger's [velocity] (px/s).
    fun go(dir: Int, velocity: Float = 0f) {
        val j = pv.index + dir
        scope.launch {
            if (dir == 0 || j !in pv.list.indices) {
                pv.pageOffset.animateTo(0f, Springs.spatial(), initialVelocity = velocity)
                return@launch
            }
            // The next look becomes the one shown where it is now (one screen to the side), then springs into place.
            val at = pv.pageOffset.value + dir * pageWidth
            pv.index = j
            pv.applied = false
            pv.pageOffset.snapTo(at)
            pv.pageOffset.animateTo(0f, Springs.spatial(), initialVelocity = velocity)
        }
    }

    // The clock the side-tap cards move on: one draw a frame; once the card filed to the front has come to rest it is
    // the screen again, and the cards behind it go.
    LaunchedEffect(pv) {
        pv.fpClock.run {
            pv.fpFrame.intValue++
            // Cards gone off the screen leave; once one card is left at rest it is the screen again.
            if (pv.stack.removeAll { it.dead }) Unit
            val only = pv.stack.singleOrNull()
            if (only != null && !only.A.active) {
                CardHaptics.land(view)
                pv.endStack()
            }
        }
    }

    /*
     * Previous (-1) or next (+1): a swatch turn, with the whole screen as the strip, the way the deck turns. On, the next
     * look is laid under, and the current one swings off to the right. Back, the look before swings in from the right
     * over the current one and lands on it. A tap while cards are still turning adds to the same motion; every card
     * stays whole and opaque.
     */
    fun fileTo(dir: Int) {
        val size = pv.list.size
        if (size < 2 || pv.closing) return
        if (pv.search) return go(dir)
        // At either end it stops, as a swipe and the fan do: the look gives a little that way and comes back.
        val j = pv.index + dir
        if (j !in pv.list.indices) {
            scope.launch {
                pv.endStack()
                pv.pageOffset.animateTo(-dir * pageWidth * 0.06f, Springs.fast())
                pv.pageOffset.animateTo(0f, Springs.spatial())
            }
            return
        }
        val clock = pv.fpClock
        scope.launch { pv.pageOffset.snapTo(0f) }
        // The look shown now (read here: this runs from gestures that outlive a composition).
        val shown = pv.look ?: return
        if (pv.stack.isEmpty()) pv.stack.add(FpCard(shown, ++pv.fpSeq, clock))
        pv.index = j
        pv.applied = false
        val n = FpCard(pv.list[j], ++pv.fpSeq, clock)
        CardHaptics.pass(view)
        if (dir < 0) {
            n.A.set(FP_SWING)
            n.A.to(0f, 0f, SP.spatial) { pv.stack.forEach { c -> if (c !== n && c.seq < n.seq) c.kill() }; clock.kick() }
            pv.stack.add(n)
        } else {
            pv.stack.forEach { o ->
                o.leaving = true
                o.A.to(FP_SWING, null, SP.spatial) { o.kill(); clock.kick() }
            }
            pv.stack.add(0, n)
        }
    }

    fun setSearch(on: Boolean) {
        if (pv.search == on) return
        pv.search = on
        pv.bare = false
        scope.launch {
            if (on) pv.searchReveal.animateTo(1f, Springs.damped()) else pv.searchReveal.animateTo(0f, Springs.damped())
        }
    }

    fun apply() {
        val asBefore = pv.context == "yours" && look === state.before
        if (state.isOn(look)) return host.customize()
        if (pv.applied) return
        host.apply(look, asBefore) { result ->
            if (result !is Commit.Done) return@apply
            pv.applied = true
            scope.launch {
                delay(450)
                close()
                while (pv.open) delay(16)
                host.snack(result.snack)
            }
        }
    }

    // Back: in search, back to the home screen; otherwise predictive back shrinks the window toward its card.
    PredictiveBackHandler(enabled = pv.open && !pv.closing && !pv.search && !host.popOpen) { events ->
        try {
            events.collect { e -> pv.bounds.snapTo(1f - 0.12f * e.progress) }
            close()
        } catch (e: CancellationException) {
            pv.bounds.animateTo(1f, Springs.damped())
            throw e
        }
    }
    BackHandler(enabled = pv.open && !pv.closing && pv.search && !host.popOpen) { setSearch(false) }

    val searchDark = look.appDark(host.phoneDark)
    val q = look.search

    // The real wallpaper is blurred by the window, as the real search page blurs it; a look's own wallpaper and
    // Colors only's mesh are drawn by the app, which blurs them itself.
    val window = remember(context) { context.findActivity()?.window }
    LaunchedEffect(look, pv.search, blurAvailable, host.backdrop) {
        val px = if (q.wallpaper && blurAvailable) q.blur else 0
        snapshotFlow { pv.searchReveal.value }.collect { r ->
            setWindowBlur(window, if (realWallpaper) (px * r).roundToInt() else 0)
            pv.wallBlur = if (wallpaper != null) px * r else 0f
            pv.backdropBlur = if (host.backdrop == Backdrop.ColorsOnly) px * r else 0f
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            setWindowBlur(window, 0)
            pv.backdropBlur = 0f
            pv.wallBlur = 0f
        }
    }
    // Status bar icons suit what is behind them: the wallpaper, or the search page's light or dark.
    val wallDarkIcons = rememberWallpaperWantsDarkIcons()
    val meshColors = rememberWallpaperColors()
    // Leaving, the icons suit the app's theme as it is then: Apply may have changed it while the preview was up, and the
    // theme doesn't set them again on its own.
    val appDark = androidx.compose.runtime.rememberUpdatedState(MaterialTheme.colorScheme.isDark)
    DisposableEffect(window) {
        val c = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        onDispose { if (c != null) c.isAppearanceLightStatusBars = !appDark.value }
    }
    val ownTopLight = if (store != null && wallpaper != null) store.palettes[wallpaper.id]?.lightTop ?: (Color(wallpaper.base).luminance() > 0.5f) else null
    LaunchedEffect(pv.search, searchDark, q.wallpaper, wallDarkIcons, ownTopLight, host.backdrop) {
        val c = window?.let { WindowCompat.getInsetsController(it, it.decorView) } ?: return@LaunchedEffect
        c.isAppearanceLightStatusBars = when {
            pv.search -> !searchDark
            ownTopLight != null -> ownTopLight
            realWallpaper -> wallDarkIcons
            else -> meshColors[0].luminance() > 0.5f
        }
    }

    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    var topRowH by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }
    var toolbarH by remember { mutableFloatStateOf(with(density) { 64.dp.toPx() }) }
    var noteMax by remember { mutableFloatStateOf(with(density) { 28.dp.toPx() }) }
    val barScale = ((pageWidth - with(density) { 32.dp.toPx() }) / with(density) { BAR_WIDTH.toPx() }).coerceAtMost(1f)
    val barW = with(density) { BAR_WIDTH.toPx() } * barScale
    fun barHOf(l: Look) = with(density) { barHeight(l).toPx() } * barScale
    val barH = barHOf(look)
    val topRowTop = statusTop + with(density) { 8.dp.toPx() }
    val topEnd = topRowTop + topRowH
    SideEffect { pv.topEnd = topEnd }
    val tbTop = pageHeight - navBottom - with(density) { 16.dp.toPx() } - toolbarH
    val c0 = topEnd + with(density) { 16.dp.toPx() } + barH / 2
    val room = tbTop - with(density) { 8.dp.toPx() } - noteMax - with(density) { 14.dp.toPx() } - barH / 2 - c0
    if (room < with(density) { 40.dp.toPx() } && !pv.noHint && state.opens <= 3) pv.noHint = true
    val c5 = c0 + room.coerceAtLeast(0f)
    val rows = (0..5).map { c0 + (c5 - c0) * it / 5f }
    val notesTop = tbTop - with(density) { 8.dp.toPx() } - noteMax
    SideEffect {
        pv.barTop = rows[state.row] - barH / 2
        pv.barBottom = rows[state.row] + barH / 2
        pv.notesTop = notesTop
        pv.lowReach = pageHeight - notesTop
    }
    // Bare, the lowest row's bar goes down to where a home screen's bottom bar sits, with the buttons below it.
    val bareCenter = pageHeight - navBottom - with(density) { 24.dp.toPx() } - barH / 2
    val barX = (pageWidth - barW) / 2f
    val barY = remember { Animatable(rows[state.row] - barH / 2) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(rows[state.row], barH) { if (!dragging) barY.animateTo(rows[state.row] - barH / 2, Springs.spatial()) }
    // Tapping the wallpaper hides the buttons: the top ones slide up and the bottom ones down, on the spatial spring,
    // and the lowest row's bar moves down with them on the same spring, so the buttons never pass over it.
    LaunchedEffect(pv.bare, state.row, bareCenter) {
        val shift = if (pv.bare && state.row == 5) bareCenter - rows[5] else 0f
        launch { pv.bareHide.animateTo(if (pv.bare) 1f else 0f, Springs.spatial()) }
        if (!dragging) pv.bareShift.animateTo(shift, Springs.spatial())
    }

    // The window: bounds from the card's scene to the screen; corners from the card's to the display's. Fully open it
    // is the whole screen, square-cornered, so nothing of what is behind the app shows in its corners (the display's
    // own corners round it), even with a sheet over it.
    val t = pv.bounds.value
    val rect = Rect(
        lerp(pv.from.left, full.left, t), lerp(pv.from.top, full.top, t),
        lerp(pv.from.right, full.right, t), lerp(pv.from.bottom, full.bottom, t)
    )
    val radius = if (t >= 0.999f && !pv.closing) 0f else lerp(pv.fromRadius, screenRadius, t)
    val windowShape = WindowShape(rect, radius)
    // The looks on the pager now: this one and, while it is moved, the one coming in beside it.
    fun pages(): List<Pair<Look, Float>> {
        val off = pv.pageOffset.value
        return (-1..1).mapNotNull { d ->
            val l = pv.list.getOrNull(pv.index + d) ?: return@mapNotNull null
            val x = d * pageWidth + off
            if (abs(x) < pageWidth - 0.5f) l to x else null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = true
                shape = windowShape
            }
            .drawBehind { drawRect(Color.Black, blendMode = BlendMode.Clear) }
            .pointerInput(pv) {
                detectTapGestures { p ->
                    if (pv.closing) return@detectTapGestures
                    // A tap on the left or right quarter files the card out that side; a middle tap hides or shows the buttons.
                    val x = p.x / size.width
                    when {
                        pv.search -> setSearch(false)
                        x < 0.25f -> fileTo(-1)
                        x > 0.75f -> fileTo(1)
                        else -> pv.bare = !pv.bare
                    }
                }
            }
            .pointerInput(pv, pageWidth) {
                var dx = 0f
                var start = 0f
                val tracker = VelocityTracker()
                fun edge(d: Float) = (d > 0 && pv.index == 0) || (d < 0 && pv.index == pv.list.lastIndex)
                detectHorizontalDragGestures(
                    onDragStart = {
                        pv.endStack()
                        dx = 0f
                        tracker.resetTracking()
                        // A swipe that catches the pager while it settles carries on from where it is.
                        start = pv.pageOffset.value
                        scope.launch { pv.pageOffset.stop() }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val travel = start + dx
                        val fling = 600.dp.toPx()
                        val dir = when {
                            v < -fling || (v <= fling && travel < -pageWidth * 0.4f) -> 1
                            v > fling || (v >= -fling && travel > pageWidth * 0.4f) -> -1
                            else -> 0
                        }
                        go(if (edge(-dir.toFloat())) 0 else dir, v)
                    },
                    onDragCancel = { go(0) }
                ) { change, d ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    dx += d
                    val travel = start + dx
                    scope.launch { pv.pageOffset.snapTo(if (edge(travel)) travel * 0.22f else travel) }
                }
            }
    ) {
        val visible = pages()
        // While a card is filed over another, the looks are a stack of cards (and the pager rests).
        if (pv.stack.isNotEmpty()) {
            FpStack(
                pv, host.phoneDark, store, pageWidth, pageHeight, barX, barScale,
                barTop = { of -> barY.value + barH / 2 - barHOf(of) / 2 + pv.bareShift.value }
            )
        }
        // Each look's own wallpaper on its page. The whole picture grows out of the band its card showed.
        if (store != null && pv.stack.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val blurPx = pv.wallBlur
                        renderEffect = if (blurPx > 0.5f) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null
                    }
                    .drawBehind {
                        val grow = pv.bounds.value
                        for ((of, dx) in pages()) clipRect(left = dx, right = dx + size.width) {
                            translate(left = dx) {
                                val w = wallpaperOf(of, of.appDark(host.phoneDark))
                                if (w == null) {
                                    // One of the owner's own looks: it is tried on the owner's wallpaper.
                                    drawRect(Color.Black, blendMode = BlendMode.Clear)
                                } else {
                                    // At rest it covers the screen; in its card it was as wide as the card's window.
                                    val cover = max(size.width / store.size.width, size.height / store.size.height)
                                    val restLeft = (size.width - store.size.width * cover) / 2f
                                    val restTop = (size.height - store.size.height * cover) / 2f
                                    val card = store.size.placeIn(pv.from.width, pv.from.height, w.focus)
                                    drawWallpaper(
                                        store, w,
                                        lerp(pv.from.left + card[0], restLeft, grow), lerp(pv.from.top + card[1], restTop, grow), lerp(card[2], cover, grow),
                                        whole = true
                                    )
                                }
                            }
                        }
                    }
            )
        }

        // What else the card's window showed (the look of the week's label and its search page, the check badge),
        // carried in the window as it grows: they keep to its top corners and slide up out of it as it fills the
        // screen, and back down into place as it shrinks into the card.
        if (pv.decor != Decor.None && t < 0.999f) {
            CardDecor(pv.decor, look, host.phoneDark, meshColors[0], rect, t)
        }

        // The search page: its dimming and stars rise from the bottom edge, never faded in. Each look's on its page.
        val reveal = pv.searchReveal.value
        if (reveal > 0f) {
            val top = topEnd + with(density) { 4.dp.toPx() }
            for ((of, dx) in visible) key(of.id) {
                SearchPagePane(
                    of, host.phoneDark, blurAvailable, dx, reveal,
                    top = top,
                    bottom = tbTop - with(density) { 16.dp.toPx() } - noteMax,
                    fieldShift = { at -> (barY.value - top - at - with(density) { 8.dp.toPx() }) * (1f - pv.searchReveal.value) }
                )
            }
        }

        // Controls: solid tonal containers over the wallpaper (nothing in the app can frost it), in the look's theme
        // when it sets one, so a look that switches dark or light shows it at a glance.
        val chromeScheme = if (q.theme == ThemeChoice.Keep) MaterialTheme.colorScheme else phoneSchemes.of(searchDark)
        MaterialTheme(colorScheme = chromeScheme, typography = MaterialTheme.typography) {
            val hide = 1f - pv.chrome.value * (1f - pv.bareHide.value)
            val n = pv.list.size
            val asBefore = pv.context == "yours" && look === state.before
            val name = if (asBefore) "Before" else look.name
            // What TalkBack says as the preview opens or the look changes.
            Box(Modifier.size(1.dp).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = (if (n > 1) "$name, look ${pv.index + 1} of $n." else "$name.") + " Preview ${host.backdrop.where(look)}." +
                    if (look.switchesTheme(host.phoneDark)) " Switches to ${if (searchDark) "dark" else "light"} theme." else ""
            })
            TopRow(
                pv, look, state, lg,
                Modifier
                    .offset { IntOffset(0, topRowTop.roundToInt()) }
                    .graphicsLayer { translationY = -hide * (topRowTop + topRowH + 40.dp.toPx()) }
                    .onSizeChanged { topRowH = it.height.toFloat() },
                onClose = ::close, onSearch = ::setSearch, onSave = { host.save(look) }
            )
            // Labels above the toolbar, each on its own opaque container.
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, (tbTop - 8.dp.toPx() - noteMax).roundToInt()) }
                    .graphicsLayer { translationY = hide * (pageHeight - tbTop + noteMax + 40.dp.toPx()) }
                    .padding(horizontal = 16.dp)
                    .onSizeChanged { if (it.height > noteMax) noteMax = it.height.toFloat() }
                    .heightIn(min = with(density) { noteMax.toDp() }),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (look.switchesTheme(host.phoneDark)) {
                    Chip(if (searchDark) Symbols.DarkMode else Symbols.LightMode, "Switches to ${if (searchDark) "dark" else "light"} theme", chromeScheme.primaryContainer, chromeScheme.onPrimaryContainer)
                }
                if (pv.search) {
                    val lost = q.wallpaper && !blurAvailable && q.blur > 0
                    Chip(null, if (lost) "Sample results · Your phone has blur off, so search dims your wallpaper instead" else "Sample results", chromeScheme.surfaceContainerHigh, chromeScheme.onSurface)
                } else if (state.opens <= 3 && !pv.noHint) {
                    Text(
                        "Tap ${host.backdrop.behind(look)} to hide the buttons. Drag the bar to where it is on your home screen.",
                        style = MaterialTheme.typography.labelMedium, color = chromeScheme.inverseOnSurface, textAlign = TextAlign.Center,
                        modifier = Modifier.background(chromeScheme.inverseSurface, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                if (wallpaper != null && !pv.search) SetWallpaperButton { host.setWallpaper(look) }
            }
            Toolbar(
                pv, name, n, lg, state, pageWidth,
                Modifier
                    .offset { IntOffset(0, tbTop.roundToInt()) }
                    .graphicsLayer { translationY = hide * (pageHeight - tbTop + 40.dp.toPx()) }
                    .onSizeChanged { toolbarH = it.height.toFloat() },
                onPrev = { fileTo(-1) }, onNext = { fileTo(1) }, onApply = ::apply, asBefore = asBefore
            )
        }

        // The bar at its row on the home screen, true size, centered as the launcher centers a widget; each look's on
        // its page. Drawn over the buttons, so nothing ever covers it.
        if (reveal == 0f && pv.stack.isEmpty()) {
            for ((of, dx) in visible) key(of.id) {
                val current = of === look
                val fb = if (current) pv.fromBar else null
                val tr = pv.barTravel.value
                val hk = barHOf(of)
                val restY = barY.value + barH / 2 - hk / 2 + pv.bareShift.value
                val x = if (fb != null) lerp(fb.left, barX, tr) else barX
                val y = if (fb != null) lerp(fb.top, restY, tr) else restY
                val s0 = if (fb != null) fb.width / barW else 1f
                val s = lerp(s0, 1f, tr)
                Box(
                    Modifier
                        .offset { IntOffset((x + dx).roundToInt(), y.roundToInt()) }
                        .graphicsLayer {
                            scaleX = s
                            scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        }
                        .then(if (!current) Modifier else Modifier
                            .semantics {
                                contentDescription = "Search bar. Double-tap to preview the search page."
                                stateDescription = "Row ${state.row + 1} of 6"
                                onClick("Preview the search page") { setSearch(true); true }
                                customActions = listOf(
                                    CustomAccessibilityAction("Move up") { state.row = (state.row - 1).coerceAtLeast(0); true },
                                    CustomAccessibilityAction("Move down") { state.row = (state.row + 1).coerceAtMost(5); true }
                                )
                            }
                            // A tap on the bar opens search, with the app's one vibration on touch down and on the tap,
                            // as every button plays it (the bar also drags, so it can't be a plain button).
                            .pointerInput(pv) {
                                detectTapGestures(onPress = { tick() }) { if (!pv.closing) { tick(); setSearch(true) } }
                            }
                            .pointerInput(rows) {
                                var y0 = 0f
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        dragging = true
                                        // Picked up where it shows, bare or not.
                                        y0 = barY.value + pv.bareShift.value
                                        scope.launch { pv.bareShift.snapTo(0f); barY.snapTo(y0) }
                                    },
                                    onDragEnd = {
                                        val center = barY.value + barH / 2
                                        state.row = rows.indices.minBy { abs(rows[it] - center) }
                                        pv.bare = false
                                        dragging = false
                                        scope.launch { barY.animateTo(rows[state.row] - barH / 2, Springs.fast()) }
                                    },
                                    onDragCancel = { dragging = false }
                                ) { change, d ->
                                    change.consume()
                                    y0 += d
                                    scope.launch { barY.snapTo(y0.coerceIn(rows[0] - barH / 2, rows[5] - barH / 2)) }
                                }
                            })
                ) { GalleryBar(of, host.phoneDark, scale = barScale) }
            }
        }
    }
}

/** One look's search page on the pager, [dx] px to the side: its dimming risen [reveal] of the way, and its sample results. */
@Composable
private fun SearchPagePane(
    look: Look, phoneDark: Boolean, blurAvailable: Boolean, dx: Float, reveal: Float, top: Float, bottom: Float,
    fieldShift: (Float) -> Float
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val layout = remember(context) { GalleryLive.pageLayout(context) }
    val schemes = rememberLookSchemes(look)
    val searchScheme = schemes.of(look.appDark(phoneDark))
    val q = look.search
    val scrim = if (q.wallpaper) overlayScrimColor(searchScheme).copy(alpha = overlayScrimAlpha(q.transparency, q.blur, blurAvailable)) else searchScheme.background
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            clip = true
            shape = WindowShape(Rect(dx, size.height * (1f - reveal), dx + size.width, size.height), 0f)
        }.background(scrim)
    ) {
        if (q.stars) MaterialTheme(colorScheme = searchScheme) {
            Box(Modifier.fillMaxSize().offset { IntOffset(dx.roundToInt(), 0) }) { StarMatrix(animate = true) }
        }
    }
    MaterialTheme(colorScheme = searchScheme, typography = MaterialTheme.typography) {
        Box(
            Modifier
                .offset { IntOffset(dx.roundToInt(), top.roundToInt()) }
                .fillMaxWidth()
                .heightIn(max = with(density) { (bottom - top).coerceAtLeast(0f).toDp() })
                .semantics(mergeDescendants = true) { contentDescription = "Sample results. Nothing here is searched or saved." }
        ) {
            SearchPageContent(look, reveal = { reveal }, fieldShift = fieldShift, layout = layout)
        }
    }
}

/**
 * The card's own decorations while its window grows into the preview or shrinks back: at [rect]'s top corners as the
 * card has them, sliding up out of the window as it opens ([t] from 0 to 1). Never faded.
 */
@Composable
private fun CardDecor(decor: Decor, look: Look, phoneDark: Boolean, wallMain: Color, rect: Rect, t: Float) {
    val density = LocalDensity.current
    val lift = with(density) { 200.dp.toPx() } * t
    val on = decor == Decor.HeroOn
    // As the cards place it: 28 dp at 10 dp in from the corner; on a tile in Yours, 24 dp at 8 dp.
    val small = decor == Decor.SmallBadge
    val pad = with(density) { (if (small) 8.dp else 10.dp).toPx() }
    if (decor == Decor.Badge || small || on) {
        CheckBadge(if (small) 24.dp else 28.dp, Modifier.offset { IntOffset((rect.left + pad).roundToInt(), (rect.top + pad - lift).roundToInt()) })
    }
    if (decor == Decor.Hero || on) {
        val eyebrowMax = with(density) { rect.width.toDp() } - 136.dp
        Box(Modifier.offset { IntOffset(rect.left.roundToInt(), (rect.top - lift).roundToInt()) }) {
            HeroEyebrow(on, eyebrowMax)
        }
        val tileW = with(density) { (88.dp + 12.dp).toPx() }
        Box(Modifier.offset { IntOffset((rect.right - tileW).roundToInt(), (rect.top - lift).roundToInt()) }) {
            SearchTile(look, phoneDark, wallMain, big = true, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** The one control here that changes the phone itself. It only opens the sheet that asks where, and says what happens. */
@Composable
private fun SetWallpaperButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .heightIn(min = 48.dp)
            .bouncyClickable(RoundedCornerShape(24.dp), onClick = onClick)
            .background(scheme.secondaryContainer)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Symbols.Wallpaper, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        Text("Set Wallpaper", style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, bg: Color, fg: Color) {
    Row(
        Modifier.shadow(6.dp, RoundedCornerShape(14.dp)).background(bg, RoundedCornerShape(14.dp)).heightIn(min = 28.dp).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TopRow(
    pv: PreviewController, look: Look, state: GalleryState, lg: Boolean, modifier: Modifier,
    onClose: () -> Unit, onSearch: (Boolean) -> Unit, onSave: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val saved = state.isSaved(look)
    val close = @Composable {
        PreviewIconButton(Symbols.ArrowBack, "Back", Modifier.shadow(8.dp, RoundedCornerShape(24.dp)), tint = scheme.onSecondaryContainer, container = scheme.secondaryContainer, size = 48.dp, onClick = onClose)
    }
    val save = @Composable {
        PreviewIconButton(
            if (saved) Symbols.BookmarkFilled else Symbols.Bookmark, if (saved) "Saved to your looks" else "Save to your looks",
            Modifier.shadow(8.dp, RoundedCornerShape(24.dp)), tint = if (saved) scheme.primary else scheme.onSurface, container = scheme.surfaceContainerHigh, size = 48.dp, onClick = onSave
        )
    }
    val segments = @Composable { m: Modifier ->
        // One group on one container, as the toolbar and the save button: the selected half is the tonal pill with a
        // check; the other is plain, only its label on the group's container.
        Row(
            m.semantics { contentDescription = "Preview" }
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Segment("Home Screen", !pv.search, first = true, lg, Modifier.weight(1f)) { onSearch(false) }
            Segment("Search", pv.search, first = false, lg, Modifier.weight(1f)) { onSearch(true) }
        }
    }
    if (lg) {
        Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { close(); Spacer(Modifier.weight(1f)); save() }
            segments(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            close()
            segments(Modifier.weight(1f).padding(horizontal = 4.dp))
            save()
        }
    }
}

/**
 * One half of the connected button group: selected, the secondary container with a check and fully round ends;
 * unselected, plain (its label only), its inner corners at 8 dp. The corners morph and the check grows in on springs.
 */
@Composable
private fun Segment(label: String, selected: Boolean, first: Boolean, lg: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val inner by animateDpAsState(if (selected) 24.dp else 8.dp, Springs.morph(), label = "segment")
    val check by animateDpAsState(if (selected) 24.dp else 0.dp, Springs.morph(), label = "segmentCheck")
    val bg by animateColorAsState(if (selected) scheme.secondaryContainer else scheme.secondaryContainer.copy(alpha = 0f), Springs.effects(), label = "segmentBg")
    val fg by animateColorAsState(if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, Springs.effects(), label = "segmentFg")
    val shape = if (first) RoundedCornerShape(24.dp, inner, inner, 24.dp) else RoundedCornerShape(inner, 24.dp, 24.dp, inner)
    Row(
        modifier
            .heightIn(min = if (lg) 48.dp else 44.dp)
            .background(bg, shape)
            .clip(shape)
            .bouncyClickable(shape, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (check > 0.dp) Box(Modifier.width(check)) {
            Icon(Symbols.Check, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center, softWrap = lg)
    }
}

@Composable
private fun Toolbar(
    pv: PreviewController, name: String, n: Int, lg: Boolean, state: GalleryState, pageWidth: Float, modifier: Modifier,
    onPrev: () -> Unit, onNext: () -> Unit, onApply: () -> Unit, asBefore: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val look = pv.look ?: return
    val on = state.isOn(look)
    val shape = RoundedCornerShape(if (lg) 28.dp else 32.dp)
    // The name rides the pager with the wallpaper and the bar: each look's name on its page, as far across this title
    // as its wallpaper is across the screen. Just one look: no count, no arrows.
    val title = @Composable { m: Modifier ->
        Box(m.clipToBounds().semantics(mergeDescendants = true) { }, contentAlignment = Alignment.Center) {
            var w by remember { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxWidth().onSizeChanged { w = it.width.toFloat() })
            for (d in -1..1) {
                val l = pv.list.getOrNull(pv.index + d) ?: continue
                val shown = d == 0
                val label = if (shown) name else if (pv.context == "yours" && l === state.before) "Before" else l.name
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val x = (d * pageWidth + pv.pageOffset.value) / pageWidth.coerceAtLeast(1f)
                            translationX = x * w
                        }
                        .then(if (shown) Modifier else Modifier.clearAndSetSemantics { }),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, textAlign = TextAlign.Center, maxLines = if (lg) 2 else 1)
                    if (n > 1) Text("${pv.index + d + 1} of $n", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
    val apply = @Composable { m: Modifier, group: Boolean -> ApplyButton(pv.applied, on, asBefore, m, group, onApply) }
    if (lg) Box(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .matteCard(shape, container = scheme.surfaceContainerHigh, lit = true, border = false)
                .heightIn(min = 64.dp)
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (n > 1) PreviewIconButton(Symbols.ChevronLeft, "Previous look", tint = scheme.onSurface, enabled = pv.index > 0, onClick = onPrev)
                else Spacer(Modifier.width(8.dp))
                title(Modifier.weight(1f))
                if (n > 1) PreviewIconButton(Symbols.ChevronRight, "Next look", tint = scheme.onSurface, enabled = pv.index < n - 1, onClick = onNext)
            }
            apply(Modifier.fillMaxWidth().padding(top = 4.dp), false)
        }
    } else Row(
        // The tools and Apply as one connected group, as the page's button pairs are: full corners outside, small ones
        // where they meet.
        modifier.fillMaxWidth().padding(horizontal = 16.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .matteCard(RoundedCornerShape(30.dp, 12.dp, 12.dp, 30.dp), container = scheme.surfaceContainerHigh, lit = true, border = false)
                .heightIn(min = 60.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (n > 1) PreviewIconButton(Symbols.ChevronLeft, "Previous look", tint = scheme.onSurface, enabled = pv.index > 0, onClick = onPrev)
            else Spacer(Modifier.width(8.dp))
            title(Modifier.weight(1f))
            if (n > 1) PreviewIconButton(Symbols.ChevronRight, "Next look", tint = scheme.onSurface, enabled = pv.index < n - 1, onClick = onNext)
        }
        apply(Modifier.fillMaxHeight(), true)
    }
}

/**
 * Apply: on tap its label becomes a check and "On". In the toolbar's group ([group]) its outer corners are full and the
 * ones next to the tools small, tightening to 18 dp while pressed; on its own, it goes from fully round to 16 dp.
 */
@Composable
private fun ApplyButton(applied: Boolean, on: Boolean, asBefore: Boolean, modifier: Modifier, group: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (applied) 16.dp else 24.dp, Springs.morph(), label = "apply")
    val tonal = on && !applied
    val label = when {
        applied -> "On"
        on -> "Customize"
        asBefore -> "Restore"
        else -> "Apply"
    }
    val shape = if (group) RoundedCornerShape(12.dp, 30.dp, 30.dp, 12.dp) else RoundedCornerShape(corner)
    Box(
        modifier
            .heightIn(min = if (group) 60.dp else 48.dp)
            .widthIn(min = 88.dp)
            .bouncyClickable(shape) { onClick() }
            .background(if (tonal) scheme.secondaryContainer else scheme.primary)
            .padding(start = if (group) 18.dp else 14.dp, end = if (group) 22.dp else 14.dp)            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (applied || label == "Apply") Icon(Symbols.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (tonal) scheme.onSecondaryContainer else scheme.onPrimary)
        }
    }
}


/** A full-preview card wholly off the screen: its angle (degrees), and its pivot (mockup px on a 360 x 780 screen). */
private const val FP_SWING = 38f
private const val FP_PIVOT_X = 70f
private const val FP_PIVOT_Y = 1480f

/** The looks as swatches while one turns over another, each on its pivot below the screen. */
@Composable
private fun FpStack(
    pv: PreviewController, phoneDark: Boolean, store: WallpaperStore?, w: Float, h: Float, barX: Float, barScale: Float,
    barTop: (Look) -> Float
) {
    val density = LocalDensity.current
    val k = w / 360f
    val cardShape = RoundedCornerShape(with(density) { (36f * k).toDp() })
    for (c in pv.stack) key(c.seq) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                pv.fpFrame.intValue
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(FP_PIVOT_X / 360f, FP_PIVOT_Y / 780f)
                rotationZ = c.A.x
            }
        ) {
            // Its shadow comes out from under its left edge as it lifts off, and goes back as it lands.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        pv.fpFrame.intValue
                        val m = (kotlin.math.abs(c.A.x) / 6f).coerceIn(0f, 1f)
                        translationX = (-25f - 24f * m) * k
                    }
                    .bakedBlur(
                        Color.Black.copy(alpha = 0.6f), with(density) { (48f * k).toDp() }, 4f,
                        bounds = { Rect(-100f * k, -100f * k, size.width + 100f * k, size.height + 100f * k) }
                    ) {
                        androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(RoundRect(0.07f * size.width, 0.08f * size.height, 0.93f * size.width, 1.01f * size.height, CornerRadius(44f * k)))
                        }
                    }
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .drawBehind {
                        val wp = if (store != null) wallpaperOf(c.look, c.look.appDark(phoneDark)) else null
                        if (store == null || wp == null) drawRect(Color.Black, blendMode = BlendMode.Clear)
                        else {
                            val cover = max(size.width / store.size.width, size.height / store.size.height)
                            drawWallpaper(
                                store, wp, (size.width - store.size.width * cover) / 2f, (size.height - store.size.height * cover) / 2f, cover,
                                whole = true
                            )
                        }
                    }
            ) {
                Box(Modifier.offset { IntOffset(barX.roundToInt(), barTop(c.look).roundToInt()) }) {
                    GalleryBar(c.look, phoneDark, scale = barScale)
                }
            }
        }
    }

}
