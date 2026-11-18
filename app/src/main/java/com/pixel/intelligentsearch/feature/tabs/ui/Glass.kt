package com.pixel.intelligentsearch.feature.tabs.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * Whether the system blurs right now, as Intelligent Search's CrossWindowBlur.kt reads it: the one check every piece of
 * glass in the preview follows. It turns off with Battery Saver, Reduce blur (Accessibility > Color and motion), on
 * phones that can't afford it, and back on later, so this follows it as it changes. While it is off, glass is a solid
 * tonal container, as Android's own surfaces are then.
 *
 * Screenshot tests set the system property `intelligentsearch.test.systemBlur` (Robolectric can't blur behind windows); it is
 * never set on a phone.
 */
@Composable
internal fun rememberSystemBlurEnabled(): State<Boolean> {
    val context = LocalContext.current
    val windowManager = remember(context) { context.getSystemService(WindowManager::class.java) }
    val inTests = System.getProperty("intelligentsearch.test.systemBlur")?.toBooleanStrictOrNull()
    return produceState(initialValue = inTests ?: windowManager?.isCrossWindowBlurEnabled ?: false, windowManager, inTests) {
        if (windowManager == null || inTests != null) return@produceState
        val listener = Consumer<Boolean> { enabled -> value = enabled }
        // Called once at once with the current state, then on every change.
        runCatching { windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener) }
        awaitDispose { runCatching { windowManager.removeCrossWindowBlurEnabledListener(listener) } }
    }
}

/**
 * What a piece of glass looks through: the app-drawn scene under it, [size] across, drawn by [draw] at the scene's own
 * origin (usually a GraphicsLayer the page records once a frame).
 */
internal class GlassBackdrop(val size: () -> Size, val draw: DrawScope.() -> Unit)

/**
 * How much the frosted glass blurs what passes under it: a Gaussian of this standard deviation, as the mockup's CSS
 * blur of 28px, with the colors made 1.3 times as rich (CSS saturate of 1.3).
 */
private val GLASS_BLUR_SIGMA = 28.dp
private const val GLASS_SATURATION = 1.3f

/**
 * The glass is blurred from a copy of the scene at a quarter of its resolution: a 28 dp blur leaves nothing finer than
 * that to see, and the blur then costs a sixteenth as much.
 */
private const val GLASS_DOWNSCALE = 4f

/**
 * Android 18's frosted glass (the Canary SDK's frosted_glass tokens), the one glass every tab draws on floating chrome
 * over app-drawn content (the pill, Wellbeing's recap bars, Gallery's stuck filter row): the scene passing under it
 * ([backdrop], of which this element's top left is at [positionInBackdrop]), blurred and made richer, under a neutral
 * scrim (L*12 at 48% dark, L*94 at 36% light) and a 1 dp rim that is bright (60%) at both ends and nearly clear (10%)
 * in the middle. Only the strip of the scene around the element is blurred, at a quarter of its resolution.
 *
 * Without [frosted] (the system isn't blurring, or the glass sits over the real wallpaper, which can't be frosted) it is
 * the solid [solid] container.
 */
@Composable
internal fun Modifier.frostedGlass(
    shape: Shape,
    frosted: Boolean,
    backdrop: GlassBackdrop?,
    positionInBackdrop: () -> Offset,
    solid: Color = MaterialTheme.colorScheme.surfaceContainerHigh
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    val density = LocalDensity.current
    val band = rememberGraphicsLayer()
    val sigmaPx = with(density) { GLASS_BLUR_SIGMA.toPx() }
    val blur = remember(sigmaPx) {
        val sigma = sigmaPx / GLASS_DOWNSCALE
        val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0f)
        val saturate = ColorMatrix().apply { setSaturation(GLASS_SATURATION) }
        RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(saturate),
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        ).asComposeRenderEffect()
    }
    // Neutral1 at the token's tone: surfaceContainer is N12 dark and N94 light; onSurface N90 dark, surface N98 light.
    val scrim = scheme.surfaceContainer.copy(alpha = if (dark) 0.48f else 0.36f)
    val rimTone = if (dark) scheme.onSurface else scheme.surface
    val rimEnd = rimTone.copy(alpha = 0.60f)
    val rimMiddle = rimTone.copy(alpha = 0.10f)
    // The shape, its rim and the rim's gradient are worked out once per size; only the strip under it is recorded
    // again as the element or the scene moves.
    return drawWithCache {
        val w = size.width
        val h = size.height
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        // The rim: a CSS linear-gradient at 100deg, bright at both ends and nearly clear in the middle.
        val angle = Math.toRadians(100.0)
        val dx = kotlin.math.sin(angle).toFloat()
        val dy = -kotlin.math.cos(angle).toFloat()
        val half = (abs(w * dx) + abs(h * dy)) / 2f
        val c = Offset(w / 2f, h / 2f)
        val rim = Brush.linearGradient(
            0f to rimEnd, 0.3f to rimMiddle, 0.7f to rimMiddle, 1f to rimEnd,
            start = c - Offset(dx, dy) * half, end = c + Offset(dx, dy) * half
        )
        val stroke = 1.dp.toPx()
        val rimStroke = Stroke(stroke)
        val rimPath = if (outline is Outline.Rounded) {
            val r = (outline.roundRect.topLeftCornerRadius.x - stroke / 2f).coerceAtLeast(0f)
            Path().apply { addRoundRect(RoundRect(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f, CornerRadius(r))) }
        } else path
        onDrawBehind {
            if (!frosted || backdrop == null) {
                drawPath(path, solid)
                return@onDrawBehind
            }
            val at = positionInBackdrop()
            val scene = backdrop.size()
            // Nothing of it is over the scene (the pill slid away): nothing to frost, and nothing is drawn, so the
            // strip isn't recorded and blurred again for every frame the page scrolls under it.
            if (at.x >= scene.width || at.y >= scene.height || at.x + w <= 0f || at.y + h <= 0f) return@onDrawBehind
            // The strip blurred: the element and the blur's reach around it, kept inside the scene, so the blur clamps
            // at the scene's edges, and always the same size while the element moves (a pill sliding away): a strip
            // that changed size every frame of the slide made Android set aside a new offscreen surface each frame,
            // hundreds of them, and the new ones cost frames.
            val margin = sigmaPx * 3f
            val stripW = min(scene.width, w + 2f * margin)
            val stripH = min(scene.height, h + 2f * margin)
            val left = (at.x - margin).coerceIn(0f, scene.width - stripW)
            val top = (at.y - margin).coerceIn(0f, scene.height - stripH)
            if (stripW > 0f && stripH > 0f) {
                val k = GLASS_DOWNSCALE
                band.renderEffect = blur
                val bandSize = IntSize(ceil(stripW / k).toInt().coerceAtLeast(1), ceil(stripH / k).toInt().coerceAtLeast(1))
                band.record(bandSize) {
                    scale(1f / k, 1f / k, pivot = Offset.Zero) { translate(-left, -top) { backdrop.draw(this) } }
                }
                clipPath(path) {
                    translate(left - at.x, top - at.y) { scale(k, k, pivot = Offset.Zero) { drawLayer(band) } }
                }
            }
            drawPath(path, scrim)
            drawPath(rimPath, rim, style = rimStroke)
        }
    }
}
