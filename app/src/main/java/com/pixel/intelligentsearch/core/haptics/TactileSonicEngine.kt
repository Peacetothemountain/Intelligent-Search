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

    @Volatile
    var isHapticEnabled: Boolean = true

    @Volatile
    var isSonicEnabled: Boolean = true

    // High-frequency debouncing timestamps
    private var lastScrollDetentTimestamp: Long = 0L
    private var lastMagneticResistanceTimestamp: Long = 0L

    /**
     * Standard interactive click (buttons, list items, search pills).
     */
    fun click(view: View? = null, scale: Float = 1.0f) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.CLICK, amplitudeScale = scale)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.CLICK, volumeScale = scale)
        }
    }

    /**
     * Subtle micro tick (sliders, segmented tabs, step increments).
     */
    fun tick(view: View? = null, scale: Float = 1.0f) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.TICK, amplitudeScale = scale)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.TICK, volumeScale = scale * 0.8f)
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

        val velAbs = kotlin.math.abs(velocity)
        val normalizedVel = (velAbs / 3500f).coerceIn(0f, 1f)
        val scale = 0.35f + 0.45f * normalizedVel

        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.SCROLL_DETENT, amplitudeScale = scale, velocity = velocity)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.TICK, volumeScale = scale * 0.6f)
        }
    }

    /**
     * Magnetic resistance tick as the user drags towards a dismiss or boundary threshold.
     * Modulated dynamically by normalized progress [0.0 .. 1.0].
     */
    fun magneticResistance(view: View? = null, progress: Float) {
        val now = SystemClock.uptimeMillis()
        val p = progress.coerceIn(0f, 1f)
        // Adaptive refractory interval: closer to threshold -> more frequent ticks
        val minInterval = (65L - (p * 35L).toLong()).coerceAtLeast(30L)
        if (now - lastMagneticResistanceTimestamp < minInterval) return
        lastMagneticResistanceTimestamp = now

        val scale = (0.25f + 0.55f * p * p)
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.MAGNETIC_RESISTANCE, amplitudeScale = scale)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.TICK, volumeScale = scale * 0.5f)
        }
    }

    /**
     * Explosive tactile snap when crossing the magnetic dismiss barrier.
     */
    fun magneticThresholdSnap(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.GESTURE_THRESHOLD, amplitudeScale = 1.0f)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.MAGNETIC_PING, volumeScale = 0.85f)
        }
    }

    /**
     * Spring-release snap sensation when an overlay snaps shut or flings closed.
     */
    fun springReleaseSnap(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.SPRING_RELEASE, amplitudeScale = 0.95f)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.DELETE_THUD, volumeScale = 0.70f)
        }
    }

    /**
     * Impactful app launch transient (sharp acceleration into crisp physical launch).
     */
    fun appLaunch(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.APP_LAUNCH, amplitudeScale = 1.0f)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.APP_LAUNCH, volumeScale = 0.90f)
        }
    }

    /**
     * Mechanical toggle switch snap (rising click for ON, falling detent for OFF).
     */
    fun toggle(view: View? = null, isChecked: Boolean) {
        val hapticType = if (isChecked) PixelHapticType.TOGGLE_ON else PixelHapticType.TOGGLE_OFF
        val sonicType = if (isChecked) SonicMicroFeedbackEngine.SonicType.TOGGLE_ON else SonicMicroFeedbackEngine.SonicType.TOGGLE_OFF

        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, hapticType, amplitudeScale = 1.0f)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(sonicType, volumeScale = 0.85f)
        }
    }

    /**
     * Math calculation live completion tick.
     */
    fun mathCalculation(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.MATH_CALCULATION, amplitudeScale = 0.75f)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.MATH_TICK, volumeScale = 0.70f)
        }
    }

    /**
     * Security unlock biometric heartbeat (bi-phasic myocardial pulse).
     */
    fun securityHeartbeat(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.SECURITY_HEARTBEAT, amplitudeScale = 1.0f)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.SECURITY_HEARTBEAT, volumeScale = 0.85f)
        }
    }

    /**
     * Heavy delete / clear confirmation thud.
     */
    fun deleteThud(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.DELETE_THUD, amplitudeScale = 1.0f)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.DELETE_THUD, volumeScale = 0.95f)
        }
    }

    /**
     * Overlay expansion tactile swell.
     */
    fun overlayOpen(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.OVERLAY_OPEN, amplitudeScale = 0.85f)
        }
    }

    /**
     * Overlay collapse settling thud.
     */
    fun overlayDismiss(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.OVERLAY_DISMISS, amplitudeScale = 0.85f)
        }
    }

    /**
     * Reorder drag-and-drop item swap.
     */
    fun reorderSwap(view: View? = null) {
        if (isHapticEnabled) {
            hapticEngine.performHaptic(view, PixelHapticType.REORDER_SWAP, amplitudeScale = 0.85f)
        }
        if (isSonicEnabled) {
            sonicEngine.playSonic(SonicMicroFeedbackEngine.SonicType.TICK, volumeScale = 0.75f)
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
