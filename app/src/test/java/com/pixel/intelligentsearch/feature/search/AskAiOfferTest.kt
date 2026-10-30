package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ai.AiAnswers
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.answers.DictionaryEntry
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.search.ShortActionPhrasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** The Ask row never sits on top of an answer that says something else for the same query. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class AskAiOfferTest {

    @Test
    fun aQueryEnterRunsAsAReminderOffersNoAskRow() {
        val query = "ask sarah tomorrow at 6pm"
        // It reads as an AI question ("ask ...") and as a reminder Enter sets: the reminder wins.
        assertNotNull(AiAnswers.questionIn(query))
        val reminder = ShortActionPhrasing.parse(query, 0L, false, TimeZone.getTimeZone("UTC"), Locale.US)
        assertEquals(ShortActionPhrasing.Kind.REMINDER, reminder?.kind)
        assertTrue(reminder!!.runsOnEnter)

        val enter = DirectAction(reminder.title, reminder.subtitle, "calendar", reminder.intent)
        assertNull(SearchUiState(query = query, aiOffer = AiProvider.ANTHROPIC, enterAction = enter).askAi)
        assertEquals(AiProvider.ANTHROPIC, SearchUiState(query = query, aiOffer = AiProvider.ANTHROPIC).askAi)
    }

    @Test
    fun aQueryThatReadsAsAGuessedReminderOffersNoAskRowEither() {
        // "Ask sarah at 6" doesn't say AM or PM, so Enter leaves the reminder for a tap (the certainty rule). The row is
        // still the answer at the top, and the Ask row would say something else beside it.
        val query = "ask sarah at 6"
        val reminder = ShortActionPhrasing.parse(query, 0L, false, TimeZone.getTimeZone("UTC"), Locale.US)
        assertEquals(ShortActionPhrasing.Kind.REMINDER, reminder?.kind)
        assertFalse(reminder!!.runsOnEnter)

        val guessed = DirectAction(reminder.title, reminder.subtitle, "calendar", reminder.intent)
        assertNull(SearchUiState(query = query, aiOffer = AiProvider.ANTHROPIC, guessedAction = guessed).askAi)
    }

    @Test
    fun aQueryWithADefinitionOffersNoAskRow() {
        val query = "what does serendipity mean?"
        assertNotNull(AiAnswers.questionIn(query))
        val definition = LiveAnswer.Definition(query, DictionaryEntry("serendipity", null, emptyList(), null))
        assertNull(SearchUiState(query = query, aiOffer = AiProvider.ANTHROPIC, liveAnswer = definition).askAi)
        // A definition kept from an earlier query doesn't count.
        val stale = LiveAnswer.Definition("define serendipity", DictionaryEntry("serendipity", null, emptyList(), null))
        assertEquals(AiProvider.ANTHROPIC, SearchUiState(query = query, aiOffer = AiProvider.ANTHROPIC, liveAnswer = stale).askAi)
    }

    @Test
    fun withoutAnOfferThereIsNoRow() {
        assertNull(SearchUiState(query = "how tall is everest?").askAi)
    }
}
