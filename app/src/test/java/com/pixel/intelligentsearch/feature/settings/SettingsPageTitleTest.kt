package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The settings pages share one window, so moving between them announces nothing by itself: each page's title is its
 * pane title (TalkBack names the page as it opens) and its heading (a heading jump lands on it).
 */
@RunWith(RobolectricTestRunner::class)
class SettingsPageTitleTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun aPageTitleIsTheHeadingAndPaneTitle() {
        compose.setContent { SettingsTopBar("Appearance", onBack = {}) }
        compose.onNodeWithText("Appearance")
            .assert(isHeading())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Appearance"))
    }
}
