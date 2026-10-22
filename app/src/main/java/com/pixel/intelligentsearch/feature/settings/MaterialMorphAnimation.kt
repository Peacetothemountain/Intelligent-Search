package com.pixel.intelligentsearch.feature.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.ColorScheme
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.MutableCubic
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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Material You colour roles the shapes are filled with, one per shape in turn, so the three on screen at most are
 * always different colours. Material 3 keeps container roles for filled shapes that are not text, and its fixed roles
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

/**
 * How a shape is drawn this frame, from its [MorphBouncePhysics.Body]: [scaleX] and [scaleY] its squash (or wobble),
 * area kept; [stretch] its stretch along [travelDegrees] in flight; [scale] its scale-in from under the card;
 * [rotationDegrees] its roll plus a quarter turn for each morph, turned with the morph's spring. Nothing fades: a
 * shape is drawn whole until it has flown off the screen. Filled in place by [morphPoseOf], so drawing allocates none.
 */
internal class MorphPose {
    var scaleX = 1f
    var scaleY = 1f
    var stretch = 0f
    var travelDegrees = 0f
    var scale = 1f
    var rotationDegrees = 0f
}

/** [body]'s pose, into [out], with its roll interpolated [fraction] of a step on from the last (see [MorphBouncePhysics.renderFraction]). */
internal fun morphPoseOf(body: MorphBouncePhysics.Body, out: MorphPose = MorphPose(), fraction: Float = 1f): MorphPose {
    val squash = body.squash.coerceIn(-MorphBouncePhysics.MAX_JELLY_STRETCH, MorphBouncePhysics.MAX_SQUASH)
    val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
    var turned = body.angle - body.prevAngle
    if (turned > 180f) turned -= 360f else if (turned < -180f) turned += 360f
    out.scaleX = 1f / (1f - squash)
    out.scaleY = 1f - squash
    out.stretch = if (body.penetration > 0f) 0f else (speed / STRETCH_SPEED * MAX_FLIGHT_STRETCH).coerceIn(0f, MAX_FLIGHT_STRETCH)
    out.travelDegrees = Math.toDegrees(atan2(body.vy, body.vx).toDouble()).toFloat()
    out.scale = body.appear.coerceIn(0f, MorphBouncePhysics.MAX_APPEAR)
    out.rotationDegrees = MorphBouncePhysics.drawnRotation(body, body.prevAngle + turned * fraction)
    return out
}

/**
 * Builds a [Morph]'s outline at a progress into a reused path, cubic by cubic, so drawing a frame allocates nothing
 * (androidx.graphics.shapes' own `toPath` makes a list of cubics every call).
 */
private class MorphPathBuilder : (MutableCubic) -> Unit {
    private val cubic = MutableCubic()
    private var path: android.graphics.Path? = null
    private var first = true

    fun build(morph: Morph, progress: Float, into: android.graphics.Path) {
        into.rewind()
        path = into
        first = true
        morph.forEachCubic(progress, cubic, this)
        into.close()
        path = null
    }

    override fun invoke(c: MutableCubic) {
        val p = path ?: return
        if (first) {
            p.moveTo(c.anchor0X, c.anchor0Y)
            first = false
        }
        p.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
    }
}

/** A soft, round falloff (close to a Gaussian) of [color] from the centre of a circle of radius [UNIT] at the origin. */
private fun softFalloff(color: Color): Brush = Brush.radialGradient(
    0f to color,
    0.25f to color.copy(alpha = color.alpha * 0.86f),
    0.5f to color.copy(alpha = color.alpha * 0.54f),
    0.75f to color.copy(alpha = color.alpha * 0.2f),
    1f to color.copy(alpha = 0f),
    center = Offset.Zero,
    radius = UNIT
)

/** The soft falloff is drawn as a circle this big, scaled into place, so its shader is made once. */
private const val UNIT = 64f

/** How flat the floor is seen: a shadow or silhouette on it is drawn this share as tall as it is wide. */
private const val FLOOR_FORESHORTENING = 0.2f

/** A shape sunk into the floor is seen through it: its colour this far towards the floor's. */
private const val SUBMERGED_TINT = 0.5f

/** On a dark page a shadow is less visible, so it is drawn this much darker. */
private const val DARK_SHADOW_BOOST = 1.7f

/** Haptics are spaced at least this far apart, so shapes landing together feel like one tap, not a buzz. */
private const val HAPTIC_SPACING_MS = 90L

/** The membrane giving under a shape is felt as a softer, deeper tap than the hard floor's, by this share. */
private const val MEMBRANE_HAPTIC_SHARE = 0.6f

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
 * Heavy shapes drop in from just under the bottom card onto a floor at the bottom of the screen. The first time, the
 * floor isn't really there, as the street isn't the first time Neo jumps in The Matrix: the shape sinks about half
 * its height into it, leaving its silhouette in the floor, and the floor springs back, overshooting, and throws it
 * back up. The second time the floor is real: the shape bounces off it, hard, and flies up and out past a side of the
 * screen, spinning. That is the only way a shape leaves: nothing fades. The motion is real physics, see
 * [MorphBouncePhysics]: heavy, dense shapes under real gravity, knocking into each other, rolling and spinning off the
 * floor's friction, and giving a haptic tap whose strength follows the impact (softer as the floor gives, full on
 * the hard bounce). Shapes come at random, one or a burst of two or three, never more than three at a time.
 *
 * It looks and moves like Google's Material 3 Expressive UI on Pixel:
 * - the shapes are the Material 3 Expressive shape set, MaterialShapes (see [MaterialMorphShapes]), drawn flat and
 *   opaque, each fitted into its body's circle so it never pokes out of it whichever way it turns;
 * - every bounce morphs the shape into the next of a shuffled sequence with androidx.graphics.shapes' [Morph], on the
 *   expressive fast spatial spring, overshooting a touch, while it turns a quarter turn the way it travels, as
 *   Google's morphing LoadingIndicator turns 90 degrees with each morph;
 * - they are filled with Material You's dynamic colour roles ([MORPH_COLOR_ROLES]), so they follow the wallpaper and
 *   the dark theme, and a shape brightens briefly with each bounce;
 * - each casts a contact shadow on the floor from one light above and a little to the left, shaped from its own
 *   silhouette seen flat on the floor: sharp, dark and tight on the floor, with a dark ambient occlusion right at the
 *   contact, and wider, softer and fainter the higher it is (see [MorphBouncePhysics.shadowFor]); on a dark page it is
 *   drawn darker, with a faint reflection of the shape's colour;
 * - sunk into the floor, a shape is seen through it, its colour towards the floor's, with the floor closing dark round
 *   it; the silhouette it leaves is a darker inset of its outline with a soft rim in its colour, which lingers, then
 *   heals as the floor's surface rings back;
 * - they flatten a little as they sink, wobble once off the hard floor, stretch a little along their path when
 *   falling fast and scale in on a spatial spring as they come out from under the card (see
 *   [MorphBouncePhysics.animateLook] and [morphPoseOf]).
 * Pixel's boot animation (Android 16, Pixel 10) shows one hero shape at a time, the Google G turning into the Gemini
 * spark and back, and M3 asks for abstract shapes to be used sparingly, in decorative moments, so the scene stays
 * sparse: never more than three shapes. Pixel's start-up shows no falling or bouncing shapes; the fall, the floor and
 * the haptic bounces are this animation's own, kept as they were asked for.
 *
 * Rules that hold however it is shown (pinned by MorphBouncePhysicsTest, MaterialMorphAnimationScaleTest and
 * MaterialMorphLookTest): at most three shapes; no shape rises above the bottom card; nothing responds to touch; every
 * bounce is a new shape and a haptic tap; every shape sinks into the floor on its first landing and bounces off it,
 * out of the screen, on its second; every shape leaves within [MorphBouncePhysics.MAX_LIFETIME] of appearing, by
 * flying off the screen, drawn whole until it is fully past the edge. Nothing is drawn when the phone's animations are
 * turned off (Remove animations) or Battery Saver is on. The scene and the shape sizes follow the area it is given
 * (see [morphSceneFor]), never the font size, so big text or Display size changes nothing but the room it has; while
 * the area is scrolled out of sight, nothing moves and nothing taps. [LocalMorphRandomSeed] makes every run the same,
 * shape sequence included, for tests.
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
    // One Morph per shape pair, made when a shape first bounces into that form.
    val morphs = remember(shapes) { arrayOfNulls<MorphStep>(shapes.size) }
    fun morphFor(shapeIndex: Int): MorphStep {
        val to = Math.floorMod(shapeIndex, shapes.size)
        val from = Math.floorMod(shapeIndex - 1, shapes.size)
        return morphs[to] ?: MorphStep(Morph(shapes[from], shapes[to]), fits[from], fits[to]).also { morphs[to] = it }
    }

    val colorScheme = MaterialTheme.colorScheme
    val colors = remember(colorScheme) { morphPalette(colorScheme) }
    val isDark = colorScheme.background.luminance() < 0.5f
    val shadowColor = if (isDark) Color.Black else colorScheme.onSurface
    // The floor's own colour, a touch off the page's, which a shape sunk into it is seen through.
    val floorColor = lerp(colorScheme.background, colorScheme.onSurface, 0.12f)
    val shadowBrush = remember(shadowColor) { softFalloff(shadowColor) }
    val reflectionBrushes = remember(colors) { colors.map { softFalloff(it) } }
    // Everything a frame draws with is made here, once: paths, poses, the shadow, strokes.
    val builder = remember { MorphPathBuilder() }
    val bodyPaths = remember { Array(physics.maxBodies) { android.graphics.Path() } }
    val bodyComposePaths = remember(bodyPaths) { Array(bodyPaths.size) { bodyPaths[it].asComposePath() } }
    val poses = remember { Array(physics.maxBodies) { MorphPose() } }
    val imprintPath = remember { android.graphics.Path() }
    val imprintScreenPath = remember { android.graphics.Path() }
    val imprintComposePath = remember(imprintScreenPath) { imprintScreenPath.asComposePath() }
    val matrix = remember { android.graphics.Matrix() }
    val shadow = remember { MorphBouncePhysics.Shadow() }
    val insetStroke = remember(density) { Stroke(width = with(density) { 5.dp.toPx() }) }
    val rimStroke = remember(density) { Stroke(width = with(density) { 1.25.dp.toPx() }) }
    val rimGlowStroke = remember(density) { Stroke(width = with(density) { 3.5.dp.toPx() }) }
    val surfaceLinePx = with(density) { 1.dp.toPx() }
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
            val onBounce = MorphBouncePhysics.BounceListener { body, impactSpeed ->
                sceneInfo.bouncesFelt++
                val now = SystemClock.uptimeMillis()
                if (now - lastHaptic >= HAPTIC_SPACING_MS) {
                    lastHaptic = now
                    // A gentle landing is a soft tick, a hard one the full standard tick; the membrane gives, so its
                    // tap is softer.
                    val strength = 0.25f + 0.75f * ((impactSpeed - 1f) / 5f).coerceIn(0f, 1f)
                    hapticEngine.performImpactHaptic(view, if (body.bounces == 1) strength * MEMBRANE_HAPTIC_SHARE else strength)
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
            val ppm = pxPerMetre
            val floorPx = physics.floor * ppm
            val f = physics.renderFraction
            val bodies = physics.bodies
            val count = minOf(bodies.size, bodyPaths.size)
            for (i in 0 until count) {
                val body = bodies[i]
                builder.build(morphFor(body.shapeIndex).morph, body.morphProgress, bodyPaths[i])
                morphPoseOf(body, poses[i], f)
            }

            // The silhouettes left in the floor, under everything else, once their shapes have left them.
            for (imprint in physics.imprints) {
                if (!imprint.active || imprint.holding || imprint.strength <= 0f) continue
                val step = morphFor(imprint.shapeIndex)
                // The outline the shape had at its deepest, sized and pivoted as the shape was drawn then.
                builder.build(step.morph, imprint.morphProgress, imprintPath)
                val t = imprint.morphProgress.coerceIn(0f, 1f)
                val pivotX = step.from.centerX + (step.to.centerX - step.from.centerX) * t
                val pivotY = step.from.centerY + (step.to.centerY - step.from.centerY) * t
                val fitScale = step.from.scale + (step.to.scale - step.from.scale) * t
                val radiusPx = imprint.radius * ppm
                val xPx = imprint.x * ppm
                // Healing, the hollow fills back up towards the floor line as it fades.
                val heal = 0.55f + 0.45f * imprint.strength
                matrix.reset()
                matrix.postTranslate(-pivotX, -pivotY)
                matrix.postScale(radiusPx * fitScale, radiusPx * fitScale)
                matrix.postRotate(imprint.rotationDegrees)
                matrix.postScale(1f / (1f - imprint.squash), 1f - imprint.squash)
                matrix.postTranslate(xPx, imprint.y * ppm)
                matrix.postScale(1f, heal, xPx, floorPx)
                imprintPath.transform(matrix, imprintScreenPath)
                val s = imprint.strength
                val rim = colors[imprint.bodyId % colors.size]
                clipRect(top = floorPx) {
                    drawPath(imprintComposePath, shadowColor, alpha = (if (isDark) 0.28f else 0.13f) * s)
                    // The inset: darker just inside the outline, as a hollow's walls are.
                    clipPath(imprintComposePath) {
                        drawPath(imprintComposePath, shadowColor, alpha = (if (isDark) 0.3f else 0.14f) * s, style = insetStroke)
                    }
                    // The rim, catching the light, in the shape's own colour.
                    drawPath(imprintComposePath, rim, alpha = (if (isDark) 0.12f else 0.22f) * s, style = rimGlowStroke)
                    drawPath(imprintComposePath, rim, alpha = (if (isDark) 0.45f else 0.7f) * s, style = rimStroke)
                }
            }

            // Shadows, so every shape is drawn above every shadow.
            for (i in 0 until count) {
                val body = bodies[i]
                val pose = poses[i]
                val radiusPx = body.radius * ppm
                val yM = body.prevY + (body.y - body.prevY) * f
                MorphBouncePhysics.shadowFor(physics.floor - yM - body.radius, body.radius, body.penetration, shadow)
                val boost = if (isDark) DARK_SHADOW_BOOST else 1f
                val alpha = (shadow.alpha * boost).coerceAtMost(0.85f)
                val xPx = (body.prevX + (body.x - body.prevX) * f) * ppm
                val cx = xPx + shadow.offsetX * ppm
                val widthPx = shadow.width * ppm
                val cy = floorPx - widthPx * FLOOR_FORESHORTENING * 0.3f
                if (alpha >= MorphBouncePhysics.SHADOW_VISIBLE) {
                    // The soft penumbra: all of the shadow high up, a rim round the core close down.
                    drawSoft(shadowBrush, cx, cy, widthPx, alpha * (0.35f + 0.65f * shadow.softness))
                    // The core: the shape's own silhouette, seen flat on the floor, sharp near it.
                    val step = morphFor(body.shapeIndex)
                    val fitScale = step.from.scale + (step.to.scale - step.from.scale) * body.morphProgress.coerceIn(0f, 1f)
                    val pivotX = step.from.centerX + (step.to.centerX - step.from.centerX) * body.morphProgress.coerceIn(0f, 1f)
                    val pivotY = step.from.centerY + (step.to.centerY - step.from.centerY) * body.morphProgress.coerceIn(0f, 1f)
                    // (A shape sunk into the floor casts none beside it: there, only the floor closing dark round it.)
                    val coreAlpha = if (body.penetration > 0f) 0f else alpha * (1f - shadow.softness)
                    if (coreAlpha >= MorphBouncePhysics.SHADOW_VISIBLE) {
                        for (layer in 0 until 3) {
                            val widthShare = (shadow.coreWidth + shadow.blur * layer * 0.5f) / (2f * body.radius)
                            translate(cx, cy) {
                                scale(1f, FLOOR_FORESHORTENING, pivot = Offset.Zero) {
                                    rotate(pose.rotationDegrees, pivot = Offset.Zero) {
                                        scale(radiusPx * fitScale * widthShare, pivot = Offset.Zero) {
                                            translate(-pivotX, -pivotY) {
                                                drawPath(bodyComposePaths[i], shadowColor, alpha = coreAlpha / (layer + 1.5f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Right at the contact, the dark occlusion where little light gets in.
                if (shadow.aoAlpha > 0.01f) {
                    drawSoft(shadowBrush, xPx, floorPx - shadow.aoWidth * ppm * FLOOR_FORESHORTENING * 0.15f, shadow.aoWidth * ppm, (shadow.aoAlpha * boost).coerceAtMost(0.9f))
                }
                // A dark floor shows a faint reflection of the shape's colour as it comes close.
                if (isDark && body.penetration <= 0f) {
                    val closeness = 1f - ((physics.floor - yM - body.radius) / (6f * body.radius)).coerceIn(0f, 1f)
                    if (closeness > 0.02f) drawSoft(reflectionBrushes[body.id % colors.size], xPx, floorPx, 2.4f * radiusPx, 0.22f * closeness * closeness)
                }
            }

            for (i in 0 until count) {
                val body = bodies[i]
                val pose = poses[i]
                if (pose.scale <= 0f) continue
                val radiusPx = body.radius * ppm
                val baseColor = colors[body.id % colors.size]
                val step = morphFor(body.shapeIndex)
                // Where the shape turns about, and how large its polygon is drawn, move from the old shape's to the
                // new one's with the morph (the path itself follows the spring past 1, the fit does not).
                val t = body.morphProgress.coerceIn(0f, 1f)
                val pivotX = step.from.centerX + (step.to.centerX - step.from.centerX) * t
                val pivotY = step.from.centerY + (step.to.centerY - step.from.centerY) * t
                val fitScale = step.from.scale + (step.to.scale - step.from.scale) * t
                val cx = (body.prevX + (body.x - body.prevX) * f) * ppm
                val cy = (body.prevY + (body.y - body.prevY) * f) * ppm
                val path = bodyComposePaths[i]
                val surfacePx = floorPx + (body.imprint?.surface ?: 0f) * ppm
                if (cy + radiusPx * MorphBouncePhysics.SHAPE_REACH <= surfacePx) {
                    drawShape(path, cx, cy, radiusPx * pose.scale * fitScale, pivotX, pivotY, pose, baseColor, body.glow)
                } else {
                    // Sunk into the floor: above its surface the shape as it is, below it seen through the floor.
                    clipRect(bottom = surfacePx) {
                        drawShape(path, cx, cy, radiusPx * pose.scale * fitScale, pivotX, pivotY, pose, baseColor, body.glow)
                    }
                    clipRect(top = surfacePx) {
                        drawShape(path, cx, cy, radiusPx * pose.scale * fitScale, pivotX, pivotY, pose, lerp(baseColor, floorColor, SUBMERGED_TINT), 0f)
                    }
                }
            }

            // The floor's surface, seen only where a shape has broken it: sagging round a sunk shape, then springing
            // back past its rest line and ringing out.
            for (imprint in physics.imprints) {
                if (!imprint.active) continue
                val reach = imprint.radius * ppm * 3.2f
                val sagPx = imprint.surface * ppm
                val strength = (imprint.strength * 0.5f + kotlin.math.abs(imprint.surface) / (0.12f * imprint.radius)).coerceIn(0f, 1f)
                if (strength < 0.02f) continue
                val xPx = imprint.x * ppm
                var previousX = xPx - reach
                var previousY = floorPx
                for (k in 1..SURFACE_SEGMENTS) {
                    val u = -1f + 2f * k / SURFACE_SEGMENTS
                    val bump = 0.5f + 0.5f * cos(PI.toFloat() * u)
                    val x = xPx + u * reach
                    val y = floorPx + sagPx * bump
                    val mid = 0.5f + 0.5f * cos(PI.toFloat() * (u - 1f / SURFACE_SEGMENTS))
                    drawLine(shadowColor, Offset(previousX, previousY), Offset(x, y), strokeWidth = surfaceLinePx, alpha = (if (isDark) 0.5f else 0.28f) * strength * sqrt(mid))
                    previousX = x
                    previousY = y
                }
            }
        }
    }
}

private const val SURFACE_SEGMENTS = 18

/** A soft falloff ellipse [widthPx] wide (and [FLOOR_FORESHORTENING] as tall), centred on [cx], [cy], lying on the floor. */
private fun DrawScope.drawSoft(brush: Brush, cx: Float, cy: Float, widthPx: Float, alpha: Float) {
    if (widthPx <= 0f || alpha <= 0f) return
    translate(cx, cy) {
        scale(widthPx / 2f / UNIT, widthPx * FLOOR_FORESHORTENING / 2f / UNIT, pivot = Offset.Zero) {
            drawCircle(brush, radius = UNIT, center = Offset.Zero, alpha = alpha.coerceAtMost(1f))
        }
    }
}

/**
 * One shape, [path] being its outline in the polygon's own units: centred on [cx], [cy], stretched along its path,
 * squashed, turned, drawn [sizePx] per polygon unit about its pivot, with a soft halo while it [glow]s.
 */
private fun DrawScope.drawShape(
    path: androidx.compose.ui.graphics.Path,
    cx: Float,
    cy: Float,
    sizePx: Float,
    pivotX: Float,
    pivotY: Float,
    pose: MorphPose,
    color: Color,
    glow: Float
) {
    translate(left = cx, top = cy) {
        rotate(pose.travelDegrees, pivot = Offset.Zero) {
            scale(scaleX = 1f + pose.stretch, scaleY = 1f / (1f + pose.stretch), pivot = Offset.Zero) {
                rotate(-pose.travelDegrees, pivot = Offset.Zero) {
                    scale(scaleX = pose.scaleX, scaleY = pose.scaleY, pivot = Offset.Zero) {
                        rotate(pose.rotationDegrees, pivot = Offset.Zero) {
                            // The glow: a soft halo and a brief lift in tone on every bounce.
                            if (glow > 0f) {
                                scale(sizePx * (1f + 0.22f * glow), pivot = Offset.Zero) {
                                    translate(left = -pivotX, top = -pivotY) {
                                        drawPath(path = path, color = color, alpha = 0.26f * glow)
                                    }
                                }
                            }
                            scale(sizePx, pivot = Offset.Zero) {
                                translate(left = -pivotX, top = -pivotY) {
                                    drawPath(path = path, color = lerp(color, Color.White, 0.3f * glow))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
