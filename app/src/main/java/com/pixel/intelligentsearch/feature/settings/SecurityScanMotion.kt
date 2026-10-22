package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The Hardware & Security page's entrance "security scan": one clock, in milliseconds since the page opened, that
 * every part of the page reads in its draw phase. The shield traces itself and ticks, a light passes over the
 * banner, the description rises in, the details resolve line by line, the certificate chain links up and the
 * posture radar draws out alongside, all from this one timeline. With animations off the clock simply sits at
 * [SCAN_END_MILLIS], so everything is drawn finished.
 */

/** The banner shield's outline traces round over this span. */
internal const val SCAN_SHIELD_START = 0f
internal const val SCAN_SHIELD_MILLIS = 650f

/** Then the check mark strokes in inside it. */
internal const val SCAN_CHECK_START = 500f
internal const val SCAN_CHECK_MILLIS = 320f

/** A single soft light passes across the banner. */
internal const val SCAN_SHEEN_START = 750f
internal const val SCAN_SHEEN_MILLIS = 700f

/** The description fades and rises in after the banner. */
internal const val SCAN_DESCRIPTION_START = 550f
internal const val SCAN_DESCRIPTION_MILLIS = 350f

/** The details lines reveal one after another from here. */
internal const val SCAN_DETAILS_START = 850f
internal const val SCAN_LINE_STAGGER = 70f
internal const val SCAN_LINE_FADE_MILLIS = 220f

/** How long a line's characters take to resolve from random glyphs to its text. */
internal const val DECRYPT_MILLIS = 250

/** How many distinct scrambled frames a line steps through while it resolves. */
internal const val DECRYPT_FRAMES = 6

/** The details line the certificate chain sits on, and when its links start to draw. */
internal const val SCAN_CHAIN_LINE = 5
internal val SCAN_CHAIN_START = detailLineStart(SCAN_CHAIN_LINE) + 120f

/** All the links have drawn within this span, however long the chain. */
private const val CHAIN_DRAW_SPAN = 560f
private const val CHAIN_LINK_MAX_STAGGER = 80f
internal const val SCAN_LINK_DRAW_MILLIS = 200f

/** Verification reaches the first link this long after the chain starts drawing, then one link per step. */
internal val SCAN_LOCK_START = SCAN_CHAIN_START + 220f
internal const val SCAN_LOCK_STEP_MILLIS = 110

/** When the posture radar starts to draw out, alongside the details. */
internal const val SCAN_RADAR_START_MILLIS = 700L

/** When the entrance is over; the scan clock stops here. */
internal val SCAN_END_MILLIS = SCAN_CHAIN_START + CHAIN_DRAW_SPAN + SCAN_LINK_DRAW_MILLIS

/** One cycle of the shield's resting glow, which brightens briefly near the end of each cycle. */
internal const val SHIELD_GLOW_PERIOD_MILLIS = 6000

/** Where in its cycle the shield's glow starts to rise. */
private const val SHIELD_GLOW_RISE = 0.8f

/** How long the posture chip's count-up takes after the radar's first axis, so it lands as the radar settles. */
private const val COUNT_UP_SETTLE_MILLIS = 700

/** How far along a stage that starts at [startMs] and lasts [durationMs] is at [nowMs]: 0 before it, 1 after it. */
internal fun scanStage(nowMs: Float, startMs: Float, durationMs: Float): Float = when {
    durationMs <= 0f -> if (nowMs >= startMs) 1f else 0f
    nowMs <= startMs -> 0f
    nowMs >= startMs + durationMs -> 1f
    else -> (nowMs - startMs) / durationMs
}

/** When details line [index] starts to reveal. */
internal fun detailLineStart(index: Int): Float = SCAN_DETAILS_START + index * SCAN_LINE_STAGGER

/** When link [index] of a [count]-link certificate chain starts to draw; a long chain draws faster, in the same span. */
internal fun chainLinkStart(index: Int, count: Int): Float =
    SCAN_CHAIN_START + index * minOf(CHAIN_LINK_MAX_STAGGER, CHAIN_DRAW_SPAN / count.coerceAtLeast(1))

/**
 * How locked step [index] of [steps] is (0 to 1) when verification has run [progress] (0 to 1) of the way down the
 * chain: each step locks in turn, in its own equal share of the run.
 */
internal fun chainStepProgress(progress: Float, index: Int, steps: Int): Float {
    if (steps <= 0) return 0f
    return (progress * steps - index).coerceIn(0f, 1f)
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

/** The shield's resting glow at [phase] (0 to 1) through its cycle: dark most of the cycle, one soft swell near its end. */
internal fun shieldGlow(phase: Float): Float {
    val p = phase - phase.toInt()
    if (p <= SHIELD_GLOW_RISE) return 0f
    val s = sin(PI.toFloat() * (p - SHIELD_GLOW_RISE) / (1f - SHIELD_GLOW_RISE))
    return s * s
}

/**
 * When (0 to 1 of the resolve) character [index] of a [length]-character line settles on its real glyph: roughly left
 * to right, with a little randomness from [seed] so the line doesn't resolve as a hard wipe. Always below 1.
 */
internal fun decryptResolveAt(index: Int, length: Int, seed: Int): Float {
    val order = if (length <= 1) 0f else index.toFloat() / length
    return 0.55f * order + 0.45f * unit(mix(seed, index, 0x5EC))
}

/**
 * Line [final] part-way ([progress] 0 to 1) through its decrypt: each letter or digit not yet settled
 * ([decryptResolveAt]) shows a random glyph of its own kind (lower case, upper case or digit, so the line keeps
 * roughly its width), changing with [frame]. Spaces, punctuation and the first [keep] characters always show as they
 * are. At [progress] 1 it is [final]; it is always as long as [final].
 */
internal fun decryptFrame(final: String, progress: Float, seed: Int, frame: Int, keep: Int = 0): String {
    if (progress >= 1f) return final
    val chars = final.toCharArray()
    for (i in chars.indices) {
        val c = chars[i]
        if (i < keep || !c.isLetterOrDigit() || c.isSurrogate()) continue
        if (decryptResolveAt(i, chars.size, seed) <= progress) continue
        val pick = mix(seed, i, frame + 1)
        chars[i] = when {
            c.isDigit() -> DIGIT_GLYPHS[pick % DIGIT_GLYPHS.length]
            c.isUpperCase() -> UPPER_GLYPHS[pick % UPPER_GLYPHS.length]
            else -> LOWER_GLYPHS[pick % LOWER_GLYPHS.length]
        }
    }
    return String(chars)
}

private const val LOWER_GLYPHS = "abcdefghknopqrsuvxyz"
private const val UPPER_GLYPHS = "ABCDEFGHKLNOPRSTUVXYZ"
private const val DIGIT_GLYPHS = "0123456789"

/** A well-mixed non-negative hash of three ints, so the scramble is the same every run. */
private fun mix(a: Int, b: Int, c: Int): Int {
    var h = a * 0x27D4EB2D xor b * 0x165667B1 xor c * 0x1B873593
    h = h xor (h ushr 15)
    h *= -0x7a143595
    h = h xor (h ushr 13)
    h *= -0x3d4d51cb
    h = h xor (h ushr 16)
    return h and 0x7FFFFFFF
}

/** [h] (non-negative) as a fraction in [0, 1). */
private fun unit(h: Int): Float = (h % 10_000) / 10_000f
