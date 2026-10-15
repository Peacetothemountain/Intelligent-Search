package com.pixel.intelligentsearch.core.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
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
 */
@Composable
fun rememberAnimationsEnabled(): State<Boolean> {
    val resolver = LocalContext.current.contentResolver
    return produceState(initialValue = ValueAnimator.areAnimatorsEnabled(), resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = ValueAnimator.areAnimatorsEnabled()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        awaitDispose { resolver.unregisterContentObserver(observer) }
    }
}
