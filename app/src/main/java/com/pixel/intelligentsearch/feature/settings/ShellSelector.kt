package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The one selector every settings page uses for tabs and one-of-a-few choices: equal options in a row, the chosen
 * one on an indicator. Its colours are those of the rows it replaced: by default the chosen option sits on the
 * primary container in its content colour and the rest are in the muted on-surface-variant, straight on what is
 * behind the selector. A [containerColor] or [border] puts the options on a track of that colour and outline, in
 * [shape], drawn as a matte shell ([MatteShell]): the same colour with only the matte grain added.
 *
 * When the choice changes, the old indicator dissolves away and a new one grows in under the new option on the
 * Material 3 Expressive fast spatial spring, so it overshoots a touch and settles (the squish), its fill fading
 * smoothly in to exactly its own colour, with no grain or shimmer. Only the indicator is
 * redrawn while that plays; nothing recomposes per frame. With animations off it simply moves.
 *
 * [itemContent] draws an option; [LocalContentColor] is the option's colour ([selectedContentColor] when chosen,
 * [unselectedContentColor] otherwise), and the chosen option may show a filled icon and bold label. Options tap
 * through [bouncyClickable], so a tap bounces and gives the same one haptic as every other control, and each
 * announces whether it is selected. [indicatorColor] can give an option its own indicator colour (a custom colour
 * swatch); [unselectedColor] and [unselectedBorder] fill and outline the options that aren't chosen, as rows that
 * showed every option as its own pill or chip did. [indicatorShape] is the indicator's and each option's shape.
 */
@Composable
fun <T> ShellSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 48.dp,
    role: Role = Role.RadioButton,
    containerColor: Color = Color.Transparent,
    shape: Shape = ShellSelectorDefaults.ItemShape,
    border: BorderStroke? = null,
    contentPadding: Dp = ShellSelectorDefaults.Padding,
    gap: Dp = ShellSelectorDefaults.Gap,
    indicatorShape: Shape = ShellSelectorDefaults.ItemShape,
    indicatorColor: (T) -> Color = ShellSelectorDefaults.NoOverride,
    unselectedColor: (T) -> Color = ShellSelectorDefaults.NoFill,
    unselectedBorder: BorderStroke? = null,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    itemContent: @Composable BoxScope.(option: T, isSelected: Boolean) -> Unit
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val defaultIndicator = MaterialTheme.colorScheme.primaryContainer
    val currentIndicatorColor by rememberUpdatedState(indicatorColor)
    val currentUnselectedColor by rememberUpdatedState(unselectedColor)
    val currentOptions by rememberUpdatedState(options)
    val animationsOn by rememberAnimationsEnabled()
    val indicator = remember { ShellIndicatorState(selectedIndex) }

    LaunchedEffect(selectedIndex) { indicator.moveTo(selectedIndex, animate = animationsOn) }

    val row: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .drawBehind {
                    val count = currentOptions.size
                    if (count == 0) return@drawBehind
                    val layout = SelectorLayout(contentPadding.toPx(), gap.toPx(), count)
                    // The options that aren't chosen, each in its own fill and outline where it has them.
                    for (index in 0 until count) {
                        if (index == indicator.current) continue
                        drawOption(layout, index, currentUnselectedColor(currentOptions[index]), unselectedBorder, indicatorShape)
                    }
                    fun colorAt(index: Int): Color {
                        val option = currentOptions.getOrNull(index) ?: return defaultIndicator
                        return currentIndicatorColor(option).takeIf { it != Color.Unspecified } ?: defaultIndicator
                    }
                    val previous = indicator.previous
                    val fade = indicator.fadeOut.value
                    if (previous in 0 until count && previous != indicator.current && fade > 0f) {
                        drawIndicator(layout, previous, 0.85f + 0.15f * fade, fade, colorAt(previous), indicatorShape)
                    }
                    val current = indicator.current
                    if (current in 0 until count) {
                        drawIndicator(layout, current, indicator.grow.value, indicator.reveal.value, colorAt(current), indicatorShape)
                    }
                }
                .padding(contentPadding)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) selectedContentColor else unselectedContentColor,
                    animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
                    label = "shell_selector_content"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // At least [itemHeight], taller where large text needs it; all options as tall as the row.
                        .heightIn(min = itemHeight)
                        .fillMaxHeight()
                        .semantics { this.selected = isSelected }
                        .bouncyClickable(shape = indicatorShape, role = role) { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        itemContent(option, isSelected)
                    }
                }
            }
        }
    }

    if (containerColor.alpha > 0f || border != null) {
        MatteShell(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            containerColor = containerColor,
            border = border,
            content = row
        )
    } else {
        // No track: the options sit straight on what is behind them, with no shell and no grain.
        Column(modifier = modifier.fillMaxWidth(), content = row)
    }
}

object ShellSelectorDefaults {
    /** Space between the track's edge and the options, and between options. */
    val Padding: Dp = 4.dp
    val Gap: Dp = 4.dp

    /** A pill: the indicator's and each option's default shape. */
    val ItemShape = RoundedCornerShape(percent = 50)

    /** [ShellSelector]'s default [indicatorColor]: no option has its own, all use the primary container. */
    val NoOverride: (Any?) -> Color = { Color.Unspecified }

    /** [ShellSelector]'s default unselected fill: none, so the options that aren't chosen show what is behind them. */
    val NoFill: (Any?) -> Color = { Color.Transparent }
}

/** Where [ShellSelector]'s options lie in its row: inset by [pad], [gap] apart, [count] equal slots. */
private class SelectorLayout(val pad: Float, val gap: Float, val count: Int)

/** Option [index]'s slot (its top left and size), mirrored right to left, as [ShellSelector]'s row lays it out. */
private fun DrawScope.slotOf(layout: SelectorLayout, index: Int): Pair<Offset, Size>? {
    val itemW = (size.width - 2f * layout.pad - (layout.count - 1) * layout.gap) / layout.count
    val itemH = size.height - 2f * layout.pad
    if (itemW <= 0f || itemH <= 0f) return null
    val slot = if (layoutDirection == LayoutDirection.Ltr) index else layout.count - 1 - index
    return Offset(layout.pad + slot * (itemW + layout.gap), layout.pad) to Size(itemW, itemH)
}

/** Fills [shape] laid out at [topLeft] in [shapeSize] with [color], or with [brush] where one is given. */
private fun DrawScope.drawShape(
    shape: Shape,
    topLeft: Offset,
    shapeSize: Size,
    color: Color,
    brush: Brush? = null,
    alpha: Float = 1f
) {
    val outline = shape.createOutline(shapeSize, layoutDirection, this)
    translate(topLeft.x, topLeft.y) {
        if (brush != null) drawOutline(outline, brush, alpha = alpha) else drawOutline(outline, color, alpha = alpha)
    }
}

/** Draws an option that isn't chosen: its [fill] and [border], each where it has one. */
private fun DrawScope.drawOption(layout: SelectorLayout, index: Int, fill: Color, border: BorderStroke?, shape: Shape) {
    val (topLeft, slot) = slotOf(layout, index) ?: return
    if (fill.alpha > 0f) drawShape(shape, topLeft, slot, fill)
    if (border != null) {
        // Just inside the option's edge, as a Material chip draws its outline.
        val width = border.width.toPx()
        val inset = Size(slot.width - width, slot.height - width)
        if (inset.width <= 0f || inset.height <= 0f) return
        val outline = shape.createOutline(inset, layoutDirection, this)
        translate(topLeft.x + width / 2f, topLeft.y + width / 2f) {
            drawOutline(outline, border.brush, style = Stroke(width))
        }
    }
}

/**
 * Draws the indicator under option [index]: [grow] sizes it (0 is 60% wide and 80% tall, 1 is the full option, a
 * spring's overshoot a little over), and [reveal] is how far its fill has faded in (1 is a plain fill of exactly
 * [color]).
 */
private fun DrawScope.drawIndicator(
    layout: SelectorLayout,
    index: Int,
    grow: Float,
    reveal: Float,
    color: Color,
    shape: Shape
) {
    val (slotTopLeft, slot) = slotOf(layout, index) ?: return
    val w = slot.width * (0.6f + 0.4f * grow)
    val h = slot.height * (0.8f + 0.2f * grow)
    if (w <= 0f || h <= 0f) return
    val topLeft = Offset(slotTopLeft.x + (slot.width - w) / 2f, slotTopLeft.y + (slot.height - h) / 2f)
    val indicatorSize = Size(w, h)
    drawShape(shape, topLeft, indicatorSize, color, alpha = reveal.coerceIn(0f, 1f))
}

/** Where [ShellSelector]'s indicator is, and how far its grow, reveal and fade-out have come. */
@Stable
private class ShellIndicatorState(initial: Int) {
    private val currentState = mutableIntStateOf(initial)
    private val previousState = mutableIntStateOf(-1)
    val current: Int get() = currentState.intValue
    val previous: Int get() = previousState.intValue
    val grow = Animatable(1f)
    val reveal = Animatable(1f)
    val fadeOut = Animatable(0f)

    suspend fun moveTo(index: Int, animate: Boolean) {
        if (index == current) return
        previousState.intValue = current
        currentState.intValue = index
        if (!animate) {
            grow.snapTo(1f)
            reveal.snapTo(1f)
            fadeOut.snapTo(0f)
            return
        }
        grow.snapTo(0f)
        reveal.snapTo(0f)
        fadeOut.snapTo(1f)
        coroutineScope {
            launch { grow.animateTo(1f, ExpressiveMotionTokens.Spatial.FastSpatial()) }
            launch { reveal.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing)) }
            launch { fadeOut.animateTo(0f, tween(durationMillis = 180, easing = FastOutSlowInEasing)) }
        }
    }
}
