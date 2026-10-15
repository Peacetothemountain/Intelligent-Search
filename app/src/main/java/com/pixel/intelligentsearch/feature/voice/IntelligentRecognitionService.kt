package com.pixel.intelligentsearch.feature.voice

import android.content.AttributionSource
import android.content.ComponentName
import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * The speech recognizer named in voice_interaction.xml.
 *
 * When Intelligent Search becomes the default assistant, Android may make the assistant's declared
 * recognitionService the device's default speech recognizer, so that component has to be a real
 * [RecognitionService]. This one does no recognition itself: it forwards each request to the device's
 * on-device recognizer, or else to another installed recognition service, and relays the callbacks.
 * It never forwards to the default recognizer, which may be this service.
 */
class IntelligentRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "IntelligentRecognition"
    }

    private var delegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        destroyDelegate()
        val recognizer = createDelegate(attributedContext(listener.callingAttributionSource))
        if (recognizer == null) {
            safeError(listener, SpeechRecognizer.ERROR_CLIENT)
            return
        }
        delegate = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = relay { listener.readyForSpeech(params ?: Bundle()) }
            override fun onBeginningOfSpeech() = relay { listener.beginningOfSpeech() }
            override fun onRmsChanged(rmsdB: Float) = relay { listener.rmsChanged(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) = relay { buffer?.let { listener.bufferReceived(it) } }
            override fun onEndOfSpeech() = relay { listener.endOfSpeech() }
            override fun onError(error: Int) = relay { listener.error(error) }
            override fun onResults(results: Bundle?) = relay { listener.results(results ?: Bundle()) }
            override fun onPartialResults(partialResults: Bundle?) = relay { partialResults?.let { listener.partialResults(it) } }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        try {
            recognizer.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Delegate recognizer failed to start", e)
            safeError(listener, SpeechRecognizer.ERROR_CLIENT)
            destroyDelegate()
        }
    }

    override fun onStopListening(listener: Callback) {
        try {
            delegate?.stopListening()
        } catch (_: Exception) {}
    }

    override fun onCancel(listener: Callback) {
        try {
            delegate?.cancel()
        } catch (_: Exception) {}
        destroyDelegate()
    }

    /** Lets apps ask whether a language is supported, answered by the same delegate recognizer. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCheckRecognitionSupport(recognizerIntent: Intent, supportCallback: SupportCallback) {
        val recognizer = createDelegate(this)
        if (recognizer == null) {
            relay { supportCallback.onError(SpeechRecognizer.ERROR_CLIENT) }
            return
        }
        recognizer.checkRecognitionSupport(recognizerIntent, mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                relay { supportCallback.onSupportResult(recognitionSupport) }
                recognizer.destroy()
            }

            override fun onError(error: Int) {
                relay { supportCallback.onError(error) }
                recognizer.destroy()
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onTriggerModelDownload(recognizerIntent: Intent) {
        val recognizer = createDelegate(this) ?: return
        try {
            recognizer.triggerModelDownload(recognizerIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Model download request failed", e)
        } finally {
            recognizer.destroy()
        }
    }

    override fun onDestroy() {
        destroyDelegate()
        super.onDestroy()
    }

    /**
     * A context that names the calling app as the next attribution source, so microphone access and the
     * privacy indicator are attributed to the app that asked for speech, not only to Intelligent Search.
     */
    private fun attributedContext(caller: AttributionSource): Context = try {
        createContext(ContextParams.Builder().setNextAttributionSource(caller).build())
    } catch (e: Exception) {
        Log.w(TAG, "Could not attribute recognition to the caller", e)
        this
    }

    /** The on-device recognizer, else another app's recognition service; never the default one. */
    private fun createDelegate(context: Context): SpeechRecognizer? {
        try {
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "On-device recognizer unavailable", e)
        }
        val other = findOtherRecognitionService() ?: return null
        return try {
            SpeechRecognizer.createSpeechRecognizer(context, other)
        } catch (e: Exception) {
            Log.w(TAG, "Could not bind recognition service $other", e)
            null
        }
    }

    private fun findOtherRecognitionService(): ComponentName? {
        val intent = Intent(SERVICE_INTERFACE)
        val services = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, 0)
        }
        // Prefer Google's recognizer when present, otherwise the first other provider.
        val candidates = services.mapNotNull { it.serviceInfo }.filter { it.packageName != packageName }
        val chosen = candidates.firstOrNull { it.packageName.startsWith("com.google.") } ?: candidates.firstOrNull()
        return chosen?.let { ComponentName(it.packageName, it.name) }
    }

    private inline fun relay(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // The client went away (RemoteException); nothing left to deliver to.
            Log.d(TAG, "Recognition callback dropped: ${e.message}")
        }
    }

    private fun safeError(listener: Callback, error: Int) = relay { listener.error(error) }

    private fun destroyDelegate() {
        try {
            delegate?.destroy()
        } catch (_: Exception) {}
        delegate = null
    }
}
