package com.pixel.intelligentsearch.feature.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
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

/** Whether Battery Saver is on, following changes while shown. */
@Composable
private fun rememberPowerSaveMode(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true, context) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                value = powerManager?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitDispose { context.unregisterReceiver(receiver) }
    }
}

/**
 * The Material Morph animation shown under the last card of some settings pages (turned on in Appearance).
 * Shapes drop in from just under the bottom card, bounce on the bottom of the screen as if it were a trampoline,
 * change shape and give a haptic tap on every bounce, and leave by the sides; while the page stays open, new shapes
 * keep dropping in, at most five at a time. The motion is real physics, see [MorphBouncePhysics]: shapes also knock
 * into each other, stretch a little when falling fast, cast a soft shadow on the floor that darkens as they land,
 * and briefly glow on every bounce, with a haptic tap whose strength follows the impact. Nothing is drawn when the
 * phone's animations are turned off or Battery Saver is on.
 */
@Composable
fun MaterialMorphAnimation(modifier: Modifier = Modifier) {
    val animationsEnabled by rememberAnimationsEnabled()
    val powerSaveMode by rememberPowerSaveMode()
    if (!animationsEnabled || powerSaveMode) return

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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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

        // Shape sizes: a share of the screen width (5.5%, up to 1.6x for the largest), scaled for small and large
        // screens and font size.
        val dpScale = (configuration.smallestScreenWidthDp / 411f).coerceIn(0.65f, 1.4f)
        val fontAdjust = 1f / density.fontScale.coerceIn(0.8f, 1.5f)
        val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
        val radiiMetres = remember(widthPx, heightPx, dpScale, fontAdjust, screenWidthPx) {
            (0 until 4).map { i ->
                val sizePx = (screenWidthPx * 0.055f * dpScale * fontAdjust * (1f + i * 0.2f)).coerceIn(16f, (heightPx * 0.35f).coerceAtLeast(24f))
                sizePx / 2f / pxPerMetre
            }
        }
        LaunchedEffect(widthPx, heightPx, radiiMetres) {
            physics.resize(widthPx / pxPerMetre, heightPx / pxPerMetre, radiiMetres)
        }
        LaunchedEffect(Unit) {
            var lastHaptic = 0L
            val onBounce = MorphBouncePhysics.BounceListener { _, impactSpeed ->
                val now = SystemClock.uptimeMillis()
                if (now - lastHaptic >= HAPTIC_SPACING_MS) {
                    lastHaptic = now
                    // A gentle landing is a soft tick, a hard one the full standard tick.
                    val strength = 0.25f + 0.75f * ((impactSpeed - 1f) / 5f).coerceIn(0f, 1f)
                    hapticEngine.performImpactHaptic(view, strength)
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
            val floorPx = size.height
            // Shadows first, so every shape is drawn above every shadow.
            for (body in physics.bodies) {
                val radiusPx = body.radius * pxPerMetre
                val heightAboveFloor = (floorPx - (body.y * pxPerMetre + radiusPx)).coerceAtLeast(0f)
                val closeness = 1f - (heightAboveFloor / floorPx).coerceIn(0f, 1f)
                val shadowWidth = radiusPx * 2f * (0.55f + 0.6f * closeness)
                val shadowHeight = shadowWidth * 0.24f
                // A dark shadow disappears on a dark background, so there it is a soft reflection in the shape's colour.
                val shadowAlpha = (if (isDark) 0.30f else 0.26f) * closeness * closeness
                if (shadowAlpha < 0.01f) continue
                val shadowColor = if (isDark) colors[body.id % colors.size] else Color.Black
                val center = Offset(body.x * pxPerMetre, floorPx - shadowHeight * 0.35f)
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(shadowColor.copy(alpha = shadowAlpha), Color.Transparent),
                        center = center,
                        radius = shadowWidth / 2f
                    ),
                    topLeft = Offset(center.x - shadowWidth / 2f, center.y - shadowHeight / 2f),
                    size = Size(shadowWidth, shadowHeight)
                )
            }
            for (body in physics.bodies) {
                val radiusPx = body.radius * pxPerMetre
                val cx = body.x * pxPerMetre
                val cy = body.y * pxPerMetre
                val baseColor = colors[body.id % colors.size]
                // On the trampoline the shape squashes flat and widens (keeping its area), pressed against the
                // bottom edge; part of the dip shows as the bed giving way below the screen edge.
                val squash = (body.penetration / (2f * body.radius) * 0.55f).coerceIn(0f, 0.32f)
                // In the air it stretches slightly along its path when moving fast.
                val speed = kotlin.math.sqrt(body.vx * body.vx + body.vy * body.vy)
                val stretch = if (squash > 0f) 0f else (speed / 6f * 0.14f).coerceIn(0f, 0.14f)
                val travelDegrees = Math.toDegrees(kotlin.math.atan2(body.vy, body.vx).toDouble()).toFloat()
                val drawCy = cy + radiusPx * squash
                nativePath.rewind()
                morphFor(body.shapeIndex).toPath(progress = body.morphProgress, path = nativePath)
                translate(left = cx, top = drawCy) {
                    rotate(travelDegrees, pivot = Offset.Zero) {
                        scale(scaleX = 1f + stretch, scaleY = 1f / (1f + stretch), pivot = Offset.Zero) {
                            rotate(-travelDegrees, pivot = Offset.Zero) {
                                scale(scaleX = 1f / (1f - squash), scaleY = 1f - squash, pivot = Offset.Zero) {
                                    rotate(body.angle, pivot = Offset.Zero) {
                                        // The glow: a soft halo and a brief lift towards white on every bounce.
                                        if (body.glow > 0f) {
                                            scale(radiusPx * (1f + 0.28f * body.glow), pivot = Offset.Zero) {
                                                drawPath(path = composePath, color = baseColor, alpha = 0.28f * body.glow)
                                            }
                                        }
                                        scale(radiusPx, pivot = Offset.Zero) {
                                            drawPath(
                                                path = composePath,
                                                color = lerp(baseColor, Color.White, 0.4f * body.glow),
                                                alpha = 0.9f + 0.1f * body.glow
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
