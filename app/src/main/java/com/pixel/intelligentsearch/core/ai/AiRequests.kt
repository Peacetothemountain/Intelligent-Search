package com.pixel.intelligentsearch.core.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * One HTTPS request to a provider: where it goes, its headers (the key among them), its JSON body, and how long its
 * answer may take ([timeoutMs], [AiRequests.timeoutMs]): the whole exchange, and also the longest silence between two
 * lines, since a model can stream nothing at all while it thinks.
 */
class AiRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val timeoutMs: Long = AiRequests.timeoutMs(AiReasoning.OFF)
) {
    /** Never prints the headers: they carry the user's key. */
    override fun toString(): String = "AiRequest($url)"
}

/** What one line of a provider's event stream says. */
sealed interface AiEvent {
    data class Text(val text: String) : AiEvent
    /** The answer ended normally: the provider said why it stopped and it wasn't the limit or a refusal. */
    data object Finished : AiEvent
    /** The answer reached the output limit and was cut short. */
    data object Truncated : AiEvent
    /** The provider declined to answer, or blocked the question before answering (Gemini's prompt feedback). */
    data object Refused : AiEvent
    data class Error(val message: String) : AiEvent
}

/**
 * Builds each provider's streaming request for one question and reads its server-sent events. Pure, so what is sent
 * can be checked exactly: the question and nothing else of the user's, a short answer (within [outputLimit]), the
 * user's reasoning level and speed, no sampling settings, the key only in its header.
 */
object AiRequests {

    const val MAX_OUTPUT_TOKENS = 400

    /**
     * The limit at the least thinking ([outputLimit]): the limit caps thinking and answer together, so a model that
     * thinks gets more room; at low effort it thinks briefly and answers in well under this.
     */
    const val MAX_OUTPUT_TOKENS_WITH_THINKING = 1024
    const val ANTHROPIC_VERSION = "2026-10-21"
    /** Re-asks a declined question on a model of Anthropic's choosing, inside the same request. */
    const val ANTHROPIC_FALLBACK_BETA = "server-side-fallback-2026-07-01"

    /** The instructions sent with every question: the same for everyone, nothing about the user. */
    const val INSTRUCTIONS = "You answer a question typed into a phone's search bar. Reply in under 150 words: " +
        "the answer first, then at most a few short paragraphs or '-' bullet points. Use **bold** sparingly. No tables."

    /**
     * The request for [question] to [model], reasoning at [reasoning] (the model's default unless given; a level it
     * doesn't accept is never passed: [AiChoices] reads only accepted ones), in fast mode when [fast] and the model
     * has one.
     */
    fun build(
        provider: AiProvider,
        model: String,
        apiKey: String,
        question: String,
        reasoning: AiReasoning = provider.spec(model).defaultReasoning,
        fast: Boolean = false
    ): AiRequest {
        val withFast = fast && provider.spec(model).fast
        return when (provider) {
            AiProvider.ANTHROPIC -> anthropic(model, apiKey, question, reasoning, withFast)
            AiProvider.OPENAI -> openAi(model, apiKey, question, reasoning, withFast)
            AiProvider.GEMINI -> gemini(model, apiKey, question, reasoning, withFast)
        }
    }

    /**
     * The output limit at [reasoning]: thinking counts within it on every provider, so each level up gets more room,
     * and the answer itself stays under 150 words ([INSTRUCTIONS]). Only what is used is billed.
     */
    fun outputLimit(reasoning: AiReasoning): Int = when (reasoning) {
        AiReasoning.OFF -> MAX_OUTPUT_TOKENS
        AiReasoning.MINIMAL, AiReasoning.LOW -> MAX_OUTPUT_TOKENS_WITH_THINKING
        AiReasoning.MEDIUM -> 2_048
        AiReasoning.HIGH -> 4_096
        AiReasoning.XHIGH -> 8_192
        AiReasoning.MAX -> 16_000
    }

    /**
     * How long an answer at [reasoning] may take, the whole exchange and the longest silence while it thinks: 30
     * seconds while it thinks briefly or not at all, then more with each level's room ([outputLimit]) to think in, up
     * to six minutes at Max. A fixed 30 seconds cut High and above off before their answer could come.
     */
    fun timeoutMs(reasoning: AiReasoning): Long = when (reasoning) {
        AiReasoning.OFF, AiReasoning.MINIMAL, AiReasoning.LOW -> 30_000L
        AiReasoning.MEDIUM -> 60_000L
        AiReasoning.HIGH -> 120_000L
        AiReasoning.XHIGH -> 240_000L
        AiReasoning.MAX -> 360_000L
    }

    private fun anthropic(model: String, apiKey: String, question: String, reasoning: AiReasoning, fast: Boolean): AiRequest {
        // Off is each model's own way of not thinking: Sonnet 5.5 says "between_tools" ("disabled" is refused there),
        // Haiku 5.5 "disabled" (accepted at low effort). Every other level thinks adaptively at that effort; Opus and
        // Fable can't turn thinking off, so they never offer Off.
        val off = reasoning == AiReasoning.OFF
        val thinking = when {
            !off -> null
            model.startsWith("claude-sonnet") -> JSONObject().put("type", "between_tools")
            else -> JSONObject().put("type", "disabled")
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", outputLimit(reasoning))
            .put("stream", true)
            .put("system", INSTRUCTIONS)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", question)))
            // With thinking off, low effort keeps the answer quick; otherwise effort is how long it thinks.
            .put("output_config", JSONObject().put("effort", if (off) AiReasoning.LOW.id else reasoning.id))
        if (thinking != null) body.put("thinking", thinking)
        // Fast mode: up to 2.5 times the output speed at twice the price, on Opus 5.5 and Fable 5.2.
        if (fast) body.put("speed", "fast")
        val headers = linkedMapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to ANTHROPIC_VERSION,
            "content-type" to "application/json",
            "accept" to "text/event-stream"
        )
        // Haiku has no server-side fallback; Sonnet, Opus and Fable re-ask a declined question on another model.
        if (!model.startsWith("claude-haiku")) {
            body.put("fallbacks", "default")
            headers["anthropic-beta"] = ANTHROPIC_FALLBACK_BETA
        }
        return AiRequest("https://api.anthropic.com/v1/messages", headers, body.toString(), timeoutMs(reasoning))
    }

    private fun openAi(model: String, apiKey: String, question: String, reasoning: AiReasoning, fast: Boolean): AiRequest {
        // Reasoning tokens count against the limit. Off is "none", which only Terra and Luna accept.
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("max_completion_tokens", outputLimit(reasoning))
            .put("reasoning_effort", if (reasoning == AiReasoning.OFF) "none" else reasoning.id)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "developer").put("content", INSTRUCTIONS))
                    .put(JSONObject().put("role", "user").put("content", question))
            )
        // Fast mode, at twice the price; when it has no room OpenAI answers at standard speed and price instead.
        if (fast) body.put("service_tier", "fast")
        val headers = linkedMapOf(
            "authorization" to "Bearer $apiKey",
            "content-type" to "application/json",
            "accept" to "text/event-stream"
        )
        return AiRequest("https://api.openai.com/v1/chat/completions", headers, body.toString(), timeoutMs(reasoning))
    }

    private fun gemini(model: String, apiKey: String, question: String, reasoning: AiReasoning, fast: Boolean): AiRequest {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", INSTRUCTIONS))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question)))))
            .put(
                "generationConfig",
                JSONObject()
                    // Gemini can't turn thinking off and counts it within the limit.
                    .put("maxOutputTokens", outputLimit(reasoning))
                    // thinkingBudget is only kept for older models.
                    .put("thinkingConfig", JSONObject().put("thinkingLevel", reasoning.id))
            )
        // Google's fast mode is its Priority tier, for keys on Tier 2 and up; past its allowance it answers at standard.
        if (fast) body.put("service_tier", "priority")
        // The key goes in its header, never in the address, where it could end up in logs.
        val headers = linkedMapOf(
            "x-goog-api-key" to apiKey,
            "content-type" to "application/json",
            "accept" to "text/event-stream"
        )
        return AiRequest(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse",
            headers,
            body.toString(),
            timeoutMs(reasoning)
        )
    }

    /**
     * What one line of [provider]'s server-sent events says, in order (one chunk can carry the last words and why the
     * answer ended); keep-alives, metadata, the end marker and anything but a "data:" line say nothing.
     */
    fun parseLine(provider: AiProvider, line: String): List<AiEvent> {
        if (!line.startsWith("data:")) return emptyList()
        val data = line.removePrefix("data:").trim()
        if (data.isEmpty() || data == "[DONE]") return emptyList()
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return emptyList()
        json.optJSONObject("error")?.let { return listOf(AiEvent.Error(it.optString("message").ifBlank { "The provider reported an error." })) }
        return when (provider) {
            AiProvider.ANTHROPIC -> when (json.optString("type")) {
                "content_block_delta" -> listOfNotNull(
                    json.optJSONObject("delta")?.takeIf { it.optString("type") == "text_delta" }
                        ?.optString("text")?.takeIf { it.isNotEmpty() }?.let(AiEvent::Text)
                )
                "message_delta" -> listOfNotNull(endOf(json.optJSONObject("delta")?.optString("stop_reason"), "max_tokens", setOf("refusal")))
                else -> emptyList()
            }
            AiProvider.OPENAI -> {
                val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return emptyList()
                val text = choice.optJSONObject("delta")?.optString("content").orEmpty()
                listOfNotNull(
                    text.takeIf { it.isNotEmpty() }?.let(AiEvent::Text),
                    endOf(choice.optString("finish_reason"), "length", setOf("content_filter"))
                )
            }
            AiProvider.GEMINI -> {
                // A question Google blocks before answering comes back with only the reason, and no answer to read.
                if (json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty().isNotEmpty()) return listOf(AiEvent.Refused)
                val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: return emptyList()
                val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                val text = buildString {
                    for (i in 0 until (parts?.length() ?: 0)) {
                        val part = parts?.optJSONObject(i) ?: continue
                        if (!part.optBoolean("thought")) append(part.optString("text"))
                    }
                }
                listOfNotNull(
                    text.takeIf { it.isNotEmpty() }?.let(AiEvent::Text),
                    endOf(candidate.optString("finishReason"), "MAX_TOKENS", setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION"))
                )
            }
        }
    }

    /**
     * The end that stop reason [reason] means: [truncated], one of [refused], a normal finish, or none yet (no
     * reason, or JSON null, which org.json reads as "null").
     */
    private fun endOf(reason: String?, truncated: String, refused: Set<String>): AiEvent? = when (reason) {
        null, "", "null" -> null
        truncated -> AiEvent.Truncated
        in refused -> AiEvent.Refused
        else -> AiEvent.Finished
    }
}
