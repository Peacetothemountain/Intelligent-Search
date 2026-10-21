package com.pixel.intelligentsearch.feature.search

import android.Manifest
import android.content.ComponentName
import android.content.IntentFilter
import android.speech.RecognitionService
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSpeechRecognizer

/**
 * Voice search listens only while the search screen is on screen. Once it is gone (home, another app, the screen
 * off) the microphone is let go, and nothing heard afterwards is searched.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayVoiceLifecycleTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val heard = mutableListOf<String>()
    private val problems = mutableListOf<String>()
    private lateinit var voice: OverlayVoice

    @Before
    fun setUp() {
        val app = compose.activity.application
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val recognizer = ComponentName("com.example.speech", "com.example.speech.Recognizer")
        shadowOf(app.packageManager).addServiceIfNotPresent(recognizer)
        shadowOf(app.packageManager).addIntentFilterForService(recognizer, IntentFilter(RecognitionService.SERVICE_INTERFACE))
        compose.setContent { voice = rememberOverlayVoice(onHeard = { heard += it }, onProblem = { problems += it }) }
        compose.runOnIdle { voice.start() }
        assertTrue(voice.active)
    }

    private fun latestRecognizer(): ShadowSpeechRecognizer {
        val recognizer = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        assertNotNull(recognizer)
        return Shadow.extract(recognizer)
    }

    @Test
    fun leavingTheScreenStopsListening() {
        val recognizer = latestRecognizer()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        assertFalse(voice.active)
        assertTrue(recognizer.isDestroyed)
        assertTrue(heard.isEmpty())
        assertTrue(problems.isEmpty())
    }

    @Test
    fun aDialogOverTheScreenKeepsItListening() {
        // The microphone permission dialog, or the notification shade, only pauses the screen.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)

        assertTrue(voice.active)
        assertFalse(latestRecognizer().isDestroyed)
    }
}
