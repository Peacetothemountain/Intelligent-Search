package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.ui.splitsAWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The warning card's buttons keep each label on one line. In the dialog's narrow card the three didn't fit side by
 * side, and the accept button was squeezed into what was left, reading S/a/v/e down the screen; now they move onto
 * lines of their own instead. Text is measured with real fonts (native graphics), as on a phone.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
class WarningCardButtonsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun inject() = hilt.inject()

    private fun showCard() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                WarningCard(
                    title = "Discard unsaved changes?",
                    message = "You have unsaved customizations. If you leave now, none of your changes will be applied.",
                    acceptLabel = "Save",
                    onAccept = {},
                    onDiscard = {},
                    onDontShowAgain = {},
                    onDismissRequest = {}
                )
            }
        }
        compose.waitForIdle()
    }

    private fun lines(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().lineCount
    }

    private fun assertEveryLabelOnOneLine() {
        for (label in listOf("Save", "Discard", "Don't show this again")) assertEquals("$label's lines", 1, lines(label))
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun onAPhoneEachButtonKeepsItsLabelOnOneLine() {
        showCard()
        assertEveryLabelOnOneLine()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi", fontScale = 2f)
    fun atTheLargestTextNoButtonSplitsAWord() {
        showCard()
        assertEquals(1, lines("Save"))
        assertEquals(1, lines("Discard"))
        // The longest label may need two lines in the dialog's width at this size, but only between its words.
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Don't show this again", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(splitsAWord("Don't show this again", layouts.single()))
    }
}
