package com.ngdesigns.intelligentsearch.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Whether the user wants haptics; provided from settings at the top of each screen. */
val LocalHapticsEnabled = compositionLocalOf { true }

/**
 * The app's one haptic vocabulary (Neural Expressive): ticks for stepping through choices, confirm and reject for
 * outcomes, toggles for switches, a threshold bump when a swipe commits, and long press for menus.
 */
class Haptics(private val feedback: HapticFeedback, private val enabled: Boolean) {
    private fun play(type: HapticFeedbackType) { if (enabled) feedback.performHapticFeedback(type) }
    fun tick() = play(HapticFeedbackType.SegmentTick)
    fun confirm() = play(HapticFeedbackType.Confirm)
    fun reject() = play(HapticFeedbackType.Reject)
    fun toggle(on: Boolean) = play(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun threshold() = play(HapticFeedbackType.GestureThresholdActivate)
    fun longPress() = play(HapticFeedbackType.LongPress)
    fun key() = play(HapticFeedbackType.KeyboardTap)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}
