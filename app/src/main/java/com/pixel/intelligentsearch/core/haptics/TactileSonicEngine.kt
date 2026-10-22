package com.pixel.intelligentsearch.core.haptics

import com.pixel.intelligentsearch.di.appEntryPoint
import javax.inject.Singleton
import javax.inject.Inject
import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app's haptic feedback by occasion: clicks, ticks, scroll detents, toggles, app launches and the like. Every
 * occasion plays the one app haptic, [PixelHapticEngine.performPredictiveBackHaptic], which checks the Vibration
 * setting; the names say where a haptic belongs, not how it feels. It is haptics only; nothing is played as sound.
 */
@Singleton
class TactileSonicEngine @Inject constructor(val hapticEngine: PixelHapticEngine) {

    companion object {
        /** The app-wide instance, for composables and other places without injection. */
        fun get(context: Context): TactileSonicEngine = context.appEntryPoint.tactileSonicEngine()

        /** Scroll detents closer together than this are dropped, so a fast fling doesn't buzz. */
        private const val SCROLL_DETENT_SPACING_MS = 38L
    }

    private var lastScrollDetentTimestamp: Long = 0L

    /** Standard interactive click (buttons, list items, search pills). */
    fun click(view: View? = null) = hapticEngine.performPredictiveBackHaptic(view)

    /** Touch down and step increments (presses, sliders, segmented tabs). */
    fun tick(view: View? = null) = hapticEngine.performPredictiveBackHaptic(view)

    /** A detent while scrolling through search results, at most one every [SCROLL_DETENT_SPACING_MS]. */
    fun scrollDetent(view: View? = null) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScrollDetentTimestamp < SCROLL_DETENT_SPACING_MS) return
        lastScrollDetentTimestamp = now
        hapticEngine.performPredictiveBackHaptic(view)
    }

    /** An app launch. */
    fun appLaunch(view: View? = null) = hapticEngine.performPredictiveBackHaptic(view)

    /** A toggle switched on or off. */
    fun toggle(view: View? = null) = hapticEngine.performPredictiveBackHaptic(view)

    /** A calculation's live result appearing. */
    fun mathCalculation(view: View? = null) = hapticEngine.performPredictiveBackHaptic(view)

    /** A delete or dismissal going through. */
    fun deleteThud(view: View? = null) = hapticEngine.performPredictiveBackHaptic(view)

    /** The search overlay closing. */
    fun overlayDismiss(view: View? = null) = hapticEngine.performPredictiveBackHaptic(view)
}

/**
 * Convenient remember helper for Jetpack Compose.
 */
@Composable
fun rememberTactileSonicEngine(): TactileSonicEngine {
    val context = LocalContext.current
    return remember(context) { TactileSonicEngine.get(context) }
}
