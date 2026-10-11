package com.pixel.intelligentsearch.core.theme

import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

    private data class SchemeCacheKey(
        val seedArgb: Int,
        val isDark: Boolean,
        val isAmoled: Boolean,
        val style: PaletteStyle,
        val contrast100: Int
    )

    private val composeSchemeCache = object : LinkedHashMap<SchemeCacheKey, ColorScheme>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SchemeCacheKey, ColorScheme>?): Boolean {
            return size > 32
        }
    }

    /**
     * Clears all memoized tonal palettes and Compose color schemes.
     */
    fun clearCache() {
        synchronized(cacheLock) {
            tonalColorsCache.clear()
            composeSchemeCache.clear()
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
     * Builds a complete Compose [ColorScheme] from an arbitrary seed color, style, and contrast level,
     * memoized via a thread-safe cache with optional pure black AMOLED support.
     */
    @Suppress("DEPRECATION")
    fun createComposeColorScheme(
        seedColor: Color,
        isDark: Boolean,
        paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
        contrastLevel: Double = 0.0,
        isAmoled: Boolean = false
    ): ColorScheme {
        val argb = seedColor.toArgb()
        val key = SchemeCacheKey(
            seedArgb = argb,
            isDark = isDark,
            isAmoled = isAmoled,
            style = paletteStyle,
            contrast100 = (contrastLevel * 100.0).toInt()
        )

        synchronized(cacheLock) {
            composeSchemeCache[key]?.let { return it }
        }

        val hct = Hct.fromInt(argb)
        val scheme = createDynamicScheme(hct, isDark, paletteStyle, contrastLevel)

        fun toColor(argbInt: Int): Color = Color(argbInt or 0xFF000000.toInt())

        val baseScheme = ColorScheme(
            primary = toColor(scheme.primary),
            onPrimary = toColor(scheme.onPrimary),
            primaryContainer = toColor(scheme.primaryContainer),
            onPrimaryContainer = toColor(scheme.onPrimaryContainer),
            inversePrimary = toColor(scheme.inversePrimary),
            secondary = toColor(scheme.secondary),
            onSecondary = toColor(scheme.onSecondary),
            secondaryContainer = toColor(scheme.secondaryContainer),
            onSecondaryContainer = toColor(scheme.onSecondaryContainer),
            tertiary = toColor(scheme.tertiary),
            onTertiary = toColor(scheme.onTertiary),
            tertiaryContainer = toColor(scheme.tertiaryContainer),
            onTertiaryContainer = toColor(scheme.onTertiaryContainer),
            background = toColor(scheme.background),
            onBackground = toColor(scheme.onBackground),
            surface = toColor(scheme.surface),
            onSurface = toColor(scheme.onSurface),
            surfaceVariant = toColor(scheme.surfaceVariant),
            onSurfaceVariant = toColor(scheme.onSurfaceVariant),
            surfaceTint = toColor(scheme.primary),
            inverseSurface = toColor(scheme.inverseSurface),
            inverseOnSurface = toColor(scheme.inverseOnSurface),
            error = toColor(scheme.error),
            onError = toColor(scheme.onError),
            errorContainer = toColor(scheme.errorContainer),
            onErrorContainer = toColor(scheme.onErrorContainer),
            outline = toColor(scheme.outline),
            outlineVariant = toColor(scheme.outlineVariant),
            scrim = toColor(scheme.scrim),
            surfaceBright = toColor(scheme.surfaceBright),
            surfaceDim = toColor(scheme.surfaceDim),
            surfaceContainer = toColor(scheme.surfaceContainer),
            surfaceContainerHigh = toColor(scheme.surfaceContainerHigh),
            surfaceContainerHighest = toColor(scheme.surfaceContainerHighest),
            surfaceContainerLow = toColor(scheme.surfaceContainerLow),
            surfaceContainerLowest = toColor(scheme.surfaceContainerLowest)
        )

        val finalScheme = if (isDark && isAmoled) {
            baseScheme.copy(
                background = AmoledDarkBackground,
                onBackground = AmoledDarkOnBackground,
                surface = AmoledDarkSurface,
                onSurface = AmoledDarkOnSurface,
                surfaceDim = AmoledDarkSurfaceDim,
                surfaceBright = AmoledDarkSurfaceBright,
                surfaceVariant = AmoledDarkSurfaceVariant,
                surfaceContainerLowest = AmoledDarkSurfaceContainerLowest,
                surfaceContainerLow = AmoledDarkSurfaceContainerLow,
                surfaceContainer = AmoledDarkSurfaceContainer,
                surfaceContainerHigh = AmoledDarkSurfaceContainerHigh,
                surfaceContainerHighest = AmoledDarkSurfaceContainerHighest
            )
        } else {
            baseScheme
        }

        synchronized(cacheLock) {
            composeSchemeCache[key] = finalScheme
        }

        return finalScheme
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
     * Checks if two colors satisfy WCAG contrast requirements:
     * - Normal text: >= 4.5:1 (WCAG AA) or >= 7.0:1 (WCAG AAA)
     * - Large text / UI elements: >= 3.0:1
     */
    fun meetsWcagContrast(
        @ColorInt foreground: Int,
        @ColorInt background: Int,
        isLargeText: Boolean = false,
        isAaa: Boolean = false
    ): Boolean {
        val ratio = calculateContrastRatio(foreground, background)
        return when {
            isAaa && isLargeText -> ratio >= 4.5
            isAaa -> ratio >= 7.0
            isLargeText -> ratio >= 3.0
            else -> ratio >= 4.5
        }
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
}
