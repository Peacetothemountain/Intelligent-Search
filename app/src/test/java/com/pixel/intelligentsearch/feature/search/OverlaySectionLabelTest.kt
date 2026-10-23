package com.pixel.intelligentsearch.feature.search

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Every overlay section label is a TalkBack heading, so its users can jump from section to section. */
@RunWith(RobolectricTestRunner::class)
class OverlaySectionLabelTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun aSectionLabelIsAHeading() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { OverlaySectionLabel("Recent") } }
        compose.onNode(hasText("Recent") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
    }
}
