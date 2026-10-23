package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.system.openSettingsPage
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Test tag on the posture card's advice for the chosen check. */
internal const val POSTURE_ADVICE_TAG = "posture_advice"

/** Test tag on the radar chart. */
internal const val POSTURE_RADAR_TAG = "posture_radar"

/**
 * The Hardware & Security page's posture card: the overall rating, the radar chart of each check, and under it what
 * the chosen check means and how to raise it ([postureAdvice]), with a button to Android's own page where the phone
 * can do something about it (a screen lock, a security update). It opens on the check that most needs attention
 * ([weakestAxis]); tapping a check on the chart explains that one instead. Text is shown as it is, never animated.
 */
@Composable
internal fun SecurityPostureCard(axes: List<SecurityPosture.AxisScore>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val overall = SecurityPosture.overall(axes)
    val percent = overall?.let { (it * 100).roundToInt() }
    // The user's choice wins; until they make one, the card follows whatever check needs attention most.
    var chosen by remember { mutableStateOf<SecurityPosture.Axis?>(null) }
    val selected = chosen ?: weakestAxis(axes)
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
            DiagnosticsStatusChip(text = if (percent == null) label else "$label · $percent%", container = container, content = content)
        }
    ) {
        SecurityRadarChart(axes, selected = selected, onSelect = { chosen = it })
        PostureAdviceRow(axes, selected)
    }
}

/**
 * What the [selected] check means and how to raise it, with a button to Android's page for it when the phone can do
 * something about it; when every check is at full marks, says so.
 */
@Composable
private fun PostureAdviceRow(axes: List<SecurityPosture.AxisScore>, selected: SecurityPosture.Axis?) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val score = axes.firstOrNull { it.axis == selected }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .testTag(POSTURE_ADVICE_TAG),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (score == null) {
            Text(
                text = if (axes.any { it.rating == SecurityPosture.Rating.Pending }) "Checking the rest..." else "Every check is at full marks.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            return@Column
        }
        val advice = postureAdvice(score)
        val color = ratingColor(score.rating, scheme)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(
                text = "${score.axis.label} · ${score.value}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Text(text = advice.text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
        advice.fix?.let { fix ->
            FilledTonalButton(onClick = { openSettingsPage(context, *fix.intents().toTypedArray()) }) {
                Text(fix.label)
            }
        }
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

/** One lap of the light round the posture shape. */
internal const val TRACE_LAP_MILLIS = 4200

/** One run of the lights along a check's gap, from its point out to full marks. */
internal const val GAP_RUN_MILLIS = 1300

/** How much of the shape's edge the travelling light's fading trail covers. */
internal const val TRACE_TAIL = 0.2f

/** How much of a lap a check's ping takes to spread and fade after the light passes it. */
internal const val PING_SPAN = 0.22f

/**
 * How far (0 to 1) a check's ping has spread when the light, at [trace] of its lap, has passed the check's point at
 * [vertex] of the lap; null when it isn't pinging (the light hasn't reached it, or the ping has faded).
 */
internal fun vertexPing(trace: Float, vertex: Float, span: Float = PING_SPAN): Float? {
    val since = ((trace - vertex) % 1f + 1f) % 1f
    return if (since < span) since / span else null
}

/** How bright the travelling light's trail is [behind] the light (as a share of the lap): full at the light, gone at [tail]. */
internal fun traceTrail(behind: Float, tail: Float = TRACE_TAIL): Float {
    if (behind < 0f || behind > tail) return 0f
    val f = 1f - behind / tail
    return f * f
}

/** The check under a tap at [angle] (radians, as atan2 gives it) on a chart whose checks point along [angles]. */
internal fun axisAtAngle(angle: Float, angles: List<Float>): Int {
    val turn = (2 * PI).toFloat()
    return angles.indices.minByOrNull { i ->
        val d = ((angle - angles[i]) % turn + turn) % turn
        minOf(d, turn - d)
    } ?: 0
}

/**
 * A radar ("spider") chart of the security checks: dashed grid rings, a soft Material You fill under a crisp outline,
 * and a dot at each check in its rating's colour. Its motion says where the posture stands:
 * - every check short of full marks shows its gap: lights run out from its point to where full marks would be, and
 *   that spot pulses, so the eye goes straight to what's missing and how much;
 * - a light travels round the edge of the shape, and each check pings in its colour as the light passes it;
 * - the [selected] check (the one explained under the chart) has its spoke lit out to the edge.
 * Tapping near a check selects it ([onSelect]); TalkBack offers the same as actions. With animations off it is drawn
 * still, gaps shown as dotted lines. Nothing written on it moves.
 *
 * Axis labels sit around the chart when they fit; when the text is too wide for the space (large fonts, narrow or
 * zoomed screens) the chart is drawn alone and the labels move into a list below it.
 */
@Composable
internal fun SecurityRadarChart(
    axes: List<SecurityPosture.AxisScore>,
    modifier: Modifier = Modifier,
    selected: SecurityPosture.Axis? = null,
    onSelect: (SecurityPosture.Axis) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled by rememberAnimationsEnabled()
    val count = axes.size

    val trace: State<Float>?
    val march: State<Float>
    val pulse: State<Float>
    if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "posture")
        trace = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(TRACE_LAP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "postureTrace"
        )
        march = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(GAP_RUN_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "postureGapRun"
        )
        pulse = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(GAP_RUN_MILLIS / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "postureTargetPulse"
        )
    } else {
        trace = null
        march = remember { mutableFloatStateOf(0f) }
        pulse = remember { mutableFloatStateOf(0.5f) }
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
    val currentAxes by rememberUpdatedState(axes)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val selectedIndex = axes.indexOfFirst { it.axis == selected }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .testTag(POSTURE_RADAR_TAG)
            .clearAndSetSemantics {
                contentDescription = description
                customActions = currentAxes.map { score ->
                    CustomAccessibilityAction("Explain ${score.axis.label}") { currentOnSelect(score.axis); true }
                }
            }
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
        // A tap selects the check whose spoke is nearest the tap, measured from the chart's centre.
        val tapToSelect = Modifier.pointerInput(angles) {
            detectTapGestures { at ->
                val angle = atan2(at.y - size.height / 2f, at.x - size.width / 2f)
                currentAxes.getOrNull(axisAtAngle(angle, angles))?.let { currentOnSelect(it.axis) }
            }
        }

        if (inline) {
            val chartHeight = with(density) { (2 * (inlineRadius + gapPx + labelHeight)).toDp() + ChartPadding * 2 }
            RadarCanvas(
                axes = axes, trace = trace, march = march, pulse = pulse, selectedIndex = selectedIndex, angles = angles,
                radiusPx = inlineRadius, labels = labels, badges = null, badgeDiameterPx = 0f, labelGapPx = gapPx,
                scheme = scheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .then(tapToSelect)
            )
        } else {
            val compactRadius = with(density) {
                minOf(MaxRadius.toPx(), halfWidthPx - gapPx - badgeDiameterPx).coerceAtLeast(24.dp.toPx())
            }
            val badgeSize = with(density) { badgeDiameterPx.toDp() }
            Column(modifier = Modifier.padding(bottom = ChartPadding + 4.dp)) {
                RadarCanvas(
                    axes = axes, trace = trace, march = march, pulse = pulse, selectedIndex = selectedIndex, angles = angles,
                    radiusPx = compactRadius, labels = null, badges = badges, badgeDiameterPx = badgeDiameterPx,
                    labelGapPx = gapPx, scheme = scheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (2 * (compactRadius + gapPx + badgeDiameterPx)).toDp() } + ChartPadding * 2)
                        .then(tapToSelect)
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
        Box(
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

/** How many lights run along a check's gap at once. */
private const val GAP_LIGHTS = 3

/** How many short pieces the travelling light's trail is drawn in, each a little fainter. */
private const val TRAIL_PIECES = 14

/**
 * The chart itself. The grid, the shape, its edge's length and every check's place along it, the gaps and the label
 * places are worked out once per size in [drawWithCache]; a frame only reads [trace], [march] and [pulse] and draws,
 * reusing one path for the trail.
 */
@Composable
private fun RadarCanvas(
    axes: List<SecurityPosture.AxisScore>,
    trace: State<Float>?,
    march: State<Float>,
    pulse: State<Float>,
    selectedIndex: Int,
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
    val gapDash = remember { PathEffect.dashPathEffect(floatArrayOf(2f, 5f)) }
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
        val pingStroke = Stroke(width = 1.5.dp.toPx())
        val targetStroke = Stroke(width = 1.5.dp.toPx())
        val spokeWidth = 0.8.dp.toPx()
        val gridFaint = grid.copy(alpha = 0.3f)
        val gridOuter = grid.copy(alpha = 0.55f)
        val outlineColor = primary.copy(alpha = 0.85f)
        val fill = Brush.radialGradient(
            listOf(primary.copy(alpha = 0.08f), primary.copy(alpha = 0.32f)),
            center = center,
            radius = radiusPx.coerceAtLeast(1f)
        )
        val colors = List(count) { ratingColor(axes[it].rating, scheme) }
        val dotRing = 3.5.dp.toPx()
        val dot = 2.dp.toPx()
        val lightRadius = 2.2.dp.toPx()
        val labelPad = 3.dp.toPx()
        val labelCorner = CornerRadius(6.dp.toPx())
        val trailStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val glowStroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)

        // Each check's point (pending checks sit at the centre) and where full marks would be.
        val points = List(count) { i ->
            val r = radiusPx * axes[i].score.coerceIn(0f, 1f)
            Offset(center.x + cosA[i] * r, center.y + sinA[i] * r)
        }
        val fullPoints = List(count) { i -> Offset(center.x + cosA[i] * radiusPx, center.y + sinA[i] * radiusPx) }
        val shape = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }
        // The edge's length, and where along it (as a share of a lap) each check's point lies.
        val measure = PathMeasure().apply { setPath(shape, false) }
        val perimeter = measure.length
        val vertexAt = FloatArray(count)
        run {
            var along = 0f
            for (i in 0 until count) {
                vertexAt[i] = if (perimeter > 0f) along / perimeter else 0f
                val next = points[(i + 1) % count]
                along += (next - points[i]).getDistance()
            }
        }
        val gaps = BooleanArray(count) { axes[it].rating != SecurityPosture.Rating.Pending && axes[it].score < FULL_MARKS }
        val trail = Path()

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

        onDrawBehind {
            // Grid: dashed inner rings, a fine solid outer ring and dashed spokes; the chosen check's spoke lit.
            for (r in innerRings) drawPath(r, gridFaint, style = gridStroke)
            drawPath(outerRing, gridOuter, style = outerStroke)
            for (i in 0 until count) {
                if (i == selectedIndex) {
                    drawLine(colors[i].copy(alpha = 0.55f), center, fullPoints[i], strokeWidth = spokeWidth * 2.5f, cap = StrokeCap.Round)
                } else {
                    drawLine(gridFaint, center, fullPoints[i], strokeWidth = spokeWidth, pathEffect = spokeDash)
                }
            }

            // Each gap: a faint dotted line from the check out to full marks, lights running outward along it, and
            // the full-marks spot pulsing.
            val m = march.value
            val pl = pulse.value
            for (i in 0 until count) {
                if (!gaps[i]) continue
                val from = points[i]
                val to = fullPoints[i]
                drawLine(colors[i].copy(alpha = 0.5f), from, to, strokeWidth = spokeWidth * 1.6f, pathEffect = gapDash, cap = StrokeCap.Round)
                if (trace != null) {
                    for (k in 0 until GAP_LIGHTS) {
                        val f = (k + m) / GAP_LIGHTS
                        val at = Offset(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f)
                        // Rising as it leaves the check, fading as it reaches full marks.
                        val alpha = sin(PI.toFloat() * f)
                        drawCircle(colors[i], radius = lightRadius * (0.7f + 0.3f * f), center = at, alpha = alpha * 0.9f)
                    }
                }
                drawCircle(colors[i], radius = dotRing * (1.2f + 0.5f * pl), center = to, alpha = 0.35f + 0.4f * pl, style = targetStroke)
            }

            // The posture shape.
            drawPath(shape, brush = fill)
            drawPath(shape, outlineColor, style = outlineStroke)

            // The light travelling round the edge, its trail fading behind it.
            val t = trace?.value
            if (t != null && perimeter > 0f) {
                val step = TRACE_TAIL / TRAIL_PIECES
                for (j in 0 until TRAIL_PIECES) {
                    val behind = j * step
                    val alpha = traceTrail(behind + step / 2f)
                    if (alpha <= 0f) continue
                    var end = (t - behind) * perimeter
                    var start = end - step * perimeter
                    if (end < 0f) {
                        end += perimeter
                        start += perimeter
                    }
                    trail.reset()
                    if (start < 0f) {
                        measure.getSegment(start + perimeter, perimeter, trail, true)
                        measure.getSegment(0f, end, trail, true)
                    } else {
                        measure.getSegment(start, end, trail, true)
                    }
                    drawPath(trail, primary, alpha = alpha * 0.25f, style = glowStroke)
                    drawPath(trail, primary, alpha = alpha, style = trailStroke)
                }
                drawCircle(primary, radius = lightRadius * 1.3f, center = measure.getPosition(t * perimeter))
            }

            // A dot at each check in its rating colour, ringed while it pings after the light passes; the chosen
            // check's dot a little larger. Pending checks have none yet.
            for (i in 0 until count) {
                if (axes[i].rating == SecurityPosture.Rating.Pending) continue
                val p = points[i]
                val ping = if (t == null) null else vertexPing(t, vertexAt[i])
                if (ping != null) {
                    val fade = 1f - ping
                    drawCircle(colors[i], radius = dotRing * (1.4f + 3f * ping), center = p, alpha = 0.55f * fade * fade, style = pingStroke)
                }
                val grow = if (i == selectedIndex) 1.35f else 1f
                drawCircle(colors[i].copy(alpha = 0.25f), radius = dotRing * 1.9f * grow, center = p)
                drawCircle(surface, radius = dotRing * grow, center = p)
                drawCircle(colors[i], radius = dot * grow, center = p)
            }

            // Numbered badges just outside each vertex, matching the list below the chart.
            badges?.forEachIndexed { i, badge ->
                val d = radiusPx + labelGapPx + badgeDiameterPx / 2f
                val at = Offset(center.x + cosA[i] * d, center.y + sinA[i] * d)
                drawCircle(colors[i].copy(alpha = if (i == selectedIndex) 0.34f else 0.16f), radius = badgeDiameterPx / 2f, center = at)
                drawText(badge, topLeft = Offset(at.x - badge.size.width / 2f, at.y - badge.size.height / 2f))
            }

            // Labels, still; the chosen check's sits on a soft tint of its colour.
            labels?.forEachIndexed { i, (name, value) ->
                if (i == selectedIndex) {
                    drawRoundRect(
                        colors[i].copy(alpha = 0.14f),
                        topLeft = Offset(boxLeft[i] - labelPad, boxTop[i] - labelPad),
                        size = Size(boxWidth[i] + labelPad * 2, boxHeight[i] + labelPad * 2),
                        cornerRadius = labelCorner
                    )
                }
                drawText(name, topLeft = Offset(nameX[i], boxTop[i]))
                drawText(value, topLeft = Offset(valueX[i], boxTop[i] + name.size.height))
            }
        }
    })
}
