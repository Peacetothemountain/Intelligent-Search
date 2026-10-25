package com.pixel.intelligentsearch.feature.settings

import kotlin.math.PI
import kotlin.math.sin

/*
 * The curves behind what the Material Morph animation's floor does round a shape, as the street does under Neo in The
 * Matrix: the ripples a landing sends across it, the floor's near edge dipping round a sunk shape, and the silhouette
 * healing after the shape has gone. All pure functions of time, so drawing a frame allocates nothing and tests can pin them.
 * None of it feeds back into the motion (see MorphBouncePhysics).
 */

/** A soft landing sends this many ripples across the floor, [SOFT_RIPPLE_GAP] seconds apart. */
internal const val SOFT_RIPPLES = 3
internal const val SOFT_RIPPLE_GAP = 0.11f
/** Each soft ripple spreads over this long, seconds. */
internal const val SOFT_RIPPLE_TIME = 0.7f
/** The first soft ripple spreads to this many times the silhouette's size, each later one [SOFT_RIPPLE_SPREAD_STEP] less. */
internal const val SOFT_RIPPLE_SPREAD = 2.6f
internal const val SOFT_RIPPLE_SPREAD_STEP = 0.35f
/** A hard landing's one ripple spreads to this many times the shape's size, over [MorphBouncePhysics.HARD_RIPPLE_TIME]. */
internal const val HARD_RIPPLE_SPREAD = 1.9f
/** How sharply a ripple slows as it spreads: an ease-out of this power (higher is a quicker start, a sharper ring). */
internal const val SOFT_RIPPLE_EASE = 3
internal const val HARD_RIPPLE_EASE = 5
/** A ripple fades in over this share of its run, then out. */
private const val RIPPLE_RISE = 0.08f

/** How far along a ripple started [delay] seconds after an impact [age] seconds ago, and spreading over [duration], is. */
internal fun rippleProgress(age: Float, delay: Float, duration: Float): Float =
    if (duration <= 0f) 1f else (age - delay) / duration

/**
 * A ripple's size [progress] of the way along, as a multiple of the silhouette it follows: from 1, the silhouette's
 * own outline, out to [spread], fast at first and slowing, on an ease-out of [ease].
 */
internal fun rippleScale(progress: Float, spread: Float, ease: Int): Float {
    val p = progress.coerceIn(0f, 1f)
    var remaining = 1f
    repeat(ease) { remaining *= 1f - p }
    return 1f + (spread - 1f) * (1f - remaining)
}

/** A ripple's opacity [progress] of the way along, 0 to 1: a quick fade in, then fading out as it spreads, gone at 1. */
internal fun rippleAlpha(progress: Float): Float {
    if (progress <= 0f || progress >= 1f) return 0f
    if (progress < RIPPLE_RISE) return (progress / RIPPLE_RISE) * (1f - RIPPLE_RISE) * (1f - RIPPLE_RISE)
    val left = 1f - progress
    return left * left
}

/** The membrane dips round a sunk shape over this many of its radii each side. */
internal const val MEMBRANE_REACH = 2.8f

/**
 * How far down the membrane is [u] of the way from the sunk shape's center (0) to the edge of the dip (±1), as a share
 * of its sag there: 1 under the shape, easing smoothly to 0, flat, at the edge, so the floor curves down into it.
 */
internal fun membraneBump(u: Float): Float {
    if (u <= -1f || u >= 1f) return 0f
    val v = 1f - u * u
    return v * v
}

/**
 * How stretched the membrane is over a shape sunk into it, 0 to 1, from its [surface] sag under a shape of [radius]
 * (both metres): it catches the light along the stretch this strongly, and the dip round it is shaded this deep.
 */
internal fun membraneStretch(surface: Float, radius: Float): Float =
    if (radius <= 0f) 0f else (surface / (MEMBRANE_FULL_STRETCH * radius)).coerceIn(0f, 1f)

/** The membrane is fully stretched once it sags this many radii under a shape. */
private const val MEMBRANE_FULL_STRETCH = 0.2f

/** As the floor fills back in, the hollow has closed at this share of its healing, then overfills a touch and settles. */
internal const val HEAL_CLOSE = 0.55f
private const val HEAL_CURVE = (1f + 2f * HEAL_CLOSE) / (HEAL_CLOSE * HEAL_CLOSE)

/**
 * The size of the healing hollow, as a share of the silhouette, [heal] of the way through its healing: the floor
 * fills it in from the rim inwards like a spring, slow to start, closing at [HEAL_CLOSE], overshooting a touch (the
 * floor overfilling into a tiny mound, under a tenth of the silhouette) and settling flat at exactly 0, at rest.
 */
internal fun healShrink(heal: Float): Float {
    val h = heal.coerceIn(0f, 1f)
    val left = 1f - h
    return left * left * (1f + 2f * h - HEAL_CURVE * h * h)
}

/**
 * How far through its healing a hollow [shrink] of its silhouette's size is: the inverse of [healShrink] while it is
 * still closing (heal 0 to [HEAL_CLOSE]), so a silhouette that closes as the rubber's dent recovers is drawn just as one
 * healing on its own clock.
 */
internal fun healForShrink(shrink: Float): Float {
    if (shrink >= 1f) return 0f
    if (shrink <= 0f) return HEAL_CLOSE
    var lo = 0f
    var hi = HEAL_CLOSE
    repeat(20) {
        val mid = (lo + hi) / 2f
        if (healShrink(mid) > shrink) lo = mid else hi = mid
    }
    return (lo + hi) / 2f
}

/** How dark the hollow's inside still is as it heals: it lightens first, while the rim still catches the light. */
internal fun healShade(heal: Float): Float = 1f - smoothStep(0.05f, 0.45f, heal)

/** How bright the rim still is as the hollow heals: it fades last, gone just as the hollow closes. */
internal fun healRim(heal: Float): Float = 1f - smoothStep(0.3f, HEAL_CLOSE, heal)

/** As the hollow closes, its last moment breaks into a few tiny pixels: how far that has got, 0 to 1, at [heal]. */
internal fun healPixelPhase(heal: Float): Float = ((heal - 0.32f) / 0.4f).coerceIn(0f, 1f)

/** Each pixel lives this share of the pixel phase; started at most [MAX_PIXEL_DELAY] in, every one is gone by its end. */
internal const val PIXEL_LIFE = 0.6f
internal const val MAX_PIXEL_DELAY = 1f - PIXEL_LIFE

/** A pixel's opacity, 0 to 1, at [phase] of the pixel phase, for one that starts [delay] in: up and dissolved again. */
internal fun pixelAlpha(phase: Float, delay: Float): Float {
    val local = (phase - delay) / PIXEL_LIFE
    if (local <= 0f || local >= 1f) return 0f
    return 4f * local * (1f - local)
}

private fun smoothStep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
