package com.pixel.intelligentsearch.core.ui.expressive

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The colours one matte shell is drawn with, worked out from the theme by [rememberMatteShellLook].
 *
 * [glowPrimary] and [glowTertiary] are the soft Material You light behind the shell; [rimTop] and [rimBottom] the
 * rim light along its edge, bright at the top and fading down; [accentRim] an extra full rim (a selected shell),
 * transparent for none; [grainAlpha] how strongly the film grain shows; the shadow colours lift it off the page.
 */
@Immutable
class MatteShellLook(
    val container: Color,
    val glowPrimary: Color,
    val glowTertiary: Color,
    val rimTop: Color,
    val rimBottom: Color,
    val accentRim: Color,
    val grainAlpha: Float,
    val ambientShadow: Color,
    val spotShadow: Color
)

/**
 * The [MatteShellLook] for a shell filled with [containerColor] in the current theme. A [nested] shell (one inside
 * another, like a shortcut card in a settings card) keeps the grain and rim but leaves the glow to its parent.
 */
@Composable
fun rememberMatteShellLook(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    accentRim: Color = Color.Transparent,
    nested: Boolean = false
): MatteShellLook {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return remember(scheme, containerColor, accentRim, nested, dark) {
        MatteShellLook(
            container = containerColor,
            glowPrimary = if (nested) Color.Transparent else scheme.primary.copy(alpha = if (dark) 0.18f else 0.10f),
            glowTertiary = if (nested) Color.Transparent else scheme.tertiary.copy(alpha = if (dark) 0.09f else 0.06f),
            // On a dark shell the rim is light catching the top edge; on a light one, a bright edge over a soft line.
            rimTop = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.9f),
            rimBottom = scheme.outlineVariant.copy(alpha = if (dark) 0.14f else 0.42f),
            accentRim = accentRim,
            grainAlpha = if (dark) 0.055f else 0.035f,
            ambientShadow = if (dark) scheme.primary.copy(alpha = 0.45f) else Color.Black,
            // In the dark theme the spot shadow is a faint primary glow under the shell rather than a darker patch.
            spotShadow = if (dark) scheme.primary.copy(alpha = 0.6f) else scheme.primary.copy(alpha = 0.55f)
        )
    }
}

/**
 * Draws a matte shell behind (and its rim over) what the modified element holds, clipped to [shape]: the [look]'s
 * container tone, a soft primary glow rising from below and a tertiary one from the top end, a fine static film
 * grain, a rim light that is brightest along the top edge, and, at a [shadowElevation] above zero, a soft shadow.
 *
 * Every brush is built once per size and theme ([drawWithCache]); the grain is a baked tile ([MatteGrain]), so
 * nothing is re-rendered from frame to frame and a page of shells scrolls as cheaply as plain cards.
 */
fun Modifier.matteShell(look: MatteShellLook, shape: Shape, shadowElevation: Dp = 0.dp): Modifier =
    this
        .then(
            if (shadowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = shadowElevation,
                    shape = shadowShapeFor(shape),
                    clip = false,
                    ambientColor = look.ambientShadow,
                    spotColor = look.spotShadow
                )
            } else Modifier
        )
        .clip(shape)
        .drawWithCache {
            val w = size.width
            val h = size.height
            val outline = shape.createOutline(size, layoutDirection, this)
            val ltr = layoutDirection == LayoutDirection.Ltr
            val glowFromBelow = Brush.radialGradient(
                colors = listOf(look.glowPrimary, Color.Transparent),
                center = Offset(w * 0.5f, h + w * 0.15f),
                radius = max(w * 0.7f, 1f)
            )
            val glowFromTopEnd = Brush.radialGradient(
                colors = listOf(look.glowTertiary, Color.Transparent),
                center = Offset(if (ltr) w * 0.95f else w * 0.05f, -w * 0.1f),
                radius = max(w * 0.55f, 1f)
            )
            // The rim fades over the top 64dp, so a tall card is lit at its top edge like a short pill is.
            val rim = Brush.verticalGradient(
                colors = listOf(look.rimTop, look.rimBottom),
                startY = 0f,
                endY = max(min(h, 64.dp.toPx()), 1f)
            )
            // Stroked on the outline and clipped to it, so half the width shows: a 1dp rim.
            val rimStroke = Stroke(width = 2.dp.toPx())
            val grain = MatteGrain.brush(MatteGrain.cellPxFor(density))
            val hasGlow = look.glowPrimary.alpha > 0f || look.glowTertiary.alpha > 0f
            val hasAccent = look.accentRim.alpha > 0f
            onDrawWithContent {
                drawRect(look.container)
                if (hasGlow) {
                    drawRect(glowFromBelow)
                    drawRect(glowFromTopEnd)
                }
                drawRect(grain, alpha = look.grainAlpha)
                drawContent()
                drawOutline(outline, rim, style = rimStroke)
                if (hasAccent) drawOutline(outline, look.accentRim, style = rimStroke)
            }
        }

/**
 * The container every settings page groups its controls in: a matte shell ([matteShell]) holding a column, with
 * the content colour that goes with [containerColor]. A top-level shell floats on a soft shadow and rises in once,
 * the first time it is shown (not again when scrolled back into a list); a [nested] shell sits flat inside another.
 * [accentRim] rims the whole shell in a colour, as a selected shell is.
 */
@Composable
fun MatteShell(
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveCornerTokens.ShapeExtraLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    accentRim: Color = Color.Transparent,
    nested: Boolean = false,
    shadowElevation: Dp = if (nested) 0.dp else MatteShellDefaults.ShadowElevation,
    content: @Composable ColumnScope.() -> Unit
) {
    val look = rememberMatteShellLook(containerColor, accentRim, nested)
    Column(
        modifier = modifier
            .then(if (nested) Modifier else Modifier.shellEntrance())
            .semantics { isTraversalGroup = true }
            .matteShell(look, shape, shadowElevation)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColorFor(containerColor)) {
            content()
        }
    }
}

/**
 * The shape a shell's shadow is cast from. A [SquircleShape]'s outline is a path, and a shadow cast from a path
 * can come out boxy where shadows are drawn without a renderer that handles any path; the same corners as a
 * rounded rectangle (its effective radii, see [SquircleShape]) give the shadow a rounded outline everywhere.
 */
internal fun shadowShapeFor(shape: Shape): Shape {
    if (shape !is SquircleShape) return shape
    val grow = 1f + shape.smoothing.coerceIn(0f, 1f) * 0.5f
    return androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = shape.topStart * grow,
        topEnd = shape.topEnd * grow,
        bottomEnd = shape.bottomEnd * grow,
        bottomStart = shape.bottomStart * grow
    )
}

object MatteShellDefaults {
    /** How high a top-level shell floats: enough for a soft shadow (a faint glow in the dark theme), no more. */
    val ShadowElevation: Dp = 3.dp
}

/**
 * The shell's entrance: it fades in while rising 12dp and growing from 97%, on the Material 3 Expressive default
 * spatial spring. It plays once per shell; with animations off the shell is simply there.
 */
@Composable
private fun Modifier.shellEntrance(): Modifier {
    val animationsOn by rememberAnimationsEnabled()
    var entered by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (entered || !animationsOn) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, ExpressiveMotionTokens.Spatial.ExpressiveDefault())
        entered = true
    }
    return graphicsLayer {
        val p = progress.value
        if (p < 1f || p > 1f) {
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 12.dp.toPx()
            val scale = 0.97f + 0.03f * p
            scaleX = scale
            scaleY = scale
        }
    }
}

/**
 * The matte shells' film grain: a 64 × 64-cell tile of light and dark specks, baked once per cell size and
 * repeated by a bitmap shader. Its seed is fixed, so the grain is the same on every run (and in every golden).
 * The specks are as often light as dark, so the grain textures a surface without shifting its tone, and text on
 * it keeps its contrast.
 */
object MatteGrain {
    private const val CELLS = 64
    private const val SEED = 0x5EED_6A1EL
    private val brushes = HashMap<Int, ShaderBrush>()
    private val tiles = HashMap<Int, ImageBitmap>()

    /** Grain cells about 0.75dp across, never below a pixel: fine, but visible on a dense screen. */
    fun cellPxFor(density: Float): Int = max(1, (density * 0.75f).roundToInt())

    @Synchronized
    fun tile(cellPx: Int): ImageBitmap = tiles.getOrPut(cellPx) { bake(cellPx) }

    @Synchronized
    fun brush(cellPx: Int): ShaderBrush = brushes.getOrPut(cellPx) {
        ShaderBrush(ImageShader(tile(cellPx), TileMode.Repeated, TileMode.Repeated))
    }

    /** The speck colour for a uniform random [v]: lighter or darker by how far it is from the middle. */
    internal fun speck(v: Float): Int {
        val strength = (abs(v - 0.5f) * 2f).pow(1.5f)
        val alpha = (strength * 255f).roundToInt().coerceIn(0, 255)
        val rgb = if (v >= 0.5f) 0xFFFFFF else 0x000000
        return (alpha shl 24) or rgb
    }

    private fun bake(cellPx: Int): ImageBitmap {
        val side = CELLS * cellPx
        val pixels = IntArray(side * side)
        val random = java.util.Random(SEED)
        for (cy in 0 until CELLS) {
            for (cx in 0 until CELLS) {
                val color = speck(random.nextFloat())
                for (y in cy * cellPx until (cy + 1) * cellPx) {
                    val row = y * side
                    for (x in cx * cellPx until (cx + 1) * cellPx) pixels[row + x] = color
                }
            }
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
