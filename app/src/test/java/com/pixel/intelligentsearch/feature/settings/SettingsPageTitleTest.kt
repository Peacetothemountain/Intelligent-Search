package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The settings pages share one window, so moving between them announces nothing by itself: each page's title is its
 * pane title (TalkBack names the page as it opens) and its heading (a heading jump lands on it).
 */
@RunWith(RobolectricTestRunner::class)
class SettingsPageTitleTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun aPageTitleIsTheHeadingAndPaneTitle() {
        compose.setContent { SettingsPageTitle("Appearance") }
        compose.onNodeWithText("Appearance")
            .assert(isHeading())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Appearance"))
    }

    @Test
    fun everySettingsTopBarTitleIsAPageTitle() {
        val missing = File("src/main/java/com/pixel/intelligentsearch/feature/settings").walk()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                val source = file.readText()
                Regex("""\bTopAppBar\(""").findAll(source).mapNotNull { bar ->
                    val title = Regex("""title\s*=\s*\{(.{0,700}?)\n\s*\},?\s*\n""", RegexOption.DOT_MATCHES_ALL)
                        .find(source, bar.range.last)?.groupValues?.get(1) ?: return@mapNotNull null
                    "${file.name}: ${title.trim().take(60)}".takeIf { "Text(" in title && "SettingsPageTitle(" !in title }
                }
            }
            .toList()
        assertTrue("Top bars titled without SettingsPageTitle:\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
