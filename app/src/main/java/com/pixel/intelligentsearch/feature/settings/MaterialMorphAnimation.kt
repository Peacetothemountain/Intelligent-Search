package com.pixel.intelligentsearch.feature.settings

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled

/** Forty Material shapes: rounded polygons and stars, sharp and soft, in a shuffled order. */
private fun materialShapes(): List<RoundedPolygon> {
    val shapes = mutableListOf<RoundedPolygon>()
    for (i in 3..12) shapes.add(RoundedPolygon(numVertices = i, rounding = CornerRounding(radius = 0.2f)))
    for (i in 3..12) shapes.add(RoundedPolygon(numVertices = i, rounding = CornerRounding(radius = 1f)))
    for (i in 4..13) shapes.add(RoundedPolygon.star(numVerticesPerRadius = i, innerRadius = 0.5f, rounding = CornerRounding(radius = 0.2f)))
    for (i in 4..13) shapes.add(RoundedPolygon.star(numVerticesPerRadius = i, innerRadius = 0.7f, rounding = CornerRounding(radius = 0.6f)))
    return shapes.shuffled()
}

/** Haptics are spaced at least this far apart, so shapes landing together feel like one tap, not a buzz. */
private const val HAPTIC_SPACING_MS = 90L

/**
 * The Material Morph animation shown under the last card of some settings pages (turned on in Appearance).
 * Shapes drop in from just under the bottom card, bounce on the bottom of the screen as if it were a trampoline,
 * change shape and give a haptic tap on every bounce, and leave by the sides; while the page stays open, new shapes
 * keep dropping in, at most five at a time. The motion is real physics, see [MorphBouncePhysics]. Nothing is drawn
 * when the phone's animations are turned off.
 */
@Composable
fun MaterialMorphAnimation(modifier: Modifier = Modifier) {
    val animationsEnabled by rememberAnimationsEnabled()
    if (!animationsEnabled) return

    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val hapticEngine = remember(context) { PixelHapticEngine.get(context) }
    val physics = remember { MorphBouncePhysics() }
    val shapes = remember { materialShapes() }
    // One Morph per shape pair, made when a shape bounces into its next form.
    val morphs = remember { HashMap<Long, Morph>() }
    fun morphFor(shapeIndex: Int): Morph {
        val from = Math.floorMod(shapeIndex - 1, shapes.size)
        val to = Math.floorMod(shapeIndex, shapes.size)
        return morphs.getOrPut(from.toLong() * 1000 + to) { Morph(shapes[from], shapes[to]) }
    }

    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer
    )
    val nativePath = remember { android.graphics.Path() }
    val composePath = remember(nativePath) { nativePath.asComposePath() }
    // Bumped every frame; the Canvas reads it so only drawing reruns, never composition.
    val frame = remember { mutableLongStateOf(0L) }

    // The scene is drawn at one dp per centimetre.
    val pxPerMetre = density.density * 100f

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        if (widthPx <= 0f || heightPx <= 0f) return@BoxWithConstraints

        // Shape sizes as before: a share of the screen width, scaled for small and large screens and font size.
        val dpScale = (configuration.smallestScreenWidthDp / 411f).coerceIn(0.65f, 1.4f)
        val fontAdjust = 1f / density.fontScale.coerceIn(0.8f, 1.5f)
        val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
        val radiiMetres = remember(widthPx, heightPx, dpScale, fontAdjust, screenWidthPx) {
            (0 until 4).map { i ->
                val sizePx = (screenWidthPx * 0.038f * dpScale * fontAdjust * (1f + i * 0.2f)).coerceIn(16f, (heightPx * 0.35f).coerceAtLeast(24f))
                sizePx / 2f / pxPerMetre
            }
        }
        LaunchedEffect(widthPx, heightPx, radiiMetres) {
            physics.resize(widthPx / pxPerMetre, heightPx / pxPerMetre, radiiMetres)
        }
        LaunchedEffect(Unit) {
            var lastHaptic = 0L
            val onBounce = MorphBouncePhysics.BounceListener { _, _ ->
                val now = SystemClock.uptimeMillis()
                if (now - lastHaptic >= HAPTIC_SPACING_MS) {
                    lastHaptic = now
                    hapticEngine.performPredictiveBackHaptic(view)
                }
            }
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                physics.advance((now - last) / 1_000_000_000f, onBounce)
                last = now
                frame.longValue = now
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            frame.longValue
            for (body in physics.bodies) {
                val radiusPx = body.radius * pxPerMetre
                val cx = body.x * pxPerMetre
                val cy = body.y * pxPerMetre
                // On the trampoline the shape squashes flat and widens (keeping its area), pressed against the
                // bottom edge; part of the dip shows as the bed giving way below the screen edge.
                val squash = (body.penetration / (2f * body.radius) * 0.55f).coerceIn(0f, 0.32f)
                val scaleY = 1f - squash
                val scaleX = 1f / scaleY
                val drawCy = cy + radiusPx * squash
                nativePath.rewind()
                morphFor(body.shapeIndex).toPath(progress = body.morphProgress, path = nativePath)
                translate(left = cx, top = drawCy) {
                    scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero) {
                        rotate(body.angle, pivot = Offset.Zero) {
                            scale(radiusPx, pivot = Offset.Zero) {
                                drawPath(path = composePath, color = colors[body.id % colors.size], alpha = 0.9f)
                            }
                        }
                    }
                }
            }
        }
    }
}
