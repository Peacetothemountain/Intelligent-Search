package com.ngdesigns.intelligentsearch.ui.components

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Neural Expressive glow: a gradient in the colour scheme's own tones travels around the edge of a rounded
 * container, with a soft halo, while [active] (listening, or an answer being worked out). It fades in and out and
 * draws nothing at rest. With animations off the edge glows but doesn't move.
 */
fun Modifier.aura(active: Boolean, cornerRadius: Dp = 28.dp, width: Dp = 2.5.dp, animate: Boolean = true): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    val strength by animateFloatAsState(if (active) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "aura")
    val spin = rememberInfiniteTransition(label = "aura").animateFloat(
        initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "aura-angle",
    )
    if (strength == 0f) return@composed this
    val stops = intArrayOf(colors.primary.toArgb(), colors.tertiary.toArgb(), colors.primaryContainer.toArgb(), colors.secondary.toArgb(), colors.primary.toArgb())
    drawWithCache {
        val shader = SweepGradient(size.width / 2, size.height / 2, stops, null)
        val brush = ShaderBrush(shader)
        val matrix = Matrix()
        val radius = cornerRadius.toPx()
        val stroke = width.toPx()
        onDrawWithContent {
            matrix.setRotate(if (animate) spin.value else 0f, size.width / 2, size.height / 2)
            shader.setLocalMatrix(matrix)
            val halo = stroke * 3
            drawRoundRect(
                brush = brush, topLeft = Offset(-halo, -halo), size = Size(size.width + halo * 2, size.height + halo * 2),
                cornerRadius = CornerRadius(radius + halo), alpha = 0.16f * strength, style = Stroke(halo * 2),
            )
            drawContent()
            drawRoundRect(brush = brush, cornerRadius = CornerRadius(radius), style = Stroke(stroke), alpha = strength)
        }
    }
}
