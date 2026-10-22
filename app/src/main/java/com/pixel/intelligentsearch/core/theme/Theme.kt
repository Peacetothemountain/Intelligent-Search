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

    // Compose's own haptics (long presses, text handles and the like) play the app's one haptic too.
    val expressiveHapticFeedback = androidx.compose.runtime.remember(context, view) {
        val hapticEngine = com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context)
        object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                hapticEngine.performPredictiveBackHaptic(view)
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalHapticFeedback provides expressiveHapticFeedback
    ) {
        // Large accessibility text sizes hyphenate long words instead of splitting them at any letter.
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val typography = androidx.compose.runtime.remember(fontScale) {
            if (fontScale >= LARGE_TEXT_SCALE) Typography.withHyphenation() else Typography
        }
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes,
            content = content
        )
    }
}
