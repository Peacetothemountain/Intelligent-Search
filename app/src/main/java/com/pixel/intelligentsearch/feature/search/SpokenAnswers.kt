package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.answers.ForecastDay
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.answers.LiveAnswerParser
import com.pixel.intelligentsearch.core.answers.ScreenTimeCalculator
import com.pixel.intelligentsearch.core.answers.WeatherCodes
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What is said aloud after a voice search, for the answer the overlay is showing. Only answers that are an answer
 * are spoken: a card that only offers to look something up, a translation handed to another app, an address to copy
 * or a prompt for a permission is not.
 */
internal object SpokenAnswers {

    /** How long a spoken definition may run before it is cut at a sentence or a word. */
    private const val MAX_DEFINITION = 220

    /**
     * True when [state] holds, or can still get, an answer to its query: the spoken words then stay in search to be
     * answered, where a search with no answer goes on to the web as Enter would.
     */
    fun mayAnswer(state: SearchUiState): Boolean =
        sentence(state) != null || awaitsLiveAnswer(state)

    /** True while a definition, forecast or screen time for the query may still arrive. */
    fun awaitsLiveAnswer(state: SearchUiState): Boolean {
        if (state.liveAnswer?.query == state.query || state.liveAnswerSettled == state.query) return false
        return when (LiveAnswerParser.parse(state.query)) {
            null, is com.pixel.intelligentsearch.core.answers.LiveAnswerQuery.Translate -> false
            else -> true
        }
    }

    /** The sentence for what [state] shows for its query, or null when nothing shown is an answer to say. */
    fun sentence(state: SearchUiState): String? {
        state.liveAnswer?.takeIf { it.query == state.query }?.let { live -> live(live)?.let { return it } }
        state.mathResult?.let { math -> math(math)?.let { return it } }
        return state.instantAnswer?.let(::instant)
    }

    internal fun live(answer: LiveAnswer): String? = when (answer) {
        is LiveAnswer.Definition -> answer.entry.senses.firstOrNull()?.let { "${answer.entry.word}: ${clip(it.definition)}" }
        is LiveAnswer.Weather -> {
            val report = answer.report
            val sky = WeatherCodes.describe(report.code).lowercase(Locale.US)
            val high = report.high.roundToInt()
            val low = report.low.roundToInt()
            val place = report.placeName.takeIf { it.isNotBlank() && !answer.here }
            if (report.day == ForecastDay.TOMORROW) {
                "Tomorrow${place?.let { " in $it" }.orEmpty()}: $sky, with a high of $high and a low of $low."
            } else {
                "${report.temperature.roundToInt()} degrees and $sky${place?.let { " in $it" }.orEmpty()}. High $high, low $low."
            }
        }
        is LiveAnswer.ScreenTime -> answer.report?.let { "${spokenDuration(ScreenTimeCalculator.formatDuration(it.totalMs))} of screen time today." }
        is LiveAnswer.Translation, is LiveAnswer.WeatherNeedsLocation -> null
    }

    /** A number is said; a result written in another base ("0b1111", "255 (0xFF)") is for reading, not hearing. */
    internal fun math(result: String): String? =
        if (result.startsWith("0x") || result.startsWith("0b") || result.startsWith("0o") || '(' in result) null
        else "The answer is $result."

    internal fun instant(answer: InstantAnswer): String? = when (answer.iconType) {
        // "100 USD ≈ 92.00 EUR" is said "100 USD is about 92.00 EUR".
        "conversion", "currency" -> answer.title.replace(" = ", " is ").replace(" \u2248 ", " is about ")
        // "41 days until Friday, December 25, 2026.": the card's whole sentence, not "41 days" on its own.
        "date" -> answer.spoken?.let { sentence -> "${sentence.replaceFirstChar { it.uppercase() }}." } ?: answer.title
        // "Current time in Tokyo, tomorrow: 1:30 AM.", saying where and which day as well as what time.
        "time" -> "${answer.subtitle.replace(" · ", ", ").replace("Tomorrow", "tomorrow").replace("Yesterday", "yesterday")}: ${answer.title}."
        "random" -> answer.title
        // "118 GB free" + "of 256 GB · 53% used"
        "storage" -> "${answer.title} ${answer.subtitle.substringBefore(" · ")}."
        else -> null
    }

    /** "3 hr 12 min" said in full: "3 hours 12 minutes"; "1 hr" as "1 hour". */
    internal fun spokenDuration(short: String): String =
        short.replace(Regex("\\b1 hr\\b"), "1 hour").replace(Regex("\\b1 min\\b"), "1 minute")
            .replace(" hr", " hours").replace(" min", " minutes")

    private fun clip(definition: String): String {
        val text = definition.trim()
        if (text.length <= MAX_DEFINITION) return text
        val sentenceEnd = text.lastIndexOf(". ", MAX_DEFINITION)
        if (sentenceEnd > MAX_DEFINITION / 3) return text.substring(0, sentenceEnd + 1)
        return text.substring(0, text.lastIndexOf(' ', MAX_DEFINITION).takeIf { it > 0 } ?: MAX_DEFINITION)
    }
}
