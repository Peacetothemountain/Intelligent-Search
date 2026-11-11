package com.pixel.intelligentsearch.core.ai

import com.pixel.intelligentsearch.core.ai.AiReasoning.HIGH
import com.pixel.intelligentsearch.core.ai.AiReasoning.LOW
import com.pixel.intelligentsearch.core.ai.AiReasoning.MAX
import com.pixel.intelligentsearch.core.ai.AiReasoning.MEDIUM
import com.pixel.intelligentsearch.core.ai.AiReasoning.MINIMAL
import com.pixel.intelligentsearch.core.ai.AiReasoning.OFF
import com.pixel.intelligentsearch.core.ai.AiReasoning.XHIGH
import com.pixel.intelligentsearch.core.bangs.AiAssistants

/**
 * How long a model thinks before it answers. [id] is what is stored, and what each provider is sent for it (Off is
 * sent as each provider's own way of not thinking); [label] and [hint] are what AI Answers shows.
 */
enum class AiReasoning(val id: String, val label: String, val hint: String) {
    OFF("off", "Off", "Answers Right Away, Without Thinking First."),
    MINIMAL("minimal", "Minimal", "Thinks for a Moment Before Answering."),
    LOW("low", "Low", "Thinks Briefly Before Answering."),
    MEDIUM("medium", "Medium", "Thinks a While for a More Careful Answer."),
    HIGH("high", "High", "Thinks Longer for Harder Questions. Slower, and Costs More."),
    XHIGH("xhigh", "Extra High", "Thinks Much Longer. Slower, and Costs More."),
    MAX("max", "Max", "Thinks as Long as It Needs. Slowest, and Costs the Most.")
}

/**
 * One model AI Answers offers: the [reasoning] levels it accepts (its provider refuses the others), the one it uses
 * until the user picks another ([defaultReasoning], the least each model allows, so answers come quickly), and
 * whether it has a [fast] mode.
 */
class AiModel(val id: String, val reasoning: List<AiReasoning>, val defaultReasoning: AiReasoning, val fast: Boolean) {
    init {
        require(defaultReasoning in reasoning) { "$id's default reasoning must be one it accepts" }
    }
}

/** Thinking levels: Claude and GPT take five; Gemini three, its Flash-Lite also Minimal; some can also skip thinking. */
private val THINKING = listOf(LOW, MEDIUM, HIGH, XHIGH, MAX)
private val GEMINI_THINKING = listOf(LOW, MEDIUM, HIGH)

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
    /** The models offered and what each accepts; the first is the default. */
    val specs: List<AiModel>,
    val assistant: AiAssistants.Assistant,
    /** Where the user makes a key, shown under the key field. */
    val keyPage: String
) {
    ANTHROPIC(
        "anthropic", "Anthropic Claude", "Claude", "api.anthropic.com",
        listOf(
            // Sonnet turns thinking off with "between_tools", Haiku with "disabled"; Opus and Fable can't turn it off.
            AiModel("claude-sonnet-5-5", listOf(OFF) + THINKING, OFF, fast = false),
            AiModel("claude-fable-5-2", THINKING, LOW, fast = true),
            AiModel("claude-opus-5-5", THINKING, LOW, fast = true),
            AiModel("claude-haiku-5-5", listOf(OFF) + THINKING, OFF, fast = false)
        ),
        AiAssistants.CLAUDE, "platform.claude.com"
    ),
    OPENAI(
        "openai", "OpenAI", "ChatGPT", "api.openai.com",
        // The GPT-6 family; Terra is its fast one for clear writing. Astra and 6.1 Sol can't turn reasoning off.
        listOf(
            AiModel("gpt-6.1-terra", listOf(OFF) + THINKING, OFF, fast = true),
            AiModel("gpt-6-astra", THINKING, LOW, fast = true),
            AiModel("gpt-6.1-sol", THINKING, LOW, fast = true),
            AiModel("gpt-6-luna", listOf(OFF) + THINKING, OFF, fast = true)
        ),
        AiAssistants.CHATGPT, "platform.openai.com"
    ),
    GEMINI(
        "gemini", "Google Gemini", "Gemini", "generativelanguage.googleapis.com",
        // Flash is Google's everyday model; Carbon and Argon are the Gemini 4 models. None can turn thinking off.
        listOf(
            AiModel("gemini-3.8-flash", GEMINI_THINKING, LOW, fast = true),
            AiModel("gemini-4-carbon", GEMINI_THINKING, LOW, fast = true),
            AiModel("gemini-4-argon", GEMINI_THINKING, LOW, fast = true),
            AiModel("gemini-3.5-flash-lite", listOf(MINIMAL) + GEMINI_THINKING, LOW, fast = true)
        ),
        AiAssistants.GEMINI, "aistudio.google.com"
    );

    val models: List<String> get() = specs.map { it.id }

    val defaultModel: String get() = models.first()

    /** [stored] if it is one of this provider's models, otherwise the default (a blank or stale choice). */
    fun model(stored: String): String = stored.takeIf { it in models } ?: defaultModel

    /** What [model] accepts; the default model's rules for one this provider doesn't offer. */
    fun spec(model: String): AiModel = specs.firstOrNull { it.id == model } ?: specs.first()

    companion object {
        fun fromId(id: String): AiProvider = entries.firstOrNull { it.id == id } ?: ANTHROPIC
    }
}
