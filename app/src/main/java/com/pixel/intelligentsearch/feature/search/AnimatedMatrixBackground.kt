package com.pixel.intelligentsearch.feature.search
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AnimatedMatrixBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Spacer(modifier = modifier.fillMaxSize().drawWithCache {
        val width = size.width
        val height = size.height
        
        val spacing = 55f // Kept at 55f for performance
        val cols = (width / spacing).toInt() + 1
        val rows = (height / spacing).toInt() + 1
        val sparkleSize = 4.5f
        val baseColor = Color(0x60A09EB0)
        
        // Pre-calculate positions to eliminate per-frame math overhead on cold boots
        val points = mutableListOf<Pair<Offset, Float>>()
        for (c in 0..cols) {
            for (r in 0..rows) {
                val cx = c * spacing + (r % 2) * (spacing / 2)
                val cy = r * spacing
                val offsetPhase = (cx + cy) / 200f
                points.add(Offset(cx, cy) to offsetPhase)
            }
        }
        
        onDrawBehind {
            points.forEach { (offset, offsetPhase) ->
                val alpha = ((kotlin.math.sin((phase + offsetPhase).toDouble()).toFloat() + 1f) / 2f) * 0.8f + 0.2f
                val s = sparkleSize
                val cx = offset.x
                val cy = offset.y
                val path = Path().apply {
                    moveTo(cx, cy - s)
                    quadraticBezierTo(cx, cy, cx + s, cy)
                    quadraticBezierTo(cx, cy, cx, cy + s)
                    quadraticBezierTo(cx, cy, cx - s, cy)
                    quadraticBezierTo(cx, cy, cx, cy - s)
                    close()
                }
                drawPath(
                    path = path,
                    color = baseColor.copy(alpha = alpha)
                )
            }
        }
    })
}
