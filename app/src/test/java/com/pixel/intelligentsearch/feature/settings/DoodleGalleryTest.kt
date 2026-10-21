package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pixel.intelligentsearch.core.occasion.DoodleCatalog
import com.pixel.intelligentsearch.core.occasion.Occasion
import com.pixel.intelligentsearch.core.ui.LOADING_INDICATOR_TAG
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** Developer Settings' doodle gallery: picking, previewing on the widgets, searching and what it says about a doodle. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h2400dp-xhdpi")
class DoodleGalleryTest {

    @get:Rule val compose = createComposeRule(confinedEffects())

    private val today = LocalDate.of(2026, 10, 20)
    private val newYork = ZoneId.of("America/New_York")
    private val previews = mutableListOf<Occasion?>()

    @Before fun stillDoodles() = disableHandRolledAnimations()

    /** Opens the gallery as Developer Settings does, where showing a doodle on the widgets makes it the preview. */
    private fun open(previewing: Occasion?) {
        compose.setContent {
            var shown by remember { mutableStateOf(previewing) }
            MaterialTheme {
                DoodleGallery(
                    previewing = shown,
                    onPreview = { previews += it; shown = it },
                    onClose = {},
                    today = today,
                    zone = newYork,
                    region = "US"
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(LOADING_INDICATOR_TAG), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun card(title: String) = compose.onNode(hasClickAction() and hasText(title))

    @Test
    fun whileTheWidgetsPreviewTheyFollowTheDoodlePicked() {
        open(previewing = Occasion.HALLOWEEN)
        card("Pi Day").performClick()
        assertEquals(listOf<Occasion?>(Occasion.PI_DAY), previews)
    }

    @Test
    fun withoutAPreviewPickingOnlyShowsItHereUntilAskedToShowItOnTheWidgets() {
        open(previewing = null)
        card("Pi Day").performClick()
        assertEquals(emptyList<Occasion?>(), previews)
        compose.onNodeWithText("Show on Widgets").performClick()
        assertEquals(listOf<Occasion?>(Occasion.PI_DAY), previews)
    }

    @Test
    fun thePreviewCanBeStoppedFromTheStage() {
        open(previewing = Occasion.PI_DAY)
        compose.onNodeWithText("Stop Widget Preview").performClick()
        assertEquals(listOf<Occasion?>(null), previews)
    }

    @Test
    fun steppingMovesThroughTheDoodlesInOrder() {
        open(previewing = Occasion.PI_DAY)
        compose.onNodeWithContentDescription("Next Doodle").performClick()
        compose.onNodeWithContentDescription("Previous Doodle").performClick()
        compose.onNodeWithContentDescription("Previous Doodle").performClick()
        val order = Occasion.entries
        assertEquals(
            listOf(order[Occasion.PI_DAY.ordinal + 1], Occasion.PI_DAY, order[Occasion.PI_DAY.ordinal - 1]),
            previews
        )
    }

    @Test
    fun theStageSaysWhenAndWhereTheDoodleShows() {
        open(previewing = Occasion.DIWALI)
        compose.onNodeWithText("Not Shown in United States. Next in India Sunday, November 8, 2026.").assertExists()
        compose.onNodeWithText("Shown in India").assertExists()
    }

    @Test
    fun searchFindsDoodlesByCountry() {
        open(previewing = null)
        compose.onNode(hasSetTextAction()).performTextInput("india")
        compose.waitForIdle()
        card("Diwali").assertExists()
        card("Halloween").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("zzz")
        compose.onNodeWithText("No Doodles Match.").assertExists()
    }

    @Test
    fun cardLinesSayHowSoon() {
        val entries = DoodleCatalog.entries(today, newYork, "US").associateBy { it.occasion }
        assertEquals("In 11 Days", shortWhen(entries.getValue(Occasion.HALLOWEEN), today))
        assertEquals("India Only", shortWhen(entries.getValue(Occasion.DIWALI), today))
        assertEquals("Feb 29, 2028", shortWhen(entries.getValue(Occasion.LEAP_DAY), today))
        assertEquals("Shown in United States and Canada", whereText(entries.getValue(Occasion.THANKSGIVING), "US"))
        assertEquals("Shown Everywhere", whereText(entries.getValue(Occasion.HALLOWEEN), "US"))
        assertEquals(
            "Next Thursday, November 26, 2026, in 37 Days",
            longWhen(entries.getValue(Occasion.THANKSGIVING), today, "US")
        )
    }
}
