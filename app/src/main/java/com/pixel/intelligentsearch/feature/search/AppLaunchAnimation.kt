package com.pixel.intelligentsearch.feature.search

import android.app.ActivityOptions
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt

/**
 * The App Animations setting for apps and shortcuts opened from the overlay. On, the app scales up out of the point
 * the user touched, as it does from a launcher icon; off, Android's default app-open transition plays.
 */
internal object AppLaunchAnimation {

    /**
     * The ActivityOptions for a launch from [source] (the overlay's view), growing out of [origin] in its coordinates,
     * or out of its middle when the launch didn't come from a touch (Enter on a hardware keyboard). Null when
     * [enabled] is off, which leaves the system default.
     */
    fun options(source: View, enabled: Boolean, origin: Offset?): Bundle? {
        if (!enabled) return null
        val x = origin?.x?.roundToInt() ?: (source.width / 2)
        val y = origin?.y?.roundToInt() ?: (source.height / 2)
        return ActivityOptions.makeScaleUpAnimation(source, x, y, 0, 0).toBundle()
    }
}

/** Where the user last touched the overlay, in its view's coordinates. */
internal class LaunchOrigin {
    var point: Offset? = null
    internal var layout: LayoutCoordinates? = null
}

/** Records each touch-down on this element into [origin], without taking the touch from anything below it. */
internal fun Modifier.recordLaunchOrigin(origin: LaunchOrigin): Modifier = this
    .onGloballyPositioned { origin.layout = it }
    .pointerInput(origin) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val layout = origin.layout?.takeIf { it.isAttached }
            origin.point = layout?.localToRoot(down.position) ?: down.position
        }
    }
