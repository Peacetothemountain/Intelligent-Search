package com.pixel.intelligentsearch.feature.voice

import org.junit.Assert.assertEquals
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
}
