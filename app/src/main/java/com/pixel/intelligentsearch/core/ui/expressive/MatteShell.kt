package com.pixel.intelligentsearch.core.ui.expressive

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.withTranslation
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * How one matte shell is drawn, worked out by [rememberMatteShellLook]. A shell is the container it always was,
 * the same colour, border and shape, with only a matte texture added: [container] is exactly the caller's colour,
 * [border] the container's own outline (null for none), and [grainAlpha] how strongly the film grain shows on it
 * (none on a container with no fill, which stays as see-through as it was). A lit shell (the settings pages' main
 * cards) has a light behind it, as the floating bar in Google's own apps does: [edgeLight] is a tight light behind
 * the container that shows only as a thin band just outside its edge, brightest along the top, and [underShadow] a
 * soft shade beneath it. Both are behind the container, never over it (transparent for an unlit shell).
 */
@Immutable
class MatteShellLook(
    val container: Color,
    val border: BorderStroke?,
    val grainAlpha: Float,
    val edgeLight: Color,
    val underShadow: Color
)

/** The [MatteShellLook] for a container filled with [containerColor] and outlined with [border] in the current theme. */
@Composable
fun rememberMatteShellLook(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
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
            // Matched to Google's floating bar: just outside the edge the light peaks about a tenth brighter than the
            // page in the dark theme, in the theme's own on-surface tone. A light page can't show a light behind a
            // card, so there only the soft shade beneath remains.
            edgeLight = when {
                !shown || !dark -> Color.Transparent
                else -> scheme.onSurface.copy(alpha = 0.22f)
            },
            underShadow = when {
                !shown -> Color.Transparent
                dark -> Color.Black.copy(alpha = 0.30f)
                else -> Color.Black.copy(alpha = 0.10f)
            }
        )
    }
}

/**
 * Draws the [look]'s container behind what the modified element holds, as a Material Surface or Card draws its
 * own: [border] outline, the container colour in [shape] and the content clipped to it, flat on the page with no
 * shadow. The only addition is a fine static film grain over the container, as often lighter as darker, so the
 * container keeps its colour and only turns matte.
 *
 * The grain is a baked tile ([MatteGrain]) held in a brush built once per size ([drawWithCache]), so nothing is
 * re-rendered from frame to frame and a page of shells scrolls as cheaply as plain cards.
 */
fun Modifier.matteShell(look: MatteShellLook, shape: Shape): Modifier =
    this
        // Behind the container: a soft shade under it, then the light that peeks out around its edge, a little
        // higher than the container so the top edge is the brightest, as on Google's floating bar.
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
                    val grain = if (MatteGrain.drawn) MatteGrain.brush(MatteGrain.cellPxFor(density)) else null
                    onDrawBehind {
                        if (grain != null) drawRect(grain, alpha = look.grainAlpha)
                    }
                }
            } else Modifier
        )

/**
 * A soft shadow (or light) of [color] behind the element in [shape], blurred by [radius] and moved by [offset]: the
 * same blurred shape Compose's dropShadow draws (a [android.graphics.BlurMaskFilter] of that radius), drawn straight
 * onto the canvas instead of into a bitmap. Compose's dropShadow renders and blurs a new bitmap on the main thread
 * for every size the element takes, so a card growing or shrinking (rows rising away under a switch) re-blurred its
 * whole area every frame; drawn directly, a rounded rectangle's blur is Android's own fast path on the render thread
 * and a new height costs nothing.
 */
fun Modifier.shellShadow(shape: Shape, radius: Dp, color: Color, offset: DpOffset = DpOffset.Zero): Modifier = drawWithCache {
    val blur = radius.toPx()
    val dx = offset.x.toPx()
    val dy = offset.y.toPx()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (blur > 0f) maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val outline = shape.createOutline(size, layoutDirection, this)
    val rounded = (outline as? Outline.Rounded)?.roundRect
    // A rounded rectangle with one radius all round draws as one; anything else as its path.
    val uniform = rounded != null && rounded.topLeftCornerRadius == rounded.topRightCornerRadius &&
        rounded.topLeftCornerRadius == rounded.bottomRightCornerRadius && rounded.topLeftCornerRadius == rounded.bottomLeftCornerRadius
    val path = if (uniform || outline is Outline.Rectangle) null else when (outline) {
        is Outline.Rounded -> androidx.compose.ui.graphics.Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
        else -> null
    }?.asAndroidPath()
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.withTranslation(dx, dy) {
                when {
                    uniform && rounded != null -> drawRoundRect(
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

/**
 * A container in the matte shell ([matteShell]) holding a column, with the content colour that goes with
 * [containerColor]: the same colour, [border] and [shape] the container had as a Card or Surface, flat and
 * static, with only the matte grain added to its surface. The border defaults to the settings cards' own
 * ([MatteShellDefaults.border]); pass null for a container that had none.
 */
@Composable
fun MatteShell(
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveCornerTokens.ShapeExtraLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    border: BorderStroke? = MatteShellDefaults.border(),
    lit: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val look = rememberMatteShellLook(containerColor, border, lit)
    Column(
        modifier = modifier
            .semantics { isTraversalGroup = true }
            .matteShell(look, shape)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColorFor(containerColor)) {
            content()
        }
    }
}

object MatteShellDefaults {
    /** The settings cards' outline: a 1dp hairline in the outline-variant colour at a fifth of its strength. */
    @Composable
    fun border(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
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

    /**
     * Whether shells draw the grain. Screenshot tests turn it off (system property `intelligentsearch.matteGrain`
     * = `off`, set for every unit test run): random specks don't compress, and with them each settings golden is
     * about 1 MB. `MatteShellGoldenTest` turns it back on to pin the grain itself.
     */
    @Volatile
    var drawn: Boolean = System.getProperty("intelligentsearch.matteGrain") != "off"

    /** One screen pixel per grain cell, too fine to see as specks on any screen: a matte finish, not a texture. */
    @Suppress("UNUSED_PARAMETER")
    fun cellPxFor(density: Float): Int = 1

    @Synchronized
    fun tile(cellPx: Int): ImageBitmap = tiles.getOrPut(cellPx) { bake(cellPx) }

    @Synchronized
    fun brush(cellPx: Int): ShaderBrush = brushes.getOrPut(cellPx) {
        ShaderBrush(ImageShader(tile(cellPx), TileMode.Repeated, TileMode.Repeated))
    }

    /** The speck colour for a uniform random [v]: lighter or darker by how far it is from the middle. */
    internal fun speck(v: Float): Int {
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
