package com.pixel.intelligentsearch.core.theme

import com.pixel.intelligentsearch.core.util.findActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val RobinDarkColorScheme = darkColorScheme(
    primary = GoogleDarkPrimary,
    onPrimary = GoogleDarkOnPrimary,
    primaryContainer = GoogleDarkPrimaryContainer,
    onPrimaryContainer = GoogleDarkOnPrimaryContainer,
    inversePrimary = GoogleDarkInversePrimary,
    secondary = GoogleDarkSecondary,
    onSecondary = GoogleDarkOnSecondary,
    secondaryContainer = GoogleDarkSecondaryContainer,
    onSecondaryContainer = GoogleDarkOnSecondaryContainer,
    tertiary = GoogleDarkTertiary,
    onTertiary = GoogleDarkOnTertiary,
    tertiaryContainer = GoogleDarkTertiaryContainer,
    onTertiaryContainer = GoogleDarkOnTertiaryContainer,
    background = GoogleDarkBackground,
    onBackground = GoogleDarkOnBackground,
    surface = GoogleDarkSurface,
    onSurface = GoogleDarkOnSurface,
    surfaceVariant = GoogleDarkSurfaceVariant,
    onSurfaceVariant = GoogleDarkOnSurfaceVariant,
    surfaceDim = GoogleDarkSurfaceDim,
    surfaceBright = GoogleDarkSurfaceBright,
    surfaceContainerLowest = GoogleDarkSurfaceContainerLowest,
    surfaceContainer = GoogleDarkSurfaceContainer,
    surfaceContainerLow = GoogleDarkSurfaceContainerLow,
    surfaceContainerHigh = GoogleDarkSurfaceContainerHigh,
    surfaceContainerHighest = GoogleDarkSurfaceContainerHighest,
    surfaceTint = GoogleDarkSurfaceTint,
    inverseSurface = GoogleDarkInverseSurface,
    inverseOnSurface = GoogleDarkInverseOnSurface,
    outline = GoogleDarkOutline,
    outlineVariant = GoogleDarkOutlineVariant,
    error = GoogleDarkError,
    onError = GoogleDarkOnError,
    errorContainer = GoogleDarkErrorContainer,
    onErrorContainer = GoogleDarkOnErrorContainer,
    scrim = GoogleDarkScrim,
    primaryFixed = GooglePrimaryFixed,
    primaryFixedDim = GooglePrimaryFixedDim,
    onPrimaryFixed = GoogleOnPrimaryFixed,
    onPrimaryFixedVariant = GoogleOnPrimaryFixedVariant,
    secondaryFixed = GoogleSecondaryFixed,
    secondaryFixedDim = GoogleSecondaryFixedDim,
    onSecondaryFixed = GoogleOnSecondaryFixed,
    onSecondaryFixedVariant = GoogleOnSecondaryFixedVariant,
    tertiaryFixed = GoogleTertiaryFixed,
    tertiaryFixedDim = GoogleTertiaryFixedDim,
    onTertiaryFixed = GoogleOnTertiaryFixed,
    onTertiaryFixedVariant = GoogleOnTertiaryFixedVariant
)

val AmoledDarkColorScheme = darkColorScheme(
    primary = AmoledDarkPrimary,
    onPrimary = AmoledDarkOnPrimary,
    primaryContainer = AmoledDarkPrimaryContainer,
    onPrimaryContainer = AmoledDarkOnPrimaryContainer,
    inversePrimary = AmoledDarkInversePrimary,
    secondary = AmoledDarkSecondary,
    onSecondary = AmoledDarkOnSecondary,
    secondaryContainer = AmoledDarkSecondaryContainer,
    onSecondaryContainer = AmoledDarkOnSecondaryContainer,
    tertiary = AmoledDarkTertiary,
    onTertiary = AmoledDarkOnTertiary,
    tertiaryContainer = AmoledDarkTertiaryContainer,
    onTertiaryContainer = AmoledDarkOnTertiaryContainer,
    background = AmoledDarkBackground,
    onBackground = AmoledDarkOnBackground,
    surface = AmoledDarkSurface,
    onSurface = AmoledDarkOnSurface,
    surfaceVariant = AmoledDarkSurfaceVariant,
    onSurfaceVariant = AmoledDarkOnSurfaceVariant,
    surfaceDim = AmoledDarkSurfaceDim,
    surfaceBright = AmoledDarkSurfaceBright,
    surfaceContainerLowest = AmoledDarkSurfaceContainerLowest,
    surfaceContainer = AmoledDarkSurfaceContainer,
    surfaceContainerLow = AmoledDarkSurfaceContainerLow,
    surfaceContainerHigh = AmoledDarkSurfaceContainerHigh,
    surfaceContainerHighest = AmoledDarkSurfaceContainerHighest,
    surfaceTint = AmoledDarkSurfaceTint,
    inverseSurface = AmoledDarkInverseSurface,
    inverseOnSurface = AmoledDarkInverseOnSurface,
    outline = AmoledDarkOutline,
    outlineVariant = AmoledDarkOutlineVariant,
    error = AmoledDarkError,
    onError = AmoledDarkOnError,
    errorContainer = AmoledDarkErrorContainer,
    onErrorContainer = AmoledDarkOnErrorContainer,
    scrim = AmoledDarkScrim,
    primaryFixed = GooglePrimaryFixed,
    primaryFixedDim = GooglePrimaryFixedDim,
    onPrimaryFixed = GoogleOnPrimaryFixed,
    onPrimaryFixedVariant = GoogleOnPrimaryFixedVariant,
    secondaryFixed = GoogleSecondaryFixed,
    secondaryFixedDim = GoogleSecondaryFixedDim,
    onSecondaryFixed = GoogleOnSecondaryFixed,
    onSecondaryFixedVariant = GoogleOnSecondaryFixedVariant,
    tertiaryFixed = GoogleTertiaryFixed,
    tertiaryFixedDim = GoogleTertiaryFixedDim,
    onTertiaryFixed = GoogleOnTertiaryFixed,
    onTertiaryFixedVariant = GoogleOnTertiaryFixedVariant
)

val LightColorScheme = lightColorScheme(
    primary = GoogleLightPrimary,
    onPrimary = GoogleLightOnPrimary,
    primaryContainer = GoogleLightPrimaryContainer,
    onPrimaryContainer = GoogleLightOnPrimaryContainer,
    inversePrimary = GoogleLightInversePrimary,
    secondary = GoogleLightSecondary,
    onSecondary = GoogleLightOnSecondary,
    secondaryContainer = GoogleLightSecondaryContainer,
    onSecondaryContainer = GoogleLightOnSecondaryContainer,
    tertiary = GoogleLightTertiary,
    onTertiary = GoogleLightOnTertiary,
    tertiaryContainer = GoogleLightTertiaryContainer,
    onTertiaryContainer = GoogleLightOnTertiaryContainer,
    background = GoogleLightBackground,
    onBackground = GoogleLightOnBackground,
    surface = GoogleLightSurface,
    onSurface = GoogleLightOnSurface,
    surfaceVariant = GoogleLightSurfaceVariant,
    onSurfaceVariant = GoogleLightOnSurfaceVariant,
    surfaceDim = GoogleLightSurfaceDim,
    surfaceBright = GoogleLightSurfaceBright,
    surfaceContainerLowest = GoogleLightSurfaceContainerLowest,
    surfaceContainer = GoogleLightSurfaceContainer,
    surfaceContainerLow = GoogleLightSurfaceContainerLow,
    surfaceContainerHigh = GoogleLightSurfaceContainerHigh,
    surfaceContainerHighest = GoogleLightSurfaceContainerHighest,
    surfaceTint = GoogleLightSurfaceTint,
    inverseSurface = GoogleLightInverseSurface,
    inverseOnSurface = GoogleLightInverseOnSurface,
    outline = GoogleLightOutline,
    outlineVariant = GoogleLightOutlineVariant,
    error = GoogleLightError,
    onError = GoogleLightOnError,
    errorContainer = GoogleLightErrorContainer,
    onErrorContainer = GoogleLightOnErrorContainer,
    scrim = GoogleLightScrim,
    primaryFixed = GooglePrimaryFixed,
    primaryFixedDim = GooglePrimaryFixedDim,
    onPrimaryFixed = GoogleOnPrimaryFixed,
    onPrimaryFixedVariant = GoogleOnPrimaryFixedVariant,
    secondaryFixed = GoogleSecondaryFixed,
    secondaryFixedDim = GoogleSecondaryFixedDim,
    onSecondaryFixed = GoogleOnSecondaryFixed,
    onSecondaryFixedVariant = GoogleOnSecondaryFixedVariant,
    tertiaryFixed = GoogleTertiaryFixed,
    tertiaryFixedDim = GoogleTertiaryFixedDim,
    onTertiaryFixed = GoogleOnTertiaryFixed,
    onTertiaryFixedVariant = GoogleOnTertiaryFixedVariant
)

/**
 * Transforms any dark [ColorScheme] (including dynamic Monet palettes) into pure black AMOLED surfaces.
 */
fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = AmoledDarkBackground,
    onBackground = AmoledDarkOnBackground,
    surface = AmoledDarkSurface,
    onSurface = AmoledDarkOnSurface,
    surfaceDim = AmoledDarkSurfaceDim,
    surfaceBright = AmoledDarkSurfaceBright,
    surfaceVariant = AmoledDarkSurfaceVariant,
    surfaceContainerLowest = AmoledDarkSurfaceContainerLowest,
    surfaceContainerLow = AmoledDarkSurfaceContainerLow,
    surfaceContainer = AmoledDarkSurfaceContainer,
    surfaceContainerHigh = AmoledDarkSurfaceContainerHigh,
    surfaceContainerHighest = AmoledDarkSurfaceContainerHighest
)

@Composable
fun IntelligentSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDynamicActive = dynamicColor

    val baseColorScheme = when {
        isDynamicActive -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> RobinDarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (darkTheme && amoled) {
        if (isDynamicActive) baseColorScheme.toAmoled() else AmoledDarkColorScheme
    } else {
        baseColorScheme
    }

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
