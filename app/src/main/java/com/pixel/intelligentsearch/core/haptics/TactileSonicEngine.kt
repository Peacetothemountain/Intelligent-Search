package com.pixel.intelligentsearch.core.haptics

import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Unified Tactile & Sonic Sensory Coordinator
 *
 * Orchestrates linear resonant actuator (LRA) hardware primitive waveforms
 * with ultra-low-latency procedural acoustic micro-feedback for an authentic
 * mechanical physical sensation.
 */
class TactileSonicEngine private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: TactileSonicEngine? = null

        fun get(context: Context): TactileSonicEngine {
            return instance ?: synchronized(this) {
                instance ?: TactileSonicEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    val hapticEngine = PixelHapticEngine.get(context)
    val sonicEngine = SonicMicroFeedbackEngine.get(context)

    private val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(value) {
            prefs.edit().putBoolean("vibration_enabled", value).apply()
        }

    @Volatile
    var isSonicEnabled: Boolean = false

    // High-frequency debouncing timestamps
    private var lastScrollDetentTimestamp: Long = 0L
    private var lastMagneticResistanceTimestamp: Long = 0L

    /**
     * Standard interactive click (buttons, list items, search pills).
     */
    fun click(view: View? = null, scale: Float = 1.0f) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Subtle micro tick (sliders, segmented tabs, step increments).
     */
    fun tick(view: View? = null, scale: Float = 1.0f) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
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
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Magnetic resistance tick as the user drags towards a dismiss or boundary threshold.
     * Modulated dynamically by normalized progress [0.0 .. 1.0].
     */
    fun magneticResistance(view: View? = null, progress: Float) {
        val now = SystemClock.uptimeMillis()
        val p = progress.coerceIn(0f, 1f)
        val minInterval = (65L - (p * 35L).toLong()).coerceAtLeast(30L)
        if (now - lastMagneticResistanceTimestamp < minInterval) return
        lastMagneticResistanceTimestamp = now

        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Tactile snap when crossing the magnetic dismiss barrier.
     */
    fun magneticThresholdSnap(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Spring-release snap sensation when an overlay snaps shut or flings closed.
     */
    fun springReleaseSnap(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * App launch tactile tick.
     */
    fun appLaunch(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Toggle switch snap.
     */
    fun toggle(view: View? = null, isChecked: Boolean) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Math calculation live completion tick.
     */
    fun mathCalculation(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Security unlock biometric heartbeat.
     */
    fun securityHeartbeat(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Delete / clear confirmation tick.
     */
    fun deleteThud(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Overlay expansion tactile tick.
     */
    fun overlayOpen(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Overlay collapse settling tick.
     */
    fun overlayDismiss(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    /**
     * Reorder drag-and-drop item swap.
     */
    fun reorderSwap(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performPredictiveBackHaptic(view)
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
