package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import com.pixel.intelligentsearch.core.ui.icons.icon

/*
 * The few Material icons the card designs draw that the app's icon set doesn't have yet, built the way the app builds
 * its own (see core.ui.icons.icon, Google's Apache 2.0 path data), and the icons as paths the designs can draw at
 * any scale.
 *
 * NG Designs
 */

/** The icons only the cards use. */
internal object CardIcons {
    val Bolt: ImageVector by lazy {
        icon("Filled.Bolt", "M11,21h-1l1,-7H7.5c-0.88,0 -0.33,-0.75 -0.31,-0.78C8.48,10.94 10.42,7.54 13.01,3h1l-1,7h3.51c0.4,0 0.62,0.19 0.4,0.66C12.97,17.55 11,21 11,21z")
    }
    val Favorite: ImageVector by lazy {
        icon("Filled.Favorite", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
    }
    val TaskAlt: ImageVector by lazy {
        icon("Filled.TaskAlt", "M22,5.18L10.59,16.6l-4.24,-4.24 1.41,-1.41 2.83,2.83 10,-10L22,5.18zM19.79,10.22C19.92,10.79 20,11.39 20,12c0,4.42 -3.58,8 -8,8s-8,-3.58 -8,-8c0,-4.42 3.58,-8 8,-8c1.58,0 3.04,0.46 4.28,1.25l1.44,-1.44C16.1,2.67 14.13,2 12,2C6.48,2 2,6.48 2,12c0,5.52 4.48,10 10,10s10,-4.48 10,-10c0,-1.19 -0.22,-2.33 -0.6,-3.39L19.79,10.22z")
    }
    val HideSource: ImageVector by lazy {
        icon("Filled.HideSource", "M2.81,2.81L1.39,4.22l2.27,2.27C2.61,8.07 2,9.96 2,12c0,5.52 4.48,10 10,10c2.04,0 3.93,-0.61 5.51,-1.66l2.27,2.27l1.41,-1.41L2.81,2.81zM12,20c-4.41,0 -8,-3.59 -8,-8c0,-1.48 0.41,-2.86 1.12,-4.06l10.94,10.94C14.86,19.59 13.48,20 12,20zM7.94,5.12L6.49,3.66C8.07,2.61 9.96,2 12,2c5.52,0 10,4.48 10,10c0,2.04 -0.61,3.93 -1.66,5.51l-1.46,-1.46C19.59,14.86 20,13.48 20,12c0,-4.41 -3.59,-8 -8,-8C10.52,4 9.14,4.41 7.94,5.12z")
    }
    val Work: ImageVector by lazy {
        icon("Filled.Work", "M20,6h-4V4c0,-1.11 -0.89,-2 -2,-2h-4c-1.11,0 -2,0.89 -2,2v2H4c-1.11,0 -1.99,0.89 -1.99,2L2,19c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V8c0,-1.11 -0.89,-2 -2,-2zM14,6h-4V4h4v2z")
    }
}

/** Icons as paths on their 24 by 24 viewport, built once per icon. */
internal object IconPaths {
    private val cache = HashMap<ImageVector, Path>()

    /** [vector]'s paths joined into one, on its viewport (group transforms are not applied: the app's icons have none). */
    fun of(vector: ImageVector): Path = synchronized(cache) {
        cache.getOrPut(vector) {
            val out = Path()
            fun walk(group: VectorGroup) {
                for (node in group) {
                    when (node) {
                        is VectorGroup -> walk(node)
                        is VectorPath -> {
                            val p = PathParser().addPathNodes(node.pathData).toPath()
                            if (node.pathFillType == PathFillType.EvenOdd) out.fillType = PathFillType.EvenOdd
                            out.addPath(p)
                        }
                    }
                }
            }
            walk(vector.root)
            out
        }
    }
}
