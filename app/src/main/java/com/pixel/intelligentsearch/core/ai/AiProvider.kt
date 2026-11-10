package com.pixel.intelligentsearch.core.ai

import com.pixel.intelligentsearch.core.bangs.AiAssistants

/**
 * The providers AI Answers can ask, with the user's own API key: the endpoint's host (named in the settings and the
 * privacy policy), the models offered (the first is the default, the provider's current recommendation for short
 * answers), and the assistant an answer continues in.
 */
enum class AiProvider(
    /** Stored in [com.pixel.intelligentsearch.core.data.SettingsManager.AI_PROVIDER]; never changes. */
    val id: String,
    /** The company and product, for the provider choice: "Anthropic Claude". */
    val label: String,
    /** The product, for "Ask Claude" and the answer card. */
    val displayName: String,
    val host: String,
    val models: List<String>,
    val assistant: AiAssistants.Assistant,
    /** Where the user makes a key, shown under the key field. */
    val keyPage: String
) {
    ANTHROPIC(
        "anthropic", "Anthropic Claude", "Claude", "api.anthropic.com",
        listOf("claude-sonnet-5-5", "claude-fable-5-2", "claude-opus-5-5", "claude-haiku-5-5"),
        AiAssistants.CLAUDE, "platform.claude.com"
    ),
    OPENAI(
        "openai", "OpenAI", "ChatGPT", "api.openai.com",
        // The GPT-6 family; Terra is its fast one for clear writing, and answers without reasoning first.
        listOf("gpt-6.1-terra", "gpt-6-astra", "gpt-6.1-sol", "gpt-6-luna"),
        AiAssistants.CHATGPT, "platform.openai.com"
    ),
    GEMINI(
        "gemini", "Google Gemini", "Gemini", "generativelanguage.googleapis.com",
        // Flash is Google's everyday model; Carbon and Argon are the Gemini 4 models. The 2.5 models are retired.
        listOf("gemini-3.8-flash", "gemini-4-carbon", "gemini-4-argon", "gemini-3.5-flash-lite"),
        AiAssistants.GEMINI, "aistudio.google.com"
    );

    val defaultModel: String get() = models.first()

    /** [stored] if it is one of this provider's models, otherwise the default (a blank or stale choice). */
    fun model(stored: String): String = stored.takeIf { it in models } ?: defaultModel

    companion object {
        fun fromId(id: String): AiProvider = entries.firstOrNull { it.id == id } ?: ANTHROPIC
    }
}
