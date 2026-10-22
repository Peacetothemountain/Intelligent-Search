package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Hardware & Security page's posture card: an overall rating over the radar chart of each check. The rating's
 * percentage counts up from 0 as the radar draws out ([entryDelayMillis] after the card appears, and again on a
 * re-scan), landing on its value as the radar settles; with animations off it is simply shown.
 */
@Composable
internal fun SecurityPostureCard(
    axes: List<SecurityPosture.AxisScore>,
    modifier: Modifier = Modifier,
    entryDelayMillis: Long = 0L
) {
    val scheme = MaterialTheme.colorScheme
    val overall = SecurityPosture.overall(axes)
    val percent = overall?.let { (it * 100).roundToInt() }
    val animationsEnabled by rememberAnimationsEnabled()
    val countUp = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    var rescans by remember { mutableIntStateOf(0) }
    LaunchedEffect(percent != null, animationsEnabled, rescans) {
        when {
            !animationsEnabled -> countUp.snapTo(1f)
            percent == null -> countUp.snapTo(0f)
            // Already counted (e.g. animations were switched back on): leave the number where it landed.
            rescans == 0 && countUp.value >= 1f -> Unit
            else -> {
                countUp.snapTo(0f)
                if (rescans == 0) delay(entryDelayMillis)
                countUp.animateTo(1f, tween(countUpMillis(axes.size), easing = LinearEasing))
            }
        }
    }
    val chipMeasurer = rememberTextMeasurer(cacheSize = 0)
    DiagnosticsCard(
        icon = Icons.Outlined.Shield,
        title = "Security Posture",
        accent = scheme.primary,
        modifier = modifier,
        trailing = {
            val (container, content) = when {
                overall == null -> scheme.surfaceVariant to scheme.onSurfaceVariant
                overall >= 0.65f -> scheme.primaryContainer.copy(alpha = 0.6f) to scheme.onPrimaryContainer
                overall >= 0.4f -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                else -> scheme.errorContainer to scheme.onErrorContainer
            }
            val label = SecurityPosture.overallLabel(overall)
            val countStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = content)
            DiagnosticsStatusChip(
                text = if (percent == null) label else "$label · $percent%",
                container = container,
                content = content,
                textModifier = if (percent == null || !animationsEnabled) {
                    Modifier
                } else {
                    Modifier.countUpText("$label · ", percent, { countUp.value }, countStyle, chipMeasurer)
                }
            )
        }
    ) {
        SecurityRadarChart(axes, entryDelayMillis = entryDelayMillis, onRescan = { rescans++ })
    }
}

/** The colour that marks an axis's rating, on the chart and in its value label. */
internal fun ratingColor(rating: SecurityPosture.Rating, scheme: ColorScheme): Color = when (rating) {
    SecurityPosture.Rating.Good -> scheme.primary
    SecurityPosture.Rating.Fair -> scheme.tertiary
    SecurityPosture.Rating.Weak -> scheme.error
    SecurityPosture.Rating.Pending -> scheme.outline
}

/** The spring each axis draws out on. */
private val AxisSpring = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)

private val MaxRadius = 84.dp
private val MinInlineRadius = 44.dp
private val LabelGap = 6.dp
private val ChartPadding = 8.dp

/**
 * A radar ("spider") chart of the security checks, in the battery page's visual language: dashed grid rings, a soft
 * Material You fill under a crisp outline, and pulsing vertex dots. Each axis springs out from the centre in turn
 * when the card appears (and again when a pending check resolves, or when the chart is tapped to re-scan), then the
 * shape breathes gently while a radar beam sweeps round it, lighting each check as it passes. With animations
 * removed it is drawn finished and still, with no beam. [entryDelayMillis] holds the first draw-out so it can follow
 * the page's own entrance, and [onRescan] hears each tap-to-re-scan (the posture chip counts up again with it).
 *
 * Axis labels sit around the chart when they fit; when the text is too wide for the space (large fonts, narrow or
 * zoomed screens) the chart is drawn alone and the labels move into a list below it.
 */
@Composable
internal fun SecurityRadarChart(
    axes: List<SecurityPosture.AxisScore>,
    modifier: Modifier = Modifier,
    entryDelayMillis: Long = 0L,
    onRescan: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled by rememberAnimationsEnabled()
    val count = axes.size

    val animated = remember(count) { List(count) { Animatable(0f) } }
    // The entry delay holds only the first draw-out, not a later one when a pending check resolves.
    val awaitingEntry = remember { booleanArrayOf(true) }
    axes.forEachIndexed { i, axis ->
        key(i) {
            LaunchedEffect(axis.score, animationsEnabled) {
                if (!animationsEnabled) {
                    animated[i].snapTo(axis.score)
                } else {
                    // On entry the axes draw out one after another.
                    if (animated[i].value == 0f) {
                        delay(i * RADAR_ENTRY_STAGGER_MILLIS + if (awaitingEntry[0]) entryDelayMillis else 0L)
                    }
                    awaitingEntry[0] = false
                    animated[i].animateTo(axis.score, AxisSpring)
                }
            }
        }
    }

    // A tap re-scans: every axis falls back to the centre and draws out again, one after another.
    val scope = rememberCoroutineScope()
    val currentAxes by rememberUpdatedState(axes)
    val currentOnRescan by rememberUpdatedState(onRescan)
    val rescan: () -> Unit = remember(animated) {
        {
            currentOnRescan()
            currentAxes.forEachIndexed { i, axis ->
                scope.launch {
                    animated.getOrNull(i)?.let { anim ->
                        anim.snapTo(0f)
                        delay(i * RADAR_ENTRY_STAGGER_MILLIS)
                        anim.animateTo(axis.score, AxisSpring)
                    }
                }
            }
        }
    }

    val phase: State<Float>
    val pulse: State<Float>
    val sweep: State<Float>?
    if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "postureBreath")
        phase = transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
            label = "postureBreathPhase"
        )
        pulse = transition.animateFloat(
            initialValue = 3.dp.value,
            targetValue = 5.5.dp.value,
            animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "posturePointPulse"
        )
        sweep = transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(SWEEP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "postureSweep"
        )
    } else {
        phase = remember { mutableFloatStateOf(0f) }
        pulse = remember { mutableFloatStateOf(4f) }
        sweep = null
    }

    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val density = LocalDensity.current
    val labels = remember(axes, nameStyle, valueStyle, scheme, density) {
        axes.map { axis ->
            val name = measurer.measure(axis.axis.label, nameStyle, softWrap = false, maxLines = 1)
            val value = measurer.measure(axis.value, valueStyle.copy(color = ratingColor(axis.rating, scheme)), softWrap = false, maxLines = 1)
            name to value
        }
    }
    // Numbers that tie each vertex to its row in the list when the labels can't sit beside the chart.
    val badgeStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val badges = remember(axes, badgeStyle, scheme, density) {
        axes.mapIndexed { i, axis ->
            measurer.measure("${i + 1}", badgeStyle.copy(color = ratingColor(axis.rating, scheme)), softWrap = false, maxLines = 1)
        }
    }
    val badgeDiameterPx = (badges.maxOfOrNull { maxOf(it.size.width, it.size.height) } ?: 0) + with(density) { 6.dp.toPx() }
    val description = remember(axes) { SecurityPosture.describe(axes) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .clearAndSetSemantics { contentDescription = description }
            .then(if (animationsEnabled) Modifier.pointerInput(rescan) { detectTapGestures { rescan() } } else Modifier)
    ) {
        val angles = List(count) { i -> (-PI / 2 + i * 2 * PI / count).toFloat() }
        val halfWidthPx = with(density) { (maxWidth / 2 - ChartPadding).toPx() }
        val gapPx = with(density) { LabelGap.toPx() }
        val labelWidths = labels.map { (n, v) -> maxOf(n.size.width, v.size.width).toFloat() }
        val labelHeight = labels.maxOfOrNull { (n, v) -> n.size.height + v.size.height }?.toFloat() ?: 0f
        // The largest radius at which every label still fits beside its vertex.
        var inlineRadius = with(density) { MaxRadius.toPx() }
        angles.forEachIndexed { i, angle ->
            val c = abs(cos(angle))
            if (c > 0.05f) inlineRadius = minOf(inlineRadius, (halfWidthPx - gapPx - labelWidths[i]) / c)
            else if (labelWidths[i] / 2 > halfWidthPx) inlineRadius = 0f
        }
        val inline = inlineRadius >= with(density) { MinInlineRadius.toPx() }

        if (inline) {
            val chartHeight = with(density) { (2 * (inlineRadius + gapPx + labelHeight)).toDp() + ChartPadding * 2 }
            RadarCanvas(
                axes = axes, animated = animated, phase = phase, pulse = pulse, sweep = sweep, angles = angles,
                radiusPx = inlineRadius, labels = labels, badges = null, badgeDiameterPx = 0f, labelGapPx = gapPx,
                scheme = scheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            )
        } else {
            val compactRadius = with(density) {
                minOf(MaxRadius.toPx(), halfWidthPx - gapPx - badgeDiameterPx).coerceAtLeast(24.dp.toPx())
            }
            val badgeSize = with(density) { badgeDiameterPx.toDp() }
            Column(modifier = Modifier.padding(bottom = ChartPadding + 4.dp)) {
                RadarCanvas(
                    axes = axes, animated = animated, phase = phase, pulse = pulse, sweep = sweep, angles = angles,
                    radiusPx = compactRadius, labels = null, badges = badges, badgeDiameterPx = badgeDiameterPx,
                    labelGapPx = gapPx, scheme = scheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (2 * (compactRadius + gapPx + badgeDiameterPx)).toDp() } + ChartPadding * 2)
                )
                Column(
                    modifier = Modifier.padding(horizontal = ChartPadding + 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    axes.forEachIndexed { i, axis -> RadarLegendRow(i + 1, axis, badgeSize, scheme) }
                }
            }
        }
    }
}

@Composable
private fun RadarLegendRow(number: Int, axis: SecurityPosture.AxisScore, badgeSize: androidx.compose.ui.unit.Dp, scheme: ColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(ratingColor(axis.rating, scheme).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ratingColor(axis.rating, scheme)
            )
        }
        // Name and value share a line when they fit; with large text the value wraps under the name, never mid-word.
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = axis.axis.label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = axis.value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ratingColor(axis.rating, scheme)
            )
        }
    }
}

/** How far behind the radar beam (radians) a vertex it has passed fades back to rest; also the beam's visible tail. */
internal const val BEAM_TRAIL = 1.6f

/** How far ahead of the beam (radians) a vertex starts to light, so it brightens as the beam reaches it. */
private const val BEAM_LEAD = 0.12f

/** One turn of the radar beam. */
private const val SWEEP_MILLIS = 4000

/** How much of each turn the ring ripple from the centre takes to spread out and fade. */
private const val RIPPLE_SHARE = 0.6f

/** How long after the previous axis each axis starts to draw out, on entry and on a re-scan. */
internal const val RADAR_ENTRY_STAGGER_MILLIS = 60L

/**
 * How lit a vertex at [axisAngle] is by the radar beam at [beamAngle] (both radians, clockwise from 3 o'clock, any
 * number of turns): 1 with the beam on it, easing back to 0 over [BEAM_TRAIL] behind the beam as it moves on, and
 * rising over a short [BEAM_LEAD] just before the beam arrives; 0 everywhere else.
 */
internal fun sweepHighlight(beamAngle: Float, axisAngle: Float): Float {
    val turn = (2 * PI).toFloat()
    val behind = ((beamAngle - axisAngle) % turn + turn) % turn
    if (behind <= BEAM_TRAIL) {
        val f = 1f - behind / BEAM_TRAIL
        return f * f
    }
    val ahead = turn - behind
    if (ahead < BEAM_LEAD) {
        val f = 1f - ahead / BEAM_LEAD
        return f * f
    }
    return 0f
}

/**
 * The chart itself. With [sweep] (the beam's angle past 12 o'clock, radians) it is a live radar: a soft primary
 * beam turns around the centre, fading behind its leading edge, a faint ring spreads from the centre once a turn,
 * and each vertex's dot flashes and grows and its label lights in its rating colour as the beam passes. Without it
 * the chart is drawn still. Its grid, brushes and label places are built once per size in [drawWithCache], and a
 * frame only reads the animations and redraws, allocating nothing but the text it draws.
 */
@Composable
private fun RadarCanvas(
    axes: List<SecurityPosture.AxisScore>,
    animated: List<Animatable<Float, *>>,
    phase: State<Float>,
    pulse: State<Float>,
    sweep: State<Float>?,
    angles: List<Float>,
    radiusPx: Float,
    labels: List<Pair<TextLayoutResult, TextLayoutResult>>?,
    badges: List<TextLayoutResult>?,
    badgeDiameterPx: Float,
    labelGapPx: Float,
    scheme: ColorScheme,
    modifier: Modifier
) {
    val primary = scheme.primary
    val grid = scheme.outlineVariant
    val surface = scheme.surface
    val nameColor = scheme.onSurfaceVariant
    val gridDash = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) }
    val spokeDash = remember { PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) }
    Spacer(modifier = modifier.drawWithCache {
        val count = angles.size
        val center = Offset(size.width / 2f, size.height / 2f)
        val cosA = FloatArray(count) { cos(angles[it]) }
        val sinA = FloatArray(count) { sin(angles[it]) }
        fun ring(fraction: Float) = Path().apply {
            for (i in 0 until count) {
                val x = center.x + cosA[i] * radiusPx * fraction
                val y = center.y + sinA[i] * radiusPx * fraction
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val innerRings = List(3) { ring((it + 1) / 4f) }
        val outerRing = ring(1f)
        val gridStroke = Stroke(width = 0.8.dp.toPx(), pathEffect = gridDash)
        val outerStroke = Stroke(width = 1.dp.toPx())
        val outlineStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val rippleStroke = Stroke(width = 1.5.dp.toPx())
        val spokeWidth = 0.8.dp.toPx()
        val edgeWidth = 1.2.dp.toPx()
        val gridFaint = grid.copy(alpha = 0.3f)
        val gridOuter = grid.copy(alpha = 0.55f)
        val outlineColor = primary.copy(alpha = 0.85f)
        val edgeColor = primary.copy(alpha = 0.45f)
        val fill = Brush.radialGradient(
            listOf(primary.copy(alpha = 0.08f), primary.copy(alpha = 0.32f)),
            center = center,
            radius = radiusPx.coerceAtLeast(1f)
        )
        // Clear up to the tail, then rising to the leading edge at 0 degrees; turned to the beam's angle each frame.
        val tail = (BEAM_TRAIL / (2 * PI)).toFloat()
        val beam = Brush.sweepGradient(
            0f to primary.copy(alpha = 0f),
            (1f - tail) to primary.copy(alpha = 0f),
            1f to primary.copy(alpha = 0.22f),
            center = center
        )
        val colors = List(count) { ratingColor(axes[it].rating, scheme) }
        val shape = Path()
        val breathePx = 2.dp.toPx()
        val dotRing = 3.5.dp.toPx()
        val dot = 2.dp.toPx()
        val labelPad = 3.dp.toPx()
        val labelCorner = CornerRadius(6.dp.toPx())

        // Labels beside each vertex: left of it on the left side, right of it on the right, centred above or below.
        val boxLeft = FloatArray(count)
        val boxTop = FloatArray(count)
        val boxWidth = FloatArray(count)
        val boxHeight = FloatArray(count)
        val nameX = FloatArray(count)
        val valueX = FloatArray(count)
        labels?.forEachIndexed { i, (name, value) ->
            val c = cosA[i]
            val ax = center.x + c * (radiusPx + labelGapPx)
            val ay = center.y + sinA[i] * (radiusPx + labelGapPx)
            val w = maxOf(name.size.width, value.size.width).toFloat()
            val h = (name.size.height + value.size.height).toFloat()
            val left = when {
                c > 0.05f -> ax
                c < -0.05f -> ax - w
                else -> ax - w / 2f
            }
            boxLeft[i] = left
            boxTop[i] = when {
                abs(c) > 0.05f -> ay - h / 2f
                sinA[i] < 0f -> ay - h
                else -> ay
            }
            boxWidth[i] = w
            boxHeight[i] = h
            fun lineX(line: TextLayoutResult) = when {
                c > 0.05f -> left
                c < -0.05f -> left + w - line.size.width
                else -> left + (w - line.size.width) / 2f
            }
            nameX[i] = lineX(name)
            valueX[i] = lineX(value)
        }

        // How far out along its axis vertex i is this frame: its spring, breathing a couple of dp.
        fun reach(i: Int, t: Float): Float {
            val a = animated[i].value
            return (a * radiusPx + if (a > 0f) sin(t + i * 1.4f) * breathePx else 0f).coerceIn(0f, radiusPx + breathePx * 2)
        }

        onDrawBehind {
            // Grid: dashed inner rings, a fine solid outer ring and dashed spokes.
            for (r in innerRings) drawPath(r, gridFaint, style = gridStroke)
            drawPath(outerRing, gridOuter, style = outerStroke)
            for (i in 0 until count) {
                drawLine(
                    gridFaint, center, Offset(center.x + cosA[i] * radiusPx, center.y + sinA[i] * radiusPx),
                    strokeWidth = spokeWidth, pathEffect = spokeDash
                )
            }

            // The beam, its leading edge and the ripple, inside the outer ring.
            val turned = sweep?.value
            val beamAngle = if (turned == null) 0f else turned - (PI / 2).toFloat()
            if (turned != null) {
                clipPath(outerRing) {
                    rotate(degrees = beamAngle * (180f / PI.toFloat()), pivot = center) {
                        drawCircle(beam, radius = radiusPx, center = center)
                        drawLine(edgeColor, center, Offset(center.x + radiusPx, center.y), strokeWidth = edgeWidth, cap = StrokeCap.Round)
                    }
                    val spread = turned / (2f * PI.toFloat()) / RIPPLE_SHARE
                    if (spread < 1f) {
                        val fade = 1f - spread
                        drawCircle(primary, radius = radiusPx * spread, center = center, alpha = 0.35f * fade * fade, style = rippleStroke)
                    }
                }
            }

            // The posture shape, breathing a couple of dp along each axis.
            val t = phase.value
            shape.reset()
            for (i in 0 until count) {
                val r = reach(i, t)
                val x = center.x + cosA[i] * r
                val y = center.y + sinA[i] * r
                if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
            }
            shape.close()
            drawPath(shape, brush = fill)
            drawPath(shape, outlineColor, style = outlineStroke)

            // Pulsing vertex dots in each check's rating colour, flashing and growing as the beam passes; pending
            // checks have none yet.
            val halo = pulse.value.dp.toPx() * 1.6f
            for (i in 0 until count) {
                if (axes[i].rating == SecurityPosture.Rating.Pending) continue
                val lit = if (turned == null) 0f else sweepHighlight(beamAngle, angles[i])
                val r = reach(i, t)
                val p = Offset(center.x + cosA[i] * r, center.y + sinA[i] * r)
                drawCircle(colors[i].copy(alpha = 0.25f + 0.3f * lit), radius = halo * (1f + 0.5f * lit), center = p)
                drawCircle(surface, radius = dotRing * (1f + 0.3f * lit), center = p)
                drawCircle(colors[i], radius = dot * (1f + 0.6f * lit), center = p)
            }

            // Numbered badges just outside each vertex, matching the list below the chart.
            badges?.forEachIndexed { i, badge ->
                val lit = if (turned == null) 0f else sweepHighlight(beamAngle, angles[i])
                val d = radiusPx + labelGapPx + badgeDiameterPx / 2f
                val at = Offset(center.x + cosA[i] * d, center.y + sinA[i] * d)
                drawCircle(colors[i].copy(alpha = 0.16f + 0.24f * lit), radius = badgeDiameterPx / 2f, center = at)
                drawText(badge, topLeft = Offset(at.x - badge.size.width / 2f, at.y - badge.size.height / 2f))
            }

            // Labels, each lighting up in its rating colour as the beam passes.
            labels?.forEachIndexed { i, (name, value) ->
                val lit = if (turned == null) 0f else sweepHighlight(beamAngle, angles[i])
                if (lit > 0f) {
                    drawRoundRect(
                        colors[i].copy(alpha = 0.12f * lit),
                        topLeft = Offset(boxLeft[i] - labelPad, boxTop[i] - labelPad),
                        size = Size(boxWidth[i] + labelPad * 2, boxHeight[i] + labelPad * 2),
                        cornerRadius = labelCorner
                    )
                    drawText(name, color = lerp(nameColor, colors[i], lit), topLeft = Offset(nameX[i], boxTop[i]))
                } else {
                    drawText(name, topLeft = Offset(nameX[i], boxTop[i]))
                }
                drawText(value, topLeft = Offset(valueX[i], boxTop[i] + name.size.height))
            }
        }
    })
}
