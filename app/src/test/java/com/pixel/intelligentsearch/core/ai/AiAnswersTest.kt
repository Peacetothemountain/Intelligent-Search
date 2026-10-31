package com.pixel.intelligentsearch.core.ai

import com.pixel.intelligentsearch.core.net.HttpsCall
import com.pixel.intelligentsearch.core.net.HttpsResponse
import com.pixel.intelligentsearch.core.net.HttpsTimeoutException
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.security.AiKeyVault
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Software stand-in for the Keystore key, which Robolectric doesn't have. */
internal class SoftwareAiKeys : AiKeyVault.Keys {
    var key: SecretKey? = null
    override fun getOrCreate(): SecretKey = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
    override fun existing(): SecretKey? = key
    override fun delete() {
        key = null
    }
}

/**
 * AI Answers against a stand-in transport, never the network: nothing is sent unless the user taps Ask, never while
 * the setting is off, Tor is the engine or no key is saved; only the question goes; and every failure is named.
 */
@RunWith(RobolectricTestRunner::class)
class AiAnswersTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsManager(context)
    private val vault = AiKeyVault(context, SoftwareAiKeys())

    /** Records every request; answers with [code] and streams [lines], or throws [failure]. */
    private class FakeTransport(var code: Int = 200, var lines: List<String> = emptyList(), var failure: Exception? = null) : AiTransport {
        val requests = mutableListOf<AiRequest>()
        override fun post(request: AiRequest, call: HttpsCall, onLine: (String) -> Unit): HttpsResponse {
            requests += request
            failure?.let { throw it }
            if (code == 200) lines.forEach(onLine)
            return HttpsResponse(code, if (code == 400) """{"error":{"message":"API key not valid","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""" else "")
        }
    }

    private val transport = FakeTransport()
    private val answers = AiAnswers(settings, vault, transport)

    @Before fun turnOn() {
        settings.prefs.edit().put(SettingsManager.AI_ANSWERS_ENABLED, true).put(SettingsManager.AI_PROVIDER, "anthropic").commit()
        vault.save(AiProvider.ANTHROPIC, "sk-ant-test-key-0123456789")
    }

    private fun ask(query: String) = runBlocking { answers.ask(query).toList() }

    @Test
    fun onlyQuestionsAreOffered() {
        assertEquals(AiProvider.ANTHROPIC, answers.offerFor("how tall is everest?"))
        assertEquals(AiProvider.ANTHROPIC, answers.offerFor("ask why the sky is blue"))
        assertNull(answers.offerFor("everest"))
        assertNull(answers.offerFor("?"))
        assertNull(answers.offerFor("ask"))
        assertEquals("why the sky is blue", AiAnswers.questionIn("Ask why the sky is blue"))
        // Other languages' question marks ask too: full-width (Chinese, Japanese) and Arabic.
        assertEquals("你好吗？", AiAnswers.questionIn("你好吗？"))
        assertEquals(AiProvider.ANTHROPIC, answers.offerFor("これは何ですか？"))
        assertEquals("كيف حالك؟", AiAnswers.questionIn("كيف حالك؟"))
        assertNull(answers.offerFor("？"))
        // Offering never sends anything.
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun nothingIsOfferedOrSentWhileOffUnderTorWithWebOffOrWithoutAKey() {
        settings.prefs.edit().put(SettingsManager.AI_ANSWERS_ENABLED, false).commit()
        assertNull(answers.offerFor("how tall is everest?"))
        assertEquals(AiProblem.OFF, (ask("how tall is everest?").single() as AiAnswerState.Failed).problem)

        settings.prefs.edit().put(SettingsManager.AI_ANSWERS_ENABLED, true).put(SettingsManager.SEARCH_ENGINE, "Tor Project").commit()
        assertNull(answers.offerFor("how tall is everest?"))
        assertEquals(AiProblem.TOR, (ask("how tall is everest?").single() as AiAnswerState.Failed).problem)

        // Web off in Search Sources means no web traffic, AI questions included.
        settings.prefs.edit().put(SettingsManager.SEARCH_ENGINE, "Google").put(SettingsManager.SEARCH_WEB, false).commit()
        assertNull(answers.offerFor("how tall is everest?"))
        assertEquals(AiProblem.WEB_OFF, (ask("how tall is everest?").single() as AiAnswerState.Failed).problem)

        settings.prefs.edit().put(SettingsManager.SEARCH_WEB, true).commit()
        vault.remove(AiProvider.ANTHROPIC)
        assertNull(answers.offerFor("how tall is everest?"))
        assertEquals(AiProblem.NO_KEY, (ask("how tall is everest?").single() as AiAnswerState.Failed).problem)

        assertTrue("nothing was sent", transport.requests.isEmpty())
    }

    @Test
    fun askingSendsOnlyTheQuestionAndStreamsTheAnswer() {
        transport.lines = listOf(
            "event: content_block_delta",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"About "}}""",
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"**8,849 m**."}}""",
            """data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}"""
        )
        val states = ask("ask how tall is everest")
        assertEquals(1, transport.requests.size)
        val request = transport.requests.single()
        assertEquals("sk-ant-test-key-0123456789", request.headers["x-api-key"])
        assertTrue(request.body.contains("\"content\":\"how tall is everest\""))
        assertTrue(states.first() is AiAnswerState.Asking)
        assertEquals("About ", (states[1] as AiAnswerState.Answer).text)
        val done = states.last() as AiAnswerState.Answer
        assertEquals("About **8,849 m**.", done.text)
        assertTrue(done.done)
        assertFalse("it ended normally", done.truncated)
    }

    @Test
    fun theChosenProviderAndModelAreUsed() {
        settings.prefs.edit().put(SettingsManager.AI_PROVIDER, "gemini").put(SettingsManager.AI_MODEL, "gemini-3.5-flash-lite").commit()
        vault.save(AiProvider.GEMINI, "gemini-key-0123456789")
        ask("why?")
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:streamGenerateContent?alt=sse",
            transport.requests.single().url
        )
        assertEquals("gemini-key-0123456789", transport.requests.single().headers["x-goog-api-key"])
    }

    @Test
    fun failuresAreNamed() {
        fun problemFor(code: Int) = run {
            transport.code = code
            (ask("how tall is everest?").last() as AiAnswerState.Failed).problem
        }
        assertEquals(AiProblem.BAD_KEY, problemFor(401))
        assertEquals(AiProblem.BAD_KEY, problemFor(403))
        assertEquals(AiProblem.QUOTA, problemFor(429))
        assertEquals(AiProblem.MODEL, problemFor(404))
        // Gemini says a bad key with 400.
        assertEquals(AiProblem.BAD_KEY, problemFor(400))
        assertEquals(AiProblem.PROVIDER, problemFor(529))

        transport.code = 200
        transport.failure = UnknownHostException("api.anthropic.com")
        assertEquals(AiProblem.OFFLINE, (ask("how tall is everest?").last() as AiAnswerState.Failed).problem)
        transport.failure = HttpsTimeoutException()
        assertEquals(AiProblem.TIMEOUT, (ask("how tall is everest?").last() as AiAnswerState.Failed).problem)

        transport.failure = null
        transport.lines = listOf("""data: {"type":"message_delta","delta":{"stop_reason":"refusal"}}""")
        assertEquals(AiProblem.REFUSED, (ask("how tall is everest?").last() as AiAnswerState.Failed).problem)
    }

    @Test
    fun aCutShortAnswerSaysSo() {
        transport.lines = listOf(
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Everest is"}}""",
            """data: {"type":"message_delta","delta":{"stop_reason":"max_tokens"}}"""
        )
        val done = ask("how tall is everest?").last() as AiAnswerState.Answer
        assertTrue(done.truncated)
        assertEquals("Everest is", done.text)
    }

    @Test
    fun anAnswerThatStopsBeforeItsEndSaysItWasCutShort() {
        // The stream just ends: no stop reason ever came.
        transport.lines = listOf("""data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Everest is"}}""")
        val dropped = ask("how tall is everest?").last() as AiAnswerState.Answer
        assertTrue(dropped.done)
        assertTrue(dropped.truncated)

        // An error event after some words.
        transport.lines = listOf(
            """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Everest is"}}""",
            """data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""
        )
        val failed = ask("how tall is everest?").last() as AiAnswerState.Answer
        assertEquals("Everest is", failed.text)
        assertTrue(failed.truncated)
    }

    /**
     * Typing on cancels the question's flow, and that must end the request at once: the stand-in blocks the way a
     * socket read does, ignoring interrupts, until its call is cancelled.
     */
    @Test(timeout = 15_000)
    fun typingOnEndsTheRequestInFlight() {
        val reached = java.util.concurrent.CountDownLatch(1)
        val released = java.util.concurrent.CountDownLatch(1)
        val blocking = AiTransport { _, call, _ ->
            call.onCancel { released.countDown() }
            reached.countDown()
            while (!call.isCancelled) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000)
            throw java.io.IOException("Socket closed")
        }
        val answers = AiAnswers(settings, vault, blocking)
        runBlocking {
            val job = launch(kotlinx.coroutines.Dispatchers.Default) { answers.ask("how tall is everest?").toList() }
            assertTrue("the request never went out", reached.await(5, java.util.concurrent.TimeUnit.SECONDS))
            job.cancel()
            assertTrue("cancelling didn't end the request", released.await(5, java.util.concurrent.TimeUnit.SECONDS))
            job.join()
        }
    }

    @Test
    fun aProblemNamesThePageItsFixChipOpens() {
        // The chip says "Open AI Answers", so the message points there too, not at Settings in general.
        for (problem in AiProblem.entries.filter { it.settingsPage == "ai_answers" && it != AiProblem.OFF }) {
            assertTrue(problem.message, problem.message.contains("in AI Answers"))
            assertFalse(problem.message, problem.message.contains("Settings"))
        }
        assertEquals("Add your API key in AI Answers to ask.", AiProblem.NO_KEY.message)
    }
}
