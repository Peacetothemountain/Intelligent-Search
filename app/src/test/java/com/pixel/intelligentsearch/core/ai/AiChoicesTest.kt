package com.pixel.intelligentsearch.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The user's AI Answers choices: each provider keeps its own model when another is chosen, favorites list first, and
 * a stored level or speed a model doesn't accept is never used.
 */
class AiChoicesTest {

    @Test
    fun eachProviderKeepsItsOwnModel() {
        var picks = emptySet<String>()
        picks = picks.withValue("anthropic", "claude-opus-5-5")
        picks = picks.withValue("openai", "gpt-6-luna")
        picks = picks.withValue("gemini", "gemini-4-argon")
        // Choosing again for one provider replaces only its own.
        picks = picks.withValue("openai", "gpt-6-astra")
        assertEquals("claude-opus-5-5", AiProvider.ANTHROPIC.chosenModel(picks, ""))
        assertEquals("gpt-6-astra", AiProvider.OPENAI.chosenModel(picks, ""))
        assertEquals("gemini-4-argon", AiProvider.GEMINI.chosenModel(picks, ""))
        assertEquals(3, picks.size)
    }

    @Test
    fun theOneEarlierChoiceStaysWithItsProviderAndFavoritesListFirst() {
        // Chosen before choices were kept per provider: it counts for its own provider only.
        assertEquals("gpt-6-luna", AiProvider.OPENAI.chosenModel(emptySet(), "gpt-6-luna"))
        assertEquals("claude-sonnet-5-5", AiProvider.ANTHROPIC.chosenModel(emptySet(), "gpt-6-luna"))
        // A model chosen for the provider wins, and one since removed falls back to the default.
        assertEquals("claude-fable-5-2", AiProvider.ANTHROPIC.chosenModel(setOf("anthropic=claude-fable-5-2"), ""))
        assertEquals("claude-sonnet-5-5", AiProvider.ANTHROPIC.chosenModel(setOf("anthropic=claude-opus-4-8"), ""))

        val favorites = setOf("claude-haiku-5-5", "claude-opus-5-5")
        assertEquals(
            listOf("claude-opus-5-5", "claude-haiku-5-5", "claude-sonnet-5-5", "claude-fable-5-2"),
            AiProvider.ANTHROPIC.favoritesFirst(favorites)
        )
        assertEquals(setOf("a"), setOf("a", "b").toggled("b"))
        assertEquals(setOf("a", "b"), setOf("a").toggled("b"))
    }

    @Test
    fun aLevelOrSpeedAModelRefusesIsNeverUsed() {
        val opus = AiProvider.ANTHROPIC.spec("claude-opus-5-5")
        val sonnet = AiProvider.ANTHROPIC.spec("claude-sonnet-5-5")
        val levels = setOf("claude-opus-5-5=off", "claude-sonnet-5-5=xhigh")
        // Opus can't turn thinking off: its default, not Off.
        assertEquals(AiReasoning.LOW, opus.reasoningFrom(levels))
        assertEquals(AiReasoning.XHIGH, sonnet.reasoningFrom(levels))
        assertEquals(AiReasoning.OFF, sonnet.reasoningFrom(emptySet()))
        // Each model keeps its own level.
        assertEquals(AiReasoning.HIGH, opus.reasoningFrom(levels.withValue("claude-opus-5-5", "high")))

        val fast = setOf("claude-opus-5-5", "claude-sonnet-5-5")
        assertTrue(opus.fastFrom(fast))
        assertFalse("Sonnet has no fast mode", sonnet.fastFrom(fast))
    }
}
