@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.pixel.intelligentsearch.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
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

/** Text scale from which [withHyphenation] applies: large accessibility sizes, where one long word can fill a row. */
const val LARGE_TEXT_SCALE = 1.3f

/**
 * [this] with automatic hyphenation on every style. At large text sizes a single long word ("Customization")
 * can be wider than its column, and without hyphenation Android splits it at any letter; with it, the word
 * breaks at a hyphenation point and shows a hyphen. Only used from [LARGE_TEXT_SCALE], so normal sizes keep
 * their line breaks exactly.
 */
fun Typography.withHyphenation(): Typography {
    fun TextStyle.h() = copy(hyphens = Hyphens.Auto)
    return copy(
        displayLarge = displayLarge.h(), displayMedium = displayMedium.h(), displaySmall = displaySmall.h(),
        headlineLarge = headlineLarge.h(), headlineMedium = headlineMedium.h(), headlineSmall = headlineSmall.h(),
        titleLarge = titleLarge.h(), titleMedium = titleMedium.h(), titleSmall = titleSmall.h(),
        bodyLarge = bodyLarge.h(), bodyMedium = bodyMedium.h(), bodySmall = bodySmall.h(),
        labelLarge = labelLarge.h(), labelMedium = labelMedium.h(), labelSmall = labelSmall.h()
    )
}
