package com.pixel.intelligentsearch.feature.tabs

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pixel.intelligentsearch.feature.tabs.ui.GlassBackdrop
import com.pixel.intelligentsearch.feature.tabs.ui.frostedGlass
import com.pixel.intelligentsearch.feature.tabs.ui.isDark
import com.pixel.intelligentsearch.feature.tabs.ui.rememberTick
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One CSS box-shadow of the pill's halo: [y] offset, [blur], [spread] (dp) and its color. */
private class Halo(val y: Float, val blur: Float, val spread: Float, val color: Color)

/** One of the pill's tabs. */
internal class PillTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** The pill's height. It sits [PILL_BOTTOM_GAP] above the navigation bar. */
internal val PILL_HEIGHT = 70.dp
internal val PILL_BOTTOM_GAP = 18.dp

/** From each side of the screen; at large text sizes the pill takes more of the width. */
internal val PILL_SIDE = 28.dp
internal val PILL_SIDE_LARGE_TEXT = 16.dp

/** The widest it gets (landscape, a tablet): about as wide as on a phone held upright. */
internal val PILL_MAX_WIDTH = 400.dp
internal val PILL_MAX_WIDTH_LARGE_TEXT = 480.dp

/**
 * The floating pill, as M3 Expressive's floating toolbar (the approved Gallery mockup's "after"): a surface-container
 * pill with no outline, 70 dp tall, lit from behind so a soft halo shows all round its edge in the dark theme (in the
 * light one: a lifted shadow and a faint light rim). Icon over a 12sp label; the selected tab has a 56 x 32
 * primary-container indicator behind its icon only, which springs between tabs ([indicator] is its place as a
 * fractional tab index, read only while laying out and drawing), a filled icon and a bold label.
 *
 * With [largeText] (text at 1.3x or more) the labels can't fit three across: the tabs not selected show their icon
 * only, and the selected one shows its icon and label side by side, the label sliding out from behind the icon, on an
 * indicator as wide as both. [frosted], [backdrop] and [positionInBackdrop] are kept for the shell; the pill is solid.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun FloatingPill(
    tabs: List<PillTab>,
    selected: Int,
    indicator: () -> Float,
    onSelect: (Int) -> Unit,
    frosted: Boolean,
    backdrop: GlassBackdrop,
    positionInBackdrop: () -> Offset,
    largeText: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    val density = LocalDensity.current
    val fill = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLowest
    val indicatorColor = scheme.primaryContainer
    val halo = remember(dark, scheme.onSurface, scheme.primary, scheme.onSecondaryContainer) {
        // CSS box-shadows (x, y, blur, spread, color): blur is twice the standard deviation.
        if (dark) listOf(
            Halo(0f, 36f, 6f, scheme.primary.copy(alpha = 0.11f)),
            Halo(0f, 12f, 1f, scheme.onSurface.copy(alpha = 0.15f)),
            Halo(0f, 0f, 1f, scheme.onSurface.copy(alpha = 0.06f))
        ) else listOf(
            Halo(12f, 30f, 0f, scheme.onSecondaryContainer.copy(alpha = 0.24f)),
            Halo(2f, 6f, 0f, scheme.onSecondaryContainer.copy(alpha = 0.10f)),
            Halo(0f, 0f, 1f, Color.White.copy(alpha = 0.95f))
        )
    }
    val labelLine = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toPx() }

    // Where each tab is, as last laid out (inside the pill's 6dp padding), for the indicator.
    val tabLeft = remember { FloatArray(tabs.size) }
    val tabWidth = remember { FloatArray(tabs.size) }

    Box(
        modifier
            // The halo is blurred once into a picture of its own and then only moved with the pill.
            .drawWithCache {
                val dp = density.density
                val reach = ceil(40f * dp)
                val w = size.width
                val h = size.height
                val baked = obtainGraphicsLayer().apply {
                    compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                    record(size = IntSize(ceil(w + 2f * reach).toInt(), ceil(h + 2f * reach).toInt())) {
                        drawIntoCanvas { canvas ->
                            for (o in halo) {
                                val spread = o.spread * dp
                                val sigma = o.blur / 2f * dp
                                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color = o.color.toArgb()
                                    if (sigma > 0f) maskFilter = android.graphics.BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f), android.graphics.BlurMaskFilter.Blur.NORMAL)
                                }
                                val r = h / 2f + spread
                                canvas.nativeCanvas.drawRoundRect(
                                    reach - spread, reach - spread + o.y * dp, reach + w + spread, reach + h + spread + o.y * dp, r, r, paint
                                )
                            }
                        }
                    }
                }
                onDrawBehind { translate(-reach, -reach) { drawLayer(baked) } }
            }
            .background(fill, CircleShape)
            .drawBehind {
                val pad = 6.dp.toPx()
                val n = tabs.size
                val pos = indicator()
                val i0 = floor(pos.coerceIn(0f, n - 1f)).toInt().coerceIn(0, n - 2)
                val frac = pos - i0
                fun lerpF(a: Float, b: Float) = a + (b - a) * frac
                if (!largeText) {
                    // 56 x 32 behind the icon only: the icon's middle, with the label under it, centered in the tab.
                    val cx = pad + lerpF(tabLeft[i0] + tabWidth[i0] / 2f, tabLeft[i0 + 1] + tabWidth[i0 + 1] / 2f)
                    val content = 24.dp.toPx() + 4.dp.toPx() + labelLine
                    val cy = pad + (size.height - 2 * pad - content) / 2f + 12.dp.toPx()
                    val iw = 56.dp.toPx()
                    val ih = 32.dp.toPx()
                    drawRoundRect(indicatorColor, topLeft = Offset(cx - iw / 2f, cy - ih / 2f), size = Size(iw, ih), cornerRadius = CornerRadius(ih / 2f))
                } else {
                    val inset = 2.dp.toPx()
                    val edge = 8.dp.toPx()
                    val l = (pad + lerpF(tabLeft[i0], tabLeft[i0 + 1]) + inset).coerceAtLeast(edge)
                    val rr = (pad + lerpF(tabLeft[i0] + tabWidth[i0], tabLeft[i0 + 1] + tabWidth[i0 + 1]) - inset).coerceAtMost(size.width - edge)
                    if (rr > l) drawRoundRect(indicatorColor, topLeft = Offset(l, edge), size = Size(rr - l, size.height - 2 * edge), cornerRadius = CornerRadius(24.dp.toPx()))
                }
            }
    ) {
        val labelStyle = MaterialTheme.typography.labelMedium
        val measurer = rememberTextMeasurer()
        val labelWidths = remember(measurer, labelStyle, density) {
            tabs.map { measurer.measure(it.label, labelStyle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1).size.width.toFloat() }
        }
        val share = { i: Int -> (1f - abs(indicator().coerceIn(0f, tabs.size - 1f) - i)).coerceIn(0f, 1f) }
        Layout(
            content = {
                tabs.forEachIndexed { i, tab ->
                    PillTabItem(tab, selected = i == selected, share = { share(i) }, largeText = largeText, onClick = { onSelect(i) })
                }
            },
            modifier = Modifier.fillMaxSize().padding(6.dp).selectableGroup()
        ) { measurables, constraints ->
            val n = measurables.size
            val total = constraints.maxWidth.toFloat()
            val widths = FloatArray(n) { total / n }
            if (largeText) {
                // The selected tab is as wide as its icon, label and 18dp each side; the others share the rest.
                val f = FloatArray(n) { share(it) }
                val full = FloatArray(n) { (24.dp + 8.dp + 36.dp).toPx() + labelWidths[it] }
                var fixed = 0f
                var free = 0f
                for (i in 0 until n) {
                    fixed += f[i] * full[i]
                    free += 1f - f[i]
                }
                val rest = ((total - fixed) / free.coerceAtLeast(0.001f)).coerceAtLeast(48.dp.toPx())
                for (i in 0 until n) widths[i] = f[i] * full[i] + (1f - f[i]) * rest
                val sum = widths.sum()
                if (sum > total) for (i in 0 until n) widths[i] *= total / sum
            }
            var x = 0f
            val placeables = measurables.mapIndexed { i, m ->
                val wPx = widths[i].roundToInt().coerceAtLeast(0)
                tabLeft[i] = x
                tabWidth[i] = widths[i]
                x += widths[i]
                m.measure(Constraints.fixed(wPx, constraints.maxHeight))
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { i, p -> p.place(tabLeft[i].roundToInt(), 0) }
            }
        }
    }
}

/**
 * One tab: icon over label (or beside it at large text). Its icon turns onPrimaryContainer and its label onSurface as
 * the indicator arrives ([share], read while drawing), from onSurfaceVariant; its icon is filled and its label bold
 * while it is the selected tab. A tap plays the app's one vibration, on touch down and again as the tab opens, as every
 * button in the app does.
 */
@Composable
private fun PillTabItem(tab: PillTab, selected: Boolean, share: () -> Float, largeText: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val idle = scheme.onSurfaceVariant
    val iconOn = scheme.onPrimaryContainer
    val labelOn = scheme.onSurface
    val tick = rememberTick()
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    LaunchedEffect(pressed) { if (pressed) tick() }
    val weight = if (selected) FontWeight.Bold else FontWeight.Medium
    // Tinted while drawing, so the colors follow the indicator without recomposing.
    fun Modifier.tint(on: Color) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(lerp(idle, on, share()), blendMode = BlendMode.SrcIn)
        }
    Box(
        Modifier
            .fillMaxSize()
            // Its name, whether its label shows or not: at large text only the selected tab shows its label, and a
            // label cut to nothing gave TalkBack nothing to say.
            .semantics(mergeDescendants = true) { contentDescription = tab.label }
            .selectable(selected = selected, interactionSource = interactions, indication = null, role = Role.Tab) {
                tick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        val icon = if (selected) tab.selectedIcon else tab.icon
        if (!largeText) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp).tint(iconOn))
                Spacer(Modifier.height(4.dp))
                Text(tab.label, style = MaterialTheme.typography.labelMedium, fontWeight = weight, color = Color.Black, maxLines = 1, softWrap = false, modifier = Modifier.tint(labelOn))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp).tint(iconOn))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .tint(labelOn)
                        // As wide as the share of the label shown, after an 8dp gap; the rest is cut off.
                        .clipToBounds()
                        .layout { m, c ->
                            val p = m.measure(c.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                            val gap = 8.dp.roundToPx()
                            val w = ((gap + p.width) * share()).roundToInt()
                            layout(w, p.height) { p.place(gap, 0) }
                        }
                )
            }
        }
    }
}
