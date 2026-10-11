@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.pixel.intelligentsearch.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.R

import androidx.compose.ui.text.font.FontVariation

val GoogleSansFlex = FontFamily(
    Font(
        R.font.google_sans_flex,
        FontWeight.Light,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(300),
            FontVariation.width(100f),
            FontVariation.slant(0f),
            FontVariation.grade(0)
        )
    ),
    Font(
        R.font.google_sans_flex,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.slant(0f),
            FontVariation.grade(0)
        )
    ),
    Font(
        R.font.google_sans_flex,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(100f),
            FontVariation.slant(0f),
            FontVariation.grade(0)
        )
    ),
    Font(
        R.font.google_sans_flex,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.width(100f),
            FontVariation.slant(0f),
            FontVariation.grade(0)
        )
    ),
    Font(
        R.font.google_sans_flex,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(100f),
            FontVariation.slant(0f),
            FontVariation.grade(0)
        )
    )
)

private data class VariableFontCacheKey(
    val weight: Int,
    val width: Float,
    val slant: Float,
    val grade: Int
)

private val variableFontCache = java.util.concurrent.ConcurrentHashMap<VariableFontCacheKey, FontFamily>()

/**
 * Creates a specialized variable [FontFamily] with custom Material 3 Expressive variable font axes.
 * Supports weight (100..1000), width (25..151), slant (-10..0), and optical grade (-200..150).
 * Results are cached in a thread-safe cache to avoid runtime allocations during 120 FPS rendering.
 */
fun googleSansFlexVariable(
    weight: Int = 400,
    width: Float = 100f,
    slant: Float = 0f,
    grade: Int = 0
): FontFamily {
    val key = VariableFontCacheKey(
        weight = weight.coerceIn(100, 1000),
        width = width.coerceIn(25f, 151f),
        slant = slant.coerceIn(-10f, 0f),
        grade = grade.coerceIn(-200, 150)
    )
    return variableFontCache.computeIfAbsent(key) { k ->
        FontFamily(
            Font(
                R.font.google_sans_flex,
                FontWeight(k.weight),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(k.weight),
                    FontVariation.width(k.width),
                    FontVariation.slant(k.slant),
                    FontVariation.grade(k.grade)
                )
            )
        )
    }
}

// Canonical M3 Expressive font family singletons (zero-allocation)
val GoogleSansFlexThin: FontFamily by lazy { googleSansFlexVariable(200) }
val GoogleSansFlexLight: FontFamily by lazy { googleSansFlexVariable(300) }
val GoogleSansFlexRegular: FontFamily by lazy { googleSansFlexVariable(400) }
val GoogleSansFlexMedium: FontFamily by lazy { googleSansFlexVariable(500) }
val GoogleSansFlexSemiBold: FontFamily by lazy { googleSansFlexVariable(600) }
val GoogleSansFlexBold: FontFamily by lazy { googleSansFlexVariable(700) }
val GoogleSansFlexBlack: FontFamily by lazy { googleSansFlexVariable(900) }
val GoogleSansFlexCompressed: FontFamily by lazy { googleSansFlexVariable(weight = 600, width = 75f) }
val GoogleSansFlexExpanded: FontFamily by lazy { googleSansFlexVariable(weight = 600, width = 125f) }

/**
 * Expressive typography line-height style for Android 17 / Canary ZP11.
 * Trims extra font ascender/descender leading to guarantee 100% pixel-perfect vertical centering
 * in search bars, pills, chips, and 120 FPS high-refresh rate composable containers.
 */
val ExpressiveLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

/**
 * Modern platform text style: disables legacy Android font padding to prevent vertical drift
 * across variable font weights.
 */
@Suppress("DEPRECATION")
val ExpressivePlatformTextStyle = PlatformTextStyle(
    includeFontPadding = false
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        platformStyle = ExpressivePlatformTextStyle,
        lineHeightStyle = ExpressiveLineHeightStyle
    )
)
