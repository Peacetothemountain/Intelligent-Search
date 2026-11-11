package com.pixel.intelligentsearch.core.ai

import com.pixel.intelligentsearch.core.data.SettingsManager

/*
 * The user's AI Answers choices, kept per provider and per model so moving between providers never loses one: each
 * provider's model (SettingsManager.AI_MODELS, "provider=model"), each model's reasoning (AI_REASONING,
 * "model=level"), the models asked in fast mode and the favorites (sets of ids). Stored values a model doesn't accept
 * (a restored backup, a model since removed) are ignored, never sent.
 */

/** What one question is asked with: the [model], how long it reasons, and whether in fast mode. */
data class AiChoice(val model: String, val reasoning: AiReasoning, val fast: Boolean)

/**
 * The model [this] provider asks: the one chosen for it; else [legacy], the single choice kept before choices were
 * kept per provider, when it is this provider's; else its default. Starring a favorite never changes it.
 */
fun AiProvider.chosenModel(picks: Set<String>, legacy: String): String =
    picks.valueOf(id)?.takeIf { it in models }
        ?: legacy.takeIf { it in models }
        ?: defaultModel

/** This provider's models, its favorites first, each group in the provider's own order. */
fun AiProvider.favoritesFirst(favorites: Set<String>): List<String> =
    models.filter { it in favorites } + models.filterNot { it in favorites }

/** How long [this] model reasons: the level chosen for it, if it accepts it, else its default. */
fun AiModel.reasoningFrom(stored: Set<String>): AiReasoning =
    stored.valueOf(id)?.let { level -> reasoning.firstOrNull { it.id == level } } ?: defaultReasoning

/** Whether [this] model is asked in fast mode: chosen for it, and it has one. */
fun AiModel.fastFrom(fastModels: Set<String>): Boolean = fast && id in fastModels

/** [this] set with [key]'s value set to [value], replacing any it had. */
fun Set<String>.withValue(key: String, value: String): Set<String> =
    filterNot { it.startsWith("$key=") }.toSet() + "$key=$value"

/** [this] set with [id] added, or taken out if it was in it. */
fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id

object AiChoices {
    /** Everything [provider]'s next question is asked with, from [settings]. */
    fun choiceFor(settings: SettingsManager, provider: AiProvider): AiChoice {
        val model = provider.spec(provider.chosenModel(settings[SettingsManager.AI_MODELS], settings[SettingsManager.AI_MODEL]))
        return AiChoice(
            model.id,
            model.reasoningFrom(settings[SettingsManager.AI_REASONING]),
            model.fastFrom(settings[SettingsManager.AI_FAST_MODELS])
        )
    }
}

private fun Set<String>.valueOf(key: String): String? =
    firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
