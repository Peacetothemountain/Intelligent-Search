package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.ui.icons.filled.Lock
import com.pixel.intelligentsearch.core.ui.icons.outlined.Delete
import com.pixel.intelligentsearch.core.ui.icons.outlined.Interests
import com.pixel.intelligentsearch.core.ui.icons.outlined.VisibilityOff
import com.pixel.intelligentsearch.feature.search.today.cards.CARD_SHAPES
import com.pixel.intelligentsearch.feature.search.today.cards.CardForm
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.WidgetChoice
import com.pixel.intelligentsearch.feature.search.today.cards.shape.ShapeSwatch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * What edit mode draws over Discover: the top bar (Add and Done, icons only) with a short hint that fades, the selected
 * card's toolbar (Shape, Private, Hide), the shapes a card can take, popping up over it the moment a full card is
 * pinched smaller, a short message at the bottom, and the sheets.
 *
 * NG Designs
 */

/** The top bar's slide: quick, with a touch of overshoot. */
private val BarEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.1f)

/** How long the hint under the top bar stays. */
private const val HINT_MS = 4_500L

/**
 * Edit mode's layer over Discover, filling the page above its scrolling column: the top bar that slides down while
 * editing, the selected card's toolbar and shapes, short messages, and the Add card and actions sheets. Back puts the
 * shapes away, then lets the selected card go, then stops editing, before it closes the page. [widgetChoices] lists
 * the widgets a card can be made from and [onAddWidget] makes one; [onAddSite] reads a website the user typed into a
 * card (its failure's message is shown under the field); [onRemoveCustom] lets the page let go of a removed app or
 * website card. Edits go to [onLayoutChange].
 */
@Composable
internal fun DiscoverEditChrome(
    state: DiscoverEditState,
    layout: DiscoverLayout,
    onLayoutChange: (DiscoverLayout) -> Unit,
    widgetChoices: suspend () -> List<WidgetChoice>,
    onAddWidget: (WidgetChoice) -> Unit,
    onAddSite: suspend (String) -> Result<Unit>,
    onRemoveCustom: (CardSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    state.onLayoutChange = onLayoutChange
    state.onRemoveCustom = onRemoveCustom
    BackHandler(enabled = state.editing && state.sheet == null) {
        when {
            state.strip != null -> state.strip = null
            state.selectedId != null -> state.select(null)
            else -> state.exit()
        }
    }
    // A card removed just now is let go as the page closes, rather than left bound.
    DisposableEffect(state) { onDispose { state.flushRemoval() } }
    Box(modifier.fillMaxSize().onGloballyPositioned { state.chromeCoords = it }) {
        CardTools(state, layout)
        EditTopBar(state, Modifier.align(Alignment.TopCenter))
        EditToast(state, Modifier.align(Alignment.BottomCenter))
        Announcer(state)
    }
    EditSheets(state, layout, widgetChoices, onAddWidget, onAddSite)
}

/**
 * The top bar while editing, sliding down over the page: Add card on the left and Done on the right, icons only, and
 * between them, for a few seconds, how editing works.
 */
@Composable
private fun EditTopBar(state: DiscoverEditState, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val editing = state.editing
    val shown = remember { Animatable(if (editing) 1f else 0f) }
    LaunchedEffect(editing) {
        val target = if (editing) 1f else 0f
        if (state.animate) shown.animateTo(target, tween(320, easing = BarEasing)) else shown.snapTo(target)
    }
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        hint = editing
        if (editing) {
            delay(HINT_MS)
            hint = false
        }
    }
    val out by remember { derivedStateOf { shown.value <= 0f } }
    if (out && !editing) return
    val page = scheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -(1f - shown.value) * size.height }
            // Solid behind the buttons, fading out only in the margin below them, so no page text shows through.
            .background(Brush.verticalGradient(0f to page.copy(alpha = 0.96f), 0.72f to page.copy(alpha = 0.96f), 1f to page.copy(alpha = 0f)))
            .onGloballyPositioned { state.topBarBottom = it.boundsInRoot().bottom }
            // The bar takes the touches over it, as the page under it is covered.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 26.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { state.open(EditSheet.ADD) }, modifier = Modifier.size(48.dp)) {
                Icon(EditIcons.Add, contentDescription = "Add a card")
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(hint, enter = fadeIn(tween(250)), exit = fadeOut(tween(600))) {
                    Text(
                        "Drag to move. Pinch to resize.",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }
            }
            FilledIconButton(onClick = { state.exit() }, modifier = Modifier.size(48.dp)) {
                Icon(EditIcons.Check, contentDescription = "Done")
            }
        }
    }
}

/**
 * Over the selected card: its toolbar (Shape for kinds that have one, Private, and Hide or Remove), and above it the
 * shapes it can take when they are offered. The shapes follow a card being pinched, so they are there the moment a
 * full card is pinched smaller; the toolbar steps aside while a card moves.
 */
@Composable
private fun CardTools(state: DiscoverEditState, layout: DiscoverLayout) {
    val id = state.selectedId?.takeIf { state.editing }
    val spec = id?.let { layout.cards.firstOrNull { it.id == id } }
    val active = state.active
    val stripOn = spec != null && state.strip == spec.id && spec.kind.shaped
    val toolsOn = spec != null && active == null
    if (spec == null) return
    Layout(
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    stripOn,
                    enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 700f), initialScale = 0.85f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f)
                ) { ShapeStrip(state, spec) }
                androidx.compose.animation.AnimatedVisibility(
                    toolsOn,
                    enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 700f), initialScale = 0.85f),
                    exit = fadeOut(tween(100))
                ) { Toolbar(state, spec) }
            }
        }
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val placeable = measurables.first().measure(Constraints(maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Read as it is placed, so scrolling moves the tools with the card without rebuilding them.
            state.scroll?.value
            val card = toolsTarget(state, spec.id) ?: return@layout
            val x = (card.center.x - placeable.width / 2f).roundToInt().coerceIn(margin, (constraints.maxWidth - margin - placeable.width).coerceAtLeast(margin))
            val above = card.top - margin - placeable.height
            val top = state.topBarBottom - (state.chromeCoords?.takeIf { it.isAttached }?.let { it.boundsInRoot().top } ?: 0f)
            val y = when {
                above >= top -> above
                card.bottom + margin + placeable.height <= constraints.maxHeight - 120.dp.toPx() -> card.bottom + margin
                else -> top + margin
            }
            placeable.place(IntOffset(x, y.roundToInt()), zIndex = 3f)
        }
    }
}

/** Where card [id] is drawn, in the chrome's coordinates: following the fingers while it is moved or pinched. */
private fun toolsTarget(state: DiscoverEditState, id: String): Rect? {
    val grid = state.gridCoords?.takeIf { it.isAttached } ?: return null
    val chrome = state.chromeCoords?.takeIf { it.isAttached } ?: return null
    val active = state.active?.takeIf { it.id == id }
    val slot = active?.rect ?: state.slots[id]?.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) } ?: return null
    val drawn = Rect(slot.left, slot.top, slot.right - state.gap, slot.bottom - state.gap)
    val origin = chrome.localPositionOf(grid, Offset.Zero)
    return drawn.translate(origin)
}

/** The selected card's toolbar: Shape (kinds that have one), Private, and Hide (the page's parts) or Remove (the user's own). */
@Composable
private fun Toolbar(state: DiscoverEditState, spec: CardSpec) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = scheme.surfaceContainerHighest, shadowElevation = 8.dp, tonalElevation = 2.dp) {
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (spec.kind.shaped) ToolButton(Icons.Outlined.Interests, "Shapes", on = state.strip == spec.id) { state.toggleStrip(spec.id) }
            ToolButton(Icons.Filled.Lock, "Private", on = spec.private) { state.togglePrivate(spec.id) }
            if (spec.kind.builtIn) {
                ToolButton(Icons.Outlined.VisibilityOff, "Hide") { state.removeCard(spec.id) }
            } else {
                ToolButton(Icons.Outlined.Delete, "Remove") { state.removeCard(spec.id) }
            }
        }
    }
}

/** One of the toolbar's buttons, an icon only; [on] for a switch, lit while on. TalkBack hears [description]. */
@Composable
private fun ToolButton(icon: ImageVector, description: String, on: Boolean? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val lit = on == true
    val action = if (on != null) {
        Modifier.toggleable(value = lit, role = Role.Switch, onValueChange = { onClick() })
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (lit) scheme.primaryContainer else Color.Transparent)
            .then(action)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (lit) scheme.onPrimaryContainer else scheme.onSurface, modifier = Modifier.size(21.dp))
    }
}

/**
 * The forms a card can take, in a row that scrolls: its full card first, then every Material shape, the one it has now
 * marked. A tap turns it at once; the row stays, so another can be tried.
 */
@Composable
private fun ShapeStrip(state: DiscoverEditState, spec: CardSpec) {
    val scheme = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val current = if (spec.form == CardForm.SHAPE) CARD_SHAPES.indexOf(spec.shape) + 1 else 0
    LaunchedEffect(spec.id) { if (current > 2) list.scrollToItem(current - 2) }
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHighest, shadowElevation = 10.dp, tonalElevation = 2.dp) {
        LazyRow(
            state = list,
            modifier = Modifier.widthIn(max = 400.dp).selectableGroup(),
            contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "card") {
                StripItem(selected = current == 0, description = "Full card", onClick = { state.setForm(spec.id, CardForm.CARD) }) {
                    CardSwatch(current == 0, Modifier.fillMaxSize())
                }
            }
            items(CARD_SHAPES, key = { it }) { name ->
                val selected = spec.form == CardForm.SHAPE && spec.shape == name
                StripItem(selected = selected, description = "${shapeName(name)} shape", onClick = { state.setForm(spec.id, CardForm.SHAPE, name) }) {
                    ShapeSwatch(name, selected, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** One choice in the shapes row. */
@Composable
private fun StripItem(selected: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
    ) { content() }
}

/** The full card, drawn small as the shapes are: a wide rounded box on the same tile. */
@Composable
private fun CardSwatch(selected: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tile = if (selected) scheme.secondaryContainer else scheme.onSurface.copy(alpha = 0.04f)
    val fill = scheme.onSurfaceVariant
    Spacer(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .then(if (selected) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(13.dp)) else Modifier)
            .drawBehind {
                drawRect(tile)
                val w = size.width * 0.74f
                val h = size.height * 0.46f
                drawRoundRect(fill, Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h), CornerRadius(h * 0.3f))
            }
    )
}

/**
 * A short message at the bottom of the page ("Card hidden"), shown for a moment and read out by TalkBack, with Undo
 * when it can be undone (it then stays longer).
 */
@Composable
private fun EditToast(state: DiscoverEditState, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val message = state.message
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        text = message.text
        undo = message.undo
        visible = true
        delay(if (message.undo != null) DiscoverEditState.UNDO_MS else 2_000)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 100.dp)
    ) {
        // The look of the search's own message (Search History Cleared), without a button.
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = scheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = if (undo != null) 6.dp else 20.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 9.dp)
                )
                val action = undo
                if (action != null) {
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = {
                        action()
                        undo = null
                        visible = false
                    }) {
                        Text("Undo", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** What TalkBack is told as the page changes under it: moves, sizes, shapes, and editing starting and stopping. */
@Composable
private fun Announcer(state: DiscoverEditState) {
    val note = state.announcement
    var text by remember { mutableStateOf("") }
    LaunchedEffect(note) {
        if (note == null) return@LaunchedEffect
        // Cleared for a frame first, so the same words twice are said twice.
        text = ""
        withFrameNanos { }
        text = note.text
    }
    Box(
        Modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (text.isNotEmpty()) contentDescription = text
            }
    )
}
