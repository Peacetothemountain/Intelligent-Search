package com.pixel.intelligentsearch.core.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What each provider is sent for one question: its endpoint over HTTPS, the key only in its header, the chosen model,
 * a short answer and the question itself, and nothing else of the user's. And how each streamed answer is read.
 */
@RunWith(RobolectricTestRunner::class)
class AiRequestsTest {

    private val key = "sk-test-0123456789abcdef"
    private val question = "how tall is everest?"

    private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()

    @Test
    fun anthropicGetsTheMessagesApiWithItsVersionAndFallbacks() {
        val request = AiRequests.build(AiProvider.ANTHROPIC, "claude-sonnet-5-5", key, question)
        assertEquals("https://api.anthropic.com/v1/messages", request.url)
        assertEquals(key, request.headers["x-api-key"])
        assertEquals("2026-10-21", request.headers["anthropic-version"])
        assertEquals(AiRequests.ANTHROPIC_FALLBACK_BETA, request.headers["anthropic-beta"])
        val body = JSONObject(request.body)
        assertEquals("claude-sonnet-5-5", body.getString("model"))
        assertEquals(400, body.getInt("max_tokens"))
        // Sonnet 5.5's lowest thinking setting ("disabled" is refused there); a fallback runs it as disabled.
        assertEquals("between_tools", body.getJSONObject("thinking").getString("type"))
        assertEquals(setOf("type"), body.getJSONObject("thinking").keySet())
        assertTrue(body.getBoolean("stream"))
        assertEquals("default", body.getString("fallbacks"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        val message = body.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", message.getString("role"))
        assertEquals(question, message.getString("content"))
        assertEquals(1, body.getJSONArray("messages").length())
        // No sampling settings (the API refuses them) and nothing but what is listed here.
        assertEquals(setOf("model", "max_tokens", "stream", "system", "messages", "output_config", "thinking", "fallbacks"), body.keySet())
    }

    @Test
    fun opusAndFableThinkAtLowEffortWithRoomForTheAnswer() = listOf("claude-opus-5-5", "claude-fable-5-2").forEach { model ->
        val body = JSONObject(AiRequests.build(AiProvider.ANTHROPIC, model, key, question).body)
        // Thinking can't be turned off on Opus 5.5 or Fable 5.2, and max_tokens counts it with the answer.
        assertFalse(body.has("thinking"))
        assertEquals(1024, body.getInt("max_tokens"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        assertEquals("default", body.getString("fallbacks"))
    }

    @Test
    fun haikuHasNoFallback() {
        val request = AiRequests.build(AiProvider.ANTHROPIC, "claude-haiku-5-5", key, question)
        assertFalse("anthropic-beta" in request.headers)
        assertFalse(JSONObject(request.body).has("fallbacks"))
        assertEquals("claude-haiku-5-5", JSONObject(request.body).getString("model"))
        // Haiku 5.5 accepts thinking off at low effort, so the whole limit is for the answer.
        assertEquals("disabled", JSONObject(request.body).getJSONObject("thinking").getString("type"))
        assertEquals(400, JSONObject(request.body).getInt("max_tokens"))
    }

    @Test
    fun openAiGetsChatCompletionsWithABearerKey() {
        val request = AiRequests.build(AiProvider.OPENAI, "gpt-6.1-terra", key, question)
        assertEquals("https://api.openai.com/v1/chat/completions", request.url)
        assertEquals("Bearer $key", request.headers["authorization"])
        val body = JSONObject(request.body)
        assertEquals("gpt-6.1-terra", body.getString("model"))
        assertEquals(400, body.getInt("max_completion_tokens"))
        assertEquals("none", body.getString("reasoning_effort"))
        assertTrue(body.getBoolean("stream"))
        val messages = body.getJSONArray("messages")
        assertEquals("developer", messages.getJSONObject(0).getString("role"))
        assertEquals(AiRequests.INSTRUCTIONS, messages.getJSONObject(0).getString("content"))
        assertEquals(question, messages.getJSONObject(1).getString("content"))
        val sol = JSONObject(AiRequests.build(AiProvider.OPENAI, "gpt-6.1-sol", key, question).body)
        assertEquals("Sol refuses none: its lowest, with room", "low" to 1024, sol.getString("reasoning_effort") to sol.getInt("max_completion_tokens"))
    }

    @Test
    fun geminiGetsTheModelInTheAddressAndTheKeyInItsHeader() {
        val request = AiRequests.build(AiProvider.GEMINI, "gemini-3.8-flash", key, question)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:streamGenerateContent?alt=sse", request.url)
        assertEquals(key, request.headers["x-goog-api-key"])
        // Never in the address, where logs could keep it.
        assertFalse(key in request.url)
        val body = JSONObject(request.body)
        val config = body.getJSONObject("generationConfig")
        assertEquals(AiRequests.MAX_OUTPUT_TOKENS_WITH_THINKING, config.getInt("maxOutputTokens"))
        // Gemini 3's thinking level, lowest both Flash models offer; never with a thinkingBudget (a 400 together).
        assertEquals("low", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        assertFalse(config.getJSONObject("thinkingConfig").has("thinkingBudget"))
        val part = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0)
        assertEquals(question, part.getString("text"))
    }

    @Test
    fun theChosenReasoningAndSpeedReachEachProvider() {
        fun body(provider: AiProvider, model: String, reasoning: AiReasoning, fast: Boolean) =
            JSONObject(AiRequests.build(provider, model, key, question, reasoning, fast).body)

        // Claude: a thinking level is adaptive thinking at that effort, with room for it; Off is each model's own way.
        val sonnet = body(AiProvider.ANTHROPIC, "claude-sonnet-5-5", AiReasoning.HIGH, fast = true)
        assertFalse(sonnet.has("thinking"))
        assertEquals("high", sonnet.getJSONObject("output_config").getString("effort"))
        assertEquals(4096, sonnet.getInt("max_tokens"))
        assertFalse("Sonnet has no fast mode", sonnet.has("speed"))
        val haiku = body(AiProvider.ANTHROPIC, "claude-haiku-5-5", AiReasoning.OFF, fast = false)
        assertEquals("disabled", haiku.getJSONObject("thinking").getString("type"))
        assertEquals("low", haiku.getJSONObject("output_config").getString("effort"))
        val opus = body(AiProvider.ANTHROPIC, "claude-opus-5-5", AiReasoning.MAX, fast = true)
        assertEquals("fast", opus.getString("speed"))
        assertEquals("max", opus.getJSONObject("output_config").getString("effort"))
        assertEquals(16_000, opus.getInt("max_tokens"))

        val terra = body(AiProvider.OPENAI, "gpt-6.1-terra", AiReasoning.MEDIUM, fast = true)
        assertEquals("medium", terra.getString("reasoning_effort"))
        assertEquals(2048, terra.getInt("max_completion_tokens"))
        assertEquals("fast", terra.getString("service_tier"))
        assertFalse(body(AiProvider.OPENAI, "gpt-6.1-terra", AiReasoning.OFF, fast = false).has("service_tier"))

        // Gemini's fast mode is its Priority tier, named at the top of the request, not in its config.
        val argon = body(AiProvider.GEMINI, "gemini-4-argon", AiReasoning.HIGH, fast = true)
        assertEquals("priority", argon.getString("service_tier"))
        val config = argon.getJSONObject("generationConfig")
        assertEquals("high", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        assertEquals(4096, config.getInt("maxOutputTokens"))
        assertEquals("minimal", body(AiProvider.GEMINI, "gemini-3.5-flash-lite", AiReasoning.MINIMAL, fast = false)
            .getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
    }

    @Test
    fun everyRequestIsHttpsAndTheKeyNeverPrints() {
        for (provider in AiProvider.entries) {
            val request = AiRequests.build(provider, provider.defaultModel, key, question)
            assertTrue(request.url, request.url.startsWith("https://${provider.host}/"))
            assertFalse(key in request.toString())
            assertFalse(key in request.body)
        }
    }

    @Test
    fun theDefaultModelsAreTheCurrentRecommendations() {
        assertEquals("claude-sonnet-5-5", AiProvider.ANTHROPIC.defaultModel)
        assertEquals("claude-sonnet-5-5", AiProvider.ANTHROPIC.model(""))
        // A model of another provider (or a removed one) falls back to the default.
        assertEquals("gpt-6.1-terra", AiProvider.OPENAI.model("gpt-5.1"))
        assertEquals("claude-opus-5-5", AiProvider.ANTHROPIC.model("claude-opus-5-5"))
    }

    @Test
    fun anthropicStreamsTextAndSaysWhyItEnded() {
        val p = AiProvider.ANTHROPIC
        assertEquals(listOf(AiEvent.Text("Hello")), AiRequests.parseLine(p, """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""))
        assertEquals(emptyList<AiEvent>(), AiRequests.parseLine(p, """data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":""}}"""))
        assertEquals(emptyList<AiEvent>(), AiRequests.parseLine(p, "event: message_start"))
        assertEquals(listOf(AiEvent.Truncated), AiRequests.parseLine(p, """data: {"type":"message_delta","delta":{"stop_reason":"max_tokens"}}"""))
        assertEquals(listOf(AiEvent.Refused), AiRequests.parseLine(p, """data: {"type":"message_delta","delta":{"stop_reason":"refusal"}}"""))
        assertEquals(listOf(AiEvent.Finished), AiRequests.parseLine(p, """data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}"""))
        assertEquals(listOf(AiEvent.Error("Overloaded")), AiRequests.parseLine(p, """data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))
    }

    @Test
    fun openAiAndGeminiStreamTextAndTheirLastChunk() {
        assertEquals(listOf(AiEvent.Text("Hi")), AiRequests.parseLine(AiProvider.OPENAI, """data: {"choices":[{"delta":{"content":"Hi"},"finish_reason":null}]}"""))
        assertEquals(listOf(AiEvent.Text("end"), AiEvent.Truncated), AiRequests.parseLine(AiProvider.OPENAI, """data: {"choices":[{"delta":{"content":"end"},"finish_reason":"length"}]}"""))
        assertEquals(emptyList<AiEvent>(), AiRequests.parseLine(AiProvider.OPENAI, "data: [DONE]"))
        assertEquals(
            listOf(AiEvent.Text("8,849 m"), AiEvent.Truncated),
            AiRequests.parseLine(AiProvider.GEMINI, """data: {"candidates":[{"content":{"parts":[{"text":"8,849 m"}]},"finishReason":"MAX_TOKENS"}]}""")
        )
        assertEquals(listOf(AiEvent.Refused), AiRequests.parseLine(AiProvider.GEMINI, """data: {"candidates":[{"finishReason":"SAFETY"}]}"""))
        // A normal end is told apart from a stream that just stops.
        assertEquals(listOf(AiEvent.Finished), AiRequests.parseLine(AiProvider.OPENAI, """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}"""))
        assertEquals(listOf(AiEvent.Text("Done"), AiEvent.Finished), AiRequests.parseLine(AiProvider.GEMINI, """data: {"candidates":[{"content":{"parts":[{"text":"Done"}]},"finishReason":"STOP"}]}"""))
    }
}
