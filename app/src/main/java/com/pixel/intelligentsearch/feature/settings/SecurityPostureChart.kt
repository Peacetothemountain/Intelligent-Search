package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.security.SecurityPosture.Axis
import com.pixel.intelligentsearch.core.system.openSettingsPage
import com.pixel.intelligentsearch.core.ui.geminiPlateIdleFlow
import com.pixel.intelligentsearch.core.ui.materialYouGeminiPlate
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Test tag on the posture card's advice for the chosen check. */
internal const val POSTURE_ADVICE_TAG = "posture_advice"

/** Test tag on the posture radar. */
internal const val POSTURE_RADAR_TAG = "posture_radar"

/** Test tag prefix on each check's tile: the prefix and the check's name. */
internal const val POSTURE_TILE_TAG_PREFIX = "posture_tile_"

/**
 * The checks in the order the card shows them, round the radar clockwise from the top and across the tiles: the
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

/** One turn of the radar's beam. */
internal const val RADAR_SWEEP_MILLIS = 4200

/** How long after the previous check each check springs out, on entry and on a re-scan. */
internal const val RADAR_ENTRY_STAGGER_MILLIS = 70L

/** How far a check still being checked reaches out, so the shape stays whole while it waits. */
internal const val RADAR_PENDING_REACH = 0.18f

/** How far behind the beam (radians) a check it has passed settles back; also the beam's visible tail. */
internal const val BEAM_TRAIL = 1.6f

/** How far ahead of the beam (radians) a check starts to light, so it brightens as the beam reaches it. */
private const val BEAM_LEAD = 0.12f

/** Where check [index] of [count] sits, in radians clockwise from 3 o'clock: the first at 12, the rest clockwise. */
internal fun radarAngle(index: Int, count: Int): Float = (-PI / 2 + index * 2 * PI / count.coerceAtLeast(1)).toFloat()

/** The check nearest a tap at [degrees] clockwise from 12 o'clock, on a radar of [count] checks. */
internal fun radarAxisAt(degrees: Float, count: Int): Int {
    if (count <= 0) return 0
    val d = ((degrees % 360f) + 360f) % 360f
    return (d / (360f / count)).roundToInt() % count
}

/**
 * How lit a check at [axisAngle] is by the beam at [beamAngle] (both radians, any number of turns): 1 with the beam
 * on it, easing back to 0 over [BEAM_TRAIL] as it moves on, rising over a short [BEAM_LEAD] just before it arrives.
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
 * The radar's shape into [path]: a closed curve through every one of [points], rounded through each (a Catmull-Rom
 * spline drawn as cubic Beziers), so it reads as one soft Material shape rather than a hard polygon.
 */
internal fun radarBlob(points: List<Offset>, path: Path) {
    path.reset()
    val n = points.size
    if (n < 3) return
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y
        )
    }
    path.close()
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
 * - a live radar of the six checks in [POSTURE_ORDER]: tonal rings, a soft rounded shape reaching out to each check's
 *   score, and at each point the check's own badge, which a turning beam lights as it passes ([PostureRadar]);
 * - a tile per check, its icon in a badge that morphs into that check's own Material shape when chosen;
 * - what to improve, ranked by what it wins back ([improvementPlan]), the chosen check explained with Android's page
 *   to fix it where the phone can.
 * The overall rating and percentage sit still in the header's chip. Tapping a point on the radar or a tile chooses
 * that check; it opens on the check most worth improving. Text is shown as it is, never animated.
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
        modifier = modifier,
        trailing = {
            val (container, content) = when {
                overall == null -> scheme.surfaceVariant to scheme.onSurfaceVariant
                overall >= 0.65f -> scheme.primaryContainer to scheme.onPrimaryContainer
                overall >= 0.4f -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                else -> scheme.errorContainer to scheme.onErrorContainer
            }
            val label = SecurityPosture.overallLabel(overall)
            DiagnosticsStatusChip(text = if (percent == null) label else "$label · $percent%", container = container, content = content)
        }
    ) {
        PostureRadar(checks = ordered, selected = selected, onSelect = { chosen = it })
        Spacer(Modifier.size(12.dp))
        PostureTiles(ordered, selected, onSelect = { chosen = it })
        ImprovementList(axes, plan, selected, onSelect = { chosen = it })
    }
}

/**
 * The radar, on the overlay search bar's plate. Behind it, fine rings of the plate's light and a spoke per check. Its shape reaches out along each spoke to that check's
 * score, rounded through every point and breathing a little, and at each point sits the check's badge: its icon in
 * a circle that morphs towards the check's own Material shape ([postureShape]). On entry, and on a tap on the centre
 * to re-scan, the checks spring out one after another. A beam turns round it, and each badge it passes pops and
 * briefly takes its shape. The [selected] check's badge grows into its shape in solid colour, turning slowly with a
 * soft halo, and its spoke is drawn in its colour; a tap near a check chooses it ([onSelect]). With animations off it
 * is drawn finished and still, with no beam.
 */
@Composable
private fun PostureRadar(
    checks: List<SecurityPosture.AxisScore>,
    selected: Axis?,
    onSelect: (Axis) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled by rememberAnimationsEnabled()
    val count = checks.size
    val reach = remember(count) { List(count) { Animatable(0f) } }
    checks.forEachIndexed { i, check ->
        key(i) {
            LaunchedEffect(radarReach(check), animationsEnabled) {
                if (!animationsEnabled) {
                    reach[i].snapTo(radarReach(check))
                } else {
                    // On entry the checks spring out one after another.
                    if (reach[i].value == 0f) delay(i * RADAR_ENTRY_STAGGER_MILLIS)
                    reach[i].animateTo(radarReach(check), RadarSpring)
                }
            }
        }
    }
    val emphasis = checks.map { check ->
        animateFloatAsState(
            targetValue = if (check.axis == selected) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 380f),
            label = "radarEmphasis"
        )
    }

    // A tap on the centre re-scans: every check falls back in and springs out again, one after another.
    val scope = rememberCoroutineScope()
    val currentChecks by rememberUpdatedState(checks)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val rescan: () -> Unit = remember(reach) {
        {
            currentChecks.forEachIndexed { i, check ->
                scope.launch {
                    reach.getOrNull(i)?.let { anim ->
                        anim.snapTo(0f)
                        delay(i * RADAR_ENTRY_STAGGER_MILLIS)
                        anim.animateTo(radarReach(check), RadarSpring)
                    }
                }
            }
        }
    }

    val beam: State<Float>?
    val breath: State<Float>?
    val spin: State<Float>?
    if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "postureRadar")
        val turn = (2 * PI).toFloat()
        beam = transition.animateFloat(0f, turn, infiniteRepeatable(tween(RADAR_SWEEP_MILLIS, easing = LinearEasing)), label = "radarBeam")
        breath = transition.animateFloat(0f, turn, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "radarBreath")
        spin = transition.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "radarSpin")
    } else {
        beam = null
        breath = null
        spin = null
    }

    val icons = checks.map { rememberVectorPainter(postureIcon(it)) }
    val axesShown = checks.map { it.axis }
    val shapes = remember(axesShown) {
        axesShown.map { Morph(MaterialMorphShapes.byName.getValue("Circle"), MaterialMorphShapes.byName.getValue(postureShape(it))) }
    }
    val colors = checks.map { ratingColor(it.rating, scheme) }
    val description = remember(checks) { SecurityPosture.describe(checks) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RADAR_PLATE_RADIUS))
            // The overlay search bar's own plate: deep Material You interior, light pooling along the bottom edge,
            // a soft rim glow, and that bottom light gently flowing.
            .materialYouGeminiPlate(cornerRadius = RADAR_PLATE_RADIUS)
            .geminiPlateIdleFlow(idle = true)
            .testTag(POSTURE_RADAR_TAG)
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(count, animationsEnabled) {
                detectTapGestures { at ->
                    val dx = at.x - size.width / 2f
                    val dy = at.y - size.height / 2f
                    if (hypot(dx, dy) < size.height * 0.12f) {
                        if (animationsEnabled) rescan()
                        return@detectTapGestures
                    }
                    val degrees = (atan2(dx, -dy) * 180f / PI.toFloat() + 360f) % 360f
                    currentChecks.getOrNull(radarAxisAt(degrees, count))?.let { currentOnSelect(it.axis) }
                }
            }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Spacer(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .drawWithCache {
                    val badge = 30.dp.toPx()
                    val radius = size.minDimension / 2f - badge * 0.75f
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val angles = FloatArray(count) { radarAngle(it, count) }
                    val cosA = FloatArray(count) { cos(angles[it]) }
                    val sinA = FloatArray(count) { sin(angles[it]) }
                    val ringColor = scheme.primary.copy(alpha = 0.16f)
                    val outerRingColor = scheme.primary.copy(alpha = 0.32f)
                    val ringStroke = Stroke(width = 1.dp.toPx())
                    val spokeColor = scheme.primary.copy(alpha = 0.12f)
                    val spokeWidth = 1.dp.toPx()
                    val chosenSpoke = 4.dp.toPx()
                    val edgeWidth = 2.dp.toPx()
                    val hub = 4.dp.toPx()
                    val primary = scheme.primary
                    val fill = Brush.radialGradient(
                        listOf(primary.copy(alpha = 0f), primary.copy(alpha = 0.08f), primary.copy(alpha = 0.34f)),
                        center = centre,
                        radius = radius.coerceAtLeast(1f)
                    )
                    // Clear up to the tail, then rising to the leading edge at 0 degrees; turned to the beam each frame.
                    val tail = (BEAM_TRAIL / (2 * PI)).toFloat()
                    val beamBrush = Brush.sweepGradient(
                        0f to primary.copy(alpha = 0f),
                        (1f - tail) to primary.copy(alpha = 0f),
                        1f to primary.copy(alpha = 0.26f),
                        center = centre
                    )
                    val outline = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val ripple = Stroke(width = 2.dp.toPx())
                    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(centre, radius)) }
                    val blob = Path()
                    val points = MutableList(count) { centre }
                    val badgePath = android.graphics.Path()
                    val matrix = android.graphics.Matrix()
                    val breathePx = 2.5.dp.toPx()
                    val iconSize = 16.dp.toPx()
                    val tints = colors.map { ColorFilter.tint(it) }
                    val onTint = ColorFilter.tint(scheme.surface)
                    val badgeBase = radarBadgeBase(scheme)
                    onDrawBehind {
                        // Fine rings of the plate's light, the outer one brightest, and a spoke per check.
                        for (k in 1..4) drawCircle(if (k == 4) outerRingColor else ringColor, radius * k / 4f, centre, style = ringStroke)
                        for (i in 0 until count) {
                            drawLine(spokeColor, centre, Offset(centre.x + cosA[i] * radius, centre.y + sinA[i] * radius), spokeWidth, StrokeCap.Round)
                        }

                        // The beam and, once a turn, a ripple from the centre, inside the outer ring.
                        val turned = beam?.value
                        val beamAngle = if (turned == null) 0f else turned - (PI / 2).toFloat()
                        if (turned != null) {
                            clipPath(clip) {
                                rotate(degrees = beamAngle * 180f / PI.toFloat(), pivot = centre) {
                                    drawCircle(beamBrush, radius, centre)
                                    drawLine(primary.copy(alpha = 0.5f), centre, Offset(centre.x + radius, centre.y), edgeWidth, StrokeCap.Round)
                                }
                                val spread = turned / (2f * PI.toFloat()) / 0.6f
                                if (spread < 1f) {
                                    val fade = 1f - spread
                                    drawCircle(primary, radius * spread, centre, alpha = 0.3f * fade * fade, style = ripple)
                                }
                            }
                        }

                        // Where each check reaches this frame, breathing a little.
                        val t = breath?.value ?: 0f
                        for (i in 0 until count) {
                            val r = reach[i].value * radius + if (breath != null && reach[i].value > 0f) sin(t + i * 1.3f) * breathePx else 0f
                            points[i] = Offset(centre.x + cosA[i] * r, centre.y + sinA[i] * r)
                        }
                        // The chosen check's spoke in its colour.
                        for (i in 0 until count) {
                            val e = emphasis[i].value
                            if (e > 0.01f) drawLine(colors[i].copy(alpha = 0.8f * e.coerceAtMost(1f)), centre, points[i], chosenSpoke * e, StrokeCap.Round)
                        }

                        // The posture shape, rounded through every check.
                        radarBlob(points, blob)
                        drawPath(blob, fill)
                        drawPath(blob, primary.copy(alpha = 0.9f), style = outline)
                        drawCircle(primary, hub, centre)

                        // Each check's badge at its point.
                        for (i in 0 until count) {
                            val e = emphasis[i].value
                            val lit = if (turned == null) 0f else sweepHighlight(beamAngle, angles[i])
                            val pending = checks[i].rating == SecurityPosture.Rating.Pending
                            val s = badge * (0.82f + 0.3f * e + 0.16f * lit)
                            val p = points[i]
                            if (e > 0.01f) {
                                val pulse = if (breath == null) 0f else (sin(t * 2f) + 1f) / 2f
                                drawCircle(colors[i], s * (0.68f + 0.1f * pulse), p, alpha = (0.18f * e).coerceIn(0f, 1f))
                            }
                            val morph = (e + 0.6f * lit).coerceIn(0f, 1f)
                            shapes[i].toPath(morph, badgePath)
                            matrix.reset()
                            matrix.setScale(s, s)
                            matrix.postTranslate(p.x - s / 2f, p.y - s / 2f)
                            badgePath.transform(matrix)
                            val shape = badgePath.asComposePath()
                            rotate(degrees = 45f * morph + (spin?.value ?: 0f) * e.coerceIn(0f, 1f), pivot = p) {
                                drawPath(shape, badgeBase)
                                drawPath(shape, colors[i], alpha = if (pending) 0.12f else (0.22f + 0.78f * maxOf(e, 0.5f * lit)).coerceIn(0f, 1f))
                            }
                            val icon = iconSize * (0.9f + 0.2f * e)
                            translate(p.x - icon / 2f, p.y - icon / 2f) {
                                with(icons[i]) { draw(Size(icon, icon), colorFilter = if (e > 0.5f) onTint else tints[i]) }
                            }
                        }
                    }
                }
        )
    }
}

/** The radar plate's corner radius. */
private val RADAR_PLATE_RADIUS = 28.dp

/**
 * What each badge on the radar sits on, so it reads as a hole cut into the plate: the plate's own deep interior in dark
 * theme (near-black with a trace of the primary container), its light interior in light theme.
 */
private fun radarBadgeBase(scheme: ColorScheme): Color =
    if (scheme.surface.luminance() < 0.5f) lerp(Color(0xFF060305), scheme.primaryContainer, 0.12f) else scheme.surfaceContainerLowest

/** How far out [check] reaches on the radar: its score, or a short stub while it is still being checked. */
private fun radarReach(check: SecurityPosture.AxisScore): Float =
    if (check.rating == SecurityPosture.Rating.Pending) RADAR_PENDING_REACH else check.score.coerceIn(0f, 1f)

/** The spring each check reaches out on: a little bounce, as Material 3 Expressive's spatial springs have. */
private val RadarSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 200f)

/**
 * A tile per check, two to a row: its icon in a badge, its name and its value. The [selected] check's tile lifts onto
 * the secondary container and its badge morphs from a circle into the check's own Material shape ([postureShape]),
 * turning as it goes, on a spring; it morphs back when another is chosen.
 */
@Composable
private fun PostureTiles(checks: List<SecurityPosture.AxisScore>, selected: Axis?, onSelect: (Axis) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        val columns = postureTileColumns(maxWidth, fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            checks.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { check ->
                        PostureTile(check, check.axis == selected, onSelect = { onSelect(check.axis) }, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The narrowest a tile can be at normal text size and still keep "StrongBox" and "Verified Boot" whole. */
internal val POSTURE_TILE_MIN_WIDTH = 150.dp

/** Two columns of tiles where each still fits [POSTURE_TILE_MIN_WIDTH] scaled by the text size, else one. */
internal fun postureTileColumns(width: Dp, fontScale: Float): Int =
    if ((width - 8.dp) / 2 >= POSTURE_TILE_MIN_WIDTH * fontScale) 2 else 1

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

/** The colour that marks a check's rating, on the radar, its tile and its value. */
internal fun ratingColor(rating: SecurityPosture.Rating, scheme: ColorScheme): Color = when (rating) {
    SecurityPosture.Rating.Good -> scheme.primary
    SecurityPosture.Rating.Fair -> scheme.tertiary
    SecurityPosture.Rating.Weak -> scheme.error
    SecurityPosture.Rating.Pending -> scheme.outline
}
