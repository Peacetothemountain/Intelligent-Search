package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.min

/*
 * One shape, drawn small, for the shape picker.
 *
 * NG Designs
 */

private val SwatchShape = RoundedCornerShape(13.dp)

/**
 * The shape [name] at its optical size, filled with onSurfaceVariant on a faint tile; when [selected], the tile is
 * secondaryContainer with a primary outline. The caller gives it its size, its click and its semantics.
 */
@Composable
internal fun ShapeSwatch(name: String, selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tile = if (selected) scheme.secondaryContainer else scheme.onSurface.copy(alpha = 0.04f)
    val fill = scheme.onSurfaceVariant
    Spacer(
        modifier
            .clip(SwatchShape)
            .then(if (selected) Modifier.border(2.dp, scheme.primary, SwatchShape) else Modifier)
            .clearAndSetSemantics { }
            .drawWithCache {
                val side = min(size.width, size.height) * 0.7f
                val path = Path()
                if (side > 0f) CardShapes.outline(name).addTo(path, side, (size.width - side) / 2f, (size.height - side) / 2f)
                onDrawBehind {
                    drawRect(tile)
                    drawPath(path, fill)
                }
            }
    )
}
