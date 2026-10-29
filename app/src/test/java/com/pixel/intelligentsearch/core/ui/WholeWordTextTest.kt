package com.pixel.intelligentsearch.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Short labels at the largest text sizes: they wrap between words, and a word too wide for its room shrinks until it
 * fits rather than splitting mid-word ("Definitio/ns").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WholeWordTextTest {

    @get:Rule val compose = createComposeRule()

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    @Test
    fun aWordTooWideForItsRoomShrinksInsteadOfSplitting() {
        compose.setContent {
            Box(Modifier.width(110.dp)) { WholeWordText("More Definitions", TextStyle(fontSize = 32.sp)) }
        }
        compose.waitForIdle()
        val layout = layoutOf("More Definitions")
        assertFalse("a word was split", splitsAWord("More Definitions", layout))
        assertTrue("it shrank to fit", layout.layoutInput.style.fontSize.value < 32f)
    }

    @Test
    fun wordsThatFitKeepTheirSizeAndWrapBetweenWords() {
        compose.setContent {
            Box(Modifier.width(160.dp)) { WholeWordText("Open Digital Wellbeing", TextStyle(fontSize = 16.sp)) }
        }
        compose.waitForIdle()
        val layout = layoutOf("Open Digital Wellbeing")
        assertEquals(16f, layout.layoutInput.style.fontSize.value, 0.01f)
        assertFalse(splitsAWord("Open Digital Wellbeing", layout))
    }
}
