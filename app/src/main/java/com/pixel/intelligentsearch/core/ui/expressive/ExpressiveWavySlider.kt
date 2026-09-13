package com.pixel.intelligentsearch.core.ui.expressive

import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import kotlin.math.sin

/**
 * Material 3 Expressive Wavy Slider with Animated Wave Amplitude and Dynamic Tactile Thumb Expansion.
 *
 * Built with zero-allocation path rendering, dynamic velocity-responsive wave amplitude,
 * and synchronized Pixel haptic ticks.
 */
@Composable
fun ExpressiveWavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    showTrack: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val view = LocalView.current
    val hapticEngine = remember(context) { PixelHapticEngine.get(context) }

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    var isInteracting by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var lastHapticStep by remember { mutableIntStateOf(-1) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)

    // Wave phase animation active when interacting or transitioning
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_wave_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Dynamic wave amplitude swells on press/drag
    val targetAmplitude = if (isInteracting) 6.5f else 3.2f
    val waveAmplitudeAnim by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = ExpressiveMotionTokens.bouncySpring(),
        label = "wave_amplitude"
    )

    // Thumb smoothly morphs from circle to expressive vertical pill on interaction
    val thumbWidthAnim by animateDpAsState(
        targetValue = if (isInteracting) 10.dp else 16.dp,
        animationSpec = ExpressiveMotionTokens.bouncySpring(),
        label = "thumb_width"
    )
    val thumbHeightAnim by animateDpAsState(
        targetValue = if (isInteracting) 36.dp else 16.dp,
        animationSpec = ExpressiveMotionTokens.bouncySpring(),
        label = "thumb_height"
    )

    // Pre-allocated reusable path to avoid GC churn in draw loop
    val reusablePath = remember { Path() }

    val updateValueFromX = { rawX: Float ->
        val newFraction = (rawX / widthPx).coerceIn(0f, 1f)
        val calculatedValue = valueRange.start + newFraction * rangeSpan
        val finalValue = if (steps > 0) {
            val stepSize = rangeSpan / (steps + 1)
            val currentStep = kotlin.math.round((calculatedValue - valueRange.start) / stepSize).toInt()
            if (currentStep != lastHapticStep) {
                lastHapticStep = currentStep
                hapticEngine.performHaptic(view, PixelHapticType.TICK)
            }
            (valueRange.start + currentStep * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            calculatedValue
        }
        currentOnValueChange(finalValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clearAndSetSemantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                setProgress { target ->
                    val clamped = target.coerceIn(valueRange.start, valueRange.endInclusive)
                    currentOnValueChange(clamped)
                    true
                }
            }
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isInteracting = true
                        hapticEngine.performHaptic(view, PixelHapticType.TICK)
                        updateValueFromX(offset.x)
                        tryAwaitRelease()
                        isInteracting = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isInteracting = true
                        hapticEngine.performHaptic(view, PixelHapticType.TICK)
                        updateValueFromX(offset.x)
                    },
                    onDragEnd = {
                        isInteracting = false
                        lastHapticStep = -1
                    },
                    onDragCancel = {
                        isInteracting = false
                        lastHapticStep = -1
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateValueFromX(change.position.x)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackStroke = 6.dp.toPx()
            val centerY = size.height / 2f
            val amplitudePx = waveAmplitudeAnim.dp.toPx()
            val frequency = 0.045f

            val currentThumbW = thumbWidthAnim.toPx()
            val currentThumbH = thumbHeightAnim.toPx()

            val thumbX = (fraction * size.width).coerceIn(currentThumbW / 2f, size.width - currentThumbW / 2f)

            // Draw step tick marks if configured
            if (steps > 0) {
                val tickRadius = 2.dp.toPx()
                val segments = steps + 1
                val tickSpacing = size.width / segments
                for (i in 1..steps) {
                    val cx = i * tickSpacing
                    drawCircle(
                        color = if (cx <= thumbX) activeColor.copy(alpha = 0.4f) else inactiveColor.copy(alpha = 0.6f),
                        radius = tickRadius,
                        center = Offset(cx, centerY + 16.dp.toPx())
                    )
                }
            }

            if (showTrack) {
                // Active Track: Expressive dynamic sine wave
                reusablePath.reset()
                reusablePath.moveTo(0f, centerY)

                val stepPx = 4f
                var x = 0f
                while (x < thumbX) {
                    val waveOffset = sin((x * frequency - phase).toDouble()).toFloat() * amplitudePx
                    reusablePath.lineTo(x, centerY + waveOffset)
                    x += stepPx
                }
                reusablePath.lineTo(thumbX, centerY)

                drawPath(
                    path = reusablePath,
                    color = activeColor,
                    style = Stroke(
                        width = trackStroke,
                        cap = StrokeCap.Round
                    )
                )

                // Inactive Track: Smooth straight baseline
                if (thumbX < size.width) {
                    drawLine(
                        color = inactiveColor,
                        start = Offset(thumbX, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw dynamic expanding thumb
            val thumbCorner = currentThumbW / 2f
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbX - currentThumbW / 2f, centerY - currentThumbH / 2f),
                size = Size(currentThumbW, currentThumbH),
                cornerRadius = CornerRadius(thumbCorner, thumbCorner)
            )
        }
    }
}
