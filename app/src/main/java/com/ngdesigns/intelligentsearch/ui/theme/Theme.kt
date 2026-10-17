package com.ngdesigns.intelligentsearch.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.ngdesigns.intelligentsearch.data.settings.ColorStyle
import com.ngdesigns.intelligentsearch.data.settings.Contrast
import com.ngdesigns.intelligentsearch.data.settings.Look
import com.ngdesigns.intelligentsearch.data.settings.ThemeMode

/**
 * Material You. With the system colour style and contrast (the defaults) the scheme is exactly the phone's own;
 * otherwise Material's scheme variants and contrast levels are generated from the wallpaper's accent colour. Motion
 * is Material 3 Expressive springs, or instant when the user turns animations off.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchTheme(look: Look = Look(), content: @Composable () -> Unit) {
    val dark = when (look.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val scheme = remember(dark, look.colorStyle, look.contrast, context) { schemeFor(context, dark, look.colorStyle, look.contrast) }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = SearchTypography,
        motionScheme = if (look.animations) MotionScheme.expressive() else InstantMotion,
        content = content,
    )
}

/** A subtree coloured from [seed] (an app icon, a contact photo): content-based colour beside the wallpaper's. */
@Composable
fun ContentColors(seed: Color?, content: @Composable () -> Unit) {
    if (seed == null) return content()
    val base = MaterialTheme.colorScheme
    val dark = base.surface.luminance() < 0.5f
    val scheme = remember(seed, dark) { dynamicColorScheme(seedColor = seed, isDark = dark, style = PaletteStyle.TonalSpot) }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

internal fun schemeFor(context: Context, dark: Boolean, style: ColorStyle, contrast: Contrast): ColorScheme {
    if (style == ColorStyle.System && contrast == Contrast.System) {
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    val seed = Color(context.getColor(android.R.color.system_accent1_500))
    val level = when (contrast) {
        Contrast.Standard -> 0.0
        Contrast.Medium -> 0.5
        Contrast.High -> 1.0
        Contrast.System -> if (Build.VERSION.SDK_INT >= 34) context.getSystemService(UiModeManager::class.java).contrast.toDouble() else 0.0
    }
    val palette = when (style) {
        ColorStyle.System, ColorStyle.TonalSpot -> PaletteStyle.TonalSpot
        ColorStyle.Vibrant -> PaletteStyle.Vibrant
        ColorStyle.Expressive -> PaletteStyle.Expressive
        ColorStyle.FruitSalad -> PaletteStyle.FruitSalad
        ColorStyle.Rainbow -> PaletteStyle.Rainbow
        ColorStyle.Content -> PaletteStyle.Content
        ColorStyle.Fidelity -> PaletteStyle.Fidelity
        ColorStyle.Monochrome -> PaletteStyle.Monochrome
        ColorStyle.Neutral -> PaletteStyle.Neutral
    }
    return dynamicColorScheme(seedColor = seed, isDark = dark, style = palette, contrastLevel = level)
}

/** Every motion token resolves to an instant change: the "animations off" setting. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object InstantMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
