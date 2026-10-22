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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** The Hardware & Security page's posture card: an overall rating over the radar chart of each check. */
@Composable
internal fun SecurityPostureCard(axes: List<SecurityPosture.AxisScore>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val overall = SecurityPosture.overall(axes)
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
            DiagnosticsStatusChip(
                text = if (overall == null) label else "$label · ${(overall * 100).roundToInt()}%",
                container = container,
                content = content
            )
        }
    ) {
        SecurityRadarChart(axes)
    }
}

/** The colour that marks an axis's rating, on the chart and in its value label. */
internal fun ratingColor(rating: SecurityPosture.Rating, scheme: ColorScheme): Color = when (rating) {
    SecurityPosture.Rating.Good -> scheme.primary
    SecurityPosture.Rating.Fair -> scheme.tertiary
    SecurityPosture.Rating.Weak -> scheme.error
    SecurityPosture.Rating.Pending -> scheme.outline
}

private val MaxRadius = 84.dp
private val MinInlineRadius = 44.dp
private val LabelGap = 6.dp
private val ChartPadding = 8.dp

/**
 * A radar ("spider") chart of the security checks, in the battery page's visual language: dashed grid rings, a soft
 * Material You fill under a crisp outline, and pulsing vertex dots. Each axis springs out from the centre when the
 * card appears (and again when a pending check resolves), then the shape breathes gently; with animations removed
 * it is drawn still.
 *
 * Axis labels sit around the chart when they fit; when the text is too wide for the space (large fonts, narrow or
 * zoomed screens) the chart is drawn alone and the labels move into a list below it.
 */
@Composable
internal fun SecurityRadarChart(axes: List<SecurityPosture.AxisScore>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled by rememberAnimationsEnabled()
    val count = axes.size

    val animated = remember(count) { List(count) { Animatable(0f) } }
    axes.forEachIndexed { i, axis ->
        key(i) {
            LaunchedEffect(axis.score, animationsEnabled) {
                if (!animationsEnabled) {
                    animated[i].snapTo(axis.score)
                } else {
                    // On entry the axes draw out one after another.
                    if (animated[i].value == 0f) delay(i * 60L)
                    animated[i].animateTo(axis.score, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
                }
            }
        }
    }

    val phase: State<Float>
    val pulse: State<Float>
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
    } else {
        phase = remember { mutableFloatStateOf(0f) }
        pulse = remember { mutableFloatStateOf(4f) }
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
                axes = axes, animated = animated, phase = phase, pulse = pulse, angles = angles,
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
                    axes = axes, animated = animated, phase = phase, pulse = pulse, angles = angles,
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

@Composable
private fun RadarCanvas(
    axes: List<SecurityPosture.AxisScore>,
    animated: List<Animatable<Float, *>>,
    phase: State<Float>,
    pulse: State<Float>,
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
    val gridDash = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) }
    val spokeDash = remember { PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) }
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        fun point(i: Int, r: Float) = Offset(center.x + cos(angles[i]) * r, center.y + sin(angles[i]) * r)
        fun ring(fraction: Float) = Path().apply {
            angles.indices.forEach { i ->
                val p = point(i, radiusPx * fraction)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }

        // Grid: dashed inner rings, a fine solid outer ring and dashed spokes.
        for (level in 1..3) {
            drawPath(ring(level / 4f), grid.copy(alpha = 0.3f), style = Stroke(width = 0.8.dp.toPx(), pathEffect = gridDash))
        }
        drawPath(ring(1f), grid.copy(alpha = 0.55f), style = Stroke(width = 1.dp.toPx()))
        angles.indices.forEach { i ->
            drawLine(grid.copy(alpha = 0.3f), center, point(i, radiusPx), strokeWidth = 0.8.dp.toPx(), pathEffect = spokeDash)
        }

        // The posture shape, breathing a couple of dp along each axis.
        val breathePx = 2.dp.toPx()
        val t = phase.value
        val points = axes.indices.map { i ->
            val r = (animated[i].value * radiusPx + if (animated[i].value > 0f) sin(t + i * 1.4f) * breathePx else 0f)
                .coerceIn(0f, radiusPx + breathePx * 2)
            point(i, r)
        }
        val shape = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }
        drawPath(
            shape,
            brush = Brush.radialGradient(
                listOf(primary.copy(alpha = 0.08f), primary.copy(alpha = 0.32f)),
                center = center,
                radius = radiusPx.coerceAtLeast(1f)
            )
        )
        drawPath(
            shape,
            primary.copy(alpha = 0.85f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Pulsing vertex dots in each check's rating colour; pending checks have none yet.
        val halo = pulse.value.dp.toPx() * 1.6f
        axes.forEachIndexed { i, axis ->
            if (axis.rating == SecurityPosture.Rating.Pending) return@forEachIndexed
            val color = ratingColor(axis.rating, scheme)
            drawCircle(color.copy(alpha = 0.25f), radius = halo, center = points[i])
            drawCircle(surface, radius = 3.5.dp.toPx(), center = points[i])
            drawCircle(color, radius = 2.dp.toPx(), center = points[i])
        }

        // Numbered badges just outside each vertex, matching the list below the chart.
        badges?.forEachIndexed { i, badge ->
            val at = point(i, radiusPx + labelGapPx + badgeDiameterPx / 2f)
            drawCircle(ratingColor(axes[i].rating, scheme).copy(alpha = 0.16f), radius = badgeDiameterPx / 2f, center = at)
            drawText(badge, topLeft = Offset(at.x - badge.size.width / 2f, at.y - badge.size.height / 2f))
        }

        // Labels beside each vertex: left of it on the left side, right of it on the right, centred above or below.
        labels?.forEachIndexed { i, (name, value) ->
            val c = cos(angles[i])
            val s = sin(angles[i])
            val anchor = point(i, radiusPx + labelGapPx)
            val boxWidth = maxOf(name.size.width, value.size.width).toFloat()
            val boxHeight = (name.size.height + value.size.height).toFloat()
            val left = when {
                c > 0.05f -> anchor.x
                c < -0.05f -> anchor.x - boxWidth
                else -> anchor.x - boxWidth / 2f
            }
            val top = when {
                abs(c) > 0.05f -> anchor.y - boxHeight / 2f
                s < 0f -> anchor.y - boxHeight
                else -> anchor.y
            }
            fun lineX(line: TextLayoutResult) = when {
                c > 0.05f -> left
                c < -0.05f -> left + boxWidth - line.size.width
                else -> left + (boxWidth - line.size.width) / 2f
            }
            drawText(name, topLeft = Offset(lineX(name), top))
            drawText(value, topLeft = Offset(lineX(value), top + name.size.height))
        }
    }
}
