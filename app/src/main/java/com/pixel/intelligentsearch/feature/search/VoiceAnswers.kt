package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.answers.LiveAnswerParser
import com.pixel.intelligentsearch.core.answers.LiveAnswerQuery

/**
 * Whether a voice search was answered on the search screen itself: a sum, a conversion, the time somewhere, a forecast,
 * a definition. Then the answer stays on screen; otherwise the words go on to the web as Enter would. A card that only
 * offers to look something up, or a translation handed to another app, is not an answer here.
 */
internal object VoiceAnswers {

    /** Instant answers that are the answer itself ("10 mi = 16.09 km", "7:30 AM"), not a card to tap through. */
    private val ANSWER_CARDS = setOf("conversion", "currency", "date", "time", "random", "storage", "ip")

    /** True when [state] shows an answer to its own query. */
    fun shows(state: SearchUiState): Boolean {
        val answered = when (val live = state.liveAnswer?.takeIf { it.query == state.query }) {
            is LiveAnswer.Definition -> live.entry.senses.isNotEmpty()
            is LiveAnswer.Weather -> true
            is LiveAnswer.ScreenTime -> live.report != null
            is LiveAnswer.Translation, is LiveAnswer.WeatherNeedsLocation, null -> false
        }
        return answered || state.mathResult != null || state.instantAnswer?.iconType in ANSWER_CARDS
    }

    /** True when [state] shows, or can still get, an answer to its query. */
    fun mayAnswer(state: SearchUiState): Boolean = shows(state) || awaitsLiveAnswer(state)

    /** True while a definition, forecast or screen time for the query may still arrive. */
    fun awaitsLiveAnswer(state: SearchUiState): Boolean {
        if (state.liveAnswer?.query == state.query || state.liveAnswerSettled == state.query) return false
        return when (LiveAnswerParser.parse(state.query)) {
            null, is LiveAnswerQuery.Translate -> false
            else -> true
        }
    }
}
