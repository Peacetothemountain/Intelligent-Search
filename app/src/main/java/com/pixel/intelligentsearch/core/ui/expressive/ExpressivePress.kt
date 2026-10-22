package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled

/**
 * The press of Android 17's Material 3 Expressive controls: nothing shrinks; the pressed control's shape morphs
 * instead, on the fast spatial spring (a touch of overshoot), and springs back on release. A rounded control's
 * corners tighten while held, as Quick Settings tiles and Expressive buttons do; a square-cornered row rounds its
 * corners, so its pressed highlight reads as a rounded key. Put it where the control used to clip to [rest], ahead of
 * its clickable, so the ripple and content follow the morph. With animations off the shape simply stays at rest.
 */
fun Modifier.expressivePress(
    interactionSource: InteractionSource,
    rest: Shape? = null,
    enabled: Boolean = true
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val animate by rememberAnimationsEnabled()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pressed, enabled, animate) {
        val target = if (pressed && enabled && animate) 1f else 0f
        progress.animateTo(
            target,
            if (target > 0f) ExpressiveMotionTokens.Spatial.FastSpatial() else ExpressiveMotionTokens.Spatial.ExpressiveDefault()
        )
    }
    val restShape = rest ?: RectangleShape
    this.graphicsLayer {
        // Read only here, so the morph redraws the layer without recomposing.
        val p = progress.value
        shape = if (p == 0f) restShape else PressedShape(restShape, p)
        clip = rest != null || p != 0f
    }
}

/** How much a rounded control's corners tighten at the bottom of a press. */
private const val PRESSED_CORNER_TIGHTEN = 0.35f

/** How round a square-cornered row's corners get at the bottom of a press. */
private val ROW_PRESSED_CORNER = 20.dp

/** [rest] part-way ([progress] 0 to 1, a little past on the spring's overshoot) to its pressed shape. */
private class PressedShape(private val rest: Shape, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (rest is CornerBasedShape) {
            val keep = (1f - PRESSED_CORNER_TIGHTEN * progress).coerceIn(0f, 1f)
            val maxRadius = minOf(size.width, size.height) / 2f
            fun r(corner: androidx.compose.foundation.shape.CornerSize) =
                CornerRadius((corner.toPx(size, density) * keep).coerceIn(0f, maxRadius))
            val ltr = layoutDirection == LayoutDirection.Ltr
            val topStart = r(rest.topStart)
            val topEnd = r(rest.topEnd)
            val bottomEnd = r(rest.bottomEnd)
            val bottomStart = r(rest.bottomStart)
            return Outline.Rounded(
                RoundRect(
                    left = 0f, top = 0f, right = size.width, bottom = size.height,
                    topLeftCornerRadius = if (ltr) topStart else topEnd,
                    topRightCornerRadius = if (ltr) topEnd else topStart,
                    bottomRightCornerRadius = if (ltr) bottomEnd else bottomStart,
                    bottomLeftCornerRadius = if (ltr) bottomStart else bottomEnd
                )
            )
        }
        if (rest == RectangleShape) {
            val radius = with(density) { ROW_PRESSED_CORNER.toPx() } * progress.coerceIn(0f, 1.2f)
            val corner = CornerRadius(radius.coerceIn(0f, minOf(size.width, size.height) / 2f))
            return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, corner, corner, corner, corner))
        }
        // Other shapes (squircles, morphing polygons) keep their own outline.
        return rest.createOutline(size, layoutDirection, density)
    }
}
