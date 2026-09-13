package com.pixel.intelligentsearch.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.abs
import kotlin.math.min

enum class PixelHapticType {
    CLICK,
    TICK,
    LOW_TICK,
    REORDER_SWAP,
    OVERLAY_OPEN,
    OVERLAY_DISMISS,
    GESTURE_THRESHOLD,
    SPIN_TICK,
    HEAVY_IMPACT,
    TOGGLE_ON,
    TOGGLE_OFF,
    CONFIRM,
    REJECT,
    APP_LAUNCH,
    MATH_CALCULATION,
    SECURITY_HEARTBEAT,
    DELETE_THUD,
    SCROLL_DETENT,
    MAGNETIC_RESISTANCE,
    SPRING_RELEASE
}

/**
 * Pixel High-Fidelity Haptic Engine
 *
 * Specifically architected for Google Pixel dual-actuator and modern Linear Resonant Actuator (LRA) hardware.
 * Employs hardware primitive compositions with dynamic amplitude scaling (0.0 to 1.0),
 * velocity-coupled impedance, interval modulation, and low-latency VibrationAttributes.
 */
class PixelHapticEngine(private val context: Context) {

    companion object {
        @Volatile
        private var instance: PixelHapticEngine? = null

        fun get(context: Context): PixelHapticEngine {
            return instance ?: synchronized(this) {
                instance ?: PixelHapticEngine(context.applicationContext).also { instance = it }
            }
        }

        // Detect high-fidelity Pixel hardware
        val isPixelDevice: Boolean by lazy {
            Build.MANUFACTURER.equals("Google", ignoreCase = true)
        }
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val touchVibrationAttributes: VibrationAttributes by lazy {
        VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_TOUCH)
            .build()
    }

    // Cache primitive support matrix to avoid repeated IPC querying
    private val primitiveSupportMap = mutableMapOf<Int, Boolean>()

    private fun isPrimitiveSupported(primitiveId: Int): Boolean {
        if (vibrator == null || !vibrator.hasVibrator()) return false
        return primitiveSupportMap.getOrPut(primitiveId) {
            vibrator.areAllPrimitivesSupported(primitiveId)
        }
    }

    private fun arePrimitivesSupported(vararg primitives: Int): Boolean {
        if (vibrator == null || !vibrator.hasVibrator()) return false
        return primitives.all { isPrimitiveSupported(it) }
    }

    /**
     * Perform high-fidelity tactile feedback with dynamic amplitude scaling and velocity coupling.
     *
     * @param view Optional View for system haptic feedback fallback.
     * @param type Semantic tactile profile to execute.
     * @param amplitudeScale Dynamic scaling factor [0.0f .. 1.0f] to modulate waveform intensity.
     * @param velocity Optional gesture velocity (px/sec) to couple mechanical impedance.
     */
    /**
     * Perform high-fidelity tactile feedback with dynamic amplitude scaling and velocity coupling.
     * Standardized to the subtle predictive back vibration across the entire application.
     */
    fun performHaptic(
        view: View? = null,
        type: PixelHapticType = PixelHapticType.LOW_TICK,
        amplitudeScale: Float = 1.0f,
        velocity: Float = 0f
    ) {
        performPredictiveBackHaptic(view)
    }

    private fun vibrateWithAttributes(effect: VibrationEffect) {
        try {
            vibrator?.vibrate(effect, touchVibrationAttributes)
        } catch (_: Exception) {
            try {
                vibrator?.vibrate(effect)
            } catch (_: Exception) {}
        }
    }

    /**
     * Builds custom VibrationEffect.Composition waveforms using hardware primitives.
     */
    private fun composeWaveform(
        type: PixelHapticType,
        scale: Float,
        velocity: Float
    ): VibrationEffect? {
        val composition = VibrationEffect.startComposition()

        when (type) {
            PixelHapticType.CLICK -> {
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.70f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.TICK -> {
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.40f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.LOW_TICK -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, (0.85f * scale).coerceIn(0.1f, 1.0f))
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (0.70f * scale).coerceIn(0.1f, 1.0f))
                    return composition.compose()
                }
            }

            PixelHapticType.SCROLL_DETENT -> {
                // Velocity-scaled scroll detent: faster scroll yields crisper detents
                val velFactor = (abs(velocity) / 3000f).coerceIn(0f, 1f)
                val detentScale = (0.25f + 0.45f * velFactor) * scale

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, detentScale)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, detentScale)
                    return composition.compose()
                }
            }

            PixelHapticType.APP_LAUNCH -> {
                // App Launch Impact: Acceleration ramp -> Sharp Click -> Deceleration fall
                val hasRiseFall = arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
                )
                if (hasRiseFall) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.40f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f * scale, 10)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.35f * scale, 15)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.90f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.TOGGLE_ON -> {
                // Toggle On: Sharp initial tap ramping into an expressive upward rise
                if (arePrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
                    )
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.50f * scale, 15)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.TOGGLE_OFF -> {
                // Toggle Off: Quick falling deceleration settling into a grounded detent
                val hasLowTick = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)

                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL) && hasLowTick) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.50f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.40f * scale, 15)
                    return composition.compose()
                } else if (arePrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        VibrationEffect.Composition.PRIMITIVE_THUD
                    )
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.40f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.35f * scale, 18)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.50f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.MATH_CALCULATION -> {
                // Math completion: Ultra-delicate micro tick
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.SECURITY_HEARTBEAT -> {
                // Biometric Unlock Heartbeat: Bi-phasic cardiac pulse (Lub - Dub)
                val hasAll = arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
                )
                if (hasAll) {
                    // Systole (Lub)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.45f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.65f * scale, 10)
                    // Diastole (Dub)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.75f * scale, 85)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.45f * scale, 10)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.60f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f * scale, 90)
                    return composition.compose()
                }
            }

            PixelHapticType.DELETE_THUD -> {
                // Delete / Dismiss confirmation: Heavy dampening thud
                val hasLowTick = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)

                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_THUD) && hasLowTick) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.85f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.40f * scale, 22)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.90f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.MAGNETIC_RESISTANCE -> {
                // Progressive magnetic resistance near threshold
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.50f * scale)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.GESTURE_THRESHOLD -> {
                if (arePrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                        VibrationEffect.Composition.PRIMITIVE_CLICK
                    )
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.70f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f * scale, 12)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.80f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.SPRING_RELEASE -> {
                if (arePrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                        VibrationEffect.Composition.PRIMITIVE_THUD
                    )
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.65f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.50f * scale, 15)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.60f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.OVERLAY_OPEN -> {
                if (arePrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                        VibrationEffect.Composition.PRIMITIVE_CLICK
                    )
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.65f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f * scale, 15)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.80f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.OVERLAY_DISMISS -> {
                if (arePrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_THUD,
                        VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
                    )
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.60f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.35f * scale, 12)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.65f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.REORDER_SWAP -> {
                if (arePrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_TICK
                    )
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.60f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.30f * scale, 20)
                    return composition.compose()
                }
            }

            PixelHapticType.SPIN_TICK -> {
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_SPIN)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.50f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.HEAVY_IMPACT -> {
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.CONFIRM -> {
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.70f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.40f * scale, 40)
                    return composition.compose()
                }
            }

            PixelHapticType.REJECT -> {
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.70f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.50f * scale, 30)
                    return composition.compose()
                }
            }
        }

        return null
    }

    private fun performViewFallback(view: View, type: PixelHapticType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Subtle predictive-back level micro-haptic tick used consistently throughout the application.
     */
    fun performPredictiveBackHaptic(view: View? = null) {
        var vibrated = false
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                ) {
                    val composition = VibrationEffect.startComposition()
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.85f)
                    vibrateWithAttributes(composition.compose())
                    vibrated = true
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    val composition = VibrationEffect.startComposition()
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.40f)
                    vibrateWithAttributes(composition.compose())
                    vibrated = true
                } else {
                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    vibrateWithAttributes(effect)
                    vibrated = true
                }
            } catch (_: Exception) {}
        }

        if (!vibrated && view != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            } catch (_: Exception) {}
        }
    }
}
