package com.pixel.intelligentsearch.feature.voice

import android.Manifest
import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer

/**
 * The assistant sheet shows whatever the recognition manager says, so its states and messages are checked here
 * against a simulated speech recognizer: listening, live words, processing, the final result, and each failure.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class VoiceRecognitionManagerTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var manager: VoiceRecognitionManager

    @Before fun setUp() {
        hilt.inject()
        ShadowSpeechRecognizer.reset()
        manager = VoiceRecognitionManager(app)
    }

    @After fun tearDown() = ShadowSpeechRecognizer.reset()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun grantMicrophone() = shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)

    private fun recognizer() = shadowOf(assertNotNullAndGet(ShadowSpeechRecognizer.getLatestSpeechRecognizer()))

    private fun <T> assertNotNullAndGet(value: T?): T { assertNotNull("no recognizer was created", value); return value!! }

    private fun words(vararg text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(*text))
    }

    @Test
    fun withoutTheMicrophoneItExplainsInsteadOfListening() {
        manager.startListening()
        idle()
        assertEquals(VoiceRecognitionState.ERROR, manager.state.value)
        assertEquals("Microphone permission required for voice search.", manager.errorMessage.value)
    }

    @Test
    fun aSpokenRequestGoesFromListeningToTheResult() {
        grantMicrophone()
        manager.startListening()
        idle()
        assertEquals(VoiceRecognitionState.INITIALIZING, manager.state.value)

        recognizer().triggerOnReadyForSpeech(Bundle())
        assertEquals(VoiceRecognitionState.LISTENING, manager.state.value)

        recognizer().triggerOnPartialResults(words("set a timer"))
        assertEquals("set a timer", manager.recognizedText.value)

        recognizer().triggerOnRmsChanged(8f)
        assertTrue("speech raises the waveform", manager.amplitude.floatValue > 0f)

        recognizer().triggerOnEndOfSpeech()
        assertEquals(VoiceRecognitionState.PROCESSING, manager.state.value)
        assertEquals(0f, manager.amplitude.floatValue)

        recognizer().triggerOnResults(words("set a timer for 10 minutes", "set a time for 10 minutes"))
        assertEquals(VoiceRecognitionState.RESULT, manager.state.value)
        assertEquals("the best match wins", "set a timer for 10 minutes", manager.recognizedText.value)
    }

    @Test
    fun silenceAndEmptyResultsAskTheUserToTryAgain() {
        grantMicrophone()
        manager.startListening()
        idle()
        recognizer().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(VoiceRecognitionState.ERROR, manager.state.value)
        assertEquals("No speech detected. Tap mic to retry.", manager.errorMessage.value)

        manager.startListening()
        idle()
        recognizer().triggerOnResults(words(""))
        assertEquals(VoiceRecognitionState.ERROR, manager.state.value)
        assertEquals("No words recognized. Tap mic to speak.", manager.errorMessage.value)
    }

    @Test
    fun eachFailureHasItsOwnMessage() {
        grantMicrophone()
        val expected = mapOf(
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT to "Listening timed out. Tap mic to speak.",
            SpeechRecognizer.ERROR_NETWORK to "Network connection issue. Tap mic to retry.",
            SpeechRecognizer.ERROR_AUDIO to "Audio recording error. Check microphone.",
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS to "Voice service busy. Please wait a moment."
        )
        for ((code, message) in expected) {
            manager.startListening()
            idle()
            recognizer().triggerOnError(code)
            assertEquals("error $code", message, manager.errorMessage.value)
        }
    }

    @Test
    fun tappingTheMicWhileListeningStops() {
        grantMicrophone()
        manager.startListening()
        idle()
        recognizer().triggerOnReadyForSpeech(Bundle())
        manager.toggleListening()
        idle()
        assertEquals(VoiceRecognitionState.INITIALIZING, manager.state.value)
    }
}
