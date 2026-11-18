@file:Suppress("RestrictedApi")

package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Contrast
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The search bar widget's color rules, ported from Intelligent Search so every bar in the Gallery is drawn by the same
 * math the widget and Widget Customization's live preview use:
 * - MaterialYouPaletteHelper (core/theme): HCT seeds, contrast tones, the overlay's icon tiers and the Material G;
 * - SearchIconColors (core/theme): the search overlay's icon colors;
 * - WidgetColorRules, WidgetBarShape (feature/widget);
 * - previewPill and widgetPreviewColors (feature/settings/WidgetSettingsScreen.kt).
 * Logic is unchanged; only what the Gallery doesn't draw (Custom G icon colors) is left out.
 */

/** MaterialYouPaletteHelper's color functions, as Intelligent Search has them. */
internal object GalleryPalette {
    fun maxChroma(hue: Double, tone: Double): Double = Hct.from(hue, 200.0, tone).chroma

    @ColorInt
    fun colorAtShare(hue: Double, share: Double, tone: Double): Int {
        val chroma = share.coerceIn(0.0, 1.0) * maxChroma(hue, tone)
        return Hct.from(((hue % 360.0) + 360.0) % 360.0, chroma, tone.coerceIn(0.0, 100.0)).toInt() or 0xFF000000.toInt()
    }

    /** The seed the editor's Hue, Saturation and Lightness sliders pick. */
    @ColorInt
    fun seedColor(hue: Float, saturation: Float, lightness: Float): Int =
        colorAtShare(hue.toDouble(), saturation / 100.0, lightness.toDouble())

    fun foregroundTone(backgroundTone: Double, ratio: Double): Double =
        DynamicColor.foregroundTone(backgroundTone.coerceIn(0.0, 100.0), ratio)

    fun keepContrast(tone: Double, backgroundTone: Double, darker: Boolean, ratio: Double): Double =
        if (darker) min(tone, Contrast.darkerUnsafe(backgroundTone, ratio)) else max(tone, Contrast.lighterUnsafe(backgroundTone, ratio))

    @ColorInt
    fun darkenForContrast(@ColorInt color: Int, targetTone: Double = 24.0): Int {
        val hct = Hct.fromInt(color)
        val darkTone = min(hct.tone, targetTone)
        return Hct.from(hct.hue, max(hct.chroma, 32.0), darkTone.coerceIn(14.0, 30.0)).toInt() or 0xFF000000.toInt()
    }

    /** Tonal-spot primary, secondary and tertiary from a custom hue and saturation (getMaterialYouTonalColors). */
    fun tonalAccents(hue: Float, saturation: Float, isDarkSurface: Boolean): IconAccents {
        val normalizedHue = ((hue % 360f + 360f) % 360f).toDouble()
        val chroma = ((saturation / 100f) * 48.0).coerceIn(4.0, 64.0)
        val scheme = SchemeTonalSpot(Hct.from(normalizedHue, chroma, 50.0), isDarkSurface, 0.0)
        return IconAccents(scheme.primary or 0xFF000000.toInt(), scheme.secondary or 0xFF000000.toInt(), scheme.tertiary or 0xFF000000.toInt())
    }

    data class Tiers(@ColorInt val primaryDarker: Int, @ColorInt val secondLighter: Int, @ColorInt val thirdLighter: Int)

    fun threeTier(@ColorInt primaryColor: Int, @ColorInt secondaryColor: Int, isPillLight: Boolean): Tiers {
        val hctP = Hct.fromInt(primaryColor)
        val hctS = if (secondaryColor != primaryColor && secondaryColor != 0) Hct.fromInt(secondaryColor) else hctP
        val pTone = if (isPillLight) 34.0 else 50.0
        val sTone = if (isPillLight) 46.0 else 64.0
        val tTone = if (isPillLight) 58.0 else 76.0
        val pChroma = max(hctP.chroma, 32.0).coerceIn(24.0, 52.0)
        val sChroma = max(hctS.chroma, 26.0).coerceIn(20.0, 44.0)
        val tChroma = max(hctP.chroma * 0.8, 18.0).coerceIn(16.0, 36.0)
        return Tiers(
            Hct.from(hctP.hue, pChroma, pTone).toInt() or 0xFF000000.toInt(),
            Hct.from(hctS.hue, sChroma, sTone).toInt() or 0xFF000000.toInt(),
            Hct.from(hctP.hue, tChroma, tTone).toInt() or 0xFF000000.toInt()
        )
    }

    data class FourG(@ColorInt val rightArcAndBar: Int, @ColorInt val bottomArc: Int, @ColorInt val leftArc: Int, @ColorInt val topArc: Int)

    fun fourColorG(@ColorInt primaryColor: Int, @ColorInt secondaryColor: Int, @ColorInt tertiaryColor: Int, isPillLight: Boolean): FourG {
        val hctP = Hct.fromInt(primaryColor)
        val hctS = if (secondaryColor != primaryColor && secondaryColor != 0) Hct.fromInt(secondaryColor) else hctP
        val hctT = if (tertiaryColor != 0 && tertiaryColor != primaryColor) Hct.fromInt(tertiaryColor) else hctP
        val pTone = if (isPillLight) 36.0 else 58.0
        val sTone = if (isPillLight) 44.0 else 66.0
        val tTone = if (isPillLight) 52.0 else 74.0
        val qTone = if (isPillLight) 60.0 else 82.0
        val pChroma = max(hctP.chroma, 30.0).coerceIn(24.0, 52.0)
        val sChroma = max(hctS.chroma, 22.0).coerceIn(18.0, 44.0)
        val tChroma = max(hctT.chroma, 20.0).coerceIn(16.0, 40.0)
        val qChroma = max(hctP.chroma * 0.75, 18.0).coerceIn(16.0, 36.0)
        return FourG(
            Hct.from(hctP.hue, pChroma, pTone).toInt() or 0xFF000000.toInt(),
            Hct.from(hctS.hue, sChroma, sTone).toInt() or 0xFF000000.toInt(),
            Hct.from(hctT.hue, tChroma, tTone).toInt() or 0xFF000000.toInt(),
            Hct.from(hctP.hue, qChroma, qTone).toInt() or 0xFF000000.toInt()
        )
    }

    fun tone(color: Int): Double = Hct.fromInt(color or 0xFF000000.toInt()).tone
}

/** The theme colors the overlay's icons are worked out from (SearchIconColors.Accents). */
internal data class IconAccents(@ColorInt val primary: Int, @ColorInt val secondary: Int, @ColorInt val tertiary: Int)

/** SearchIconColors: the Material G's four colors and the three accent tiers, on a light or dark bar. */
internal class IconColors(val g: GalleryPalette.FourG, val tiers: GalleryPalette.Tiers) {
    val lensRim get() = g.rightArcAndBar
    val lensTop get() = g.topArc
    val lensHandle get() = g.bottomArc
    val lensSparkle get() = g.leftArc
    val micCapsule get() = tiers.thirdLighter
    val micStem get() = tiers.secondLighter
    val micLeftCradle get() = tiers.secondLighter
    val micRightCradle get() = tiers.primaryDarker

    companion object {
        fun of(accents: IconAccents, isLight: Boolean) = IconColors(
            GalleryPalette.fourColorG(accents.primary, accents.secondary, accents.tertiary, isLight),
            GalleryPalette.threeTier(accents.primary, accents.secondary, isLight)
        )
    }
}

/** DynamicThemeDetector.ResolvedWidgetColors, from the phone's dynamic scheme for one theme. */
internal data class WidgetThemeColors(
    @ColorInt val primary: Int,
    @ColorInt val secondary: Int,
    @ColorInt val tertiary: Int,
    @ColorInt val primaryContainer: Int,
    @ColorInt val surfaceContainer: Int,
    @ColorInt val onSurface: Int,
    @ColorInt val lockedPill: Int
)

internal const val LOCKED_PILL = 0xFF121212.toInt()

internal fun ColorScheme.widgetThemeColors(dark: Boolean) = WidgetThemeColors(
    primary = primary.toArgb(),
    secondary = secondary.toArgb(),
    tertiary = tertiary.toArgb(),
    primaryContainer = primaryContainer.toArgb(),
    surfaceContainer = surfaceContainer.toArgb(),
    onSurface = onSurface.toArgb(),
    lockedPill = if (dark) LOCKED_PILL else surfaceBright.toArgb()
)

/**
 * One design's settings, as WidgetDesignSettings holds them (style only: a look never changes the person's buttons).
 * Slider values are as stored: hue 0..360, the rest 0..100.
 */
internal data class WidgetStyle(
    val subtheme: String,
    val gIconTheme: String,
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val colorOpacity: Float,
    val transparency: Float,
    val outlineThickness: Int,
    val cornerRadius: Int,
    val lockBlack: Boolean,
    @ColorInt val lockedPillColor: Int
) {
    /** The custom color Save writes (custom_color_int): the seed of the three sliders. */
    @get:ColorInt val customColorInt: Int get() = GalleryPalette.seedColor(hue, saturation, lightness)
}

/** WidgetColorRules, the part the bar's colors need. */
internal object WidgetRules {
    private const val ACCENT_CONTRAST = 7.0
    private const val PLAIN_CONTRAST = 11.0
    private const val PLAIN_SHARE = 1.0 / 6.0

    fun lockedPill(s: WidgetStyle, theme: WidgetThemeColors): Int {
        val stored = s.lockedPillColor or 0xFF000000.toInt()
        return if (stored == LOCKED_PILL) theme.lockedPill or 0xFF000000.toInt() else stored
    }

    fun iconsGoDark(color: Int): Boolean {
        val tone = GalleryPalette.tone(color)
        return GalleryPalette.foregroundTone(tone, ACCENT_CONTRAST) < tone
    }

    fun locksDark(s: WidgetStyle, theme: WidgetThemeColors): Boolean = s.lockBlack && !iconsGoDark(lockedPill(s, theme))

    data class CustomBarColors(val seed: Int, val background: Int, val darkIcons: Boolean, val accent: Int, val plainIcon: Int)

    fun customBarColors(s: WidgetStyle, isMaterialDesign: Boolean, theme: WidgetThemeColors): CustomBarColors {
        val seed = s.customColorInt or 0xFF000000.toInt()
        val background = if (isMaterialDesign && s.lockBlack) lockedPill(s, theme) else seed
        val backgroundTone = GalleryPalette.tone(background)
        val darkIcons = iconsGoDark(background)
        fun toneFor(ratio: Double) = GalleryPalette.keepContrast(if (darkIcons) 100.0 else 0.0, backgroundTone, darkIcons, ratio)
        val hue = s.hue.toDouble()
        val share = (s.saturation / 100.0).coerceIn(0.0, 1.0)
        return CustomBarColors(
            seed = seed,
            background = background,
            darkIcons = darkIcons,
            accent = GalleryPalette.colorAtShare(hue, share, toneFor(ACCENT_CONTRAST)),
            plainIcon = GalleryPalette.colorAtShare(hue, share * PLAIN_SHARE, toneFor(PLAIN_CONTRAST))
        )
    }

    fun materialIconAccents(s: WidgetStyle, isPillLight: Boolean, themeAccents: (Boolean) -> IconAccents): IconAccents =
        if (s.subtheme == "Custom") GalleryPalette.tonalAccents(s.hue, s.saturation, isDarkSurface = !isPillLight) else themeAccents(isPillLight)

    fun backgroundAlpha(s: WidgetStyle): Float = ((100 - s.transparency.toInt()) / 100f).coerceIn(0f, 1f)

    data class BackdropAlphas(val backdrop: Float, val rim: Float)

    fun backdropAlphas(s: WidgetStyle, isMaterialDesign: Boolean): BackdropAlphas {
        val transparency = backgroundAlpha(s)
        val opacity = (s.colorOpacity / 100f).coerceIn(0f, 1f)
        val isCustom = s.subtheme == "Custom"
        return when {
            isMaterialDesign && s.lockBlack -> BackdropAlphas(1f, if (isCustom) transparency * opacity else transparency)
            !isCustom -> BackdropAlphas(transparency, 1f)
            else -> BackdropAlphas(transparency * opacity, 1f)
        }
    }

    fun alphaInt(alpha: Float): Int = (alpha * 255).toInt().coerceIn(0, 255)

    fun darkensIcons(isMaterialDesign: Boolean, lockBlack: Boolean, isPillLight: Boolean) = isMaterialDesign && !lockBlack && isPillLight

    fun iconColor(color: Int, isMaterialDesign: Boolean, lockBlack: Boolean, isPillLight: Boolean, targetTone: Double = 32.0): Int =
        if (darkensIcons(isMaterialDesign, lockBlack, isPillLight)) GalleryPalette.darkenForContrast(color, targetTone) else color

    fun usesMaterialYouIcons(isMaterialDesign: Boolean, gIconTheme: String, isPillLight: Boolean, subtheme: String): Boolean =
        isMaterialDesign || gIconTheme != "System G Icon" || isPillLight || subtheme == "Custom"
}

/** WidgetBarShape: the pill keeps its height and the outline grows around it, in whole dp. */
internal data class BarShape(val outline: Int, val pillHeight: Int, val outerRadius: Int, val pillRadius: Int, val circleRadius: Int) {
    val height: Int get() = pillHeight + 2 * outline
    val circleIsRound: Boolean get() = circleRadius >= CIRCLE_SIZE / 2

    companion object {
        const val CIRCLE_SIZE = 48
        private const val STEP = 2
        private const val MAX_OUTLINE = 12
        private const val MAX_CORNER = 40
        private fun step(v: Int) = v - v.mod(STEP)

        fun of(s: WidgetStyle, isMaterialDesign: Boolean): BarShape {
            val pillHeight = if (isMaterialDesign) 48 else 56
            val outline = step(s.outlineThickness.coerceIn(0, MAX_OUTLINE))
            val pillRadius = step(s.cornerRadius.coerceIn(0, MAX_CORNER)).coerceAtMost(pillHeight / 2)
            val outerRadius = if (pillRadius == 0) 0 else pillRadius + outline
            return BarShape(outline, pillHeight, outerRadius, pillRadius, pillRadius.coerceAtMost(CIRCLE_SIZE / 2))
        }
    }
}

internal data class PreviewPill(val fill: Color, val isLight: Boolean)

internal data class PreviewTheme(val primary: Color, val primaryContainer: Color, val surfaceContainer: Color, val appDark: Boolean)

/** Everything the bar paints for one design (WidgetPreviewColors). */
internal data class BarColors(
    val backdropAlpha: Float,
    val rim: Color,
    val pill: PreviewPill,
    val accentPrimary: Color,
    val materialIconAccents: IconAccents,
    val systemIconTint: Color,
    val usesMaterialYouIcons: Boolean,
    /** The Material design's pill is locked to a dark color (WidgetColorRules.locksDark): its icons aren't darkened. */
    val lockDark: Boolean = false
)

private fun previewPill(
    isMaterialDesign: Boolean,
    subtheme: String,
    lockBlack: Boolean,
    accentColor: Color,
    themedSurface: Color,
    appDark: Boolean,
    lockedPill: Color
): PreviewPill {
    val themed = themedSurface.copy(alpha = 1f)
    val custom = accentColor.copy(alpha = 1f)
    val customIsLight = WidgetRules.iconsGoDark(accentColor.toArgb())
    return if (isMaterialDesign) {
        when {
            lockBlack -> lockedPill.copy(alpha = 1f).let { PreviewPill(it, WidgetRules.iconsGoDark(it.toArgb())) }
            subtheme == "Custom" -> PreviewPill(custom, customIsLight)
            else -> PreviewPill(themed, !appDark)
        }
    } else {
        when (subtheme) {
            "Light" -> PreviewPill(Color(0xFFF1F3F4), true)
            "Dark" -> PreviewPill(Color(0xFF303134), false)
            "Custom" -> PreviewPill(custom, customIsLight)
            // Dynamic color is always on for the phones this preview is for (Android 12 and later, wallpaper colors).
            else -> PreviewPill(themed, !appDark)
        }
    }
}

/** widgetPreviewColors: what the live preview paints for [draft] on the [isMaterialDesign] design, drawn with [theme]. */
internal fun barColors(
    draft: WidgetStyle,
    isMaterialDesign: Boolean,
    theme: PreviewTheme,
    iconAccents: (isLight: Boolean) -> IconAccents,
    widgetColors: (isDark: Boolean) -> WidgetThemeColors
): BarColors {
    val isCustomTheme = draft.subtheme == "Custom"
    val lockBlack = draft.lockBlack
    val themeColors = widgetColors(theme.appDark)
    val lockDark = isMaterialDesign && WidgetRules.locksDark(draft, themeColors)
    val alphas = WidgetRules.backdropAlphas(draft, isMaterialDesign)
    val rimAlpha = WidgetRules.alphaInt(alphas.rim) / 255f
    val accentColor = if (isCustomTheme) Color(draft.customColorInt) else theme.primary

    val materialRim = if (isMaterialDesign) {
        val lockedDarkTheme = WidgetRules.locksDark(draft, themeColors)
        val (rimPrimary, rimContainer) = if (lockBlack && lockedDarkTheme != theme.appDark) {
            widgetColors(lockedDarkTheme).let { Color(it.primary) to Color(it.primaryContainer) }
        } else theme.primary to theme.primaryContainer
        when (draft.subtheme) {
            "Custom" -> accentColor.copy(alpha = rimAlpha)
            "Material" -> rimContainer.copy(alpha = rimAlpha)
            else -> rimPrimary.copy(alpha = rimAlpha)
        }
    } else null

    val pill = previewPill(
        isMaterialDesign, draft.subtheme, lockBlack, accentColor, theme.surfaceContainer, theme.appDark,
        Color(WidgetRules.lockedPill(draft, themeColors))
    )
    val isPillLight = pill.isLight
    val isDarkSurface = if (isMaterialDesign) lockDark || !isPillLight else !isPillLight
    val surfaceColors = widgetColors(isDarkSurface)
    val customBar = if (isCustomTheme) WidgetRules.customBarColors(draft, isMaterialDesign, themeColors) else null

    val accentPrimary = customBar?.let { Color(it.accent) }
        ?: Color(WidgetRules.iconColor(surfaceColors.primary, isMaterialDesign, lockDark, isPillLight, 32.0))
    val rim = materialRim ?: accentPrimary.copy(alpha = rimAlpha)
    val systemIconTint = when {
        customBar != null -> Color(customBar.plainIcon)
        isPillLight && !lockDark -> Color(surfaceColors.onSurface)
        else -> Color.White
    }
    return BarColors(
        backdropAlpha = alphas.backdrop,
        rim = rim,
        pill = pill,
        accentPrimary = accentPrimary,
        materialIconAccents = WidgetRules.materialIconAccents(draft, isPillLight, iconAccents),
        systemIconTint = systemIconTint,
        usesMaterialYouIcons = WidgetRules.usesMaterialYouIcons(isMaterialDesign, draft.gIconTheme, isPillLight, draft.subtheme),
        lockDark = WidgetRules.locksDark(draft, themeColors)
    )
}

/** [color] at [alpha] over [background] as one opaque color (WidgetColorRules.blendOver). */
internal fun blendOver(color: Int, alpha: Float, background: Int): Int {
    val a = alpha.coerceIn(0f, 1f)
    fun channel(shift: Int) = ((color shr shift and 0xFF) * a + (background shr shift and 0xFF) * (1f - a)).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
