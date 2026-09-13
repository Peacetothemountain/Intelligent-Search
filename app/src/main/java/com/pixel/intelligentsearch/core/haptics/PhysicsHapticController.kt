package com.pixel.intelligentsearch.core.haptics

import android.view.View
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Attaches a physics-driven haptic scroll detent observer to a LazyListState.
 */
@Composable
fun rememberScrollDetentController(
    lazyListState: LazyListState,
    engine: TactileSonicEngine = rememberTactileSonicEngine()
): ScrollDetentController {
    val view = LocalView.current
    val controller = remember(engine, view) { ScrollDetentController(engine, view) }

    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val firstVisibleItemScrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }

    LaunchedEffect(firstVisibleItemIndex, firstVisibleItemScrollOffset) {
        if (lazyListState.isScrollInProgress) {
            controller.onScrollUpdate(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        }
    }

    return controller
}

/**
 * Controller for magnetic resistance and spring-release snap when dragging an overlay or dismissible item.
 */
class MagneticDismissController(
    private val thresholdPx: Float,
    private val engine: TactileSonicEngine,
    private val view: View?
) {
    private var hasCrossedThreshold = false
    private var lastEmittedProgress = 0f

    fun onDrag(currentOffsetPx: Float) {
        val absOffset = abs(currentOffsetPx)
        val progress = (absOffset / thresholdPx).coerceIn(0f, 1.2f)

        if (progress < 1.0f) {
            if (hasCrossedThreshold) {
                // Dragged back inside safe zone
                hasCrossedThreshold = false
                engine.tick(view, scale = 0.5f)
            } else if (progress > 0.25f && abs(progress - lastEmittedProgress) > 0.15f) {
                lastEmittedProgress = progress
                engine.magneticResistance(view, progress)
            }
        } else {
            // Crossed threshold boundary
            if (!hasCrossedThreshold) {
                hasCrossedThreshold = true
                engine.magneticThresholdSnap(view)
            }
        }
    }

    fun onDragEnd(willDismiss: Boolean) {
        if (willDismiss) {
            engine.springReleaseSnap(view)
        } else if (hasCrossedThreshold) {
            engine.tick(view, scale = 0.6f)
        }
        hasCrossedThreshold = false
        lastEmittedProgress = 0f
    }

    fun onDragCancel() {
        hasCrossedThreshold = false
        lastEmittedProgress = 0f
    }
}

@Composable
fun rememberMagneticDismissController(
    thresholdPx: Float,
    engine: TactileSonicEngine = rememberTactileSonicEngine()
): MagneticDismissController {
    val view = LocalView.current
    return remember(thresholdPx, engine, view) {
        MagneticDismissController(thresholdPx, engine, view)
    }
}
