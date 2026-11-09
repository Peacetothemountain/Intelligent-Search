package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.feature.search.today.cards.CardSize
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.GRID_COLUMNS
import com.pixel.intelligentsearch.feature.search.today.cards.GRID_MAX_ROWS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Discover's grid: the page's cards laid out in reading order (GridPacking), and, in edit mode, the selected card's
 * frame, toolbar and resize handles, the soft outline where a held card will land, and the frame that follows a
 * resize. A card held under the finger is drawn over the page by DiscoverEditChrome, from a layer this grid records.
 *
 * NG Designs
 */

/** The space between cards, as the page's column has always had. */
private val GAP = 10.dp

/** How long a press is held before the card lifts. */
private const val LONG_PRESS_MS = 450L

/** How far the finger may move before a press stops counting as a long-press. */
private val PRESS_SLOP = 8.dp

/** How far the frame stands off the card it selects. */
private val FRAME_OUTSET = 6.dp

/** The touch area of a resize handle. */
private val HANDLE_TOUCH = 46.dp

/** The layout id of a card. */
private data class CardKey(val id: String)

/** The layout ids of the grid's edit-mode parts. */
private enum class Part { DROP, FRAME, TOOLS, RESIZE }

/** The layout id of a resize handle. */
private data class HandleKey(val edge: Edge)

/**
 * Discover's cards in [layout] order, laid out on a 4-across grid of square cells, inside the page's scrolling column
 * ([scroll]). Cards [shown] says aren't on the page are left out, taking no space and no gap. [card] draws a card's
 * content for its mode, at the size its modifier gives: as wide as its place and as tall as its content for a card
 * as tall as its content, filling its cells otherwise. With every card full width and as tall as its content (the
 * default), it lays out exactly as the page's column always has.
 *
 * Long-pressing a card starts editing and lifts it to be moved. While editing, a tap selects a card; [fits] says
 * whether a card is still readable at a size, [label] names it for TalkBack, and every change goes to
 * [onLayoutChange].
 */
@Composable
internal fun DiscoverGrid(
    state: DiscoverEditState,
    layout: DiscoverLayout,
    onLayoutChange: (DiscoverLayout) -> Unit,
    shown: (CardSpec) -> Boolean,
    fits: (CardSpec, CardSize) -> Boolean,
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
    state.fits = fits
    state.label = label
    state.scroll = scroll
    val editing = state.editing
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { PlacementMotion(scope) }

    val onPage = layout.cards.filter(shown).distinctBy { it.id }
    val order = onPage.withIndex().associate { it.value.id to it.index }
    val selected = state.selectedId?.takeIf { id -> editing && onPage.any { it.id == id } }
    val held = state.drag
    val resizing = state.resize

    NewCards(state, layout)
    Reveal(state, scroll)

    Layout(
        modifier = modifier
            .onGloballyPositioned { state.gridCoords = it }
            .semantics { isTraversalGroup = true }
            .pointerInput(state) { deselectOnBlankTap(state) },
        content = {
            if (held != null && !held.released) {
                Box(
                    Modifier
                        .layoutId(Part.DROP)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f), RoundedCornerShape(28.dp))
                )
            }
            // Composed in a fixed order, so moving a card never moves its node (or its gesture) in the tree.
            for (spec in onPage.sortedBy { it.id }) {
                key(spec.id) {
                    CardHost(state, spec, order.getValue(spec.id), spec.id == selected, editing, card)
                }
            }
            if (selected != null) {
                val spec = onPage.first { it.id == selected }
                SelectionParts(state, spec, order.getValue(selected), resizing != null, held != null)
            }
            if (resizing != null) ResizeFrame(resizing, Modifier.layoutId(Part.RESIZE))
        }
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val metrics = GridMetrics(width, GAP.roundToPx())
        val cardMeasurables = HashMap<String, Measurable>()
        val parts = HashMap<Any, Measurable>()
        for (measurable in measurables) {
            when (val id = measurable.layoutId) {
                is CardKey -> cardMeasurables[id.id] = measurable
                null -> Unit
                else -> parts[id] = measurable
            }
        }
        val cards = layout.cards
        val seen = HashSet<String>()
        val slots = packSlots(cards.map { if (it.id in cardMeasurables && seen.add(it.id)) it.size else null })
        val placeables = arrayOfNulls<Placeable>(cards.size)
        slots.forEachIndexed { i, slot ->
            if (slot == null) return@forEachIndexed
            val measurable = cardMeasurables.getValue(cards[i].id)
            placeables[i] = when (slot.type) {
                BlockType.CELLS -> measurable.measure(
                    Constraints.fixed(metrics.span(slot.column, slot.columns), metrics.span(slot.row, slot.rows))
                )
                BlockType.FULL -> measurable.measure(Constraints(maxWidth = width))
                BlockType.BAND -> measurable.measure(Constraints(maxWidth = metrics.span(slot.column, slot.columns)))
            }
        }
        val placement = placeSlots(slots, { placeables[it]?.height ?: 0 }, metrics)
        val rects = HashMap<String, IntRect>()
        val heights = HashMap<String, Int>()
        cards.forEachIndexed { i, spec ->
            val rect = placement.rects[i] ?: return@forEachIndexed
            rects[spec.id] = rect
            if (slots[i]?.type != BlockType.CELLS) heights[spec.id] = placeables[i]?.height ?: 0
        }
        state.metrics = metrics
        state.rects = rects
        state.heights = heights
        state.spots = placement.spots

        val outset = FRAME_OUTSET.roundToPx()
        val selectedRect = selected?.let { rects[it] }
        val dropRect = held?.let { rects[it.id] }
        val drop = parts[Part.DROP]?.let { m -> dropRect?.let { m.measure(Constraints.fixed(it.width, it.height)) } }
        val frame = parts[Part.FRAME]?.let { m ->
            selectedRect?.let { m.measure(Constraints.fixed(it.width + 2 * outset, it.height + 2 * outset)) }
        }
        val tools = parts[Part.TOOLS]?.measure(Constraints())
        val touch = HANDLE_TOUCH.roundToPx()
        val handles = Edge.entries.mapNotNull { edge -> parts[HandleKey(edge)]?.let { edge to it.measure(Constraints.fixed(touch, touch)) } }
        val resizeRect = resizing?.let { it.settling?.value ?: it.frame }
        val resize = parts[Part.RESIZE]?.let { m ->
            resizeRect?.let {
                m.measure(Constraints.fixed((it.width + 2 * outset).toInt().coerceAtLeast(0), (it.height + 2 * outset).toInt().coerceAtLeast(0)))
            }
        }
        val animateMoves = editing && state.animate
        motion.retain(rects.keys)

        layout(width, placement.height) {
            if (drop != null && dropRect != null) {
                drop.place(motion.at(Part.DROP, IntOffset(dropRect.left, dropRect.top), animateMoves, 240))
            } else {
                // The next card lifted shows its outline where it is at once, not gliding from the last one's.
                motion.forget(Part.DROP)
            }
            cards.forEachIndexed { i, spec ->
                val placeable = placeables[i] ?: return@forEachIndexed
                val rect = placement.rects[i] ?: return@forEachIndexed
                placeable.place(motion.at(spec.id, IntOffset(rect.left, rect.top), animateMoves, 300))
            }
            if (selectedRect != null) {
                frame?.place(selectedRect.left - outset, selectedRect.top - outset)
                if (tools != null) {
                    val x = (selectedRect.center.x - tools.width / 2).coerceIn(-outset, (width + outset - tools.width).coerceAtLeast(-outset))
                    // Clear of the top handle's touch area, so a tap on the toolbar never starts a resize.
                    val y = selectedRect.top - outset - touch / 2 - 4.dp.roundToPx() - tools.height
                    tools.place(x, y, zIndex = 2f)
                }
                for ((edge, handle) in handles) {
                    val center = when (edge) {
                        Edge.LEFT -> IntOffset(selectedRect.left - outset, selectedRect.center.y)
                        Edge.RIGHT -> IntOffset(selectedRect.right + outset, selectedRect.center.y)
                        Edge.TOP -> IntOffset(selectedRect.center.x, selectedRect.top - outset)
                        Edge.BOTTOM -> IntOffset(selectedRect.center.x, selectedRect.bottom + outset)
                    }
                    handle.place(center.x - touch / 2, center.y - touch / 2, zIndex = 1f)
                }
            }
            if (resize != null && resizeRect != null) {
                resize.place((resizeRect.left - outset).toInt(), (resizeRect.top - outset).toInt(), zIndex = 2f)
            }
        }
    }
}

/**
 * Cards glide to their new places while editing (at once otherwise, or with animations off), each from wherever it
 * was drawn when its place changed.
 */
private class PlacementMotion(private val scope: CoroutineScope) {
    private val anims = HashMap<Any, Animatable<IntOffset, AnimationVector2D>>()
    private val targets = HashMap<Any, IntOffset>()

    /** Where to draw [key] on its way to [target]. */
    fun at(key: Any, target: IntOffset, animate: Boolean, millis: Int): IntOffset {
        if (!animate) {
            anims.remove(key)
            targets.remove(key)
            return target
        }
        val anim = anims.getOrPut(key) { Animatable(target, IntOffset.VectorConverter) }
        val before = targets.put(key, target)
        if (before != null && before != target) {
            scope.launch { anim.animateTo(target, tween(millis, easing = SettleEasing)) }
        }
        return anim.value
    }

    /** Forgets [key], so it next shows where it is at once. */
    fun forget(key: Any) {
        anims.remove(key)
        targets.remove(key)
    }

    /** Forgets cards no longer on the page. */
    fun retain(keys: Set<String>) {
        anims.keys.retainAll { it !is String || it in keys }
        targets.keys.retainAll { it !is String || it in keys }
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
        val rect = state.rects[target]
        val grid = state.gridCoords?.takeIf { it.isAttached }
        val view = state.chromeCoords?.takeIf { it.isAttached }
        if (rect != null && grid != null && view != null) {
            val middle = grid.positionInRoot().y + rect.top + rect.height / 2f
            val delta = middle - (view.positionInRoot().y + view.size.height / 2f)
            if (state.animate) scroll.animateScrollBy(delta) else scroll.dispatchRawDelta(delta)
        }
        if (state.reveal == target) state.reveal = null
    }
}

/** A tap on the page between cards, while editing, lets the selected card go. */
private suspend fun PointerInputScope.deselectOnBlankTap(state: DiscoverEditState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        if (!state.editing || state.drag != null || state.resize != null) return@awaitEachGesture
        val x = up.position.x.toInt()
        val y = up.position.y.toInt()
        if (state.rects.values.none { it.contains(IntOffset(x, y)) }) state.select(null)
    }
}

/** Where a card's layout coordinates are kept, for its gestures. */
private class Coordinates {
    var value: LayoutCoordinates? = null
}

/**
 * One card on the grid: [card]'s content with the long-press that lifts it. While [editing] the content takes no
 * touches (a tap selects the card instead), the cards not [selected] dim, TalkBack hears the card whole with every
 * edit as an action, and keys move and resize it. While it is held it draws into a layer the chrome draws over the page.
 */
@Composable
private fun CardHost(
    state: DiscoverEditState,
    spec: CardSpec,
    index: Int,
    selected: Boolean,
    editing: Boolean,
    card: @Composable (spec: CardSpec, modifier: Modifier) -> Unit
) {
    val layer = rememberGraphicsLayer()
    val coordinates = remember { Coordinates() }
    val id = spec.id
    val lifted = state.drag?.id == id
    DisposableEffect(id) {
        onDispose { if (state.drag?.id == id) state.cancelDrag() }
    }
    val dim by animateFloatAsState(
        if (editing && !selected) 0.6f else 1f,
        tween(if (state.animate) 200 else 0),
        label = "card dim"
    )
    val lean = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val nudge = state.nudge?.takeIf { it.id == id }
    LaunchedEffect(nudge) {
        when {
            nudge == null -> if (state.animate) lean.animateTo(Offset.Zero, tween(150)) else lean.snapTo(Offset.Zero)
            !state.animate -> lean.snapTo(nudge.offset)
            else -> while (true) {
                lean.animateTo(nudge.offset, tween(320, easing = FastOutSlowInEasing))
                lean.animateTo(Offset.Zero, tween(320, easing = FastOutSlowInEasing))
            }
        }
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
        Modifier.semantics {
            contentDescription = state.label(spec)
            traversalIndex = index.toFloat()
            customActions = listOf(
                CustomAccessibilityAction("Edit Discover") {
                    state.enter(id)
                    true
                }
            )
        }
    }

    Box(
        Modifier
            .layoutId(CardKey(id))
            .onPlaced { coordinates.value = it }
            // First in the chain, so starting or stopping edit mode (which changes what follows) never resets a
            // gesture in progress: the long-press that starts editing goes on to move the card.
            .pointerInput(state, id) { cardGestures(state, id, coordinates, layer) }
            .then(semantics)
            .then(
                if (editing) {
                    Modifier
                        .graphicsLayer {
                            alpha = dim
                            translationX = lean.value.x
                            translationY = lean.value.y
                        }
                        .onPreviewKeyEvent { event ->
                            event.type == KeyEventType.KeyDown && cardKey(state, id, event.key, event.isAltPressed, event.isShiftPressed)
                        }
                        .focusable()
                } else Modifier
            )
            .then(if (lifted) Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() } } else Modifier)
    ) {
        card(spec, if (spec.size is CardSize.Natural) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
        if (editing) {
            // While editing, the card's own buttons take no touches: the card is being arranged, not used.
            Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        }
    }
}

/** A key pressed on a card while editing: Alt and an arrow move it, plus and minus resize it, the menu key opens its actions. */
private fun cardKey(state: DiscoverEditState, id: String, key: Key, alt: Boolean, shift: Boolean): Boolean {
    when {
        alt && (key == Key.DirectionUp || key == Key.DirectionLeft) -> state.move(id, -1)
        alt && (key == Key.DirectionDown || key == Key.DirectionRight) -> state.move(id, 1)
        key == Key.Plus || key == Key.Equals || key == Key.NumPadAdd -> state.grow(id)
        key == Key.Minus || key == Key.NumPadSubtract -> state.shrink(id)
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

/** Where the finger is now, as a press is waited on. */
private class Press(var position: Offset)

/**
 * A card's gestures. A press held [LONG_PRESS_MS] without moving more than [PRESS_SLOP] lifts the card (starting edit
 * mode if it isn't on) and moves it until the finger lifts; the content under it never sees the rest of that
 * gesture. It is watched on the way down (the initial pass), before the card's own buttons, and nothing is taken
 * from them until the press is long enough. While editing, a tap selects the card.
 */
private suspend fun PointerInputScope.cardGestures(
    state: DiscoverEditState,
    id: String,
    coordinates: Coordinates,
    layer: GraphicsLayer
) {
    val slop = PRESS_SLOP.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val press = Press(down.position)
        val tapped = withTimeoutOrNull(LONG_PRESS_MS) { awaitPress(down, press, slop) }
        if (tapped == true) {
            if (state.editing) state.select(id)
            return@awaitEachGesture
        }
        if (tapped == false || state.drag != null || state.resize != null) return@awaitEachGesture
        val place = coordinates.value?.takeIf { it.isAttached } ?: return@awaitEachGesture
        state.haptic(EditHaptic.LONG_PRESS)
        if (!state.editing) state.enter(id) else state.select(id)
        state.pickUp(id, layer, place.size, place.localToRoot(press.position), press.position)
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                event.changes.forEach { it.consume() }
                if (change == null || !change.pressed) {
                    state.drop(ok = change != null)
                    break
                }
                coordinates.value?.takeIf { it.isAttached }?.let { state.dragTo(it.localToRoot(change.position)) }
            }
        } finally {
            if (state.drag?.id == id) state.drop(ok = false)
        }
    }
}

/** Waits on a press: true when it lifts (a tap), false when it moves away or something else takes it. */
private suspend fun AwaitPointerEventScope.awaitPress(down: PointerInputChange, press: Press, slop: Float): Boolean {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return false
        if (event.changes.size > 1) return false
        if (!change.pressed) return true
        if (change.isConsumed) return false
        press.position = change.position
        if ((change.position - down.position).getDistance() > slop) return false
    }
}

/**
 * The selected card's frame, its toolbar above it (Shape when it is shaped, Size, Hide or Private, More) and its
 * resize handles, on the edges whose size can change. The frame, toolbar and handles step aside while the card
 * is being [resizing] or [moving]; the handles stay in place (unseen), so a resize in progress keeps its finger.
 */
@Composable
private fun SelectionParts(state: DiscoverEditState, spec: CardSpec, index: Int, resizing: Boolean, moving: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val busy = resizing || moving
    if (!busy) {
        Box(Modifier.layoutId(Part.FRAME).border(2.dp, scheme.primary, RoundedCornerShape(30.dp)))
        EditToolbar(state, spec, Modifier.layoutId(Part.TOOLS).semantics { traversalIndex = index + 0.5f })
    }
    val edges = if (resizing) Edge.entries else resizableEdges(state, spec)
    for (edge in edges) {
        key(edge) {
            ResizeHandle(state, spec.id, edge, visible = !busy, Modifier.layoutId(HandleKey(edge)))
        }
    }
}

/** The edges of [spec] with a handle: across when another width fits, up and down when another height does. */
private fun resizableEdges(state: DiscoverEditState, spec: CardSpec): List<Edge> {
    val (c, r) = state.dims(spec)
    fun changes(size: CardSize) = size != spec.size && state.fits(spec, size)
    val across = (1..GRID_COLUMNS).any { it != c && changes(candidateSize(spec.kind, it, r)) }
    val tall = (spec.size is CardSize.Cells || spec.kind.shaped) &&
        (1..GRID_MAX_ROWS).any { it != r && changes(candidateSize(spec.kind, c, it)) }
    return buildList {
        if (across) addAll(listOf(Edge.LEFT, Edge.RIGHT))
        if (tall) addAll(listOf(Edge.TOP, Edge.BOTTOM))
    }
}

/** One resize handle: a dot on the frame's edge, dragged to resize the card along it. */
@Composable
private fun ResizeHandle(state: DiscoverEditState, id: String, edge: Edge, visible: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val coordinates = remember { Coordinates() }
    Box(
        modifier
            .onPlaced { coordinates.value = it }
            .clearAndSetSemantics { }
            .pointerInput(id, edge) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val grid = state.gridCoords?.takeIf { it.isAttached } ?: return@awaitEachGesture
                    val me = coordinates.value?.takeIf { it.isAttached } ?: return@awaitEachGesture
                    state.startResize(id, edge, grid.localPositionOf(me, down.position))
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            val g = state.gridCoords?.takeIf { it.isAttached } ?: break
                            val h = coordinates.value?.takeIf { it.isAttached } ?: break
                            state.resizeTo(g.localPositionOf(h, change.position))
                        }
                    } finally {
                        state.endResize()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            Box(
                Modifier
                    .size(24.dp)
                    .shadow(2.dp, CircleShape)
                    .background(scheme.surface, CircleShape)
                    .padding(3.dp)
                    .background(scheme.primary, CircleShape)
            )
        }
    }
}

/**
 * The frame that follows a resize: it tracks the finger exactly and settles onto the card on release. Where the
 * size under the finger is too small to read it turns red and dashed, with a small note under it.
 */
@Composable
private fun ResizeFrame(resize: CardResize, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val limited = resize.limited
    val color = if (limited) scheme.error else scheme.primary
    Box(
        modifier.drawBehind {
            val stroke = 2.dp.toPx()
            val radius = 30.dp.toPx()
            drawRoundRect(
                color,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(radius),
                style = Stroke(
                    width = stroke,
                    pathEffect = if (limited) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null
                )
            )
        }
    ) {
        if (limited) {
            Text(
                "Too small to read",
                color = scheme.onError,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .wrapContentSize(unbounded = true)
                    .offset { IntOffset(0, 30.dp.roundToPx()) }
                    .background(scheme.error, CircleShape)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
    }
}
