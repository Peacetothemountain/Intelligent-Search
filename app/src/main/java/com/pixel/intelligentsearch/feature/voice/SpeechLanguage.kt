package com.pixel.intelligentsearch.feature.voice

import android.content.Context
import android.os.LocaleList
import android.view.inputmethod.InputMethodManager
import java.util.Locale

/**
 * Which language voice search listens in. A speech engine left to pick for itself uses only the phone's first
 * language, and the on-device engine knows only the languages downloaded to it, so a phone set to one it lacks
 * (Albanian, say, or English for a region it has no model for) failed at once with "language not supported". Instead
 * the engine is asked what it knows, and the first of the user's own languages it has is chosen.
 */
internal object SpeechLanguage {

    /** The user's languages, best first: Android's languages in the order the user set them, then the keyboard's. */
    fun wanted(context: Context): List<Locale> {
        val system = LocaleList.getDefault().let { list -> (0 until list.size()).map { list[it] } }
        val keyboard = runCatching {
            context.getSystemService(InputMethodManager::class.java)?.currentInputMethodSubtype?.languageTag
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag)
        return (system + listOfNotNull(keyboard)).filter { it.language.isNotEmpty() }.distinct()
    }

    /**
     * The tag, out of [supported] (as the engine reports them, "en-US"), for the first of [wanted] the engine can
     * recognize: the same language in the user's own region if offered, else its usual form ("en-US" for English,
     * "de-DE" for German), else any form of it. Null when the engine has none of the user's languages.
     */
    fun pick(wanted: List<Locale>, supported: Collection<String>): String? {
        val offered = supported.mapNotNull { tag ->
            Locale.forLanguageTag(tag.replace('_', '-')).takeIf { it.language.isNotEmpty() }?.let { tag to it }
        }
        for (locale in wanted) {
            val language = base(locale.language)
            val same = offered.filter { base(it.second.language) == language }
            if (same.isEmpty()) continue
            val usual = USUAL_REGION[language] ?: language.uppercase(Locale.ROOT)
            return (same.firstOrNull { locale.country.isNotEmpty() && it.second.country == locale.country }
                ?: same.firstOrNull { it.second.country.isEmpty() }
                ?: same.firstOrNull { it.second.country == usual }
                ?: same.first()).first
        }
        return null
    }

    /** Google names Mandarin "cmn" ("cmn-Hans-CN") where Android says "zh". */
    private fun base(language: String) = if (language == "cmn") "zh" else language

    /** A language's usual region where it isn't the language's own code. */
    private val USUAL_REGION = mapOf("en" to "US", "pt" to "BR", "zh" to "CN", "ja" to "JP", "ko" to "KR", "sq" to "AL",
        "hi" to "IN", "ar" to "SA", "sv" to "SE", "da" to "DK", "uk" to "UA", "el" to "GR", "cs" to "CZ", "he" to "IL",
        "iw" to "IL", "vi" to "VN", "fa" to "IR", "ur" to "PK", "bn" to "IN", "sr" to "RS", "ms" to "MY", "nb" to "NO")
}
