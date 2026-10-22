package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.sin

/**
 * Fades [content] in and lifts it [rise] into place over the scan stage from [startMs] lasting [durationMs], reading
 * the [scan] clock only in its layer, so the entrance redraws the layer without recomposing.
 */
internal fun Modifier.scanReveal(scan: () -> Float, startMs: Float, durationMs: Float, rise: Dp = 6.dp): Modifier =
    graphicsLayer {
        val p = FastOutSlowInEasing.transform(scanStage(scan(), startMs, durationMs))
        alpha = p
        translationY = (1f - p) * rise.toPx()
    }

/**
 * One soft band of [light] passing once across the element, slanted a little, over the scan stage from
 * [SCAN_SHEEN_START]; drawn over the content and nothing at all before or after it.
 */
internal fun Modifier.scanSheen(scan: () -> Float, light: Color): Modifier = drawWithCache {
    val band = maxOf(size.height * 1.4f, 48.dp.toPx())
    val brush = Brush.horizontalGradient(
        0f to light.copy(alpha = 0f),
        0.5f to light,
        1f to light.copy(alpha = 0f),
        startX = 0f,
        endX = band
    )
    val travel = size.width + band * 2f
    onDrawWithContent {
        drawContent()
        val p = scanStage(scan(), SCAN_SHEEN_START, SCAN_SHEEN_MILLIS)
        if (p <= 0f || p >= 1f) return@onDrawWithContent
        val x = -band + travel * FastOutSlowInEasing.transform(p)
        // Fades in and out at the ends of its pass, so it never starts or stops abruptly.
        val fade = sin(PI.toFloat() * p)
        translate(left = x) {
            rotate(degrees = 18f, pivot = Offset(band / 2f, size.height / 2f)) {
                drawRect(brush, topLeft = Offset(0f, -size.height), size = Size(band, size.height * 3f), alpha = fade)
            }
        }
    }
}

/**
 * The banner's shield, drawn rather than an icon so it can trace itself: its outline strokes round from the top, then
 * a check mark strokes in inside it as a faint fill comes up, both on the [scan] clock. Afterwards, with animations
 * on, it glows softly about every [SHIELD_GLOW_PERIOD_MILLIS]. With animations off it is drawn finished and still.
 * The paths and their measures are built once per size; a frame only cuts segments into two reused paths.
 */
@Composable
internal fun ScanShield(scan: () -> Float, animate: Boolean, color: Color, modifier: Modifier = Modifier) {
    val glowPhase: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "shieldGlow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SHIELD_GLOW_PERIOD_MILLIS, easing = LinearEasing)),
            label = "shieldGlowPhase"
        )
    } else {
        null
    }
    Spacer(modifier = modifier.drawWithCache {
        // Drawn on a 24-unit grid, like a Material icon.
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
        val shieldMeasure = PathMeasure().apply { setPath(shield, false) }
        val checkMeasure = PathMeasure().apply { setPath(check, false) }
        val shieldLength = shieldMeasure.length
        val checkLength = checkMeasure.length
        val shieldPart = Path()
        val checkPart = Path()
        val stroke = Stroke(width = 1.9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val fill = color.copy(alpha = 0.14f)
        val glowRadius = size.minDimension * 0.75f
        val glow = Brush.radialGradient(
            listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = glowRadius
        )
        onDrawBehind {
            val t = scan()
            val lit = if (glowPhase == null || t < SCAN_END_MILLIS) 0f else shieldGlow(glowPhase.value)
            if (lit > 0f) drawCircle(glow, radius = glowRadius, center = center, alpha = lit)

            val trace = FastOutSlowInEasing.transform(scanStage(t, SCAN_SHIELD_START, SCAN_SHIELD_MILLIS))
            val tick = FastOutSlowInEasing.transform(scanStage(t, SCAN_CHECK_START, SCAN_CHECK_MILLIS))
            if (tick > 0f) drawPath(shield, fill, alpha = tick)
            if (trace >= 1f) {
                drawPath(shield, color, style = stroke)
            } else if (trace > 0f) {
                shieldPart.reset()
                shieldMeasure.getSegment(0f, shieldLength * trace, shieldPart, true)
                drawPath(shieldPart, color, style = stroke)
            }
            if (tick >= 1f) {
                drawPath(check, color, style = stroke)
            } else if (tick > 0f) {
                checkPart.reset()
                checkMeasure.getSegment(0f, checkLength * tick, checkPart, true)
                drawPath(checkPart, color, style = stroke)
            }
        }
    })
}

private const val CHECK_ID = "scanCheck"

/** Each scrambled frame of a line, cut along the finished text's own lines so it draws exactly where the text sits. */
private class DecryptFrames(val x: FloatArray, val y: FloatArray, val lines: Array<Array<TextLayoutResult?>>)

/**
 * [text] that resolves from random glyphs to itself as [progress] (its entry, 0 to 1) runs, and again for just the
 * part that changed when [text] changes after it has appeared (e.g. "Verifying..." to "Hardware Verified"). The text
 * is laid out once, finished, so nothing moves or wraps differently while it resolves: the scrambled frames are
 * measured line by line in the theme's own font and drawn over the finished text's lines, then the real text is
 * drawn. [trailingCheck], when given, sits after the text in a fixed slot and is drawn once the text has resolved.
 *
 * The scrambled frames ([DECRYPT_FRAMES] of them) are measured once, the first time they are needed; a frame only
 * picks one and draws it. With [animate] off this is plain [Text].
 */
@Composable
internal fun DecryptText(
    text: String,
    style: TextStyle,
    color: Color,
    progress: () -> Float,
    animate: Boolean,
    seed: Int,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    trailingCheck: (@Composable () -> Unit)? = null
) {
    val hasCheck = trailingCheck != null
    val annotated = remember(text, hasCheck) {
        buildAnnotatedString {
            append(text)
            if (hasCheck) {
                append(' ')
                appendInlineContent(CHECK_ID, "✓")
            }
        }
    }
    val check by rememberUpdatedState(trailingCheck)
    val inline = remember(hasCheck) {
        if (!hasCheck) emptyMap() else mapOf(
            CHECK_ID to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                // The check's own line is taller than the 1 em slot; let it centre on the slot rather than squeeze in.
                Box(modifier = Modifier.fillMaxSize().wrapContentSize(unbounded = true), contentAlignment = Alignment.Center) {
                    check?.invoke()
                }
            }
        )
    }
    if (!animate) {
        Text(text = annotated, modifier = modifier, color = color, fontWeight = fontWeight, style = style, inlineContent = inline)
        return
    }

    val measurer = rememberTextMeasurer(cacheSize = 0)
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val change = remember { Animatable(1f) }
    val keep = remember { mutableIntStateOf(0) }
    val shown = remember { arrayOf(text) }
    val currentProgress by rememberUpdatedState(progress)
    LaunchedEffect(text) {
        val before = shown[0]
        shown[0] = text
        // Before the line has appeared its entry resolves whatever it says by then.
        if (before == text || currentProgress() < 1f) return@LaunchedEffect
        keep.intValue = before.commonPrefixWith(text).length
        change.snapTo(0f)
        change.animateTo(1f, tween(DECRYPT_MILLIS, easing = LinearEasing))
    }
    val drawStyle = style.copy(color = color, fontWeight = fontWeight ?: style.fontWeight)
    Text(
        text = annotated,
        color = color,
        fontWeight = fontWeight,
        style = style,
        inlineContent = inline,
        onTextLayout = { layout.value = it },
        modifier = modifier.drawWithCache {
            val finished = layout.value
            val kept = keep.intValue
            var entryFrames: DecryptFrames? = null
            var changeFrames: DecryptFrames? = null
            onDrawWithContent {
                if (finished == null) {
                    drawContent()
                    return@onDrawWithContent
                }
                val entry = progress()
                val changed = change.value
                val frames: DecryptFrames
                val p: Float
                if (entry < 1f) {
                    frames = entryFrames ?: buildDecryptFrames(finished, hasCheck, 0, seed, measurer, drawStyle, this, layoutDirection)
                        .also { entryFrames = it }
                    p = entry
                } else if (changed < 1f) {
                    frames = changeFrames ?: buildDecryptFrames(finished, hasCheck, kept, seed, measurer, drawStyle, this, layoutDirection)
                        .also { changeFrames = it }
                    p = changed
                } else {
                    drawContent()
                    return@onDrawWithContent
                }
                val frame = frames.lines[(p * DECRYPT_FRAMES).toInt().coerceIn(0, DECRYPT_FRAMES - 1)]
                clipRect {
                    for (i in frame.indices) {
                        val line = frame[i] ?: continue
                        drawText(line, topLeft = Offset(frames.x[i], frames.y[i]))
                    }
                }
            }
        }
    )
}

/** Measures every scrambled frame of [finished]'s text, one layout per line per frame, at the finished line places. */
private fun buildDecryptFrames(
    finished: TextLayoutResult,
    hasCheck: Boolean,
    keep: Int,
    seed: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    density: Density,
    layoutDirection: LayoutDirection
): DecryptFrames {
    val full = finished.layoutInput.text.text
    // The check's slot is the last character; it isn't scrambled, just left out until the text has resolved.
    val end = if (hasCheck) (full.length - 1).coerceAtLeast(0) else full.length
    val plain = full.substring(0, end)
    val lineCount = finished.lineCount
    val x = FloatArray(lineCount) { finished.getLineLeft(it) }
    val y = FloatArray(lineCount) { finished.getLineTop(it) }
    val lines = Array(DECRYPT_FRAMES) { frame ->
        val scrambled = decryptFrame(plain, frame.toFloat() / DECRYPT_FRAMES, seed, frame, keep)
        Array(lineCount) { line ->
            val start = finished.getLineStart(line).coerceAtMost(end)
            val stop = finished.getLineEnd(line, visibleEnd = true).coerceAtMost(end)
            if (stop <= start) {
                null
            } else {
                measurer.measure(
                    scrambled.substring(start, stop), style, softWrap = false, maxLines = 1,
                    density = density, layoutDirection = layoutDirection
                )
            }
        }
    }
    return DecryptFrames(x, y, lines)
}

private val LinkWidth = 14.dp
private val LinkHeight = 9.dp
private val LinkStep = 10.dp
private val KeyGap = 4.dp
private val KeyWidth = 13.dp

/** The most the chain grows with the text size. */
private const val MAX_CHAIN_GROW = 1.6f

/** How much a link swells for the instant it locks. */
private const val LOCK_POP = 0.18f

/**
 * The certificate chain as [count] interlinked rounded links, the leaf first. Each draws in on the [scan] clock in
 * turn, starting outlined; as verification runs down the chain ([lock], 0 to 1) each link in turn locks with a brief
 * swell, its outline turning primary over a soft primary fill. A hardware-rooted chain ([rooted]) ends with a small
 * key for the root, which locks last. It grows a little with large text, and where the row is narrower than the chain
 * the whole drawing scales down to fit.
 * Purely visual: the row it sits in carries the description.
 */
@Composable
internal fun CertificateChainLinks(
    count: Int,
    rooted: Boolean,
    scan: () -> Float,
    lock: () -> Float,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outline
    val primary = scheme.primary
    val natural = LinkWidth + LinkStep * (count - 1) + if (rooted) KeyGap + KeyWidth else 0.dp
    // Grows a little with larger text so it stays in proportion to the line beside it.
    val grow = LocalDensity.current.fontScale.coerceIn(1f, MAX_CHAIN_GROW)
    Spacer(modifier = modifier.width(natural * grow).height((LinkHeight + 4.dp) * grow).drawWithCache {
        val naturalPx = natural.toPx()
        val fit = if (naturalPx > 0f) minOf(grow, size.width / naturalPx) else grow
        val cy = size.height / 2f
        val linkW = LinkWidth.toPx()
        val linkH = LinkHeight.toPx()
        val step = LinkStep.toPx()
        val stroke = Stroke(width = 1.5.dp.toPx())
        val keyStroke = Stroke(width = 1.4.dp.toPx())
        val keyX = linkW + step * (count - 1) + KeyGap.toPx()
        val keyRing = 2.6.dp.toPx()
        val keyW = KeyWidth.toPx()
        val tooth = 2.4.dp.toPx()
        val steps = count + if (rooted) 1 else 0
        onDrawBehind {
            val t = scan()
            val l = lock()
            scale(fit, fit, pivot = Offset(0f, cy)) {
                for (k in 0 until count) {
                    val appear = FastOutSlowInEasing.transform(scanStage(t, chainLinkStart(k, count), SCAN_LINK_DRAW_MILLIS))
                    if (appear <= 0f) continue
                    val locked = chainStepProgress(l, k, steps)
                    val swell = (0.6f + 0.4f * appear) * (1f + LOCK_POP * sin(PI.toFloat() * locked))
                    val left = k * step
                    // Alternate links are seen edge-on, as in a real chain.
                    val h = if (k % 2 == 0) linkH else linkH * 0.62f
                    scale(swell, swell, pivot = Offset(left + linkW / 2f, cy)) {
                        val topLeft = Offset(left, cy - h / 2f)
                        val linkSize = Size(linkW, h)
                        val corner = CornerRadius(h / 2f)
                        if (locked > 0f) drawRoundRect(primary, topLeft, linkSize, corner, alpha = 0.22f * locked * appear)
                        drawRoundRect(lerp(outline, primary, locked), topLeft, linkSize, corner, alpha = appear, style = stroke)
                    }
                }
                if (rooted) {
                    val appear = FastOutSlowInEasing.transform(scanStage(t, chainLinkStart(count, count), SCAN_LINK_DRAW_MILLIS))
                    if (appear > 0f) {
                        val locked = chainStepProgress(l, count, steps)
                        val c = lerp(outline, primary, locked)
                        val swell = (0.6f + 0.4f * appear) * (1f + LOCK_POP * sin(PI.toFloat() * locked))
                        scale(swell, swell, pivot = Offset(keyX + keyW / 2f, cy)) {
                            val ring = Offset(keyX + keyRing, cy)
                            if (locked > 0f) drawCircle(primary, radius = keyRing, center = ring, alpha = 0.22f * locked * appear)
                            drawCircle(c, radius = keyRing, center = ring, alpha = appear, style = keyStroke)
                            val shaftStart = keyX + keyRing * 2f
                            val shaftEnd = keyX + keyW
                            val w = keyStroke.width
                            drawLine(c, Offset(shaftStart, cy), Offset(shaftEnd, cy), strokeWidth = w, cap = StrokeCap.Round, alpha = appear)
                            drawLine(c, Offset(shaftEnd, cy), Offset(shaftEnd, cy + tooth), strokeWidth = w, cap = StrokeCap.Round, alpha = appear)
                            val inner = shaftEnd - tooth
                            drawLine(c, Offset(inner, cy), Offset(inner, cy + tooth * 0.7f), strokeWidth = w, cap = StrokeCap.Round, alpha = appear)
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
