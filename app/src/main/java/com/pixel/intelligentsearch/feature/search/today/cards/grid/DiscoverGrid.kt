package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.feature.search.today.cards.CardKind
import com.pixel.intelligentsearch.feature.search.today.cards.CardMode
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.shape.CardOutlineShape
import com.pixel.intelligentsearch.feature.search.today.cards.shape.CardShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Discover's freeform page: the cards where the user put them (FreeFlow), and, while editing, the selected card's
 * frame, the card being moved or pinched (over the page, following the fingers, lifted), the soft outline where it will
 * land, the cards under it making room, and the lines it lines up with.
 *
 * NG Designs
 */

/** The space between cards, as the page's column has always had. */
private val GAP = 10.dp

/** How long a press is held before a card lifts: longer while browsing, quicker while editing. */
private const val LONG_PRESS_MS = 420L
private const val EDIT_HOLD_MS = 220L

/** A card let go less than this far (dp) under the one above it sits right against it. */
private val STICK = 40.dp

/** How long a selected card can be off the page before it is let go. */
private const val GONE_MS = 800L

/** A box card's height before it has one of its own. */
private const val BOX_DEFAULT_DP = 170f

/** How far the frame stands off the card it selects. */
private val FRAME_OUTSET = 5.dp

/** The layout id of a card. */
private data class CardKey(val id: String)

/** The layout ids of the page's edit-mode parts. */
private enum class Part { DROP, FRAME, GUIDES }

/** The layout id of one of the selected card's grips. */
private data class GripKey(val grip: Grip)

/** A grip's touch area, and how far its middle sits in from the card's bottom (and, for the corner, its right) edge. */
private val GRIP_TOUCH = 48.dp
private val GRIP_INSET = 14.dp

/**
 * Discover's cards in [layout] order, where the user put them across the page, falling up against the cards above them,
 * inside the page's scrolling column ([scroll]). Cards [shown] says aren't on the page today are left out, taking no
 * space. [card] draws a card's content for its mode at the size its modifier gives: as wide as its place and as tall as
 * its content for a full card, filling its place otherwise. With every card full width (the default but two), it lays
 * out as the page's column always has.
 *
 * Long-pressing a card starts editing and lifts it to be moved. While editing, a tap selects a card, a drag on the
 * selected card moves it at once, and two fingers pinch any card to size. [minShape] is the least side (dp) a card's
 * shape reads at, [label] names a card for TalkBack, and every change goes to [onLayoutChange].
 */
@Composable
internal fun DiscoverGrid(
    state: DiscoverEditState,
    layout: DiscoverLayout,
    onLayoutChange: (DiscoverLayout) -> Unit,
    shown: (CardSpec) -> Boolean,
    minShape: (CardSpec) -> Float,
    label: (CardSpec) -> String,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    card: @Composable (spec: CardSpec, modifier: Modifier) -> Unit
) {
    // The page's layout wins whenever it changes; between changes, the edits made since (sent but not back yet) stand.
    val received = remember { arrayOfNulls<DiscoverLayout>(1) }
    if (received[0] != layout) {
        received[0] = layout
        state.layout = layout
    }
    state.onLayoutChange = onLayoutChange
    state.shown = shown
    state.minShape = minShape
    state.label = label
    state.scroll = scroll
    val editing = state.editing
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { PlacementMotion(scope) }
    val active = state.active

    val onPage = layout.cards.filter(shown).distinctBy { it.id }
    val order = onPage.withIndex().associate { it.value.id to it.index }
    val selected = state.selectedId?.takeIf { id -> editing && onPage.any { it.id == id } }
    // A selected card that leaves the page (switched off, or its app or website is gone) is let go: Back would be spent on nothing.
    // Not at once: a card can be off the page for a moment as it changes form (its shape's content is still being read).
    val gone = editing && state.selectedId != null && selected == null
    LaunchedEffect(gone) {
        if (!gone) return@LaunchedEffect
        delay(GONE_MS)
        if (state.active == null) state.select(null)
    }

    NewCards(state, layout)
    Reveal(state, scroll)
    AutoScroll(state, scroll)

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { state.gridCoords = it }
            .semantics { isTraversalGroup = true }
            .pointerInput(state) { cardGestures(state) },
        content = {
            if (active != null) {
                val spec = onPage.firstOrNull { it.id == active.id }
                Spacer(Modifier.layoutId(Part.DROP).landingOutline(spec))
            }
            // Composed in a fixed order, so moving a card never moves its node (or its content's state) in the tree.
            for (spec in onPage.sortedBy { it.id }) {
                key(spec.id) {
                    CardHost(state, spec, order.getValue(spec.id), spec.id == selected, editing, active?.id == spec.id, card)
                }
            }
            if (selected != null && active?.id != selected) {
                val spec = onPage.first { it.id == selected }
                Spacer(Modifier.layoutId(Part.FRAME).selectionFrame(spec))
            }
            if (active != null) Spacer(Modifier.layoutId(Part.GUIDES).guides(state, active))
            // Kept while their own drag is on, so the finger holding one stays with it; hidden while the card moves.
            if (selected != null) {
                val spec = onPage.first { it.id == selected }
                val held = active?.takeIf { it.id == selected }
                val show = held == null || held.grip != null
                if (spec.mode != CardMode.SHAPED) GripHandle(state, selected, Grip.HEIGHT, show && held?.grip?.grip != Grip.CORNER, Modifier.layoutId(GripKey(Grip.HEIGHT)))
                GripHandle(state, selected, Grip.CORNER, show && held?.grip?.grip != Grip.HEIGHT, Modifier.layoutId(GripKey(Grip.CORNER)))
            }
        }
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val gap = GAP.roundToPx()
        val page = width + gap
        state.page = page
        state.gap = gap
        val cardMeasurables = HashMap<String, Measurable>()
        val parts = HashMap<Part, Measurable>()
        for (measurable in measurables) {
            when (val id = measurable.layoutId) {
                is CardKey -> cardMeasurables[id.id] = measurable
                is Part -> parts[id] = measurable
            }
        }
        val live = state.active
        val placeables = HashMap<String, Placeable>()
        val flowCards = ArrayList<FlowCard>()
        for (spec in onPage) {
            val measurable = cardMeasurables[spec.id] ?: continue
            val isLive = live?.id == spec.id
            val slotWidth = if (isLive) live.rect.width.roundToInt() else (spec.frame.width * page).roundToInt()
            val drawnWidth = (slotWidth - gap).coerceIn(1, width.coerceAtLeast(1))
            val placeable = when (spec.mode) {
                CardMode.NATURAL -> {
                    // A full card is as tall as its content, or as tall as the user made it.
                    val height = when {
                        isLive && live.autoHeight -> 0
                        isLive -> live.rect.height.roundToInt() - gap
                        spec.frame.height > 0f -> spec.frame.height.dp.roundToPx()
                        else -> 0
                    }
                    if (height > 0) {
                        measurable.measure(Constraints(minWidth = drawnWidth, maxWidth = drawnWidth, minHeight = height, maxHeight = height))
                    } else {
                        measurable.measure(Constraints(minWidth = drawnWidth, maxWidth = drawnWidth)).also {
                            if (!isLive) state.naturalHeights[spec.id] = it.height + gap
                        }
                    }
                }
                CardMode.SHAPED -> measurable.measure(Constraints.fixed(drawnWidth, drawnWidth))
                CardMode.BOX -> {
                    val height = if (isLive) live.rect.height.roundToInt() - gap else (spec.frame.height.takeIf { it > 0f } ?: BOX_DEFAULT_DP).dp.roundToPx()
                    measurable.measure(Constraints.fixed(drawnWidth, height.coerceAtLeast(1)))
                }
            }
            placeables[spec.id] = placeable
            val slotHeight = placeable.height + gap
            val left = if (isLive) live.rect.left.roundToInt() else (spec.frame.x * page).roundToInt().coerceIn(0, (page - slotWidth).coerceAtLeast(0))
            if (isLive) live.measured = slotHeight
            flowCards += FlowCard(spec.id, left, slotWidth, slotHeight, spec.frame.gap.dp.roundToPx())
        }
        val arranged = arrange(flowCards, live?.id, live?.rect?.top?.roundToInt() ?: 0, STICK.roundToPx(), live?.reference)
        state.arranged = arranged
        state.flowCards = flowCards
        state.stick = STICK.roundToPx()
        if (state.slots != arranged.rects) state.slots = arranged.rects
        motion.retain(placeables.keys)

        val height = (arranged.height - gap).coerceAtLeast(0)
        val outset = FRAME_OUTSET.roundToPx()
        val selectedRect = selected?.let { arranged.rects[it] }
        val frame = parts[Part.FRAME]?.let { m ->
            selectedRect?.let { m.measure(Constraints.fixed(it.width - gap + 2 * outset, it.height - gap + 2 * outset)) }
        }
        val landing = live?.let { arranged.rects[it.id] }
        val drop = parts[Part.DROP]?.let { m -> landing?.let { m.measure(Constraints.fixed(it.width - gap, it.height - gap)) } }
        val guides = parts[Part.GUIDES]?.measure(Constraints.fixed(width, height))
        val touch = GRIP_TOUCH.roundToPx()
        val grips = Grip.entries.mapNotNull { grip -> measurables.firstOrNull { it.layoutId == GripKey(grip) }?.let { grip to it.measure(Constraints.fixed(touch, touch)) } }
        val landed = state.landed
        state.landed = null

        layout(width, height) {
            if (drop != null && landing != null) drop.place(landing.left, landing.top)
            for (spec in onPage) {
                val placeable = placeables[spec.id] ?: continue
                val rect = arranged.rects[spec.id] ?: continue
                if (live?.id == spec.id) {
                    motion.forget(spec.id)
                    placeable.place(live.rect.left.roundToInt(), live.rect.top.roundToInt(), zIndex = 10f)
                } else {
                    if (landed?.first == spec.id) motion.jump(spec.id, IntOffset(landed.second.x.roundToInt(), landed.second.y.roundToInt()))
                    val moving = editing && state.animate
                    placeable.place(motion.at(spec.id, IntOffset(rect.left, rect.top), moving), zIndex = if (landed?.first == spec.id) 5f else 0f)
                }
            }
            if (frame != null && selectedRect != null) {
                frame.place(motion.peek(selected) - IntOffset(outset, outset), zIndex = 4f)
            }
            guides?.place(0, 0, zIndex = 11f)
            // The grips sit inside the selected card's bottom edge (where it is drawn now, following a grip's drag).
            val gripCard = selected?.let { id ->
                live?.takeIf { it.id == id }?.rect?.let { IntRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()) }
                    ?: arranged.rects[id]?.let { IntRect(motion.peek(id), IntSize(it.width, it.height)) }
            }
            val inset = GRIP_INSET.roundToPx()
            val half = gap / 2f
            val placed = HashMap<Grip, Rect>()
            if (gripCard != null) {
                // On a narrow card the bar steps left of the corner, so each keeps a whole touch area of its own.
                val cornerX = gripCard.right - gap - inset
                for ((grip, placeable) in grips) {
                    val cx = if (grip == Grip.CORNER) cornerX else min(gripCard.left + (gripCard.width - gap) / 2, cornerX - touch)
                    val cy = gripCard.bottom - gap - inset
                    placeable.place(cx - touch / 2, cy - touch / 2, zIndex = 12f)
                    placed[grip] = Rect(cx - touch / 2f + half, cy - touch / 2f + half, cx + touch / 2f + half, cy + touch / 2f + half)
                }
            }
            state.grips = placed
        }
    }
}

/**
 * Cards glide to their new places while editing (at once otherwise, or with animations off), each from wherever it
 * was drawn when its place changed.
 */
private class PlacementMotion(private val scope: CoroutineScope) {
    private val anims = HashMap<String, Animatable<IntOffset, AnimationVector2D>>()
    private val targets = HashMap<String, IntOffset>()

    /** Where to draw [key] on its way to [target]. */
    fun at(key: String, target: IntOffset, animate: Boolean): IntOffset {
        if (!animate) {
            anims.remove(key)
            targets[key] = target
            return target
        }
        val anim = anims.getOrPut(key) { Animatable(targets[key] ?: target, IntOffset.VectorConverter) }
        val before = targets.put(key, target)
        if (before != target || anim.targetValue != target) {
            scope.launch { anim.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 520f, visibilityThreshold = IntOffset(1, 1))) }
        }
        return anim.value
    }

    /** Where [key] is drawn now. */
    fun peek(key: String): IntOffset = anims[key]?.value ?: targets[key] ?: IntOffset.Zero

    /** Starts [key] from [from], so it glides from there to its place. */
    fun jump(key: String, from: IntOffset) {
        anims[key] = Animatable(from, IntOffset.VectorConverter)
        targets.remove(key)
    }

    /** Forgets [key], so it next shows where it is at once. */
    fun forget(key: String) {
        anims.remove(key)
        targets.remove(key)
    }

    /** Forgets cards no longer on the page. */
    fun retain(keys: Set<String>) {
        anims.keys.retainAll(keys)
        targets.keys.retainAll(keys)
    }
}

/** Selects and shows a card added while editing (from a widget or a website), once it is on the page. */
@Composable
private fun NewCards(state: DiscoverEditState, layout: DiscoverLayout) {
    val known = remember { HashSet<String>() }
    val ids = layout.cards.map { it.id }
    LaunchedEffect(ids) {
        val fresh = ids.filter { it !in known }
        if (state.editing && known.isNotEmpty() && fresh.size == 1) {
            state.select(fresh.first())
            state.reveal = fresh.first()
        }
        known.clear()
        known.addAll(ids)
    }
}

/** Scrolls the page to bring the card to show ([DiscoverEditState.reveal]) to the middle of the screen. */
@Composable
private fun Reveal(state: DiscoverEditState, scroll: ScrollState) {
    val target = state.reveal ?: return
    LaunchedEffect(target) {
        // Laid out first.
        withFrameNanos { }
        withFrameNanos { }
        val rect = state.slots[target]
        val grid = state.gridCoords?.takeIf { it.isAttached }
        val view = state.chromeCoords?.takeIf { it.isAttached }
        if (rect != null && grid != null && view != null) {
            val middle = grid.positionInRoot().y + (rect.top + rect.bottom) / 2f
            val delta = middle - (view.positionInRoot().y + view.size.height / 2f)
            if (state.animate) scroll.animateScrollBy(delta) else scroll.dispatchRawDelta(delta)
        }
        if (state.reveal == target) state.reveal = null
    }
}

/**
 * While a card is moved near the top or bottom of the screen, the page scrolls under it, faster nearer the edge. Not
 * while it is pinched or sized by a grip: the page scrolling under a grip would go on growing the card with the finger
 * held still.
 */
@Composable
private fun AutoScroll(state: DiscoverEditState, scroll: ScrollState) {
    val moving = state.active?.takeIf { !it.pinching && it.grip == null }
    val current by rememberUpdatedState(moving)
    LaunchedEffect(moving?.id) {
        if (moving == null) return@LaunchedEffect
        val edge = with(state.density) { 96.dp.toPx() }
        val fastest = with(state.density) { 14.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val card = current ?: break
            if (card.pinching) continue
            val grid = state.gridCoords?.takeIf { it.isAttached } ?: continue
            val view = state.chromeCoords?.takeIf { it.isAttached } ?: continue
            val top = view.positionInRoot().y + state.topBarBottom.coerceAtLeast(0f) - view.positionInRoot().y
            val bottom = view.size.height.toFloat() - with(state.density) { 110.dp.toPx() }
            val y = grid.positionInRoot().y - view.positionInRoot().y + card.rect.center.y
            val speed = when {
                y < top + edge -> -fastest * ((top + edge - y) / edge).coerceIn(0f, 1f)
                y > bottom - edge -> fastest * ((y - bottom + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                val moved = scroll.dispatchRawDelta(speed)
                if (moved != 0f) state.shift(moved)
            }
        }
    }
}

/** What the first moments of a press on a card turned out to be. */
private enum class Press { TAP, HOLD, MOVE, PINCH, OTHER }

/**
 * The page's gestures, watched on the way down (the initial pass), before the cards' own buttons and the page's
 * scrolling, and taking nothing from them until a card is held or pinched:
 *
 * - a press held still lifts the card (starting edit mode if it isn't on) and moves it until the finger lifts;
 * - while editing, a drag on the selected card moves it at once, a tap selects a card, a tap between cards lets the
 *   selected one go, and a drag on any other card scrolls the page;
 * - two fingers on a card pinch it to size (starting edit mode), the middle between them moving it too; lifting one
 *   goes on moving it with the other.
 */
private suspend fun PointerInputScope.cardGestures(state: DiscoverEditState) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val half = state.gap / 2f
        fun onPage(p: Offset) = p + Offset(half, half)
        // A press on a grip is the grip's own.
        if (state.editing && state.grips.values.any { it.contains(onPage(down.position)) }) return@awaitEachGesture
        val id = state.cardAt(onPage(down.position))
        val editing = state.editing
        if (id == null) {
            if (!editing) return@awaitEachGesture
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                if ((change.position - down.position).getDistance() > slop) return@awaitEachGesture
                if (!change.pressed) {
                    if (state.active == null) state.select(null)
                    return@awaitEachGesture
                }
            }
        }
        val immediate = editing && state.selectedId == id
        var last = down.position
        val press = withTimeoutOrNull(if (editing) EDIT_HOLD_MS else LONG_PRESS_MS) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) {
                    // While browsing nothing is taken yet: the two fingers must really pinch first.
                    if (editing) event.changes.forEach { it.consume() }
                    return@withTimeoutOrNull Press.PINCH
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Press.OTHER
                if (!change.pressed) return@withTimeoutOrNull Press.TAP
                if (change.isConsumed) return@withTimeoutOrNull Press.OTHER
                last = change.position
                if ((change.position - down.position).getDistance() > slop) {
                    if (!immediate) return@withTimeoutOrNull Press.OTHER
                    change.consume()
                    return@withTimeoutOrNull Press.MOVE
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Press.OTHER
        } ?: Press.HOLD
        when (press) {
            Press.TAP -> {
                if (editing) state.select(id)
                return@awaitEachGesture
            }
            Press.OTHER -> return@awaitEachGesture
            Press.HOLD -> {
                if (!state.editing) state.enter(id)
                state.beginMove(id, onPage(last))
            }
            Press.MOVE -> {
                state.beginMove(id, onPage(down.position))
                state.moveTo(onPage(last))
            }
            Press.PINCH -> {
                if (!state.editing) {
                    if (!pinchBegins(slop)) return@awaitEachGesture
                    state.enter(id)
                }
                state.select(id)
            }
        }
        try {
            var pair: Pair<Long, Long>? = null
            var single: Long? = if (press == Press.PINCH) null else down.id.value
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    val a = pressed[0]
                    val b = pressed[1]
                    val ids = a.id.value to b.id.value
                    if (pair != ids) {
                        pair = ids
                        single = null
                        state.beginPinch(id, onPage(a.position), onPage(b.position))
                    } else {
                        state.pinchTo(onPage(a.position), onPage(b.position))
                    }
                } else {
                    val p = pressed[0]
                    if (single != p.id.value) {
                        single = p.id.value
                        pair = null
                        state.beginMove(id, onPage(p.position))
                    } else {
                        state.moveTo(onPage(p.position))
                    }
                }
            }
        } finally {
            if (state.active?.id == id) state.end()
        }
    }
}

/**
 * While browsing, two fingers on a card start an edit only once they really pinch: each has moved more than [slop],
 * against the other, and the space between them has changed by more than [slop]. A second finger resting on the page
 * hasn't moved, and two scrolling it together move the same way, so neither changes anything or takes anything from the
 * page. A pinch made with one finger held still isn't told from that resting finger, so it starts no edit here either: a
 * long press does, and once editing every pinch sizes at once. False when a finger lifts first.
 */
private suspend fun AwaitPointerEventScope.pinchBegins(slop: Float): Boolean {
    var event = currentEvent
    val from = HashMap<Long, Offset>()
    var first = -1f
    while (true) {
        val pressed = event.changes.filter { it.pressed }
        if (pressed.size < 2) return false
        val a = pressed[0]
        val b = pressed[1]
        val spread = (a.position - b.position).getDistance()
        if (first < 0f) first = spread
        val movedA = a.position - from.getOrPut(a.id.value) { a.position }
        val movedB = b.position - from.getOrPut(b.id.value) { b.position }
        val against = movedA.x * movedB.x + movedA.y * movedB.y < 0f
        if (abs(spread - first) > slop && movedA.getDistance() > slop && movedB.getDistance() > slop && against) {
            event.changes.forEach { it.consume() }
            return true
        }
        event = awaitPointerEvent(PointerEventPass.Initial)
    }
}

/**
 * One card on the page: [card]'s content. While [editing] the content takes no touches (the page's gestures arrange
 * it), TalkBack hears the card whole with every edit as an action, and keys move and resize it. While [lifted] it rises
 * a little, with a shadow in its own shape.
 */
@Composable
private fun CardHost(
    state: DiscoverEditState,
    spec: CardSpec,
    index: Int,
    selected: Boolean,
    editing: Boolean,
    lifted: Boolean,
    card: @Composable (spec: CardSpec, modifier: Modifier) -> Unit
) {
    val id = spec.id
    val page = MaterialTheme.colorScheme.surface
    DisposableEffect(id) {
        onDispose { if (state.active?.id == id) state.cancel() }
    }
    val lift by animateFloatAsState(
        if (lifted) 1f else 0f,
        if (state.animate) spring(dampingRatio = 0.6f, stiffness = 600f) else tween(0),
        label = "card lift"
    )
    val outline = remember(spec.mode, spec.shape) {
        if (spec.mode == CardMode.SHAPED) CardOutlineShape(spec.shape) else RoundedCornerShape(24.dp)
    }

    val semantics = if (editing) {
        Modifier.clearAndSetSemantics {
            contentDescription = state.describe(spec)
            this.selected = selected
            traversalIndex = index.toFloat()
            onClick(label = "Select") {
                state.select(id)
                true
            }
            customActions = state.spec(id)?.let { current ->
                state.actions(current).filter { it.enabled }.map { action ->
                    CustomAccessibilityAction(action.label) {
                        action.run()
                        true
                    }
                } + CustomAccessibilityAction("All card actions") {
                    state.select(id)
                    state.open(EditSheet.ACTIONS)
                    true
                }
            } ?: emptyList()
        }
    } else {
        // A full card and an app's live widget are named here, over their own parts. Every other card (a shape, a
        // website's box, a covered private card) is one button that carries the name itself: named here too, TalkBack
        // would stop twice, on the name and then on a button with none.
        val named = (spec.mode == CardMode.NATURAL || (spec.mode == CardMode.BOX && spec.kind == CardKind.APP)) && !spec.private
        Modifier.semantics {
            traversalIndex = index.toFloat()
            if (named) {
                contentDescription = state.label(spec)
                customActions = listOf(
                    CustomAccessibilityAction("Edit Discover") {
                        state.enter(id)
                        true
                    }
                )
            }
        }
    }

    Box(
        propagateMinConstraints = true,
        modifier = Modifier
            .layoutId(CardKey(id))
            .then(semantics)
            .graphicsLayer {
                val s = 1f + 0.035f * lift
                scaleX = s
                scaleY = s
                shadowElevation = 16.dp.toPx() * lift
                shape = outline
            }
            // Lifted over other cards, it is solid: the page's see-through cards would show what is under it.
            .then(if (lifted || lift > 0f) Modifier.background(page, outline) else Modifier)
            .then(
                if (editing) {
                    Modifier
                        .onPreviewKeyEvent { event ->
                            event.type == KeyEventType.KeyDown && cardKey(state, id, event.key, event.isAltPressed, event.isShiftPressed)
                        }
                        // The card a keyboard is on is the selected one, so its frame shows where the keys will act.
                        .onFocusChanged { if (it.isFocused && state.selectedId != id) state.select(id) }
                        .focusable()
                } else Modifier
            )
    ) {
        // While editing a keyboard stops on the card, not on the buttons inside it, which take no touches either: a
        // group that refuses focus, as turning focus off reaches only the first button down, not the ones inside it.
        val quiet = if (editing) Modifier.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup() else Modifier
        if (spec.mode == CardMode.NATURAL) {
            val least = with(LocalDensity.current) { spec.kind.minCardDp.dp.roundToPx() }
            // Cut short, a card's content says how tall it would be, so its height can snap back to fitting it.
            FitHeight({ if (state.active?.id != id) state.naturalHeights[id] = it + state.gap }) {
                ShrinkToFit(least) { card(spec, Modifier.fillMaxWidth().then(quiet)) }
            }
        } else {
            card(spec, Modifier.fillMaxSize().then(quiet))
        }
        if (editing) {
            // While editing, the card's own buttons take no touches: the card is being arranged, not used.
            Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        }
    }
}

/**
 * A full card's content at the height the page gives it: as tall as itself when the height is free; stretched to a
 * height of its own when that is taller (the card's surface grows with it); and when that is shorter, cut to the card's
 * rounded shape with its bottom fading out, so it never ends on half a line, and [onCut] is told how tall it would be.
 */
@Composable
private fun FitHeight(onCut: (Int) -> Unit, content: @Composable () -> Unit) {
    val cut = remember { androidx.compose.runtime.mutableStateOf(false) }
    Layout(
        content = content,
        modifier = Modifier
            .graphicsLayer { compositingStrategy = if (cut.value) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
                if (!cut.value) {
                    drawContent()
                    return@drawWithContent
                }
                val fade = 40.dp.toPx().coerceAtMost(size.height / 2f)
                val r = 24.dp.toPx()
                clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }) {
                    this@drawWithContent.drawContent()
                }
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
                    topLeft = Offset(0f, size.height - fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn
                )
            }
    ) { measurables, constraints ->
        // Nothing yet while the page builds its cards one by one as it opens.
        val child = measurables.firstOrNull() ?: return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        if (!constraints.hasBoundedHeight || constraints.minHeight != constraints.maxHeight) {
            if (cut.value) cut.value = false
            val placeable = child.measure(constraints)
            return@Layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val height = constraints.maxHeight
        val placeable = child.measure(constraints.copy(minHeight = height, maxHeight = Constraints.Infinity))
        val short = placeable.height > height
        if (cut.value != short) cut.value = short
        if (short) onCut(placeable.height)
        layout(constraints.maxWidth, height) { placeable.place(0, 0) }
    }
}

/**
 * A full card's design at any width: as it is from its own least width ([least], pixels) up, and below that laid out at
 * its least width and shrunk as a whole to fit, as a thumbnail of itself, so a card can be far narrower than its design
 * and still show all of it. Heights asked of it are in the card's own pixels.
 */
@Composable
private fun ShrinkToFit(least: Int, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val child = measurables.firstOrNull() ?: return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val width = constraints.maxWidth
        if (!constraints.hasBoundedWidth || width >= least || width <= 0) {
            val placeable = child.measure(constraints)
            return@Layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val scale = width.toFloat() / least
        fun inner(h: Int) = if (h == Constraints.Infinity) h else (h / scale).toInt()
        val placeable = child.measure(
            Constraints(minWidth = least, maxWidth = least, minHeight = inner(constraints.minHeight), maxHeight = inner(constraints.maxHeight))
        )
        layout(width, (placeable.height * scale).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/**
 * One of the selected card's grips, inside its bottom edge: a short bar in the middle that sizes its height alone, and
 * a corner at its bottom right that sizes its width and height together. Each takes its own drag, kept clear of
 * Android's back gesture so a grip near the screen's edge never goes back. Drawn only when [visible]. TalkBack isn't
 * offered them: a grip does nothing when tapped, and the card's own actions (Make taller, Make shorter, Make bigger,
 * Make smaller) size it without a drag.
 */
@Composable
private fun GripHandle(state: DiscoverEditState, id: String, grip: Grip, visible: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val me = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    Box(
        modifier
            .onGloballyPositioned { me[0] = it }
            .systemGestureExclusion()
            .clearAndSetSemantics { }
            .pointerInput(id, grip) {
                awaitEachGesture {
                    // Only a press of its own: a finger of a pinch or a move that lands here is theirs, already taken.
                    val down = awaitFirstDown()
                    down.consume()
                    fun onPage(p: Offset): Offset? {
                        val grid = state.gridCoords?.takeIf { it.isAttached } ?: return null
                        val self = me[0]?.takeIf { it.isAttached } ?: return null
                        val half = state.gap / 2f
                        return grid.localPositionOf(self, p) + Offset(half, half)
                    }
                    val start = onPage(down.position) ?: return@awaitEachGesture
                    state.beginGrip(id, grip, start)
                    if (state.active?.grip == null) return@awaitEachGesture
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            onPage(change.position)?.let(state::gripTo)
                        }
                    } finally {
                        if (state.active?.grip != null) state.end()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            val color = scheme.primary
            val back = scheme.surface
            Spacer(
                Modifier
                    .size(if (grip == Grip.HEIGHT) 30.dp else 22.dp)
                    .drawBehind {
                        val stroke = 4.dp.toPx()
                        if (grip == Grip.HEIGHT) {
                            val y = size.height / 2f
                            drawLine(back, Offset(0f, y), Offset(size.width, y), stroke + 3.dp.toPx(), StrokeCap.Round)
                            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
                        } else {
                            // A rounded corner, as the card's own: the arc of its bottom right.
                            val path = Path().apply {
                                moveTo(size.width, 0f)
                                quadraticTo(size.width, size.height, 0f, size.height)
                            }
                            drawPath(path, back, style = Stroke(stroke + 3.dp.toPx(), cap = StrokeCap.Round))
                            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
            )
        }
    }
}

/**
 * A key pressed on a card while editing: Alt and an arrow move it, plus and minus make it wider and narrower (with Alt,
 * taller and shorter), the menu key opens its actions.
 */
private fun cardKey(state: DiscoverEditState, id: String, key: Key, alt: Boolean, shift: Boolean): Boolean {
    when {
        alt && key == Key.DirectionUp -> state.move(id, -1)
        alt && key == Key.DirectionDown -> state.move(id, 1)
        alt && key == Key.DirectionLeft -> state.nudge(id, -1)
        alt && key == Key.DirectionRight -> state.nudge(id, 1)
        alt && (key == Key.Plus || key == Key.Equals || key == Key.NumPadAdd) -> state.stretch(id, 1)
        alt && (key == Key.Minus || key == Key.NumPadSubtract) -> state.stretch(id, -1)
        key == Key.Plus || key == Key.Equals || key == Key.NumPadAdd -> state.resize(id, 1)
        key == Key.Minus || key == Key.NumPadSubtract -> state.resize(id, -1)
        key == Key.Enter || key == Key.Spacebar -> state.select(id)
        key == Key.Escape -> state.exit()
        key == Key.Menu || (shift && key == Key.F10) -> {
            state.select(id)
            state.open(EditSheet.ACTIONS)
        }
        else -> return false
    }
    return true
}

/** The selected card's frame: its own shape (a rounded box round a full card), a little larger, in the accent color. */
@Composable
private fun Modifier.selectionFrame(spec: CardSpec): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val shaped = spec.mode == CardMode.SHAPED
    val name = spec.shape
    return drawBehind {
        val stroke = 2.dp.toPx()
        if (shaped) {
            val side = min(size.width, size.height) - stroke
            val path = Path()
            CardShapes.outline(name).addTo(path, side, (size.width - side) / 2f, (size.height - side) / 2f)
            drawPath(path, color, style = Stroke(stroke))
        } else {
            drawRoundRect(
                color,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(28.dp.toPx()),
                style = Stroke(stroke)
            )
        }
    }
}

/** Where the card being moved will land: a soft, dashed outline in its shape. */
@Composable
private fun Modifier.landingOutline(spec: CardSpec?): Modifier {
    val scheme = MaterialTheme.colorScheme
    val fill = scheme.onSurface.copy(alpha = 0.06f)
    val line = scheme.onSurface.copy(alpha = 0.28f)
    val shaped = spec?.mode == CardMode.SHAPED
    val name = spec?.shape ?: "circle"
    return drawBehind {
        val stroke = 1.5.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
        if (shaped) {
            val side = min(size.width, size.height) - stroke
            val path = Path()
            CardShapes.outline(name).addTo(path, side, (size.width - side) / 2f, (size.height - side) / 2f)
            drawPath(path, fill)
            drawPath(path, line, style = Stroke(stroke, pathEffect = dash))
        } else {
            val r = CornerRadius(24.dp.toPx())
            drawRoundRect(fill, cornerRadius = r)
            drawRoundRect(line, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), r, style = Stroke(stroke, pathEffect = dash))
        }
    }
}

/** The lines the card being moved lines up with, down the whole page. */
@Composable
private fun Modifier.guides(state: DiscoverEditState, active: ActiveCard): Modifier {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    return drawBehind {
        val half = state.gap / 2f
        val stroke = 1.dp.toPx()
        for (x in active.guides) {
            // The page's own edges need no line: the card is plainly against them.
            if (x <= 0 || x >= state.page) continue
            val at = (x - half).coerceIn(0f, size.width)
            translate(at, 0f) { drawLine(color, Offset(0f, 0f), Offset(0f, size.height), stroke) }
        }
    }
}
