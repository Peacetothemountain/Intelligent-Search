package com.pixel.intelligentsearch.feature.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.MutableCubic
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Material You color roles the shapes take, one per shape in turn, so the shapes on screen at once are different
 * colors. Material 3 keeps container roles for filled shapes that are not text, and its fixed roles at the same tone
 * in light and dark themes (https://m3.material.io/styles/color/roles). All of them follow the wallpaper with dynamic
 * color, as Pixel's own expressive UI does.
 */
internal val MORPH_COLOR_ROLES = listOf("primaryFixedDim", "tertiaryContainer", "secondaryFixedDim", "primaryContainer", "tertiaryFixedDim")

/** The colors for [scheme], in the order of [MORPH_COLOR_ROLES]. */
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

/**
 * How a shape is drawn this frame, from its [MorphBouncePhysics.Body]: [scale] its scale-in from under the card and
 * [rotationDegrees] its roll, interpolated between steps. Nothing fades: a shape is drawn whole until it has flown off
 * the screen. Filled in place by [morphPoseOf], so drawing allocates none.
 */
internal class MorphPose {
    var scale = 1f
    var rotationDegrees = 0f
}

/** [body]'s pose, into [out], with its roll interpolated [fraction] of a step on from the last (see [MorphBouncePhysics.renderFraction]). */
internal fun morphPoseOf(body: MorphBouncePhysics.Body, out: MorphPose = MorphPose(), fraction: Float = 1f): MorphPose {
    var turned = body.angle - body.prevAngle
    if (turned > 180f) turned -= 360f else if (turned < -180f) turned += 360f
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

/** A soft, round falloff (close to a Gaussian) of [color] from the center of a circle of radius [UNIT] at the origin. */
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

/** On a dark page a shadow is less visible, so it is drawn this much darker. */
private const val DARK_SHADOW_BOOST = 1.7f

/** Haptics are spaced at least this far apart, so shapes landing together feel like one tap, not a buzz. */
private const val HAPTIC_SPACING_MS = 90L

/** Test tag on the Material Morph animation's area. */
internal const val MORPH_ANIMATION_TAG = "material_morph_animation"

/** How long a taller area must hold before the scene takes it, so a card closing up above doesn't re-size it per frame. */
internal const val MORPH_RESIZE_SETTLE_MILLIS = 120L

/** What a running [MaterialMorphAnimation] is showing, for tests, through [MorphSceneKey] on its area. */
internal class MorphSceneInfo(val physics: MorphBouncePhysics) {
    /** How many pixels make one metre of the scene. */
    var pxPerMetre = 0f
    /** The area height the scene is sized for, which waits for the area to stop resizing ([MORPH_RESIZE_SETTLE_MILLIS]). */
    var sceneHeightPx = 0f
    /** Bounces that asked for a haptic tap (taps closer together than [HAPTIC_SPACING_MS] are then felt as one). */
    var bouncesFelt = 0
    /** The MaterialShapes names of the shapes, in the order a shape morphs through them. */
    var shapeNames: List<String> = emptyList()
    /** The colors, from the theme's roles in [MORPH_COLOR_ROLES]; shape n takes color n mod five. */
    var palette: List<Color> = emptyList()
}

internal val MorphSceneKey = SemanticsPropertyKey<MorphSceneInfo>("MorphScene")

/**
 * A seed for the random choices of every [MaterialMorphAnimation] below it (when shapes drop in, where, how they are
 * thrown, how heavy they are), so a test sees the same run every time; null, as in the app, draws them from
 * [Random.Default]. With a seed the drops are also planned at once rather than over a few frames, so the run is the
 * same however fast the frames come.
 */
internal val LocalMorphRandomSeed = staticCompositionLocalOf<Int?> { null }

/** The area under the cards is at least this tall; when the cards leave less room, the page scrolls to it. */
internal val MORPH_AREA_MIN_HEIGHT = 160.dp

/** An area shorter than this, in dp, has no room for a real bounce and stays empty. */
private const val MIN_AREA_HEIGHT_DP = 40f

/** The scene is at least this tall, in metres: a shorter area is drawn at a smaller scale, not with shorter bounces. */
private const val MIN_SCENE_HEIGHT = 1.2f

/** No shape is wider than this share of the area's height, so every shape has room to bounce. */
internal const val MAX_SHAPE_HEIGHT_SHARE = 0.25f

/** The smallest shape is this share of the area's width, the largest 1.6 times it. */
internal const val SHAPE_WIDTH_SHARE = 0.091f

/** How an animation area maps onto the physics: its scale, its size in metres, and the four shape radii. */
internal data class MorphScene(val pxPerMetre: Float, val widthMetres: Float, val heightMetres: Float, val radiiMetres: List<Float>)

/**
 * The scene for an area [widthPx] by [heightPx] at [density] pixels per dp, or null if the area is too short to show
 * it. One dp is one centimetre, except that an area under [MIN_SCENE_HEIGHT] metres is drawn smaller instead, so the
 * shapes keep real, countable bounces however little room big text or Display size leaves. Shape sizes follow the
 * area alone, never the font size: [SHAPE_WIDTH_SHARE] of its width (up to 1.6x for the largest), scaled for narrow
 * and wide areas, and never more than [MAX_SHAPE_HEIGHT_SHARE] of its height.
 */
internal fun morphSceneFor(widthPx: Float, heightPx: Float, density: Float): MorphScene? {
    if (widthPx <= 0f || density <= 0f || heightPx < MIN_AREA_HEIGHT_DP * density) return null
    val pxPerMetre = minOf(density * 100f, heightPx / MIN_SCENE_HEIGHT)
    val widthScale = (widthPx / density / 411f).coerceIn(0.65f, 1.4f)
    val radii = (0 until 4).map { i ->
        val diameterPx = minOf(widthPx * SHAPE_WIDTH_SHARE * widthScale * (1f + i * 0.2f), heightPx * MAX_SHAPE_HEIGHT_SHARE)
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
 * How a shape of one color role is lit, in the color language of the search bar and At a Glance: a near-black body
 * tinted with the role's deep tone, deeper toward its edge, a soft pool of light in its light tone where its felt
 * gravity holds it, a second swirl, a flash on impact, a rim catching the light from above, and a faint halftone grain
 * carried by the light (the logo's diamond dot grid). Every shader is made once and moved into place with its local
 * matrix, so drawing a frame allocates nothing.
 */
private class PlateLook(color: Color, floorColor: Color, density: Float) {
    private val light = if (color.luminance() < 0.45f) lerp(color, Color.White, 0.45f) else color
    private val deep = deepTone(light)
    private val centre = lerp(NEAR_BLACK, deep, 0.05f)
    private val outer = lerp(Color(0xFF090608), deep, 0.32f)
    private val lift = lerp(Color(0xFF160E14), deep, 0.55f)
    private val rim = lerp(Color(0xFF0B070A), light, 0.4f)
    private val baseShader = RadialGradient(0f, 0f, 1f, intArrayOf(centre.toArgb(), lerp(centre, outer, 0.5f).toArgb(), outer.toArgb()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    private val poolShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(lerp(lift, light, 0.25f).copy(alpha = 0.85f).toArgb(), lift.copy(alpha = 0.45f).toArgb(), lift.copy(alpha = 0f).toArgb()),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    private val flashShader = RadialGradient(0f, 0f, 1f, intArrayOf(lerp(lift, light, 0.8f).copy(alpha = 0.85f).toArgb(), light.copy(alpha = 0f).toArgb()), floatArrayOf(0f, 0.55f), Shader.TileMode.CLAMP)
    private val swirlShader = RadialGradient(0f, 0f, 1f, intArrayOf(lerp(deep, light, 0.35f).toArgb(), deep.copy(alpha = 0f).toArgb()), null, Shader.TileMode.CLAMP)
    private val rimShader = LinearGradient(0f, 0f, 0f, 1f, intArrayOf(rim.copy(alpha = 0.9f).toArgb(), rim.copy(alpha = 0.25f).toArgb(), lift.copy(alpha = 0.5f).toArgb()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = baseShader }
    private val poolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = poolShader }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = flashShader }
    private val swirlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = swirlShader }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = rimShader; style = Paint.Style.STROKE; strokeWidth = 1.5f * density }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = lerp(lerp(lift, light, 0.25f), light, 0.6f).toArgb()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        blendMode = BlendMode.PLUS
    }
    val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = lerp(lerp(outer, deep, 0.5f), floorColor, IN_FLOOR_TINT).toArgb() }
    private val matrix = android.graphics.Matrix()
    private val minCell = 2.4f * density

    /**
     * Draws the plate of [body] into [canvas], already clipped to its outline, centerd on [cx], [cy] and [r] pixels in
     * radius; [dots] is scratch for the halftone.
     */
    fun draw(canvas: android.graphics.Canvas, body: MorphBouncePhysics.Body, cx: Float, cy: Float, r: Float, dots: HalftoneScratch) {
        place(baseShader, r, r, cx, cy)
        canvas.drawRect(cx - 1.2f * r, cy - 1.2f * r, cx + 1.2f * r, cy + 1.2f * r, basePaint)
        val lx = cx + body.lightX * r
        val ly = cy + body.lightY * r
        val spread = r * (0.55f + 0.7f * body.lightSpread)
        place(poolShader, spread, spread, lx, ly)
        canvas.drawCircle(lx, ly, spread, poolPaint)
        if (body.flash > 0.01f) {
            place(flashShader, spread, spread, lx, ly)
            flashPaint.alpha = (body.flash * 0.7f * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(lx, ly, spread * 0.55f, flashPaint)
        }
        val sx = cx + cos(body.swirl) * r * 0.38f
        val sy = cy + sin(body.swirl) * r * 0.38f
        place(swirlShader, r * 0.6f, r * 0.6f, sx, sy)
        swirlPaint.alpha = ((0.35f + 0.3f * body.flash) * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(sx, sy, r * 0.6f, swirlPaint)
        drawHalftone(canvas, body, r, lx, ly, spread, dots)
    }

    /**
     * The halftone carried by the light: a diamond grid (a square grid turned 45°) centerd on the light, so it travels
     * with it, with a dot only in its brighter core, each as big as the light there, added as a barely-there glint.
     * Dots are batched into a few sizes, one draw each.
     */
    private fun drawHalftone(canvas: android.graphics.Canvas, body: MorphBouncePhysics.Body, r: Float, lx: Float, ly: Float, spread: Float, dots: HalftoneScratch) {
        val cell = max(minCell, r / 12f)
        val h = cell * HALF_SQRT2
        val span = ceil(min(spread, r * 1.1f) / h).toInt()
        val strength = 0.85f + 0.3f * body.flash
        dots.clear()
        val inv = 1f / spread
        for (ga in -span..span) {
            var gc = -span + ((ga + span) and 1)
            while (gc <= span) {
                val dx = ga * h
                val dy = gc * h
                val d2 = (dx * dx + dy * dy) * inv * inv
                if (d2 < 0.49f) {
                    val a = min((1f - sqrt(d2) / 0.7f).pow(1.6f) * strength, 1f)
                    if (a >= 0.12f) dots.add(lx + dx, ly + dy, a.pow(0.75f))
                }
                gc += 2
            }
        }
        dotPaint.alpha = ((0.08f + 0.06f * body.flash) * 255f).toInt().coerceIn(0, 255)
        for (bucket in 0 until HalftoneScratch.BUCKETS) {
            val n = dots.count[bucket]
            if (n == 0) continue
            dotPaint.strokeWidth = 2f * 0.3f * (bucket + 0.5f) / HalftoneScratch.BUCKETS * cell
            canvas.drawPoints(dots.points[bucket], 0, n * 2, dotPaint)
        }
    }

    /** The rim: its outline catching the light from above, fading down it. */
    fun drawRim(canvas: android.graphics.Canvas, path: android.graphics.Path, cy: Float, r: Float) {
        place(rimShader, 1f, 2f * r, 0f, cy - r)
        canvas.drawPath(path, rimPaint)
    }

    private fun place(shader: Shader, sx: Float, sy: Float, tx: Float, ty: Float) {
        matrix.setScale(sx, sy)
        matrix.postTranslate(tx, ty)
        shader.setLocalMatrix(matrix)
    }

    private companion object {
        val NEAR_BLACK = Color(0xFF060305)
        const val HALF_SQRT2 = 0.70710677f

        /** The role's deep tone: its hue, more saturated and much darker. */
        fun deepTone(light: Color): Color {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(light.toArgb(), hsv)
            hsv[1] = (hsv[1] * 1.6f).coerceAtMost(1f)
            hsv[2] *= 0.55f
            return Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
}

/** The halftone's dots for one shape, by size bucket, made once. */
private class HalftoneScratch {
    val points = Array(BUCKETS) { FloatArray(MAX_DOTS * 2) }
    val count = IntArray(BUCKETS)

    fun clear() = count.fill(0)

    /** A dot at [x], [y] of [size], 0 to 1 of the largest. */
    fun add(x: Float, y: Float, size: Float) {
        val bucket = (size * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
        val n = count[bucket]
        if (n >= MAX_DOTS) return
        points[bucket][2 * n] = x
        points[bucket][2 * n + 1] = y
        count[bucket] = n + 1
    }

    companion object {
        const val BUCKETS = 4
        const val MAX_DOTS = 600
    }
}

/**
 * The Material Morph animation shown under the last card of some settings pages (turned on in Appearance).
 * Heavy shapes, each weighing 50 to 100 lb at random, drop in from just under the bottom card onto a floor of rubber
 * at the bottom of the screen, press into it in their own outline and are thrown back up by it, a few times, until
 * they have flown out past a side of the screen, spinning: that is the only way a shape leaves; nothing fades. The
 * motion is real physics, see [MorphBouncePhysics]. Every bounce gives the app's one haptic tap (the back gesture's)
 * and morphs the shape into the next of a shuffled sequence as it leaves the rubber. Shapes come at random, one or a
 * burst of a few, never more than four at a time.
 *
 * It looks and moves like Google's Material 3 Expressive UI on Pixel:
 * - the shapes are the Material 3 Expressive shape set, MaterialShapes (see [MaterialMorphShapes]), each fitted into
 *   its body's circle so it never pokes out of it whichever way it turns, morphing with androidx.graphics.shapes'
 *   [Morph] on the expressive fast spatial spring;
 * - each is lit in the color language of the search bar and At a Glance ([PlateLook]): near-black, tinted with its
 *   Material You role ([MORPH_COLOR_ROLES]), with a pool of light inside that sloshes with its fall and bounce, a
 *   swirl, a flash on impact and a faint halftone grain carried by the light;
 * - the floor is drawn only where a shape breaks it, as before: each shape casts a contact shadow from one light above
 *   and a little to the left, sharp and dark on the floor, wider, softer and fainter the higher it is, with a faint
 *   reflection of its color on a dark page; pressed into the rubber it sinks past the floor's near edge, under which
 *   only a ghost of it shows, the edge dipping round it and catching its color along the stretch;
 * - a first landing sends three thin ripples in its color across the floor, following its outline seen flat on the
 *   floor; a later one, one sharp quick ripple;
 * - the silhouette it leaves is a real hollow in the floor's perspective, lit from above: crisp at its edge in its
 *   outline and color, its far wall in shadow, a thin bright lip on its near edge, soft occlusion just outside the
 *   rim. It closes as the rubber's dent there heals (never growing back), its last moment breaking into a few tiny
 *   pixels in the primary color that dissolve.
 *
 * Rules that hold however it is shown (pinned by MorphBouncePhysicsTest, MaterialMorphAnimationScaleTest and
 * MaterialMorphLookTest): at most four shapes; no shape rises back above the bottom card; nothing responds to touch;
 * every bounce is a new shape and a haptic tap; every shape lands at least twice and at most four times, and leaves
 * within [MorphBouncePhysics.MAX_LIFETIME] of appearing, by flying off the screen, drawn whole until it is fully past
 * the edge. Nothing is drawn when the phone's animations are turned off (Remove animations) or Battery Saver is on. The
 * scene and the shape sizes follow the area it is given (see [morphSceneFor]), never the font size; while the area is
 * scrolled out of sight, nothing moves and nothing taps. [LocalMorphRandomSeed] makes every run the same, shape
 * sequence included, for tests.
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
    val shapeNames = remember { MaterialMorphShapes.sequence(seed) }
    val shapes = remember(shapeNames) { shapeNames.map { MaterialMorphShapes.byName.getValue(it) } }
    val fits = remember(shapes) { shapes.map { MorphShapeFit.of(it) } }
    val physics = remember {
        MorphBouncePhysics(
            random = if (seed != null) Random(seed) else Random.Default,
            outlines = MorphOutlines.forSequence(shapeNames),
            planBudgetNanos = if (seed != null) 0L else MorphBouncePhysics.PLAN_BUDGET_NANOS
        )
    }
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
    // The floor's own color, a touch off the page's, which a shape sunk into it is seen through.
    val floorColor = lerp(colorScheme.background, colorScheme.onSurface, 0.12f)
    // The last moment of a healing silhouette breaks into a few pixels of this.
    val pixelColor = colorScheme.primary
    val looks = remember(colors, floorColor, density.density) { colors.map { PlateLook(it, floorColor, density.density) } }
    val shadowBrush = remember(shadowColor) { softFalloff(shadowColor) }
    val reflectionBrushes = remember(colors) { colors.map { softFalloff(it) } }
    // Shade falling off from a hollow's far wall, and darkening down the floor's slope into a dip.
    val shadeDownBrush = remember(shadowColor) { verticalFade(shadowColor, darkAtTop = true) }
    val shadeUpBrush = remember(shadowColor) { verticalFade(shadowColor, darkAtTop = false) }
    // The light a hollow's near lip and the stretched floor catch, from each shape's own color.
    val lipColors = remember(colors, isDark) { colors.map { if (isDark) lerp(it, Color.White, 0.3f) else it } }
    val membraneLights = remember(colors, isDark) { colors.map { lerp(it, Color.White, if (isDark) 0.45f else 0.15f) } }
    // Everything a frame draws with is made here, once: paths, poses, the shadow, strokes.
    val builder = remember { MorphPathBuilder() }
    val bodyPaths = remember { Array(physics.maxBodies) { android.graphics.Path() } }
    val bodyComposePaths = remember(bodyPaths) { Array(bodyPaths.size) { bodyPaths[it].asComposePath() } }
    val screenPaths = remember { Array(physics.maxBodies) { android.graphics.Path() } }
    val poses = remember { Array(physics.maxBodies) { MorphPose() } }
    val sunk = remember { BooleanArray(physics.maxBodies) }
    val sags = remember { FloatArray(physics.maxBodies) }
    // The floor's near edge, one point per rubber node (remade only if the node count changes).
    val edgeHolder = remember { arrayOf(FloatArray(MorphBouncePhysics.MIN_SHEET_NODES + 1)) }
    val membranePath = remember { android.graphics.Path() }
    val membraneComposePath = remember(membranePath) { membranePath.asComposePath() }
    val imprintPath = remember { android.graphics.Path() }
    val hollowPath = remember { android.graphics.Path() }
    val hollowComposePath = remember(hollowPath) { hollowPath.asComposePath() }
    val ripplePath = remember { android.graphics.Path() }
    val rippleComposePath = remember(ripplePath) { ripplePath.asComposePath() }
    val matrix = remember { android.graphics.Matrix() }
    val shadow = remember { MorphBouncePhysics.Shadow() }
    val halftone = remember { HalftoneScratch() }
    val rimStroke = remember(density) { Stroke(width = with(density) { 1.dp.toPx() }) }
    val occlusionStroke = remember(density) { Stroke(width = with(density) { 3.dp.toPx() }) }
    val wideOcclusionStroke = remember(density) { Stroke(width = with(density) { 7.dp.toPx() }) }
    val rippleStroke = remember(density) { Stroke(width = with(density) { 1.dp.toPx() }) }
    val hardRippleStroke = remember(density) { Stroke(width = with(density) { 1.5.dp.toPx() }) }
    val surfaceLinePx = with(density) { 1.dp.toPx() }
    val innerShadowPx = with(density) { 2.5.dp.toPx() }
    val lipPx = with(density) { 1.5.dp.toPx() }
    val membraneBandPx = with(density) { 1.5.dp.toPx() }
    val pixelPx = with(density) { 2.dp.toPx() }
    // How strongly each part of the floor is drawn: a light page a lighter touch, a dark one deeper shade.
    val occlusionAlpha = if (isDark) 0.22f else 0.08f
    val hollowAlpha = if (isDark) 0.26f else 0.1f
    val farWallAlpha = if (isDark) 0.5f else 0.2f
    val innerShadowAlpha = if (isDark) 0.3f else 0.12f
    val lipAlpha = if (isDark) 0.55f else 0.6f
    val edgeAlpha = if (isDark) 0.5f else 0.7f
    val softRippleAlpha = if (isDark) 0.42f else 0.6f
    val hardRippleAlpha = if (isDark) 0.6f else 0.75f
    val dipAlpha = if (isDark) 0.28f else 0.1f
    val slopeAlpha = if (isDark) 0.45f else 0.18f
    val membraneLightAlpha = if (isDark) 0.32f else 0.45f
    val floorLineAlpha = if (isDark) 0.5f else 0.28f
    val pixelAlphaMax = if (isDark) 0.75f else 0.6f
    val scanlineAlphaMax = if (isDark) 0.35f else 0.28f
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
        val measuredHeightPx = constraints.maxHeight.toFloat()
        // The area's height follows the cards above it, so a card closing up (a switch's rows rising away) grows it
        // on every frame of that motion. The scene takes the taller height once it has settled instead, so the
        // physics isn't re-sized frame after frame; meanwhile the shapes simply stay inside the smaller scene. A
        // shorter area is taken at once, so no shape is ever outside the area.
        val settledHeightPx = remember { mutableFloatStateOf(measuredHeightPx) }
        LaunchedEffect(measuredHeightPx) {
            if (measuredHeightPx > settledHeightPx.floatValue && settledHeightPx.floatValue > 0f) {
                kotlinx.coroutines.delay(MORPH_RESIZE_SETTLE_MILLIS)
            }
            settledHeightPx.floatValue = measuredHeightPx
        }
        val settled = settledHeightPx.floatValue
        val heightPx = if (settled <= 0f || measuredHeightPx < settled) measuredHeightPx else settled
        val scene = remember(widthPx, heightPx, density.density) { morphSceneFor(widthPx, heightPx, density.density) }
            ?: return@BoxWithConstraints
        val pxPerMetre = scene.pxPerMetre
        sceneInfo.pxPerMetre = pxPerMetre
        sceneInfo.sceneHeightPx = heightPx
        // Applied before this frame is drawn, so shapes never show at one size's scale in another size's area.
        SideEffect { physics.resize(scene.widthMetres, scene.heightMetres, scene.radiiMetres) }
        LaunchedEffect(Unit) {
            var lastHaptic = 0L
            val onBounce = MorphBouncePhysics.BounceListener { _, _ ->
                sceneInfo.bouncesFelt++
                val now = SystemClock.uptimeMillis()
                if (now - lastHaptic >= HAPTIC_SPACING_MS) {
                    lastHaptic = now
                    // Every landing worth feeling gets the app's one haptic.
                    hapticEngine.performPredictiveBackHaptic(view)
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
            val sheet = physics.sheet
            val base = physics.sheetBase
            val nodePx = physics.nodeSpacing * ppm

            // The floor's near edge: the rubber's surface seen in perspective, dipping a share of the way it does.
            if (edgeHolder[0].size != sheet.u.size) edgeHolder[0] = FloatArray(sheet.u.size)
            val edge = edgeHolder[0]
            for (i in edge.indices) edge[i] = floorPx + (SURFACE_SAG * (sheet.u[i] - base[i]) * ppm).toFloat()
            membranePath.rewind()
            membranePath.moveTo(-1f, -size.height)
            membranePath.lineTo(-1f, edge[0])
            for (i in edge.indices) membranePath.lineTo(i * nodePx, edge[i])
            membranePath.lineTo(size.width + 1f, edge[edge.lastIndex])
            membranePath.lineTo(size.width + 1f, -size.height)
            membranePath.close()

            for (i in 0 until count) {
                val body = bodies[i]
                val step = morphFor(body.shapeIndex)
                builder.build(step.morph, body.morphProgress, bodyPaths[i])
                morphPoseOf(body, poses[i], f)
                val t = body.morphProgress.coerceIn(0f, 1f)
                val fitScale = step.from.scale + (step.to.scale - step.from.scale) * t
                matrix.reset()
                matrix.postTranslate(-(step.from.centerX + (step.to.centerX - step.from.centerX) * t), -(step.from.centerY + (step.to.centerY - step.from.centerY) * t))
                matrix.postScale(body.radius * ppm * poses[i].scale * fitScale, body.radius * ppm * poses[i].scale * fitScale)
                matrix.postRotate(poses[i].rotationDegrees)
                matrix.postTranslate((body.prevX + (body.x - body.prevX) * f) * ppm, (body.prevY + (body.y - body.prevY) * f) * ppm)
                bodyPaths[i].transform(matrix, screenPaths[i])
                // In the rubber: how far its near edge dips under the shape.
                sunk[i] = body.inContact || body.y + body.radius > physics.floor
                var sag = 0f
                if (sunk[i]) {
                    val a = ((body.x - body.radius) / physics.nodeSpacing).toInt().coerceIn(0, edge.lastIndex)
                    val z = ceil((body.x + body.radius) / physics.nodeSpacing).toInt().coerceIn(0, edge.lastIndex)
                    for (n in a..z) sag = max(sag, edge[n] - floorPx)
                }
                sags[i] = sag
            }

            // The floor, under everything else: the silhouettes pressed into it, healing as the rubber does, and the
            // ripples each landing sends across it.
            for (imprint in physics.imprints) {
                if (!imprint.active) continue
                val step = morphFor(imprint.shapeIndex)
                builder.build(step.morph, imprint.morphProgress, imprintPath)
                val t = imprint.morphProgress.coerceIn(0f, 1f)
                val pivotX = step.from.centerX + (step.to.centerX - step.from.centerX) * t
                val pivotY = step.from.centerY + (step.to.centerY - step.from.centerY) * t
                val fitScale = step.from.scale + (step.to.scale - step.from.scale) * t
                val radiusPx = imprint.radius * ppm
                val sizePx = radiusPx * fitScale
                val xPx = imprint.x * ppm
                val colorIndex = imprint.bodyId % colors.size
                if (!imprint.holding) {
                    val heal = imprint.heal
                    val k = imprint.shrink
                    val depthPx = imprint.depth * ppm
                    val centreY = floorPx + depthPx * HOLLOW_CENTRE
                    // Where the floor line crossed it, closing in with the rest.
                    val top = centreY + (floorPx - centreY) * k
                    val lip = lipColors[colorIndex]
                    if (k > 0.01f) {
                        // The hollow: the silhouette at its deepest, closing on its center as the rubber heals.
                        matrix.reset()
                        matrix.postTranslate(-pivotX, -pivotY)
                        matrix.postScale(sizePx, sizePx)
                        matrix.postRotate(imprint.rotationDegrees)
                        matrix.postTranslate(xPx, imprint.y * ppm)
                        matrix.postScale(k, k, xPx, centreY)
                        imprintPath.transform(matrix, hollowPath)
                        val spanPx = radiusPx * MorphBouncePhysics.SHAPE_REACH * k
                        val shade = healShade(heal)
                        val rimLight = healRim(heal)
                        clipRect(top = top) {
                            if (shade > 0f) {
                                // Just outside the rim, the floor curling over into the hollow: soft occlusion.
                                clipPath(hollowComposePath, ClipOp.Difference) {
                                    drawPath(hollowComposePath, shadowColor, alpha = occlusionAlpha * 0.5f * shade, style = wideOcclusionStroke)
                                    drawPath(hollowComposePath, shadowColor, alpha = occlusionAlpha * shade, style = occlusionStroke)
                                }
                            }
                            clipPath(hollowComposePath) {
                                // Its floor, out of the light from above.
                                drawPath(hollowComposePath, shadowColor, alpha = hollowAlpha * shade)
                                // The far wall, under the floor line, darkest at the top, softening to the center.
                                drawUnitGradient(shadeDownBrush, xPx - spanPx, top, 2f * spanPx, (centreY - top) * 1.3f + 1f, farWallAlpha * shade)
                                // The inner shadow under every upper edge of the outline.
                                for (layer in 1..2) {
                                    val d = innerShadowPx * layer
                                    translate(top = d) {
                                        clipPath(hollowComposePath, ClipOp.Difference) {
                                            translate(top = -d) { drawPath(hollowComposePath, shadowColor, alpha = innerShadowAlpha * shade / layer) }
                                        }
                                    }
                                }
                                // The near wall faces the light: a thin bright lip along its lower edge.
                                translate(top = -lipPx) {
                                    clipPath(hollowComposePath, ClipOp.Difference) {
                                        translate(top = lipPx) { drawPath(hollowComposePath, lip, alpha = lipAlpha * rimLight) }
                                    }
                                }
                            }
                            // The crisp edge, in the shape's own outline and color, fading last.
                            drawPath(hollowComposePath, colors[colorIndex], alpha = edgeAlpha * rimLight, style = rimStroke)
                        }
                    }
                    // The hollow's last moment breaks into a few tiny pixels, which dissolve.
                    val phase = healPixelPhase(heal)
                    if (phase > 0f && phase < 1f) {
                        val halfWidth = radiusPx * 0.8f
                        val mirror = if (imprint.bodyId % 2 == 0) 1f else -1f
                        for (n in HEAL_PIXEL_X.indices) {
                            val a = pixelAlpha(phase, HEAL_PIXEL_DELAY[n])
                            if (a <= 0f) continue
                            val local = (phase - HEAL_PIXEL_DELAY[n]) / PIXEL_LIFE
                            val px = snapTo(xPx + mirror * HEAL_PIXEL_X[n] * halfWidth, pixelPx)
                            val py = snapTo(centreY + HEAL_PIXEL_Y[n] * depthPx * 0.5f - pixelPx * 1.5f * local, pixelPx)
                            val side = pixelPx * (1f - 0.45f * local)
                            drawRect(pixelColor, topLeft = Offset(px - side / 2f, py - side / 2f), size = Size(side, side), alpha = a * pixelAlphaMax)
                        }
                        val scan = scanlineAlpha(phase)
                        if (scan > 0f) {
                            val half = halfWidth * (0.45f + 0.8f * k)
                            for (line in 0..1) {
                                val y = snapTo(centreY + (line - 0.5f) * pixelPx * 1.5f, pixelPx / 2f)
                                drawLine(pixelColor, Offset(xPx - half, y), Offset(xPx + half, y), strokeWidth = surfaceLinePx * 0.75f, alpha = scan * scanlineAlphaMax * (1f - 0.4f * line))
                            }
                        }
                    }
                }
                if (imprint.soft) {
                    // A first landing's ripples, each following the silhouette's outline, flat on the floor.
                    for (r in 0 until SOFT_RIPPLES) {
                        val p = rippleProgress(imprint.sincePressed, r * SOFT_RIPPLE_GAP, SOFT_RIPPLE_TIME)
                        val a = rippleAlpha(p) * softRippleAlpha * (1f - 0.3f * r)
                        if (a < MorphBouncePhysics.SHADOW_VISIBLE) continue
                        val spread = rippleScale(p, SOFT_RIPPLE_SPREAD - SOFT_RIPPLE_SPREAD_STEP * r, SOFT_RIPPLE_EASE)
                        drawOutlineRipple(imprintPath, ripplePath, rippleComposePath, matrix, pivotX, pivotY, sizePx * spread, imprint.rotationDegrees, xPx, floorPx, colors[colorIndex], a, rippleStroke)
                    }
                } else {
                    // A later landing's one sharp, quick ripple.
                    val p = rippleProgress(imprint.sincePressed, 0f, MorphBouncePhysics.HARD_RIPPLE_TIME)
                    val a = rippleAlpha(p) * hardRippleAlpha
                    if (a >= MorphBouncePhysics.SHADOW_VISIBLE) {
                        val spread = rippleScale(p, HARD_RIPPLE_SPREAD, HARD_RIPPLE_EASE)
                        drawOutlineRipple(imprintPath, ripplePath, rippleComposePath, matrix, pivotX, pivotY, sizePx * spread, imprint.rotationDegrees, xPx, floorPx, colors[colorIndex], a, hardRippleStroke)
                    }
                }
            }

            // The floor round a shape pressing into it, in its shade, its slope darkening down into the dip.
            for (i in 0 until count) {
                if (!sunk[i]) continue
                val body = bodies[i]
                val stretch = membraneStretch(sags[i] / ppm, body.radius)
                if (stretch <= 0f) continue
                val xPx = (body.prevX + (body.x - body.prevX) * f) * ppm
                val reach = body.radius * ppm * MEMBRANE_REACH
                drawSoft(shadowBrush, xPx, floorPx + sags[i] * 0.6f, 2f * reach, dipAlpha * stretch)
                clipRect(top = floorPx) {
                    clipPath(membraneComposePath) {
                        drawUnitGradient(shadeUpBrush, xPx - reach, floorPx, 2f * reach, sags[i].coerceAtLeast(1f), slopeAlpha * stretch)
                    }
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
                    val t = body.morphProgress.coerceIn(0f, 1f)
                    val fitScale = step.from.scale + (step.to.scale - step.from.scale) * t
                    val pivotX = step.from.centerX + (step.to.centerX - step.from.centerX) * t
                    val pivotY = step.from.centerY + (step.to.centerY - step.from.centerY) * t
                    // (A shape pressed into the floor casts none beside it: there, only the floor closing dark round it.)
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
                // A dark floor shows a faint reflection of the shape's color as it comes close.
                if (isDark && body.penetration <= 0f) {
                    val closeness = 1f - ((physics.floor - yM - body.radius) / (6f * body.radius)).coerceIn(0f, 1f)
                    if (closeness > 0.02f) drawSoft(reflectionBrushes[body.id % colors.size], xPx, floorPx, 2.4f * radiusPx, 0.22f * closeness * closeness)
                }
            }

            // The shapes: above the floor's near edge each as it is; pressed under it, only a ghost of it shows.
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                for (i in 0 until count) {
                    val body = bodies[i]
                    val pose = poses[i]
                    if (pose.scale <= 0f) continue
                    val look = looks[body.id % looks.size]
                    val cx = (body.prevX + (body.x - body.prevX) * f) * ppm
                    val cy = (body.prevY + (body.y - body.prevY) * f) * ppm
                    val r = body.radius * ppm * pose.scale
                    val path = screenPaths[i]
                    canvas.withSave {
                        if (sunk[i]) clipPath(membranePath)
                        withClip(path) { look.draw(this, body, cx, cy, r, halftone) }
                        look.drawRim(this, path, cy, r)
                    }
                    if (sunk[i]) {
                        canvas.withSave {
                            clipOutPath(membranePath)
                            drawPath(path, look.ghostPaint)
                        }
                    }
                }
            }

            // The floor's surface, seen only where a shape has broken it: dipping round a pressing shape and catching
            // its light along the stretch, then springing back past its rest line and ringing out across the rubber.
            for (i in 0 until count) {
                if (!sunk[i]) continue
                val body = bodies[i]
                val sagPx = sags[i]
                val stretch = membraneStretch(sagPx / ppm, body.radius)
                if (stretch <= 0f) continue
                val light = membraneLights[body.id % colors.size]
                val xPx = (body.prevX + (body.x - body.prevX) * f) * ppm
                val reach = body.radius * ppm * MEMBRANE_REACH
                val a = ((xPx - reach) / nodePx).toInt().coerceIn(0, edge.lastIndex)
                val z = ceil((xPx + reach) / nodePx).toInt().coerceIn(0, edge.lastIndex)
                for (band in 1..3) {
                    for (n in a until z) {
                        val d0 = ((edge[n] - floorPx) / sagPx).coerceIn(0f, 1f)
                        val d1 = ((edge[n + 1] - floorPx) / sagPx).coerceIn(0f, 1f)
                        val fade = min(d0, d1)
                        if (fade <= 0.01f) continue
                        drawLine(
                            light,
                            Offset(n * nodePx, edge[n] + band * membraneBandPx * d0),
                            Offset((n + 1) * nodePx, edge[n + 1] + band * membraneBandPx * d1),
                            strokeWidth = membraneBandPx * 1.2f,
                            alpha = (membraneLightAlpha * stretch / band * fade).coerceAtMost(1f)
                        )
                    }
                }
            }
            val typicalPx = scene.radiiMetres[1] * ppm
            for (n in 0 until edge.lastIndex) {
                val moved = max(abs(edge[n] - floorPx), abs(edge[n + 1] - floorPx)) / (0.12f * typicalPx)
                var fresh = 0f
                val x = (n + 0.5f) * nodePx
                for (imprint in physics.imprints) {
                    if (!imprint.active) continue
                    val strength = imprint.shrink
                    if (strength <= 0f) continue
                    fresh = max(fresh, strength * membraneBump((x - imprint.x * ppm) / (imprint.radius * ppm * MEMBRANE_REACH)))
                }
                val strength = (fresh * 0.5f + moved).coerceIn(0f, 1f)
                if (strength < 0.02f) continue
                drawLine(shadowColor, Offset(n * nodePx, edge[n]), Offset((n + 1) * nodePx, edge[n + 1]), strokeWidth = surfaceLinePx, alpha = floorLineAlpha * sqrt(strength))
            }
        }
    }
}

/** The floor's near edge dips this share of the way the rubber under a shape does: the dent seen in perspective. */
internal const val SURFACE_SAG = 0.3f

/** A healing hollow closes on this point, this share of its depth below the floor line. */
private const val HOLLOW_CENTRE = 0.45f

/** A shape pressed past the floor's near edge is hidden under it: only this much of a ghost of it shows through. */
private const val IN_FLOOR_TINT = 0.72f

/** Where the healing hollow's pixels sit (across its half width, down its half depth) and when each starts. */
private val HEAL_PIXEL_X = floatArrayOf(-0.62f, -0.24f, 0.12f, 0.46f, -0.4f, 0.3f)
private val HEAL_PIXEL_Y = floatArrayOf(-0.2f, 0.25f, -0.35f, 0.1f, 0.55f, 0.45f)
private val HEAL_PIXEL_DELAY = floatArrayOf(0f, 0.16f, 0.06f, 0.24f, 0.32f, MAX_PIXEL_DELAY)

private fun snapTo(value: Float, grid: Float): Float = round(value / grid) * grid

/** A vertical fade of [color] over [UNIT], from full at the top to nothing ([darkAtTop]) or the other way, eased. */
private fun verticalFade(color: Color, darkAtTop: Boolean): Brush = Brush.verticalGradient(
    0f to color.copy(alpha = if (darkAtTop) color.alpha else 0f),
    0.5f to color.copy(alpha = color.alpha * 0.35f),
    1f to color.copy(alpha = if (darkAtTop) 0f else color.alpha),
    startY = 0f,
    endY = UNIT
)

/** [brush], made over a [UNIT] square, drawn over the rectangle at [left], [top], [width] by [height]. */
private fun DrawScope.drawUnitGradient(brush: Brush, left: Float, top: Float, width: Float, height: Float, alpha: Float) {
    if (width <= 0f || height <= 0f || alpha <= 0f) return
    translate(left, top) {
        scale(width / UNIT, height / UNIT, pivot = Offset.Zero) {
            drawRect(brush, topLeft = Offset.Zero, size = Size(UNIT, UNIT), alpha = alpha.coerceAtMost(1f))
        }
    }
}

/**
 * A ripple on the floor: [source], a shape's outline in its polygon's units, drawn [sizePx] per unit about its pivot,
 * turned [rotationDegrees] and seen flat on the floor round [cx], [cy], as a thin line in [color]. Transformed into
 * [target] (whose Compose view is [targetCompose]), so the line keeps its width however far the ripple has spread.
 */
private fun DrawScope.drawOutlineRipple(
    source: android.graphics.Path,
    target: android.graphics.Path,
    targetCompose: androidx.compose.ui.graphics.Path,
    matrix: android.graphics.Matrix,
    pivotX: Float,
    pivotY: Float,
    sizePx: Float,
    rotationDegrees: Float,
    cx: Float,
    cy: Float,
    color: Color,
    alpha: Float,
    stroke: Stroke
) {
    matrix.reset()
    matrix.postTranslate(-pivotX, -pivotY)
    matrix.postScale(sizePx, sizePx)
    matrix.postRotate(rotationDegrees)
    matrix.postScale(1f, FLOOR_FORESHORTENING)
    matrix.postTranslate(cx, cy)
    source.transform(matrix, target)
    drawPath(targetCompose, color, alpha = alpha.coerceAtMost(1f), style = stroke)
}

/** A soft falloff ellipse [widthPx] wide (and [FLOOR_FORESHORTENING] as tall), centerd on [cx], [cy], lying on the floor. */
private fun DrawScope.drawSoft(brush: Brush, cx: Float, cy: Float, widthPx: Float, alpha: Float) {
    if (widthPx <= 0f || alpha <= 0f) return
    translate(cx, cy) {
        scale(widthPx / 2f / UNIT, widthPx * FLOOR_FORESHORTENING / 2f / UNIT, pivot = Offset.Zero) {
            drawCircle(brush, radius = UNIT, center = Offset.Zero, alpha = alpha.coerceAtMost(1f))
        }
    }
}
