package com.pixel.intelligentsearch.core.theme

import com.pixel.intelligentsearch.core.util.findActivity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    val colorScheme = appColorScheme(context, darkTheme)

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

/**
 * The app's color scheme: the dynamic (wallpaper) palette in light or dark. The light theme's page
 * ([ColorScheme.background]) is the palette's surfaceContainer, as Pixel's own Settings draws its light pages, so the
 * near-white containers on it ([pageContainer]) stand out the way the dark theme's lighter ones do on its dark page.
 * The dark theme is the palette as it comes. Anything that has to match the app's page outside Compose (the widget
 * picker's preview) reads it here too.
 */
fun appColorScheme(context: Context, darkTheme: Boolean): ColorScheme =
    if (darkTheme) dynamicDarkColorScheme(context)
    else dynamicLightColorScheme(context).let { it.copy(background = it.surfaceContainer) }

/** Whether this is a dark scheme: its surface is dark. */
val ColorScheme.isDark: Boolean get() = surface.luminance() < 0.5f

/**
 * The fill of a container that sits on the page (a settings card, the overlay's menus): surfaceContainer on the dark
 * page, and on the light theme's surfaceContainer page ([appColorScheme]) surfaceContainerLowest, the light palette's
 * brightest surface, as Pixel's Settings fills its light cards.
 */
val ColorScheme.pageContainer: Color get() = if (isDark) surfaceContainer else surfaceContainerLowest

/**
 * A container laid straight on the page that holds rows or choices (a tab row's track, a list of apps, a locked
 * panel): on the dark page a faint surfaceVariant at [darkAlpha], and on the light theme's tinted page the same
 * near-white as its cards ([pageContainer]), where a faint tint only grayed into the page.
 */
fun ColorScheme.pageTrack(darkAlpha: Float = 0.5f): Color =
    if (isDark) surfaceVariant.copy(alpha = darkAlpha) else surfaceContainerLowest

/**
 * The fill of a card raised over the search overlay's see-through panel (an answer, the best match): surfaceContainerHigh
 * in the dark theme, and in the light theme surfaceContainerLowest, the near-white Google's light search cards are,
 * which a mid-gray container would only dull on the light panel.
 */
val ColorScheme.raisedContainer: Color get() = if (isDark) surfaceContainerHigh else surfaceContainerLowest
