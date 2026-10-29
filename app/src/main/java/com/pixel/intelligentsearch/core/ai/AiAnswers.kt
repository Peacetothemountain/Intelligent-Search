package com.pixel.intelligentsearch.core.ai

import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.net.HttpsResponse
import com.pixel.intelligentsearch.core.net.HttpsTimeoutException
import com.pixel.intelligentsearch.core.net.withHttpsCall
import com.pixel.intelligentsearch.core.security.AiKeyVault
import com.pixel.intelligentsearch.core.security.NetworkPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Why a question got no answer, each with what the card says, the settings page that fixes it ([settingsPage], a
 * [com.pixel.intelligentsearch.core.navigation.Route] key) and whether asking again could work ([retryable]).
 */
enum class AiProblem(val message: String, val settingsPage: String? = null, val retryable: Boolean = false) {
    OFF("AI Answers is off.", settingsPage = "ai_answers"),
    TOR("AI Answers is off while Tor is your search engine.", settingsPage = "web_search"),
    WEB_OFF("AI Answers needs Web on in Search Sources.", settingsPage = "search_sources"),
    NO_KEY("Add your API key in Settings to ask.", settingsPage = "ai_answers"),
    BAD_KEY("The API key was refused. Check it in Settings.", settingsPage = "ai_answers"),
    QUOTA("Your key's rate limit or quota is used up. Try again later."),
    OFFLINE("No connection. Check your internet and try again.", retryable = true),
    TIMEOUT("The answer took too long. Try again.", retryable = true),
    MODEL("This model isn't available to your key. Pick another in Settings.", settingsPage = "ai_answers"),
    REFUSED("The provider declined to answer this."),
    PROVIDER("The provider couldn't answer right now. Try again later.", retryable = true)
}

/** One question's answer as it arrives. */
sealed interface AiAnswerState {
    val provider: AiProvider
    val question: String

    data class Asking(override val provider: AiProvider, override val question: String) : AiAnswerState
    /**
     * [text] so far; [done] once the stream ended, [truncated] when the answer was cut short: it hit the output limit,
     * or the stream stopped before the provider said it was finished.
     */
    data class Answer(
        override val provider: AiProvider,
        override val question: String,
        val text: String,
        val done: Boolean = false,
        val truncated: Boolean = false
    ) : AiAnswerState
    data class Failed(override val provider: AiProvider, override val question: String, val problem: AiProblem) : AiAnswerState
}

/**
 * Optional AI Answers with the user's own key. A query that reads as a question ([questionIn]) gets an "Ask" row while
 * the setting is on, a key is saved and Tor isn't the engine; nothing is sent until the user taps it, and then only that
 * question goes, over HTTPS, to the chosen provider. Nothing about the user, the phone or other searches goes with it,
 * and neither the question nor the key is ever logged.
 */
@Singleton
class AiAnswers internal constructor(
    private val settings: SettingsManager,
    private val vault: AiKeyVault,
    private val transport: AiTransport
) {
    @Inject constructor(settings: SettingsManager, vault: AiKeyVault) : this(settings, vault, HttpsAiTransport())

    val provider: AiProvider get() = AiProvider.fromId(settings[SettingsManager.AI_PROVIDER])

    /** Why asking isn't possible right now, or null when it is. */
    fun unavailableReason(): AiProblem? {
        if (!settings[SettingsManager.AI_ANSWERS_ENABLED]) return AiProblem.OFF
        return when (NetworkPolicy.aiBlock(settings.prefs)) {
            NetworkPolicy.Block.TOR -> AiProblem.TOR
            NetworkPolicy.Block.WEB_OFF, NetworkPolicy.Block.WEB_SUGGESTIONS_OFF -> AiProblem.WEB_OFF
            null -> AiProblem.NO_KEY.takeUnless { vault.has(provider) }
        }
    }

    /** The provider an Ask row offers for [query], or null when there is no row: not a question, or asking is off. */
    fun offerFor(query: String): AiProvider? =
        if (questionIn(query) != null && unavailableReason() == null) provider else null

    /**
     * Asks [query]'s question (see [questionIn]): only ever called from the user's tap. Emits [AiAnswerState.Asking],
     * then the answer as it streams in, ending done or failed. Runs on the IO dispatcher; cancelling stops the request.
     */
    fun ask(query: String): Flow<AiAnswerState> = channelFlow {
        val provider = provider
        val question = questionIn(query)
        val blocked = unavailableReason()
        if (question == null || blocked != null) {
            send(AiAnswerState.Failed(provider, query.trim(), blocked ?: AiProblem.OFF))
            return@channelFlow
        }
        val key = vault.get(provider)
        if (key == null) {
            send(AiAnswerState.Failed(provider, question, AiProblem.NO_KEY))
            return@channelFlow
        }
        send(AiAnswerState.Asking(provider, question))
        val request = AiRequests.build(provider, provider.model(settings[SettingsManager.AI_MODEL]), key, question)
        val text = StringBuilder()
        var truncated = false
        var finished = false
        var problem: AiProblem? = null
        val response = try {
            // Typing on cancels this flow; that cancels the call, which closes the connection mid-stream.
            withHttpsCall { call ->
                transport.post(request, call) { line ->
                    for (event in AiRequests.parseLine(provider, line)) {
                        when (event) {
                            is AiEvent.Text -> {
                                text.append(event.text)
                                trySend(AiAnswerState.Answer(provider, question, text.toString()))
                            }
                            AiEvent.Finished -> finished = true
                            AiEvent.Truncated -> truncated = true
                            AiEvent.Refused -> if (text.isEmpty()) problem = AiProblem.REFUSED
                            is AiEvent.Error -> if (problem == null) problem = AiProblem.PROVIDER
                        }
                    }
                }
            }
        } catch (e: HttpsTimeoutException) {
            if (text.isEmpty()) problem = AiProblem.TIMEOUT
            null
        } catch (e: UnknownHostException) {
            problem = AiProblem.OFFLINE
            null
        } catch (e: ConnectException) {
            problem = AiProblem.OFFLINE
            null
        } catch (e: IOException) {
            if (text.isEmpty()) problem = AiProblem.OFFLINE
            null
        }
        currentCoroutineContext().ensureActive()
        if (response != null && response.code != 200) problem = problemFor(response)
        // Words came but the stream never said it ended (dropped, timed out, an error mid-answer): the card says the
        // answer was cut short rather than passing part of one off as whole.
        if (text.isNotEmpty() && !finished) truncated = true
        val failed = problem
        send(
            if (failed != null && text.isEmpty()) AiAnswerState.Failed(provider, question, failed)
            else if (text.isEmpty()) AiAnswerState.Failed(provider, question, AiProblem.PROVIDER)
            else AiAnswerState.Answer(provider, question, text.toString(), done = true, truncated = truncated)
        )
    }.flowOn(Dispatchers.IO)

    companion object {
        /**
         * The question in [query], or null when it isn't one: it ends with "?" ("how tall is everest?") or starts with
         * "ask " ("ask why the sky is blue", sent without the "ask"). A bare "?" or "ask" asks nothing.
         */
        fun questionIn(query: String): String? {
            val trimmed = query.trim()
            val lower = trimmed.lowercase(Locale.ROOT)
            val question = when {
                lower.startsWith("ask ") -> trimmed.substring(4).trim()
                trimmed.endsWith("?") -> trimmed
                else -> return null
            }
            return question.takeIf { q -> q.trimEnd('?').count { it.isLetterOrDigit() } >= 2 && q.length <= MAX_QUESTION_LENGTH }
        }

        const val MAX_QUESTION_LENGTH = 500

        /** What a refused request's [response] means. */
        internal fun problemFor(response: HttpsResponse): AiProblem = when (response.code) {
            401, 403 -> AiProblem.BAD_KEY
            429 -> AiProblem.QUOTA
            404 -> AiProblem.MODEL
            400 -> when {
                // Gemini says a bad key with 400 and API_KEY_INVALID.
                response.errorBody.contains("API_KEY", ignoreCase = true) || response.errorBody.contains("api key", ignoreCase = true) -> AiProblem.BAD_KEY
                response.errorBody.contains("model", ignoreCase = true) -> AiProblem.MODEL
                else -> AiProblem.PROVIDER
            }
            402 -> AiProblem.QUOTA
            else -> AiProblem.PROVIDER
        }
    }
}
