@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.pixel.intelligentsearch.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.Density
import com.pixel.intelligentsearch.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's text is Google Sans Flex, Pixel's system typeface, everywhere: every style of the theme, and no text that
 * picks the platform's default font (Roboto) instead.
 */
class GoogleSansFlexEverywhereTest {

    /** Every style [androidx.compose.material3.Typography] holds, the emphasized ones the components use included. */
    private fun androidx.compose.material3.Typography.allStyles(): Map<String, TextStyle> =
        javaClass.methods
            .filter { it.parameterCount == 0 && it.returnType == TextStyle::class.java && it.name.startsWith("get") }
            .associate { it.name.removePrefix("get").substringBefore('$') to it.invoke(this) as TextStyle }

    private fun FontFamily?.isGoogleSansFlex(): Boolean =
        this is FontListFontFamily && fonts.isNotEmpty() && fonts.all { it is ResourceFont && it.resId == R.font.google_sans_flex }

    private fun ResourceFont.axis(tag: String): Float? =
        variationSettings.settings.firstOrNull { it.axisName == tag }?.toVariationValue(Density(1f))

    @Test
    fun everyThemeStyleIsGoogleSansFlex() {
        val styles = Typography.allStyles()
        assertTrue("found ${styles.keys}", styles.size >= 15)
        for ((name, style) in styles) assertTrue("$name is ${style.fontFamily}", style.fontFamily.isGoogleSansFlex())
        // Large accessibility text sizes swap in a hyphenating copy, which must keep the font.
        for ((name, style) in Typography.withHyphenation().allStyles()) {
            assertTrue("hyphenated $name is ${style.fontFamily}", style.fontFamily.isGoogleSansFlex())
        }
    }

    @Test
    fun everyWeightIsUprightSquareAndAtNormalWidth() {
        for (font in (GoogleSansFlex as FontListFontFamily).fonts.map { it as ResourceFont }) {
            assertEquals(font.weight.weight.toFloat(), font.axis(FontVariation.weight(1).axisName))
            assertEquals(100f, font.axis("wdth"))
            assertEquals(0f, font.axis("slnt"))
            assertEquals(0f, font.axis("ROND"))
            assertEquals(0f, font.axis("GRAD"))
            // The optical size stays at the font's default, which the screenshot goldens are drawn with.
            assertEquals(null, font.axis("opsz"))
        }
    }

    /**
     * No source picks a font other than Google Sans Flex: not the platform's default families, not a platform
     * Typeface, and no TextStyle built from scratch without a font (which falls back to Roboto). Monospace is kept
     * only where text is typed syntax: the adb commands and the !bang triggers.
     */
    @Test
    fun noSourcePicksAnotherFont() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("sources found", sources.size > 50)
        val forbidden = Regex("""FontFamily\.(Default|SansSerif|Serif|Cursive)\b|Typeface\.(DEFAULT|DEFAULT_BOLD|SANS_SERIF|SERIF|create)\b""")
        val monospaceAllowed = setOf("SearchRoleInfoDialog.kt", "WebShortcutBadges.kt")
        val problems = mutableListOf<String>()
        for (file in sources) {
            val text = file.readText()
            text.lineSequence().forEachIndexed { i, line ->
                if (forbidden.containsMatchIn(line)) problems += "${file.name}:${i + 1}: $line".trim()
                if ("FontFamily.Monospace" in line && file.name !in monospaceAllowed) problems += "${file.name}:${i + 1}: $line".trim()
            }
            for (call in textStyleCalls(text)) {
                if ("fontFamily" !in call.second) problems += "${file.name}:${call.first}: TextStyle without a fontFamily"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Each `TextStyle(...)` built in [source]: its line and its arguments (copies and the theme's own aren't). */
    private fun textStyleCalls(source: String): List<Pair<Int, String>> {
        val calls = mutableListOf<Pair<Int, String>>()
        Regex("""(?<![\w.])(androidx\.compose\.ui\.text\.)?TextStyle\(""").findAll(source).forEach { match ->
            var depth = 1
            var i = match.range.last + 1
            while (i < source.length && depth > 0) {
                when (source[i]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                i++
            }
            val line = source.substring(0, match.range.first).count { it == '\n' } + 1
            calls += line to source.substring(match.range.last + 1, i - 1)
        }
        return calls
    }
}
