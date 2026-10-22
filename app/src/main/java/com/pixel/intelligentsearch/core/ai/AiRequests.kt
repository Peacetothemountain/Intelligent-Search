package com.pixel.intelligentsearch.core.ai

import org.json.JSONArray
import org.json.JSONObject

/** One HTTPS request to a provider: where it goes, its headers (the key among them) and its JSON body. */
class AiRequest(val url: String, val headers: Map<String, String>, val body: String) {
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
    /** The provider declined to answer. */
    data object Refused : AiEvent
    data class Error(val message: String) : AiEvent
}

/**
 * Builds each provider's streaming request for one question and reads its server-sent events. Pure, so what is sent
 * can be checked exactly: the question and nothing else of the user's, a short answer (at most [MAX_OUTPUT_TOKENS]),
 * no sampling settings, the key only in its header.
 */
object AiRequests {

    const val MAX_OUTPUT_TOKENS = 400
    const val ANTHROPIC_VERSION = "2026-10-21"
    /** Re-asks a declined question on a model of Anthropic's choosing, inside the same request. */
    const val ANTHROPIC_FALLBACK_BETA = "server-side-fallback-2026-07-01"

    /** The instructions sent with every question: the same for everyone, nothing about the user. */
    const val INSTRUCTIONS = "You answer a question typed into a phone's search bar. Reply in under 150 words: " +
        "the answer first, then at most a few short paragraphs or '-' bullet points. Use **bold** sparingly. No tables."

    fun build(provider: AiProvider, model: String, apiKey: String, question: String): AiRequest = when (provider) {
        AiProvider.ANTHROPIC -> anthropic(model, apiKey, question)
        AiProvider.OPENAI -> openAi(model, apiKey, question)
        AiProvider.GEMINI -> gemini(model, apiKey, question)
    }

    private fun anthropic(model: String, apiKey: String, question: String): AiRequest {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_OUTPUT_TOKENS)
            .put("stream", true)
            .put("system", INSTRUCTIONS)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", question)))
            // Low effort keeps the thinking short, so the answer fits the output limit and comes quickly.
            .put("output_config", JSONObject().put("effort", "low"))
        val headers = linkedMapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to ANTHROPIC_VERSION,
            "content-type" to "application/json",
            "accept" to "text/event-stream"
        )
        // Haiku has no server-side fallback; Sonnet and Opus re-ask a declined question on another model.
        if (!model.startsWith("claude-haiku")) {
            body.put("fallbacks", "default")
            headers["anthropic-beta"] = ANTHROPIC_FALLBACK_BETA
        }
        return AiRequest("https://api.anthropic.com/v1/messages", headers, body.toString())
    }

    private fun openAi(model: String, apiKey: String, question: String): AiRequest {
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            // Reasoning tokens count against the limit; the least reasoning each model allows keeps room for the answer.
            .put("max_completion_tokens", MAX_OUTPUT_TOKENS)
            .put("reasoning_effort", if (model == "gpt-5.1") "none" else "minimal")
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "developer").put("content", INSTRUCTIONS))
                    .put(JSONObject().put("role", "user").put("content", question))
            )
        val headers = linkedMapOf(
            "authorization" to "Bearer $apiKey",
            "content-type" to "application/json",
            "accept" to "text/event-stream"
        )
        return AiRequest("https://api.openai.com/v1/chat/completions", headers, body.toString())
    }

    private fun gemini(model: String, apiKey: String, question: String): AiRequest {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", INSTRUCTIONS))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question)))))
            .put(
                "generationConfig",
                JSONObject()
                    .put("maxOutputTokens", MAX_OUTPUT_TOKENS)
                    // No thinking: the whole limit is for the answer.
                    .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            )
        // The key goes in its header, never in the address, where it could end up in logs.
        val headers = linkedMapOf(
            "x-goog-api-key" to apiKey,
            "content-type" to "application/json",
            "accept" to "text/event-stream"
        )
        return AiRequest(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse",
            headers,
            body.toString()
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
                    endOf(candidate.optString("finishReason"), "MAX_TOKENS", setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST"))
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
