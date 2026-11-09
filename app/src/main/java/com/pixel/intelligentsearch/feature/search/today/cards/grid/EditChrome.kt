package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.ui.icons.filled.Close
import com.pixel.intelligentsearch.core.ui.icons.filled.Lock
import com.pixel.intelligentsearch.core.ui.icons.outlined.Interests
import com.pixel.intelligentsearch.core.ui.icons.outlined.VisibilityOff
import com.pixel.intelligentsearch.feature.search.today.cards.CardMode
import com.pixel.intelligentsearch.feature.search.today.cards.CardSpec
import com.pixel.intelligentsearch.feature.search.today.cards.DiscoverLayout
import com.pixel.intelligentsearch.feature.search.today.cards.WidgetChoice
import com.pixel.intelligentsearch.feature.search.today.cards.shape.CardOutlineShape
import kotlinx.coroutines.delay

/*
 * What edit mode draws over Discover: the top bar (Edit Discover, Add card, Done) that turns into the Remove or Hide
 * target while a card is held, the held card itself (above the bar), a short message at the bottom, and the sheets.
 *
 * NG Designs
 */

/** The top bar's slide: quick, with a touch of overshoot. */
private val BarEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.1f)

/**
 * Edit mode's layer over Discover, filling the page above its scrolling column: the top bar that slides down while
 * editing, the card being moved, short messages, and the Shape, Add card and actions sheets. Back stops editing
 * before it closes the page. [widgetChoices] lists the widgets a card can be made from and [onAddWidget] makes one;
 * [onAddSite] reads a website the user typed into a card (its failure's message is shown under the field);
 * [onRemoveCustom] lets the page let go of a removed app or website card. Edits go to [onLayoutChange].
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
    BackHandler(enabled = state.editing && state.sheet == null) { state.exit() }
    Box(modifier.fillMaxSize().onGloballyPositioned { state.chromeCoords = it }) {
        EditTopBar(state, Modifier.align(Alignment.TopCenter))
        HeldCard(state, Modifier.fillMaxSize())
        EditToast(state, Modifier.align(Alignment.BottomCenter))
        Announcer(state)
    }
    EditSheets(state, layout, widgetChoices, onAddWidget, onAddSite)
}

/**
 * The top bar while editing, sliding down over the page: "Edit Discover", Add card and Done. While a card is held it
 * is the Remove (the user's own cards) or Hide (the page's own parts) target, red with the card over it.
 */
@Composable
private fun EditTopBar(state: DiscoverEditState, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val editing = state.editing
    val shown = remember { Animatable(if (editing) 1f else 0f) }
    LaunchedEffect(editing) {
        val target = if (editing) 1f else 0f
        if (state.animate) shown.animateTo(target, tween(300, easing = BarEasing)) else shown.snapTo(target)
    }
    val out by remember { derivedStateOf { shown.value <= 0f } }
    if (out && !editing) return
    val held = state.drag
    val page = scheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -(1f - shown.value) * size.height }
            // Solid behind the buttons, fading out only in the margin below them, so no page text shows through.
            .background(Brush.verticalGradient(0f to page.copy(alpha = 0.97f), 0.78f to page.copy(alpha = 0.97f), 1f to page.copy(alpha = 0f)))
            .onGloballyPositioned { state.topBarBottom = it.boundsInRoot().bottom }
            // The bar takes the touches over it, as the page under it is covered.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 26.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (held != null) 0f else 1f },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Edit Discover",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            FilledTonalButton(
                onClick = { state.open(EditSheet.ADD) },
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp)
            ) {
                Icon(EditIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add card")
            }
            Spacer(Modifier.width(6.dp))
            Button(onClick = { state.exit() }) { Text("Done") }
        }
        if (held != null) DropTarget(state, held, Modifier.matchParentSize())
    }
}

/** Remove or Hide, where a held card is dropped to take it off the page: red while the card is over it. */
@Composable
private fun DropTarget(state: DiscoverEditState, held: CardDrag, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val builtIn = state.spec(held.id)?.kind?.builtIn != false
    val hot = held.hot
    val back by animateColorAsState(if (hot) scheme.error.copy(alpha = 0.32f) else Color.Transparent, tween(150), label = "drop target")
    val ink by animateColorAsState(if (hot) scheme.error else scheme.onSurface, tween(150), label = "drop target ink")
    Row(
        modifier
            .clip(CircleShape)
            .background(back)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Close, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (builtIn) "Hide" else "Remove", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

/**
 * The card being moved, drawn over the page and its top bar from the layer the card records: lifted (a little larger,
 * with a shadow in its own shape), following the finger, shrunk over the Remove or Hide target, and gliding home when
 * let go.
 */
@Composable
private fun HeldCard(state: DiscoverEditState, modifier: Modifier) {
    val held = state.drag ?: return
    val spec = state.spec(held.id)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val size = Size(held.size.width.toFloat(), held.size.height.toFloat())
    val outline = remember(spec?.mode, spec?.shape, size, density, direction) {
        val shape = if (spec?.mode == CardMode.SHAPED) CardOutlineShape(spec.shape) else RoundedCornerShape(24.dp)
        shape.createOutline(size, direction, density)
    }
    Spacer(
        modifier.drawBehind {
            val origin = state.chromeCoords?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
            val topLeft = (held.landing?.value ?: (held.finger - held.grab)) - origin
            val layer = held.layer
            layer.scaleX = held.scale.value
            layer.scaleY = held.scale.value
            layer.alpha = held.alpha.value
            layer.shadowElevation = 14.dp.toPx()
            when (outline) {
                is Outline.Rounded -> {
                    val r = outline.roundRect
                    layer.setRoundRectOutline(Offset(r.left, r.top), Size(r.width, r.height), r.topLeftCornerRadius.x)
                }
                is Outline.Generic -> layer.setPathOutline(outline.path)
                is Outline.Rectangle -> layer.setRectOutline(Offset(outline.rect.left, outline.rect.top), outline.rect.size)
            }
            translate(topLeft.x, topLeft.y) { drawLayer(layer) }
        }
    )
}

/** A short message at the bottom of the page ("Card removed"), shown for a moment and read out by TalkBack. */
@Composable
private fun EditToast(state: DiscoverEditState, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val message = state.message
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        text = message.text
        visible = true
        delay(2_000)
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
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center
                )
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

/**
 * The selected card's toolbar: Shape (shaped cards), Size (the next preset size that fits), Hide (the page's own
 * parts) or Private (the user's own cards), and More, the actions sheet.
 */
@Composable
internal fun EditToolbar(state: DiscoverEditState, spec: CardSpec, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier, shape = CircleShape, color = scheme.surfaceContainerHighest, shadowElevation = 8.dp) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (spec.mode == CardMode.SHAPED) ToolButton(Icons.Outlined.Interests, "Shape") { state.open(EditSheet.SHAPE) }
            ToolButton(EditIcons.OpenInFull, "Size") { state.cycleSize(spec.id) }
            if (spec.kind.builtIn) {
                ToolButton(Icons.Outlined.VisibilityOff, "Hide") { state.removeCard(spec.id) }
            } else {
                ToolButton(Icons.Filled.Lock, "Private", on = spec.private) { state.togglePrivate(spec.id) }
            }
            ToolButton(EditIcons.MoreHoriz, null, description = "More actions") { state.open(EditSheet.ACTIONS) }
        }
    }
}

/** One of the toolbar's buttons; [on] for a switch that is on. */
@Composable
private fun ToolButton(icon: ImageVector, label: String?, on: Boolean? = null, description: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val lit = on == true
    val ink = if (lit) scheme.onPrimaryContainer else scheme.onSurface
    val action = if (on != null) {
        Modifier.toggleable(value = lit, role = Role.Switch, onValueChange = { onClick() })
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (lit) scheme.primaryContainer else Color.Transparent)
            .then(action)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .heightIn(min = 40.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        if (label != null) {
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontSize = 12.sp, color = ink)
        }
    }
}
