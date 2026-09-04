package com.pixel.intelligentsearch.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

enum class PixelHapticType {
    CLICK,
    TICK,
    REORDER_SWAP,
    OVERLAY_OPEN,
    OVERLAY_DISMISS,
    GESTURE_THRESHOLD,
    SPIN_TICK,
    HEAVY_IMPACT,
    TOGGLE_ON,
    TOGGLE_OFF,
    CONFIRM,
    REJECT
}

class PixelHapticEngine(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Perform high-fidelity Pixel LRA haptic feedback
     */
    fun performHaptic(view: View? = null, type: PixelHapticType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null && vibrator.hasVibrator()) {
            val composition = VibrationEffect.startComposition()
            when (type) {
                PixelHapticType.CLICK -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.TICK -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.REORDER_SWAP -> {
                    if (vibrator.areAllPrimitivesSupported(
                            VibrationEffect.Composition.PRIMITIVE_CLICK,
                            VibrationEffect.Composition.PRIMITIVE_TICK
                        )
                    ) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 20)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.OVERLAY_OPEN -> {
                    if (vibrator.areAllPrimitivesSupported(
                            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                            VibrationEffect.Composition.PRIMITIVE_CLICK
                        )
                    ) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.65f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f, 15)
                        vibrator.vibrate(composition.compose())
                        return
                    } else if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.OVERLAY_DISMISS -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.GESTURE_THRESHOLD -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.SPIN_TICK -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SPIN)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.5f)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.HEAVY_IMPACT -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.TOGGLE_ON -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.45f, 20)
                        vibrator.vibrate(composition.compose())
                        return
                    } else if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.TOGGLE_OFF -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_THUD)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.35f, 20)
                        vibrator.vibrate(composition.compose())
                        return
                    } else if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.CONFIRM -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f, 40)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
                PixelHapticType.REJECT -> {
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f, 30)
                        vibrator.vibrate(composition.compose())
                        return
                    }
                }
            }
        }

        // Vibrator fallback when view is null or primitives are unavailable
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effect = when (type) {
                        PixelHapticType.CLICK, PixelHapticType.OVERLAY_OPEN ->
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        PixelHapticType.TICK, PixelHapticType.SPIN_TICK, PixelHapticType.TOGGLE_OFF ->
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        PixelHapticType.HEAVY_IMPACT, PixelHapticType.OVERLAY_DISMISS ->
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        PixelHapticType.CONFIRM ->
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                        else ->
                            VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                    vibrator.vibrate(effect)
                    return
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(25)
                    return
                }
            } catch (e: Exception) {}
        }

        // System view fallback
        view?.let {
            when (type) {
                PixelHapticType.CLICK -> it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                PixelHapticType.TICK, PixelHapticType.SPIN_TICK -> it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                PixelHapticType.REORDER_SWAP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        it.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                    } else {
                        it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                PixelHapticType.OVERLAY_OPEN, PixelHapticType.OVERLAY_DISMISS, PixelHapticType.GESTURE_THRESHOLD, PixelHapticType.HEAVY_IMPACT -> {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                PixelHapticType.TOGGLE_ON -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        it.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
                    } else {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
                PixelHapticType.TOGGLE_OFF -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        it.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
                    } else {
                        it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                PixelHapticType.CONFIRM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
                PixelHapticType.REJECT -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        it.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    } else {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
        }
    }
}
