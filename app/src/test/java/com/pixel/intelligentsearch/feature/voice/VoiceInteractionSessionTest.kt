package com.pixel.intelligentsearch.feature.voice

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInteractionSessionTest {

    @Test
    fun testRmsDbNormalization() {
        // Test min boundary clamping
        val minNorm = Dictation.normalizeRmsDb(-10f)
        assertEquals(0.0f, minNorm, 0.001f)

        // Test max boundary clamping
        val maxNorm = Dictation.normalizeRmsDb(20f)
        assertEquals(1.0f, maxNorm, 0.001f)

        // Test mid-range normalization (-2dB -> 0.0, 10dB -> 1.0, 4dB -> 0.5)
        val midNorm = Dictation.normalizeRmsDb(4f)
        assertEquals(0.5f, midNorm, 0.001f)

        // Test silence (-2dB)
        val silenceNorm = Dictation.normalizeRmsDb(-2f)
        assertEquals(0.0f, silenceNorm, 0.001f)
    }

    @Test
    fun testVisualizerDecayPreventsFreezeAtNonZeroAmplitude() {
        // Simulates the visualizer smoothing engine when speech stops
        var smoothedLevel = 0.85f // Active speech amplitude
        val targetLevel = 0.0f    // onSpeechEnded() sets target to 0f

        // Over 30 frames (~250ms at 120Hz), level must decay smoothly down to near 0
        for (frame in 1..30) {
            smoothedLevel = Dictation.computeAmplitudeStep(smoothedLevel, targetLevel)
        }

        // Must decay below 0.015f without freezing at 0.85f
        assertTrue("Visualizer must decay to near zero, was $smoothedLevel", smoothedLevel < 0.015f)
    }

    @Test
    fun testFastAttackResponseToVoice() {
        // Simulates fast attack when vocal plosive occurs
        var smoothedLevel = 0.05f
        val targetLevel = 0.90f

        // After just 3 frames (~25ms), smoothedLevel should respond sharply
        for (frame in 1..3) {
            smoothedLevel = Dictation.computeAmplitudeStep(smoothedLevel, targetLevel)
        }

        assertTrue("Visualizer must quickly attack upwards, was $smoothedLevel", smoothedLevel > 0.70f)
    }

    @Test
    fun testOnBackPressedDispatcherIntegration() {
        var fallbackInvoked = false
        val dispatcher = OnBackPressedDispatcher(Runnable {
            fallbackInvoked = true
        })

        // Initially no enabled callbacks
        assertFalse(dispatcher.hasEnabledCallbacks())

        var customCallbackInvoked = false
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                customCallbackInvoked = true
            }
        }

        dispatcher.addCallback(callback)
        assertTrue(dispatcher.hasEnabledCallbacks())

        dispatcher.onBackPressed()
        assertTrue(customCallbackInvoked)
        assertFalse(fallbackInvoked)

        // Disable callback -> fallback should be invoked
        callback.isEnabled = false
        assertFalse(dispatcher.hasEnabledCallbacks())

        dispatcher.onBackPressed()
        assertTrue(fallbackInvoked)
    }

    @Test
    fun testHannWindowEnvelopeCharacteristics() {
        // Hann window: sin^2(pi * x)
        // At edges (x=0 and x=1), envelope must be 0
        val edgeStart = Math.sin(0.0 * Math.PI).let { (it * it).toFloat() }
        val edgeEnd = Math.sin(1.0 * Math.PI).let { (it * it).toFloat() }
        assertEquals(0f, edgeStart, 0.0001f)
        assertEquals(0f, edgeEnd, 0.0001f)

        // At center (x=0.5), envelope must reach peak 1.0
        val center = Math.sin(0.5 * Math.PI).let { (it * it).toFloat() }
        assertEquals(1f, center, 0.0001f)
    }
}
