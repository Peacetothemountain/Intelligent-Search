package com.pixel.intelligentsearch.feature.search

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AnimatedMatrixBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix")
    
    val background = MaterialTheme.colorScheme.background
    val isDark = (0.299f * background.red + 0.587f * background.green + 0.114f * background.blue) < 0.5f
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isDark) 10000 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val density = LocalDensity.current
    val lightSpacingPx = with(density) { 24.dp.toPx() }
    val lightSparkleSizePx = with(density) { 5.5.dp.toPx() }

    Spacer(modifier = modifier.fillMaxSize().drawWithCache {
        val width = size.width
        val height = size.height
        
        // Material Dark mode retains original parameters (55f spacing, 4.5f sparkle, 0x60A09EB0 color)
        // Material Light mode utilizes high-contrast density-independent parameters
        val spacing = if (isDark) 55f else lightSpacingPx
        val sparkleSize = if (isDark) 4.5f else lightSparkleSizePx
        val baseColor = if (isDark) Color(0x60A09EB0) else Color(0xFF000000)
        
        val cols = (width / spacing).toInt() + 1
        val rows = (height / spacing).toInt() + 1
        
        val sparklePath = Path().apply {
            val s = sparkleSize
            moveTo(0f, -s)
            quadraticTo(0f, 0f, s, 0f)
            quadraticTo(0f, 0f, 0f, s)
            quadraticTo(0f, 0f, -s, 0f)
            quadraticTo(0f, 0f, 0f, -s)
            close()
        }

        val totalPoints = (cols + 1) * (rows + 1)
        val pointData = FloatArray(totalPoints * 3)
        var ptr = 0
        for (c in 0..cols) {
            for (r in 0..rows) {
                val cx = c * spacing + (r % 2) * (spacing / 2f)
                val cy = r * spacing
                val offsetPhase = if (isDark) (cx + cy) / 200f else (cx + cy) / 150f
                pointData[ptr++] = cx
                pointData[ptr++] = cy
                pointData[ptr++] = offsetPhase
            }
        }

        onDrawBehind {
            val currentPhase = phase
            val baseAlpha = if (isDark) 0.8f else 0.75f
            val minAlpha = if (isDark) 0.2f else 0.45f
            var i = 0
            val limit = ptr
            while (i < limit) {
                val cx = pointData[i++]
                val cy = pointData[i++]
                val offsetPhase = pointData[i++]
                val alpha = (((sin((currentPhase + offsetPhase).toDouble()).toFloat() + 1f) * 0.5f) * baseAlpha + minAlpha).coerceIn(0f, 1f)
                translate(left = cx, top = cy) {
                    drawPath(
                        path = sparklePath,
                        color = baseColor,
                        alpha = alpha
                    )
                }
            }
        }
    })
}
