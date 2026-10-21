package com.pixel.intelligentsearch.core.haptics

import com.pixel.intelligentsearch.di.appEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import javax.inject.Inject
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app's haptic feedback by occasion: clicks, ticks, scroll detents, toggles, app launches and the like. Each call
 * checks the Vibration setting and plays its [PixelHapticEngine] waveform. It is haptics only; nothing is played as
 * sound.
 */
@Singleton
class TactileSonicEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val hapticEngine: PixelHapticEngine
) {

    companion object {
        /** The app-wide instance, for composables and other places without injection. */
        fun get(context: Context): TactileSonicEngine = context.appEntryPoint.tactileSonicEngine()
    }


    private val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    var isHapticEnabled: Boolean
        get() = prefs[SettingsManager.VIBRATION]
        set(value) {
            prefs.edit().put(SettingsManager.VIBRATION, value).apply()
        }

    // High-frequency debouncing timestamps
    private var lastScrollDetentTimestamp: Long = 0L

    /**
     * Standard interactive click (buttons, list items, search pills).
     */
    fun click(view: View? = null, scale: Float = 1.0f) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.CLICK, scale)
        }
    }

    /**
     * Subtle micro tick (sliders, segmented tabs, step increments).
     */
    fun tick(view: View? = null, scale: Float = 1.0f) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.TICK, scale)
        }
    }

    /**
     * Velocity-proportional detent tick when scrolling through search results.
     * Enforces a refractory period (38ms) to prevent actuator voice-coil saturation.
     */
    fun scrollDetent(view: View? = null, velocity: Float = 0f) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScrollDetentTimestamp < 38L) return
        lastScrollDetentTimestamp = now

        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.SCROLL_DETENT, 1.0f, velocity)
        }
    }

    /**
     * App launch tactile tick.
     */
    fun appLaunch(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.APP_LAUNCH)
        }
    }

    /**
     * Toggle switch snap.
     */
    fun toggle(view: View? = null, isChecked: Boolean) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, if (isChecked) PixelHapticType.TOGGLE_ON else PixelHapticType.TOGGLE_OFF)
        }
    }

    /**
     * Math calculation live completion tick.
     */
    fun mathCalculation(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.LOW_TICK)
        }
    }

    /**
     * Delete / clear confirmation tick.
     */
    fun deleteThud(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.DELETE_THUD)
        }
    }

    /**
     * Overlay collapse settling tick.
     */
    fun overlayDismiss(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.GESTURE_THRESHOLD)
        }
    }

}

/**
 * Convenient remember helper for Jetpack Compose.
 */
@Composable
fun rememberTactileSonicEngine(): TactileSonicEngine {
    val context = LocalContext.current
    return remember(context) { TactileSonicEngine.get(context) }
}
