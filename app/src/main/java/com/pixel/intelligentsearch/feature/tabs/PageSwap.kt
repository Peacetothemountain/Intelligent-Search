package com.pixel.intelligentsearch.feature.tabs

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.pixel.intelligentsearch.feature.tabs.ui.Springs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Two pages sliding past each other sideways on the spatial spring, as the mockup's tab switches and sub-pages do:
 * the page in [front] comes in from the side [direction] points to (+1: from the right) while the one [behind] it
 * leaves the other way. Nothing fades. A back gesture can hold the slide part-way ([begin], [seek]) and then finish
 * it ([settle]) or put it back ([revert]).
 */
@Stable
class PageSwap<T : Any>(initial: T) {
    /** The page shown, or coming in. */
    var front: T by mutableStateOf(initial)
        private set

    /** The page leaving, while it is still in sight. */
    var behind: T? by mutableStateOf(null)
        private set

    /** +1 when [front] comes in from the right (a step forward), -1 from the left (a step back). */
    var direction: Int by mutableIntStateOf(1)
        private set

    /** 0 shows [behind] where the page sits, 1 shows [front] there. */
    val progress = Animatable(1f)

    /** Slides to [to]. Going back to the page that is leaving turns the slide around where it is. */
    suspend fun go(to: T, direction: Int) {
        if (behind == null && to == front) return
        begin(to, direction)
        settle()
    }

    /** Starts a slide to [to] without moving yet (a back gesture's start). */
    suspend fun begin(to: T, direction: Int) {
        val leaving = behind
        if (leaving != null && to == leaving) {
            // Turn around mid-slide: the same picture, seen from the other side.
            behind = front
            front = to
            this.direction = -this.direction
            progress.snapTo(1f - progress.value)
            return
        }
        if (to == front) return
        behind = front
        front = to
        this.direction = direction
        progress.snapTo(0f)
    }

    /** Holds the slide at [fraction] of the way (a back gesture in progress). */
    suspend fun seek(fraction: Float) = progress.snapTo(fraction)

    /** Finishes the slide on the spring; the page behind leaves composition once it is out of sight. */
    suspend fun settle() {
        progress.animateTo(1f, Springs.spatial())
        behind = null
    }

    /** Puts the slide back (a cancelled back gesture): the page behind is the one shown again. */
    suspend fun revert() {
        val back = behind ?: return
        progress.animateTo(0f, Springs.spatial())
        front = back
        behind = null
        progress.snapTo(1f)
    }

    /** Where [page] sits now, in page widths from its place (0 = in place, 1 = one page width to the right). */
    fun offsetOf(page: T): Float {
        val p = progress.value
        return when (page) {
            front -> direction * (1f - p)
            behind -> -direction * p
            else -> 0f
        }
    }

    /** How much [page] is in place: 1 when it alone is shown, 0 when it is out of sight. */
    fun shareOf(page: T): Float {
        val p = progress.value
        return when (page) {
            front -> if (behind == null) 1f else p
            behind -> 1f - p
            else -> 0f
        }
    }
}

/** Moves the element sideways to where [swap] has [page] now (read while drawing, so the slide only redraws). */
fun <T : Any> Modifier.swapOffset(swap: PageSwap<T>, page: T): Modifier =
    graphicsLayer { translationX = swap.offsetOf(page) * size.width }

/**
 * The pages [swap] has in sight, each in its own place, each keeping its saved state (scroll position and the rest)
 * while it is away, so going back finds it as it was left.
 */
@Composable
fun <T : Any> PageSwapHost(swap: PageSwap<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val holder = rememberSaveableStateHolder()
    Box(modifier) {
        for (page in listOfNotNull(swap.behind, swap.front)) {
            key(page) {
                Box(Modifier.fillMaxSize().swapOffset(swap, page)) {
                    holder.SaveableStateProvider(page.toString()) { content(page) }
                }
            }
        }
    }
}

/**
 * Predictive back from [swap]'s page to [to] (to its left), while [enabled]: the gesture pulls [to] in from the left
 * part-way ([PEEK] of a page) as the finger moves; letting go finishes the slide on the spring, and a cancelled gesture
 * springs it back. [onCommitted] runs once the gesture is committed, before the slide finishes.
 */
@Composable
fun <T : Any> PredictiveBackSwap(swap: PageSwap<T>, to: () -> T, enabled: Boolean, onCommitted: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val currentTo by rememberUpdatedState(to)
    val committed by rememberUpdatedState(onCommitted)
    PredictiveBackHandler(enabled = enabled) { events ->
        val target = currentTo()
        try {
            swap.begin(target, direction = -1)
            events.collect { event: BackEventCompat -> swap.seek(PEEK * event.progress) }
            committed()
            scope.launch { swap.settle() }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { swap.revert() }
            throw e
        }
    }
}

/** How far a back gesture pulls the page underneath in before it is let go, as a share of a page. */
private const val PEEK = 0.3f
