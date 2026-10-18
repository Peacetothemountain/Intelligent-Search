package com.pixel.intelligentsearch.feature.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

/**
 * Dictation that listens until the user says they're done, like Gboard's Rambler: pauses don't end it. Android's
 * speech recognizer is built for single phrases, so on Android 13+ a segmented session keeps one recognizer listening
 * through pauses, and when an engine ends its session anyway (or on older Android) listening restarts quietly. Every
 * phrase heard is kept; [finish] stops, waits briefly for the last words and hands back the whole thing tidied by
 * [SpeechCleanup]. The on-device engine is used when the phone has one (Google's on a Pixel), so speech stays on the
 * phone; Intelligent Search records and stores nothing.
 */
class Dictation(private val context: Context) {
    enum class Phase { Idle, Starting, Listening, Finishing }

    val phase = mutableStateOf(Phase.Idle)
    /** Voice level, 0 to 1, smoothed; drives the listening animation. */
    val amplitude = mutableFloatStateOf(0f)

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val heard = mutableListOf<String>()
    private var pending = ""
    private var onFinished: ((String) -> Unit)? = null
    private var onProblem: ((String) -> Unit)? = null
    private var startedAt = 0L
    private var failures = 0
    private val finishTimeout = Runnable { deliver() }
    private val maxLength = Runnable { finish() }

    fun start(onFinished: (String) -> Unit, onProblem: (String) -> Unit) {
        cancel()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onProblem("Allow the microphone to search by voice."); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onProblem("Speech recognition service unavailable on device."); return
        }
        this.onFinished = onFinished
        this.onProblem = onProblem
        heard.clear()
        pending = ""
        failures = 0
        startedAt = System.currentTimeMillis()
        phase.value = Phase.Starting
        main.postDelayed(maxLength, MAX_LENGTH_MS)
        listen()
    }

    /** The user is done (Rambler's check button): stop, collect the last words, deliver. */
    fun finish() {
        if (phase.value == Phase.Idle || phase.value == Phase.Finishing) return
        phase.value = Phase.Finishing
        runCatching { recognizer?.stopListening() }
        main.postDelayed(finishTimeout, FINISH_WAIT_MS)
    }

    /** Throws away everything heard (Rambler's cancel button). */
    fun cancel() {
        main.removeCallbacks(finishTimeout)
        main.removeCallbacks(maxLength)
        onFinished = null
        onProblem = null
        release()
        phase.value = Phase.Idle
        amplitude.floatValue = 0f
    }

    private fun listen() {
        release()
        val r = try {
            if (Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "On-device recognizer unavailable, using the default", e)
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Ask for long silences before the engine gives up; engines that ignore this just restart.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000L)
            if (Build.VERSION.SDK_INT >= 33) {
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
            }
        }
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start listening", e)
            fail("Voice recognizer error. Tap the mic to try again.")
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { if (phase.value == Phase.Starting) phase.value = Phase.Listening }
        override fun onBeginningOfSpeech() { if (phase.value == Phase.Starting) phase.value = Phase.Listening }
        override fun onRmsChanged(rmsdB: Float) {
            amplitude.floatValue = VoiceRecognitionManager.computeAmplitudeStep(amplitude.floatValue, VoiceRecognitionManager.normalizeRmsDb(rmsdB))
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {
            best(partialResults)?.let { pending = it }
        }
        override fun onSegmentResults(segmentResults: Bundle) {
            best(segmentResults)?.let { heard += it }
            pending = ""
            failures = 0
        }
        override fun onEndOfSegmentedSession() = sessionEnded()
        override fun onResults(results: Bundle?) {
            best(results)?.let { heard += it }
            pending = ""
            failures = 0
            sessionEnded()
        }
        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Allow the microphone to search by voice.")
                SpeechRecognizer.ERROR_AUDIO -> fail("The microphone isn't available right now.")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    fail("Your language isn't supported by the speech recognizer.")
                else -> {
                    if (phase.value == Phase.Finishing) { deliver(); return }
                    // Silence, no match, a busy or reconnecting engine: keep the words so far and keep listening.
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) failures++
                    if (failures > 4) fail("Voice search stopped. Tap the mic to try again.") else sessionEnded()
                }
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun sessionEnded() {
        when (phase.value) {
            Phase.Finishing -> deliver()
            Phase.Idle -> Unit
            else -> main.postDelayed({ if (phase.value == Phase.Starting || phase.value == Phase.Listening) listen() }, RESTART_DELAY_MS)
        }
    }

    private fun deliver() {
        main.removeCallbacks(finishTimeout)
        main.removeCallbacks(maxLength)
        val done = onFinished ?: return
        val problem = onProblem
        if (pending.isNotBlank()) heard += pending
        val text = SpeechCleanup.clean(heard)
        onFinished = null
        onProblem = null
        release()
        phase.value = Phase.Idle
        amplitude.floatValue = 0f
        if (text.isBlank()) problem?.invoke("No words heard. Tap the mic to try again.") else done(text)
    }

    private fun fail(message: String) {
        val problem = onProblem
        cancel()
        problem?.invoke(message)
    }

    private fun release() {
        recognizer?.let { r ->
            runCatching { r.cancel() }
            runCatching { r.destroy() }
        }
        recognizer = null
    }

    private companion object {
        const val TAG = "Dictation"
        const val FINISH_WAIT_MS = 1_800L
        const val RESTART_DELAY_MS = 120L
        /** A safety stop for a mic left open: three minutes, then finish with what was heard. */
        const val MAX_LENGTH_MS = 180_000L
    }
}
