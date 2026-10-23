package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.security.SecurityPosture.Axis
import com.pixel.intelligentsearch.core.system.openSettingsPage
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Test tag on the posture card's advice for the chosen check. */
internal const val POSTURE_ADVICE_TAG = "posture_advice"

/** Test tag on the posture ring. */
internal const val POSTURE_RING_TAG = "posture_ring"

/** Test tag prefix on each check's tile: the prefix and the check's name. */
internal const val POSTURE_TILE_TAG_PREFIX = "posture_tile_"

/**
 * The checks in the order the card shows them, round the ring clockwise from the top and across the tiles: the
 * phone's defences from the hardware up (the Android platform security model's defence in depth), keys kept in
 * hardware first and the user's own screen lock last.
 */
internal val POSTURE_ORDER: List<Axis> = listOf(
    Axis.KeyStorage, Axis.Attestation, Axis.VerifiedBoot, Axis.Bootloader, Axis.PatchLevel, Axis.ScreenLock
)

/** What one check could still add to the overall score, in whole percent: its missing share of the average. */
internal fun scoreGainPercent(score: SecurityPosture.AxisScore, checks: Int): Int =
    if (checks <= 0 || score.rating == SecurityPosture.Rating.Pending) 0
    else ((1f - score.score.coerceIn(0f, 1f)) / checks * 100f).roundToInt()

/**
 * What to improve, most first: every checked check short of full marks, ranked by how much of the overall score it
 * would win back (the most worth doing first, as people struggle to prioritize security advice on their own).
 */
internal fun improvementPlan(axes: List<SecurityPosture.AxisScore>): List<Pair<SecurityPosture.AxisScore, Int>> {
    val checked = axes.count { it.rating != SecurityPosture.Rating.Pending }
    return axes.filter { it.rating != SecurityPosture.Rating.Pending && it.score < FULL_MARKS }
        .map { it to scoreGainPercent(it, checked) }
        .sortedWith(compareByDescending<Pair<SecurityPosture.AxisScore, Int>> { 1f - it.first.score }
            .thenBy { if (it.first.rating == SecurityPosture.Rating.Weak) 0 else 1 })
}

/** The space between the ring's arcs, in degrees. */
internal const val RING_GAP_DEGREES = 9f

/** One flow of the ring's wave along its arcs. */
internal const val RING_FLOW_MILLIS = 2600

/** The degrees each of [count] arcs spans, leaving [RING_GAP_DEGREES] between them. */
internal fun ringSweep(count: Int): Float = 360f / count.coerceAtLeast(1) - RING_GAP_DEGREES

/** Where arc [index] of [count] starts, in degrees clockwise from 12 o'clock (half a gap in, so the gaps are even). */
internal fun ringStart(index: Int, count: Int): Float = index * 360f / count.coerceAtLeast(1) + RING_GAP_DEGREES / 2f

/**
 * The arc under a tap at [degrees] clockwise from 12 o'clock on a ring of [count] arcs: the arc whose span (or the
 * half-gaps either side of it) the tap falls in.
 */
internal fun ringArcAt(degrees: Float, count: Int): Int {
    if (count <= 0) return 0
    val d = ((degrees % 360f) + 360f) % 360f
    return (d / (360f / count)).toInt().coerceIn(0, count - 1)
}

/**
 * How far the wave lifts the ring's line at [along] degrees into an arc, at [phase] (0 to 1) of its flow: a gentle
 * sine that travels round the ring as the phase runs, eased to nothing at both ends of the arc so each arc starts and
 * ends cleanly, as Material's wavy progress indicator does.
 */
internal fun ringWave(along: Float, sweep: Float, phase: Float, wavelengthDegrees: Float = 14f): Float {
    if (sweep <= 0f) return 0f
    val edge = minOf(along, sweep - along).coerceAtLeast(0f)
    val taper = (edge / 6f).coerceAtMost(1f)
    return sin(2f * PI.toFloat() * (along / wavelengthDegrees - phase)) * taper
}

/** The Material shape each check's badge becomes when it is chosen, so each check has its own. */
internal fun postureShape(axis: Axis): String = when (axis) {
    Axis.KeyStorage -> "Cookie4Sided"
    Axis.Attestation -> "Sunny"
    Axis.VerifiedBoot -> "Gem"
    Axis.Bootloader -> "Clover4Leaf"
    Axis.PatchLevel -> "Cookie9Sided"
    Axis.ScreenLock -> "Pentagon"
}

private fun postureIcon(score: SecurityPosture.AxisScore): ImageVector = when (score.axis) {
    Axis.KeyStorage -> Icons.Outlined.Key
    Axis.Attestation -> Icons.Outlined.VerifiedUser
    Axis.VerifiedBoot -> Icons.Outlined.RestartAlt
    Axis.Bootloader -> if (score.value == "Unlocked") Icons.Outlined.LockOpen else Icons.Outlined.Lock
    Axis.PatchLevel -> Icons.Outlined.SystemUpdate
    Axis.ScreenLock -> Icons.Outlined.Password
}

/**
 * The Hardware & Security page's posture card, in Material 3 Expressive's language:
 * - a ring of six arcs, one per check in [POSTURE_ORDER], each filled to its score in its rating's colour along a
 *   gently flowing wave (Material's wavy progress), the rest of the arc left as track, with the overall percentage
 *   and rating at its heart;
 * - a tile per check, its icon in a badge that morphs into that check's own Material shape when chosen;
 * - what to improve, ranked by what it wins back ([improvementPlan]), the chosen check explained with Android's page
 *   to fix it where the phone can.
 * Tapping an arc or a tile chooses that check: its arc thickens and the others step back, its tile lifts. It opens on
 * the check most worth improving. Text is shown as it is, never animated.
 */
@Composable
internal fun SecurityPostureCard(axes: List<SecurityPosture.AxisScore>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val overall = SecurityPosture.overall(axes)
    val percent = overall?.let { (it * 100).roundToInt() }
    val plan = remember(axes) { improvementPlan(axes) }
    var chosen by remember { mutableStateOf<Axis?>(null) }
    val selected = chosen ?: plan.firstOrNull()?.first?.axis
    val ordered = POSTURE_ORDER.mapNotNull { axis -> axes.firstOrNull { it.axis == axis } }
    DiagnosticsCard(
        icon = Icons.Outlined.Shield,
        title = "Security Posture",
        accent = scheme.primary,
        modifier = modifier
    ) {
        PostureRing(
            checks = ordered,
            percent = percent,
            label = SecurityPosture.overallLabel(overall),
            selected = selected,
            onSelect = { chosen = it },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.size(12.dp))
        PostureTiles(ordered, selected, onSelect = { chosen = it })
        ImprovementList(axes, plan, selected, onSelect = { chosen = it })
    }
}

/**
 * The ring: six arcs round a still centre that reads the overall percentage and rating. Each arc fills to its check's
 * score along a wave that keeps flowing round (still with animations off); a check still being checked shows a short
 * wave chasing along its empty arc. The [selected] check's arc thickens and glows on a spring and the others step
 * back. A tap on an arc chooses its check ([onSelect]).
 */
@Composable
private fun PostureRing(
    checks: List<SecurityPosture.AxisScore>,
    percent: Int?,
    label: String,
    selected: Axis?,
    onSelect: (Axis) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled by rememberAnimationsEnabled()
    val flow: State<Float>? = if (animationsEnabled) {
        rememberInfiniteTransition(label = "postureRing").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(RING_FLOW_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "postureRingFlow"
        )
    } else null
    val count = checks.size
    // Each arc's emphasis springs between chosen (1), stepped back (0) and evenly shown (0.5, nothing chosen).
    val emphasis = checks.map { check ->
        animateFloatAsState(
            targetValue = when (selected) {
                null -> 0.5f
                check.axis -> 1f
                else -> 0f
            },
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
            label = "postureArc"
        )
    }
    val currentChecks by rememberUpdatedState(checks)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val track = scheme.surfaceContainerHighest
    val colors = checks.map { ratingColor(it.rating, scheme) }
    val description = remember(checks, percent, label) { SecurityPosture.describe(checks) }
    Box(
        modifier = modifier
            .widthIn(max = 260.dp)
            .fillMaxWidth(0.78f)
            .aspectRatio(1f)
            .testTag(POSTURE_RING_TAG)
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(count) {
                detectTapGestures { at ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = at.x - cx
                    val dy = at.y - cy
                    // Only on the ring itself, not its centre.
                    if (hypot(dx, dy) < size.width * 0.3f) return@detectTapGestures
                    val degrees = (atan2(dx, -dy) * 180f / PI.toFloat() + 360f) % 360f
                    currentChecks.getOrNull(ringArcAt(degrees, count))?.let { currentOnSelect(it.axis) }
                }
            }
            .drawWithCache {
                val stroke = 7.dp.toPx()
                val trackStroke = Stroke(width = stroke * 1.55f, cap = StrokeCap.Round)
                val radius = size.minDimension / 2f - stroke * 2f
                val centre = Offset(size.width / 2f, size.height / 2f)
                val amplitude = 2.6.dp.toPx()
                val sweep = ringSweep(count)
                val wave = Path()
                fun point(degrees: Float, lift: Float): Offset {
                    val a = (degrees - 90f) * PI.toFloat() / 180f
                    val r = radius + lift
                    return Offset(centre.x + cos(a) * r, centre.y + sin(a) * r)
                }
                onDrawBehind {
                    val phase = flow?.value ?: 0f
                    for (i in 0 until count) {
                        val start = ringStart(i, count)
                        val e = emphasis[i].value
                        // The track: the whole arc, a little deeper for the chosen check.
                        drawArc(
                            track, startAngle = start - 90f, sweepAngle = sweep, useCenter = false,
                            topLeft = Offset(centre.x - radius, centre.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                            style = trackStroke, alpha = 0.7f + 0.3f * e
                        )
                        val check = checks[i]
                        val pending = check.rating == SecurityPosture.Rating.Pending
                        val filled = if (pending) sweep * 0.22f else sweep * check.score.coerceIn(0f, 1f)
                        if (filled <= 0.5f) continue
                        // A pending check's short wave chases along its arc; a checked one fills from the arc's start.
                        val from = if (pending) (sweep - filled) * ((phase * 1.3f) % 1f) else 0f
                        wave.rewind()
                        val steps = (filled * 1.6f).toInt().coerceAtLeast(8)
                        for (s in 0..steps) {
                            val along = from + filled * s / steps
                            val p = point(start + along, amplitude * (0.55f + 0.45f * e) * ringWave(along, sweep, phase))
                            if (s == 0) wave.moveTo(p.x, p.y) else wave.lineTo(p.x, p.y)
                        }
                        val width = stroke * (0.75f + 0.55f * e)
                        val alpha = 0.55f + 0.45f * e
                        // The chosen check's arc glows under its line.
                        if (e > 0.6f) drawPath(wave, colors[i], alpha = 0.22f * (e - 0.6f) / 0.4f, style = Stroke(width = width * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(wave, colors[i], alpha = alpha, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = percent?.let { "$it%" } ?: "...",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = scheme.primary)
        }
    }
}

/**
 * A tile per check, two to a row: its icon in a badge, its name and its value. The [selected] check's tile lifts onto
 * the secondary container and its badge morphs from a circle into the check's own Material shape ([postureShape]),
 * turning as it goes, on a spring; it morphs back when another is chosen.
 */
@Composable
private fun PostureTiles(checks: List<SecurityPosture.AxisScore>, selected: Axis?, onSelect: (Axis) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        checks.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { check ->
                    PostureTile(check, check.axis == selected, onSelect = { onSelect(check.axis) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PostureTile(check: SecurityPosture.AxisScore, isSelected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val color = ratingColor(check.rating, scheme)
    val animationsEnabled by rememberAnimationsEnabled()
    val container by animateColorAsState(if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHigh, label = "postureTile")
    val morph = remember { Animatable(if (isSelected) 1f else 0f) }
    LaunchedEffect(isSelected, animationsEnabled) {
        val target = if (isSelected) 1f else 0f
        if (animationsEnabled) morph.animateTo(target, spring(dampingRatio = 0.55f, stiffness = 320f)) else morph.snapTo(target)
    }
    val shapes = remember(check.axis) {
        Morph(MaterialMorphShapes.byName.getValue("Circle"), MaterialMorphShapes.byName.getValue(postureShape(check.axis)))
    }
    val badgeFill by animateColorAsState(if (isSelected) color else color.copy(alpha = 0.16f), label = "postureBadge")
    val iconTint by animateColorAsState(if (isSelected) scheme.surface else color, label = "postureIcon")
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .testTag(POSTURE_TILE_TAG_PREFIX + check.axis.name)
            .clip(shape)
            .background(container)
            .clearAndSetSemantics {
                contentDescription = "${check.axis.label}: ${check.value}"
                role = Role.Button
                selected = isSelected
                onClick(label = "Explain ${check.axis.label}") { onSelect(); true }
            }
            .bouncyClickable(shape = shape, onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .drawBehind {
                    val m = morph.value
                    val path = android.graphics.Path()
                    shapes.toPath(m.coerceIn(0f, 1f), path)
                    path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
                    rotate(degrees = 45f * m) { drawPath(path.asComposePath(), badgeFill) }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(postureIcon(check), contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = check.axis.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(text = check.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

/**
 * Under the tiles: what to improve, most first, each with what it would add to the overall score; the [selected]
 * check explained in full with Android's page to fix it where the phone can. With nothing to improve, says so.
 */
@Composable
private fun ImprovementList(
    axes: List<SecurityPosture.AxisScore>,
    plan: List<Pair<SecurityPosture.AxisScore, Int>>,
    selected: Axis?,
    onSelect: (Axis) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag(POSTURE_ADVICE_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val explained = axes.firstOrNull { it.axis == selected }
        if (plan.isEmpty() && explained == null) {
            Text(
                text = if (axes.any { it.rating == SecurityPosture.Rating.Pending }) "Checking the rest..." else "Every check is at full strength. Nothing to improve.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            return@Column
        }
        if (explained != null) {
            val advice = postureAdvice(explained)
            val color = ratingColor(explained.rating, scheme)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${explained.axis.label} · ${explained.value}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(text = advice.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                advice.fix?.let { fix ->
                    FilledTonalButton(onClick = { openSettingsPage(context, *fix.intents().toTypedArray()) }) {
                        Text(fix.label)
                    }
                }
            }
        }
        if (plan.isNotEmpty()) {
            Text("To Improve", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            plan.forEach { (score, gain) ->
                val color = ratingColor(score.rating, scheme)
                val isSelected = score.axis == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) color.copy(alpha = 0.12f) else Color.Transparent)
                        .bouncyClickable(shape = RoundedCornerShape(16.dp)) { onSelect(score.axis) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${score.axis.label} · ${score.value}",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (gain > 0) {
                        Text("+$gain%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
                    }
                }
            }
        }
    }
}

/** The colour that marks a check's rating, on its arc, its tile and its value. */
internal fun ratingColor(rating: SecurityPosture.Rating, scheme: ColorScheme): Color = when (rating) {
    SecurityPosture.Rating.Good -> scheme.primary
    SecurityPosture.Rating.Fair -> scheme.tertiary
    SecurityPosture.Rating.Weak -> scheme.error
    SecurityPosture.Rating.Pending -> scheme.outline
}
