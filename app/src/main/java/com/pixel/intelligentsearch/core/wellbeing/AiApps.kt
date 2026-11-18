package com.pixel.intelligentsearch.core.wellbeing

/**
 * The apps counted as AI apps, by package (Play listings checked 2026-11-16; Intelligent Search's own assistant list,
 * AiAssistants.kt, is the first five). Only time in these apps can be measured: AI inside other apps isn't visible.
 * Gemini also counts its time inside the Google app ([Attribution]).
 */
object AiApps {
    /** Chat assistants, shown by name. */
    val assistants: Map<String, String> = linkedMapOf(
        "com.openai.chatgpt" to "ChatGPT",
        Attribution.GEMINI to "Gemini",
        "com.anthropic.claude" to "Claude",
        "ai.perplexity.app.android" to "Perplexity",
        "com.microsoft.copilot" to "Copilot",
        "ai.x.grok" to "Grok",
        "com.facebook.stella" to "Meta AI",
        "com.deepseek.chat" to "DeepSeek",
        "ai.mistral.chat" to "Mistral",
        "com.poe.android" to "Poe",
        "ai.inflection.pi" to "Pi",
        "me.proton.android.lumo" to "Lumo",
        "com.google.android.apps.labs.language.tailwind" to "NotebookLM",
        "tech.butterfly.app" to "Manus",
        "ai.perplexity.comet" to "Comet",
        "com.moonshot.kimichat" to "Kimi",
        "ai.qwenlm.chat.android" to "Qwen",
        "com.aliyun.tongyi" to "Qwen",
        "com.larus.nova" to "Doubao",
        "co.appnation.geniechat" to "Genie",
        "ai.chat.gpt.bot" to "ChatOn",
        "com.smartwidgetlabs.chatgpt" to "Chat Smith"
    )

    /** AI companion apps, counted with the rest. */
    val companions: Map<String, String> = linkedMapOf(
        "ai.character.app" to "Character AI",
        "ai.replika.app" to "Replika",
        "com.weaver.app.prod" to "Talkie",
        "ai.socialapps.speakmaster" to "PolyBuzz",
        "com.Beauchamp.Messenger.external" to "Chai",
        "com.kindroid.app" to "Kindroid"
    )

    val all: Set<String> = assistants.keys + companions.keys

    fun isAi(key: String): Boolean = key in all
}
