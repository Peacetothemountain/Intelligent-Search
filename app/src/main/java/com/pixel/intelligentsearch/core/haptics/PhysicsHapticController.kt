package com.pixel.intelligentsearch.core.haptics

import android.view.View
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

/**
 * Controller for physics-coupled tactile detents during LazyList scrolling.
 */
class ScrollDetentController(
    private val engine: TactileSonicEngine,
    private val view: View?
) {
    private var lastIndex = -1
    private var lastScrollOffset = 0
    private var lastTimeMs = 0L

    fun onScrollUpdate(firstVisibleIndex: Int, firstVisibleOffset: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        val dt = (now - lastTimeMs).coerceAtLeast(1L)
        lastTimeMs = now

        if (lastIndex == -1) {
            lastIndex = firstVisibleIndex
            lastScrollOffset = firstVisibleOffset
            return
        }

        val deltaIndex = firstVisibleIndex - lastIndex
        val deltaOffset = (firstVisibleOffset - lastScrollOffset) + (deltaIndex * 150)
        val velocity = (deltaOffset.toFloat() / dt.toFloat()) * 1000f

        lastIndex = firstVisibleIndex
        lastScrollOffset = firstVisibleOffset

        // If item boundary crossed or large delta traversed
        if (deltaIndex != 0 || abs(deltaOffset) > 85) {
            engine.scrollDetent(view, velocity)
        }
    }
}

/**
 * Attaches a physics-driven haptic scroll detent observer to a LazyListState. The scroll position is watched from
 * an effect, never read during composition, so scrolling doesn't recompose the screen that calls this.
 */
@Composable
fun rememberScrollDetentController(
    lazyListState: LazyListState,
    engine: TactileSonicEngine = rememberTactileSonicEngine()
): ScrollDetentController {
    val view = LocalView.current
    val controller = remember(engine, view) { ScrollDetentController(engine, view) }

    LaunchedEffect(lazyListState, controller) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (lazyListState.isScrollInProgress) controller.onScrollUpdate(index, offset)
            }
    }

    return controller
}

