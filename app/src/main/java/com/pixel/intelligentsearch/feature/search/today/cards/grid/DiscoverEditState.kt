package com.pixel.intelligentsearch.feature.search.today.cards.grid

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.ui.icons.outlined.Delete
import com.pixel.intelligentsearch.core.ui.icons.outlined.Interests
import com.pixel.intelligentsearch.core.ui.icons.outlined.VisibilityOff
import com.pixel.intelligentsearch.core.ui.icons.filled.Lock
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardMode
import com.pixel.intelligentsearch.feature.search.today.cards.CardSize
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.GRID_COLUMNS
import com.pixel.intelligentsearch.feature.search.today.cards.GRID_MAX_ROWS
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/*
 * Discover's edit mode: what is selected, what is being moved or resized, which sheet is open, and every edit, each
 * with its haptic and what TalkBack hears. The grid and the chrome over the page share one of these.
 *
 * NG Designs
 */

/** The sheets edit mode opens from the bottom of the screen. */
internal enum class EditSheet {
    /** The shapes a shaped card can take. */
    SHAPE,

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

/** An edge of a card a resize handle sits on. */
internal enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

/** Lift: a little past 1.05 and back, as the preview's 180 ms overshoot. */
private val LiftEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.3f)

/** Settling: quick out, a touch of overshoot. */
internal val SettleEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.05f)

/** How long the finger rests on a spot before the cards make room. */
private const val REORDER_MS = 380L

/** How long cards that just made room are left alone as targets, so the order doesn't swing back and forth. */
private const val SETTLE_MS = 340L

/** The sizes the Size button and the actions offer: small, wide and large. */
private val PRESETS = listOf(Triple("small", 2, 2), Triple("wide", 4, 2), Triple("large", 4, 4))

/**
 * A card held under the finger: [id], drawn from [layer] (recorded by the card where it sits on the page) at [size],
 * held [grab] in from its top left. [finger] is in the root's coordinates; [hot] while it is over the Remove or Hide
 * target. While it glides home, [landing] is its top left.
 */
@Stable
internal class CardDrag(val id: String, val layer: GraphicsLayer, val size: IntSize, val grab: Offset, finger: Offset) {
    var finger by mutableStateOf(finger)
    var hot by mutableStateOf(false)
    val scale = Animatable(1f)
    val alpha = Animatable(1f)
    var landing by mutableStateOf<Animatable<Offset, AnimationVector2D>?>(null)
    var released by mutableStateOf(false)
    internal var pendingTo = -1
    internal var pending: Job? = null
}

/**
 * A card's [edge] being dragged: [frame] (the grid's coordinates) follows the finger, which is [offset] from the
 * edge; [limited] while the size under the finger is too small to read; [settling] while the frame settles onto
 * the card after release.
 */
@Stable
internal class CardResize(val id: String, val edge: Edge, val offset: Float, frame: Rect) {
    var frame by mutableStateOf(frame)
    var limited by mutableStateOf(false)
    var settling by mutableStateOf<Animatable<Rect, AnimationVector4D>?>(null)
}

/** The card [id] that will make room leaning [offset] (pixels) the way it is about to go. */
internal class Nudge(val id: String, val offset: Offset)

/**
 * Discover's edit mode, shared by [DiscoverGrid] and [DiscoverEditChrome]: whether the page is [editing], the
 * [selectedId] card, the open [sheet], and the card being moved or resized. The grid keeps it up to date with the
 * layout and how the cards are laid out; every edit goes through [commit] to the page's onLayoutChange.
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

    internal var message by mutableStateOf<EditMessage?>(null)
    internal var announcement by mutableStateOf<EditMessage?>(null)
    internal var drag by mutableStateOf<CardDrag?>(null)
    internal var resize by mutableStateOf<CardResize?>(null)
    internal var nudge by mutableStateOf<Nudge?>(null)

    /** A card to bring into view once it is laid out (one just added). */
    internal var reveal by mutableStateOf<String?>(null)

    // What the grid and chrome keep current, read by the gestures and edits (never drawn from).
    internal var layout: DiscoverLayout = DiscoverLayout.DEFAULT
    internal var onLayoutChange: (DiscoverLayout) -> Unit = {}
    internal var shown: (CardSpec) -> Boolean = { true }
    internal var fits: (CardSpec, CardSize) -> Boolean = { _, _ -> true }
    internal var label: (CardSpec) -> String = { it.kind.label }
    internal var onRemoveCustom: (CardSpec) -> Unit = {}
    internal var density: Density = Density(1f)
    internal var metrics: GridMetrics? = null
    internal var rects: Map<String, IntRect> = emptyMap()
    internal var heights: Map<String, Int> = emptyMap()
    internal var spots: List<Spot> = emptyList()
    internal var gridCoords: LayoutCoordinates? = null
    internal var chromeCoords: LayoutCoordinates? = null
    internal var topBarBottom: Float = 0f
    internal var scroll: ScrollState? = null
    private var settledAt = 0L

    internal val animate: Boolean get() = animations.value

    private fun px(dp: Float): Float = with(density) { dp.dp.toPx() }

    // ---------------------------------------------------------------------------------------------------------------
    // Feedback

    /** Plays [kind] when the app's Vibration setting is on, as the app's own haptics do. */
    internal fun haptic(kind: EditHaptic) {
        if (!vibration.value) return
        val constant = when (kind) {
            EditHaptic.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
            EditHaptic.TICK ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK
                else HapticFeedbackConstants.CLOCK_TICK
            EditHaptic.SETTLE -> HapticFeedbackConstants.GESTURE_END
            EditHaptic.CONFIRM -> HapticFeedbackConstants.CONFIRM
            EditHaptic.TOGGLE_ON ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.TOGGLE_ON
                else HapticFeedbackConstants.CLOCK_TICK
            EditHaptic.TOGGLE_OFF ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.TOGGLE_OFF
                else HapticFeedbackConstants.CLOCK_TICK
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
        editing = true
        selectedId = id
        val spec = id?.let(::spec)
        announce("Editing Discover." + (spec?.let { " " + describe(it) } ?: ""))
    }

    /** Stops editing: the selection, any sheet and any card held let go. */
    fun exit() {
        if (!editing) return
        haptic(EditHaptic.SETTLE)
        cancelDrag()
        resize = null
        editing = false
        selectedId = null
        sheet = null
        announce("Done editing Discover")
    }

    /** Selects [id] (or nothing). */
    internal fun select(id: String?) {
        selectedId = id
    }

    /** Opens [which] (or closes the open sheet). */
    internal fun open(which: EditSheet?) {
        sheet = which
    }

    /** Makes [new] the page's layout. */
    internal fun commit(new: DiscoverLayout) {
        layout = new
        onLayoutChange(new)
    }

    internal fun spec(id: String): CardSpec? = layout.cards.firstOrNull { it.id == id }

    private fun indexOf(id: String) = layout.cards.indexOfFirst { it.id == id }

    /** The cards on the page, in order. */
    private fun shownCards() = layout.cards.filter(shown)

    /** [spec]'s size in cells: its rows are its height in cells when it is as tall as its content. */
    internal fun dims(spec: CardSpec): Pair<Int, Int> = when (val size = spec.size) {
        is CardSize.Cells -> size.columns to size.rows
        is CardSize.Natural -> {
            val height = heights[spec.id]
            val m = metrics
            size.columns to if (height != null && m != null) m.cellsFor(height.toFloat(), GRID_MAX_ROWS) else 1
        }
    }

    /** What TalkBack hears for [spec]: its label, and while editing its size, shape and how to move it. */
    internal fun describe(spec: CardSpec): String {
        val base = label(spec)
        if (!editing) return base
        val (c, r) = dims(spec)
        val shape = if (spec.mode == CardMode.SHAPED) ", ${shapeName(spec.shape)} shape" else ""
        return "$base, $c by $r$shape, double-tap and hold to move, or use the actions menu"
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Edits

    /** Moves [id] one place earlier ([delta] -1) or later (+1) among the cards on the page. */
    internal fun move(id: String, delta: Int) {
        val visible = shownCards()
        val at = visible.indexOfFirst { it.id == id }
        val other = visible.getOrNull(at + delta) ?: return
        commit(layout.moved(indexOf(id), indexOf(other.id)))
        haptic(EditHaptic.TICK)
        announce("Moved to position ${at + delta + 1} of ${visible.size}")
    }

    /** Resizes [spec] to [size], keeping its row, with a tick. */
    internal fun applySize(spec: CardSpec, size: CardSize) {
        val index = indexOf(spec.id)
        if (index < 0 || size == spec.size) return
        commit(layout.resized(index, size, shown))
        haptic(EditHaptic.TICK)
        val (c, r) = when (size) {
            is CardSize.Cells -> size.columns to size.rows
            is CardSize.Natural -> size.columns to dims(spec).second
        }
        announce("${label(spec)} is now $c by $r")
    }

    /** One step bigger: wider while it is no wider than tall, otherwise taller. */
    internal fun grow(id: String) {
        val spec = spec(id) ?: return
        val (c, r) = dims(spec)
        val first = if (c < GRID_COLUMNS && c <= r) c + 1 to r else c to r + 1
        val options = listOf(first, c + 1 to r, c to r + 1)
            .filter { it.first <= GRID_COLUMNS && it.second <= GRID_MAX_ROWS }
            .map { candidateSize(spec.kind, it.first, it.second) }
        val size = options.firstOrNull { it != spec.size && fits(spec, it) }
        if (size != null) applySize(spec, size) else announce("It can't get bigger")
    }

    /** One step smaller, as long as its text stays readable. */
    internal fun shrink(id: String) {
        val spec = spec(id) ?: return
        val (c, r) = dims(spec)
        val options = (if (c >= r) listOf(c - 1 to r, c to r - 1) else listOf(c to r - 1, c - 1 to r))
            .filter { it.first >= 1 && it.second >= 1 }
            .map { candidateSize(spec.kind, it.first, it.second) }
        val size = options.firstOrNull { it != spec.size && fits(spec, it) }
        if (size != null) {
            applySize(spec, size)
        } else {
            haptic(EditHaptic.REJECT)
            announce("It can't get smaller: its text would be too small to read")
        }
    }

    /** The preset sizes [spec] can take, by name, each once. */
    internal fun presets(spec: CardSpec): List<Pair<String, CardSize>> {
        val out = ArrayList<Pair<String, CardSize>>()
        for ((name, c, r) in PRESETS) {
            val size = candidateSize(spec.kind, c, r)
            if (out.none { it.second == size } && fits(spec, size)) out += name to size
        }
        return out
    }

    /** The Size button: the next preset size that fits. */
    internal fun cycleSize(id: String) {
        val spec = spec(id) ?: return
        val options = presets(spec)
        if (options.isEmpty()) {
            announce("No other size fits")
            return
        }
        val now = options.indexOfFirst { it.second == spec.size }
        applySize(spec, options[(now + 1) % options.size].second)
    }

    /** Covers [id] until tapped, or stops covering it. */
    internal fun togglePrivate(id: String) {
        val spec = spec(id) ?: return
        val private = !spec.private
        commit(layout.copy(cards = layout.cards.map { if (it.id == id) it.copy(private = private) else it }))
        haptic(if (private) EditHaptic.TOGGLE_ON else EditHaptic.TOGGLE_OFF)
        announce(if (private) "Private: covered until tapped" else "No longer private")
    }

    /** Gives [id] the Material shape [shape]. */
    internal fun setShape(id: String, shape: String) {
        val spec = spec(id) ?: return
        if (spec.shape == shape) return
        commit(layout.copy(cards = layout.cards.map { if (it.id == id) it.copy(shape = shape) else it }))
        haptic(EditHaptic.CONFIRM)
        announce("${shapeName(shape)} shape")
    }

    /** Hides a built-in card (it can be added back) or removes one of the user's own. */
    internal fun removeCard(id: String) {
        val spec = spec(id) ?: return
        val cards = layout.cards.filter { it.id != id }
        if (spec.kind.builtIn) {
            commit(DiscoverLayout(cards, layout.hidden + spec.kind))
            say("Hidden. Add it back from Add card.")
        } else {
            commit(layout.copy(cards = cards))
            onRemoveCustom(spec)
            say("Card removed")
        }
        haptic(EditHaptic.REJECT)
        if (selectedId == id) selectedId = null
    }

    /** Puts the hidden built-in [kind] back at the end of the page, full width. */
    internal fun addBack(kind: CardKind) {
        if (kind !in layout.hidden) return
        val spec = CardSpec(id = kind.key, kind = kind, size = CardSize.Natural())
        commit(DiscoverLayout(layout.cards.filter { it.id != spec.id } + spec, layout.hidden - kind))
        haptic(EditHaptic.CONFIRM)
        announce("${kind.label} added")
        selectedId = spec.id
        reveal = spec.id
    }

    /** Every edit of [spec], for the actions sheet and TalkBack's actions. */
    internal fun actions(spec: CardSpec): List<CardAction> {
        val visible = shownCards()
        val at = visible.indexOfFirst { it.id == spec.id }
        val id = spec.id
        val out = arrayListOf(
            CardAction("Move earlier", EditIcons.ArrowUpward, at > 0) { move(id, -1) },
            CardAction("Move later", EditIcons.ArrowDownward, at in 0 until visible.size - 1) { move(id, 1) },
            CardAction("Make bigger", EditIcons.ZoomOutMap, true) { grow(id) },
            CardAction("Make smaller", EditIcons.CloseFullscreen, true) { shrink(id) }
        )
        for ((name, size) in presets(spec)) {
            val icon = when (name) {
                "small" -> EditIcons.CropSquare
                "wide" -> EditIcons.Crop169
                else -> EditIcons.CropPortrait
            }
            val measure = when (size) {
                is CardSize.Cells -> "${size.columns} by ${size.rows}"
                is CardSize.Natural -> "${size.columns} across"
            }
            out += CardAction("Make $name ($measure)", icon, size != spec.size) { applySize(spec(id) ?: spec, size) }
        }
        if (spec.mode == CardMode.SHAPED) out += CardAction("Change shape", Icons.Outlined.Interests, true) { select(id); open(EditSheet.SHAPE) }
        if (spec.kind.builtIn) {
            out += CardAction("Hide card", Icons.Outlined.VisibilityOff, true) { removeCard(id) }
        } else {
            out += CardAction(if (spec.private) "Stop covering it" else "Make private", Icons.Filled.Lock, true) { togglePrivate(id) }
            out += CardAction("Remove card", Icons.Outlined.Delete, true) { removeCard(id) }
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Moving, the Pixel Launcher way

    /** Lifts [id] (drawn from [layer] at [size]) under the finger at [finger] (root), held [grab] in from its top left. */
    internal fun pickUp(id: String, layer: GraphicsLayer, size: IntSize, finger: Offset, grab: Offset) {
        if (resize != null || drag != null) return
        val held = CardDrag(id, layer, size, grab, finger)
        drag = held
        selectedId = id
        scope.launch { held.scale.animateTo(1.05f, if (animate) tween(180, easing = LiftEasing) else tween(0)) }
        scope.launch { autoScroll(held) }
    }

    /** The held card follows the finger, now at [finger] (root). */
    internal fun dragTo(finger: Offset) {
        val held = drag ?: return
        if (held.released) return
        held.finger = finger
        retarget(held)
    }

    /** Lets go of the held card: onto Remove or Hide when it is there and [ok], otherwise home with a glide. */
    internal fun drop(ok: Boolean) {
        val held = drag ?: return
        if (held.released) return
        held.released = true
        clearPending(held)
        if (held.hot && ok) {
            scope.launch {
                if (animate) {
                    launch { held.alpha.animateTo(0f, tween(200)) }
                    held.scale.animateTo(held.scale.value * 0.5f, tween(200))
                }
                if (drag === held) drag = null
                removeCard(held.id)
            }
            return
        }
        haptic(EditHaptic.SETTLE)
        val home = homeOf(held.id)
        if (!animate || home == null) {
            drag = null
            return
        }
        scope.launch {
            val glide = Animatable(held.finger - held.grab, Offset.VectorConverter)
            held.landing = glide
            launch { held.alpha.animateTo(1f, tween(320, easing = SettleEasing)) }
            launch { held.scale.animateTo(1f, tween(320, easing = SettleEasing)) }
            glide.animateTo(home, tween(320, easing = SettleEasing))
            if (drag === held) drag = null
        }
    }

    /** Lets go of the held card at once, with no glide (its card left the page). */
    internal fun cancelDrag() {
        drag?.let { clearPending(it) }
        drag = null
        nudge = null
    }

    /** Where [id] sits on the page, in the root's coordinates. */
    private fun homeOf(id: String): Offset? {
        val coords = gridCoords?.takeIf { it.isAttached } ?: return null
        val rect = rects[id] ?: return null
        return coords.positionInRoot() + Offset(rect.left.toFloat(), rect.top.toFloat())
    }

    private fun clearPending(held: CardDrag) {
        held.pending?.cancel()
        held.pending = null
        held.pendingTo = -1
        nudge = null
    }

    private fun retarget(held: CardDrag) {
        val hot = held.finger.y < topBarBottom + px(6f)
        if (hot != held.hot) {
            held.hot = hot
            haptic(EditHaptic.TICK)
            clearPending(held)
            scope.launch {
                launch { held.alpha.animateTo(if (hot) 0.55f else 1f, tween(if (animate) 150 else 0)) }
                held.scale.animateTo(if (hot) 0.7f else 1.05f, tween(if (animate) 180 else 0, easing = SettleEasing))
            }
        }
        if (hot) return
        val coords = gridCoords?.takeIf { it.isAttached } ?: return
        val at = held.finger - coords.positionInRoot()
        val from = indexOf(held.id)
        val (to, under) = targetAt(at, from, held.id)
        if (to < 0 || to == from) {
            clearPending(held)
            return
        }
        if (held.pendingTo == to) return
        clearPending(held)
        held.pendingTo = to
        if (under != null) nudge = Nudge(under, leanOf(under, forward = to < from))
        held.pending = scope.launch {
            delay(REORDER_MS)
            if (drag !== held || held.released) return@launch
            held.pending = null
            held.pendingTo = -1
            nudge = null
            reorder(held, to)
        }
    }

    /** The layout index a card dropped at [at] (the grid's coordinates) would take, and the card that is there. */
    private fun targetAt(at: Offset, from: Int, id: String): Pair<Int, String?> {
        val x = at.x.toInt()
        val y = at.y.toInt()
        spots.firstOrNull { it.rect.contains(IntOffset(x, y)) }?.let { spot ->
            var to = spot.insertAt
            if (from < to) to--
            return to to null
        }
        if (System.currentTimeMillis() < settledAt) return -1 to null
        val target = rects.entries.firstOrNull { it.key != id && it.value.contains(IntOffset(x, y)) } ?: return -1 to null
        return indexOf(target.key) to target.key
    }

    /** Which way the card [id] leans as it gets ready to go: on along its line, or down (or back, or up). */
    private fun leanOf(id: String, forward: Boolean): Offset {
        val rect = rects[id] ?: return Offset.Zero
        val m = metrics ?: return Offset.Zero
        val step = px(7f)
        return if (forward) {
            if (rect.right < m.width - m.gap / 2) Offset(step, 0f) else Offset(0f, step)
        } else {
            if (rect.left > m.gap / 2) Offset(-step, 0f) else Offset(0f, -step)
        }
    }

    private fun reorder(held: CardDrag, to: Int) {
        val from = indexOf(held.id)
        if (from < 0 || to == from) return
        commit(layout.moved(from, to))
        haptic(EditHaptic.TICK)
        settledAt = System.currentTimeMillis() + SETTLE_MS
        val visible = shownCards()
        announce("Moved to position ${visible.indexOfFirst { it.id == held.id } + 1} of ${visible.size}")
    }

    /** Holding near the top or bottom edge scrolls the page under the held card. */
    private suspend fun autoScroll(held: CardDrag) {
        var last = 0L
        var moved = false
        while (drag === held && !held.released) {
            val now = withFrameNanos { it }
            val frames = if (last == 0L) 1f else ((now - last) / 16_666_667f).coerceIn(0.25f, 4f)
            last = now
            if (moved) {
                moved = false
                retarget(held)
            }
            val state = scroll ?: continue
            val view = chromeCoords?.takeIf { it.isAttached } ?: continue
            val bottom = view.positionInRoot().y + view.size.height
            val y = held.finger.y
            val top = topBarBottom + px(70f)
            val most = px(14f)
            val speed = when {
                !held.hot && y < top -> -min(most, (top - y) / 4f)
                y > bottom - px(80f) -> min(most, (y - (bottom - px(80f))) / 4f)
                else -> 0f
            }
            if (speed != 0f && abs(state.dispatchRawDelta(speed * frames)) > 0f) moved = true
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Resizing, the Pixel Launcher way

    /** Starts dragging [edge] of [id] with the finger at [finger] (the grid's coordinates). */
    internal fun startResize(id: String, edge: Edge, finger: Offset) {
        if (drag != null) return
        val rect = rects[id]?.toRect() ?: return
        val offset = when (edge) {
            Edge.LEFT -> rect.left - finger.x
            Edge.RIGHT -> rect.right - finger.x
            Edge.TOP -> rect.top - finger.y
            Edge.BOTTOM -> rect.bottom - finger.y
        }
        selectedId = id
        resize = CardResize(id, edge, offset, rect)
    }

    /**
     * The dragged edge follows the finger at [finger] (the grid's coordinates); the card snaps to the size the frame
     * is closest to, and the frame turns red where that size is too small to read.
     */
    internal fun resizeTo(finger: Offset) {
        val r = resize ?: return
        if (r.settling != null) return
        val m = metrics ?: return
        val spec = spec(r.id) ?: return
        val rect = rects[r.id]?.toRect() ?: return
        val minSide = m.cell * 0.6f
        val tallest = m.span(0, GRID_MAX_ROWS).toFloat()
        val reach = px(6f)
        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom
        when (r.edge) {
            Edge.LEFT -> left = (finger.x + r.offset).coerceIn(-reach, rect.right - minSide)
            Edge.RIGHT -> right = (finger.x + r.offset).coerceIn(rect.left + minSide, m.width + reach)
            Edge.TOP -> top = (finger.y + r.offset).coerceIn(rect.bottom - tallest, rect.bottom - minSide)
            Edge.BOTTOM -> bottom = (finger.y + r.offset).coerceIn(rect.top + minSide, rect.top + tallest)
        }
        r.frame = Rect(left, top, right, bottom)
        val columns = m.cellsFor(right - left, GRID_COLUMNS)
        val rows = m.cellsFor(bottom - top, GRID_MAX_ROWS)
        val size = candidateSize(spec.kind, columns, rows)
        if (size != spec.size) {
            if (fits(spec, size)) {
                r.limited = false
                applySize(spec, size)
            } else if (!r.limited) {
                r.limited = true
                haptic(EditHaptic.REJECT)
            }
        } else if (r.limited) {
            r.limited = false
        }
    }

    /** Lets go of the edge: the frame settles onto the card. */
    internal fun endResize() {
        val r = resize ?: return
        if (r.settling != null) return
        haptic(EditHaptic.SETTLE)
        if (!animate) {
            resize = null
            return
        }
        scope.launch {
            // The card's last snap is laid out a frame later.
            withFrameNanos { }
            val target = rects[r.id]?.toRect()
            if (target != null) {
                val settle = Animatable(r.frame, Rect.VectorConverter)
                r.limited = false
                r.settling = settle
                settle.animateTo(target, tween(260, easing = SettleEasing))
            }
            if (resize === r) resize = null
        }
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
