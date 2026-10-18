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
 * The search bar's listening animation, matched to Gboard's Rambler voice strip. A field of light in the colour
 * scheme's primary tone rises from a glow line along the bottom edge: low and calm when quiet, filling nearly the
 * whole bar while speaking. Dark rounded valleys press down from the top and travel quickly across it. The light is a
 * true halftone, a diamond grid of dots that grow where the light is glow and shrink to nothing at its edge.
 * Drawn above the bar's plate and below its icons; the plate shader itself is untouched.
 */
internal fun Modifier.listeningGlow(active: Boolean, level: () -> Float, cornerRadius: Dp): Modifier = composed {
    // All in the Material You primary tone, as Rambler's strip is in the keyboard's accent: no white.
    val glow = MaterialTheme.colorScheme.primary
    val strength by animateFloatAsState(if (active) 1f else 0f, tween(280), label = "listening")
    // One loop of the field's motion; the waves inside run at whole multiples of it, so the loop is seamless.
    val clock = rememberInfiniteTransition(label = "listening-field").animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "phase",
    )
    // The voice level eased a little more, so the field swells and settles smoothly between frames.
    val smoothed = remember { floatArrayOf(0f) }
    if (strength == 0f) return@composed this
    drawWithCache {
        val w = size.width
        val h = size.height
        val radius = cornerRadius.toPx().coerceAtMost(h / 2)
        val spacing = 2.8f * density
        val maxDot = 2.2f * density
        val line = 1.8f * density
        val columns = (w / spacing).toInt() + 2
        val rows = (h / spacing).toInt() + 2
        val path = Path()
        val sizes = 10
        val buckets = Array(sizes) { ArrayList<Offset>(columns * rows / sizes) }
        val tops = FloatArray(columns * 2)
        val fieldPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = android.graphics.BlurMaskFilter(10f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val lineBrush = Brush.verticalGradient(
            0f to glow.copy(alpha = 0f), 0.42f to glow.copy(alpha = 0f), 0.8f to glow, 1f to glow, startY = 0f, endY = h,
        )
        onDrawWithContent {
            val t = clock.value
            val target = level().coerceIn(0f, 1f)
            smoothed[0] += (target - smoothed[0]) * (if (target > smoothed[0]) 0.45f else 0.12f)
            val loud = Math.pow(smoothed[0].toDouble(), 0.7).toFloat()
            // How high the light reaches (fraction of the bar) at x: three travelling waves make the valleys.
            fun reach(x: Float): Float {
                val u = x / w * (2 * Math.PI).toFloat()
                val n = 0.5f + 0.5f * (
                    0.50f * kotlin.math.sin(1.6f * u + 2f * t) +
                        0.32f * kotlin.math.sin(2.7f * u - 3f * t + 1.1f) +
                        0.18f * kotlin.math.sin(4.3f * u + 5f * t + 2.3f)
                    )
                // Speaking lifts the light to fill the bar; the waves carve dark valleys down into it.
                val top = (0.26f + 1.05f * loud) * (0.60f + 0.50f * n)
                return top.coerceIn(0.14f, 1.05f)
            }
            // The soft wash of light under the halftone.
            path.reset()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w) { path.lineTo(x, h - h * reach(x)); x += w / 64f }
            path.lineTo(w, h - h * reach(w)); path.lineTo(w, h); path.close()
            drawIntoCanvas { canvas ->
                fieldPaint.shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(glow.copy(alpha = 0.30f).toArgb(), glow.copy(alpha = 0.55f).toArgb(), glow.copy(alpha = 0.80f).toArgb()),
                    floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP,
                )
                fieldPaint.alpha = (strength * 255).toInt()
                canvas.nativeCanvas.drawPath(path.asAndroidPath(), fieldPaint)
            }
            // The halftone: dots grow smoothly from nothing at the light's edge to full size low down, so the field has
            // no hard rim, just a fine texture that thickens with the light.
            buckets.forEach { it.clear() }
            for (k in 0..1) for (c in 0 until columns) {
                tops[k * columns + c] = h - h * reach(c * spacing + k * spacing / 2)
            }
            for (r in 0 until rows) {
                val y = h - line - r * spacing
                if (y < 0f) break
                val k = r % 2
                val offset = k * spacing / 2
                for (c in 0 until columns) {
                    val px = c * spacing + offset
                    if (px > w) continue
                    val top = tops[k * columns + c]
                    if (y < top) continue
                    val depth = ((y - top) / (h - top).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val light = (depth * 2.4f).coerceAtMost(1f)
                    if (light < 0.08f) continue
                    buckets[(light * (sizes - 1)).toInt()].add(Offset(px, y))
                }
            }
            buckets.forEachIndexed { i, points ->
                if (points.isNotEmpty()) {
                    val light = (i + 0.5f) / sizes
                    drawPoints(points, PointMode.Points, glow, strokeWidth = maxDot * (0.2f + 0.8f * light), cap = StrokeCap.Round, alpha = strength * (0.35f + 0.6f * light))
                }
            }
            // The glow line along the bottom edge, fading out as it climbs into the corners.
            drawRoundRect(lineBrush, cornerRadius = CornerRadius(radius), style = Stroke(line * 4f), alpha = strength * 0.30f)
            drawRoundRect(lineBrush, topLeft = Offset(line / 2, line / 2), size = Size(w - line, h - line), cornerRadius = CornerRadius(radius - line / 2), style = Stroke(line), alpha = strength)
            drawContent()
        }
    }
}
