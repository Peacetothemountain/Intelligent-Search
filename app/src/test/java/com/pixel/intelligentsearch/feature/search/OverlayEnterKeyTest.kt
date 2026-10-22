package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import com.pixel.intelligentsearch.core.ai.AiAnswerState
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.SavedLink
import com.pixel.intelligentsearch.core.data.settings.EnterKeyAction
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Enter's order once short action phrasing, trigger words, the keyboard highlight and Enter Key Action all apply:
 * a trigger word, then the highlight, then the phrasing's action (with Open Top Result), then Enter Key Action. And
 * the stops the arrow keys reach for the Ask row, an AI answer, Saved Links and the file-type chip.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayEnterKeyTest {

    private val timer = DirectAction("Start 15 min Timer", "15 minutes", "timer", Intent("android.intent.action.SET_TIMER"))
    private val highlighted = NavTarget.Correction("camera")
    private val pinnedResult = "the trigger word's result"

    private fun decide(
        query: String = "15 min",
        triggered: Any? = null,
        highlighted: NavTarget? = null,
        phrasing: DirectAction? = null,
        isBang: Boolean = false,
        setting: EnterKeyAction = EnterKeyAction.OPEN_TOP_RESULT
    ) = OverlayEnterKey.decide(query, triggered, highlighted, phrasing, isBang, setting)

    @Test
    fun aTriggerWordComesBeforeEverythingElse() {
        for (setting in EnterKeyAction.entries) {
            assertEquals(
                EnterOutcome.OpenTriggered(pinnedResult),
                decide(triggered = pinnedResult, highlighted = highlighted, phrasing = timer, isBang = true, setting = setting)
            )
        }
    }

    @Test
    fun theHighlightComesBeforeThePhrasingAndTheSetting() {
        for (setting in EnterKeyAction.entries) {
            assertEquals(EnterOutcome.OpenHighlighted(highlighted), decide(highlighted = highlighted, phrasing = timer, setting = setting))
        }
    }

    @Test
    fun withOpenTopResultThePhrasingsActionRuns() {
        assertEquals(EnterOutcome.RunAction(timer), decide(phrasing = timer))
        assertEquals(EnterOutcome.OpenTopResult, decide(query = "camera"))
    }

    @Test
    fun searchTheWebSkipsThePhrasingsAction() {
        assertEquals(EnterOutcome.SearchWeb, decide(phrasing = timer, setting = EnterKeyAction.SEARCH_WEB))
        assertEquals(EnterOutcome.SearchWeb, decide(query = "camera", setting = EnterKeyAction.SEARCH_WEB))
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
    fun theAskRowAndSavedLinksFollowTheSuggestedActions() {
        val link = SavedLink("Timesheet", "https://work.example.com/timesheet")
        val rows = NavSources(
            directActions = listOf(timer),
            aiAsk = AiProvider.ANTHROPIC to "why is the sky blue",
            savedLinks = listOf(link),
            bestMatch = link
        ).rows()
        assertEquals(
            listOf(ResultListKeys.directAction(timer), ResultListKeys.ASK_AI, ResultListKeys.savedLink(link), ResultListKeys.BEST_MATCH),
            rows.map { it.single().key }
        )
        assertEquals(NavTarget.AskAi(AiProvider.ANTHROPIC, "why is the sky blue"), rows[1].single().target)
        assertEquals(NavTarget.Result(link), rows[2].single().target)
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

    @Test
    fun aPinnedSavedLinkHasItsOwnStop() {
        val link = SavedLink("Docs", "https://docs.example.com")
        val rows = NavSources(pinned = listOf(link)).rows()
        assertEquals(ResultListKeys.inRow(ResultListKeys.PINNED, "link:https://docs.example.com"), rows.single().single().key)
    }
}
