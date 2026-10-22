package com.pixel.intelligentsearch.core.haptics

import com.pixel.intelligentsearch.di.appEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import javax.inject.Inject
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

/** The two halves of the system back gesture's haptic, which are the only haptics the app plays. */
private enum class GestureThreshold {
    /** The swipe crossing the point where letting go commits it. Every tap, press, toggle, drag step and landing. */
    ACTIVATE,

    /** The swipe pulled back under that point, so letting go cancels it: a gesture sprung back before it committed. */
    DEACTIVATE,
}

/**
 * The app's one haptic: the haptic Pixel's predictive back gesture plays (SystemUI's back panel), measured on a
 * Pixel 11 Pro XL on Android 17 with `dumpsys vibrator_manager`. Crossing the commit threshold plays
 * [HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE], a predefined TICK; pulling back under it plays
 * [HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE], a TICK primitive at 0.4. Every haptic in the app goes
 * through [performPredictiveBackHaptic] or [performPredictiveBackCancelHaptic], and each checks the Vibration setting.
 *
 * Given a view on Android 14+, the haptic is asked for by its constant, exactly as SystemUI asks, so the system plays
 * its own effect scaled by the user's touch vibration intensity. Without a view, or where the view can't play it,
 * the vibrator plays the same effect as a touch vibration, which the system scales the same way. Before Android 14
 * the commit tick is [HapticFeedbackConstants.CLOCK_TICK], which those versions play as the same predefined TICK.
 */
@Singleton
class PixelHapticEngine @Inject constructor(@param:ApplicationContext private val context: Context) {

    companion object {
        /** The app-wide instance, for composables and other places without injection. */
        fun get(context: Context): PixelHapticEngine = context.appEntryPoint.pixelHapticEngine()

        /** The scale of the TICK primitive the system plays for [HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE]. */
        internal const val DEACTIVATE_TICK_SCALE = 0.4f
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

    /** The back gesture's commit haptic, which the app plays for every haptic but a cancelled gesture. */
    fun performPredictiveBackHaptic(view: View? = null) {
        perform(view, GestureThreshold.ACTIVATE)
    }

    /** The back gesture's cancel haptic, for a gesture pulled back under its threshold before it committed. */
    fun performPredictiveBackCancelHaptic(view: View? = null) {
        perform(view, GestureThreshold.DEACTIVATE)
    }

    private fun perform(view: View?, threshold: GestureThreshold) {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs[SettingsManager.VIBRATION]) return
        val constant = feedbackConstant(threshold)
        if (view != null && constant != null) {
            try {
                // The app's own Vibration setting decides, as the view's setting is not one the user can change.
                if (view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)) return
            } catch (_: Exception) {}
        }
        vibrate(threshold)
    }

    /** The view haptic constant for [threshold], or null where this Android version has none and the vibrator plays it. */
    private fun feedbackConstant(threshold: GestureThreshold): Int? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> when (threshold) {
            GestureThreshold.ACTIVATE -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            GestureThreshold.DEACTIVATE -> HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
        }
        threshold == GestureThreshold.ACTIVATE -> HapticFeedbackConstants.CLOCK_TICK
        else -> null
    }

    /** Plays [threshold]'s effect on the vibrator, as the system builds it for the view haptic. */
    private fun vibrate(threshold: GestureThreshold) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            val effect = when (threshold) {
                GestureThreshold.ACTIVATE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                GestureThreshold.DEACTIVATE ->
                    if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, DEACTIVATE_TICK_SCALE)
                            .compose()
                    } else {
                        // Without the primitive, the plain predefined tick.
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, touchVibrationAttributes)
            } else {
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {}
    }
}
