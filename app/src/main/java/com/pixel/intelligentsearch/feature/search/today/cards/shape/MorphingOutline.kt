package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlin.math.cos
import kotlin.math.exp

/** How long a card's shape takes to morph into the next. */
private const val MORPH_MS = 600

/**
 * A card's outline as it morphs from one shape to another: a slightly springy blend over [MORPH_MS], as the approved
 * design's `stepGeom` runs it (1 - e^(-6.5t) cos 8t, a little past the new shape and back). Read [current] while
 * drawing, so a morph redraws the card and never recomposes it.
 */
@Stable
internal class MorphingOutline(name: String) {
    private var from: CardOutline = CardShapes.outline(name)
    private var to: CardOutline = from
    private var target = name
    private val progress = Animatable(1f)
    private var lastT = -1f
    private var last: CardOutline = to

    /** The outline now. Reading it in drawing redraws on every step of a morph. */
    val current: CardOutline
        get() {
            val t = progress.value
            if (t >= 1f) return to
            if (t == lastT) return last
            last = from.blend(to, spring(t))
            lastT = t
            return last
        }

    /** Morphs to [name] from wherever the outline is now (at once when [animate] is false). */
    suspend fun morphTo(name: String, animate: Boolean) {
        if (name == target) {
            if (!animate && progress.value < 1f) progress.snapTo(1f)
            return
        }
        from = current
        to = CardShapes.outline(name)
        target = name
        lastT = -1f
        if (!animate) {
            progress.snapTo(1f)
            return
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(MORPH_MS, easing = LinearEasing))
    }

    private fun spring(t: Float): Float = if (t >= 1f) 1f else 1f - exp(-6.5f * t) * cos(8f * t)
}

/**
 * The outline of a card of shape [name], morphing to each new name it is given with a short spring (at once with
 * animations off).
 */
@Composable
internal fun rememberMorphingOutline(name: String): MorphingOutline {
    val animate by rememberAnimationsEnabled()
    val outline = remember { MorphingOutline(name) }
    LaunchedEffect(outline, name, animate) { outline.morphTo(name, animate) }
    return outline
}
