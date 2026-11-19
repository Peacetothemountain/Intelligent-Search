package com.pixel.intelligentsearch.feature.tabs.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Whether the pages' progressive blur draws (the approved blur mockup's "New"): from Android 13, which the gradient blur
 * needs, and only while the system blurs ([rememberSystemBlurEnabled]: off with Battery Saver, Reduce blur, on phones
 * that can't afford it). Otherwise every page, the floating pill and the popups look exactly as they did before it.
 */
@Composable
internal fun rememberProgressiveBlurOn(): Boolean {
    val system by rememberSystemBlurEnabled()
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && system
}

/**
 * The band of blur behind the floating pill, handed down by the shell while the blur is on (null otherwise): [height]
 * from the bottom of the screen to 24 dp above the pill, and how much of it shows ([strength], 0 to 1), which follows the
 * pill sliding away and back.
 */
internal class PillBlur(val height: Dp, val strength: () -> Float)

internal val LocalPillBlur = staticCompositionLocalOf<PillBlur?> { null }

/**
 * How far the blur reaches, as standard deviations in dp at eleven even steps from the band's screen edge (first) to
 * its sharp end (last): the mockup's stack of backdrop blurs, each a little stronger and starting a little further toward
 * the edge, worked out as one radius. About 26 dp at the top of the screen, about 24 dp at the bottom.
 */
private val TOP_SIGMA = floatArrayOf(26.1f, 24.9f, 20.1f, 13.8f, 8.8f, 5.4f, 3.1f, 1.8f, 0.9f, 0.4f, 0f)
private val BOTTOM_SIGMA = floatArrayOf(23.5f, 22.4f, 18.2f, 12.7f, 8.6f, 5.4f, 3.1f, 1.8f, 0.9f, 0.4f, 0f)

/** Below the top bar the colors are made up to 1.3 times as rich (CSS saturate) over this much of the band, strongest at the top. */
private const val TOP_SATURATION = 1.3f
private const val TOP_SATURATION_REACH = 0.34f

/** The blur under the top bar comes in over the first 40 dp of scroll, so the page's top is sharp at rest. */
internal val TOP_BLUR_RAMP = 40.dp

/** The blur's sharp end lies this far past the top bar or above the pill. */
internal val BLUR_SHARP_GAP = 24.dp

/**
 * The bands are blurred from a copy of the page at half its resolution: the blur hides the difference, and costs an
 * eighth as much (a quarter of the pixels, each reaching half as far).
 */
private const val BAND_DOWNSCALE = 2f

/**
 * Where a band's half-resolution blur takes over from the page itself, as fractions of the band from its screen edge:
 * fully from [BAND_FULL], fading to nothing by [BAND_SHARP], where the blur is under a dp and the page shows as it is.
 */
private const val BAND_FULL = 0.72f
private const val BAND_SHARP = 0.96f

/**
 * The page with its top [top] tall and its bottom [bottom] tall blurred progressively, more toward the screen's edge
 * (Compose's gradient blur, [BlurRadiusSpec]). [topStrength] and [bottom]'s strength say how much of each band shows;
 * at 0 the page draws as it is, and nothing is blurred.
 *
 * The page is recorded once a frame; only the bands are drawn blurred from that recording, each in a layer no bigger
 * than itself at half resolution, over the page, which is left out where a band shows in full. Put it outside the
 * page's background, so a window cleared through the page (Gallery's) stays clear in the bands.
 */
@Composable
internal fun Modifier.blurBands(top: Dp? = null, topStrength: () -> Float = { 1f }, bottom: PillBlur? = null): Modifier {
    if (top == null && bottom == null) return this
    val page = rememberGraphicsLayer()
    val topBand = rememberBand(TOP_SIGMA, fromTop = true)
    val bottomBand = rememberBand(BOTTOM_SIGMA, fromTop = false)
    val bottomHeight = bottom?.height
    return drawWithCache {
        val h = size.height
        val topH = top?.let { ceil(it.toPx()).coerceAtMost(h) } ?: 0f
        val bottomH = bottomHeight?.let { ceil(it.toPx()).coerceAtMost(h) } ?: 0f
        val topOn = topBand.size(size.width, topH, this)
        val bottomOn = bottomBand.size(size.width, bottomH, this)
        onDrawWithContent {
            val ts = if (topOn) topStrength().coerceIn(0f, 1f) else 0f
            val bs = if (bottomOn) (bottom?.strength?.invoke() ?: 0f).coerceIn(0f, 1f) else 0f
            if (ts <= 0f && bs <= 0f) {
                drawContent()
                return@onDrawWithContent
            }
            page.record { this@onDrawWithContent.drawContent() }
            clipRect(
                top = if (ts >= 1f) floor(topH * BAND_FULL) else 0f,
                bottom = if (bs >= 1f) h - floor(bottomH * BAND_FULL) else h
            ) { drawLayer(page) }
            if (ts > 0f) topBand.draw(this, page, 0f, ts)
            if (bs > 0f) bottomBand.draw(this, page, h - bottomH, bs)
        }
    }
}

/** One band: the page blurred at half resolution ([blurred]), faded out toward its sharp end in [faded]. */
private class Band(val sigma: FloatArray, val fromTop: Boolean, val blurred: GraphicsLayer, val faded: GraphicsLayer) {
    var full = IntSize.Zero
    var low = IntSize.Zero
    var fade: Brush? = null

    /** Sets the band up for a page [w] wide and a band [h] tall; false if there is no band. */
    fun size(w: Float, h: Float, density: Density): Boolean {
        full = IntSize(ceil(w).toInt(), h.toInt())
        if (full.width <= 0 || full.height <= 0) return false
        val k = BAND_DOWNSCALE
        low = IntSize(ceil(w / k).toInt().coerceAtLeast(1), ceil(h / k).toInt().coerceAtLeast(1))
        val lowDensity = Density(density.density / k, density.fontScale)
        val blur = bandEffect(sigma, fromTop, low, lowDensity)
        blurred.renderEffect = if (fromTop && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) saturateTop(blur, low.height * TOP_SATURATION_REACH) else blur
        faded.compositingStrategy = CompositingStrategy.Offscreen
        val edge = if (fromTop) 0f else h
        fun at(f: Float) = if (fromTop) f * h else h - f * h
        fade = Brush.verticalGradient(
            0f to Color.Black, BAND_FULL to Color.Black, (BAND_FULL + BAND_SHARP) / 2f to Color.Black.copy(alpha = 0.5f), BAND_SHARP to Color.Transparent,
            startY = edge, endY = at(1f)
        )
        return true
    }

    fun draw(scope: DrawScope, page: GraphicsLayer, top: Float, strength: Float) = with(scope) {
        val k = BAND_DOWNSCALE
        blurred.record(low) { scale(1f / k, 1f / k, pivot = Offset.Zero) { translate(top = -top) { drawLayer(page) } } }
        faded.alpha = strength
        val brush = fade ?: return@with
        faded.record(full) {
            scale(k, k, pivot = Offset.Zero) { drawLayer(blurred) }
            drawRect(brush, blendMode = BlendMode.DstIn)
        }
        translate(top = top) { drawLayer(faded) }
    }
}

@Composable
private fun rememberBand(sigma: FloatArray, fromTop: Boolean): Band {
    val blurred = rememberGraphicsLayer()
    val faded = rememberGraphicsLayer()
    return remember(blurred, faded) { Band(sigma, fromTop, blurred, faded) }
}

/** A band's gradient blur: [sigma] from the screen's edge to the sharp end, the top or the bottom of the band. */
private fun bandEffect(sigma: FloatArray, fromTop: Boolean, size: IntSize, density: Density): RenderEffect {
    val last = sigma.size - 1
    // Compose's gradient blur takes twice the standard deviation as its radius.
    val stops = sigma.indices.map { k ->
        val f = k.toFloat() / last
        BlurStop(if (fromTop) f else 1f - f, (sigma[k] * 2f).dp)
    }.sortedBy { it.fraction }
    return BlurRadiusSpec.verticalGradient(stops).createRenderEffect(Size(size.width.toFloat(), size.height.toFloat()), density, TileMode.Clamp)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun saturateTop(blur: RenderEffect, reach: Float): RenderEffect {
    val shader = RuntimeShader(SATURATE_TOP).apply {
        setFloatUniform("reach", reach.coerceAtLeast(1f))
        setFloatUniform("amount", TOP_SATURATION - 1f)
    }
    return android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"),
        blur.asAndroidRenderEffect()
    ).asComposeRenderEffect()
}

/** CSS saturate(), fading from [amount] more at the top to none [reach] down, on the same smooth ramp as the mockup. */
private const val SATURATE_TOP = """
uniform shader content;
uniform float reach;
uniform float amount;
half4 main(float2 p) {
    half4 c = content.eval(p);
    float t = clamp(1.0 - p.y / reach, 0.0, 1.0);
    t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    half l = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
    c.rgb = clamp(mix(half3(l), c.rgb, half(1.0 + amount * t)), half3(0.0), half3(c.a));
    return c;
}
"""

/**
 * The tint the top bar keeps over the blur for contrast, in place of its solid background: the page's own color, 64%
 * (62% light) at the top of the screen, fading to nothing [height] down, the blur's sharp end. [shown] says how much of it is in;
 * with [slide] it comes down from above the screen as it does (Gallery's, with its small title), otherwise it fades.
 */
@Composable
internal fun Modifier.topBarTint(height: Dp, shown: () -> Float, slide: Boolean = false): Modifier {
    val scheme = MaterialTheme.colorScheme
    val strongest = if (scheme.isDark) 0.64f else 0.62f
    val color = scheme.background
    return drawWithCache {
        val h = height.toPx()
        val brush = Brush.verticalGradient(
            0f to color.copy(alpha = strongest),
            0.34f to color.copy(alpha = strongest * 0.86f),
            0.62f to color.copy(alpha = strongest * 0.55f),
            0.85f to color.copy(alpha = strongest * 0.18f),
            1f to color.copy(alpha = 0f),
            endY = h
        )
        onDrawWithContent {
            drawContent()
            val s = shown().coerceIn(0f, 1f)
            if (s <= 0f) return@onDrawWithContent
            val area = Size(size.width, h)
            if (slide) translate(top = -(1f - s) * h * 1.1f) { drawRect(brush, size = area) }
            else drawRect(brush, size = area, alpha = s)
        }
    }
}

/**
 * Behind a popup: everything under it blurred ([sigma], a standard deviation) as it comes in ([progress], 0 to 1).
 * Nothing while it is away.
 */
internal fun popupBlurEffect(sigma: Dp, progress: Float, density: Density): RenderEffect? {
    if (progress <= 0.004f) return null
    val s = with(density) { sigma.toPx() } * progress.coerceAtMost(1f)
    // Android's blur radius for that standard deviation (Skia's sigma = 0.57735 x radius + 0.5).
    val radius = ((s - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
    return BlurEffect(radius, radius, TileMode.Clamp)
}
