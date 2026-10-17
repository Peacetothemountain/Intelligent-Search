package com.ngdesigns.intelligentsearch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing and shape sizes. Corners stay small: the look is squared-off, not pill-shaped. */
@Immutable
data class Metrics(
    val gutter: Dp = 20.dp,
    val rowPadding: Dp = 10.dp,
    val hairline: Dp = 1.dp,
    val corner: Dp = 4.dp,
    val iconCorner: Dp = 7.dp,
    val icon: Dp = 28.dp,
    val iconLarge: Dp = 44.dp,
    val touch: Dp = 48.dp,
)

/** Light, dark, or whatever the system uses. */
enum class ThemeChoice { System, Light, Dark }

private val LocalPalette = staticCompositionLocalOf { inkPalette() }
private val LocalType = staticCompositionLocalOf { Typography() }
private val LocalMetrics = staticCompositionLocalOf { Metrics() }

/** The current theme, read anywhere inside [SearchTheme]. */
object Theme {
    val palette: Palette @Composable get() = LocalPalette.current
    val type: Typography @Composable get() = LocalType.current
    val metrics: Metrics @Composable get() = LocalMetrics.current
}

@Composable
fun SearchTheme(
    choice: ThemeChoice = ThemeChoice.System,
    accent: Accent = Accent.Vermilion,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    val palette = if (dark) inkPalette(accent) else paperPalette(accent)
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalTextSelectionColors provides TextSelectionColors(palette.accent, palette.accent.copy(alpha = 0.35f)),
        content = content,
    )
}
