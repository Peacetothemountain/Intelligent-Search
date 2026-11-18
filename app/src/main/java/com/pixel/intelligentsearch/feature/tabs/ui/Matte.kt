package com.pixel.intelligentsearch.feature.tabs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Ported from Intelligent Search (core/ui/expressive/MatteShell.kt and ExpressiveCornerTokens.kt), unchanged in what
// it draws: the settings cards' matte shell, its film grain, and the rounded shape behind the corner tokens. The one
// card and the one grain every tab draws.

/**
 * The page's side gutter on every tab: content cards sit this far from the screen's edges, as on Intelligent Search's
 * Settings (16 dp of page padding and the cards' own 4 dp).
 */
internal val PageGutter = 20.dp

/**
 * Rounded rectangle with its own radius per corner, behind the app's corner tokens. Each corner is a circular arc with
 * an effective radius of r × (1 + [smoothing] / 2), capped at half the shorter side, handed over as a plain rounded
 * rectangle so everything drawn in it takes the fast rounded-rectangle paths.
 */
internal class SquircleShape(private val radius: Dp, private val smoothing: Float = 0.6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(size.toRect())
        val cap = min(size.width, size.height) / 2f
        val px = with(density) { radius.toPx() }.coerceIn(0f, cap)
        if (px <= 0f) return Outline.Rectangle(size.toRect())
        val r = CornerRadius(min(px * (1f + smoothing.coerceIn(0f, 1f) * 0.5f), cap))
        return Outline.Rounded(RoundRect(size.toRect(), r, r, r, r))
    }

    override fun equals(other: Any?) = other is SquircleShape && other.radius == radius && other.smoothing == smoothing
    override fun hashCode() = 31 * radius.hashCode() + smoothing.hashCode()
}

/** The app's corner tokens' shapes (ExpressiveCornerTokens). */
internal object ExpressiveCornerTokens {
    val ShapeExtraLarge: Shape = SquircleShape(28.dp)
}

/**
 * How one matte shell is drawn. A shell is the container it always was, the same colour, border and shape, with only a
 * matte texture added. A lit shell (the settings pages' main cards) has a light behind it, as the floating bar in
 * Google's own apps does: [edgeLight] shows only as a thin band just outside its edge, brightest along the top, and
 * [underShadow] is a soft shade beneath it. Both are behind the container, never over it.
 */
@Immutable
internal class MatteShellLook(
    val container: Color,
    val border: BorderStroke?,
    val grainAlpha: Float,
    val edgeLight: Color,
    val underShadow: Color
)

@Composable
internal fun rememberMatteShellLook(
    containerColor: Color = MaterialTheme.colorScheme.pageContainer,
    border: BorderStroke? = MatteShellDefaults.border(),
    lit: Boolean = false
): MatteShellLook {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val shown = lit && containerColor.alpha > 0f
    return remember(containerColor, border, dark, shown, scheme.onSurface) {
        MatteShellLook(
            container = containerColor,
            border = border,
            grainAlpha = when {
                containerColor.alpha <= 0f -> 0f
                // Faint enough that no speck shows: the surface reads as soft and unshiny, not textured.
                dark -> 0.022f
                else -> 0.014f
            },
            edgeLight = if (!shown || !dark) Color.Transparent else scheme.onSurface.copy(alpha = 0.22f),
            underShadow = when {
                !shown -> Color.Transparent
                dark -> Color.Black.copy(alpha = 0.30f)
                else -> Color.Black.copy(alpha = 0.10f)
            }
        )
    }
}

/**
 * Draws the [look]'s container behind what the modified element holds: [MatteShellLook.border], the container colour in
 * [shape] and the content clipped to it, with a fine static film grain over the container ([MatteGrain]).
 */
internal fun Modifier.matteShell(look: MatteShellLook, shape: Shape): Modifier =
    this
        .then(
            if (look.underShadow.alpha > 0f) {
                Modifier.shellShadow(shape, radius = 10.dp, color = look.underShadow, offset = DpOffset(0.dp, 4.dp))
            } else Modifier
        )
        .then(
            if (look.edgeLight.alpha > 0f) {
                Modifier.shellShadow(shape, radius = 6.dp, color = look.edgeLight, offset = DpOffset(0.dp, (-1.5).dp))
            } else Modifier
        )
        .then(if (look.border != null) Modifier.border(look.border, shape) else Modifier)
        .background(look.container, shape)
        .clip(shape)
        .then(
            if (look.grainAlpha > 0f) {
                Modifier.drawWithCache {
                    val grain = if (MatteGrain.drawn) MatteGrain.brush(1) else null
                    onDrawBehind { if (grain != null) drawRect(grain, alpha = look.grainAlpha) }
                }
            } else Modifier
        )

/**
 * A soft shadow (or light) of [color] behind the element in [shape], blurred by [radius] and moved by [offset], drawn
 * with a [android.graphics.BlurMaskFilter].
 *
 * A rectangle or a rounded rectangle with the same corners all round goes straight onto the canvas: Android's fast
 * path, which blurs a small corner once and stretches it. Any other shape (a card with big corners at one end and
 * small ones at the other) has no fast path: Android blurred a mask of the whole card on the GPU again for every
 * fractional place it was scrolled to, and kept each one, hundreds of card-sized surfaces on Wellbeing. So that
 * shadow is blurred once into a picture of its own, at [SHADOW_DOWNSCALE] of the resolution (a blur this soft has no
 * detail finer than that), and the picture is only moved.
 */
internal fun Modifier.shellShadow(shape: Shape, radius: Dp, color: Color, offset: DpOffset = DpOffset.Zero): Modifier = drawWithCache {
    val blur = radius.toPx()
    val dx = offset.x.toPx()
    val dy = offset.y.toPx()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (blur > 0f) maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val outline = shape.createOutline(size, layoutDirection, this)
    val rounded = (outline as? Outline.Rounded)?.roundRect
    val uniform = rounded != null && rounded.topLeftCornerRadius == rounded.topRightCornerRadius &&
        rounded.topLeftCornerRadius == rounded.bottomRightCornerRadius && rounded.topLeftCornerRadius == rounded.bottomLeftCornerRadius
    val path = if (uniform || outline is Outline.Rectangle) null else when (outline) {
        is Outline.Rounded -> androidx.compose.ui.graphics.Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
        is Outline.Rectangle -> null
    }?.asAndroidPath()
    if (path != null && blur > 0f) {
        // The blur's reach: three standard deviations (Android's sigma for a blur radius r is 0.57735 r + 0.5).
        val reach = ceil(3f * (0.57735f * blur + 0.5f))
        val k = SHADOW_DOWNSCALE
        val bakedSize = IntSize(ceil((size.width + 2f * reach) / k).toInt(), ceil((size.height + 2f * reach) / k).toInt())
        val baked = obtainGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
            record(size = bakedSize) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.withScale(1f / k, 1f / k, 0f, 0f) {
                        withTranslation(reach + dx, reach + dy) { drawPath(path, paint) }
                    }
                }
            }
        }
        // Drawn back at full size.
        return@drawWithCache onDrawBehind { translate(-reach, -reach) { scale(k, k, pivot = Offset.Zero) { drawLayer(baked) } } }
    }
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.withTranslation(dx, dy) {
                when {
                    uniform -> drawRoundRect(
                        rounded.left, rounded.top, rounded.right, rounded.bottom,
                        rounded.topLeftCornerRadius.x, rounded.topLeftCornerRadius.y, paint
                    )
                    path != null -> drawPath(path, paint)
                    else -> drawRect(0f, 0f, size.width, size.height, paint)
                }
            }
        }
    }
}

/** The resolution a [shellShadow] without Android's fast path is blurred at: half, in each direction. */
private const val SHADOW_DOWNSCALE = 2f

/**
 * The one card every tab draws: Intelligent Search's lit matte card ([lit] by default), the container color with a
 * faint outline-variant hairline, its shade beneath and its top-edge light, only gaining the matte grain on its
 * surface. A container that isn't a page card (a sheet, a dialog) passes [lit] false and no border.
 */
@Composable
internal fun Modifier.matteCard(
    shape: Shape = ExpressiveCornerTokens.ShapeExtraLarge,
    container: Color = MaterialTheme.colorScheme.pageContainer,
    lit: Boolean = true,
    border: Boolean = true
): Modifier {
    val look = rememberMatteShellLook(container, if (border) MatteShellDefaults.border() else null, lit)
    return matteShell(look, shape)
}

/** A column in the matte shell ([matteShell]), with the content colour that goes with [containerColor]. */
@Composable
internal fun MatteShell(
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveCornerTokens.ShapeExtraLarge,
    containerColor: Color = MaterialTheme.colorScheme.pageContainer,
    border: BorderStroke? = MatteShellDefaults.border(),
    lit: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val look = rememberMatteShellLook(containerColor, border, lit)
    Column(modifier = modifier.semantics { isTraversalGroup = true }.matteShell(look, shape)) {
        CompositionLocalProvider(LocalContentColor provides contentColorFor(containerColor)) { content() }
    }
}

internal object MatteShellDefaults {
    /** The settings cards' outline: a 1dp hairline in the outline-variant colour at a fifth of its strength. */
    @Composable
    fun border(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
}

/**
 * The matte shells' film grain: a 64 × 64-cell tile of light and dark specks, baked once and repeated by a bitmap
 * shader. Its seed is fixed, so the grain is the same on every run. The specks are as often light as dark, so the
 * grain textures a surface without shifting its tone.
 */
internal object MatteGrain {
    private const val CELLS = 64
    private const val SEED = 0x5EED_6A1EL
    private val brushes = HashMap<Int, ShaderBrush>()

    /** Screenshot tests turn it off (system property `intelligentsearch.matteGrain` = `off`), as the app's goldens do. */
    @Volatile
    var drawn: Boolean = System.getProperty("intelligentsearch.matteGrain") != "off"

    @Synchronized
    fun brush(cellPx: Int): ShaderBrush = brushes.getOrPut(cellPx) {
        ShaderBrush(ImageShader(bake(cellPx), TileMode.Repeated, TileMode.Repeated))
    }

    private fun speck(v: Float): Int {
        val strength = (abs(v - 0.5f) * 2f).pow(2f)
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
