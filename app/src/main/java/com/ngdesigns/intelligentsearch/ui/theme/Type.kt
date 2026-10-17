package com.ngdesigns.intelligentsearch.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ngdesigns.intelligentsearch.R

/**
 * Google Sans Flex, the typeface Material 3 Expressive pairs with its shapes, bundled so type is the same on every
 * device. One family per optical size, so large text uses the display cut and small text the text cut; the
 * emphasized display and headline styles turn up the roundness axis (ROND) to echo the Material shapes.
 */
private fun googleSansFlex(opticalSize: Float, roundness: Float = 0f) = FontFamily(
    (300..800 step 100).map { weight ->
        Font(
            R.font.google_sans_flex,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", opticalSize),
                FontVariation.Setting("ROND", roundness),
            ),
        )
    },
)

private val Display = googleSansFlex(opticalSize = 48f)
private val Headline = googleSansFlex(opticalSize = 28f)
private val Title = googleSansFlex(opticalSize = 18f)
private val Text = googleSansFlex(opticalSize = 14f)
private val DisplayRound = googleSansFlex(opticalSize = 48f, roundness = 100f)
private val HeadlineRound = googleSansFlex(opticalSize = 28f, roundness = 100f)

private fun TextStyle.on(family: FontFamily, weight: Int? = null) =
    copy(fontFamily = family, fontWeight = weight?.let(::FontWeight) ?: fontWeight)

/**
 * All 30 Material 3 Expressive type styles: 15 baseline and 15 emphasized. Display styles are set light (Neural
 * Expressive), so a key part of a figure can be picked out in an emphasized weight.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val SearchTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.on(Display, 300),
        displayMedium = displayMedium.on(Display, 300),
        displaySmall = displaySmall.on(Display, 400),
        headlineLarge = headlineLarge.on(Headline),
        headlineMedium = headlineMedium.on(Headline),
        headlineSmall = headlineSmall.on(Headline),
        titleLarge = titleLarge.on(Title),
        titleMedium = titleMedium.on(Title),
        titleSmall = titleSmall.on(Title),
        bodyLarge = bodyLarge.on(Text),
        bodyMedium = bodyMedium.on(Text),
        bodySmall = bodySmall.on(Text),
        labelLarge = labelLarge.on(Text),
        labelMedium = labelMedium.on(Text),
        labelSmall = labelSmall.on(Text),
        displayLargeEmphasized = displayLargeEmphasized.on(DisplayRound),
        displayMediumEmphasized = displayMediumEmphasized.on(DisplayRound),
        displaySmallEmphasized = displaySmallEmphasized.on(DisplayRound),
        headlineLargeEmphasized = headlineLargeEmphasized.on(HeadlineRound),
        headlineMediumEmphasized = headlineMediumEmphasized.on(HeadlineRound),
        headlineSmallEmphasized = headlineSmallEmphasized.on(HeadlineRound),
        titleLargeEmphasized = titleLargeEmphasized.on(Title),
        titleMediumEmphasized = titleMediumEmphasized.on(Title),
        titleSmallEmphasized = titleSmallEmphasized.on(Title),
        bodyLargeEmphasized = bodyLargeEmphasized.on(Text),
        bodyMediumEmphasized = bodyMediumEmphasized.on(Text),
        bodySmallEmphasized = bodySmallEmphasized.on(Text),
        labelLargeEmphasized = labelLargeEmphasized.on(Text),
        labelMediumEmphasized = labelMediumEmphasized.on(Text),
        labelSmallEmphasized = labelSmallEmphasized.on(Text),
    )
}
