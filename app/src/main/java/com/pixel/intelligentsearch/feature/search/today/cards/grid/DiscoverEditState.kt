package com.pixel.intelligentsearch.feature.search.today.cards.grid

import android.os.Build
import android.view.HapticFeedbackConstants
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
    ACTIONS
}

/** Edit mode's haptics. */
internal enum class EditHaptic { LONG_PRESS, TICK, SETTLE, CONFIRM, TOGGLE_ON, TOGGLE_OFF, REJECT }

/** One message, shown or spoken once even when its words are the same as the last one's. */
internal class EditMessage(val text: String)

/** One edit of a card: its [label], [icon], whether it can be done now ([enabled]), and doing it ([run]). */
internal class CardAction(val label: String, val icon: ImageVector, val enabled: Boolean, val run: () -> Unit)

/** Settling: quick out, a touch of overshoot. */
internal val SettleEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.05f)

/** How a pinch began: the card's place then, how far apart the fingers were across and down, and between them. */
internal class PinchStart(val rect: Rect, val across: Float, val down: Float, val distance: Float, val middle: Offset)

/**
 * The card being moved or pinched: its place now ([rect], the page's pixels, the card plus half the gap all round),
 * following the fingers, and the lines it lines up with ([guides], page x).
 */
@Stable
internal class ActiveCard(val id: String, start: Rect, internal var grab: Offset, internal val reference: Map<String, IntRect>) {
    var rect by mutableStateOf(start)
    var guides by mutableStateOf<List<Int>>(emptyList())
    var pinching by mutableStateOf(false)

    /** The height the card measured at its live width (a full design is as tall as its content). Layout only. */
    internal var measured: Int = start.height.roundToInt()
    internal var atLeast = false
    internal var snappedTo: List<Int> = emptyList()
    internal var pinch: PinchStart? = null
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
    internal var gridCoords: LayoutCoordinates? = null
    internal var chromeCoords: LayoutCoordinates? = null
    internal var topBarBottom: Float = 0f
    internal var scroll: ScrollState? = null

    internal val animate: Boolean get() = animations.value

    private fun px(dp: Float): Float = with(density) { dp.dp.toPx() }
    private fun dp(px: Float): Float = with(density) { px.toDp().value }

    // ---------------------------------------------------------------------------------------------------------------
    // Feedback

    /** Plays [kind] when the app's Vibration setting is on, as the app's own haptics do. */
    internal fun haptic(kind: EditHaptic) {
        if (!vibration.value) return
        val tick = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val constant = when (kind) {
            EditHaptic.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            EditHaptic.TICK -> if (tick) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            EditHaptic.SETTLE -> HapticFeedbackConstants.GESTURE_END
            EditHaptic.CONFIRM -> HapticFeedbackConstants.CONFIRM
            EditHaptic.TOGGLE_ON -> if (tick) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
            EditHaptic.TOGGLE_OFF -> if (tick) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
            EditHaptic.REJECT -> HapticFeedbackConstants.REJECT
        }
        try {
            // The app's own Vibration setting decides, as it does for every haptic the app plays.
            view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
        } catch (_: Exception) {}
    }

    /** Has TalkBack say [text]. */
    internal fun announce(text: String) {
        announcement = EditMessage(text)
    }

    /** Shows [text] briefly at the bottom of the page, and has TalkBack say it. */
    internal fun say(text: String) {
        message = EditMessage(text)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Edit mode

    /** Starts editing with [id] selected. */
    fun enter(id: String?) {
        if (!editing) haptic(EditHaptic.LONG_PRESS)
        editing = true
        selectedId = id
        announce("Editing Discover. Drag a card to move it, pinch it to resize it.")
    }

    /** Stops editing: the selection, any sheet and any card held let go. */
    fun exit() {
        if (!editing) return
        haptic(EditHaptic.SETTLE)
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
        haptic(EditHaptic.TICK)
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
            CardMode.NATURAL -> spec.kind.minCardDp.toFloat()
            CardMode.SHAPED -> minShape(spec)
            CardMode.BOX -> spec.kind.minCardDp.toFloat()
        }
    )

    /** A box's least and most height, in pixels of its place. */
    private val boxLeast get() = gap + px(BOX_MIN_DP)
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
        active = ActiveCard(id, rect, finger - rect.topLeft, slots)
        haptic(EditHaptic.LONG_PRESS)
    }

    /** Moves the card being moved so the finger, now at [finger], holds it where it took it. */
    internal fun moveTo(finger: Offset) {
        val card = active ?: return
        if (card.pinch != null) return
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
            card = ActiveCard(id, rect, Offset.Zero, slots)
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
        val sx = (abs(a.x - b.x) + soft) / (start.across + soft)
        val sy = (abs(a.y - b.y) + soft) / (start.down + soft)
        val middle = (a + b) / 2f - start.middle
        val least = leastWidth(spec)
        val reach = px(SNAP_DP).roundToInt()
        val others = othersThan(card.id)
        val page = page.toFloat()
        var width: Float
        var height: Float
        when (spec.mode) {
            CardMode.NATURAL -> {
                val wanted = start.rect.width * sx
                width = wanted.coerceIn(least, page)
                height = card.measured.toFloat()
                // Pinched smaller, a full card offers its shapes at once; one that has none says it's as small as it goes.
                if (sx * sy < SHRINK_OFFERS && spec.kind.shaped && strip != card.id) {
                    strip = card.id
                    haptic(EditHaptic.TICK)
                }
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
                width = (start.rect.width * sx).coerceIn(least, page)
                height = (start.rect.height * sy).coerceIn(boxLeast, boxMost)
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
        val arranged = arranged
        val spec = spec(card.id)
        val rect = arranged?.rects?.get(card.id)
        if (arranged == null || spec == null || rect == null || page <= 0) return
        landed = card.id to card.rect.topLeft
        val frame = CardFrame(
            x = rect.left.toFloat() / page,
            width = rect.width.toFloat() / page,
            height = if (spec.mode == CardMode.BOX) dp((rect.height - gap).toFloat()) else spec.frame.height,
            gap = dp(arranged.activeGap.toFloat())
        )
        val moved = spec.copy(frame = frame)
        val rest = layout.cards.filter { it.id != card.id }
        val after = arranged.order.indexOf(card.id).let { arranged.order.getOrNull(it + 1) }
        val at = after?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: rest.size
        val cards = ArrayList(rest).apply { add(at, moved) }
        commit(layout.copy(cards = sortedDown(cards) { arranged.rects[it.id]?.top }))
        haptic(EditHaptic.SETTLE)
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

    /** Makes [id] its full card, or its design in [shape]: a full card becomes a shape half the page wide (or as small as it reads). */
    internal fun setForm(id: String, form: CardForm, shape: String? = null) {
        val spec = spec(id) ?: return
        if (form == CardForm.SHAPE && !spec.kind.shaped) return
        if (spec.form == form && (shape == null || shape == spec.shape)) return
        val page = page.toFloat().takeIf { it > 0f } ?: return
        reframe(id) { card ->
            val turned = card.copy(form = form, shape = shape ?: card.shape)
            val least = leastWidth(turned) / page
            val width = when {
                card.form == form -> card.frame.width
                form == CardForm.SHAPE -> max(least, min(card.frame.width, 0.5f))
                else -> max(least, card.frame.width)
            }.coerceAtMost(1f)
            turned.copy(frame = card.frame.copy(x = card.frame.x.coerceIn(0f, 1f - width), width = width))
        }
        haptic(EditHaptic.CONFIRM)
        announce(if (form == CardForm.SHAPE) "${shapeName(shape ?: spec.shape)} shape" else "Full card")
    }

    /** Makes [id] a tenth of the page wider ([delta] 1) or narrower (-1), within what it reads at. */
    internal fun resize(id: String, delta: Int) {
        val spec = spec(id) ?: return
        val page = page.toFloat().takeIf { it > 0f } ?: return
        val least = leastWidth(spec) / page
        val width = (spec.frame.width + delta * 0.1f).coerceIn(least.coerceAtMost(1f), 1f)
        if (width == spec.frame.width) {
            haptic(EditHaptic.REJECT)
            return
        }
        reframe(id) { it.copy(frame = it.frame.copy(x = it.frame.x.coerceIn(0f, 1f - width), width = width)) }
        haptic(EditHaptic.TICK)
        announce("${(width * 100).roundToInt()} percent wide")
    }

    /** Moves [id] a tenth of the page left ([delta] -1) or right (1). */
    internal fun nudge(id: String, delta: Int) {
        val spec = spec(id) ?: return
        val x = (spec.frame.x + delta * 0.1f).coerceIn(0f, 1f - spec.frame.width)
        if (x == spec.frame.x) return
        reframe(id) { it.copy(frame = it.frame.copy(x = x)) }
        haptic(EditHaptic.TICK)
    }

    /** Moves [id] above ([delta] -1) or below (1) the next card it sits over or under. */
    internal fun move(id: String, delta: Int) {
        val mine = slots[id] ?: return
        val cards = layout.cards
        val at = cards.indexOfFirst { it.id == id }
        val near = if (delta < 0) (at - 1 downTo 0) else (at + 1 until cards.size)
        val other = near.map { cards[it] }.firstOrNull { c -> slots[c.id]?.let { overlapsAcross(it, mine.left, mine.right) } == true } ?: return
        val rest = cards.filter { it.id != id }.toMutableList()
        val to = rest.indexOf(other) + if (delta < 0) 0 else 1
        rest.add(to, cards[at].copy(frame = cards[at].frame.copy(gap = 0f)))
        commit(layout.copy(cards = rest))
        haptic(EditHaptic.TICK)
        announce(if (delta < 0) "Moved up" else "Moved down")
    }

    /** Covers [id] until tapped, or stops covering it. */
    internal fun togglePrivate(id: String) {
        val spec = spec(id) ?: return
        val private = !spec.private
        reframe(id) { it.copy(private = private) }
        haptic(if (private) EditHaptic.TOGGLE_ON else EditHaptic.TOGGLE_OFF)
        announce(if (private) "Private: covered until tapped" else "No longer private")
    }

    /** Hides a built-in card (it can be added back) or removes one of the user's own. */
    internal fun removeCard(id: String) {
        val spec = spec(id) ?: return
        val cards = layout.cards.filter { it.id != id }
        if (spec.kind.builtIn) {
            commit(DiscoverLayout(cards, layout.hidden + spec.kind))
            say("Hidden. Add it back with +.")
        } else {
            commit(layout.copy(cards = cards))
            onRemoveCustom(spec)
            say("Card removed")
        }
        haptic(EditHaptic.REJECT)
        if (selectedId == id) select(null)
    }

    /** Puts the hidden built-in [kind] back at the end of the page, full width. */
    internal fun addBack(kind: CardKind) {
        if (kind !in layout.hidden) return
        val spec = CardSpec(id = kind.key, kind = kind)
        commit(DiscoverLayout(layout.cards.filter { it.id != spec.id } + spec, layout.hidden - kind))
        haptic(EditHaptic.CONFIRM)
        announce("${kind.label} added")
        select(spec.id)
        reveal = spec.id
    }

    /** Every edit of [spec], for the actions sheet and TalkBack's actions. */
    internal fun actions(spec: CardSpec): List<CardAction> {
        val id = spec.id
        val wide = spec.frame.width < 0.999f
        val out = arrayListOf(
            CardAction("Move up", EditIcons.ArrowUpward, true) { move(id, -1) },
            CardAction("Move down", EditIcons.ArrowDownward, true) { move(id, 1) },
            CardAction("Move left", EditIcons.ArrowBack, wide && spec.frame.x > 0.001f) { nudge(id, -1) },
            CardAction("Move right", EditIcons.ArrowForward, wide && spec.frame.x + spec.frame.width < 0.999f) { nudge(id, 1) },
            CardAction("Make bigger", EditIcons.ZoomOutMap, wide) { resize(id, 1) },
            CardAction("Make smaller", EditIcons.CloseFullscreen, true) { resize(id, -1) }
        )
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

    private companion object {
        /** How near (dp) a card's edge comes to a line before it lines up with it. */
        const val SNAP_DP = 10f

        /** How much a pinch's spread is softened, so fingers close together don't make a jumpy size (dp). */
        const val PINCH_SOFTEN_DP = 48f

        /** Pinched to less than this much of its area, a full card offers its shapes. */
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
