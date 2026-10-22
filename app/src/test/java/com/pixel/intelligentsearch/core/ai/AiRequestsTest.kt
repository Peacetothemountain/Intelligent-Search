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
        assertTrue(body.getBoolean("stream"))
        assertEquals("default", body.getString("fallbacks"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        val message = body.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", message.getString("role"))
        assertEquals(question, message.getString("content"))
        assertEquals(1, body.getJSONArray("messages").length())
        // No sampling settings (the API refuses them) and nothing but what is listed here.
        assertEquals(setOf("model", "max_tokens", "stream", "system", "messages", "output_config", "fallbacks"), body.keySet())
    }

    @Test
    fun haikuHasNoFallback() {
        val request = AiRequests.build(AiProvider.ANTHROPIC, "claude-haiku-5-5", key, question)
        assertFalse("anthropic-beta" in request.headers)
        assertFalse(JSONObject(request.body).has("fallbacks"))
        assertEquals("claude-haiku-5-5", JSONObject(request.body).getString("model"))
    }

    @Test
    fun openAiGetsChatCompletionsWithABearerKey() {
        val request = AiRequests.build(AiProvider.OPENAI, "gpt-5.1", key, question)
        assertEquals("https://api.openai.com/v1/chat/completions", request.url)
        assertEquals("Bearer $key", request.headers["authorization"])
        val body = JSONObject(request.body)
        assertEquals("gpt-5.1", body.getString("model"))
        assertEquals(400, body.getInt("max_completion_tokens"))
        assertEquals("none", body.getString("reasoning_effort"))
        assertTrue(body.getBoolean("stream"))
        val messages = body.getJSONArray("messages")
        assertEquals("developer", messages.getJSONObject(0).getString("role"))
        assertEquals(AiRequests.INSTRUCTIONS, messages.getJSONObject(0).getString("content"))
        assertEquals(question, messages.getJSONObject(1).getString("content"))
        assertEquals("minimal", JSONObject(AiRequests.build(AiProvider.OPENAI, "gpt-5-mini", key, question).body).getString("reasoning_effort"))
    }

    @Test
    fun geminiGetsTheModelInTheAddressAndTheKeyInItsHeader() {
        val request = AiRequests.build(AiProvider.GEMINI, "gemini-2.5-flash", key, question)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse", request.url)
        assertEquals(key, request.headers["x-goog-api-key"])
        // Never in the address, where logs could keep it.
        assertFalse(key in request.url)
        val body = JSONObject(request.body)
        val config = body.getJSONObject("generationConfig")
        assertEquals(400, config.getInt("maxOutputTokens"))
        assertEquals(0, config.getJSONObject("thinkingConfig").getInt("thinkingBudget"))
        val part = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0)
        assertEquals(question, part.getString("text"))
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
        assertEquals("claude-sonnet-5-5", AiProvider.ANTHROPIC.model("gpt-5.1"))
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
    }
}
