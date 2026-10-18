package com.pixel.intelligentsearch.feature.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import com.pixel.intelligentsearch.feature.voice.Dictation

/**
 * Voice search on the search screen, Rambler style: the search bar listens until the user taps the check button, then
 * fills in what they meant (filler sounds, repeats and spoken corrections tidied). Speech goes through the phone's
 * speech recognizer (on a Pixel, Google's on-device engine); Android gives apps no way to use the keyboard's own
 * microphone, so the search bar's mic button and the assistant gesture (with Start Assistant with Voice on) start this.
 */
@Stable
internal class OverlayVoice(
    private val dictation: Dictation,
    private val askPermission: () -> Unit,
    private val begin: () -> Unit,
) {
    /** True from the moment listening starts until the words are delivered or it is cancelled. */
    val active: Boolean get() = dictation.phase.value != Dictation.Phase.Idle

    /** The current voice level, 0 to 1, read in the draw phase so the animation follows speech without recomposing. */
    val level: Float get() = dictation.amplitude.floatValue

    internal var permissionGranted: () -> Boolean = { false }

    /** Starts listening, asking for the microphone first if it hasn't been allowed yet. */
    fun start() = if (permissionGranted()) begin() else askPermission()

    /** Done speaking (the check button): stop and fill in the words. */
    fun finish() = dictation.finish()

    /** Cancel (the close button): stop and keep nothing. */
    fun stop() = dictation.cancel()
}

/** [onHeard] gets the finished, tidied words; [onProblem] a short message when nothing usable was heard. */
@Composable
internal fun rememberOverlayVoice(onHeard: (text: String) -> Unit, onProblem: (String) -> Unit): OverlayVoice {
    val context = LocalContext.current
    val heard by rememberUpdatedState(onHeard)
    val problem by rememberUpdatedState(onProblem)
    val dictation = remember { Dictation(context) }
    val begin = remember { { dictation.start(onFinished = { heard(it) }, onProblem = { problem(it) }) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else problem("Allow the microphone to search by voice.")
    }
    val voice = remember { OverlayVoice(dictation, askPermission = { permission.launch(Manifest.permission.RECORD_AUDIO) }, begin = begin) }
    voice.permissionGranted = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    DisposableEffect(Unit) { onDispose { dictation.cancel() } }
    return voice
}

/**
 * The search bar's listening animation, matched to Gboard's Rambler voice strip: a bright line hugging the bar's bottom
 * edge and curving up into its corners, and above it a soft glow of broad hills that drift sideways and rise with the
 * voice, built from a fine diamond halftone of dots that fade as they climb. All in the colour scheme's primary tone.
 * Drawn above the bar's plate and below its text; the plate shader itself is untouched.
 */
internal fun Modifier.listeningGlow(active: Boolean, level: () -> Float, cornerRadius: Dp): Modifier = composed {
    val glow = MaterialTheme.colorScheme.primary
    val strength by animateFloatAsState(if (active) 1f else 0f, tween(320), label = "listening")
    val clock = rememberInfiniteTransition(label = "listening-wave").animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "phase",
    )
    if (strength == 0f) return@composed this
    drawWithCache {
        val w = size.width
        val h = size.height
        val radius = cornerRadius.toPx().coerceAtMost(h / 2)
        val spacing = 4.5f * density
        val dot = 1.5f * density
        val line = 2.2f * density
        val columns = (w / spacing).toInt() + 2
        val rows = (h / spacing).toInt() + 2
        val path = Path()
        val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = android.graphics.BlurMaskFilter(12f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val lineBrush = Brush.verticalGradient(
            0f to glow.copy(alpha = 0f), 0.38f to glow.copy(alpha = 0f), 0.78f to glow, 1f to glow, startY = 0f, endY = h,
        )
        val buckets = Array(4) { ArrayList<Offset>(columns * rows / 4) }
        onDrawWithContent {
            val t = clock.value
            val voice = level().coerceIn(0f, 1f)
            // Wave height (px from the bottom) at x: two drifting hills, taller while speaking.
            fun crest(x: Float): Float {
                val u = x / w
                val a = 0.5f + 0.5f * kotlin.math.sin((u * 2.2f * Math.PI).toFloat() + t)
                val b = 0.55f + 0.45f * kotlin.math.sin((u * 1.1f * Math.PI).toFloat() - 0.7f * t + 1.3f)
                return h * (0.22f + (0.16f + 0.42f * voice) * a * b)
            }
            // Soft glow under the wave, brightest at the bottom edge.
            path.reset()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w) { path.lineTo(x, h - crest(x)); x += w / 48f }
            path.lineTo(w, h - crest(w)); path.lineTo(w, h); path.close()
            // Soft-edged glow: the wave shape blurred, so its crest melts into the bar like Rambler's.
            drawIntoCanvas { canvas ->
                glowPaint.shader = android.graphics.LinearGradient(
                    0f, h * 0.15f, 0f, h,
                    intArrayOf(glow.copy(alpha = 0f).toArgb(), glow.copy(alpha = 0.30f).toArgb(), glow.copy(alpha = 0.70f).toArgb()),
                    floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP,
                )
                glowPaint.alpha = (strength * 255).toInt()
                canvas.nativeCanvas.drawPath(path.asAndroidPath(), glowPaint)
            }
            // Halftone: a diamond grid of dots inside the glow, fading as they rise.
            buckets.forEach { it.clear() }
            for (r in 0 until rows) {
                val y = h - r * spacing
                val offset = if (r % 2 == 0) 0f else spacing / 2
                for (c in 0 until columns) {
                    val px = c * spacing + offset
                    if (px > w) continue
                    val top = h - crest(px)
                    if (y < top || y > h - line * 2) continue
                    val depth = ((y - top) / (h - top).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    if (depth < 0.12f) continue
                    buckets[(depth * 3.99f).toInt()].add(Offset(px, y))
                }
            }
            buckets.forEachIndexed { i, points ->
                if (points.isNotEmpty()) drawPoints(points, PointMode.Points, glow, strokeWidth = dot, cap = StrokeCap.Round, alpha = strength * (0.14f + 0.20f * i))
            }
            // The bright line along the bottom edge, rising into the corners.
            // fading out as it climbs the sides.
            drawRoundRect(lineBrush, cornerRadius = CornerRadius(radius), style = Stroke(line * 4f), alpha = strength * 0.25f)
            drawRoundRect(lineBrush, topLeft = Offset(line / 2, line / 2), size = Size(w - line, h - line), cornerRadius = CornerRadius(radius - line / 2), style = Stroke(line), alpha = strength)
            drawContent()
        }
    }
}
