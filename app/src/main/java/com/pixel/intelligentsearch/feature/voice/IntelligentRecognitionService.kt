package com.pixel.intelligentsearch.feature.voice

import android.content.AttributionSource
import android.content.ComponentName
import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi

private const val TAG = "IntelligentRecognition"

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

    private var delegate: SpeechRecognizer? = null
    /** The recognizer a model download was asked of; it binds asynchronously, so it lives until the service ends. */
    private var downloadDelegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        destroyDelegate()
        val recognizer = createDelegate(attributedContext(listener.callingAttributionSource))
        if (recognizer == null) {
            safeError(listener, SpeechRecognizer.ERROR_CLIENT)
            return
        }
        delegate = recognizer
        recognizer.setRecognitionListener(RecognitionRelay(CallbackSink(listener)))
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
        runCatching { downloadDelegate?.destroy() }
        val recognizer = createDelegate(this) ?: return
        downloadDelegate = recognizer
        try {
            // Destroying here would cancel the request before the engine has even connected; onDestroy cleans up.
            recognizer.triggerModelDownload(recognizerIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Model download request failed", e)
        }
    }

    override fun onDestroy() {
        runCatching { downloadDelegate?.destroy() }
        downloadDelegate = null
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
        return chooseRecognitionDelegate(services.mapNotNull { it.serviceInfo }, packageName)
    }

    private fun safeError(listener: Callback, error: Int) = relay { listener.error(error) }

    private fun destroyDelegate() {
        try {
            delegate?.destroy()
        } catch (_: Exception) {}
        delegate = null
    }
}

/** Google's recognizers, most preferred first. They count only when they came with the phone (see [isSystem]). */
private val GOOGLE_RECOGNIZERS = listOf(
    "com.google.android.googlequicksearchbox",
    "com.google.android.tts",
    "com.google.android.as",
)

/**
 * The recognition service to hand the microphone to, out of [services] (every one installed), never [ownPackage]'s.
 *
 * Any app can declare a recognition service, under any package name, including one starting with "com.google.", so
 * the choice rests on what came with the phone: Google's own recognizer if it is part of the system, then any other
 * system recognizer. An app the user installed is used only when the phone has no system recognizer at all, which
 * keeps voice working on phones without Google apps and matches how Android picks its own default recognizer.
 */
internal fun chooseRecognitionDelegate(services: List<ServiceInfo>, ownPackage: String): ComponentName? {
    val candidates = services.filter { it.packageName != ownPackage }
    val system = candidates.filter { it.isSystem() }
    val chosen = GOOGLE_RECOGNIZERS.firstNotNullOfOrNull { google -> system.firstOrNull { it.packageName == google } }
        ?: system.firstOrNull()
        ?: candidates.firstOrNull()
    return chosen?.let { ComponentName(it.packageName, it.name) }
}

/** Part of the system image, including a system app updated from the Play Store. */
private fun ServiceInfo.isSystem(): Boolean =
    ((applicationInfo?.flags ?: 0) and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

/**
 * Where a [RecognitionRelay] delivers what the delegate recognizer reports. In the service this is the calling app's
 * [RecognitionService.Callback] (see [CallbackSink]), which can't be created outside the framework.
 */
internal interface RecognitionSink {
    fun readyForSpeech(params: Bundle)
    fun beginningOfSpeech()
    fun rmsChanged(rmsdB: Float)
    fun bufferReceived(buffer: ByteArray)
    fun endOfSpeech()
    fun error(error: Int)
    fun results(results: Bundle)
    fun partialResults(partialResults: Bundle)
    fun segmentResults(segmentResults: Bundle)
    fun endOfSegmentedSession()
    fun languageDetection(results: Bundle)
}

/**
 * Hands each callback of the delegate recognizer on to the app that asked for speech. A segmented session (dictation
 * that keeps listening through pauses, Android 13+) reports every phrase through onSegmentResults and ends with
 * onEndOfSegmentedSession instead of onResults, so those are passed on too: without them the caller lost everything
 * said before the last pause.
 */
internal class RecognitionRelay(private val sink: RecognitionSink) : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = relay { sink.readyForSpeech(params ?: Bundle()) }
    override fun onBeginningOfSpeech() = relay { sink.beginningOfSpeech() }
    override fun onRmsChanged(rmsdB: Float) = relay { sink.rmsChanged(rmsdB) }
    override fun onBufferReceived(buffer: ByteArray?) = relay { buffer?.let { sink.bufferReceived(it) } }
    override fun onEndOfSpeech() = relay { sink.endOfSpeech() }
    override fun onError(error: Int) = relay { sink.error(error) }
    override fun onResults(results: Bundle?) = relay { sink.results(results ?: Bundle()) }
    override fun onPartialResults(partialResults: Bundle?) = relay { partialResults?.let { sink.partialResults(it) } }
    override fun onSegmentResults(segmentResults: Bundle) = relay { sink.segmentResults(segmentResults) }
    override fun onEndOfSegmentedSession() = relay { sink.endOfSegmentedSession() }
    override fun onLanguageDetection(results: Bundle) = relay { sink.languageDetection(results) }
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

/**
 * The calling app's callback. The delegate only reports segments and languages on Android versions that have them,
 * where the callback has the matching methods; the version checks just say so.
 */
private class CallbackSink(private val callback: RecognitionService.Callback) : RecognitionSink {
    override fun readyForSpeech(params: Bundle) = callback.readyForSpeech(params)
    override fun beginningOfSpeech() = callback.beginningOfSpeech()
    override fun rmsChanged(rmsdB: Float) = callback.rmsChanged(rmsdB)
    override fun bufferReceived(buffer: ByteArray) = callback.bufferReceived(buffer)
    override fun endOfSpeech() = callback.endOfSpeech()
    override fun error(error: Int) = callback.error(error)
    override fun results(results: Bundle) = callback.results(results)
    override fun partialResults(partialResults: Bundle) = callback.partialResults(partialResults)

    override fun segmentResults(segmentResults: Bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) callback.segmentResults(segmentResults)
    }

    override fun endOfSegmentedSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) callback.endOfSegmentedSession()
    }

    override fun languageDetection(results: Bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) callback.languageDetection(results)
    }
}

/** Delivers one callback to the calling app. */
private inline fun relay(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        // The client went away (RemoteException); nothing left to deliver to.
        Log.d(TAG, "Recognition callback dropped: ${e.message}")
    }
}
