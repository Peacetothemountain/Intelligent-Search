package com.pixel.intelligentsearch.feature.search.today.cards.grid

import androidx.compose.ui.graphics.vector.ImageVector
import com.pixel.intelligentsearch.core.ui.icons.icon

/*
 * The few Material icons Discover's edit mode draws that the app's icon set doesn't have yet, built the same way
 * (see the icon builder in core.ui.icons) from Google's Material Icons path data.
 *
 * NG Designs
 */

/** Edit mode's icons, each built the first time it is drawn. */
internal object EditIcons {
    val Add: ImageVector by lazy { icon("Filled.Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }

    val AddCircle: ImageVector by lazy {
        icon("Outlined.AddCircleOutline", "M13,7h-2v4H7v2h4v4h2v-4h4v-2h-4V7zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z")
    }

    val MoreHoriz: ImageVector by lazy {
        icon("Filled.MoreHoriz", "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
    }

    val OpenInFull: ImageVector by lazy { icon("Filled.OpenInFull", "M21,11V3h-8l3.29,3.29l-10,10L3,13v8h8l-3.29,-3.29l10,-10L21,11z") }

    val ArrowUpward: ImageVector by lazy { icon("Filled.ArrowUpward", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z") }

    val ArrowDownward: ImageVector by lazy { icon("Filled.ArrowDownward", "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z") }

    val ZoomOutMap: ImageVector by lazy {
        icon("Filled.ZoomOutMap", "M15,3l2.3,2.3 -2.89,2.87 1.42,1.42L18.7,6.7 21,9V3zM3,9l2.3,-2.3 2.87,2.89 1.42,-1.42L6.7,5.3 9,3H3zM9,21l-2.3,-2.3 2.89,-2.87 -1.42,-1.42L5.3,17.3 3,15v6zM21,15l-2.3,2.3 -2.87,-2.89 -1.42,1.42 2.89,2.87L15,21h6z")
    }

    val CloseFullscreen: ImageVector by lazy {
        icon("Filled.CloseFullscreen", "M22,3.41l-5.29,5.29L20,12h-8V4l3.29,3.29L20.59,2L22,3.41zM3.41,22l5.29,-5.29L12,20v-8H4l3.29,3.29L2,20.59L3.41,22z")
    }

    val CropSquare: ImageVector by lazy {
        icon("Filled.CropSquare", "M18,4H6c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM18,18H6V6h12v12z")
    }

    val Crop169: ImageVector by lazy {
        icon("Filled.Crop169", "M19,6H5c-1.1,0 -2,0.9 -2,2v8c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM19,16H5V8h14v8z")
    }

    val CropPortrait: ImageVector by lazy {
        icon("Filled.CropPortrait", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,19c0,1.1 0.89,2 1.99,2H17c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM17,19H7V5h10v14z")
    }
}
