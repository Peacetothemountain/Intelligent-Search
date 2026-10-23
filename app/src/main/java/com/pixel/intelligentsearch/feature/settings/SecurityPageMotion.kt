package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The Hardware & Security page's motion, apart from Compose so it can be tested. The page itself is drawn at once,
 * without an entrance. What moves does a job: the check indicator runs while attestation is really running, the
 * chain carries a soft pulse from the app's key down to the root (quick while verifying, slow and calm afterwards),
 * and the posture chip counts up as the radar draws out.
 */

/** One trip of the chain's pulse, leaf to root, while attestation is running. */
internal const val CHAIN_PULSE_VERIFYING_MILLIS = 1100

/** One trip of the pulse, leaf to root and a rest, once the result is in. */
internal const val CHAIN_PULSE_RESTING_MILLIS = 3600

/** The share of a resting trip the pulse spends travelling; the rest of it the chain is still. */
internal const val CHAIN_PULSE_TRAVEL = 0.55f

/** How far either side of the pulse's centre a link still lights, in links. */
private const val CHAIN_PULSE_WIDTH = 0.9f

/** One sway of the chain, as a hanging chain sways, in milliseconds. */
internal const val CHAIN_SWAY_MILLIS = 2800

/** How long the posture chip's count-up takes after the radar's first axis, so it lands as the radar settles. */
private const val COUNT_UP_SETTLE_MILLIS = 700

/**
 * How lit step [index] of [steps] (the links, then the root's key) is, 0 to 1, at [phase] (0 to 1) of the pulse's
 * trip: a soft bell that travels from the first step to the last over the first [travel] of the trip, then leaves
 * the chain dark for the rest. Steps right under the pulse are fully lit; it never lights anything outside 0..1.
 */
internal fun chainPulse(phase: Float, index: Int, steps: Int, travel: Float = CHAIN_PULSE_TRAVEL): Float {
    if (steps <= 0 || travel <= 0f) return 0f
    val p = phase - phase.toInt()
    if (p > travel) return 0f
    // From a little before the first step to a little past the last, so it eases on and off the chain.
    val position = -CHAIN_PULSE_WIDTH + (p / travel) * (steps - 1 + 2 * CHAIN_PULSE_WIDTH)
    val d = abs(index - position) / CHAIN_PULSE_WIDTH
    return exp(-d * d * 2.2f).coerceIn(0f, 1f)
}

/**
 * How far link [index] rises (positive) or falls (negative) at [phase] (0 to 1) of the chain's sway, as a fraction
 * of its full sway: a slow wave down the chain, each link a little behind the one before, so it moves like a chain
 * hanging in still air rather than all at once.
 */
internal fun chainSway(phase: Float, index: Int): Float {
    val p = phase - phase.toInt()
    return sin(2f * PI.toFloat() * (p - index * 0.11f))
}

/** The posture chip's percentage [progress] (0 to 1) of the way through its count-up to [target]: 0 to exactly [target]. */
internal fun countUpValue(progress: Float, target: Int): Int = when {
    progress <= 0f -> 0
    progress >= 1f -> target
    else -> (target * FastOutSlowInEasing.transform(progress)).roundToInt().coerceIn(0, target)
}

/** How long the posture chip counts up for a radar of [axisCount] axes, in step with the radar's staggered draw-in. */
internal fun countUpMillis(axisCount: Int): Int =
    (axisCount - 1).coerceAtLeast(0) * RADAR_ENTRY_STAGGER_MILLIS.toInt() + COUNT_UP_SETTLE_MILLIS
