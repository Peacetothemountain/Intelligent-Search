package com.pixel.intelligentsearch.feature.search.today.cards.grid

import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.ui.icons.filled.Lock
import com.pixel.intelligentsearch.core.ui.icons.outlined.Delete
import com.pixel.intelligentsearch.core.ui.icons.outlined.Interests
import com.pixel.intelligentsearch.core.ui.icons.outlined.Tune
import com.pixel.intelligentsearch.core.ui.icons.outlined.VisibilityOff
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.search.today.cards.CardForm
import com.pixel.intelligentsearch.feature.search.today.cards.CardFrame
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardMode
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Discover's edit mode: what is selected, the card being moved or pinched, which sheet is open, and every edit, each
 * with its haptic and what TalkBack hears. The page (DiscoverGrid) and the chrome over it share one of these.
 *
 * Editing is freeform: a card follows the finger anywhere and is pinched to any size, sideways for width, both ways for
 * a box; a full card pinched smaller offers its shapes at once. Cards it is moved over make room by moving down.
 *
 * NG Designs
 */

/** The sheets edit mode opens from the bottom of the screen. */
internal enum class EditSheet {
    /** Add a card from an app's widget or a website, or a hidden part back. */
    ADD,

    /** Every edit of the selected card as a plain button. */
    ACTIONS,

    /** The Quick controls card's controls, to switch on or off: its own tiles take no touches while editing. */
    CONTROLS
}

/**
 * Edit mode's haptics, the app's two: [TICK] as a gesture does something (a card lifted, lined up, at a step, let go),
 * [REJECT] as an edit is refused or a card reaches its limit.
 */
internal enum class EditHaptic { TICK, REJECT }

/** One message, shown or spoken once even when its words are the same as the last one's, with an [undo] if it has one. */
internal class EditMessage(val text: String, val undo: (() -> Unit)? = null)

/**
 * Where one of the app's own parts stands when it has nothing to show: [note] ("Needs calendar access", "Shows while
 * something is playing"), and, when a tap can change that, what Add to Discover's tile says ([action], "Tap to
 * allow"), what the empty card's own button says ([button], "Allow calendar access") and what both do ([fix]: turn the
 * part on, or ask for what it reads).
 */
internal class PartStatus(
    val note: String,
    val action: String? = null,
    val button: String? = null,
    val fix: (() -> Unit)? = null,
    /** What the part waits for the user to allow, when that is what it waits for. */
    val access: CardAccess? = null
)

/** What one of the page's own parts can wait for the user to allow, and what allowing it [shows] on the page. */
internal enum class CardAccess(val shows: String) {
    CALENDAR("your next events"),
    NOTIFICATIONS("timers, what's playing and missed messages"),
    LOCATION("the weather where you are")
}

/** One edit of a card: its [label], [icon], whether it can be done now ([enabled]), and doing it ([run]). */
internal class CardAction(val label: String, val icon: ImageVector, val enabled: Boolean, val run: () -> Unit)

/** Settling: quick out, a touch of overshoot. */
internal val SettleEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.05f)

/** How a pinch began: the card's place then, how far apart the fingers were across and down, and between them. */
internal class PinchStart(val rect: Rect, val across: Float, val down: Float, val distance: Float, val middle: Offset) {
    /** Which ways the pinch sizes: fingers set side by side size the width only, one above the other the height only. */
    val widthOnly: Boolean get() = down < across * AXIS_LOCK
    val heightOnly: Boolean get() = across < down * AXIS_LOCK

    private companion object {
        /** How much more one way than the other the fingers must be set for the pinch to size that way only. */
        const val AXIS_LOCK = 0.55f
    }
}

/** The grips a selected card has inside its bottom edge: one for its height alone, one at its corner for both. */
internal enum class Grip { HEIGHT, CORNER }

/** How a grip drag began: the card's place then, and where the finger was (page pixels). */
internal class GripStart(val grip: Grip, val rect: Rect, val finger: Offset)

/**
 * The card being moved or pinched: its place now ([rect], the page's pixels, the card plus half the gap all round),
 * following the fingers, and the lines it lines up with ([guides], page x).
 */
@Stable
internal class ActiveCard(val id: String, start: Rect, internal var grab: Offset, internal val reference: Map<String, IntRect>, autoHeight: Boolean) {
    /** Whether a full card is as tall as its content (no height of its own), as it is being pinched. */
    var autoHeight by mutableStateOf(autoHeight)
    var rect by mutableStateOf(start)
    var guides by mutableStateOf<List<Int>>(emptyList())
    var pinching by mutableStateOf(false)

    /** The height the card measured at its live width (a full design is as tall as its content). Layout only. */
    internal var measured: Int = start.height.roundToInt()
    internal var atLeast = false
    internal var atFloor = false
    internal var snappedTo: List<Int> = emptyList()
    internal var pinch: PinchStart? = null
    internal var grip: GripStart? = null
}

/**
 * Discover's edit mode, shared by [DiscoverGrid] and [DiscoverEditChrome]: whether the page is [editing], the
 * [selectedId] card, the open [sheet], the card being moved or pinched ([active]), and the card whose shapes are
 * offered ([strip]). The grid keeps it up to date with the layout and where the cards are; every edit goes through
 * [commit] to the page's onLayoutChange.
 */
@Stable
internal class DiscoverEditState(
    private val view: View,
    private val vibration: State<Boolean>,
    private val animations: State<Boolean>,
    internal val scope: CoroutineScope
) {
    /** Whether the page is being edited. */
    var editing by mutableStateOf(false)
        private set

    /** The selected card's id, framed with its toolbar. */
    var selectedId by mutableStateOf<String?>(null)
        internal set

    /** The open sheet, if any. */
    var sheet by mutableStateOf<EditSheet?>(null)
        internal set

    /** The card whose shapes are offered over it. */
    var strip by mutableStateOf<String?>(null)
        internal set

    internal var active by mutableStateOf<ActiveCard?>(null)
    internal var message by mutableStateOf<EditMessage?>(null)
    internal var announcement by mutableStateOf<EditMessage?>(null)

    /** A card to bring into view once it is laid out (one just added). */
    internal var reveal by mutableStateOf<String?>(null)

    /** A card just let go, and where it was drawn, so it glides from there to its place. */
    internal var landed: Pair<String, Offset>? = null

    // What the grid and chrome keep current, read by the gestures and edits (never drawn from).
    internal var layout: DiscoverLayout = DiscoverLayout.DEFAULT
    internal var onLayoutChange: (DiscoverLayout) -> Unit = {}
    internal var shown: (CardSpec) -> Boolean = { true }

    /**
     * Where one of the app's own parts stands when it is switched off or has nothing to show, and what a tap can do
     * about it; the page keeps it current, so the open sheet follows a grant or a switch at once.
     */
    internal var partStatus by mutableStateOf<(CardKind) -> PartStatus>({ PartStatus("Has nothing to show right now") })
    internal var label: (CardSpec) -> String = { it.kind.label }

    /** The least side (dp) a card's shape reads at. */
    internal var minShape: (CardSpec) -> Float = { 96f }
    internal var onRemoveCustom: (CardSpec) -> Unit = {}
    internal var density: Density = Density(1f)

    /** The page's width of places and the space between cards, in pixels. */
    internal var page: Int = 0
    internal var gap: Int = 0

    /** Where each card on the page is, its place (page pixels), as last laid out, and the order they were laid in. */
    internal var slots by mutableStateOf<Map<String, IntRect>>(emptyMap())
    internal var arranged: Arranged? = null

    /** The places the page was last laid out from, and how near a card sticks to the one above (pixels). */
    internal var flowCards: List<FlowCard> = emptyList()
    internal var stick: Int = 0

    /** Each full card's place height when it is as tall as its content, as last laid out, so a pinch can snap back to it. */
    internal val naturalHeights = HashMap<String, Int>()
    internal var gridCoords: LayoutCoordinates? = null
    internal var chromeCoords: LayoutCoordinates? = null
    internal var topBarBottom: Float = 0f
    internal var scroll: ScrollState? = null

    internal val animate: Boolean get() = animations.value

    private fun px(dp: Float): Float = with(density) { dp.dp.toPx() }
    private fun dp(px: Float): Float = with(density) { px.toDp().value }

    // ---------------------------------------------------------------------------------------------------------------
    // Feedback

    /**
     * Plays the app's one haptic for [EditHaptic.TICK], or its cancel half for [EditHaptic.REJECT], when the app's
     * Vibration setting is on. Played for what a gesture does and for an edit refused; a button's tap is the button's.
     */
    internal fun haptic(kind: EditHaptic) {
        if (!vibration.value) return
        val engine = runCatching { PixelHapticEngine.get(view.context) }.getOrNull() ?: return
        if (kind == EditHaptic.REJECT) engine.performPredictiveBackCancelHaptic(view) else engine.performPredictiveBackHaptic(view)
    }

    /** Has TalkBack say [text]. */
    internal fun announce(text: String) {
        announcement = EditMessage(text)
    }

    /** Shows [text] briefly at the bottom of the page, with an Undo button when [undo] is given, and has TalkBack say it. */
    internal fun say(text: String, undo: (() -> Unit)? = null) {
        message = EditMessage(text, undo)
    }

    /**
     * How long a message stays for one that would stay [ms]: longer for people who have asked Android for more time to
     * read and act (Accessibility, Time to take action). [action] for a message with a button.
     */
    internal var timeout: (ms: Long, action: Boolean) -> Long = { ms, _ -> ms }

    /** A removed app or website card, let go (its widget unbound, what was read deleted) once its Undo has gone. */
    private var pendingRemoval: Pair<CardSpec, Job>? = null

    /** The widget of a removed app card whose Undo is still showing, kept bound so Undo can bring the card back. */
    internal val pendingWidget: Int? get() = pendingRemoval?.first?.widgetId?.takeIf { it != 0 }

    /** Lets go of a removed card now rather than when its Undo goes: the page is closing, or another card was removed. */
    internal fun flushRemoval() {
        val (spec, job) = pendingRemoval ?: return
        pendingRemoval = null
        job.cancel()
        onRemoveCustom(spec)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Edit mode

    /** Starts editing with [id] selected. */
    fun enter(id: String?) {
        if (!editing) haptic(EditHaptic.TICK)
        editing = true
        selectedId = id
        announce("Editing Discover. Drag a card to move it, pinch it to resize it.")
    }

    /** Stops editing: the selection, any sheet and any card held let go. */
    fun exit() {
        if (!editing) return
        if (active != null) end()
        editing = false
        selectedId = null
        strip = null
        sheet = null
        announce("Done editing Discover")
    }

    /** Selects [id] (or nothing); the shapes offered for another card are put away. */
    internal fun select(id: String?) {
        if (id != strip) strip = null
        selectedId = id
    }

    /** Opens [which] (or closes the open sheet). */
    internal fun open(which: EditSheet?) {
        sheet = which
    }

    /** Offers [id]'s shapes over it, or puts them away. */
    internal fun toggleStrip(id: String) {
        strip = if (strip == id) null else id
    }

    /** Makes [new] the page's layout. */
    internal fun commit(new: DiscoverLayout) {
        layout = new
        onLayoutChange(new)
    }

    internal fun spec(id: String): CardSpec? = layout.cards.firstOrNull { it.id == id }

    /** What TalkBack hears for [spec]: its label, and while editing its form and how to change it. */
    internal fun describe(spec: CardSpec): String {
        val base = label(spec)
        if (!editing) return base
        val form = if (spec.mode == CardMode.SHAPED) ", ${shapeName(spec.shape)} shape" else ""
        val across = (spec.frame.width * 100).roundToInt()
        return "$base$form, $across percent wide. Drag to move, pinch to resize, or use the actions."
    }

    /** The card under [at] (page pixels): the one being moved first, then the topmost place that holds it. */
    internal fun cardAt(at: Offset): String? {
        active?.let { if (it.rect.contains(at)) return it.id }
        return slots.entries.lastOrNull { (_, rect) ->
            at.x >= rect.left && at.x < rect.right && at.y >= rect.top && at.y < rect.bottom
        }?.key
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Sizes

    /** The narrowest [spec]'s place can be in [mode], in pixels: its design's least width, plus the gap. */
    internal fun leastWidth(spec: CardSpec, mode: CardMode = spec.mode): Float = gap + px(
        when (mode) {
            CardMode.NATURAL -> maxOf(NARROWEST_CARD_DP, spec.kind.minCardDp * SMALLEST_DESIGN_SCALE)
            CardMode.SHAPED -> minShape(spec)
            CardMode.BOX -> maxOf(spec.kind.minCardDp.toFloat(), widgetLeastDp(spec)?.first ?: 0f)
        }
    )

    /**
     * The least height (dp) [spec] can be given: an app's widget as short as its app lets a launcher make it (one short
     * row at least); a website's box keeps room for its picture and words.
     */
    private fun leastHeightDp(spec: CardSpec): Float = when {
        spec.mode == CardMode.BOX && spec.kind == CardKind.APP -> maxOf(MIN_HEIGHT_DP, widgetLeastDp(spec)?.second ?: 0f)
        spec.mode == CardMode.BOX -> BOX_MIN_DP
        else -> MIN_HEIGHT_DP
    }

    /** Each live widget's least width and height (dp), as looked up. */
    private val widgetLeast = HashMap<Int, Pair<Float, Float>>()

    /**
     * The least size (dp) the live widget on app card [spec] takes, as its app declares it: the size a launcher won't
     * resize it under (its least resize size where it can be resized that way, else its least size). Smaller, a widget
     * cuts off its own content. Null for other cards, or a widget Android no longer has.
     */
    private fun widgetLeastDp(spec: CardSpec): Pair<Float, Float>? {
        if (spec.kind != CardKind.APP || spec.mode != CardMode.BOX || spec.widgetId == 0) return null
        return widgetLeast.getOrPut(spec.widgetId) {
            val info = runCatching { AppWidgetManager.getInstance(view.context).getAppWidgetInfo(spec.widgetId) }.getOrNull() ?: return null
            val density = view.resources.displayMetrics.density
            val across = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 && info.minResizeWidth > 0
            val down = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0 && info.minResizeHeight > 0
            val w = if (across) info.minResizeWidth else info.minWidth
            val h = if (down) info.minResizeHeight else info.minHeight
            (w / density) to (h / density)
        }
    }

    /**
     * Gives every live widget at least the size its app needs, for a widget placed smaller before that was kept to
     * (it would cut off its own content). One commit, and only when one is too small. Needs the page's width.
     */
    internal fun fitWidgets() {
        val page = page.toFloat().takeIf { it > 0f } ?: return
        var changed = false
        val cards = layout.cards.map { card ->
            val least = widgetLeastDp(card) ?: return@map card
            val width = max(card.frame.width, (leastWidth(card) / page).coerceAtMost(1f))
            val height = if (card.frame.height > 0f) max(card.frame.height, leastHeightDp(card)) else card.frame.height
            if (width == card.frame.width && height == card.frame.height || least.first <= 0f && least.second <= 0f) card
            else {
                changed = true
                card.copy(frame = card.frame.copy(x = card.frame.x.coerceIn(0f, 1f - width), width = width, height = height))
            }
        }
        if (changed) commit(layout.copy(cards = cards))
    }

    /** A card's least and a box's most height, in pixels of its place. */
    private fun leastHeight(spec: CardSpec) = gap + px(leastHeightDp(spec))
    private val boxMost get() = gap + px(BOX_MAX_DP)

    // ---------------------------------------------------------------------------------------------------------------
    // Moving and pinching

    /** Starts moving [id] under the finger at [finger] (page pixels). */
    internal fun beginMove(id: String, finger: Offset) {
        val current = active
        if (current != null && current.id == id) {
            current.grab = finger - current.rect.topLeft
            current.pinch = null
            current.pinching = false
            return
        }
        val rect = slots[id]?.toRect() ?: return
        select(id)
        active = ActiveCard(id, rect, finger - rect.topLeft, slots, spec(id)?.frame?.height?.let { it <= 0f } ?: true)
        haptic(EditHaptic.TICK)
    }

    /** Moves the card being moved so the finger, now at [finger], holds it where it took it. */
    internal fun moveTo(finger: Offset) {
        val card = active ?: return
        if (card.pinch != null || card.grip != null) return
        val width = card.rect.width
        val raw = finger.x - card.grab.x
        val snapped = snapLeft(raw.roundToInt(), width.roundToInt(), page, othersThan(card.id), px(SNAP_DP).roundToInt())
        val left = snapped.value.toFloat().coerceIn(0f, max(0f, page - width))
        val top = finger.y - card.grab.y
        lined(card, snapped.guides)
        card.rect = Rect(left, top, left + width, top + card.rect.height)
    }

    /** Starts pinching [id] with fingers at [a] and [b] (page pixels). */
    internal fun beginPinch(id: String, a: Offset, b: Offset) {
        var card = active
        if (card == null || card.id != id) {
            val rect = slots[id]?.toRect() ?: return
            select(id)
            card = ActiveCard(id, rect, Offset.Zero, slots, spec(id)?.frame?.height?.let { it <= 0f } ?: true)
            active = card
            haptic(EditHaptic.TICK)
        }
        card.pinch = PinchStart(card.rect, abs(a.x - b.x), abs(a.y - b.y), (a - b).getDistance(), (a + b) / 2f)
        card.pinching = true
        card.atLeast = false
    }

    /** Resizes the card being pinched for fingers now at [a] and [b]; the middle between them moves it too. */
    internal fun pinchTo(a: Offset, b: Offset) {
        val card = active ?: return
        val start = card.pinch ?: return
        val spec = spec(card.id) ?: return
        val soft = px(PINCH_SOFTEN_DP)
        val sx = if (start.heightOnly) 1f else (abs(a.x - b.x) + soft) / (start.across + soft)
        val sy = if (start.widthOnly) 1f else (abs(a.y - b.y) + soft) / (start.down + soft)
        val middle = (a + b) / 2f - start.middle
        // A card already narrower than its least (the text size went up since) is never made wider by sizing it.
        val least = min(leastWidth(spec), start.rect.width)
        val floor = leastHeight(spec)
        val reach = px(SNAP_DP).roundToInt()
        val others = othersThan(card.id)
        val page = page.toFloat()
        var width: Float
        var height: Float
        when (spec.mode) {
            CardMode.NATURAL -> {
                val wanted = start.rect.width * sx
                width = wanted.coerceIn(least, page)
                // Taller or shorter as the fingers spread or close up and down; near its content's own height it snaps
                // back to fitting it exactly. A pinch that is all sideways leaves a card that fits its content fitting it.
                val natural = naturalHeights[card.id]?.toFloat()
                val wasAuto = spec.frame.height <= 0f
                val tall = (start.rect.height * sy).coerceIn(floor, gap + px(MAX_HEIGHT_DP))
                atFloor(card, start.rect.height * sy < floor)
                val auto = (wasAuto && (start.widthOnly || abs(sy - 1f) < 0.04f)) || (natural != null && abs(tall - natural) < px(SNAP_HEIGHT_DP))
                if (auto && !card.autoHeight && !wasAuto) haptic(EditHaptic.TICK)
                card.autoHeight = auto
                height = if (auto) card.measured.toFloat() else tall
                // Pinched narrower, a full card offers its shapes at once; one that has none says it's as narrow as it goes.
                offerShapes(card, spec, sx < SHRINK_OFFERS)
                atLeast(card, wanted < least, if (spec.kind.shaped) EditHaptic.TICK else EditHaptic.REJECT)
            }
            CardMode.SHAPED -> {
                val scale = (a - b).getDistance() / start.distance.coerceAtLeast(1f)
                val wanted = start.rect.width * scale
                width = wanted.coerceIn(least, page)
                height = width
                atLeast(card, wanted < least, EditHaptic.REJECT)
            }
            CardMode.BOX -> {
                val wanted = start.rect.width * sx
                width = wanted.coerceIn(least, page)
                height = (start.rect.height * sy).coerceIn(floor, boxMost)
                // An app's or a website's full card offers its shapes as the page's own cards do.
                offerShapes(card, spec, sx < SHRINK_OFFERS)
                atLeast(card, wanted < least, EditHaptic.TICK)
                atFloor(card, start.rect.height * sy < floor)
            }
        }
        width = snapWidth(width.roundToInt(), page.roundToInt(), others, reach, least.roundToInt()).toFloat().coerceAtMost(page)
        if (spec.mode == CardMode.SHAPED) height = width
        val centerX = start.rect.center.x + middle.x
        val snapped = snapLeft((centerX - width / 2f).roundToInt(), width.roundToInt(), page.roundToInt(), others, reach)
        val left = snapped.value.toFloat().coerceIn(0f, max(0f, page - width))
        val top = if (spec.mode == CardMode.NATURAL) start.rect.top + middle.y else start.rect.center.y + middle.y - height / 2f
        lined(card, snapped.guides)
        card.rect = Rect(left, top, left + width, top + height)
    }

    /** Where the selected card's grips are (page pixels), as last laid out, so a press on one isn't taken as a move. */
    internal var grips: Map<Grip, Rect> = emptyMap()

    /** Starts sizing [id] by its [grip], the finger at [finger] (page pixels). */
    internal fun beginGrip(id: String, grip: Grip, finger: Offset) {
        // A card held by a move or a pinch stays theirs: a pinch's second finger can come down on a grip.
        if (active != null) return
        val rect = slots[id]?.toRect() ?: return
        select(id)
        val card = ActiveCard(id, rect, Offset.Zero, slots, spec(id)?.frame?.height?.let { it <= 0f } ?: true)
        card.grip = GripStart(grip, rect, finger)
        active = card
        haptic(EditHaptic.TICK)
    }

    /**
     * Sizes the card by the grip held, the finger now at [finger]: its top and left edge stay; the height grip changes
     * the height only, the corner grip the width and height together (a shape stays square). A full card near its
     * content's height snaps back to fitting it, and one made narrower offers its shapes.
     */
    internal fun gripTo(finger: Offset) {
        val card = active ?: return
        val start = card.grip ?: return
        val spec = spec(card.id) ?: return
        val d = finger - start.finger
        val page = page.toFloat()
        val left = start.rect.left
        val least = min(leastWidth(spec), start.rect.width)
        val floor = leastHeight(spec)
        val corner = start.grip == Grip.CORNER
        val wanted = if (corner) start.rect.width + d.x else start.rect.width
        var width = wanted.coerceIn(least.coerceAtMost(page - left), page - left)
        var height: Float
        when (spec.mode) {
            CardMode.NATURAL -> {
                val natural = naturalHeights[card.id]?.toFloat()
                val tall = (start.rect.height + d.y).coerceIn(floor, gap + px(MAX_HEIGHT_DP))
                atFloor(card, start.rect.height + d.y < floor)
                // A card that fits its content goes on fitting it while the grip moves sideways only (its words wrap
                // anew at the new width), as a sideways pinch leaves it; and any card snaps back to fitting its content
                // when its height is brought near it.
                val wasAuto = spec.frame.height <= 0f
                val auto = (wasAuto && abs(d.y) < px(SNAP_HEIGHT_DP)) ||
                    (natural != null && abs(tall - natural) < px(SNAP_HEIGHT_DP) && abs(width - start.rect.width) < 1f)
                if (auto && !card.autoHeight) haptic(EditHaptic.TICK)
                card.autoHeight = auto
                height = if (auto) card.measured.toFloat() else tall
                offerShapes(card, spec, corner && width < start.rect.width * SHRINK_OFFERS)
            }
            CardMode.SHAPED -> {
                width = (start.rect.width + (d.x + d.y) / 2f).coerceIn(least.coerceAtMost(page - left), page - left)
                height = width
            }
            CardMode.BOX -> {
                height = (start.rect.height + d.y).coerceIn(floor, boxMost)
                atFloor(card, start.rect.height + d.y < floor)
                offerShapes(card, spec, corner && width < start.rect.width * SHRINK_OFFERS)
            }
        }
        atLeast(card, wanted < least, EditHaptic.REJECT)
        if (corner && spec.mode != CardMode.SHAPED) {
            val reach = px(SNAP_DP).roundToInt()
            width = snapWidth(width.roundToInt(), page.roundToInt(), othersThan(card.id), reach, least.roundToInt(), most = (page - left).roundToInt()).toFloat()
        }
        if (spec.mode == CardMode.SHAPED) height = width
        card.rect = Rect(left, start.rect.top, left + width, start.rect.top + height)
    }

    /** A full card made narrower ([shrunk]) offers its shapes at once, with a tick as they come up. */
    private fun offerShapes(card: ActiveCard, spec: CardSpec, shrunk: Boolean) {
        if (shrunk && spec.kind.shaped && strip != card.id) {
            strip = card.id
            haptic(EditHaptic.TICK)
        }
    }

    /** A card went past its least height ([below]): one haptic as it gets there, so the grip doesn't just go dead. */
    private fun atFloor(card: ActiveCard, below: Boolean) {
        if (below && !card.atFloor) haptic(EditHaptic.REJECT)
        card.atFloor = below
    }

    /** A pinch went past the least size ([below]): one [kind] haptic as it gets there. */
    private fun atLeast(card: ActiveCard, below: Boolean, kind: EditHaptic) {
        if (below && !card.atLeast) haptic(kind)
        card.atLeast = below
    }

    /** The card lines up with [guides] now: a tick as it first lines up with new ones. */
    private fun lined(card: ActiveCard, guides: List<Int>) {
        if (guides.isNotEmpty() && guides != card.snappedTo) haptic(EditHaptic.TICK)
        card.snappedTo = guides
        card.guides = guides
    }

    /** Lets the card being moved or pinched go where it would land, and keeps the page that way. */
    internal fun end() {
        val card = active ?: return
        active = null
        val spec = spec(card.id)
        // Where the fingers left it, not where the last frame drew it: a finger can move after that frame, and every
        // move made between frames would otherwise be lost.
        val arranged = if (flowCards.any { it.id == card.id } && spec != null) {
            val live = card.rect
            val height = if (spec.mode == CardMode.NATURAL && card.autoHeight) card.measured else live.height.roundToInt()
            val cards = flowCards.map { if (it.id == card.id) FlowCard(it.id, live.left.roundToInt(), live.width.roundToInt(), height) else it }
            arrange(cards, card.id, live.top.roundToInt(), stick, card.reference)
        } else arranged
        val rect = arranged?.rects?.get(card.id)
        if (arranged == null || spec == null || rect == null || page <= 0) return
        landed = card.id to card.rect.topLeft
        val frame = CardFrame(
            x = rect.left.toFloat() / page,
            width = rect.width.toFloat() / page,
            height = when (spec.mode) {
                CardMode.BOX -> dp((rect.height - gap).toFloat())
                CardMode.NATURAL -> if (card.autoHeight) 0f else dp((rect.height - gap).toFloat())
                CardMode.SHAPED -> spec.frame.height
            },
            gap = dp(arranged.activeGap.toFloat())
        )
        val moved = spec.copy(frame = frame)
        val rest = layout.cards.filter { it.id != card.id }
        val after = arranged.order.indexOf(card.id).let { arranged.order.getOrNull(it + 1) }
        val at = after?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: rest.size
        val cards = ArrayList(rest).apply { add(at, moved) }
        commit(layout.copy(cards = sortedDown(cards) { arranged.rects[it.id]?.top }))
        haptic(EditHaptic.TICK)
        announce("${label(spec)}, ${(frame.width * 100).roundToInt()} percent wide")
    }

    /** Lets the card being moved go back where it was. */
    internal fun cancel() {
        active = null
    }

    /** Moves the card being moved [by] pixels down the page, as the page scrolls under the finger. */
    internal fun shift(by: Float) {
        val card = active ?: return
        card.rect = card.rect.translate(0f, by)
        card.pinch?.let { card.pinch = PinchStart(it.rect.translate(0f, by), it.across, it.down, it.distance, it.middle + Offset(0f, by)) }
    }

    private fun othersThan(id: String): List<IntRect> = slots.filterKeys { it != id }.values.toList()

    // ---------------------------------------------------------------------------------------------------------------
    // Edits

    /** Changes [id]'s frame by [change], keeping it on the page. */
    private fun reframe(id: String, change: (CardSpec) -> CardSpec) {
        commit(layout.copy(cards = layout.cards.map { if (it.id == id) change(it) else it }))
    }

    /** Each card's size as a full card, kept while its shapes are tried, so Full card puts it back as it was. */
    private val fullFrames = HashMap<String, CardFrame>()

    /** Makes [id] its full card, or its design in [shape]: a full card becomes a shape half the page wide (or as small as it reads). */
    internal fun setForm(id: String, form: CardForm, shape: String? = null) {
        val spec = spec(id) ?: return
        if (form == CardForm.SHAPE && !spec.kind.shaped) return
        if (spec.form == form && (shape == null || shape == spec.shape)) return
        val page = page.toFloat().takeIf { it > 0f } ?: return
        reframe(id) { card ->
            val turned = card.copy(form = form, shape = shape ?: card.shape)
            val least = leastWidth(turned) / page
            if (card.form == CardForm.CARD && form == CardForm.SHAPE) fullFrames[id] = card.frame
            // Back to a full card, it is the size it was before its shapes were tried, where it is now.
            val was = if (card.form == CardForm.SHAPE && form == CardForm.CARD) fullFrames.remove(id) else null
            val width = when {
                card.form == form -> card.frame.width
                form == CardForm.SHAPE -> max(least, min(card.frame.width, 0.5f))
                else -> max(least, was?.width ?: card.frame.width)
            }.coerceAtMost(1f)
            turned.copy(frame = card.frame.copy(x = card.frame.x.coerceIn(0f, 1f - width), width = width, height = was?.height ?: card.frame.height))
        }
        announce(if (form == CardForm.SHAPE) "${shapeName(shape ?: spec.shape)} shape" else "Full card")
    }

    /** Makes [id] a tenth of the page wider ([delta] 1) or narrower (-1), within what it reads at. */
    internal fun resize(id: String, delta: Int) {
        val spec = spec(id) ?: return
        val page = page.toFloat().takeIf { it > 0f } ?: return
        val least = min(leastWidth(spec) / page, spec.frame.width)
        val width = (spec.frame.width + delta * 0.1f).coerceIn(least.coerceAtMost(1f), 1f)
        if (width == spec.frame.width) {
            haptic(EditHaptic.REJECT)
            announce(if (delta < 0) "As small as it goes" else "As wide as it goes")
            return
        }
        reframe(id) { it.copy(frame = it.frame.copy(x = it.frame.x.coerceIn(0f, 1f - width), width = width)) }
        announce("${(width * 100).roundToInt()} percent wide")
    }

    /** Makes a full card or a box a step taller ([delta] 1) or shorter (-1), from the height it has now. */
    internal fun stretch(id: String, delta: Int) {
        val spec = spec(id) ?: return
        // A shape is as tall as it is wide: taller is bigger.
        if (spec.mode == CardMode.SHAPED) return resize(id, delta)
        val now = spec.frame.height.takeIf { it > 0f } ?: slots[id]?.let { dp((it.height - gap).toFloat()) } ?: return
        val most = if (spec.mode == CardMode.BOX) BOX_MAX_DP else MAX_HEIGHT_DP
        val height = (now + delta * HEIGHT_STEP_DP).coerceIn(leastHeightDp(spec), most)
        if (height == now) {
            haptic(EditHaptic.REJECT)
            announce(if (delta > 0) "As tall as it goes" else "As short as it goes")
            return
        }
        reframe(id) { it.copy(frame = it.frame.copy(height = height)) }
        announce(if (delta > 0) "Taller" else "Shorter")
    }

    /** Makes a full card as tall as its content again, after it was given a height of its own. */
    internal fun fitHeight(id: String) {
        val spec = spec(id) ?: return
        if (!hasOwnHeight(spec)) return
        reframe(id) { it.copy(frame = it.frame.copy(height = 0f)) }
        announce("Fits its content")
    }

    /** Whether [spec] is a full card with a height of its own, which [fitHeight] takes away. */
    internal fun hasOwnHeight(spec: CardSpec): Boolean = spec.mode == CardMode.NATURAL && spec.frame.height > 0f

    /** Moves [id] a tenth of the page left ([delta] -1) or right (1). */
    internal fun nudge(id: String, delta: Int) {
        val spec = spec(id) ?: return
        val x = (spec.frame.x + delta * 0.1f).coerceIn(0f, 1f - spec.frame.width)
        if (x == spec.frame.x) {
            haptic(EditHaptic.REJECT)
            announce(if (delta < 0) "Already at the left edge" else "Already at the right edge")
            return
        }
        reframe(id) { it.copy(frame = it.frame.copy(x = x)) }
        announce(if (delta < 0) "Moved left" else "Moved right")
    }

    /** Moves [id] above ([delta] -1) or below (1) the next card it sits over or under. */
    internal fun move(id: String, delta: Int) {
        val cards = layout.cards
        val at = cards.indexOfFirst { it.id == id }
        val other = neighbor(id, delta)
        if (at < 0 || other == null) {
            haptic(EditHaptic.REJECT)
            announce(if (delta < 0) "Already at the top" else "Already at the bottom")
            return
        }
        val rest = cards.filter { it.id != id }.toMutableList()
        val to = rest.indexOf(other) + if (delta < 0) 0 else 1
        rest.add(to, cards[at].copy(frame = cards[at].frame.copy(gap = 0f)))
        commit(layout.copy(cards = rest))
        announce(if (delta < 0) "Moved up" else "Moved down")
    }

    /** The next card above ([delta] -1) or below (1) that [id] sits under or over, if there is one to move past. */
    private fun neighbor(id: String, delta: Int): CardSpec? {
        val mine = slots[id] ?: return null
        val cards = layout.cards
        val at = cards.indexOfFirst { it.id == id }
        if (at < 0) return null
        val near = if (delta < 0) (at - 1 downTo 0) else (at + 1 until cards.size)
        return near.map { cards[it] }.firstOrNull { c -> slots[c.id]?.let { overlapsAcross(it, mine.left, mine.right) } == true }
    }

    /** Covers [id] until tapped, or stops covering it. */
    internal fun togglePrivate(id: String) {
        val spec = spec(id) ?: return
        val private = !spec.private
        reframe(id) { it.copy(private = private) }
        announce(if (private) "Private: covered until tapped" else "No longer private")
    }

    /**
     * Hides a built-in card or removes one of the user's own, with Undo for a few seconds: Undo puts it back where it
     * was. A removed card's widget and what was read for it are let go only once Undo has gone.
     */
    internal fun removeCard(id: String) {
        val spec = spec(id) ?: return
        val at = layout.cards.indexOfFirst { it.id == id }
        // Undo puts it back under the card it was under, wherever that is by then: the order can change before Undo.
        val above = layout.cards.getOrNull(at - 1)?.id
        val cards = layout.cards.filter { it.id != id }
        flushRemoval()
        val undo = {
            val now = layout
            if (now.cards.none { it.id == id }) {
                val under = above?.let { a -> now.cards.indexOfFirst { it.id == a } }?.takeIf { it >= 0 }?.plus(1)
                val back = now.cards.toMutableList().apply { add((under ?: at).coerceIn(0, size), spec) }
                commit(DiscoverLayout(back, now.hidden - spec.kind))
                if (pendingRemoval?.first?.id == id) {
                    pendingRemoval?.second?.cancel()
                    pendingRemoval = null
                }
                announce(label(spec) + " is back")
                if (editing) select(id)
            }
        }
        if (spec.kind.builtIn) {
            commit(DiscoverLayout(cards, layout.hidden + spec.kind))
            say("Card hidden", undo)
        } else {
            commit(layout.copy(cards = cards))
            pendingRemoval = spec to scope.launch {
                delay(timeout(UNDO_MS, true) + 500)
                if (pendingRemoval?.first?.id == id) flushRemoval()
            }
            say("Card removed", undo)
        }
        if (selectedId == id) select(null)
    }

    /** Puts the hidden built-in [kind] back at the end of the page, full width. */
    internal fun addBack(kind: CardKind) {
        if (kind !in layout.hidden) return
        val spec = CardSpec(id = kind.key, kind = kind)
        commit(DiscoverLayout(layout.cards.filter { it.id != spec.id } + spec, layout.hidden - kind))
        if (shown(spec)) {
            announce("${kind.label} added")
            select(spec.id)
            reveal = spec.id
        } else {
            // Nothing to select or scroll to: said on the page, or the tap would look as if it did nothing.
            say("${kind.label} is back. It has nothing to show right now.")
        }
    }

    /** Every edit of [spec], for the actions sheet and TalkBack's actions. */
    internal fun actions(spec: CardSpec): List<CardAction> {
        val id = spec.id
        val wide = spec.frame.width < 0.999f
        val out = arrayListOf(
            CardAction("Move up", EditIcons.ArrowUpward, neighbor(id, -1) != null) { move(id, -1) },
            CardAction("Move down", EditIcons.ArrowDownward, neighbor(id, 1) != null) { move(id, 1) },
            CardAction("Move left", EditIcons.ArrowBack, wide && spec.frame.x > 0.001f) { nudge(id, -1) },
            CardAction("Move right", EditIcons.ArrowForward, wide && spec.frame.x + spec.frame.width < 0.999f) { nudge(id, 1) },
            CardAction("Make bigger", EditIcons.ZoomOutMap, wide) { resize(id, 1) },
            CardAction("Make smaller", EditIcons.CloseFullscreen, true) { resize(id, -1) }
        )
        if (spec.mode != CardMode.SHAPED) {
            out += CardAction("Make taller", EditIcons.Expand, true) { stretch(id, 1) }
            out += CardAction("Make shorter", EditIcons.Compress, true) { stretch(id, -1) }
        }
        if (hasOwnHeight(spec)) out += CardAction("Fit height to content", EditIcons.FitHeight, true) { fitHeight(id) }
        if (spec.kind == CardKind.CONTROLS) out += CardAction("Choose controls", Icons.Outlined.Tune, true) { open(EditSheet.CONTROLS) }
        if (spec.kind.shaped) {
            out += if (spec.form == CardForm.SHAPE) {
                CardAction("Make it a full card", EditIcons.CropSquare, true) { setForm(id, CardForm.CARD) }
            } else {
                CardAction("Make it a shape", Icons.Outlined.Interests, true) { setForm(id, CardForm.SHAPE) }
            }
        }
        out += CardAction(if (spec.private) "Stop covering it" else "Make private", Icons.Filled.Lock, true) { togglePrivate(id) }
        out += if (spec.kind.builtIn) {
            CardAction("Hide card", Icons.Outlined.VisibilityOff, true) { removeCard(id) }
        } else {
            CardAction("Remove card", Icons.Outlined.Delete, true) { removeCard(id) }
        }
        return out
    }

    internal companion object {
        /** How long a message with Undo stays. */
        const val UNDO_MS = 8_000L

        /** How near (dp) a card's edge comes to a line before it lines up with it. */
        const val SNAP_DP = 10f

        /** The narrowest a full card goes (dp), and the least its design is shrunk to get there. */
        const val NARROWEST_CARD_DP = 120f
        const val SMALLEST_DESIGN_SCALE = 0.42f

        /** A full card's least and most height (dp), and how near its content's height it snaps back to fitting it. */
        const val MIN_HEIGHT_DP = 64f
        const val MAX_HEIGHT_DP = 900f
        const val SNAP_HEIGHT_DP = 14f

        /** How much taller or shorter one step of Make taller and Make shorter is (dp). */
        const val HEIGHT_STEP_DP = 40f

        /** How much a pinch's spread is softened, so fingers close together don't make a jumpy size (dp). */
        const val PINCH_SOFTEN_DP = 16f

        /** Pinched to less than this much of its width, a full card offers its shapes. */
        const val SHRINK_OFFERS = 0.9f

        /** A box card's least and most height (dp). */
        const val BOX_MIN_DP = 110f
        const val BOX_MAX_DP = 520f
    }
}

private fun IntRect.toRect() = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

/** [name] (one of the CARD_SHAPES) in words. */
internal fun shapeName(name: String): String = when (name) {
    "verysunny" -> "Very sunny"
    "softburst" -> "Soft burst"
    else -> {
        val word = name.takeWhile { !it.isDigit() }.replaceFirstChar { it.uppercase() }
        val number = name.dropWhile { !it.isDigit() }
        if (number.isEmpty()) word else "$word $number"
    }
}

/**
 * Discover's edit mode state, shared by [DiscoverGrid] and [DiscoverEditChrome]. Its haptics follow the app's
 * Vibration setting, and its motion snaps with animations off.
 */
@Composable
internal fun rememberDiscoverEditState(): DiscoverEditState {
    val view = LocalView.current
    val vibration = rememberSetting(SettingsManager.VIBRATION)
    val animations = rememberAnimationsEnabled()
    val scope = rememberCoroutineScope()
    val state = remember(view, scope) { DiscoverEditState(view, vibration, animations, scope) }
    state.density = LocalDensity.current
    return state
}
