package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape-Morphing Loading Indicator.
 *
 * Smoothly morphs geometry across Circle, Squircle, Pill, and Star Diamond phases
 * with continuous rotation and rhythmic breathing pacing.
 */
@Composable
fun ExpressiveShapeMorphLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_morph_loop")

    // Continuous 360 rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Morph progress cycling across 4 geometric states
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morph_stage"
    )

    // Subtle scale breathing
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val reusableStarPath = remember { Path() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.62f)) {
            val w = this.size.width
            val h = this.size.height
            val phase = morphProgress % 4f

            rotate(degrees = rotation) {
                scale(scale = pulseScale) {
                    when {
                        // Stage 0 -> 1: Circle to Rounded Squircle
                        phase < 1f -> {
                            val t = phase
                            val cornerFactor = 0.5f - (t * 0.22f)
                            val cornerRadiusPx = w * cornerFactor
                            drawRoundRect(
                                color = color,
                                topLeft = Offset.Zero,
                                size = this.size,
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                            )
                        }
                        // Stage 1 -> 2: Squircle to Pill
                        phase < 2f -> {
                            val t = phase - 1f
                            val currentW = w * (1f + t * 0.15f)
                            val currentH = h * (1f - t * 0.15f)
                            val cornerRadiusPx = currentH / 2f
                            drawRoundRect(
                                color = color,
                                topLeft = Offset((w - currentW) / 2f, (h - currentH) / 2f),
                                size = Size(currentW, currentH),
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                            )
                        }
                        // Stage 2 -> 3: Pill to 4-Point Star Diamond
                        phase < 3f -> {
                            val t = phase - 2f
                            reusableStarPath.reset()
                            val cx = w / 2f
                            val cy = h / 2f
                            val outer = (w / 2f)
                            val inner = outer * (0.35f + (1f - t) * 0.45f)

                            reusableStarPath.moveTo(cx, cy - outer)
                            reusableStarPath.quadraticTo(cx, cy, cx + inner, cy)
                            reusableStarPath.quadraticTo(cx, cy, cx + outer, cy)
                            reusableStarPath.quadraticTo(cx, cy, cx, cy + inner)
                            reusableStarPath.quadraticTo(cx, cy, cx, cy + outer)
                            reusableStarPath.quadraticTo(cx, cy, cx - inner, cy)
                            reusableStarPath.quadraticTo(cx, cy, cx - outer, cy)
                            reusableStarPath.quadraticTo(cx, cy, cx, cy - inner)
                            reusableStarPath.close()

                            drawPath(path = reusableStarPath, color = color)
                        }
                        // Stage 3 -> 4: Star Diamond returning to Circle
                        else -> {
                            val t = phase - 3f
                            val cornerRadiusPx = (w * 0.28f) + (t * (w * 0.22f))
                            drawRoundRect(
                                color = color,
                                topLeft = Offset.Zero,
                                size = this.size,
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                            )
                        }
                    }
                }
            }
        }
    }
}
