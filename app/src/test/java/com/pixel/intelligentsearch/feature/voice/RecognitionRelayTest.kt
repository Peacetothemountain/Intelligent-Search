package com.pixel.intelligentsearch.feature.voice

import android.os.Bundle
import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The assistant's speech recognizer relays what the delegate recognizer hears to the app that asked. Segmented
 * sessions (dictation that keeps going through pauses) report each phrase on its own and never send onResults.
 */
@RunWith(RobolectricTestRunner::class)
class RecognitionRelayTest {

    /** Records every call it gets, by name. */
    private class RecordingSink : RecognitionSink {
        val calls = mutableListOf<String>()
        val bundles = mutableListOf<Bundle>()
        override fun readyForSpeech(params: Bundle) { calls += "readyForSpeech" }
        override fun beginningOfSpeech() { calls += "beginningOfSpeech" }
        override fun rmsChanged(rmsdB: Float) { calls += "rmsChanged" }
        override fun bufferReceived(buffer: ByteArray) { calls += "bufferReceived" }
        override fun endOfSpeech() { calls += "endOfSpeech" }
        override fun error(error: Int) { calls += "error" }
        override fun results(results: Bundle) { calls += "results"; bundles += results }
        override fun partialResults(partialResults: Bundle) { calls += "partialResults"; bundles += partialResults }
        override fun segmentResults(segmentResults: Bundle) { calls += "segmentResults"; bundles += segmentResults }
        override fun endOfSegmentedSession() { calls += "endOfSegmentedSession" }
        override fun languageDetection(results: Bundle) { calls += "languageDetection"; bundles += results }
    }

    private fun phrase(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    @Test
    fun everyPhraseOfASegmentedSessionReachesTheCaller() {
        val sink = RecordingSink()
        val relay = RecognitionRelay(sink)
        val first = phrase("weather in")
        val second = phrase("Paris tomorrow")
        relay.onSegmentResults(first)
        relay.onSegmentResults(second)
        relay.onEndOfSegmentedSession()
        assertEquals(listOf("segmentResults", "segmentResults", "endOfSegmentedSession"), sink.calls)
        assertEquals(listOf(first, second), sink.bundles)
    }

    @Test
    fun languageDetectionReachesTheCaller() {
        val sink = RecordingSink()
        val detected = Bundle()
        RecognitionRelay(sink).onLanguageDetection(detected)
        assertEquals(listOf("languageDetection"), sink.calls)
        assertEquals(listOf(detected), sink.bundles)
    }

    @Test
    fun singlePhraseCallbacksStillReachTheCaller() {
        val sink = RecordingSink()
        val relay = RecognitionRelay(sink)
        relay.onReadyForSpeech(null)
        relay.onBeginningOfSpeech()
        relay.onPartialResults(phrase("hello"))
        relay.onEndOfSpeech()
        relay.onResults(null)
        relay.onError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf("readyForSpeech", "beginningOfSpeech", "partialResults", "endOfSpeech", "results", "error"), sink.calls)
    }

    @Test
    fun aCallerThatHasGoneAwayDoesNotBreakTheRelay() {
        val sink = object : RecognitionSink by RecordingSink() {
            override fun segmentResults(segmentResults: Bundle) = throw android.os.DeadObjectException()
        }
        // Must not throw into the delegate recognizer.
        RecognitionRelay(sink).onSegmentResults(phrase("lost"))
    }
}
