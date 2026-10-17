package com.ngdesigns.intelligentsearch.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ngdesigns.intelligentsearch.R

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Space Grotesk: names, queries and headings. */
val Grotesk = FontFamily(
    variable(R.font.space_grotesk, 400),
    variable(R.font.space_grotesk, 500),
    variable(R.font.space_grotesk, 600),
    variable(R.font.space_grotesk, 700),
)

/** JetBrains Mono: labels, counts, numbers and key hints. */
val Mono = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 500),
    variable(R.font.jetbrains_mono, 700),
)

/** Every text style the app uses. Sizes are in sp, so they follow the user's text size. */
@Immutable
data class Typography(
    val hero: TextStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(600), fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-0.03).em),
    val title: TextStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em),
    val query: TextStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(500), fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em),
    val body: TextStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 22.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(500), fontSize = 16.sp, lineHeight = 22.sp),
    val note: TextStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp),
    val label: TextStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight(500), fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em),
    val key: TextStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight(700), fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em),
    val figure: TextStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight(500), fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.03).em),
)
