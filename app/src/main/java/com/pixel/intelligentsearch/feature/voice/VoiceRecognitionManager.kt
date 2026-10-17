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
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticType

/**
 * Voice Recognition state engine.
 */
enum class VoiceRecognitionState {
    INITIALIZING,
    LISTENING,
    PROCESSING,
    RESULT,
    ERROR
}

/**
 * Encapsulates Android SpeechRecognizer with reactive state and audio amplitude normalization.
 * Supports Android 14+ / 17 Canary on-device speech recognition model when available.
 */
class VoiceRecognitionManager(private val context: Context) {
    val state = mutableStateOf(VoiceRecognitionState.INITIALIZING)
    val amplitude = mutableFloatStateOf(0f)
    val recognizedText = mutableStateOf("")
    val errorMessage = mutableStateOf<String?>(null)

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening() {
        mainHandler.post {
            executeStartListening()
        }
    }

    private fun executeStartListening() {
        if (!checkAudioPermission()) {
            state.value = VoiceRecognitionState.ERROR
            errorMessage.value = "Microphone permission required for voice search."
            PixelHapticEngine.get(context).performHaptic(PixelHapticType.REJECT)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            state.value = VoiceRecognitionState.ERROR
            errorMessage.value = "Speech recognition service unavailable on device."
            PixelHapticEngine.get(context).performHaptic(PixelHapticType.REJECT)
            return
        }

        cleanupRecognizer()

        try {
            state.value = VoiceRecognitionState.INITIALIZING
            errorMessage.value = null
            recognizedText.value = ""
            amplitude.floatValue = 0f

            // Leverage on-device recognition if supported (API 33+, e.g. Pixel Tensor) with resilient fallback
            val recognizer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Throwable) {
                Log.w("VoiceRecManager", "Failed to initialize on-device recognizer, falling back", e)
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    state.value = VoiceRecognitionState.LISTENING
                }

                override fun onBeginningOfSpeech() {
                    state.value = VoiceRecognitionState.LISTENING
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = normalizeRmsDb(rmsdB)
                    val current = amplitude.floatValue
                    amplitude.floatValue = computeAmplitudeStep(current, normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    state.value = VoiceRecognitionState.PROCESSING
                    amplitude.floatValue = 0f
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap mic to retry."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap mic to speak."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection issue. Tap mic to retry."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                        SpeechRecognizer.ERROR_CLIENT -> "Speech engine busy. Tap mic to retry."
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Voice service disconnected. Tap mic to retry."
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Voice service busy. Please wait a moment."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language not supported by voice recognizer."
                        else -> "Unable to recognize voice. Tap mic to retry."
                    }
                    errorMessage.value = msg
                    state.value = VoiceRecognitionState.ERROR
                    amplitude.floatValue = 0f
                    PixelHapticEngine.get(context).performHaptic(PixelHapticType.REJECT)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull().orEmpty()
                    if (best.isNotBlank()) {
                        recognizedText.value = best
                        state.value = VoiceRecognitionState.RESULT
                        PixelHapticEngine.get(context).performHaptic(PixelHapticType.CONFIRM)
                    } else {
                        state.value = VoiceRecognitionState.ERROR
                        errorMessage.value = "No words recognized. Tap mic to speak."
                    }
                    amplitude.floatValue = 0f
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        recognizedText.value = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoiceRecManager", "Error initializing speech recognizer", e)
            state.value = VoiceRecognitionState.ERROR
            errorMessage.value = "Voice recognizer error. Tap mic to retry."
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w("VoiceRecManager", "Error stopping speech recognizer", e)
            }
        }
    }

    fun toggleListening() {
        if (state.value == VoiceRecognitionState.LISTENING) {
            stopListening()
            state.value = VoiceRecognitionState.INITIALIZING
        } else {
            startListening()
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("VoiceRecManager", "Error cleaning up speech recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }

    /** Abandons any recognition in progress and releases the recognizer; startListening() creates a new one. */
    fun cancel() = destroy()

    fun destroy() {
        mainHandler.post {
            cleanupRecognizer()
            amplitude.floatValue = 0f
        }
    }

    companion object {
        fun normalizeRmsDb(rmsdB: Float): Float {
            val clamped = rmsdB.coerceIn(-2f, 10f)
            return ((clamped + 2f) / 12f).coerceIn(0f, 1f)
        }

        fun computeAmplitudeStep(
            current: Float,
            target: Float,
            attack: Float = 0.40f,
            decay: Float = 0.14f
        ): Float {
            val speed = if (target > current) attack else decay
            return current + (target - current) * speed
        }
    }
}
