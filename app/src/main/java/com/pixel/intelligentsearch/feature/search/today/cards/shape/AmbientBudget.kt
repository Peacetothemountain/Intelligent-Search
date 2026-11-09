package com.pixel.intelligentsearch.feature.search.today.cards.shape

import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.search.today.LoopReader
import com.pixel.intelligentsearch.feature.search.today.rememberLoop

/** The most cards that keep moving at once (the living light, a spinning record, the battery's shimmer). */
internal const val MAX_AMBIENT = 2

/** The ambient clock's loop: every ambient motion repeats a whole number of times in it, so it never jumps. */
internal const val AMBIENT_PERIOD_MS = 60_000

/** One frame at 60 Hz, the rate ambient motion draws at whatever the screen's rate. */
private const val AMBIENT_FRAME_MS = 1000f / 60f

/**
 * Who may keep moving. Discover's cards draw in once and hold still, except for at most [slots] that keep a little
 * ambient motion. A card that has some asks for a slot while it is on screen and lets it go when it leaves (scrolls
 * away, is covered, or is removed); the first [slots] to ask hold one, and the next in line takes a slot as soon as one
 * is let go.
 */
@Stable
internal class AmbientBudget(val slots: Int = MAX_AMBIENT) {
    private val asking = mutableStateListOf<Any>()

    /** Puts [owner] in line for a slot (once). */
    fun request(owner: Any) {
        if (owner !in asking) asking += owner
    }

    /** Takes [owner] out of the line, freeing its slot if it held one. */
    fun release(owner: Any) {
        asking.remove(owner)
    }

    /** Whether [owner] holds a slot now. A snapshot read: it updates whoever reads it when the line moves. */
    fun holds(owner: Any): Boolean {
        val i = asking.indexOf(owner)
        return i in 0 until slots
    }
}

/** The page's [AmbientBudget]. Without one, no card keeps ambient motion: they all draw in and hold still. */
internal val LocalAmbientBudget = staticCompositionLocalOf<AmbientBudget?> { null }

/** Gives [content] one [AmbientBudget] for all of its cards. Discover's page wraps its grid in this. */
@Composable
internal fun ProvideAmbientBudget(content: @Composable () -> Unit) {
    val budget = remember { AmbientBudget() }
    CompositionLocalProvider(LocalAmbientBudget provides budget, content = content)
}

/**
 * Ambient time for a card that [wants] ambient motion while it is [onScreen]: milliseconds along the page's shared
 * frame loop ([AMBIENT_PERIOD_MS] long), stepping at 60 Hz, or null while the card doesn't hold a slot (or animations
 * are off), when its motion holds still. Read the reader in drawing.
 */
@Composable
internal fun rememberAmbientTime(wants: Boolean, onScreen: Boolean): LoopReader? {
    val budget = LocalAmbientBudget.current
    val animate by rememberAnimationsEnabled()
    val owner = remember { Any() }
    val asking = budget != null && wants && onScreen && animate
    DisposableEffect(budget, asking) {
        if (asking) budget.request(owner)
        onDispose { budget?.release(owner) }
    }
    val holds by remember(budget) { derivedStateOf { budget?.holds(owner) == true } }
    if (!asking || !holds) return null
    // Only a card that holds a slot joins the frame loop, so the loop sleeps when no card moves.
    val loop = rememberLoop(AMBIENT_PERIOD_MS, LinearEasing)
    // Whole 60 Hz frames: a derived value that only changes once a frame at 60 Hz, so a faster screen redraws the card
    // at 60 Hz, not at its own rate.
    val frame = remember(loop) { derivedStateOf { (loop() * AMBIENT_PERIOD_MS / AMBIENT_FRAME_MS).toInt() } }
    return remember(frame) { LoopReader { frame.value * AMBIENT_FRAME_MS } }
}
