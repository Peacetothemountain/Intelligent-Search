package com.pixel.intelligentsearch.core.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 standard Shape hierarchy.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Material 3 Expressive (M3X) Extended Shape Tokens (Android 17 Canary ZP11).
 * Tailored for high-refresh rate fluid surfaces, search pill containers, and cards.
 */
val PillShape = CircleShape
val ExpressivePillShape = CircleShape
val ExpressiveContainerShape = RoundedCornerShape(28.dp)
val ExpressiveSearchBarShape = RoundedCornerShape(28.dp)
val ExpressiveChipShape = RoundedCornerShape(16.dp)
val ExpressiveCardShape = RoundedCornerShape(24.dp)
val ExpressiveDialogShape = RoundedCornerShape(28.dp)
val ExpressiveBottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val ExpressiveSegmentShape = RoundedCornerShape(14.dp)
val ExpressiveExtraSmallShape = RoundedCornerShape(8.dp)
val ExpressiveSmallShape = RoundedCornerShape(12.dp)
val ExpressiveMediumShape = RoundedCornerShape(16.dp)
val ExpressiveLargeShape = RoundedCornerShape(24.dp)
val ExpressiveExtraLargeShape = RoundedCornerShape(32.dp)
val ExpressiveFullShape = CircleShape

/**
 * Continuous curvature squircle shape tokens for Android 17 / Pixel M3 Expressive surfaces.
 */
val ExpressiveSquircleCardShape = com.pixel.intelligentsearch.core.ui.expressive.SquircleShape(24.dp)
val ExpressiveSquircleContainerShape = com.pixel.intelligentsearch.core.ui.expressive.SquircleShape(28.dp)
val ExpressiveSquircleChipShape = com.pixel.intelligentsearch.core.ui.expressive.SquircleShape(16.dp)
val ExpressiveSquircleDialogShape = com.pixel.intelligentsearch.core.ui.expressive.SquircleShape(28.dp)

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
