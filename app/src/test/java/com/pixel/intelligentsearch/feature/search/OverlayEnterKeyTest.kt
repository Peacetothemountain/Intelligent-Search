package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import com.pixel.intelligentsearch.core.ai.AiAnswerState
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Enter's order once short action phrasing and the keyboard highlight both apply: the highlight, then a bang's site,
 * then the phrasing's action, then the top result. And
 * the stops the arrow keys reach for the Ask row, an AI answer and the file-type chip.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayEnterKeyTest {

    private val timer = DirectAction("Start 15 min Timer", "15 minutes", "timer", Intent("android.intent.action.SET_TIMER"))
    private val highlighted = NavTarget.Correction("camera")

    private fun decide(
        query: String = "15 min",
        highlighted: NavTarget? = null,
        phrasing: DirectAction? = null,
        isBang: Boolean = false
    ) = OverlayEnterKey.decide(query, highlighted, phrasing, isBang)

    @Test
    fun theHighlightComesBeforeEverythingElse() {
        // A result the user moved to with the arrow keys opens, even on a bang.
        assertEquals(EnterOutcome.OpenHighlighted(highlighted), decide(highlighted = highlighted, phrasing = timer, isBang = true))
        assertEquals(EnterOutcome.OpenHighlighted(highlighted), decide(highlighted = highlighted, phrasing = timer))
    }

    @Test
    fun thePhrasingsActionRunsElseTheTopResultOpens() {
        assertEquals(EnterOutcome.RunAction(timer), decide(phrasing = timer))
        assertEquals(EnterOutcome.OpenTopResult, decide(query = "camera"))
    }

    @Test
    fun aBangGoesToItsSiteEvenWithAPhrasingAction() {
        assertEquals(EnterOutcome.SearchWeb, decide(query = "!g 15 min", phrasing = timer, isBang = true))
    }

    @Test
    fun nothingTypedDoesNothingUnlessSomethingIsHighlighted() {
        assertEquals(EnterOutcome.NothingTyped, decide(query = ""))
        assertEquals(EnterOutcome.OpenHighlighted(highlighted), decide(query = "", highlighted = highlighted))
    }

    @Test
    fun theAskRowFollowsTheSuggestedActions() {
        val sky = FileItem("sky.pdf", "/sdcard/sky.pdf", "application/pdf", "content://files/sky")
        val rows = NavSources(
            directActions = listOf(timer),
            aiAsk = AiProvider.ANTHROPIC to "why is the sky blue",
            bestMatch = sky
        ).rows()
        assertEquals(
            listOf(ResultListKeys.directAction(timer), ResultListKeys.ASK_AI, ResultListKeys.BEST_MATCH),
            rows.map { it.single().key }
        )
        assertEquals(NavTarget.AskAi(AiProvider.ANTHROPIC, "why is the sky blue"), rows[1].single().target)
        assertEquals(NavTarget.Result(sky), rows[2].single().target)
    }

    @Test
    fun anAiAnswerTakesTheAskRowsPlaceAndContinuesInTheAssistant() {
        val answer = AiAnswerState.Answer(AiProvider.GEMINI, "why is the sky blue", "Rayleigh scattering.", done = true)
        val rows = NavSources(aiAsk = AiProvider.GEMINI to "why is the sky blue", aiAnswer = answer).rows()
        assertEquals(listOf(ResultListKeys.AI_ANSWER), rows.map { it.single().key })
        assertEquals(
            NavTarget.Answer(AnswerAction.ContinueInAssistant(AiProvider.GEMINI, "why is the sky blue")),
            rows.single().single().target
        )
    }

    @Test
    fun theFileTypeChipLeadsTheFiles() {
        val invoice = FileItem("invoice.pdf", "/sdcard/invoice.pdf", "application/pdf", "content://files/invoice")
        val rows = NavSources(sectionOrder = listOf("files"), fileTypeFilter = FileTypeGroup.PDF, files = listOf(invoice)).rows()
        assertEquals(listOf(ResultListKeys.FILE_TYPE_FILTER, ResultListKeys.file(invoice)), rows.map { it.single().key })
        assertEquals(NavTarget.RemoveFileTypeFilter(FileTypeGroup.PDF), rows.first().single().target)
        // With no file of that type yet, the chip is still a stop.
        val chipOnly = NavSources(sectionOrder = listOf("files"), fileTypeFilter = FileTypeGroup.PDF).rows()
        assertEquals(listOf(ResultListKeys.FILE_TYPE_FILTER), chipOnly.map { it.single().key })
    }
}
