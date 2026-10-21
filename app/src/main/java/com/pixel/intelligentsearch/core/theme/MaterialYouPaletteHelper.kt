package com.pixel.intelligentsearch.core.theme

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeRainbow
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Authentic Google Material Design 3 / Material You Color Engine (Android 17 / Canary ZP11).
 *
 * Utilizes the official Google Material Color Utilities (M3 HCT color space)
 * to generate harmonized tonal schemes, contrast-aware color roles, and custom
 * dynamic palettes across all Android 14+ / 17 tonal styles:
 * - TONAL_SPOT: Default AOSP / Pixel Monet dynamic style
 * - SPRITZ: Desaturated neutral / slate tonal style
 * - VIBRANT: High saturation, dynamic harmony
 * - EXPRESSIVE: Bold tertiary-shifted expressive style
 * - MONOCHROME: Pure grayscale tonal scheme for minimalists
 * - RAINBOW & FRUIT_SALAD: Playful multi-hue harmonic accents
 *
 * The `com.google.android.material.color.utilities` package is annotated `@RestrictTo(LIBRARY_GROUP)`
 * by Material Components, i.e. it is not a public, source/binary-stable API. This helper intentionally
 * depends on it because it is the only on-device source of the exact AOSP/Monet HCT tonal schemes.
 * The dependency is pinned via `google-material` in `gradle/libs.versions.toml`; re-verify
 * [MaterialYouPaletteHelperTest] whenever that version is bumped.
 */
@Suppress("RestrictedApi")
object MaterialYouPaletteHelper {

    enum class PaletteStyle {
        TONAL_SPOT,
        SPRITZ,
        VIBRANT,
        EXPRESSIVE,
        RAINBOW,
        FRUIT_SALAD,
        MONOCHROME,
        CONTENT,
        FIDELITY
    }

    data class MaterialTonalRoleColors(
        @get:ColorInt val primary: Int,
        @get:ColorInt val secondary: Int,
        @get:ColorInt val tertiary: Int,
        @get:ColorInt val primaryContainer: Int = 0,
        @get:ColorInt val secondaryContainer: Int = 0,
        @get:ColorInt val tertiaryContainer: Int = 0,
        @get:ColorInt val surfaceContainer: Int = 0,
        @get:ColorInt val onPrimary: Int = 0,
        @get:ColorInt val onSurface: Int = 0,
        @get:ColorInt val primaryFixed: Int = 0,
        @get:ColorInt val primaryFixedDim: Int = 0,
        @get:ColorInt val onPrimaryFixed: Int = 0,
        @get:ColorInt val onPrimaryFixedVariant: Int = 0,
        @get:ColorInt val secondaryFixed: Int = 0,
        @get:ColorInt val secondaryFixedDim: Int = 0,
        @get:ColorInt val onSecondaryFixed: Int = 0,
        @get:ColorInt val onSecondaryFixedVariant: Int = 0,
        @get:ColorInt val tertiaryFixed: Int = 0,
        @get:ColorInt val tertiaryFixedDim: Int = 0,
        @get:ColorInt val onTertiaryFixed: Int = 0,
        @get:ColorInt val onTertiaryFixedVariant: Int = 0,
        @get:ColorInt val outline: Int = 0,
        @get:ColorInt val outlineVariant: Int = 0
    )

    private val cacheLock = Any()

    private data class TonalCacheKey(
        val hue10: Int,
        val sat10: Int,
        val isDark: Boolean,
        val style: PaletteStyle,
        val contrast100: Int
    )

    private val tonalColorsCache = object : LinkedHashMap<TonalCacheKey, MaterialTonalRoleColors>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TonalCacheKey, MaterialTonalRoleColors>?): Boolean {
            return size > 128
        }
    }

    /**
     * Clears all memoized tonal palettes.
     */
    fun clearCache() {
        synchronized(cacheLock) {
            tonalColorsCache.clear()
        }
    }

    /**
     * Instantiates the exact Google Material Color Utilities DynamicScheme subclass
     * for the requested [PaletteStyle].
     */
    fun createDynamicScheme(
        hct: Hct,
        isDark: Boolean,
        style: PaletteStyle = PaletteStyle.TONAL_SPOT,
        contrastLevel: Double = 0.0
    ): DynamicScheme {
        val clampedContrast = contrastLevel.coerceIn(-1.0, 1.0)
        return when (style) {
            PaletteStyle.TONAL_SPOT -> SchemeTonalSpot(hct, isDark, clampedContrast)
            PaletteStyle.SPRITZ -> SchemeNeutral(hct, isDark, clampedContrast)
            PaletteStyle.VIBRANT -> SchemeVibrant(hct, isDark, clampedContrast)
            PaletteStyle.EXPRESSIVE -> SchemeExpressive(hct, isDark, clampedContrast)
            PaletteStyle.RAINBOW -> SchemeRainbow(hct, isDark, clampedContrast)
            PaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, isDark, clampedContrast)
            PaletteStyle.MONOCHROME -> SchemeMonochrome(hct, isDark, clampedContrast)
            PaletteStyle.CONTENT -> SchemeContent(hct, isDark, clampedContrast)
            PaletteStyle.FIDELITY -> SchemeFidelity(hct, isDark, clampedContrast)
        }
    }

    /**
     * Generates authentic Material 3 / Material You tonal colors from a custom Hue and Saturation,
     * memoized via a thread-safe LRU cache to eliminate CAM16 computation overhead at 120 FPS.
     *
     * @param hue Seed hue in degrees [0f, 360f].
     * @param saturation Seed saturation in percent [0f, 100f].
     * @param isDarkSurface True if rendering against a dark background/pill (uses Tone 80 tokens),
     *                      False if rendering against a light background/pill (uses Tone 40 tokens).
     * @param paletteStyle The Material 3 expressive palette style.
     * @param contrastLevel System or user accessibility contrast [-1.0, 1.0].
     */
    fun getMaterialYouTonalColors(
        hue: Float,
        saturation: Float,
        isDarkSurface: Boolean,
        paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
        contrastLevel: Double = 0.0
    ): MaterialTonalRoleColors {
        val key = TonalCacheKey(
            hue10 = (hue * 10f).toInt(),
            sat10 = (saturation * 10f).toInt(),
            isDark = isDarkSurface,
            style = paletteStyle,
            contrast100 = (contrastLevel * 100.0).toInt()
        )

        synchronized(cacheLock) {
            tonalColorsCache[key]?.let { return it }
        }

        val normalizedHue = ((hue % 360f + 360f) % 360f).toDouble()
        val chroma = if (paletteStyle == PaletteStyle.MONOCHROME) {
            0.0
        } else {
            ((saturation / 100f) * 48.0).coerceIn(4.0, 64.0)
        }

        // Canonical M3 HCT coordinate at tone 50
        val hct = Hct.from(normalizedHue, chroma, 50.0)
        val scheme = createDynamicScheme(hct, isDarkSurface, paletteStyle, contrastLevel)

        // Ensure 100% opaque alpha (0xFF000000) for crisp icon rendering
        val p = scheme.primary or 0xFF000000.toInt()
        val s = scheme.secondary or 0xFF000000.toInt()
        val t = scheme.tertiary or 0xFF000000.toInt()
        val pc = scheme.primaryContainer or 0xFF000000.toInt()
        val sc = scheme.secondaryContainer or 0xFF000000.toInt()
        val tc = scheme.tertiaryContainer or 0xFF000000.toInt()
        val surfC = scheme.surfaceContainer or 0xFF000000.toInt()
        val onP = scheme.onPrimary or 0xFF000000.toInt()
        val onSurf = scheme.onSurface or 0xFF000000.toInt()

        val pf = scheme.primaryFixed or 0xFF000000.toInt()
        val pfd = scheme.primaryFixedDim or 0xFF000000.toInt()
        val onPf = scheme.onPrimaryFixed or 0xFF000000.toInt()
        val onPfv = scheme.onPrimaryFixedVariant or 0xFF000000.toInt()
        val sf = scheme.secondaryFixed or 0xFF000000.toInt()
        val sfd = scheme.secondaryFixedDim or 0xFF000000.toInt()
        val onSf = scheme.onSecondaryFixed or 0xFF000000.toInt()
        val onSfv = scheme.onSecondaryFixedVariant or 0xFF000000.toInt()
        val tf = scheme.tertiaryFixed or 0xFF000000.toInt()
        val tfd = scheme.tertiaryFixedDim or 0xFF000000.toInt()
        val onTf = scheme.onTertiaryFixed or 0xFF000000.toInt()
        val onTfv = scheme.onTertiaryFixedVariant or 0xFF000000.toInt()
        val outl = scheme.outline or 0xFF000000.toInt()
        val outlVar = scheme.outlineVariant or 0xFF000000.toInt()

        val result = MaterialTonalRoleColors(
            primary = p,
            secondary = s,
            tertiary = t,
            primaryContainer = pc,
            secondaryContainer = sc,
            tertiaryContainer = tc,
            surfaceContainer = surfC,
            onPrimary = onP,
            onSurface = onSurf,
            primaryFixed = pf,
            primaryFixedDim = pfd,
            onPrimaryFixed = onPf,
            onPrimaryFixedVariant = onPfv,
            secondaryFixed = sf,
            secondaryFixedDim = sfd,
            onSecondaryFixed = onSf,
            onSecondaryFixedVariant = onSfv,
            tertiaryFixed = tf,
            tertiaryFixedDim = tfd,
            onTertiaryFixed = onTf,
            onTertiaryFixedVariant = onTfv,
            outline = outl,
            outlineVariant = outlVar
        )

        synchronized(cacheLock) {
            tonalColorsCache[key] = result
        }

        return result
    }

    /**
     * Calculates the standard relative luminance of an sRGB color.
     */
    fun calculateLuminance(@ColorInt color: Int): Double {
        fun channelLuminance(channel: Int): Double {
            val sRgb = channel / 255.0
            return if (sRgb <= 0.04045) sRgb / 12.92 else ((sRgb + 0.055) / 1.055).pow(2.4)
        }
        val r = channelLuminance((color shr 16) and 0xFF)
        val g = channelLuminance((color shr 8) and 0xFF)
        val b = channelLuminance(color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Computes the WCAG contrast ratio between two colors (ranging from 1.0 to 21.0).
     */
    fun calculateContrastRatio(@ColorInt foreground: Int, @ColorInt background: Int): Double {
        val l1 = calculateLuminance(foreground)
        val l2 = calculateLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Darkens a color for high-contrast icon rendering while preserving its authentic
     * Material You hue and chroma.
     *
     * Used when the inner pill state is turned off on Material design widgets so
     * themed icons remain distinct and legible against colored or tinted pill backgrounds.
     *
     * @param color The source ARGB color.
     * @param targetTone The target M3 tone (default 24.0 for rich, deep contrast).
     */
    @ColorInt
    fun darkenForContrast(@ColorInt color: Int, targetTone: Double = 24.0): Int {
        val hct = Hct.fromInt(color)
        val darkTone = min(hct.tone, targetTone)
        val darkened = Hct.from(
            hct.hue,
            max(hct.chroma, 32.0),
            darkTone.coerceIn(14.0, 30.0)
        )
        return darkened.toInt() or 0xFF000000.toInt()
    }

    data class ThreeTierAccentedPalette(
        @get:ColorInt val primaryDarker: Int,    // 1st: Darker accented
        @get:ColorInt val secondLighter: Int,    // 2nd: A little lighter than primary
        @get:ColorInt val thirdLighter: Int      // 3rd: A little lighter than second (grounded in darker accented, never bright)
    )

    /**
     * Resolves an authentic three-tier Material Design darker-accented color palette:
     * 1. Primary: Darker accented (Tone ~54 on dark surfaces, Tone ~36 on light surfaces)
     * 2. Second: A little lighter than primary (Tone ~68 on dark, Tone ~48 on light)
     * 3. Third: A little lighter than second (Tone ~80 on dark, Tone ~60 on light)
     *
     * Focuses on darker accented tones without washed-out bright accented highlights.
     */
    fun getThreeTierAccentedPalette(
        @ColorInt primaryColor: Int,
        @ColorInt secondaryColor: Int = primaryColor,
        isPillLight: Boolean = false
    ): ThreeTierAccentedPalette {
        return try {
            val hctP = Hct.fromInt(primaryColor)
            val hctS = if (secondaryColor != primaryColor && secondaryColor != 0) Hct.fromInt(secondaryColor) else hctP

            val pTone = if (isPillLight) 34.0 else 50.0
            val sTone = if (isPillLight) 46.0 else 64.0
            val tTone = if (isPillLight) 58.0 else 76.0

            val pChroma = max(hctP.chroma, 32.0).coerceIn(24.0, 52.0)
            val sChroma = max(hctS.chroma, 26.0).coerceIn(20.0, 44.0)
            val tChroma = max(hctP.chroma * 0.8, 18.0).coerceIn(16.0, 36.0)

            val p = Hct.from(hctP.hue, pChroma, pTone).toInt() or 0xFF000000.toInt()
            val s = Hct.from(hctS.hue, sChroma, sTone).toInt() or 0xFF000000.toInt()
            val t = Hct.from(hctP.hue, tChroma, tTone).toInt() or 0xFF000000.toInt()

            ThreeTierAccentedPalette(p, s, t)
        } catch (_: Throwable) {
            if (isPillLight) {
                ThreeTierAccentedPalette(0xFF004D40.toInt(), 0xFF00695C.toInt(), 0xFF00897B.toInt())
            } else {
                ThreeTierAccentedPalette(0xFF00796B.toInt(), 0xFF009688.toInt(), 0xFF26A69A.toInt())
            }
        }
    }

    data class FourColorMaterialGPalette(
        @get:ColorInt val rightArcAndBar: Int,  // Quadrant 1: Horizontal crossbar & right arc (Google Blue position)
        @get:ColorInt val bottomArc: Int,        // Quadrant 2: Bottom arc (Google Green position)
        @get:ColorInt val leftArc: Int,          // Quadrant 3: Left arc (Google Yellow position)
        @get:ColorInt val topArc: Int            // Quadrant 4: Top arc (Google Red position)
    )

    /**
     * Resolves an authentic 4-color Material You G Icon palette that maps harmoniously
     * to the quadrants and continuous sweep gradient of the Material G Logo:
     * - Quadrant 1 / Right Arc & Crossbar (0° to 30° / 330° to 360°): Primary anchor color
     * - Quadrant 2 / Bottom Arc (30° to 135°): Secondary harmonized tone from user's theme palette
     * - Quadrant 3 / Left Arc (135° to 225°): Tertiary accented tone from user's theme palette
     * - Quadrant 4 / Top Arc (225° to 330°): Lighter tonal accent from user's theme palette
     *
     * In HCT color space, all 4 quadrants strictly stay within the user's Material You
     * theme palette without artificial rainbow hue rotations (+135°, +220°), ensuring the
     * 4 colors sync together as elegant Material accented colors rather than striped blocks.
     */
    fun getFourColorMaterialGPalette(
        @ColorInt primaryColor: Int,
        @ColorInt secondaryColor: Int = primaryColor,
        @ColorInt tertiaryColor: Int = 0,
        isPillLight: Boolean = false
    ): FourColorMaterialGPalette {
        return try {
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

            // Quadrant 1: Primary anchor hue (Right Arc & Crossbar)
            val c1 = Hct.from(hctP.hue, pChroma, pTone).toInt() or 0xFF000000.toInt()

            // Quadrant 2: Secondary harmonized tone from user's secondary palette (Bottom Arc)
            val c2 = Hct.from(hctS.hue, sChroma, sTone).toInt() or 0xFF000000.toInt()

            // Quadrant 3: Tertiary dynamic tone from user's tertiary palette (Left Arc)
            val c3 = Hct.from(hctT.hue, tChroma, tTone).toInt() or 0xFF000000.toInt()

            // Quadrant 4: Lighter tonal accent from user's primary/tertiary palette (Top Arc)
            val c4 = Hct.from(hctP.hue, qChroma, qTone).toInt() or 0xFF000000.toInt()

            FourColorMaterialGPalette(c1, c2, c3, c4)
        } catch (_: Throwable) {
            val tier = getThreeTierAccentedPalette(primaryColor, secondaryColor, isPillLight)
            FourColorMaterialGPalette(tier.primaryDarker, tier.secondLighter, tier.thirdLighter, tier.primaryDarker)
        }
    }
}

