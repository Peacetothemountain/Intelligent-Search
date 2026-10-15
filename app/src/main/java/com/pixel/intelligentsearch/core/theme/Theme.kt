package com.pixel.intelligentsearch.core.theme

import com.pixel.intelligentsearch.core.util.findActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
/** The app theme: the dynamic (wallpaper) palette, always available on Android 12+, in light or dark. */
fun IntelligentSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val expressiveHapticFeedback = androidx.compose.runtime.remember(context, view) {
        val hapticEngine = com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context)
        object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                when (hapticFeedbackType) {
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress -> {
                        hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.HEAVY_IMPACT)
                    }
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove -> {
                        hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.TICK)
                    }
                    else -> {
                        hapticEngine.performPredictiveBackHaptic(view)
                    }
                }
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalHapticFeedback provides expressiveHapticFeedback
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
