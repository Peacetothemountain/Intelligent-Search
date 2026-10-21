package com.pixel.intelligentsearch.feature.widget

import android.graphics.Color
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper

/**
 * The colour rules the widget and the widget editor's live preview share, so the preview paints what the widget
 * draws for the same settings. Slider values are as [WidgetDesignSettings] holds them: hue 0..360, the rest 0..100.
 */
internal object WidgetColorRules {

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

    /** Whether a pill filled with the custom colour [color] counts as light, which picks the icons' tones on it. */
    fun isLight(color: Int): Boolean =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0 > 0.5

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
