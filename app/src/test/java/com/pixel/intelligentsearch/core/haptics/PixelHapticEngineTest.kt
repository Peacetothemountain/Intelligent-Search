package com.pixel.intelligentsearch.core.haptics

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSystemVibrator

/**
 * The app's one haptic is Pixel's predictive back gesture haptic, measured on a Pixel 11 Pro XL: SystemUI asks a view
 * for GESTURE_THRESHOLD_ACTIVATE as the swipe commits and GESTURE_THRESHOLD_DEACTIVATE as it is pulled back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [PixelHapticEngineTest.RecordingVibrator::class])
class PixelHapticEngineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val vibrator get() = shadowOf(context.getSystemService(VibratorManager::class.java).defaultVibrator)
    private val engine by lazy { PixelHapticEngine(context) }

    /** A view that records the haptic constants asked of it, as an attached view passes them to its window. */
    private class RecordingView(context: Context, private val canPlay: Boolean = true) : View(context) {
        val played = mutableListOf<Int>()
        val flags = mutableListOf<Int>()

        override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
            played += feedbackConstant
            this.flags += flags
            return canPlay
        }
    }

    /** Records every vibration the app asks the vibrator for, and its attributes. */
    @Implements(className = "android.os.SystemVibrator", isInAndroidSdk = false)
    class RecordingVibrator : ShadowSystemVibrator() {
        @Implementation(minSdk = 33)
        protected fun vibrate(uid: Int, opPkg: String?, effect: VibrationEffect, reason: String?, attributes: VibrationAttributes) {
            vibrations += effect
            usages += attributes.usage
            super.vibrate(uid, opPkg, effect as Any, reason, attributes as Any)
        }

        companion object {
            val vibrations = mutableListOf<VibrationEffect>()
            val usages = mutableListOf<Int>()
        }
    }

    private val vibrations get() = RecordingVibrator.vibrations
    private val tick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    private val tickAtFortyPercent = VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
        .compose()

    @Before fun setUp() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .clear().put(SettingsManager.VIBRATION, true).commit()
        vibrator.setHasVibrator(true)
        RecordingVibrator.vibrations.clear()
        RecordingVibrator.usages.clear()
    }

    private fun turnVibrationOff() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .put(SettingsManager.VIBRATION, false).commit()
    }

    @Test
    fun theHapticIsTheBackGesturesCommitHaptic() {
        val view = RecordingView(context)
        engine.performPredictiveBackHaptic(view)
        assertEquals(listOf(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE), view.played)
        // The app's Vibration setting decides, not the view's.
        assertEquals(listOf(HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING), view.flags)
        // The system plays it; the app doesn't vibrate as well.
        assertEquals(emptyList<VibrationEffect>(), vibrations)
    }

    @Test
    fun theCancelHapticIsTheBackGesturesDeactivateHaptic() {
        val view = RecordingView(context)
        engine.performPredictiveBackCancelHaptic(view)
        assertEquals(listOf(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE), view.played)
        assertEquals(emptyList<VibrationEffect>(), vibrations)
    }

    @Test
    fun theOccasionsAllPlayTheSameHaptic() {
        val view = RecordingView(context)
        val occasions = TactileSonicEngine(engine)
        occasions.click(view)
        occasions.tick(view)
        occasions.toggle(view)
        occasions.appLaunch(view)
        occasions.mathCalculation(view)
        occasions.deleteThud(view)
        occasions.overlayDismiss(view)
        occasions.scrollDetent(view)
        assertEquals(List(8) { HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE }, view.played)
    }

    @Test
    fun withoutAViewTheVibratorPlaysTheSameTickAsATouchVibration() {
        engine.performPredictiveBackHaptic()
        // The back gesture's Prebaked=TICK(MEDIUM), scaled by the system's touch vibration intensity.
        assertEquals(listOf(tick), vibrations)
        assertEquals(listOf(VibrationAttributes.USAGE_TOUCH), RecordingVibrator.usages)
    }

    @Test
    fun aViewThatCannotPlayItLeavesItToTheVibrator() {
        val view = RecordingView(context, canPlay = false)
        engine.performPredictiveBackHaptic(view)
        assertEquals(listOf(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE), view.played)
        assertEquals(listOf(tick), vibrations)
    }

    @Test
    fun withoutAViewTheCancelHapticIsTheSystemsTickAtFortyPercent() {
        vibrator.setSupportedPrimitives(listOf(VibrationEffect.Composition.PRIMITIVE_TICK))
        engine.performPredictiveBackCancelHaptic()
        // The back gesture's PresetSegment{TICK, scale=0.40}.
        assertEquals(listOf(tickAtFortyPercent), vibrations)
        assertEquals(listOf(VibrationAttributes.USAGE_TOUCH), RecordingVibrator.usages)
    }

    @Test
    fun withoutTheTickPrimitiveTheCancelHapticIsThePlainTick() {
        vibrator.setSupportedPrimitives(emptyList())
        engine.performPredictiveBackCancelHaptic()
        assertEquals(listOf(tick), vibrations)
    }

    @Test
    @Config(sdk = [33])
    fun beforeAndroid14TheCommitHapticIsTheClockTick() {
        // Android 12 and 13 have no gesture threshold constants; they play CLOCK_TICK as the same predefined TICK.
        val view = RecordingView(context)
        engine.performPredictiveBackHaptic(view)
        assertEquals(listOf(HapticFeedbackConstants.CLOCK_TICK), view.played)
        assertEquals(emptyList<VibrationEffect>(), vibrations)
    }

    @Test
    @Config(sdk = [33])
    fun beforeAndroid14TheVibratorPlaysTheCancelHaptic() {
        vibrator.setSupportedPrimitives(listOf(VibrationEffect.Composition.PRIMITIVE_TICK))
        val view = RecordingView(context)
        engine.performPredictiveBackCancelHaptic(view)
        assertEquals(emptyList<Int>(), view.played)
        assertEquals(listOf(tickAtFortyPercent), vibrations)
    }

    @Test
    fun theVibrationSettingOffSilencesBothHaptics() {
        turnVibrationOff()
        val view = RecordingView(context)
        engine.performPredictiveBackHaptic(view)
        engine.performPredictiveBackCancelHaptic(view)
        engine.performPredictiveBackHaptic()
        engine.performPredictiveBackCancelHaptic()
        TactileSonicEngine(engine).click(view)
        assertEquals(emptyList<Int>(), view.played)
        assertEquals(emptyList<VibrationEffect>(), vibrations)
    }
}
