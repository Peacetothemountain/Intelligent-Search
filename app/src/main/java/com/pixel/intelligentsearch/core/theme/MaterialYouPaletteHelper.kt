package com.pixel.intelligentsearch.core.theme

import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot

/**
 * Authentic Google Material Design 3 / Material You Color Engine.
 *
 * Utilizes the official Google Material Color Utilities (M3 HCT color space)
 * to generate mathematically harmonized Primary (a1), Secondary (a2), and
 * Tertiary (a3) tonal roles for any user-selected seed color.
 */
object MaterialYouPaletteHelper {

    data class MaterialTonalRoleColors(
        val primary: Int,
        val secondary: Int,
        val tertiary: Int
    )

    /**
     * Generates authentic Material 3 / Material You tonal colors from a custom Hue and Saturation.
     *
     * @param hue Seed hue in degrees [0f, 360f].
     * @param saturation Seed saturation in percent [0f, 100f].
     * @param isDarkSurface True if rendering against a dark background/pill (uses Tone 80 tokens),
     *                      False if rendering against a light background/pill (uses Tone 40 tokens).
     */
    fun getMaterialYouTonalColors(
        hue: Float,
        saturation: Float,
        isDarkSurface: Boolean
    ): MaterialTonalRoleColors {
        val normalizedHue = ((hue % 360f + 360f) % 360f).toDouble()
        // Map saturation percentage [0..100] to standard M3 Chroma space.
        // Google M3 defines key primary chroma typically around ~48.0.
        // We scale dynamically with user saturation to preserve custom user intent.
        val chroma = ((saturation / 100f) * 48.0).coerceIn(4.0, 64.0)

        // Construct canonical M3 HCT coordinate at tone 50 (neutral lightness seed)
        val hct = Hct.from(normalizedHue, chroma, 50.0)

        // SchemeTonalSpot is Google's canonical Material You dynamic color algorithm:
        // - Primary: Seed Hue, tone 80 (dark) / tone 40 (light)
        // - Secondary: Same Seed Hue, muted chroma 16, tone 80 (dark) / tone 40 (light)
        // - Tertiary: Harmonized (Hue + 60) % 360, chroma 24, tone 80 (dark) / tone 40 (light)
        val scheme = SchemeTonalSpot(hct, isDarkSurface, 0.0)

        // Ensure 100% opaque alpha (0xFF000000) for crisp icon rendering
        val p = scheme.primary or 0xFF000000.toInt()
        val s = scheme.secondary or 0xFF000000.toInt()
        val t = scheme.tertiary or 0xFF000000.toInt()

        return MaterialTonalRoleColors(primary = p, secondary = s, tertiary = t)
    }
}
