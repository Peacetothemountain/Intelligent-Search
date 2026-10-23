package com.pixel.intelligentsearch.feature.settings

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/*
 * The Hardware & Security page's motion, apart from Compose so it can be tested. The page itself, and every line of
 * text on it, is drawn at once, without an entrance. The banner's secure chip draws light in along its pins (quickly
 * while attestation is verifying, calmly afterwards), and the chain carries a soft pulse from the app's key down to the
 * root. The posture radar's motion lives with it (SecurityPostureChart.kt).
 */

/** One round of light into the secure chip, every pin in turn, while attestation is verifying. */
internal const val CHIP_ROUND_VERIFYING_MILLIS = 1600

/** One round of light into the chip and a rest, once the result is in. */
internal const val CHIP_ROUND_RESTING_MILLIS = 5200

/** The share of a resting round the light spends coming in; the rest of it the chip is still. */
internal const val CHIP_LIGHT_TRAVEL = 0.6f

/** How long one light takes along its pin, as a share of the round's travelling part. */
private const val CHIP_LIGHT_SPAN = 0.26f

/** How long the chip glows after a light arrives, as a share of the round's travelling part. */
private const val CHIP_GLOW_SPAN = 0.14f

/**
 * When, in the round's travelling part, pin [index] of [pins] sends its light: each a little after the one before. A
 * round with no rest ([travel] 1, while verifying) spaces them right round, so the last leads straight into the first.
 */
private fun chipLightStart(index: Int, pins: Int, travel: Float): Float {
    val spread = if (travel >= 1f) 1f else 1f - CHIP_LIGHT_SPAN
    return index.toFloat() / pins.coerceAtLeast(1) * spread
}

/** How far into pin [index]'s turn the round is at travelling time [t], wrapping round when the round never rests. */
private fun chipSince(t: Float, index: Int, pins: Int, travel: Float): Float {
    val since = t - chipLightStart(index, pins, travel)
    return if (travel >= 1f && since < 0f) since + 1f else since
}

/**
 * How far along pin [index] of [pins] its light is at [phase] (0 to 1) of the round, from the pin's outer end (0) to
 * the chip (1), or null while that pin has no light: the lights go in turn, round the chip, over the first [travel]
 * of the round.
 */
internal fun chipLightPosition(phase: Float, index: Int, pins: Int, travel: Float = CHIP_LIGHT_TRAVEL): Float? {
    if (pins <= 0 || travel <= 0f) return null
    val t = (phase - phase.toInt()) / travel
    if (t > 1f) return null
    val along = chipSince(t, index, pins, travel) / CHIP_LIGHT_SPAN
    return along.takeIf { it in 0f..1f }
}

/** How brightly the chip glows (0 to 1) from pin [index]'s light at [phase]: brightest as it arrives, then fading. */
internal fun chipArrival(phase: Float, index: Int, pins: Int, travel: Float = CHIP_LIGHT_TRAVEL): Float {
    if (pins <= 0 || travel <= 0f) return 0f
    val t = (phase - phase.toInt()) / travel
    val since = chipSince(t, index, pins, travel) - CHIP_LIGHT_SPAN
    if (since < 0f || since > CHIP_GLOW_SPAN) return 0f
    return 1f - since / CHIP_GLOW_SPAN
}

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
