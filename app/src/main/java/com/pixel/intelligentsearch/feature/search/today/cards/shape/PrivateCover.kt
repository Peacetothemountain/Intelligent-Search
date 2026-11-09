package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.ui.icons.filled.Lock
import kotlin.math.hypot

/*
 * The Private cover: a card the user made private is covered completely by opaque stripes until it is tapped. Never a
 * blur, which would still leak shapes, colors and digits.
 *
 * NG Designs
 */

/**
 * Covers a card of [shape] with diagonal stripes, a lock, "Private" and "Tap to show", fully opaque. Lines that don't
 * fit the height step aside ("Tap to show" first, then the lock); "Private" always shows. For any card mode: shaped
 * cards draw the same cover in their own outline as they morph. Taps go to whatever is under it; it has no semantics,
 * the card's own description says it is private.
 */
@Composable
internal fun PrivateCover(shape: Shape, modifier: Modifier) {
    val colors = rememberShapedColors()
    val typography = MaterialTheme.typography
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(colors.cover)
                val line = colors.on.copy(alpha = 0.07f)
                val stripe = 2.dp.toPx()
                val gap = 8.dp.toPx()
                val reach = hypot(size.width, size.height)
                rotate(45f) {
                    var x = center.x - reach
                    while (x < center.x + reach) {
                        drawRect(line, Offset(x, center.y - reach), Size(stripe, reach * 2f))
                        x += gap
                    }
                }
            }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Layout(
            content = {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.on, modifier = Modifier.size(22.dp))
                Text("Private", style = typography.bodyMedium.copy(fontSize = 13.sp), color = colors.on, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Tap to show", style = typography.bodySmall.copy(fontSize = 10.5.sp), color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        ) { measurables, constraints ->
            val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
            val placeables = measurables.map { it.measure(loose) }
            val gap = 4.dp.roundToPx()
            // "Private" first, then the lock, then "Tap to show", while they fit.
            val shown = BooleanArray(3)
            var height = placeables[1].height
            shown[1] = true
            for (i in intArrayOf(0, 2)) {
                val more = placeables[i].height + gap
                if (height + more <= constraints.maxHeight) {
                    shown[i] = true
                    height += more
                }
            }
            val width = placeables.filterIndexed { i, _ -> shown[i] }.maxOf { it.width }
            layout(width.coerceAtMost(constraints.maxWidth), height.coerceAtMost(constraints.maxHeight)) {
                var y = 0
                for (i in 0..2) {
                    if (!shown[i]) continue
                    val p = placeables[i]
                    p.place((width - p.width) / 2, y)
                    y += p.height + gap
                }
            }
        }
    }
}
