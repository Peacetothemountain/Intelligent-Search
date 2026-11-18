package com.pixel.intelligentsearch.feature.tabs

import android.os.PowerManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.MeshGradientScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.feature.tabs.ui.rememberAnimationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

// Ported faithfully from Intelligent Search (core/ui/DynamicMeshShader.kt: pageGlow, pageGlowTones,
// configurePageGlow and the mesh clock). Only the thermal check reads PowerManager directly instead of the app's
// ADPF manager; it stops the drift at the same level (severe).

/**
 * The settings pages' glow: a smooth light drifting slowly behind the title, strongest at the top and gone about two
 * fifths of the way down. Its colours are the theme's deep tones: the primary hue and the hues 20° either side of it,
 * each richer and much darker ([pageGlowTones]). So it follows each device's wallpaper palette, and on a light page it
 * sits at about half the strength, a soft tint rather than a stain. [strength] scales it (0 hides it and stops its
 * clock); [clock], when given, is the drift's time instead of its own. It stands still with animations removed, under
 * severe thermal throttling and in the background.
 */
@Composable
fun Modifier.pageGlow(strength: () -> Float = { 1f }, clock: State<Float>? = null, depth: Float = PAGE_GLOW_DEPTH): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val tones = remember(scheme.primary) { pageGlowTones(scheme.primary) }
    val peak = if (dark) PAGE_GLOW_PEAK_DARK else PAGE_GLOW_PEAK_LIGHT
    val currentStrength by rememberUpdatedState(strength)
    val ownTime = rememberMeshClock(isPaused = clock != null || strength() <= 0f, frameGapMs = PAGE_GLOW_FRAME_GAP_MS)
    val time = clock ?: ownTime
    // The configuration block runs in the draw phase, so the clock only invalidates drawing.
    val painter = remember(tones, peak) {
        MeshGradientPainter(rows = 2, columns = 2, hasBicubicColor = true) {
            configurePageGlow(time.value * PAGE_GLOW_PACE, tones, peak)
        }
    }
    return drawBehind {
        val s = currentStrength().coerceIn(0f, 1f)
        if (s <= 0f) return@drawBehind
        with(painter) { draw(Size(size.width, size.height * depth), alpha = s) }
    }
}

/** How strong the glow is at its brightest, over a dark page and over a light one. */
internal const val PAGE_GLOW_PEAK_DARK = 0.5f
internal const val PAGE_GLOW_PEAK_LIGHT = 0.24f

/** How far down the page the glow reaches, as a fraction of its height. */
internal const val PAGE_GLOW_DEPTH = 0.42f

/** How fast it drifts: its slowest motion takes about 22 s, its quickest about 14 s. */
private const val PAGE_GLOW_PACE = 2.2f

/** Pause after each of the page glow's ticks; with the wait for the next frame, they land about 100 ms apart. */
internal const val PAGE_GLOW_FRAME_GAP_MS = 90L

/**
 * The glow's three tones from the theme's [primary]: its hue 20° one way, its own hue, and 20° the other way, each
 * at the morph's deep tone, much darker and richer than the role itself. Their richness follows the primary's own,
 * so a muted or monochrome theme gets a muted or grey glow.
 */
internal fun pageGlowTones(primary: Color): List<Color> {
    val r = primary.red
    val g = primary.green
    val b = primary.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    val deepSaturation = (saturation * 2.1f).coerceIn(0f, 0.58f)
    return listOf(-20f, 0f, 20f).map { Color.hsv((hue + it + 360f) % 360f, deepSaturation, 0.46f) }
}

/**
 * The glow's 3 × 3 vertices over its own area: the top row at full strength (the first tone at the left, the second
 * drifting along the top, the third at the right), the middle row at about a third, its centre orbiting slowly, and
 * the bottom row clear, so it fades out into the page.
 */
private fun MeshGradientScope.configurePageGlow(t: Float, tones: List<Color>, peak: Float) {
    val (left, middle, right) = tones
    fun Color.at(strength: Float) = copy(alpha = peak * strength)
    setVertex(0, 0, Offset(0f, 0f), left.at(1f))
    setVertex(0, 1, Offset(0.5f + 0.18f * sin(t * 0.13f), 0f), middle.at(0.85f))
    setVertex(0, 2, Offset(1f, 0f), right.at(0.9f))

    setVertex(1, 0, Offset(0f, 0.43f + 0.095f * sin(t * 0.15f)), left.at(0.35f))
    setVertex(1, 1, Offset(0.5f + 0.16f * sin(t * 0.21f), 0.38f + 0.12f * cos(t * 0.17f)), middle.at(0.45f))
    setVertex(1, 2, Offset(1f, 0.43f + 0.095f * cos(t * 0.19f)), right.at(0.32f))

    setVertex(2, 0, Offset(0f, 1f), left.at(0f))
    setVertex(2, 1, Offset(0.5f, 1f), middle.at(0f))
    setVertex(2, 2, Offset(1f, 1f), right.at(0f))
}

/**
 * Seconds the glow has drifted. Its drift is far too slow to need every display frame, so the clock waits
 * [frameGapMs] after each tick and then takes the next frame. It stands still while [isPaused], in the background,
 * under severe thermal throttling and while the user has removed animations. Time is taken from frame timestamps, so
 * a late frame doesn't slow it.
 */
@Composable
internal fun rememberMeshClock(isPaused: Boolean, frameGapMs: Long): MutableFloatState {
    val throttled by rememberSevereThermalThrottling()
    val animationsEnabled by rememberAnimationsEnabled()
    val lifecycleOwner = LocalLifecycleOwner.current
    val timeState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycleOwner, isPaused, throttled, animationsEnabled, frameGapMs) {
        if (!isPaused && !throttled && animationsEnabled) {
            // A tick that comes much later than its gap (a stalled frame) moves the drift by no more than this.
            val longestStep = (frameGapMs + 70L) / 1_000f
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrame = 0L
                while (isActive) {
                    withFrameNanos { frameTime ->
                        if (lastFrame == 0L) lastFrame = frameTime
                        val dt = ((frameTime - lastFrame) / 1_000_000_000f).coerceIn(0f, longestStep)
                        timeState.floatValue = (timeState.floatValue + dt) % MESH_CLOCK_PERIOD
                        lastFrame = frameTime
                    }
                    delay(frameGapMs)
                }
            }
        }
    }
    return timeState
}

/** Every mesh drift frequency completes whole cycles in this time, so the clock wraps without a jump. */
private const val MESH_CLOCK_PERIOD = (2000.0 * Math.PI).toFloat()

/** Whether the phone is throttling severely or worse right now (the level at which the app stops its drifts). */
@Composable
private fun rememberSevereThermalThrottling(): State<Boolean> {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    return produceState(initialValue = (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE, power) {
        if (power == null) return@produceState
        val listener = PowerManager.OnThermalStatusChangedListener { value = it >= PowerManager.THERMAL_STATUS_SEVERE }
        runCatching { power.addThermalStatusListener(context.mainExecutor, listener) }
        awaitDispose { runCatching { power.removeThermalStatusListener(listener) } }
    }
}
