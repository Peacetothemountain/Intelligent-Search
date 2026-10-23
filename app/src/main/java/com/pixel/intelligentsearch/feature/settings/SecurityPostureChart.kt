package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.security.SecurityPosture.Axis
import com.pixel.intelligentsearch.core.system.openSettingsPage
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Test tag on the posture card's advice for the chosen check. */
internal const val POSTURE_ADVICE_TAG = "posture_advice"

/** Test tag prefix on each defense layer's row: the prefix and the check's name. */
internal const val POSTURE_LAYER_TAG_PREFIX = "posture_layer_"

/**
 * The checks as Android stacks its defences, from the hardware at the bottom up to the user's own lock on top (the
 * Android platform security model's defence in depth): keys kept in hardware, the hardware vouching for itself, the
 * system checked at startup, the bootloader that guards it, the security fixes on top of it, and the screen lock.
 */
internal val DEFENSE_ORDER: List<Axis> = listOf(
    Axis.ScreenLock, Axis.PatchLevel, Axis.Bootloader, Axis.VerifiedBoot, Axis.Attestation, Axis.KeyStorage
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
        .sortedWith(compareByDescending<Pair<SecurityPosture.AxisScore, Int>> { it.first.let { s -> 1f - s.score } }
            .thenBy { if (it.first.rating == SecurityPosture.Rating.Weak) 0 else 1 })
}

/** One rise of the scan through every layer, from the hardware up, and a short rest. */
internal const val SCAN_RISE_MILLIS = 3600

/** The share of a rise the scan spends climbing; the rest it waits below the stack. */
internal const val SCAN_CLIMB = 0.78f

/** How many sparks escape through a layer's gap as the scan passes it. */
private const val GAP_SPARKS = 5

/** One shimmer across a layer's missing part. */
internal const val GAP_SHIMMER_MILLIS = 1800

/**
 * Where the scan is in the stack at [phase] (0 to 1) of its rise: 0 at the bottom (the hardware) to 1 at the top, or
 * null while it rests between rises.
 */
internal fun scanHeight(phase: Float): Float? {
    val p = phase - phase.toInt()
    return if (p > SCAN_CLIMB) null else p / SCAN_CLIMB
}

/**
 * How lit layer [index] of [layers] (0 at the top) is when the scan is at [height] (0 bottom to 1 top): a soft bell
 * centred on the layer, full as the scan crosses it.
 */
internal fun layerLight(height: Float?, index: Int, layers: Int): Float {
    if (height == null || layers <= 0) return 0f
    val centre = 1f - (index + 0.5f) / layers
    val d = (height - centre) * layers
    return exp(-d * d * 3f).coerceIn(0f, 1f)
}

/**
 * How far the scan at [height] has passed through layer [index] of [layers] (0 at the top): 0 as it reaches the layer's
 * lower neighbour's middle, 1 as it reaches its upper neighbour's, or null while it is further away. Drives the
 * shine along a full layer and the sparks rising out of a gap, so both move with the scan.
 */
internal fun layerPass(height: Float?, index: Int, layers: Int): Float? {
    if (height == null || layers <= 0) return null
    val centre = 1f - (index + 0.5f) / layers
    val pass = (height - centre) * layers * 0.5f + 0.5f
    return pass.takeIf { it in 0f..1f }
}

/**
 * The Hardware & Security page's posture card: the overall rating, then each check as a layer of the phone's
 * defences, hardware at the bottom ([DefenseLayers]), then what to improve, ranked by what it wins back
 * ([improvementPlan]), with the chosen check explained and Android's page to fix it where the phone can. It opens on
 * the check most worth improving; tapping a layer or an item explains that one. Text is shown as it is, never
 * animated.
 */
@Composable
internal fun SecurityPostureCard(axes: List<SecurityPosture.AxisScore>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val overall = SecurityPosture.overall(axes)
    val percent = overall?.let { (it * 100).roundToInt() }
    val plan = remember(axes) { improvementPlan(axes) }
    // The user's choice wins; until they make one, the card explains the check most worth improving.
    var chosen by remember { mutableStateOf<Axis?>(null) }
    val selected = chosen ?: plan.firstOrNull()?.first?.axis
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
        DefenseLayers(axes, selected = selected, onSelect = { chosen = it })
        ImprovementList(axes, plan, selected, onSelect = { chosen = it })
    }
}

/**
 * Each check as a layer of the phone's defences, in [DEFENSE_ORDER] (the screen lock on top, the hardware key store
 * at the bottom): its name and value, and a bar filled to its score in its rating's colour, the rest of the bar (what
 * is missing) outlined. A scan rises through the stack from the hardware up, as a signal passing each defence: a
 * layer at full marks flashes solid as it passes, and a layer with a gap lets sparks escape through it, so what is
 * thin shows where it is; between scans the gaps shimmer. With animations off the stack is drawn still. A tap on a
 * layer explains it ([onSelect]); the [selected] layer is tinted.
 */
@Composable
internal fun DefenseLayers(
    axes: List<SecurityPosture.AxisScore>,
    modifier: Modifier = Modifier,
    selected: Axis? = null,
    onSelect: (Axis) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled by rememberAnimationsEnabled()
    val scan: State<Float>?
    val shimmer: State<Float>?
    if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "defenseLayers")
        scan = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SCAN_RISE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "defenseScan"
        )
        shimmer = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(GAP_SHIMMER_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "defenseShimmer"
        )
    } else {
        scan = null
        shimmer = null
    }
    val layers = DEFENSE_ORDER.mapNotNull { axis -> axes.firstOrNull { it.axis == axis } }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 6.dp)
    ) {
        layers.forEachIndexed { index, layer ->
            DefenseLayerRow(
                layer = layer,
                index = index,
                count = layers.size,
                isSelected = layer.axis == selected,
                scan = scan,
                shimmer = shimmer,
                scheme = scheme,
                onSelect = { onSelect(layer.axis) }
            )
        }
    }
}

@Composable
private fun DefenseLayerRow(
    layer: SecurityPosture.AxisScore,
    index: Int,
    count: Int,
    isSelected: Boolean,
    scan: State<Float>?,
    shimmer: State<Float>?,
    scheme: ColorScheme,
    onSelect: () -> Unit
) {
    val color = ratingColor(layer.rating, scheme)
    val track = scheme.onSurface.copy(alpha = 0.08f)
    val tint = if (isSelected) color.copy(alpha = 0.12f) else Color.Transparent
    val pending = layer.rating == SecurityPosture.Rating.Pending
    val gapDash = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(POSTURE_LAYER_TAG_PREFIX + layer.axis.name)
            .clip(RoundedCornerShape(10.dp))
            .background(tint)
            .clearAndSetSemantics {
                contentDescription = "${layer.axis.label}: ${layer.value}"
                role = Role.Button
                selected = isSelected
                onClick(label = "Explain ${layer.axis.label}") { onSelect(); true }
            }
            .then(Modifier.bouncyClickable(shape = RoundedCornerShape(10.dp), onClick = onSelect))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Name and value share a line when they fit; with large text the value wraps under the name, never mid-word.
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = layer.axis.label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = layer.value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(Modifier.height(5.dp))
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .drawWithCache {
                    val radius = CornerRadius(size.height / 2f)
                    val filled = if (pending) 0f else size.width * layer.score.coerceIn(0f, 1f)
                    val gap = !pending && layer.score < FULL_MARKS
                    val gapStroke = Stroke(width = 1.2.dp.toPx(), pathEffect = gapDash)
                    val sparkRadius = 1.6.dp.toPx()
                    val shine = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.55f),
                        1f to Color.White.copy(alpha = 0f),
                        startX = 0f,
                        endX = size.height * 4f
                    )
                    onDrawBehind {
                        drawRoundRect(track, cornerRadius = radius)
                        val height = scan?.value?.let(::scanHeight)
                        val lit = layerLight(height, index, count)
                        val pass = layerPass(height, index, count)
                        if (filled > 0f) {
                            drawRoundRect(color, size = Size(filled, size.height), cornerRadius = radius, alpha = 0.78f + 0.22f * lit)
                            // A full layer flashes solid as the scan crosses it: a shine runs along the bar.
                            if (!gap && pass != null) {
                                val x = (size.width + size.height * 4f) * pass - size.height * 4f
                                clipRect(right = filled) {
                                    drawRect(shine, topLeft = Offset(x, 0f), size = Size(size.height * 4f, size.height), alpha = lit)
                                }
                            }
                        }
                        if (gap) {
                            // What is missing: outlined, shimmering, and letting sparks out as the scan passes.
                            val left = filled
                            val gapWidth = size.width - left
                            val s = shimmer?.value ?: 0f
                            val glow = 0.10f + 0.10f * (0.5f + 0.5f * sin(2f * PI.toFloat() * s))
                            drawRoundRect(color, topLeft = Offset(left, 0f), size = Size(gapWidth, size.height), cornerRadius = radius, alpha = glow)
                            drawRoundRect(color.copy(alpha = 0.6f), topLeft = Offset(left, 0f), size = Size(gapWidth, size.height), cornerRadius = radius, style = gapStroke)
                            if (pass != null && lit > 0.02f) {
                                for (k in 0 until GAP_SPARKS) {
                                    val f = (k + 0.5f) / GAP_SPARKS
                                    val x = left + gapWidth * f
                                    // Each spark rises out of the gap with the scan and fades as it moves on.
                                    val rise = pass * size.height * 1.4f + (k % 2) * size.height * 0.2f
                                    drawCircle(color, radius = sparkRadius * (0.6f + 0.6f * lit), center = Offset(x, size.height / 2f - rise), alpha = lit)
                                }
                            }
                        }
                    }
                }
        )
    }
}

/**
 * Under the layers: what to improve, most first, each with what it would add to the overall score; the [selected]
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
                text = if (axes.any { it.rating == SecurityPosture.Rating.Pending }) "Checking the rest..." else "Every layer is at full strength. Nothing to improve.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            return@Column
        }
        if (plan.isNotEmpty()) {
            Text("To Improve", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            plan.forEach { (score, gain) ->
                val color = ratingColor(score.rating, scheme)
                val isSelected = score.axis == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) color.copy(alpha = 0.12f) else Color.Transparent)
                        .bouncyClickable(shape = RoundedCornerShape(10.dp)) { onSelect(score.axis) }
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
        if (explained != null) {
            val advice = postureAdvice(explained)
            val color = ratingColor(explained.rating, scheme)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${explained.axis.label} · ${explained.value}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(text = advice.text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
                advice.fix?.let { fix ->
                    FilledTonalButton(onClick = { openSettingsPage(context, *fix.intents().toTypedArray()) }) {
                        Text(fix.label)
                    }
                }
            }
        }
    }
}

/** The colour that marks a check's rating, on its layer and in its value. */
internal fun ratingColor(rating: SecurityPosture.Rating, scheme: ColorScheme): Color = when (rating) {
    SecurityPosture.Rating.Good -> scheme.primary
    SecurityPosture.Rating.Fair -> scheme.tertiary
    SecurityPosture.Rating.Weak -> scheme.error
    SecurityPosture.Rating.Pending -> scheme.outline
}
