package com.pixel.intelligentsearch.feature.tabs.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine

val ColorScheme.isDark: Boolean get() = background.luminance() < 0.5f

/** A container on the page (a settings card): surfaceContainer dark, surfaceContainerLowest light, as in the app. */
val ColorScheme.pageContainer: Color get() = if (isDark) surfaceContainer else surfaceContainerLowest

/**
 * The app's one vibration: Pixel's predictive-back threshold tick ([PixelHapticEngine]), which follows the app's
 * Vibration setting. Every button and dialog button plays it; nothing else vibrates.
 */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) { { PixelHapticEngine.playButtonTap(context, view) } }
}

/** The activity this context belongs to, if any. */
internal fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
