package com.pixel.intelligentsearch.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Says an answer aloud with the phone's own text to speech, after a voice search. The engine is started on the first
 * answer, not before, and is let go with [shutdown]. Nothing is said while the phone is on silent or vibrate.
 *
 * Main thread only: the engine calls back there.
 */
class AnswerSpeaker(context: Context) {
    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    private var ready = false
    private var waiting: String? = null

    /** Says [text], in place of anything still being said. */
    fun speak(text: String) {
        if (text.isBlank() || !mayMakeSound()) return
        val tts = engine
        if (tts != null && ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
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
    }

    fun shutdown() {
        waiting = null
        ready = false
        engine?.shutdown()
        engine = null
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
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            ready = true
            waiting?.let { tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE) }
            waiting = null
        }
        if (failed) made.shutdown() else engine = made
    }

    private companion object {
        const val UTTERANCE = "answer"
    }
}
