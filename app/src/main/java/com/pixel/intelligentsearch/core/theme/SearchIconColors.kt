package com.pixel.intelligentsearch.core.theme

import android.content.Context
import androidx.annotation.ColorInt
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb

/**
 * The colours the search overlay's search bar draws its Material icons in, worked out in this one place so the
 * overlay, the home screen widget and the widget editor's preview can't drift apart. From the theme's primary,
 * secondary and tertiary ([Accents]) for a light or dark bar, there are two palettes:
 * - [g], the Material G's four colours, which the overlay's lens is drawn in (and the widget's G);
 * - [tiers], Material You's three accent tiers, which the overlay's mic is drawn in (and the widget's other icons).
 */
data class SearchIconColors(
    val g: MaterialYouPaletteHelper.FourColorMaterialGPalette,
    val tiers: MaterialYouPaletteHelper.ThreeTierAccentedPalette
) {
    /** The lens's rim: the G's right arc and bar. */
    @get:ColorInt val lensRim: Int get() = g.rightArcAndBar
    /** The top of the lens's rim: the G's top arc. */
    @get:ColorInt val lensTop: Int get() = g.topArc
    /** The lens's handle: the G's bottom arc. */
    @get:ColorInt val lensHandle: Int get() = g.bottomArc
    /** The lens's sparkle: the G's left arc. */
    @get:ColorInt val lensSparkle: Int get() = g.leftArc

    /** The mic's capsule and its sparkle: the third, lightest tier. */
    @get:ColorInt val micCapsule: Int get() = tiers.thirdLighter
    @get:ColorInt val micSparkle: Int get() = tiers.thirdLighter
    /** The mic's stem and the left of its cradle: the second tier. */
    @get:ColorInt val micStem: Int get() = tiers.secondLighter
    @get:ColorInt val micLeftCradle: Int get() = tiers.secondLighter
    /** The right and bottom of the mic's cradle: the first, darker tier. */
    @get:ColorInt val micRightCradle: Int get() = tiers.primaryDarker

    /** The theme colours the icons are worked out from. */
    data class Accents(@get:ColorInt val primary: Int, @get:ColorInt val secondary: Int, @get:ColorInt val tertiary: Int)

    companion object {
        /** The icons' colours from [accents] on a light ([isLight]) or dark bar. */
        fun of(accents: Accents, isLight: Boolean): SearchIconColors = SearchIconColors(
            g = MaterialYouPaletteHelper.getFourColorMaterialGPalette(accents.primary, accents.secondary, accents.tertiary, isLight),
            tiers = MaterialYouPaletteHelper.getThreeTierAccentedPalette(accents.primary, accents.secondary, isLight)
        )

        /**
         * The accents the overlay draws its icons from on a light ([isLight]) or dark bar: the wallpaper's dynamic
         * colours, as the app's theme has them.
         */
        fun themeAccents(context: Context, isLight: Boolean): Accents {
            val widget = DynamicThemeDetector.resolveWidgetColors(context, isDark = !isLight)
            if (widget.isDynamicActive) return Accents(widget.primary, widget.secondary, widget.tertiary)
            // The app's theme is always the dynamic scheme, even where the system says dynamic colour is off.
            val scheme = if (isLight) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
            return Accents(scheme.primary.toArgb(), scheme.secondary.toArgb(), scheme.tertiary.toArgb())
        }

        /**
         * The accents a wallpaper of [hue] and [saturation] (0..100) would give the overlay on a light ([isLight]) or
         * dark bar: Material's tonal-spot scheme from that seed, as Pixel themes the system from its wallpaper.
         */
        fun seedAccents(hue: Float, saturation: Float, isLight: Boolean): Accents =
            MaterialYouPaletteHelper.getMaterialYouTonalColors(hue, saturation, isDarkSurface = !isLight)
                .let { Accents(it.primary, it.secondary, it.tertiary) }
    }
}
