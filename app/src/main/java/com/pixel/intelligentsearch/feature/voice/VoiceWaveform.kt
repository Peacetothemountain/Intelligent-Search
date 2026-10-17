package com.pixel.intelligentsearch.feature.voice

import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Google / Pixel brand expressive color palette
internal val GoogleBlue = Color(0xFF4285F4)
internal val GoogleRed = Color(0xFFEA4335)
internal val GoogleYellow = Color(0xFFFBBC04)
internal val GoogleGreen = Color(0xFF34A853)

private class HarmonicWave(
    val freq: Float,
    val speed: Float,
    val phaseOffset: Float,
    val color: Color,
    val glowColor: Color,
    val path: Path = Path()
)

/**
 * Voice waveform: four harmonic waves that follow the speech level while listening, orbiting dots while
 * processing. Amplitude and animation values are read in the draw phase, so they redraw the canvas without
 * recomposing; composition only reruns when the recognition state or the active/idle switch changes.
 */
@Composable
fun VoiceWaveformCanvas(
    amplitude: () -> Float,
    state: VoiceRecognitionState,
    modifier: Modifier = Modifier
) {
    val isListening = state == VoiceRecognitionState.LISTENING
    val isProcessing = state == VoiceRecognitionState.PROCESSING

    // Same spring as before, driven from a snapshot flow so new speech levels never recompose this function.
    val animatedAmpState = remember {
        androidx.compose.animation.core.Animatable(if (state == VoiceRecognitionState.INITIALIZING) 0.10f else 0f)
    }
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow {
            when {
                isListening -> amplitude().coerceIn(0f, 1f)
                state == VoiceRecognitionState.INITIALIZING -> 0.10f
                else -> 0f
            }
        }.collectLatest { target ->
            animatedAmpState.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 650f))
        }
    }

    val processingProgressState = animateFloatAsState(
        targetValue = if (isProcessing) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "ProcessingProgress"
    )

    // derivedStateOf: composition reruns only when these flip, not on every animation frame.
    val isWaveActive by remember(state) {
        androidx.compose.runtime.derivedStateOf {
            isListening || isProcessing || state == VoiceRecognitionState.INITIALIZING ||
                animatedAmpState.value > 0.001f || processingProgressState.value > 0.001f
        }
    }
    val isProcessingVisible by remember(state) {
        androidx.compose.runtime.derivedStateOf { isProcessing || processingProgressState.value > 0.001f }
    }

    val idleZeroState = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val phaseState: androidx.compose.runtime.State<Float>
    val processingAngleState: androidx.compose.runtime.State<Float>
    val rippleProgressState: androidx.compose.runtime.State<Float>

    if (isWaveActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "WaveMotion")
        phaseState = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "WavePhase"
        )
        processingAngleState = if (isProcessingVisible) {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ProcessingAngle"
            )
        } else {
            idleZeroState
        }
        rippleProgressState = if (isListening) {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "RippleProgress"
            )
        } else {
            idleZeroState
        }
    } else {
        phaseState = idleZeroState
        processingAngleState = idleZeroState
        rippleProgressState = idleZeroState
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val glowStroke = remember(density) {
        Stroke(
            width = with(density) { 8.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }
    val primaryStroke = remember(density) {
        Stroke(
            width = with(density) { 3.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    val baseRadiusPx = remember(density) { with(density) { 36.dp.toPx() } }
    val maxExpandPx = remember(density) { with(density) { 64.dp.toPx() } }
    val ampMultiplierPx = remember(density) { with(density) { 24.dp.toPx() } }
    val orbitRadiusMaxPx = remember(density) { with(density) { 26.dp.toPx() } }
    val dotRadiusMaxPx = remember(density) { with(density) { 5.dp.toPx() } }
    val baseAmpPx = remember(density) { with(density) { 5.dp.toPx() } }
    val maxDynamicAmpPx = remember(density) { with(density) { 36.dp.toPx() } }

    // Wave descriptors and their paths are allocated once and reused by every frame.
    val waves = remember {
        arrayOf(
            HarmonicWave(1.0f, 1.0f, 0.0f, GoogleBlue, GoogleBlue.copy(alpha = 0.28f)),
            HarmonicWave(1.4f, -1.1f, 1.3f, GoogleRed, GoogleRed.copy(alpha = 0.28f)),
            HarmonicWave(0.85f, 1.3f, 2.6f, GoogleYellow, GoogleYellow.copy(alpha = 0.28f)),
            HarmonicWave(1.6f, -0.9f, 3.8f, GoogleGreen, GoogleGreen.copy(alpha = 0.28f))
        )
    }

    val orbitalColors = remember {
        arrayOf(GoogleBlue, GoogleRed, GoogleYellow, GoogleGreen)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val centerX = width / 2f

        if (width <= 0f || height <= 0f || !isWaveActive) return@Canvas

        val phase = phaseState.value
        val processingAngle = processingAngleState.value
        val rippleProgress = rippleProgressState.value
        val animatedAmp = animatedAmpState.value
        val processingProgress = processingProgressState.value
        val waveAlpha = (1f - processingProgress).coerceIn(0f, 1f)
        val centerOffset = Offset(centerX, centerY)

        // Radial glowing aura rings when actively listening (zero heap allocations)
        if (isListening && waveAlpha > 0.01f) {
            val rippleRadius = baseRadiusPx + (rippleProgress * maxExpandPx) + (animatedAmp * ampMultiplierPx)
            val rippleAlpha = ((1f - rippleProgress) * (0.35f + animatedAmp * 0.45f) * waveAlpha).coerceIn(0f, 1f)

            if (rippleAlpha > 0.001f) {
                drawCircle(
                    color = GoogleBlue.copy(alpha = rippleAlpha * 0.20f),
                    radius = rippleRadius,
                    center = centerOffset
                )
                drawCircle(
                    color = GoogleGreen.copy(alpha = rippleAlpha * 0.14f),
                    radius = rippleRadius * 0.65f,
                    center = centerOffset
                )
                drawCircle(
                    color = GoogleBlue.copy(alpha = rippleAlpha * 0.10f),
                    radius = rippleRadius * 0.35f,
                    center = centerOffset
                )
            }
        }

        // Orbital morphing animation during PROCESSING state (crossfaded smoothly)
        if (processingProgress > 0.01f) {
            val orbitRadius = orbitRadiusMaxPx * processingProgress
            val dotRadius = (dotRadiusMaxPx * processingProgress).coerceAtLeast(1f)
            val dotCount = orbitalColors.size
            for (i in 0 until dotCount) {
                val angleRad = Math.toRadians(processingAngle + (i * 90.0))
                val dotX = centerX + (orbitRadius * Math.cos(angleRad)).toFloat()
                val dotY = centerY + (orbitRadius * Math.sin(angleRad)).toFloat()
                drawCircle(
                    color = orbitalColors[i].copy(alpha = orbitalColors[i].alpha * processingProgress),
                    radius = dotRadius,
                    center = Offset(dotX, dotY)
                )
            }
        }

        // Multi-harmonic waveforms (rendered when waveAlpha > 0)
        if (waveAlpha > 0.01f && isWaveActive) {
            val steps = 60
            val stepX = width / steps
            val totalAmp = (baseAmpPx + (animatedAmp * maxDynamicAmpPx)) * waveAlpha
            val waveCount = waves.size

            for (wIndex in 0 until waveCount) {
                val w = waves[wIndex]
                w.path.reset()
                w.path.moveTo(0f, centerY)

                for (step in 0..steps) {
                    val x = step * stepX
                    val normalizedX = (step.toFloat() / steps).coerceIn(0f, 1f)
                    // Hann-window envelope: w(x) = sin^2(pi * x), zero at boundaries, maximum at center
                    val sinVal = Math.sin(normalizedX * Math.PI).toFloat()
                    val envelope = sinVal * sinVal

                    val waveAngle = (2 * Math.PI * w.freq * normalizedX) + (phase * w.speed) + w.phaseOffset
                    val y = centerY + (Math.sin(waveAngle).toFloat() * totalAmp * envelope)

                    if (step == 0) {
                        w.path.moveTo(x, y)
                    } else {
                        w.path.lineTo(x, y)
                    }
                }

                // Glow pass (scaled by waveAlpha, reusing preallocated Stroke)
                drawPath(
                    path = w.path,
                    color = w.glowColor.copy(alpha = w.glowColor.alpha * waveAlpha),
                    style = glowStroke
                )

                // Primary stroke pass (scaled by waveAlpha, reusing preallocated Stroke)
                drawPath(
                    path = w.path,
                    color = w.color.copy(alpha = w.color.alpha * waveAlpha),
                    style = primaryStroke
                )
            }
        }
    }
}
