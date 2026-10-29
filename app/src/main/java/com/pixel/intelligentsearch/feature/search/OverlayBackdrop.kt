package com.pixel.intelligentsearch.feature.search

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.pixel.intelligentsearch.core.theme.isDark

/** The most the overlay dims the wallpaper behind it: at Background Transparency 0. */
internal const val MAX_OVERLAY_SCRIM = 0.7f

/**
 * How much of the extra dim stands in for blur where the system has blur off ([overlayScrimAlpha]): at Background
 * Blur 100, this share of what is left of the wallpaper is dimmed away.
 */
internal const val BLUR_FALLBACK_DIM = 0.35f

/**
 * How strongly the overlay's scrim dims the wallpaper (0 to 1) for Background [transparency] and [blur] (both 0 to
 * 100). Transparency is how see-through the background is: 0 dims it the most ([MAX_OVERLAY_SCRIM]), 100 not at all.
 * Where the system can't blur behind windows ([blurAvailable] false: Battery Saver, or a phone without it), the
 * wallpaper is dimmed a little more in proportion to [blur] instead, as Android's guidance on window blur suggests, so
 * the Blur setting still quiets the background.
 */
internal fun overlayScrimAlpha(transparency: Int, blur: Int, blurAvailable: Boolean): Float {
    val base = (1f - transparency.coerceIn(0, 100) / 100f) * MAX_OVERLAY_SCRIM
    if (blurAvailable) return base
    return base + (1f - base) * BLUR_FALLBACK_DIM * (blur.coerceIn(0, 100) / 100f)
}

/**
 * The color the overlay's scrim lays over the wallpaper ([overlayScrimAlpha] says how strongly): in the dark theme the
 * scheme's scrim, which dims it; in the light theme the light palette's surfaceContainer, which washes it out toward
 * the light surfaces the overlay's panel and results are drawn in, as Pixel Launcher's light search does. A black
 * scrim under the light panel turned the wallpaper a muddy gray.
 */
internal fun overlayScrimColor(scheme: ColorScheme): Color = if (scheme.isDark) scheme.scrim else scheme.surfaceContainer
