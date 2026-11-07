package com.pixel.intelligentsearch.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Builds one of the Material Icons the app draws (`Icons.Outlined.Search` and the rest, in the packages beside this
 * file) from its path data, the same vector the `material-icons-extended` library builds: same name, size, viewport,
 * fill and path nodes, so it draws the same pixels. The library writes each icon as builder calls, which cost about
 * twice the bytes of the path text; it is Google's Apache 2.0 path data (fonts.google.com/icons, the 2018
 * Material Icons set). Each icon is built the first time it is asked for and kept. Add one by copying its path from
 * the library's `ImageVector` (one path string per `materialPath` call, one command per path node).
 */
internal fun icon(
    name: String,
    vararg paths: String,
    autoMirror: Boolean = false,
    evenOdd: Boolean = false
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror
    ).apply {
        for (path in paths) {
            addPath(
                pathData = addPathNodes(path),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = SolidColor(Color.Black),
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f
            )
        }
    }.build()
