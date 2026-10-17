package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Official 3D Bugdroid holding a 4-color Google star balloon with expressive breeze physics:
 * The balloon lightly floats and sways organically in the breeze anchored to Bugdroid's hand,
 * while Bugdroid gently hovers up and down with natural eye blinking.
 * High-resolution multi-layer graphicsLayer implementation with zero clipping or rippling.
 */
@Composable
fun AnimatedBugdroidEmoji(
    modifier: Modifier = Modifier
) {
    val resources = androidx.compose.ui.platform.LocalResources.current

    val balloonBitmap = remember(resources) {
        android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_bugdroid_balloon,
            android.graphics.BitmapFactory.Options().apply {
                inScaled = false
            }
        ).asImageBitmap()
    }

    val bodyOpenBitmap = remember(resources) {
        android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_bugdroid_body,
            android.graphics.BitmapFactory.Options().apply {
                inScaled = false
            }
        ).asImageBitmap()
    }

    val bodyHalfBitmap = remember(resources) {
        android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_bugdroid_body_half,
            android.graphics.BitmapFactory.Options().apply {
                inScaled = false
            }
        )?.asImageBitmap() ?: bodyOpenBitmap
    }

    val bodyBlinkBitmap = remember(resources) {
        android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_bugdroid_body_blink,
            android.graphics.BitmapFactory.Options().apply {
                inScaled = false
            }
        )?.asImageBitmap() ?: bodyOpenBitmap
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bugdroid_motion_transition")

    // Both phases stay State and are read only inside graphicsLayer {} below, so the float and sway run
    // in the draw phase instead of recomposing the mascot every frame.
    // Bugdroid body hover float: gentle vertical sine wave
    val hoverPhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hover_phase"
    )

    // Breeze sway phase: independent natural period for organic air currents
    val breezePhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breeze_phase"
    )

    // 0 = open, 1 = half closed/opening, 2 = fully closed
    var blinkPhase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val random = java.util.Random()
        while (true) {
            // Idle interval between blinks: around 3.2 to 4.2 seconds
            delay(3200L + random.nextInt(1000))
            // Natural blink down
            blinkPhase = 1
            delay(35L)
            blinkPhase = 2
            delay(80L)
            blinkPhase = 1
            delay(35L)
            blinkPhase = 0

            // 15% chance of an expressive double-blink matching the settings video
            if (random.nextFloat() < 0.15f) {
                delay(120L)
                blinkPhase = 1
                delay(30L)
                blinkPhase = 2
                delay(70L)
                blinkPhase = 1
                delay(30L)
                blinkPhase = 0
            }
        }
    }

    val currentBodyBitmap = when (blinkPhase) {
        1 -> bodyHalfBitmap
        2 -> bodyBlinkBitmap
        else -> bodyOpenBitmap
    }


    Box(
        // The default size comes after the caller's modifier, so a size the caller passes wins.
        modifier = modifier.size(width = 40.dp, height = 56.dp),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: Balloon & String (drawn behind hand, anchored at hand pivot 0.2033f, 0.4455f)
        Image(
            bitmap = balloonBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val breeze = breezePhase.value
                    val bodyHoverPx = sin(hoverPhase.value) * 1.5.dp.toPx()
                    transformOrigin = TransformOrigin(0.2033f, 0.4455f)
                    rotationZ = (sin(breeze) * 3.5f) + (sin(breeze * 0.5f) * 1.2f)
                    translationX = cos(breeze) * 0.8.dp.toPx()
                    translationY = bodyHoverPx + sin(breeze * 1.3f) * 1.0.dp.toPx()
                }
        )

        // Layer 2: Bugdroid Body (hand grips over string anchor, floating & blinking)
        Image(
            bitmap = currentBodyBitmap,
            contentDescription = "Bugdroid",
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = sin(hoverPhase.value) * 1.5.dp.toPx()
                }
        )
    }
}
