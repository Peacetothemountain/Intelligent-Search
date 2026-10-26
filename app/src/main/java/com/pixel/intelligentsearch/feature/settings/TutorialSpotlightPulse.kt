package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Modernized Material 3 Expressive Fluid Spotlight Pulse Ring.
 * Utilizes a hardware-accelerated GraphicsLayer to isolate continuous GPU rasterization,
 * preventing CPU recomposition invalidation loops and delivering 120 FPS fluid multi-harmonic waves.
 */
@Composable
internal fun FluidSpotlightPulseRing(
    targetCenter: Offset,
    targetRadius: Float,
    primaryColor: Color,
    tertiaryColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_pulse_transition")

    val rippleWave1 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )

    val rippleWave2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1600,
                delayMillis = 800,
                easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )

    val breathingFactor = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val auraColors = remember(primaryColor) {
        listOf(
            primaryColor.copy(alpha = 0.25f),
            primaryColor.copy(alpha = 0.08f),
            Color.Transparent
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Hardware RenderNode isolation for smooth 120 FPS rendering
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        val strokeBaseWidth = 3.dp.toPx()
        val breathing = breathingFactor.value
        val focalRadius = targetRadius * breathing

        // 1. Soft focal ambient glow aura behind target
        drawCircle(
            brush = Brush.radialGradient(
                colors = auraColors,
                center = targetCenter,
                radius = focalRadius * 1.5f
            ),
            center = targetCenter,
            radius = focalRadius * 1.5f
        )

        // 2. Focal anchor ring with breathing micro-scale
        drawCircle(
            color = primaryColor,
            alpha = 0.88f,
            radius = focalRadius,
            center = targetCenter,
            style = Stroke(width = strokeBaseWidth)
        )

        // 3. Fluid Wave 1 (Primary harmonic wave expanding outward with non-linear dissipation)
        val p1 = rippleWave1.value
        val r1 = targetRadius + (targetRadius * 0.75f * p1)
        val a1 = ((1f - p1) * 0.78f).coerceIn(0f, 1f)
        drawCircle(
            color = primaryColor,
            alpha = a1,
            radius = r1,
            center = targetCenter,
            style = Stroke(width = (strokeBaseWidth * (1f - p1 * 0.45f)).coerceAtLeast(1.dp.toPx()))
        )

        // 4. Fluid Wave 2 (Tertiary harmonic chromatic wave staggered by 800ms)
        val p2 = rippleWave2.value
        val r2 = targetRadius + (targetRadius * 0.75f * p2)
        val a2 = ((1f - p2) * 0.65f).coerceIn(0f, 1f)
        drawCircle(
            color = tertiaryColor,
            alpha = a2,
            radius = r2,
            center = targetCenter,
            style = Stroke(width = (strokeBaseWidth * (1f - p2 * 0.45f)).coerceAtLeast(1.dp.toPx()))
        )
    }
}
