package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One separately drawn part of a widget action icon, part way through the icon's selection animation: how far it
 * is turned (degrees, about its pivot), scaled (about its pivot) and moved, all in the icon's 24-unit box.
 */
internal class IconPartPose {
    var pivotX = 12f
    var pivotY = 12f
    var rotation = 0f
    var scaleX = 1f
    var scaleY = 1f
    var dx = 0f
    var dy = 0f

    val isIdentity: Boolean get() = rotation == 0f && scaleX == 1f && scaleY == 1f && dx == 0f && dy == 0f

    fun reset() {
        rotation = 0f
        scaleX = 1f
        scaleY = 1f
        dx = 0f
        dy = 0f
    }

    internal fun pivot(x: Float, y: Float) {
        pivotX = x
        pivotY = y
    }
}

/**
 * Where each part of a widget action icon is during its selection animation. [body] is the icon's main shape (the
 * X, the lens, the big star, the note); [accentA] and [accentB] are its small parts (the lens's sparkle, the two
 * small stars, the note's two sound bars), unused where an icon has none. Filled in place by [ActionIconMotion.pose],
 * so a frame allocates nothing.
 */
internal class ActionIconPose {
    val body = IconPartPose()
    val accentA = IconPartPose()
    val accentB = IconPartPose()

    val isIdentity: Boolean get() = body.isIdentity && accentA.isIdentity && accentB.isIdentity
}

/**
 * The one-shot animation an option's icon plays in the Widget Action Icon picker when it is chosen, as pure
 * curves of its progress (0 to 1, linear in time) so they can be checked without drawing. Each means what its icon
 * does:
 * - None (X): a quarter turn that overshoots and settles, with a small squash and stretch.
 * - Search: the lens sweeps right and left like it is scanning, leaning into the sweep and tilting back, while
 *   its sparkle dims, pops and turns.
 * - Assistant: the big star turns a quarter with a pop while the two small stars twinkle one after the other.
 * - Now Playing: the note bobs to three beats with a slight sway, its sound bars pulsing on each beat.
 *
 * The stars, sparkle and X look the same after a quarter turn, so they end there, which is identity; at 1 every
 * part is back at rest exactly. Scales stay at or under [MAX_SCALE] and moves within the icon's padding, so the
 * icon never leaves its tile (the picker clips it there anyway).
 */
internal object ActionIconMotion {
    const val MAX_SCALE = 1.15f

    /** How long [iconType]'s animation runs. */
    fun durationMillis(iconType: String): Int = when (iconType) {
        "Search" -> 900
        "Assistant", "Gemini", "Voice" -> 850
        "Now Playing" -> 900
        else -> 650
    }

    /** Fills [pose] with where [iconType]'s parts are at [progress]; outside (0, 1) everything is at rest. */
    fun pose(iconType: String, progress: Float, pose: ActionIconPose) {
        pose.body.reset()
        pose.accentA.reset()
        pose.accentB.reset()
        if (progress <= 0f || progress >= 1f) return
        val t = progress
        when (iconType) {
            "Search" -> {
                // The lens (rim, top and handle) about its centre; the sparkle about its own.
                pose.body.pivot(10.5f, 10.5f)
                val sweep = segment(t, 0f, 0.8f)
                pose.body.dx = 1.8f * sin(2f * PI.toFloat() * sweep) * (1f - sweep)
                val tilt = segment(t, 0.08f, 0.95f)
                pose.body.rotation = -8f * sin(2f * PI.toFloat() * tilt) * (1f - tilt)
                pose.accentA.pivot(18f, 6.5f)
                val twinkle = 1f - 0.4f * bell(segment(t, 0.15f, 0.4f)) + 0.15f * bell(segment(t, 0.4f, 0.9f))
                pose.accentA.scaleX = twinkle
                pose.accentA.scaleY = twinkle
                pose.accentA.rotation = 90f * springStep(segment(t, 0.35f, 1f))
            }
            "Assistant", "Gemini", "Voice" -> {
                pose.body.pivot(9f, 12f)
                pose.body.rotation = 90f * springStep(segment(t, 0f, 0.75f))
                val pop = 1f + 0.12f * bell(fastOut(segment(t, 0f, 0.6f)))
                pose.body.scaleX = pop
                pose.body.scaleY = pop
                pose.accentA.pivot(19f, 5f)
                twinkle(pose.accentA, segment(t, 0.12f, 0.72f))
                pose.accentB.pivot(19f, 19f)
                twinkle(pose.accentB, segment(t, 0.3f, 0.9f))
            }
            "Now Playing" -> {
                // The note (stem and head) sways about the bottom of its head; each bar stretches about its middle.
                pose.body.pivot(6.6f, 21.5f)
                val beat = sin(3f * PI.toFloat() * t)
                pose.body.dy = -1.6f * abs(beat) * (1f - 0.4f * t)
                pose.body.rotation = 5f * beat * (1f - t)
                pose.accentA.pivot(14.5f, 12.5f)
                pose.accentA.scaleY = 1f + 0.3f * barPulse(t)
                pose.accentB.pivot(19f, 12f)
                pose.accentB.scaleY = 1f + 0.9f * barPulse(segment(t, 0.06f, 1f))
            }
            else -> {
                // None: the X spins a quarter turn, squashing and stretching a little as it goes.
                pose.body.pivot(12f, 12f)
                pose.body.rotation = 90f * springStep(t)
                val squash = 0.1f * sin(3f * PI.toFloat() * t) * (1f - t) * (1f - t)
                pose.body.scaleX = 1f + squash
                pose.body.scaleY = 1f - squash
            }
        }
    }

    /** A small star's twinkle over [u]: it dims down, pops a touch past full size and turns a quarter. */
    private fun twinkle(part: IconPartPose, u: Float) {
        val scale = 1f - 0.45f * bell(segment(u, 0f, 0.35f)) + 0.15f * bell(segment(u, 0.35f, 1f))
        part.scaleX = scale
        part.scaleY = scale
        part.rotation = 90f * springStep(u)
    }

    /** How far [t] is through the window [start] to [end], 0 before it and 1 after. */
    internal fun segment(t: Float, start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)

    /** Up from 0 to 1 and back to 0 over [u]. */
    internal fun bell(u: Float): Float = sin(PI.toFloat() * u.coerceIn(0f, 1f))

    /** [u] eased fast out, slow in, so a [bell] peaks early and lingers. */
    private fun fastOut(u: Float): Float = 1f - (1f - u) * (1f - u)

    /**
     * 0 to 1 over [u] like the Expressive fast spatial spring: quick off the mark, about 9% past the end, settling
     * exactly on 1 with no speed left.
     */
    internal fun springStep(u: Float): Float {
        val left = 1f - u.coerceIn(0f, 1f)
        return 1f - left * left * left * cos(1.5f * PI.toFloat() * u.coerceIn(0f, 1f))
    }

    /** Peaks on the note's two inner beats (at a third and two thirds), 0 at the start and end. */
    private fun barPulse(u: Float): Float {
        val c = abs(cos(3f * PI.toFloat() * u))
        return c * c * c * sin(PI.toFloat() * u)
    }
}

/** Draws [block] with [part]'s pose applied: scaled and turned about its pivot, then moved. */
internal inline fun DrawScope.withPart(part: IconPartPose, block: DrawScope.() -> Unit) {
    if (part.isIdentity) {
        block()
        return
    }
    withTransform({
        translate(part.dx, part.dy)
        rotate(part.rotation, Offset(part.pivotX, part.pivotY))
        scale(part.scaleX, part.scaleY, Offset(part.pivotX, part.pivotY))
    }) { block() }
}

/**
 * The progress (0 to 1) of an option icon's one-shot animation in a picker, for [ComposeActionIcon]'s `motion`:
 * it plays each time the option is tapped while it is (or becomes) the chosen one, so tapping the chosen option again
 * plays it again: [isSelected] becoming true, or [taps] (how many times this option has been tapped) changing while
 * it is selected. It never plays on first composition, and is held at 1 (at rest) with animations off. Read it while
 * drawing, so the animation redraws without recomposing.
 */
@Composable
internal fun rememberSelectionMotion(isSelected: Boolean, durationMillis: Int, taps: Int = 0): () -> Float {
    val animationsOn = rememberAnimationsEnabled()
    val progress = remember { Animatable(1f) }
    val wasSelected = remember { booleanArrayOf(isSelected) }
    val seenTaps = remember { intArrayOf(taps) }
    LaunchedEffect(isSelected, taps, animationsOn.value) {
        val play = isSelected && (!wasSelected[0] || taps != seenTaps[0])
        wasSelected[0] = isSelected
        seenTaps[0] = taps
        if (!animationsOn.value) {
            progress.snapTo(1f)
        } else if (play) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        }
    }
    return remember(progress) { { if (animationsOn.value) progress.value else 1f } }
}

/**
 * Clips what this draws to its bounds grown by [margin] on every side: room for an animated icon to move, with
 * nothing past it ever drawn. The picker keeps [margin] within its tile's padding, so the icon stays in the tile.
 */
internal fun Modifier.clipAround(margin: Dp): Modifier = drawWithContent {
    val m = margin.toPx()
    clipRect(-m, -m, size.width + m, size.height + m) { this@drawWithContent.drawContent() }
}
