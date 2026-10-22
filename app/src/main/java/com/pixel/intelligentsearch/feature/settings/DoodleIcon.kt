package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The doodles icon: a paintbrush that has just drawn a looping flourish, with two sparkles beside it. It is drawn
 * in outline at the stroke weight of Material Symbols Outlined (2 on the 24 grid), in black, so like any Material
 * icon it takes the tint of the row it sits in.
 */
val DoodleBrushIcon: ImageVector by lazy {
    ImageVector.Builder(name = "DoodleBrush", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            fun outline(block: PathBuilder.() -> Unit) = path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
            // The handle, from the top right down to the ferrule.
            outline {
                moveTo(21.0f, 3.0f)
                lineTo(16.6f, 7.4f)
            }
            // The ferrule holding the bristles.
            outline {
                moveTo(14.7f, 6.9f)
                lineTo(17.1f, 9.3f)
            }
            // The bristles, a teardrop ending in the tip that drew the flourish.
            outline {
                moveTo(14.7f, 6.9f)
                curveTo(12.6f, 8.2f, 11.6f, 10.2f, 11.3f, 12.7f)
                curveTo(13.8f, 12.4f, 15.8f, 11.4f, 17.1f, 9.3f)
            }
            // The flourish: a loop and a wave across the bottom, as if just painted.
            outline {
                moveTo(2.5f, 21.0f)
                curveTo(5.5f, 21.0f, 8.0f, 19.0f, 8.0f, 16.5f)
                curveTo(8.0f, 14.6f, 6.6f, 13.8f, 5.4f, 14.4f)
                curveTo(4.0f, 15.1f, 4.2f, 17.4f, 5.8f, 19.0f)
                curveTo(7.4f, 20.6f, 10.0f, 21.0f, 12.0f, 20.2f)
                curveTo(13.8f, 19.5f, 14.6f, 18.4f, 16.4f, 18.4f)
                curveTo(18.0f, 18.4f, 19.0f, 19.4f, 20.0f, 20.4f)
            }
            // Two four-pointed sparkles.
            sparkle(cx = 6.0f, cy = 6.5f, r = 3.0f)
            sparkle(cx = 11.2f, cy = 3.0f, r = 1.8f)
        }
        .build()
}

/** A filled four-pointed sparkle of radius [r] centred on ([cx], [cy]), its sides curving in to the centre. */
private fun ImageVector.Builder.sparkle(cx: Float, cy: Float, r: Float) = path(fill = SolidColor(Color.Black)) {
    moveTo(cx, cy - r)
    quadTo(cx + r * 0.18f, cy - r * 0.18f, cx + r, cy)
    quadTo(cx + r * 0.18f, cy + r * 0.18f, cx, cy + r)
    quadTo(cx - r * 0.18f, cy + r * 0.18f, cx - r, cy)
    quadTo(cx - r * 0.18f, cy - r * 0.18f, cx, cy - r)
    close()
}
