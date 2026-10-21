package com.pixel.intelligentsearch.feature.widget

import com.google.android.material.color.utilities.Hct
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper

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

    /** The least contrast any part of an icon keeps with the bar: WCAG's 3:1 for icons, with room for rounding. */
    private const val ICON_MIN_CONTRAST = 3.2

    /** Chroma of the custom palette's other roles against its accent's, as Material's tonal-spot scheme relates them. */
    private const val SECONDARY_SHARE = 0.45
    private const val TERTIARY_SHARE = 0.65
    private const val PLAIN_SHARE = 1.0 / 6.0

    /**
     * The Custom subtheme's whole bar, themed from one seed as Google's search bar editor themes its bar: Hue,
     * Saturation and Lightness pick the seed ([MaterialYouPaletteHelper.seedColor]), which fills the pill, the circle
     * and the rim, and every icon takes the seed's hue and saturation at a tone that keeps its contrast with what it
     * sits on ([background]): dark icons on a light pill and light ones on a dark pill, flipping as Material does.
     */
    data class CustomBarColors(
        /** The seed, opaque: the pill, the circle and the Material design's rim. */
        val seed: Int,
        /** What the icons sit on: the seed, or the Material design's pill locked black. */
        val background: Int,
        /** Whether the icons are darker than [background] (it counts as a light pill). */
        val darkIcons: Boolean,
        /** The Accented G style's G, mic, shortcuts and circle icon; the Material G style's anchor colour. */
        val accent: Int,
        val secondary: Int,
        val tertiary: Int,
        /** The System G style's mic, shortcuts and circle icon (its G stays Google's). */
        val plainIcon: Int,
        /** The Material G style's G. */
        val gPalette: MaterialYouPaletteHelper.FourColorMaterialGPalette,
        /** The Material G style's mic, shortcuts and circle icon. */
        val iconTiers: MaterialYouPaletteHelper.ThreeTierAccentedPalette
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
     * pill that can't reach them gets the better of white and black), and the Material G style's lighter parts step
     * from the accent towards the pill but keep at least 3:1.
     */
    fun customBarColors(settings: WidgetDesignSettings, isMaterialDesign: Boolean): CustomBarColors {
        val seed = settings.customColorInt or 0xFF000000.toInt()
        val background = if (isMaterialDesign && settings.lockBlack) LOCKED_PILL else seed
        val backgroundTone = Hct.fromInt(background).tone
        // Material's rule picks the side; every tone below stays on it.
        val darkIcons = iconsGoDark(background)
        fun toneFor(ratio: Double) =
            MaterialYouPaletteHelper.keepContrast(if (darkIcons) 100.0 else 0.0, backgroundTone, darkIcons, ratio)
        val accentTone = toneFor(ACCENT_CONTRAST)
        fun stepped(by: Double) = MaterialYouPaletteHelper.keepContrast(
            accentTone + if (darkIcons) by else -by, backgroundTone, darkIcons, ICON_MIN_CONTRAST
        )
        val hue = settings.hue.toDouble()
        val share = (settings.saturation / 100.0).coerceIn(0.0, 1.0)
        fun color(hueOffset: Double, shareOf: Double, tone: Double) =
            MaterialYouPaletteHelper.colorAtShare(hue + hueOffset, share * shareOf, tone)

        val accent = color(0.0, 1.0, accentTone)
        return CustomBarColors(
            seed = seed,
            background = background,
            darkIcons = darkIcons,
            accent = accent,
            secondary = color(0.0, SECONDARY_SHARE, accentTone),
            tertiary = color(60.0, TERTIARY_SHARE, accentTone),
            plainIcon = color(0.0, PLAIN_SHARE, toneFor(PLAIN_CONTRAST)),
            gPalette = MaterialYouPaletteHelper.FourColorMaterialGPalette(
                rightArcAndBar = accent,
                bottomArc = color(0.0, SECONDARY_SHARE, stepped(8.0)),
                leftArc = color(60.0, TERTIARY_SHARE, stepped(16.0)),
                topArc = color(0.0, 0.75, stepped(24.0))
            ),
            iconTiers = MaterialYouPaletteHelper.ThreeTierAccentedPalette(
                primaryDarker = accent,
                secondLighter = color(0.0, SECONDARY_SHARE, stepped(12.0)),
                thirdLighter = color(0.0, 0.8, stepped(24.0))
            )
        )
    }

    /** How opaque the bar's background is: Transparency 0 is fully opaque, 100 invisible. */
    fun backgroundAlpha(settings: WidgetDesignSettings): Float =
        ((100 - settings.transparency.toInt()) / 100f).coerceIn(0f, 1f)

    /** How opaque something painted in the custom colour is: its Color Opacity, with the bar's Transparency on top. */
    fun customColorAlpha(settings: WidgetDesignSettings): Float =
        ((settings.colorOpacity / 100f).coerceIn(0f, 1f) * backgroundAlpha(settings)).coerceIn(0f, 1f)

    /**
     * How opaque the Material design's outer rim is. Painted in the custom colour it takes that colour's opacity;
     * painted in the theme's colour only the bar's Transparency applies, as Color Opacity belongs to the custom colour.
     */
    fun rimAlpha(settings: WidgetDesignSettings): Float =
        if (settings.subtheme == "Custom") customColorAlpha(settings) else backgroundAlpha(settings)

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
