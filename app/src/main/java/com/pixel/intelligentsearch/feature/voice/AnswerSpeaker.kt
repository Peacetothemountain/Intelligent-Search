package com.pixel.intelligentsearch.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Says an answer aloud with the phone's own text to speech, after a voice search. The engine is started on the first
 * answer, not before, and is let go with [shutdown]. Nothing is said while the phone is on silent or vibrate.
 *
 * Main thread only: the engine calls back there.
 */
class AnswerSpeaker(context: Context) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    // Music playing turns down while an answer is said, and back up after.
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private var engine: TextToSpeech? = null
    private var ready = false
    private var waiting: String? = null
    // Each answer is its own utterance, so the end of one cut short doesn't hand the music back during the next.
    private var said = 0
    @Volatile private var current = ""

    /** Says [text], in place of anything still being said. */
    fun speak(text: String) {
        if (text.isBlank() || !mayMakeSound()) return
        val tts = engine
        if (tts != null && ready) {
            say(tts, text)
            return
        }
        // Said as soon as the engine is up.
        waiting = text
        if (tts == null) start()
    }

    /** Stops what is being said, and drops what was waiting to be. */
    fun stop() {
        waiting = null
        if (ready) engine?.stop()
        audio?.abandonAudioFocusRequest(focus)
    }

    fun shutdown() {
        waiting = null
        ready = false
        engine?.shutdown()
        engine = null
        audio?.abandonAudioFocusRequest(focus)
    }

    private fun say(tts: TextToSpeech, text: String) {
        current = "$UTTERANCE-${++said}"
        audio?.requestAudioFocus(focus)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, current)
    }

    private fun finished(utteranceId: String?) {
        if (utteranceId == current) audio?.abandonAudioFocusRequest(focus)
    }

    /** A phone on silent or vibrate has been asked to keep quiet. */
    private fun mayMakeSound(): Boolean =
        appContext.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_NORMAL

    private fun start() {
        // A failed start can call back inside the constructor, before `made` is set: keep only an engine that started.
        var made: TextToSpeech? = null
        var failed = false
        made = TextToSpeech(appContext) { status ->
            val tts = made
            if (status != TextToSpeech.SUCCESS) {
                failed = true
                waiting = null
                if (tts != null) {
                    tts.shutdown()
                    if (engine === tts) engine = null
                }
                return@TextToSpeech
            }
            // Shut down while it was starting.
            if (tts == null || engine !== tts) return@TextToSpeech
            // The answers are written in English.
            tts.language = Locale.getDefault().takeIf { it.language == "en" } ?: Locale.US
            tts.setAudioAttributes(attributes)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = finished(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished(utteranceId)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
            })
            ready = true
            waiting?.let { say(tts, it) }
            waiting = null
        }
        if (failed) made.shutdown() else engine = made
    }

    private companion object {
        const val UTTERANCE = "answer"
    }
}
