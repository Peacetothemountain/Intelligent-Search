package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath

/**
 * The banner's shield, drawn on a 24-unit grid like a Material icon: its outline over a faint fill, with a check mark
 * inside once the key is hardware [verified]. Still: the page's motion lives in the check indicator and the chain.
 */
@Composable
internal fun SecurityShield(verified: Boolean, color: Color, modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.drawWithCache {
        val s = size.minDimension / 24f
        val ox = (size.width - 24f * s) / 2f
        val oy = (size.height - 24f * s) / 2f
        fun x(v: Float) = ox + v * s
        fun y(v: Float) = oy + v * s
        val shield = Path().apply {
            moveTo(x(12f), y(2.2f))
            lineTo(x(19.8f), y(5.4f))
            lineTo(x(19.8f), y(11f))
            cubicTo(x(19.8f), y(16f), x(16.5f), y(20.4f), x(12f), y(21.8f))
            cubicTo(x(7.5f), y(20.4f), x(4.2f), y(16f), x(4.2f), y(11f))
            lineTo(x(4.2f), y(5.4f))
            close()
        }
        val check = Path().apply {
            moveTo(x(8.4f), y(12.1f))
            lineTo(x(10.9f), y(14.6f))
            lineTo(x(15.8f), y(9.6f))
        }
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val fill = color.copy(alpha = 0.14f)
        onDrawBehind {
            drawPath(shield, fill)
            drawPath(shield, color, style = stroke)
            if (verified) drawPath(check, color, style = stroke)
        }
    })
}

/** One morph of the check indicator, then the next, in milliseconds. */
private const val CHECK_MORPH_MILLIS = 650

/** The shapes the check indicator morphs through, MaterialShapes' as Google's loading indicator uses them. */
private val CheckShapes = listOf("SoftBurst", "Cookie9Sided", "Pentagon", "Pill", "Sunny", "Cookie4Sided", "Oval")

/**
 * Shown in the banner while attestation is really running: a Material shape that morphs from one MaterialShapes
 * shape into the next as it turns, as Android's loading indicator does, so a re-check visibly takes place. With
 * [animate] off it is the first shape, still.
 */
@Composable
internal fun SecurityCheckIndicator(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val morphs = remember {
        val shapes = CheckShapes.map { MaterialMorphShapes.byName.getValue(it) }
        shapes.indices.map { i -> Morph(shapes[i], shapes[(i + 1) % shapes.size]) }
    }
    val phase: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "checkIndicator").animateFloat(
            initialValue = 0f,
            targetValue = morphs.size.toFloat(),
            animationSpec = infiniteRepeatable(tween(CHECK_MORPH_MILLIS * morphs.size, easing = LinearEasing)),
            label = "checkIndicatorPhase"
        )
    } else {
        null
    }
    Spacer(modifier = modifier.drawWithCache {
        val platformPath = android.graphics.Path()
        val matrix = android.graphics.Matrix()
        val box = size.minDimension * 0.86f
        onDrawBehind {
            val t = phase?.value ?: 0f
            val index = t.toInt().coerceIn(0, morphs.lastIndex)
            // Each morph eases in and out, so the shape rests a moment on each MaterialShape.
            val f = t - t.toInt()
            val eased = f * f * (3f - 2f * f)
            platformPath.rewind()
            morphs[index].toPath(eased, platformPath)
            matrix.setScale(box, box)
            matrix.postTranslate((size.width - box) / 2f, (size.height - box) / 2f)
            platformPath.transform(matrix)
            rotate(degrees = t * 140f) { drawPath(platformPath.asComposePath(), color) }
        }
    })
}

private val LinkWidth = 14.dp
private val LinkHeight = 9.dp
private val LinkStep = 10.dp
private val KeyGap = 4.dp
private val KeyWidth = 13.dp

/** How far a link sways up and down at most. */
private val SwayHeight = 1.2.dp

/** The most the chain grows with the text size. */
private const val MAX_CHAIN_GROW = 1.6f

/** How much a link swells while the pulse passes it. */
private const val PULSE_SWELL = 0.14f

/**
 * The certificate chain as [count] interlinked rounded links, the leaf first, ending in a small key for the root
 * when it is rooted in Google's hardware root ([rooted]). It is always gently alive: the links sway a little, one
 * after another, as a hanging chain does, and a soft pulse runs down it from the app's key to the root: quickly and
 * in the outline colour while attestation is [verifying], slowly in primary over primary-filled links once the chain
 * has verified. With [animate] off it is drawn still. It grows a little with large text, and where the row is
 * narrower than the chain the whole drawing scales down to fit.
 * Purely visual: the row it sits in carries the description.
 */
@Composable
internal fun CertificateChainLinks(
    count: Int,
    rooted: Boolean,
    verifying: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outline
    val primary = scheme.primary
    val locked = rooted && !verifying
    val pulseColor = if (locked) primary else scheme.onSurfaceVariant
    val (pulse, sway) = if (animate) {
        val transition = rememberInfiniteTransition(label = "chain")
        val pulseMillis = if (verifying) CHAIN_PULSE_VERIFYING_MILLIS else CHAIN_PULSE_RESTING_MILLIS
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(pulseMillis, easing = LinearEasing)),
            label = "chainPulse"
        ) to transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(CHAIN_SWAY_MILLIS, easing = LinearEasing)),
            label = "chainSway"
        )
    } else {
        null to null
    }
    val natural = LinkWidth + LinkStep * (count - 1) + if (rooted) KeyGap + KeyWidth else 0.dp
    // Grows a little with larger text so it stays in proportion to the line beside it.
    val grow = LocalDensity.current.fontScale.coerceIn(1f, MAX_CHAIN_GROW)
    Spacer(modifier = modifier.width(natural * grow).height((LinkHeight + 4.dp + SwayHeight * 2) * grow).drawWithCache {
        val naturalPx = natural.toPx()
        val fit = if (naturalPx > 0f) minOf(grow, size.width / naturalPx) else grow
        val cy = size.height / 2f
        val linkW = LinkWidth.toPx()
        val linkH = LinkHeight.toPx()
        val step = LinkStep.toPx()
        val swayPx = SwayHeight.toPx()
        val stroke = Stroke(width = 1.5.dp.toPx())
        val keyStroke = Stroke(width = 1.4.dp.toPx())
        val keyX = linkW + step * (count - 1) + KeyGap.toPx()
        val keyRing = 2.6.dp.toPx()
        val keyW = KeyWidth.toPx()
        val tooth = 2.4.dp.toPx()
        val steps = count + if (rooted) 1 else 0
        // While verifying the pulse never rests; afterwards it rests between trips.
        val travel = if (verifying) 1f else CHAIN_PULSE_TRAVEL
        onDrawBehind {
            val p = pulse?.value ?: 0f
            val w = sway?.value
            scale(fit, fit, pivot = Offset(0f, cy)) {
                for (k in 0 until count) {
                    val lit = if (pulse == null) 0f else chainPulse(p, k, steps, travel)
                    val dy = if (w == null) 0f else chainSway(w, k) * swayPx
                    val left = k * step
                    // Alternate links are seen edge-on, as in a real chain.
                    val h = if (k % 2 == 0) linkH else linkH * 0.62f
                    val swell = 1f + PULSE_SWELL * lit
                    translate(top = dy) {
                        scale(swell, swell, pivot = Offset(left + linkW / 2f, cy)) {
                            val topLeft = Offset(left, cy - h / 2f)
                            val linkSize = Size(linkW, h)
                            val corner = CornerRadius(h / 2f)
                            if (locked) drawRoundRect(primary, topLeft, linkSize, corner, alpha = 0.22f)
                            if (lit > 0f) drawRoundRect(pulseColor, topLeft, linkSize, corner, alpha = 0.28f * lit)
                            val edge = if (locked) primary else lerp(outline, pulseColor, lit)
                            drawRoundRect(edge, topLeft, linkSize, corner, style = stroke)
                        }
                    }
                }
                if (rooted) {
                    val lit = if (pulse == null) 0f else chainPulse(p, count, steps, travel)
                    val dy = if (w == null) 0f else chainSway(w, count) * swayPx
                    val c = if (locked) primary else lerp(outline, pulseColor, lit)
                    val swell = 1f + PULSE_SWELL * lit
                    translate(top = dy) {
                        scale(swell, swell, pivot = Offset(keyX + keyW / 2f, cy)) {
                            val ring = Offset(keyX + keyRing, cy)
                            if (locked || lit > 0f) {
                                drawCircle(primary, radius = keyRing, center = ring, alpha = if (locked) 0.22f + 0.2f * lit else 0.28f * lit)
                            }
                            drawCircle(c, radius = keyRing, center = ring, style = keyStroke)
                            val shaftStart = keyX + keyRing * 2f
                            val shaftEnd = keyX + keyW
                            val sw = keyStroke.width
                            drawLine(c, Offset(shaftStart, cy), Offset(shaftEnd, cy), strokeWidth = sw, cap = StrokeCap.Round)
                            drawLine(c, Offset(shaftEnd, cy), Offset(shaftEnd, cy + tooth), strokeWidth = sw, cap = StrokeCap.Round)
                            val inner = shaftEnd - tooth
                            drawLine(c, Offset(inner, cy), Offset(inner, cy + tooth * 0.7f), strokeWidth = sw, cap = StrokeCap.Round)
                        }
                    }
                }
            }
        }
    })
}

/**
 * Draws the posture chip's "label · NN%" counting up from 0 to [target] as [progress] (0 to 1) runs, over the chip's
 * finished text, which keeps the chip its final size throughout. The prefix, each digit and the percent sign are
 * measured once, so a frame only places a few of them. Draws the finished text itself once the count has landed, or
 * if the finished text wouldn't fit on one line.
 */
internal fun Modifier.countUpText(
    prefix: String,
    target: Int,
    progress: () -> Float,
    style: TextStyle,
    measurer: TextMeasurer
): Modifier = drawWithCache {
    val head = measurer.measure(prefix, style, softWrap = false, maxLines = 1, density = this, layoutDirection = layoutDirection)
    val digits = Array(10) {
        measurer.measure("$it", style, softWrap = false, maxLines = 1, density = this, layoutDirection = layoutDirection)
    }
    val percent = measurer.measure("%", style, softWrap = false, maxLines = 1, density = this, layoutDirection = layoutDirection)
    val whole = measurer.measure("$prefix$target%", style, softWrap = false, maxLines = 1, density = this, layoutDirection = layoutDirection)
    val fits = whole.size.width <= size.width + 1f
    onDrawWithContent {
        val p = progress()
        if (p >= 1f || !fits) {
            drawContent()
            return@onDrawWithContent
        }
        val value = countUpValue(p, target)
        drawText(head, topLeft = Offset.Zero)
        var x = head.size.width.toFloat()
        var divisor = 1
        while (divisor * 10 <= value) divisor *= 10
        while (divisor > 0) {
            val d = digits[(value / divisor) % 10]
            drawText(d, topLeft = Offset(x, 0f))
            x += d.size.width
            divisor /= 10
        }
        drawText(percent, topLeft = Offset(x, 0f))
    }
}
