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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
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

/** Test tag on the Material Morph animation's area. */
internal const val MORPH_ANIMATION_TAG = "material_morph_animation"

/** What a running [MaterialMorphAnimation] is showing, for tests, through [MorphSceneKey] on its area. */
internal class MorphSceneInfo(val physics: MorphBouncePhysics) {
    /** How many pixels make one metre of the scene. */
    var pxPerMetre = 0f
    /** Bounces that asked for a haptic tap (taps closer together than [HAPTIC_SPACING_MS] are then felt as one). */
    var bouncesFelt = 0
}

internal val MorphSceneKey = SemanticsPropertyKey<MorphSceneInfo>("MorphScene")

/** The area under the cards is at least this tall; when the cards leave less room, the page scrolls to it. */
internal val MORPH_AREA_MIN_HEIGHT = 160.dp

/** An area shorter than this, in dp, has no room for a real bounce and stays empty. */
private const val MIN_AREA_HEIGHT_DP = 40f

/** The scene is at least this tall, in metres: a shorter area is drawn at a smaller scale, not with shorter bounces. */
private const val MIN_SCENE_HEIGHT = 1.2f

/** No shape is wider than this share of the area's height, so every shape has room to bounce. */
private const val MAX_SHAPE_HEIGHT_SHARE = 0.22f

/** How an animation area maps onto the physics: its scale, its size in metres, and the four shape radii. */
internal data class MorphScene(val pxPerMetre: Float, val widthMetres: Float, val heightMetres: Float, val radiiMetres: List<Float>)

/**
 * The scene for an area [widthPx] by [heightPx] at [density] pixels per dp, or null if the area is too short to show
 * it. One dp is one centimetre, except that an area under [MIN_SCENE_HEIGHT] metres is drawn smaller instead, so the
 * shapes keep real, countable bounces however little room big text or Display size leaves. Shape sizes follow the
 * area alone, never the font size: 5.5% of its width (up to 1.6x for the largest), scaled for narrow and wide areas,
 * and never more than [MAX_SHAPE_HEIGHT_SHARE] of its height.
 */
internal fun morphSceneFor(widthPx: Float, heightPx: Float, density: Float): MorphScene? {
    if (widthPx <= 0f || density <= 0f || heightPx < MIN_AREA_HEIGHT_DP * density) return null
    val pxPerMetre = minOf(density * 100f, heightPx / MIN_SCENE_HEIGHT)
    val widthScale = (widthPx / density / 411f).coerceIn(0.65f, 1.4f)
    val radii = (0 until 4).map { i ->
        val diameterPx = minOf(widthPx * 0.055f * widthScale * (1f + i * 0.2f), heightPx * MAX_SHAPE_HEIGHT_SHARE)
        diameterPx / 2f / pxPerMetre
    }
    return MorphScene(pxPerMetre, widthPx / pxPerMetre, heightPx / pxPerMetre, radii)
}

/**
 * A settings page's [cards] (one layout, usually a Column) with the Material Morph animation's area under them. The
 * area fills the rest of the screen; when the cards leave it less than [MORPH_AREA_MIN_HEIGHT] (big text or Display
 * size), it keeps that height after the last card and the page scrolls to it. With the animation off the area takes
 * only the room left over, so the page scrolls just as far as its cards.
 */
@Composable
internal fun CardsAboveMorphAnimation(morphAnimationEnabled: Boolean, modifier: Modifier = Modifier, cards: @Composable () -> Unit) {
    Layout(
        content = {
            cards()
            Box(modifier = Modifier.clipToBounds()) {
                if (morphAnimationEnabled) MaterialMorphAnimation(modifier = Modifier.fillMaxSize())
            }
        },
        modifier = modifier.verticalScroll(rememberScrollState())
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val cardsPlaceable = measurables[0].measure(Constraints(minWidth = width, maxWidth = width))
        // The scroll passes the viewport's height on as the minimum.
        val minArea = if (morphAnimationEnabled) MORPH_AREA_MIN_HEIGHT.roundToPx() else 0
        val areaHeight = maxOf(constraints.minHeight - cardsPlaceable.height, minArea)
        val area = measurables[1].measure(Constraints.fixed(width, areaHeight))
        layout(width, cardsPlaceable.height + areaHeight) {
            cardsPlaceable.place(0, 0)
            area.place(0, cardsPlaceable.height)
        }
    }
}

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
 * phone's animations are turned off or Battery Saver is on. The scene and the shape sizes follow the area it is given
 * (see [morphSceneFor]), never the font size, so big text or Display size changes nothing but the room it has; while
 * the area is scrolled out of sight, nothing moves and nothing taps.
 */
@Composable
fun MaterialMorphAnimation(modifier: Modifier = Modifier) {
    val animationsEnabled by rememberAnimationsEnabled()
    val powerSaveMode by rememberPowerSaveMode()
    if (!animationsEnabled || powerSaveMode) return

    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
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

    val sceneInfo = remember(physics) { MorphSceneInfo(physics) }
    // Whether any of the area is on screen; scrolled out of sight, nothing moves and nothing taps.
    val onScreen = remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .testTag(MORPH_ANIMATION_TAG)
            .semantics { this[MorphSceneKey] = sceneInfo }
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                onScreen.value = bounds.width > 0f && bounds.height > 0f
            }
            .clipToBounds()
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val scene = remember(widthPx, heightPx, density.density) { morphSceneFor(widthPx, heightPx, density.density) }
            ?: return@BoxWithConstraints
        val pxPerMetre = scene.pxPerMetre
        sceneInfo.pxPerMetre = pxPerMetre
        // Applied before this frame is drawn, so shapes never show at one size's scale in another size's area.
        SideEffect { physics.resize(scene.widthMetres, scene.heightMetres, scene.radiiMetres) }
        LaunchedEffect(Unit) {
            var lastHaptic = 0L
            val onBounce = MorphBouncePhysics.BounceListener { _, impactSpeed ->
                sceneInfo.bouncesFelt++
                val now = SystemClock.uptimeMillis()
                if (now - lastHaptic >= HAPTIC_SPACING_MS) {
                    lastHaptic = now
                    // A gentle landing is a soft tick, a hard one the full standard tick.
                    val strength = 0.25f + 0.75f * ((impactSpeed - 1f) / 5f).coerceIn(0f, 1f)
                    hapticEngine.performImpactHaptic(view, strength)
                }
            }
            snapshotFlow { onScreen.value }.collectLatest { visible ->
                if (!visible) return@collectLatest
                var last = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    physics.advance((now - last) / 1_000_000_000f, onBounce)
                    last = now
                    frame.longValue = now
                }
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
