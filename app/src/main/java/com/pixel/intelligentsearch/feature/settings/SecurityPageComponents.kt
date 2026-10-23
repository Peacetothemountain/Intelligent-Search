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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Test tag on the banner's secure chip. */
internal const val SECURE_CHIP_TAG = "secure_chip"

/**
 * The banner's emblem: the secure chip the keys live in, drawn on a 24-unit grid like a Material icon. A rounded
 * chip with three pins on each side and a small shield at its heart (with a check once the key is hardware
 * [verified]). It is quietly alive: light travels in along the pins, one pin after the next around the chip, as data
 * going into the vault, and the chip's edge and heart glow softly as each arrives. While attestation is still
 * [verifying] the light comes in quickly and never rests; once the answer is in it slows to a calm round with a pause.
 * With [animate] off it is drawn still. The paths are built once per size; a frame only places the lights.
 */
@Composable
internal fun SecureChipEmblem(verified: Boolean, verifying: Boolean, animate: Boolean, color: Color, modifier: Modifier = Modifier) {
    val phase: State<Float>? = if (animate) {
        val millis = if (verifying) CHIP_ROUND_VERIFYING_MILLIS else CHIP_ROUND_RESTING_MILLIS
        rememberInfiniteTransition(label = "secureChip").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing)),
            label = "secureChipRound"
        )
    } else {
        null
    }
    val travel = if (verifying) 1f else CHIP_LIGHT_TRAVEL
    Spacer(modifier = modifier.drawWithCache {
        val s = size.minDimension / 24f
        val ox = (size.width - 24f * s) / 2f
        val oy = (size.height - 24f * s) / 2f
        fun x(v: Float) = ox + v * s
        fun y(v: Float) = oy + v * s
        val body = androidx.compose.ui.geometry.RoundRect(x(6.5f), y(6.5f), x(17.5f), y(17.5f), CornerRadius(2.6f * s))
        val bodyPath = Path().apply { addRoundRect(body) }
        val shield = Path().apply {
            moveTo(x(12f), y(8.6f))
            lineTo(x(15f), y(9.8f))
            lineTo(x(15f), y(12f))
            cubicTo(x(15f), y(13.9f), x(13.7f), y(15.3f), x(12f), y(15.9f))
            cubicTo(x(10.3f), y(15.3f), x(9f), y(13.9f), x(9f), y(12f))
            lineTo(x(9f), y(9.8f))
            close()
        }
        val check = Path().apply {
            moveTo(x(10.6f), y(12.2f))
            lineTo(x(11.6f), y(13.2f))
            lineTo(x(13.5f), y(11.2f))
        }
        // Each pin from its outer end to the chip's edge, going round clockwise from the top left.
        val pins = buildList {
            for (v in listOf(9f, 12f, 15f)) add(Offset(x(v), y(2.6f)) to Offset(x(v), y(6.5f)))
            for (v in listOf(9f, 12f, 15f)) add(Offset(x(21.4f), y(v)) to Offset(x(17.5f), y(v)))
            for (v in listOf(15f, 12f, 9f)) add(Offset(x(v), y(21.4f)) to Offset(x(v), y(17.5f)))
            for (v in listOf(15f, 12f, 9f)) add(Offset(x(2.6f), y(v)) to Offset(x(6.5f), y(v)))
        }
        val stroke = Stroke(width = 1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val thin = 1.4f * s
        val fill = color.copy(alpha = 0.14f)
        val glow = Brush.radialGradient(
            listOf(color.copy(alpha = 0.34f), color.copy(alpha = 0f)),
            center = Offset(x(12f), y(12f)),
            radius = 9f * s
        )
        val lightRadius = 1.25f * s
        onDrawBehind {
            val p = phase?.value
            // How strongly the chip is lit now: by the light that has most recently arrived.
            var arrived = 0f
            if (p != null) {
                for (i in pins.indices) arrived = maxOf(arrived, chipArrival(p, i, pins.size, travel))
            }
            if (arrived > 0f) drawCircle(glow, radius = 9f * s, center = Offset(x(12f), y(12f)), alpha = arrived)
            for ((outer, inner) in pins) drawLine(color.copy(alpha = 0.55f), outer, inner, strokeWidth = thin, cap = StrokeCap.Round)
            drawPath(bodyPath, fill)
            drawPath(bodyPath, color, style = stroke)
            drawPath(shield, color.copy(alpha = 0.22f + 0.25f * arrived))
            drawPath(shield, color, style = Stroke(width = 1.3f * s, join = StrokeJoin.Round))
            if (verified) drawPath(check, color, style = Stroke(width = 1.3f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (p != null) {
                for (i in pins.indices) {
                    val along = chipLightPosition(p, i, pins.size, travel) ?: continue
                    val (outer, inner) = pins[i]
                    val at = Offset(outer.x + (inner.x - outer.x) * along, outer.y + (inner.y - outer.y) * along)
                    // Fades in at the pin's end and out as it goes into the chip.
                    val alpha = kotlin.math.sin(kotlin.math.PI.toFloat() * along)
                    drawCircle(color, radius = lightRadius, center = at, alpha = alpha)
                }
            }
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
