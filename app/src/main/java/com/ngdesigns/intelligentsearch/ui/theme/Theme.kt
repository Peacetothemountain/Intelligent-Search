package com.ngdesigns.intelligentsearch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ngdesigns.intelligentsearch.R

/** Light, dark, or whatever the system uses. */
enum class ThemeChoice { System, Light, Dark }

private fun robotoFlex(weight: Int) =
    Font(R.font.roboto_flex, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Roboto Flex, bundled so the type is the same on every device, tablet and headset. */
val RobotoFlex = FontFamily(robotoFlex(400), robotoFlex(500), robotoFlex(600), robotoFlex(700))

private fun TextStyle.flex() = copy(fontFamily = RobotoFlex)

/** The Material 3 type scale, set in Roboto Flex. */
private val SearchTypography = Typography().run {
    copy(
        displayLarge = displayLarge.flex(), displayMedium = displayMedium.flex(), displaySmall = displaySmall.flex(),
        headlineLarge = headlineLarge.flex(), headlineMedium = headlineMedium.flex(), headlineSmall = headlineSmall.flex(),
        titleLarge = titleLarge.flex(), titleMedium = titleMedium.flex(), titleSmall = titleSmall.flex(),
        bodyLarge = bodyLarge.flex(), bodyMedium = bodyMedium.flex(), bodySmall = bodySmall.flex(),
        labelLarge = labelLarge.flex(), labelMedium = labelMedium.flex(), labelSmall = labelSmall.flex(),
    )
}

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
