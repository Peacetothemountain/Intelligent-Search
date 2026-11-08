package com.pixel.intelligentsearch.feature.search.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The page's cards let a little of the living light through. */
internal const val CARD_ALPHA = 0.88f

/** The page's spring for things arriving: quick, with a little bounce. */
internal fun <T> arriveSpring() = spring<T>(dampingRatio = 0.62f, stiffness = 240f)

/**
 * The light behind the page: three soft glows (the accent, the tertiary and a warm one) drifting very slowly on
 * their own paths, 40 to 60 seconds each way. Still with animations off.
 */
@Composable
internal fun LivingLight(warm: Color, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    val dark = scheme.surface.luminance() < 0.5f
    val a = androidx.compose.ui.graphics.lerp(scheme.primaryContainer, scheme.primary, 0.3f).copy(alpha = if (dark) 0.75f else 0.6f)
    val b = androidx.compose.ui.graphics.lerp(scheme.tertiaryContainer, scheme.tertiary, 0.3f).copy(alpha = if (dark) 0.45f else 0.5f)
    val c = warm.copy(alpha = if (dark) 0.18f else 0.3f)
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "living light")
        val ta by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(48_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
        val tb by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
        val tc by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(40_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c")
        Triple({ ta }, { tb }, { tc })
    } else Triple({ 0.3f }, { 0.3f }, { 0.3f })
    // Each glow's gradient is made once per size, around the origin at its smallest radius, then moved and scaled to
    // where the glow is on each frame. A gradient made new every frame kept the collector busy while the page sat open.
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val w = size.width
            val ra = w * 0.75f
            val rb = w * 0.62f
            val rc = w * 0.5f
            val ga = Brush.radialGradient(listOf(a, Color.Transparent), center = Offset.Zero, radius = ra)
            val gb = Brush.radialGradient(listOf(b, Color.Transparent), center = Offset.Zero, radius = rb)
            val gc = Brush.radialGradient(listOf(c, Color.Transparent), center = Offset.Zero, radius = rc)
            onDrawBehind {
                val pa = t.first()
                glow(ga, ra, w * (-0.05f + 0.16f * pa), size.height * (0.02f + 0.1f * pa), w * (0.75f + 0.08f * sin(pa * 3.14f)))
                val pb = t.second()
                glow(gb, rb, w * (1.02f - 0.18f * pb), size.height * (0.22f + 0.07f * pb), w * (0.62f + 0.1f * pb))
                val pc = t.third()
                glow(gc, rc, w * (0.2f + 0.3f * pc), size.height * (0.1f - 0.02f * pc), w * (0.5f + 0.06f * pc))
            }
        }
    )
}

/** Draws [brush], a radial gradient of [made] radius around the origin, as a glow of [radius] centered at [x], [y]. */
private fun DrawScope.glow(brush: Brush, made: Float, x: Float, y: Float, radius: Float) {
    translate(x, y) {
        scale(radius / made, pivot = Offset.Zero) {
            drawCircle(brush, made, Offset.Zero)
        }
    }
}

/**
 * Calls [onSeen] once, the first time this comes onto the screen (its top 40 dp above the window's bottom), so
 * a part can start its animation when the user gets to it rather than off screen.
 */
internal fun Modifier.onFirstSeen(onSeen: () -> Unit): Modifier = composed {
    var seen by remember { mutableStateOf(false) }
    val height = LocalWindowInfo.current.containerSize.height
    val margin = with(LocalDensity.current) { 40.dp.toPx() }
    onGloballyPositioned { coordinates ->
        if (!seen && coordinates.boundsInWindow().top < height - margin) {
            seen = true
            onSeen()
        }
    }
}

/**
 * [text], each letter rising into place one after another as it first shows, inking in from a light 250 weight to
 * [style]'s own as it rises (all at once with animations off). A settled letter is drawn in [style] itself.
 */
@Composable
internal fun SettleText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, delayMs: Long = 120) {
    val animate by rememberAnimationsEnabled()
    val density = LocalDensity.current
    val letters = remember(text) { text.map { Animatable(if (animate) 0f else 1f) } }
    LaunchedEffect(text, animate) {
        if (!animate) return@LaunchedEffect
        delay(delayMs)
        letters.forEachIndexed { i, letter ->
            launchLater(i * 28L) { letter.animateTo(1f, arriveSpring()) }
        }
    }
    val rest = (style.fontWeight ?: FontWeight.Normal).weight
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { i, c ->
            // Recomposes only when the letter reaches the next weight step, not on every frame.
            val weight by remember(letters, rest) { derivedStateOf { settleWeight(letters[i].value, rest) } }
            Text(
                c.toString(),
                style = if (weight >= rest) style else style.copy(fontFamily = SettleFamily, fontWeight = FontWeight(weight)),
                color = color,
                modifier = Modifier.graphicsLayer {
                    val p = letters[i].value
                    alpha = (p * 2f).coerceIn(0f, 1f)
                    translationY = (1f - p) * with(density) { 12.dp.toPx() }
                }
            )
        }
    }
}

/** The lightest weight a [SettleText] letter starts from, as in the approved design. */
private const val SETTLE_FROM = 250

/** How far apart the weights a settling letter passes through are: fine enough to read as one smooth inking in. */
private const val SETTLE_STEP = 25

/** A letter's weight at [progress] of its settle toward [rest], on [SETTLE_STEP] steps from [SETTLE_FROM]. */
private fun settleWeight(progress: Float, rest: Int): Int {
    if (rest <= SETTLE_FROM) return rest
    val w = SETTLE_FROM + (rest - SETTLE_FROM) * progress.coerceIn(0f, 1f)
    return (SETTLE_FROM + ((w - SETTLE_FROM) / SETTLE_STEP).toInt() * SETTLE_STEP).coerceAtMost(rest)
}

/**
 * Google Sans Flex at each step a settling letter passes through, with the same axis settings as [GoogleSansFlex],
 * whose own weights start at 300. Each weight's typeface is made the first time it is drawn.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val SettleFamily = FontFamily(
    (SETTLE_FROM until 700 step SETTLE_STEP).map { w ->
        Font(
            R.font.google_sans_flex,
            FontWeight(w),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w),
                FontVariation.width(100f),
                FontVariation.slant(0f),
                FontVariation.grade(0),
                FontVariation.Setting("ROND", 0f)
            )
        )
    }
)

/** [text] whose letters hop one after another each time [hops] changes. */
@Composable
internal fun HopText(text: String, hops: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val animate by rememberAnimationsEnabled()
    val density = LocalDensity.current
    val lift = remember(text) { text.map { Animatable(0f) } }
    LaunchedEffect(hops) {
        if (hops == 0 || !animate) return@LaunchedEffect
        lift.forEachIndexed { i, letter ->
            launchLater(i * 40L) {
                letter.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
                letter.animateTo(0f, arriveSpring())
            }
        }
    }
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { i, c ->
            Text(c.toString(), style = style, color = color, modifier = Modifier.graphicsLayer {
                translationY = -lift[i].value * with(density) { 9.dp.toPx() }
            })
        }
    }
}

/** Runs [block] in a child coroutine after [delayMs]. */
private fun kotlinx.coroutines.CoroutineScope.launchLater(delayMs: Long, block: suspend () -> Unit) {
    launch {
        delay(delayMs)
        block()
    }
}

/** A soft light sweeping across, every five seconds after the first. */
internal fun Modifier.sheen(): Modifier = composed {
    val animate by rememberAnimationsEnabled()
    if (!animate) return@composed this
    val transition = rememberInfiniteTransition(label = "sheen")
    val p by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "sweep")
    drawWithContent {
        drawContent()
        // Sweeps in the last 30% of each cycle.
        val s = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
        if (s <= 0f || s >= 1f) return@drawWithContent
        val x = -size.width * 0.6f + s * size.width * 2.2f
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                start = Offset(x - size.width * 0.25f, 0f),
                end = Offset(x + size.width * 0.25f, size.height)
            )
        )
    }
}

/** A gentle breath: up to 6% larger and back, every 2.4 seconds. */
internal fun Modifier.breathe(): Modifier = composed {
    val animate by rememberAnimationsEnabled()
    if (!animate) return@composed this
    val transition = rememberInfiniteTransition(label = "breathe")
    val s by transition.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/** A slow zoom and drift, as a slideshow does with a photo, 12 seconds each way. */
internal fun Modifier.kenBurns(): Modifier = composed {
    val animate by rememberAnimationsEnabled()
    if (!animate) return@composed this
    val transition = rememberInfiniteTransition(label = "ken burns")
    val p by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(12_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    graphicsLayer {
        val s = 1f + 0.14f * p
        scaleX = s
        scaleY = s
        translationX = -6.dp.toPx() * p
        translationY = -4.dp.toPx() * p
    }
}

/** A burst of confetti from a point, for a small celebration. */
internal fun interface Confetti {
    fun burst(at: Offset)
}

internal val LocalConfetti = staticCompositionLocalOf { Confetti { } }

private class Piece(val x: Float, val y: Float, val vx: Float, val vy: Float, val spin: Float, val color: Color, val born: Long)

/**
 * Where confetti flies: bursts of 26 pieces in the page's colors, thrown up and falling with gravity and fading over
 * 1.3 seconds. Nothing with animations off.
 */
@Composable
internal fun ConfettiHost(content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    val pieces = remember { mutableStateListOf<Piece>() }
    var now by remember { mutableStateOf(0L) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val colors = listOf(scheme.primary, scheme.tertiary, Color(0xFFF2B872), Color(0xFF9DD7A6))
    val confetti = remember(animate) {
        Confetti { at ->
            if (!animate) return@Confetti
            val t = System.nanoTime()
            repeat(26) { i ->
                val angle = -Math.PI / 2 + (Random.nextFloat() - 0.5f) * 2.2f
                val speed = 900f + Random.nextFloat() * 900f
                pieces += Piece(at.x, at.y, (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(), Random.nextFloat() * 720f, colors[i % colors.size], t)
            }
        }
    }
    LaunchedEffect(pieces.size) {
        while (pieces.isNotEmpty()) {
            withFrameNanos { frame ->
                now = frame
                pieces.removeAll { (frame - it.born) / 1e9f > 1.3f }
            }
        }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }) {
        CompositionLocalProvider(LocalConfetti provides Confetti { at -> confetti.burst(at - origin) }) { content() }
        if (pieces.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val size7 = Size(7.dp.toPx(), 11.dp.toPx())
                for (p in pieces) {
                    val age = ((now - p.born) / 1e9f).coerceAtLeast(0f)
                    val x = p.x + p.vx * age
                    val y = p.y + p.vy * age + 1600f * age * age
                    val alpha = (1f - age / 1.3f).coerceIn(0f, 1f)
                    rotate(p.spin * age, pivot = Offset(x, y)) {
                        drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x - size7.width / 2, y - size7.height / 2), size = size7)
                    }
                }
            }
        }
    }
}
