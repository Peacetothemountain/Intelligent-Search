package com.ngdesigns.intelligentsearch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Light, dark, or whatever the system uses. */
enum class ThemeChoice { System, Light, Dark }

/**
 * Material You: the colour scheme comes from the user's wallpaper (always available from Android 12, the minimum
 * here), with Material 3 Expressive shapes and motion.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchTheme(choice: ThemeChoice = ThemeChoice.System, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    val context = LocalContext.current
    MaterialExpressiveTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        typography = SearchTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
