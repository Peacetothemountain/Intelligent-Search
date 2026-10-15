package com.pixel.intelligentsearch.feature.voice

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

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
        val recognizer = createDelegate()
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

    override fun onDestroy() {
        destroyDelegate()
        super.onDestroy()
    }

    /** The on-device recognizer, else another app's recognition service; never the default one. */
    private fun createDelegate(): SpeechRecognizer? {
        try {
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "On-device recognizer unavailable", e)
        }
        val other = findOtherRecognitionService() ?: return null
        return try {
            SpeechRecognizer.createSpeechRecognizer(this, other)
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
