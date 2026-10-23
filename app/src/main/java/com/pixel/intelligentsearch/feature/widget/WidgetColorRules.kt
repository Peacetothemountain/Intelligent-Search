package com.pixel.intelligentsearch.feature.widget

import com.google.android.material.color.utilities.Hct
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.core.theme.SearchIconColors

/**
 * The colour rules the widget and the widget editor's live preview share, so the preview paints what the widget
 * draws for the same settings. Slider values are as [WidgetDesignSettings] holds them: hue 0..360, the rest 0..100.
 */
@Suppress("RestrictedApi")
internal object WidgetColorRules {

    /** The Material design's pill and circle when its Inner Pill & Circle State locks them black. */
    const val LOCKED_PILL = 0xFF121212.toInt()

    /** The contrast the Custom subtheme's accent keeps with the bar: Material's high-contrast level for primary. */
    private const val ACCENT_CONTRAST = 7.0

    /** The contrast the System G style's plain icons keep with the bar: Material's high-contrast level for on-surface. */
    private const val PLAIN_CONTRAST = 11.0

    /** Chroma of the System G style's plain icons against the custom colour's, as Material's tonal-spot neutrals. */
    private const val PLAIN_SHARE = 1.0 / 6.0

    /**
     * The Custom subtheme's whole bar, themed from one seed as Google's search bar editor themes its bar: Hue,
     * Saturation and Lightness pick the seed ([MaterialYouPaletteHelper.seedColor]), which fills the pill, the circle
     * and the rim. The Accented and System G styles' icons take the seed's hue and saturation at a tone that keeps its
     * contrast with what they sit on ([background]); the Material G style's are the search overlay's
     * ([materialIconAccents]). Dark icons on a light pill and light ones on a dark pill, flipping as Material does.
     */
    data class CustomBarColors(
        /** The seed, opaque: the pill, the circle and the Material design's rim. */
        val seed: Int,
        /** What the icons sit on: the seed, or the Material design's pill locked black. */
        val background: Int,
        /** Whether the icons are darker than [background] (it counts as a light pill). */
        val darkIcons: Boolean,
        /** The Accented G style's G, mic, shortcuts and circle icon. */
        val accent: Int,
        /** The System G style's mic, shortcuts and circle icon (its G stays Google's). */
        val plainIcon: Int
    )

    /**
     * Whether the Custom subtheme's icons are drawn darker than a pill of [color], so it counts as a light pill:
     * Material's foreground rule, which flips where white and black contrast with it equally (about tone 50).
     */
    fun iconsGoDark(color: Int): Boolean {
        val tone = Hct.fromInt(color or 0xFF000000.toInt()).tone
        return MaterialYouPaletteHelper.foregroundTone(tone, ACCENT_CONTRAST) < tone
    }

    /**
     * [settings]' Custom subtheme colours on the Material ([isMaterialDesign]) or System design. The accent keeps 7:1
     * with the pill and the plain icons 11:1 (Material's high-contrast levels for primary and on-surface; a mid-tone
     * pill that can't reach them gets the better of white and black).
     */
    fun customBarColors(settings: WidgetDesignSettings, isMaterialDesign: Boolean): CustomBarColors {
        val seed = settings.customColorInt or 0xFF000000.toInt()
        val background = if (isMaterialDesign && settings.lockBlack) LOCKED_PILL else seed
        val backgroundTone = Hct.fromInt(background).tone
        // Material's rule picks the side; every tone below stays on it.
        val darkIcons = iconsGoDark(background)
        fun toneFor(ratio: Double) =
            MaterialYouPaletteHelper.keepContrast(if (darkIcons) 100.0 else 0.0, backgroundTone, darkIcons, ratio)
        val hue = settings.hue.toDouble()
        val share = (settings.saturation / 100.0).coerceIn(0.0, 1.0)
        return CustomBarColors(
            seed = seed,
            background = background,
            darkIcons = darkIcons,
            accent = MaterialYouPaletteHelper.colorAtShare(hue, share, toneFor(ACCENT_CONTRAST)),
            plainIcon = MaterialYouPaletteHelper.colorAtShare(hue, share * PLAIN_SHARE, toneFor(PLAIN_CONTRAST))
        )
    }

    /**
     * The accents the Material G style's icons (its G, mic, shortcuts and circle icon) are worked out from, the search
     * overlay's way ([SearchIconColors]), for a light ([isPillLight]) or dark pill: the theme's ([themeAccents]), or
     * for the Custom subtheme those a wallpaper of the custom colour would give, so the icons are the overlay's in
     * that hue.
     */
    fun materialIconAccents(
        settings: WidgetDesignSettings,
        isPillLight: Boolean,
        themeAccents: (isLight: Boolean) -> SearchIconColors.Accents
    ): SearchIconColors.Accents =
        if (settings.subtheme == "Custom") SearchIconColors.seedAccents(settings.hue, settings.saturation, isPillLight)
        else themeAccents(isPillLight)

    /** How opaque the bar's background is: Transparency 0 is fully opaque, 100 invisible. */
    fun backgroundAlpha(settings: WidgetDesignSettings): Float =
        ((100 - settings.transparency.toInt()) / 100f).coerceIn(0f, 1f)

    /**
     * How see-through the bar's background is drawn ([backdropAlphas]). The outline (the Material design's rim), the
     * pill and the circle are drawn as one group, the backdrop, and the icons over it, always fully opaque.
     */
    data class BackdropAlphas(
        /** The whole backdrop's opacity, applied once to the group as it is drawn. */
        val backdrop: Float,
        /** The outline's opacity within the backdrop. The pill and the circle are opaque in it. */
        val rim: Float
    )

    /**
     * The backdrop's opacities for [settings] on the Material ([isMaterialDesign]) or System design. The pill and the
     * circle sit over the outline (a filled shape the size of the whole bar), so they're opaque within the backdrop:
     * the outline never shows through them, and they aren't painted twice where they overlap it. Transparency then
     * fades the whole bar's background at once, so every part of it is exactly as see-through as the slider says,
     * except a pill locked black ([LOCKED_PILL]), which stays solid.
     *
     * Color Opacity is the custom colour's own opacity. When the whole backdrop is the custom colour (the Custom
     * subtheme's pill, circle and rim, or on the System design its pill and the outline that is as see-through as it)
     * it fades the backdrop with Transparency, for the same reason.
     *
     * The Material design's Inner Pill & Circle State locks the pill and circle to a solid black: the backdrop stays
     * fully opaque, and the rim alone takes Transparency (with Color Opacity when it's the custom colour). The opaque
     * pill and circle cover the rim beneath them, so nothing is painted twice there either.
     */
    fun backdropAlphas(settings: WidgetDesignSettings, isMaterialDesign: Boolean): BackdropAlphas {
        val transparency = backgroundAlpha(settings)
        val opacity = (settings.colorOpacity / 100f).coerceIn(0f, 1f)
        val isCustom = settings.subtheme == "Custom"
        return when {
            isMaterialDesign && settings.lockBlack ->
                BackdropAlphas(backdrop = 1f, rim = if (isCustom) transparency * opacity else transparency)
            !isCustom -> BackdropAlphas(backdrop = transparency, rim = 1f)
            else -> BackdropAlphas(backdrop = transparency * opacity, rim = 1f)
        }
    }

    /** [alpha] as the 0..255 the widget's views take. */
    fun alphaInt(alpha: Float): Int = (alpha * 255).toInt().coerceIn(0, 255)

    /** Whether the icons on the bar are drawn darker for contrast: on a light Material pill that isn't locked black. */
    fun darkensIcons(isMaterialDesign: Boolean, lockBlack: Boolean, isPillLight: Boolean): Boolean =
        isMaterialDesign && !lockBlack && isPillLight

    /**
     * An icon colour as the bar draws it: the colour the user picked (Custom G style) or the theme's accent, darkened
     * to [targetTone] where [darkensIcons] says so, and as given everywhere else.
     */
    fun iconColor(color: Int, isMaterialDesign: Boolean, lockBlack: Boolean, isPillLight: Boolean, targetTone: Double = 32.0): Int =
        if (darkensIcons(isMaterialDesign, lockBlack, isPillLight)) MaterialYouPaletteHelper.darkenForContrast(color, targetTone) else color

    /**
     * The Custom G style's colours for each element as the bar draws them: with Sync All Colors every icon takes the
     * G's colour, and on a light Material pill that isn't locked black each is darkened for contrast ([iconColor]).
     */
    fun customIconColors(settings: WidgetDesignSettings, isMaterialDesign: Boolean, isPillLight: Boolean): WidgetElementColors {
        val c = settings.elementColors
        fun drawn(color: Int) = iconColor(color, isMaterialDesign, settings.lockBlack, isPillLight)
        fun slot(color: Int) = drawn(if (settings.syncColors) c.gIcon else color)
        return WidgetElementColors(
            gIcon = drawn(c.gIcon),
            mic = slot(c.mic),
            shortcut1 = slot(c.shortcut1),
            shortcut2 = slot(c.shortcut2),
            shortcut3 = slot(c.shortcut3),
            action = slot(c.action)
        )
    }

    /**
     * Whether the mic is the Material You drawing rather than the original one: always on the Material design, and
     * on the System design unless the System G style sits on a dark theme-coloured pill.
     */
    fun usesMaterialYouIcons(isMaterialDesign: Boolean, gIconTheme: String, isPillLight: Boolean, subtheme: String): Boolean =
        isMaterialDesign || gIconTheme != "System G Icon" || isPillLight || subtheme == "Custom"
}
