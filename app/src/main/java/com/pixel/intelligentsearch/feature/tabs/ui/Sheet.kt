package com.pixel.intelligentsearch.feature.tabs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The dim behind sheets and dialogs, the same everywhere: it appears and goes with them, never fading. */
@Composable
internal fun scrimColor(): Color = Color.Black.copy(alpha = if (MaterialTheme.colorScheme.isDark) 0.52f else 0.32f)

/**
 * The one bottom sheet every tab uses: over a static dim (a tap on it closes the sheet), the solid surface container
 * high with the matte grain and 28 dp top corners, sliding up on the spatial spring and down again when it closes.
 * Its handle and its top drag it down; let go far or fast enough it closes, otherwise it springs back. Back closes it.
 *
 * [value] is what the sheet shows, or null for no sheet; the last one stays drawn while it slides away. Pages cover the
 * pill while [value] is set (PageChrome.setCovered), as they do for every popup. The content is a [SheetTitle], its
 * words, and [SheetButtons].
 */
@Composable
internal fun <T : Any> PreviewSheet(
    value: T?,
    onDismiss: () -> Unit,
    paneTitle: (T) -> String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(T) -> Unit
) {
    var shown by remember { mutableStateOf<T?>(null) }
    if (value != null) shown = value
    val slide = remember { Animatable(1f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(value != null) {
        if (value != null) {
            drag = 0f
            slide.animateTo(0f, Springs.spatial())
        } else {
            slide.animateTo(1.05f, Springs.spatial())
            shown = null
            drag = 0f
        }
    }
    // Only while a sheet is up: added then, it comes after any back handler under it (the full preview's) and wins.
    if (value != null) BackHandler { onDismiss() }
    val s = shown ?: return
    val draggable = rememberDraggableState { d -> drag = (drag + d).coerceAtLeast(0f) }
    Box(modifier.fillMaxSize()) {
        if (value != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor())
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close") { onDismiss() }
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val maxH = maxHeight * 0.92f
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxH)
                    .onSizeChanged { height = it.height.toFloat() }
                    // Dragged in the page's coordinates, outside the sheet's own movement, so it follows the finger.
                    .draggable(
                        draggable, Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (drag > height * 0.3f || velocity > 1200f) onDismiss()
                            else scope.launch { Animatable(drag).animateTo(0f, Springs.spatial()) { drag = this.value } }
                        }
                    )
                    .graphicsLayer { translationY = slide.value * size.height + drag }
                    .matteCard(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), container = MaterialTheme.colorScheme.surfaceContainerHigh, lit = false, border = false)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { }
                    .semantics { this.paneTitle = paneTitle(s) }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp)
            ) {
                // The handle: drags the sheet down, and closes it for TalkBack.
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(48.dp, 24.dp)
                        .semantics { contentDescription = "Close sheet"; onClick("Close") { onDismiss(); true } },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(32.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                }
                content(s)
            }
        }
    }
}

/** A sheet's or dialog's title: headline small, a heading for TalkBack. */
@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp).semantics { heading() }
    )
}

/** A sheet's or dialog's words under its title. */
@Composable
internal fun SheetText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/**
 * A sheet's or dialog's buttons: in a row at its end, the confirming one last; they wrap onto more lines at large text.
 */
@Composable
internal fun SheetButtons(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        content = content
    )
}

/**
 * A sheet's buttons as one M3 button group: each row's buttons share its width equally, the confirming one last. On a
 * narrow window, or at large text, a button whose words don't fit its share starts a row above; the last buttons stay
 * together on the bottom row, so the confirming one keeps its place.
 */
@Composable
internal fun SheetButtonGroup(modifier: Modifier = Modifier, gap: Dp = 8.dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier.fillMaxWidth().padding(top = 16.dp)) { buttons, constraints ->
        val width = constraints.maxWidth
        val g = gap.roundToPx()
        val wants = buttons.map { it.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) }
        fun share(count: Int) = (width - g * (count - 1)) / count
        // Rows from the end: each takes as many of the buttons before it as fit at an equal share.
        val rows = ArrayDeque<IntRange>()
        var end = buttons.lastIndex
        while (end >= 0) {
            var start = end
            while (start > 0 && (start - 1..end).all { wants[it] <= share(end - start + 2) }) start--
            rows.addFirst(start..end)
            end = start - 1
        }
        val placed = rows.map { row ->
            val each = share(row.count())
            row.map { buttons[it].measure(androidx.compose.ui.unit.Constraints.fixedWidth(each)) }
        }
        val heights = placed.map { row -> row.maxOfOrNull { it.height } ?: 0 }
        layout(width, heights.sum()) {
            var y = 0
            placed.forEachIndexed { r, row ->
                var x = 0
                row.forEach {
                    it.placeRelative(x, y + (heights[r] - it.height) / 2)
                    x += it.width + g
                }
                y += heights[r]
            }
        }
    }
}

/** A snackbar to show: its words and, optionally, one action. */
class Snack(val message: String, val actionLabel: String? = null, val action: (() -> Unit)? = null) {
    val id = System.nanoTime()
}

/**
 * The one snackbar every tab shows: the M3 inverse surface, solid, rising from below on the spatial spring and
 * leaving downward on the damped one (position, never alpha). It stays as long as Android's accessibility settings ask.
 */
@Composable
internal fun PreviewSnackbar(snack: Snack?, bottom: Dp, onDismiss: () -> Unit, modifier: Modifier = Modifier, top: Dp? = null) {
    var shown by remember { mutableStateOf<Snack?>(null) }
    var visible by remember { mutableStateOf(false) }
    val a11y = LocalAccessibilityManager.current
    LaunchedEffect(snack?.id) {
        if (snack == null) {
            visible = false
            return@LaunchedEffect
        }
        shown = snack
        visible = true
        val base = if (snack.action != null) 10_000L else 4_000L
        delay(a11y?.calculateRecommendedTimeoutMillis(base, containsIcons = false, containsText = true, containsControls = snack.action != null) ?: base)
        visible = false
        onDismiss()
    }
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible && shown != null,
        // From below, or (given a [top]) from above, at that height.
        enter = slideInVertically(Springs.spatial()) { if (top != null) -it * 3 else it * 3 },
        exit = slideOutVertically(Springs.damped()) { if (top != null) -it * 3 else it * 3 },
        modifier = if (top != null) modifier.padding(start = 16.dp, end = 16.dp, top = top) else modifier.padding(start = 16.dp, end = 16.dp, bottom = bottom)
    ) {
        val s = shown ?: return@AnimatedVisibility
        Row(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(scheme.inverseSurface, RoundedCornerShape(14.dp))
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(s.message, color = scheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (s.actionLabel != null) {
                Box(
                    Modifier.heightIn(min = 48.dp).bouncyClickable(RoundedCornerShape(20.dp)) {
                        visible = false
                        onDismiss()
                        s.action?.invoke()
                    }.padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(s.actionLabel, color = scheme.inversePrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/**
 * A dialog's surface, the same in every tab: the solid surface container high with the matte grain and 28 dp corners,
 * growing in from 0.7 on the spatial spring (scale only). The dim and the back gesture belong to whoever shows it
 * ([PreviewDialog], or Gallery's popup layer).
 */
@Composable
internal fun DialogSurface(paneTitle: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val grow = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, Springs.spatial()) }
    Column(
        modifier
            .graphicsLayer { scaleX = grow.value; scaleY = grow.value }
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .matteCard(RoundedCornerShape(28.dp), container = MaterialTheme.colorScheme.surfaceContainerHigh, lit = false, border = false)
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .semantics { this.paneTitle = paneTitle }
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
        content = content
    )
}

/** A dialog over a static dim: a tap outside or back closes it. Pages cover the pill while it is open. */
@Composable
internal fun PreviewDialog(open: Boolean, onDismiss: () -> Unit, paneTitle: String, content: @Composable ColumnScope.() -> Unit) {
    if (!open) return
    BackHandler { onDismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .background(scrimColor())
            .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close") { onDismiss() }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) { DialogSurface(paneTitle, content = content) }
}
