package com.pixel.intelligentsearch.core.ui

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.ai.AiProvider
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** [provider]'s official mark: Claude's starburst, OpenAI's knot, Gemini's sparkle. */
@DrawableRes
internal fun aiProviderLogo(provider: AiProvider): Int = when (provider) {
    AiProvider.ANTHROPIC -> R.drawable.ic_logo_claude
    AiProvider.OPENAI -> R.drawable.ic_logo_openai
    AiProvider.GEMINI -> R.drawable.ic_logo_gemini
}

/** Whether [provider]'s mark is one colour, drawn in the text beside it (OpenAI's); the others keep their brand colours. */
internal fun aiProviderLogoIsMonochrome(provider: AiProvider): Boolean = provider == AiProvider.OPENAI

/**
 * [provider]'s brand colours, for its reveal's outline and sparks and its selected pill's tint: Claude's terracotta,
 * Gemini's blue with the green, red and yellow its sparkle washes in, and none for OpenAI, whose one-colour mark
 * takes the text's colour.
 */
internal fun aiProviderBrandColors(provider: AiProvider): List<Color> = when (provider) {
    AiProvider.ANTHROPIC -> listOf(Color(0xFFD97757), Color(0xFFF0A48A))
    AiProvider.OPENAI -> emptyList()
    AiProvider.GEMINI -> listOf(Color(0xFF3186FF), Color(0xFF08B962), Color(0xFFF94543), Color(0xFFFABC12))
}

/**
 * The selected provider pill's colour: the theme's selection colour washed with the brand's own ([aiProviderBrandColors]),
 * a little, so the choice reads as that provider's while its text keeps its contrast. OpenAI's stays the theme's.
 */
fun aiProviderPillColor(provider: AiProvider, selection: Color): Color =
    aiProviderBrandColors(provider).firstOrNull()?.let { lerp(selection, it, BRAND_TINT) } ?: selection

/** How much of the brand's colour the selected pill takes. */
internal const val BRAND_TINT = 0.16f

/** The reveal: how long the outline takes to trace, when the fill blooms in, and how long the sparks fly. */
internal const val LOGO_TRACE_MILLIS = 460
internal const val LOGO_FILL_DELAY_MILLIS = 240
internal const val LOGO_BURST_DELAY_MILLIS = 300
internal const val LOGO_BURST_MILLIS = 560

/** How many sparks fly out of the logo as it is picked. */
internal const val LOGO_SPARKS = 10

/** How strongly a logo that isn't picked shows, so the picked one stands out. */
internal const val LOGO_UNPICKED_ALPHA = 0.62f

/**
 * Where spark [index] of [count] is at [burst] (0 to 1) of its flight, from a logo of [radius] at [centre]: they leave
 * the logo's edge evenly round it, a little staggered, and slow as they go out to about one and a half times its
 * radius.
 */
internal fun sparkPosition(burst: Float, index: Int, count: Int, centre: Offset, radius: Float): Offset {
    val angle = 2f * PI.toFloat() * index / count.coerceAtLeast(1) + (if (index % 2 == 0) 0f else 0.18f)
    val eased = LinearOutSlowInEasing.transform(burst.coerceIn(0f, 1f))
    val reach = radius * (0.7f + (if (index % 2 == 0) 0.85f else 0.6f) * eased)
    return Offset(centre.x + cos(angle) * reach, centre.y + sin(angle) * reach)
}

/** The mark's outline as one path in its drawable's 24-unit grid, read from the drawable's own path data. */
// The logos are vector drawables, which are XML resources, so reading one with getXml is valid.
@android.annotation.SuppressLint("ResourceType")
internal fun logoOutline(resources: Resources, @DrawableRes drawable: Int): android.graphics.Path? = runCatching {
    val parser = resources.getXml(drawable)
    var path: android.graphics.Path? = null
    while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "path") {
            val data = parser.getAttributeValue(ANDROID_NAMESPACE, "pathData") ?: continue
            // Gemini's sparkle is drawn four times over (its colour washes); its outline is the first.
            path = PathParser.createPathFromPathData(data)
            break
        }
    }
    parser.close()
    path
}.getOrNull()

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

/**
 * [provider]'s official logo at [size]: in its own colours, or (OpenAI's one-colour mark) in the current content
 * colour. While not [selected] it shows a little muted. Each time it becomes selected, and each time [taps] (how
 * many times its option has been tapped) changes while it is selected, so tapping the chosen provider again replays
 * it (not when first shown, and not with animations off), it reveals itself: its own outline traces round in the
 * brand's colour, the logo blooms in from the centre on a spring, and a ring of sparks in the brand's colours
 * (Gemini's four) flies out with a soft glow. Decorative: the name beside it is what TalkBack reads.
 */
@Composable
fun AiProviderLogo(
    provider: AiProvider,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    selected: Boolean = false,
    taps: Int = 0
) {
    val resources = LocalResources.current
    val painter = painterResource(aiProviderLogo(provider))
    val animate by rememberAnimationsEnabled()
    val outline = remember(provider, resources) { logoOutline(resources, aiProviderLogo(provider)) }
    val contentColor = LocalContentColor.current
    val brand = aiProviderBrandColors(provider).ifEmpty { listOf(contentColor) }
    // 1 is finished: a logo first drawn shows as it is.
    val trace = remember { Animatable(1f) }
    val fill = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    val shownSelected = remember { booleanArrayOf(selected) }
    val seenTaps = remember { intArrayOf(taps) }
    LaunchedEffect(selected, taps, animate) {
        val play = selected && (!shownSelected[0] || taps != seenTaps[0])
        shownSelected[0] = selected
        seenTaps[0] = taps
        if (!play || !animate) return@LaunchedEffect
        trace.snapTo(0f)
        fill.snapTo(0f)
        // A replay can cut in mid-flight: clear the last sparks rather than hold them while the new ones wait.
        burst.snapTo(1f)
        coroutineScope {
            launch { trace.animateTo(1f, tween(LOGO_TRACE_MILLIS, easing = FastOutSlowInEasing)) }
            launch {
                delay(LOGO_FILL_DELAY_MILLIS.toLong())
                fill.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
            }
            launch {
                // The sparks wait for the outline to near its end, then fly.
                delay(LOGO_BURST_DELAY_MILLIS.toLong())
                burst.snapTo(0f)
                burst.animateTo(1f, tween(LOGO_BURST_MILLIS, easing = LinearOutSlowInEasing))
            }
        }
    }
    val presence by animateFloatAsState(if (selected) 1f else LOGO_UNPICKED_ALPHA, label = "logoPresence")

    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val scale = this.size.minDimension / 24f
                val traced = outline?.let { source ->
                    android.graphics.Path(source).apply {
                        transform(android.graphics.Matrix().apply { setScale(scale, scale) })
                    }
                }
                val measure = traced?.let { android.graphics.PathMeasure(it, false) }
                // Every contour's length, so the trace runs on through them as one line.
                val lengths = buildList {
                    if (measure != null) do add(measure.length) while (measure.nextContour())
                }
                val total = lengths.sum()
                val piece = android.graphics.Path()
                val traceStroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val ringStroke = Stroke(width = 1.5.dp.toPx())
                val sparkRadius = 1.7.dp.toPx()
                val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                val radius = this.size.minDimension / 2f
                onDrawWithContent {
                    val b = burst.value
                    // Behind the logo: a soft ring and the sparks, flying out and fading.
                    if (b < 1f) {
                        val fade = 1f - b
                        drawCircle(brand.first(), radius = radius * (0.6f + 0.8f * b), center = centre, alpha = 0.45f * fade * fade, style = ringStroke)
                        for (k in 0 until LOGO_SPARKS) {
                            drawCircle(
                                brand[k % brand.size],
                                radius = sparkRadius * (0.5f + 0.5f * fade),
                                center = sparkPosition(b, k, LOGO_SPARKS, centre, radius),
                                alpha = fade
                            )
                        }
                    }
                    drawContent()
                    // Over it: the outline tracing round, fading as the fill takes over.
                    val t = trace.value
                    if (traced != null && measure != null && (t < 1f || fill.value < 0.98f)) {
                        var left = total * t
                        piece.rewind()
                        measure.setPath(traced, false)
                        var i = 0
                        do {
                            val length = lengths.getOrElse(i) { 0f }
                            if (left <= 0f) break
                            measure.getSegment(0f, minOf(left, length), piece, true)
                            left -= length
                            i++
                        } while (measure.nextContour())
                        val outlineAlpha = (1f - fill.value.coerceIn(0f, 1f)).coerceIn(0f, 1f)
                        drawPath(piece.asComposePath(), brand.first(), style = traceStroke, alpha = outlineAlpha)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val bloom = Modifier
            .size(size)
            .graphicsLayer {
                val f = fill.value
                alpha = f.coerceIn(0f, 1f) * presence
                val s = 0.55f + 0.45f * f
                scaleX = s
                scaleY = s
            }
        if (aiProviderLogoIsMonochrome(provider)) {
            Icon(painter, contentDescription = null, tint = contentColor, modifier = bloom)
        } else {
            Image(painter, contentDescription = null, modifier = bloom)
        }
    }
}
