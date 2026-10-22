package com.pixel.intelligentsearch.core.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext

/**
 * False while the user has animations turned off (Accessibility > Remove animations, or an animator duration
 * scale of 0). Compose's own animations already honour this; hand-rolled frame loops must check it themselves.
 *
 * From Android 13 it follows the animator's duration scale itself, which is what animations actually use. Before
 * that it watches the setting, and reads the new value from the setting: the animator's copy may not have caught
 * up yet when the change is announced.
 */
@Composable
fun rememberAnimationsEnabled(): State<Boolean> {
    val resolver = LocalContext.current.contentResolver
    return produceState(initialValue = ValueAnimator.areAnimatorsEnabled(), resolver) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // ValueAnimator only holds the listener weakly; disposal below refers to it, keeping it alive till then.
            val listener = ValueAnimator.DurationScaleChangeListener { scale -> value = scale != 0f }
            ValueAnimator.registerDurationScaleChangeListener(listener)
            value = ValueAnimator.areAnimatorsEnabled()
            awaitDispose { ValueAnimator.unregisterDurationScaleChangeListener(listener) }
        } else {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    value = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
                }
            }
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
            awaitDispose { resolver.unregisterContentObserver(observer) }
        }
    }
}

/** Test tag on loading indicators, so screenshot tests can wait until a screen has finished loading. */
const val LOADING_INDICATOR_TAG = "loading_indicator"
