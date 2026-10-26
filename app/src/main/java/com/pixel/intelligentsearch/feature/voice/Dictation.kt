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
    /** Set once the on-device engine has dropped the connection twice; the default engine is used from then on. */
    private var preferDefaultEngine = false
    /** True while a recognizer session is open and may still produce results. */
    private var live = false
    private val finishTimeout = Runnable { deliver() }
    private val maxLength = Runnable { finish() }
    private val restart = Runnable { if (phase.value == Phase.Starting || phase.value == Phase.Listening) listen() }

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
        preferDefaultEngine = false
        startedAt = System.currentTimeMillis()
        phase.value = Phase.Starting
        main.postDelayed(maxLength, MAX_LENGTH_MS)
        listen()
    }

    /** The user is done (Rambler's check button): stop, collect the last words, deliver. */
    fun finish() {
        if (phase.value == Phase.Idle || phase.value == Phase.Finishing) return
        phase.value = Phase.Finishing
        // Between sessions (or before the engine is ready) there are no last words to wait for.
        if (!live) { deliver(); return }
        runCatching { recognizer?.stopListening() }
        main.postDelayed(finishTimeout, FINISH_WAIT_MS)
    }

    /** Throws away everything heard (Rambler's cancel button). */
    fun cancel() {
        main.removeCallbacks(finishTimeout)
        main.removeCallbacks(maxLength)
        main.removeCallbacks(restart)
        onFinished = null
        onProblem = null
        release()
        live = false
        phase.value = Phase.Idle
        amplitude.floatValue = 0f
    }

    private fun listen() {
        release()
        live = false
        val r = try {
            if (!preferDefaultEngine && Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
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
            // Ask for long silences before the engine gives up; engines that ignore this just restart. Google's
            // on-device engine reads these as Integer and drops the segmented session if they aren't.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000)
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
        override fun onReadyForSpeech(params: Bundle?) {
            live = true
            if (phase.value == Phase.Starting) phase.value = Phase.Listening
        }
        override fun onBeginningOfSpeech() { if (phase.value == Phase.Starting) phase.value = Phase.Listening }
        override fun onRmsChanged(rmsdB: Float) {
            amplitude.floatValue = computeAmplitudeStep(amplitude.floatValue, normalizeRmsDb(rmsdB))
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
        override fun onEndOfSegmentedSession() {
            live = false
            sessionEnded()
        }
        override fun onResults(results: Bundle?) {
            live = false
            best(results)?.let { heard += it }
            pending = ""
            failures = 0
            sessionEnded()
        }
        override fun onError(error: Int) {
            live = false
            Log.i(TAG, "Recognizer error $error in phase ${phase.value}, failures=$failures")
            // The user has already tapped the check button: whatever the error, the words so far are theirs.
            if (phase.value == Phase.Finishing) { deliver(); return }
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Allow the microphone to search by voice.")
                SpeechRecognizer.ERROR_AUDIO -> fail("The microphone isn't available right now.")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    fail("Your language isn't supported by the speech recognizer.")
                else -> {
                    // Silence, no match, a busy or reconnecting engine: keep the words so far and keep listening.
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) failures++
                    // An on-device engine that keeps dropping the connection (seen right after an app update) is
                    // swapped for the default engine rather than giving up.
                    if (failures >= 2) preferDefaultEngine = true
                    if (failures > 6) fail("Voice search stopped. Tap the mic to try again.") else sessionEnded()
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
            // Restarts back off a little with each failure, so a struggling engine gets time to recover. Words the
            // engine only got as far as a partial result are kept, or the next session's partials would replace them.
            else -> {
                if (pending.isNotBlank()) { heard += pending; pending = "" }
                main.removeCallbacks(restart)
                main.postDelayed(restart, RESTART_DELAY_MS * (1 + failures))
            }
        }
    }

    private fun deliver() {
        main.removeCallbacks(finishTimeout)
        main.removeCallbacks(maxLength)
        val done = onFinished ?: return
        if (pending.isNotBlank()) heard += pending
        val text = SpeechCleanup.clean(heard)
        onFinished = null
        onProblem = null
        release()
        phase.value = Phase.Idle
        amplitude.floatValue = 0f
        // Nothing said is not a problem (people open the mic just to watch the animation): it ends quietly with "".
        done(text)
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

    companion object {
        private const val TAG = "Dictation"

        /** Maps the recognizer's RMS in dB (about -2 for silence, 10 for loud speech) to 0..1. */
        fun normalizeRmsDb(rmsdB: Float): Float {
            val clamped = rmsdB.coerceIn(-2f, 10f)
            return ((clamped + 2f) / 12f).coerceIn(0f, 1f)
        }

        /** Eases the level towards [target]: quick to rise with the voice, slower to settle. */
        fun computeAmplitudeStep(current: Float, target: Float): Float {
            val speed = if (target > current) 0.40f else 0.14f
            return current + (target - current) * speed
        }
        const val FINISH_WAIT_MS = 1_800L
        const val RESTART_DELAY_MS = 120L
        /** A safety stop for a mic left open: three minutes, then finish with what was heard. */
        const val MAX_LENGTH_MS = 180_000L
    }
}
