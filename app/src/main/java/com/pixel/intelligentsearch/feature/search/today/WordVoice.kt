package com.pixel.intelligentsearch.feature.search.today

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Says the word of the day in one voice, Google's en-us-x-iob: its online form, richer, and the same voice on the
 * phone when the online one can't answer. The word is recorded once into the cache and played from there, and the
 * recording is measured as it plays (loudness, low, middle and high sound, and where syllables start) so the voice
 * nebula moves with it. Nothing needs the microphone or a permission.
 */
internal class WordVoice(private val app: Context) {
    /** True from the tap until the word has been said. */
    var speaking by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var word = ""
    private var triedOnPhone = false
    private var player: MediaPlayer? = null
    private var envelope: SpeechEnvelope? = null
    private var released = false

    fun say(word: String) {
        if (released || word.isBlank()) return
        speaking = true
        val engine = tts
        when {
            engine == null -> {
                pending = word
                start()
            }
            !ready -> pending = word
            else -> record(engine, word)
        }
    }

    /**
     * Fills [out] with how the voice sounds right now: loudness, low, middle, high and a syllable's start, each 0 to 1.
     * Without a measured recording (the engine spoke it directly) it gives a speaking-like motion from [seconds].
     */
    fun sample(out: FloatArray, seconds: Float) {
        if (!speaking) {
            out.fill(0f)
            return
        }
        val p = player
        val e = envelope
        if (p == null || e == null) {
            val l = 0.5f + 0.5f * abs(sin(seconds * 9.3f) * sin(seconds * 3.1f))
            out[0] = l; out[1] = l; out[2] = 0.6f * l; out[3] = 0.4f * l; out[4] = sin(seconds * 6f).coerceAtLeast(0f).pow(6)
            return
        }
        e.at(runCatching { p.currentPosition }.getOrDefault(0), out)
    }

    fun release() {
        released = true
        pending = null
        player?.release()
        player = null
        tts?.shutdown()
        tts = null
        speaking = false
    }

    private fun start() {
        // A start can call back inside the constructor; posting waits until `tts` holds the engine it belongs to.
        var made: TextToSpeech? = null
        made = TextToSpeech(app, { status -> main.post { onReady(made, status) } }, GOOGLE_ENGINE)
        tts = made
    }

    private fun onReady(engine: TextToSpeech?, status: Int) {
        if (released || engine == null || engine !== tts) return
        if (status != TextToSpeech.SUCCESS) {
            engine.shutdown()
            tts = null
            pending = null
            speaking = false
            return
        }
        ready = true
        engine.setOnUtteranceProgressListener(listener)
        choose(engine, VOICE_ONLINE)
        pending?.let {
            pending = null
            record(engine, it)
        }
    }

    private fun choose(engine: TextToSpeech, name: String) {
        val voice = runCatching { engine.voices }.getOrNull()
            ?.firstOrNull { it.name == name && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        if (voice != null) engine.voice = voice else engine.language = Locale.US
    }

    private fun cacheFile(word: String) = File(File(app.cacheDir, CACHE_DIR), "iob_${word.lowercase(Locale.ROOT).hashCode()}.wav")

    private fun record(engine: TextToSpeech, word: String) {
        this.word = word
        val file = cacheFile(word)
        if (file.length() > WAV_HEADER_BYTES) {
            play(file)
            return
        }
        // One word a day: the recording of an earlier word goes.
        file.parentFile?.let { dir ->
            dir.mkdirs()
            dir.listFiles()?.forEach { if (it != file) it.delete() }
        }
        if (engine.synthesizeToFile(word, Bundle(), file, RECORDING) != TextToSpeech.SUCCESS) speakDirectly(engine, word)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            main.post { if (utteranceId == RECORDING) play(cacheFile(word)) else speaking = false }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            main.post { failed(utteranceId) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { failed(utteranceId) }
        }
    }

    /** The online voice couldn't answer: the same voice on the phone, then the engine speaking it directly. */
    private fun failed(utteranceId: String?) {
        val engine = tts ?: return
        if (utteranceId != RECORDING) {
            speaking = false
            return
        }
        cacheFile(word).delete()
        if (!triedOnPhone) {
            triedOnPhone = true
            choose(engine, VOICE_ON_PHONE)
            record(engine, word)
        } else {
            speakDirectly(engine, word)
        }
    }

    private fun speakDirectly(engine: TextToSpeech, word: String) {
        envelope = null
        if (engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, DIRECT) != TextToSpeech.SUCCESS) speaking = false
    }

    private fun play(file: File) {
        if (released) return
        envelope = runCatching { SpeechEnvelope.read(file) }.getOrNull()
        player?.release()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.path)
                setOnCompletionListener { finish() }
                setOnErrorListener { _, _, _ ->
                    finish()
                    true
                }
                prepare()
                start()
            }
        }.getOrElse {
            file.delete()
            tts?.let { speakDirectly(it, word) } ?: run { speaking = false }
            null
        }
    }

    private fun finish() {
        speaking = false
        player?.release()
        player = null
    }

    private companion object {
        const val GOOGLE_ENGINE = "com.google.android.tts"
        const val VOICE_ONLINE = "en-us-x-iob-network"
        const val VOICE_ON_PHONE = "en-us-x-iob-local"
        const val CACHE_DIR = "word_voice"
        const val WAV_HEADER_BYTES = 44L
        const val RECORDING = "word-recording"
        const val DIRECT = "word-direct"
    }
}

/**
 * A recording measured every [FRAME_MS] ms: loudness, low (under 500 Hz), middle and high (over 2 kHz) sound, each
 * against the recording's loudest, and how sharply sound rose there (a syllable's start).
 */
internal class SpeechEnvelope private constructor(private val frames: FloatArray, private val count: Int) {
    fun at(ms: Int, out: FloatArray) {
        val i = (ms / FRAME_MS).coerceIn(0, count - 1)
        System.arraycopy(frames, i * VALUES, out, 0, VALUES)
    }

    companion object {
        const val VALUES = 5
        private const val FRAME_MS = 10

        /** Reads a 16-bit PCM WAV, or returns null for anything else. */
        fun read(file: File): SpeechEnvelope? {
            val b = file.readBytes()
            if (b.size < 44 || String(b, 0, 4, Charsets.US_ASCII) != "RIFF" || String(b, 8, 4, Charsets.US_ASCII) != "WAVE") return null
            fun u16(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
            fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
            var channels = 0
            var rate = 0
            var bits = 0
            var dataAt = -1
            var dataLen = 0
            var o = 12
            while (o + 8 <= b.size) {
                val id = String(b, o, 4, Charsets.US_ASCII)
                val len = u32(o + 4)
                if (id == "fmt " && o + 24 <= b.size) {
                    channels = u16(o + 10)
                    rate = u32(o + 12)
                    bits = u16(o + 22)
                } else if (id == "data") {
                    dataAt = o + 8
                    dataLen = min(len, b.size - dataAt)
                    break
                }
                o += 8 + len + (len and 1)
            }
            if (bits != 16 || channels < 1 || rate <= 0 || dataAt < 0) return null
            val perFrame = rate * FRAME_MS / 1000
            val samples = dataLen / (2 * channels)
            val count = samples / perFrame
            if (count < 1) return null
            val raw = FloatArray(count * 4)
            val aLow = 1f - exp(-2f * PI.toFloat() * 500f / rate)
            val aHigh = 1f - exp(-2f * PI.toFloat() * 2000f / rate)
            var low = 0f
            var mid = 0f
            for (f in 0 until count) {
                var sAll = 0f
                var sLow = 0f
                var sMid = 0f
                var sHigh = 0f
                for (k in 0 until perFrame) {
                    val at = dataAt + ((f * perFrame + k) * channels) * 2
                    val x = (u16(at).toShort()) / 32768f
                    low += aLow * (x - low)
                    mid += aHigh * (x - mid)
                    val l = low
                    val m = mid - low
                    val h = x - mid
                    sAll += x * x; sLow += l * l; sMid += m * m; sHigh += h * h
                }
                raw[f * 4] = sqrt(sAll / perFrame)
                raw[f * 4 + 1] = sqrt(sLow / perFrame)
                raw[f * 4 + 2] = sqrt(sMid / perFrame)
                raw[f * 4 + 3] = sqrt(sHigh / perFrame)
            }
            val peak = FloatArray(4)
            for (f in 0 until count) for (c in 0 until 4) peak[c] = max(peak[c], raw[f * 4 + c])
            val frames = FloatArray(count * VALUES)
            val prev = FloatArray(3)
            for (f in 0 until count) {
                frames[f * VALUES] = (raw[f * 4] / max(peak[0], 1e-4f)).pow(0.8f)
                var rise = 0f
                for (c in 1..3) {
                    val v = (raw[f * 4 + c] / max(peak[c], 1e-4f)).pow(0.8f)
                    frames[f * VALUES + c] = v
                    rise += max(0f, v - prev[c - 1])
                    prev[c - 1] = v
                }
                frames[f * VALUES + 4] = min(1f, rise * 3f)
            }
            return SpeechEnvelope(frames, count)
        }
    }
}
