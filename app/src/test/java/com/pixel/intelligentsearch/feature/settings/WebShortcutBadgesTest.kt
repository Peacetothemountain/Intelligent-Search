package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The shortcut badge fits its trigger, and Remove All can be done without the slide gesture. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class WebShortcutBadgesTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    private val frame = run {
        val square = RoundedPolygon(numVertices = 4, rounding = CornerRounding(radius = 0.55f))
        val triangle = RoundedPolygon(numVertices = 3, rounding = CornerRounding(radius = 0.40f))
        ShortcutBadgeFrame(Morph(square, triangle), rotationAngle = 0f, morphProgress = 0f)
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    /** The badge's text layout, and that of the same text set plainly at [size], as the badge used to set it. */
    private fun badgeAndPlain(shortcut: String, size: TextUnit): Pair<TextLayoutResult, TextLayoutResult> {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                Column {
                    SynchronizedMorphingShortcutBadge(shortcut, frame = { frame })
                    Text(shortcut, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = size, maxLines = 1)
                }
            }
        }
        val nodes = compose.onAllNodesWithText(shortcut)
        return nodes[0].layout() to nodes[1].layout()
    }

    private fun assertSameSize(shortcut: String, size: TextUnit) {
        val (badge, plain) = badgeAndPlain(shortcut, size)
        assertEquals(plain.multiParagraph.getLineWidth(0), badge.multiParagraph.getLineWidth(0), 0.01f)
        assertEquals(plain.multiParagraph.height, badge.multiParagraph.height, 0.01f)
    }

    @Test
    fun aLongTriggerFitsTheBadge() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { SynchronizedMorphingShortcutBadge("!youtube", frame = { frame }) }
        }
        assertFalse(compose.onNodeWithText("!youtube").layout().hasVisualOverflow)
    }

    /** Triggers that already fitted keep the size they always had. */
    @Test
    fun aShortTriggerKeepsItsSize() = assertSameSize("!yt", 12.sp)

    @Test
    fun aFourLetterTriggerKeepsItsSize() = assertSameSize("!gh2", 10.sp)

    @Test
    fun removeAllIsATalkBackAction() {
        var removed = 0
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { SlideToRemoveAllBar(onRemoveAll = { removed++ }) }
        }
        compose.onNodeWithContentDescription("Slide to remove all").performCustomAccessibilityActionWithLabel("Remove all")
        assertEquals(1, removed)
    }
}
