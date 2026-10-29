package com.pixel.intelligentsearch.feature.search

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import com.pixel.intelligentsearch.core.data.WebSuggestion
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Suggestion rows as Google lays them out: an entity's picture in a matte circle beside its name and description when
 * Show Thumbnails is on, the search icon when it is off, and the completion in bold.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WebSuggestionRowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val obamaPicture = "https://encrypted-tbn0.gstatic.com/images?q=tbn:obama"
    private val obama = WebSuggestion("obama", "Barack Obama", "44th U.S. President", obamaPicture)

    @Before fun setUp() {
        hilt.inject()
        ResultThumbnails.clear()
        ResultThumbnails.download = { null }
    }

    @After fun tearDown() = ResultThumbnails.clear()

    /** The picture as already fetched, so the row shows it on its first frame. */
    private fun cachePicture(source: ThumbnailSource, size: androidx.compose.ui.unit.Dp) {
        val px = (size.value * compose.density.density).let { kotlin.math.round(it).toInt() }
        ResultThumbnails.put(source, px, Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap())
    }

    private fun showRow(suggestion: WebSuggestion, showThumbnail: Boolean, isRecent: Boolean = false, onFill: () -> Unit = {}) {
        compose.setContent {
            OverlayWebSuggestionRow(suggestion, typed = "obama", isRecent = isRecent, showThumbnail = showThumbnail, onSearch = {}, onRemove = {}, onFill = onFill)
        }
    }

    @Test
    fun anEntityShowsItsPictureNameAndDescription() {
        cachePicture(ThumbnailSource.Web(obamaPicture), SUGGESTION_LEADING_SIZE)
        showRow(obama, showThumbnail = true)
        compose.onAllNodesWithTag(RESULT_THUMBNAIL_TAG, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Barack Obama", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("44th U.S. President", useUnmergedTree = true).assertExists()
    }

    @Test
    fun withShowThumbnailsOffTheRowKeepsTheSearchIcon() {
        cachePicture(ThumbnailSource.Web(obamaPicture), SUGGESTION_LEADING_SIZE)
        showRow(obama, showThumbnail = false)
        compose.onAllNodesWithTag(RESULT_THUMBNAIL_TAG, useUnmergedTree = true).assertCountEquals(0)
        // The description still says who it is.
        compose.onNodeWithText("44th U.S. President", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aPictureThatCannotLoadLeavesTheSearchIcon() {
        showRow(obama, showThumbnail = true)
        compose.waitForIdle()
        compose.onAllNodesWithTag(RESULT_THUMBNAIL_TAG, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Barack Obama", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aPastSearchShowsAsHistoryWithoutAPicture() {
        cachePicture(ThumbnailSource.Web(obamaPicture), SUGGESTION_LEADING_SIZE)
        showRow(obama, showThumbnail = true, isRecent = true)
        compose.onAllNodesWithTag(RESULT_THUMBNAIL_TAG, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Remove obama from history").assertExists()
    }

    @Test
    fun theArrowFillsTheSuggestionIn() {
        var filled = 0
        showRow(WebSuggestion("obama library"), showThumbnail = true) { filled++ }
        compose.onNodeWithContentDescription("Edit search obama library").performClick()
        assertEquals(1, filled)
    }

    @Test
    fun theCompletionIsBoldAndAnEntityNameIsNot() {
        val plain = suggestionLabel(WebSuggestion("obama library"), "obama")
        assertEquals("obama library", plain.text)
        val bold = plain.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals(" library", plain.text.substring(bold.start, bold.end))

        // Google's own marks (a corrected word as well as the completion) are kept.
        val marked = suggestionLabel(WebSuggestion("taylor swift wedding", boldRanges = listOf(0..5, 10..19)), "teylor swi")
        assertEquals(listOf("taylor", "ft wedding"), marked.spanStyles.map { marked.text.substring(it.start, it.end) })

        val entity = suggestionLabel(obama, "obama")
        assertEquals("Barack Obama", entity.text)
        assertEquals(0, entity.spanStyles.size)
    }

    @Test
    fun suggestionRequestsAreSpacedLikeChromesOmnibox() {
        assertEquals(100L, SUGGEST_REQUEST_SPACING_MS)
        assertEquals(SUGGEST_REQUEST_SPACING_MS, suggestRequestDelay(0))
        assertEquals(60L, suggestRequestDelay(40))
        assertEquals(0L, suggestRequestDelay(SUGGEST_REQUEST_SPACING_MS))
        assertEquals(0L, suggestRequestDelay(10_000))
    }
}
