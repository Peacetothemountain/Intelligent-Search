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
import androidx.compose.material3.ColorScheme
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Material You colour roles the shapes are filled with, one per shape in turn, so the five on screen at most are
 * five different colours. Material 3 keeps container roles for filled shapes that are not text, and its fixed roles
 * at the same tone in light and dark themes (https://m3.material.io/styles/color/roles): the fixed-dim tones carry the
 * set on a light page and glow on a dark one, while the containers are its light accents in the light theme and its
 * deep ones in the dark. All of them follow the wallpaper with dynamic colour, as Pixel's own expressive UI does.
 */
internal val MORPH_COLOR_ROLES = listOf("primaryFixedDim", "tertiaryContainer", "secondaryFixedDim", "primaryContainer", "tertiaryFixedDim")

/** The fill colours for [scheme], in the order of [MORPH_COLOR_ROLES]. */
internal fun morphPalette(scheme: ColorScheme): List<Color> = MORPH_COLOR_ROLES.map { role ->
    when (role) {
        "primaryFixedDim" -> scheme.primaryFixedDim
        "secondaryFixedDim" -> scheme.secondaryFixedDim
        "tertiaryFixedDim" -> scheme.tertiaryFixedDim
        "primaryContainer" -> scheme.primaryContainer
        "tertiaryContainer" -> scheme.tertiaryContainer
        else -> error("No colour role $role")
    }
}

/** A Material shape morphs a quarter turn, as Google's morphing loading indicator turns 90 degrees with every morph. */
internal const val MORPH_TURN_DEGREES = 90f

/** In the air a shape stretches along its path by at most this share, when falling at [STRETCH_SPEED] or faster. */
internal const val MAX_FLIGHT_STRETCH = 0.12f
private const val STRETCH_SPEED = 6f

/** A leaving shape shrinks to this share of its size as it fades out. */
private const val LEAVE_SCALE = 0.6f

/**
 * How a shape is drawn this frame, from its [MorphBouncePhysics.Body]: [scaleX] and [scaleY] its squash (or wobble),
 * area kept, with [drop] radii it is pressed down into the trampoline; [stretch] its stretch along [travelDegrees] in
 * flight; [scale] its scale-in and shrink on leaving; [alpha] its fade out; [rotationDegrees] its roll plus a quarter
 * turn for each morph, turned with the morph's spring.
 */
internal class MorphPose(
    val scaleX: Float,
    val scaleY: Float,
    val drop: Float,
    val stretch: Float,
    val travelDegrees: Float,
    val scale: Float,
    val alpha: Float,
    val rotationDegrees: Float
)

internal fun morphPoseOf(body: MorphBouncePhysics.Body): MorphPose {
    val squash = body.squash.coerceIn(-MorphBouncePhysics.MAX_JELLY_STRETCH, MorphBouncePhysics.MAX_SQUASH)
    val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
    val stretch = if (body.penetration > 0f) 0f else (speed / STRETCH_SPEED * MAX_FLIGHT_STRETCH).coerceIn(0f, MAX_FLIGHT_STRETCH)
    val morphsDone = if (body.bounces == 0) 0f else body.bounces - 1 + body.morphProgress
    return MorphPose(
        scaleX = 1f / (1f - squash),
        scaleY = 1f - squash,
        drop = squash.coerceAtLeast(0f),
        stretch = stretch,
        travelDegrees = Math.toDegrees(atan2(body.vy, body.vx).toDouble()).toFloat(),
        scale = body.appear.coerceIn(0f, MorphBouncePhysics.MAX_APPEAR) * (1f - (1f - LEAVE_SCALE) * body.leave),
        alpha = (1f - body.leave).coerceIn(0f, 1f),
        rotationDegrees = (body.angle + body.turnDirection * MORPH_TURN_DEGREES * morphsDone) % 360f
    )
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
    /** The MaterialShapes names of the shapes, in the order a shape morphs through them. */
    var shapeNames: List<String> = emptyList()
    /** The fill colours, from the theme's roles in [MORPH_COLOR_ROLES]; shape n is filled with colour n mod five. */
    var palette: List<Color> = emptyList()
}

internal val MorphSceneKey = SemanticsPropertyKey<MorphSceneInfo>("MorphScene")

/**
 * A seed for the random choices of every [MaterialMorphAnimation] below it (when shapes drop in, where, how they are
 * thrown), so a test sees the same run every time; null, as in the app, draws them from [Random.Default].
 */
internal val LocalMorphRandomSeed = staticCompositionLocalOf<Int?> { null }

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
 * into each other, roll and tumble from the trampoline's friction, and give a haptic tap whose strength follows the
 * impact.
 *
 * It looks and moves like Google's Material 3 Expressive UI on Pixel:
 * - the shapes are the Material 3 Expressive shape set, MaterialShapes (see [MaterialMorphShapes]), drawn flat and
 *   opaque, each fitted into its body's circle so it never pokes out of it whichever way it turns;
 * - every bounce morphs the shape into the next of a shuffled sequence with androidx.graphics.shapes' [Morph], on the
 *   expressive fast spatial spring, overshooting a touch, while it turns a quarter turn the way it rolls, as Google's
 *   morphing LoadingIndicator turns 90 degrees with each morph;
 * - they are filled with Material You's dynamic colour roles ([MORPH_COLOR_ROLES]), so they follow the wallpaper and
 *   the dark theme; a soft contact shadow (the theme's tinted onSurface neutral on a light page, a reflection in the
 *   shape's own colour on a dark one) firms up as a shape lands, and a shape brightens briefly with each bounce;
 * - they squash flat on the trampoline, wobble tall once as they lift off, stretch a little along their path when
 *   falling fast, scale in on a spatial spring as they come out from under the card, and shrink and fade on an
 *   effects spring at the edge they leave by (see [MorphBouncePhysics.animateLook] and [morphPoseOf]).
 * Pixel's boot animation (Android 16, Pixel 10) shows one hero shape at a time, the Google G turning into the Gemini
 * spark and back, and M3 asks for abstract shapes to be used sparingly, in decorative moments, so the scene stays
 * sparse: an opening wave of four or five, then one or two at a time, never more than five. Pixel's start-up shows no
 * falling or bouncing shapes; the fall, the trampoline and the haptic bounces are this animation's own, kept as they
 * were asked for.
 *
 * Rules that hold however it is shown (pinned by MorphBouncePhysicsTest, MaterialMorphAnimationScaleTest and
 * MaterialMorphLookTest): at most five shapes; no shape rises above the bottom card; nothing responds to touch; every
 * bounce is a new shape and a haptic tap; every shape leaves within [MorphBouncePhysics.MAX_LIFETIME] of appearing,
 * having made its planned bounces. Nothing is drawn when the phone's animations are turned off (Remove animations) or
 * Battery Saver is on. The scene and the shape sizes follow the area it is given (see [morphSceneFor]), never the font
 * size, so big text or Display size changes nothing but the room it has; while the area is scrolled out of sight,
 * nothing moves and nothing taps. [LocalMorphRandomSeed] makes every run the same, shape sequence included, for tests.
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
    val seed = LocalMorphRandomSeed.current
    val physics = remember { MorphBouncePhysics(if (seed != null) Random(seed) else Random.Default) }
    val shapeNames = remember { MaterialMorphShapes.sequence(seed) }
    val shapes = remember(shapeNames) { shapeNames.map { MaterialMorphShapes.byName.getValue(it) } }
    val fits = remember(shapes) { shapes.map { MorphShapeFit.of(it) } }
    // One Morph per shape pair, made when a shape bounces into its next form.
    val morphs = remember { HashMap<Int, MorphStep>() }
    fun morphFor(shapeIndex: Int): MorphStep {
        val from = Math.floorMod(shapeIndex - 1, shapes.size)
        val to = Math.floorMod(shapeIndex, shapes.size)
        return morphs.getOrPut(to) { MorphStep(Morph(shapes[from], shapes[to]), fits[from], fits[to]) }
    }

    val colorScheme = MaterialTheme.colorScheme
    val colors = remember(colorScheme) { morphPalette(colorScheme) }
    val shadowColor = colorScheme.onSurface
    val isDark = colorScheme.background.luminance() < 0.5f
    val nativePath = remember { android.graphics.Path() }
    val composePath = remember(nativePath) { nativePath.asComposePath() }
    // Bumped every frame; the Canvas reads it so only drawing reruns, never composition.
    val frame = remember { mutableLongStateOf(0L) }

    val sceneInfo = remember(physics) { MorphSceneInfo(physics) }
    sceneInfo.shapeNames = shapeNames
    sceneInfo.palette = colors
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
                val pose = morphPoseOf(body)
                val radiusPx = body.radius * pxPerMetre
                val heightAboveFloor = (floorPx - (body.y * pxPerMetre + radiusPx)).coerceAtLeast(0f)
                val closeness = 1f - (heightAboveFloor / floorPx).coerceIn(0f, 1f)
                val shadowWidth = radiusPx * 2f * pose.scale * (0.55f + 0.6f * closeness)
                val shadowHeight = shadowWidth * 0.24f
                // A dark shadow disappears on a dark background, so there it is a soft reflection in the shape's colour.
                val shadowAlpha = (if (isDark) 0.30f else 0.22f) * closeness * closeness * pose.alpha
                if (shadowAlpha < 0.01f) continue
                val tint = if (isDark) colors[body.id % colors.size] else shadowColor
                val center = Offset(body.x * pxPerMetre, floorPx - shadowHeight * 0.35f)
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = shadowAlpha), Color.Transparent),
                        center = center,
                        radius = shadowWidth / 2f
                    ),
                    topLeft = Offset(center.x - shadowWidth / 2f, center.y - shadowHeight / 2f),
                    size = Size(shadowWidth, shadowHeight)
                )
            }
            for (body in physics.bodies) {
                val pose = morphPoseOf(body)
                if (pose.scale <= 0f || pose.alpha <= 0f) continue
                val radiusPx = body.radius * pxPerMetre
                val baseColor = colors[body.id % colors.size]
                val step = morphFor(body.shapeIndex)
                // Where the shape turns about, and how large its polygon is drawn, move from the old shape's to the
                // new one's with the morph (the path itself follows the spring past 1, the fit does not).
                val t = body.morphProgress.coerceIn(0f, 1f)
                val pivotX = step.from.centerX + (step.to.centerX - step.from.centerX) * t
                val pivotY = step.from.centerY + (step.to.centerY - step.from.centerY) * t
                val fitScale = step.from.scale + (step.to.scale - step.from.scale) * t
                nativePath.rewind()
                step.morph.toPath(progress = body.morphProgress, path = nativePath)
                // On the trampoline it is pressed against the bottom edge; part of the dip shows as the bed giving
                // way below the screen edge.
                translate(left = body.x * pxPerMetre, top = body.y * pxPerMetre + radiusPx * pose.drop) {
                    rotate(pose.travelDegrees, pivot = Offset.Zero) {
                        scale(scaleX = 1f + pose.stretch, scaleY = 1f / (1f + pose.stretch), pivot = Offset.Zero) {
                            rotate(-pose.travelDegrees, pivot = Offset.Zero) {
                                scale(scaleX = pose.scaleX, scaleY = pose.scaleY, pivot = Offset.Zero) {
                                    rotate(pose.rotationDegrees, pivot = Offset.Zero) {
                                        // The glow: a soft halo and a brief lift in tone on every bounce.
                                        if (body.glow > 0f) {
                                            scale(radiusPx * pose.scale * fitScale * (1f + 0.22f * body.glow), pivot = Offset.Zero) {
                                                translate(left = -pivotX, top = -pivotY) {
                                                    drawPath(path = composePath, color = baseColor, alpha = 0.26f * body.glow * pose.alpha)
                                                }
                                            }
                                        }
                                        scale(radiusPx * pose.scale * fitScale, pivot = Offset.Zero) {
                                            translate(left = -pivotX, top = -pivotY) {
                                                drawPath(
                                                    path = composePath,
                                                    color = lerp(baseColor, Color.White, 0.3f * body.glow),
                                                    alpha = pose.alpha
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
}
