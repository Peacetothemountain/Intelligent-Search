package com.pixel.intelligentsearch.core.haptics

import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.abs

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
    SPRING_RELEASE,
    // Material 3 Expressive semantic tactile profiles (Android 17 Canary ZP11)
    SEARCH_RESULT_COMMIT,
    SEGMENT_SWITCH,
    EXPAND_COLLAPSE,
    SLIDER_DETENT,
    WARP_SPEED,
    PREDICTIVE_BACK_PROGRESS
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

    private val vibrator: Vibrator? = run {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    }

    /**
     * VibrationAttributes and Vibrator.vibrate(VibrationEffect, VibrationAttributes) are API 33+.
     * Resolved only behind an SDK check; on Android 12/12L the class does not exist, and touching it
     * would raise NoClassDefFoundError, which `catch (Exception)` cannot intercept.
     */
    @get:androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val touchVibrationAttributes: VibrationAttributes by lazy {
        VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_TOUCH)
            .build()
    }

    // Thread-safe primitive support matrix to avoid repeated IPC querying & race conditions
    private val primitiveSupportMap = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    private fun isPrimitiveSupported(primitiveId: Int): Boolean {
        if (vibrator == null || !vibrator.hasVibrator()) return false
        return primitiveSupportMap.computeIfAbsent(primitiveId) {
            try {
                vibrator.areAllPrimitivesSupported(primitiveId)
            } catch (_: Throwable) {
                false
            }
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
        type: PixelHapticType,
        amplitudeScale: Float = 1.0f,
        velocity: Float = 0f
    ) {
        performHaptic(view = null, type = type, amplitudeScale = amplitudeScale, velocity = velocity)
    }

    fun performHaptic(
        view: View? = null,
        type: PixelHapticType = PixelHapticType.LOW_TICK,
        amplitudeScale: Float = 1.0f,
        velocity: Float = 0f
    ) {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs[SettingsManager.VIBRATION]) return

        val effect = composeWaveform(type, amplitudeScale, velocity)
        if (effect != null) {
            vibrateWithAttributes(effect)
        } else if (view != null) {
            performViewFallback(view, type)
        } else {
            performPredictiveBackHaptic(view)
        }
    }

    private fun vibrateWithAttributes(effect: VibrationEffect) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator?.vibrate(effect, touchVibrationAttributes)
            } else {
                vibrator?.vibrate(effect)
            }
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
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
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

                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
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
                val hasLowTick = isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)

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
                val hasLowTick = isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)

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

            PixelHapticType.SEARCH_RESULT_COMMIT -> {
                // Crisp dual-harmonic commit tick for launching instant query results
                if (arePrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
                    )
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.40f * scale, 12)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.80f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.SEGMENT_SWITCH -> {
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.50f * scale)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.40f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.EXPAND_COLLAPSE -> {
                if (arePrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                        VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                    )
                ) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.45f * scale)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.55f * scale, 15)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.60f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.SLIDER_DETENT -> {
                val tickScale = (0.30f * scale).coerceIn(0.1f, 1.0f)
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, tickScale)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, tickScale)
                    return composition.compose()
                }
            }

            PixelHapticType.WARP_SPEED -> {
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_SPIN)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.40f * scale)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f * scale)
                    return composition.compose()
                }
            }

            PixelHapticType.PREDICTIVE_BACK_PROGRESS -> {
                val progressScale = (0.20f + 0.60f * scale).coerceIn(0.1f, 1.0f)
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, progressScale)
                    return composition.compose()
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, progressScale)
                    return composition.compose()
                }
            }
        }

        return null
    }

    private fun performViewFallback(view: View, type: PixelHapticType) {
        val feedbackConstant = when (type) {
            PixelHapticType.CLICK, PixelHapticType.APP_LAUNCH, PixelHapticType.SEARCH_RESULT_COMMIT ->
                HapticFeedbackConstants.KEYBOARD_TAP
            PixelHapticType.HEAVY_IMPACT, PixelHapticType.DELETE_THUD ->
                HapticFeedbackConstants.LONG_PRESS
            PixelHapticType.CONFIRM ->
                HapticFeedbackConstants.CONFIRM
            PixelHapticType.REJECT ->
                HapticFeedbackConstants.REJECT
            PixelHapticType.GESTURE_THRESHOLD ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK
            PixelHapticType.REORDER_SWAP ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.DRAG_START else HapticFeedbackConstants.KEYBOARD_TAP
            PixelHapticType.TOGGLE_ON ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) HapticFeedbackConstants.TOGGLE_ON else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.KEYBOARD_TAP
            PixelHapticType.TOGGLE_OFF ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) HapticFeedbackConstants.TOGGLE_OFF else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.KEYBOARD_TAP
            PixelHapticType.SEGMENT_SWITCH ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            PixelHapticType.SLIDER_DETENT ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            else ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(feedbackConstant)
    }

    /**
     * Android 17 QPR2 Beta 5 Predictive Back gesture vibration.
     * Standardized across all interactive touch, click, swipe, gesture, and press events.
     */
    fun performPredictiveBackHaptic(view: View? = null) {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs[SettingsManager.VIBRATION]) {
            return
        }

        val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        if (view != null) {
            try {
                if (view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, flags)) {
                    return
                }
            } catch (_: Exception) {}
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
                    val composition = VibrationEffect.startComposition()
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1.0f)
                    vibrateWithAttributes(composition.compose())
                } else if (isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                    val composition = VibrationEffect.startComposition()
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.60f)
                    vibrateWithAttributes(composition.compose())
                } else {
                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    vibrateWithAttributes(effect)
                }
            } catch (_: Exception) {}
        }
    }
}
