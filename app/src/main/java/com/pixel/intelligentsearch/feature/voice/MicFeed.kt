package com.pixel.intelligentsearch.feature.voice

import android.Manifest
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The microphone, recorded by the app itself and handed to the speech engine as its audio
 * ([RecognizerIntent.EXTRA_AUDIO_SOURCE], Android 13+).
 *
 * Google's speech engine plays its start and stop tones when it opens and closes the microphone itself
 * (Speech Services by Google logs "Playing beep com.google.android.tts:raw/open"). Given the audio, it never opens the
 * microphone, so no tone ever plays. Muting a channel around the tones can't do that: they play from Google's process,
 * on whatever channel it picks.
 *
 * An engine that doesn't take audio this way opens the microphone as before. The level ([onLevel], about -2 in silence
 * to 10 speaking up, as Google's engine reports it) comes from the app's own recording, so the animation follows the
 * voice whichever engine listens.
 */
internal class MicFeed private constructor(
    private val record: AudioRecord,
    private val source: ParcelFileDescriptor,
    private val sink: ParcelFileDescriptor,
    private val main: Handler,
    private val onLevel: (Float) -> Unit,
) {
    @Volatile private var running = true
    private var pumping = false
    private var stopped = false

    private val pump = Thread({ pump() }, "MicFeed")

    private fun start(): Boolean {
        return try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                false
            } else {
                pump.start()
                pumping = true
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start recording", e)
            false
        }
    }

    /**
     * Adds the audio to [intent]: the engine reads it instead of opening the microphone. With [wholeSession], one
     * segmented session runs for as long as the audio does, reporting each phrase as it is heard.
     */
    fun into(intent: Intent, wholeSession: Boolean = false): Intent {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return intent
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, ENCODING)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
        if (wholeSession) intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        return intent
    }

    /** Stops recording and ends the audio; the engine sees it end as the microphone closing. Safe to call twice. */
    fun stop() {
        main.removeCallbacksAndMessages(this)
        if (stopped) return
        stopped = true
        running = false
        // Stopping wakes the recording thread, which then lets go of the recording and the write end.
        runCatching { record.stop() }
        if (!pumping) finish()
        // The read end is the app's to close: the engine got its own copy through the binder.
        runCatching { source.close() }
    }

    /** Closing the write end tells the engine the audio is over. */
    private fun finish() {
        runCatching { record.release() }
        runCatching { sink.close() }
    }

    private fun pump() {
        val buffer = ShortArray(CHUNK)
        val bytes = ByteArray(CHUNK * 2)
        val fd = sink.fileDescriptor
        while (running) {
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) {
                if (read == 0) continue else break
            }
            var sum = 0.0
            for (i in 0 until read) {
                val s = buffer[i].toInt()
                sum += (s * s).toDouble()
                bytes[2 * i] = (s and 0xFF).toByte()
                bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
            }
            val level = levelOf(sqrt(sum / read))
            main.postAtTime({ if (running) onLevel(level) }, this, android.os.SystemClock.uptimeMillis())
            var off = 0
            val length = read * 2
            while (running && off < length) {
                try {
                    off += Os.write(fd, bytes, off, length - off)
                } catch (e: ErrnoException) {
                    // An engine that stopped reading (it has the words) or never read (it opened the microphone
                    // itself): drop the audio rather than wait for it.
                    if (e.errno == OsConstants.EAGAIN) break
                    running = false
                } catch (e: Exception) {
                    running = false
                }
            }
        }
        finish()
    }

    companion object {
        private const val TAG = "MicFeed"
        private const val RATE = 16_000
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        /** 40 ms of audio: how often the level moves. */
        private const val CHUNK = RATE / 25

        /**
         * Starts recording for an engine to listen to, or null when this Android can't hand an engine audio or the
         * microphone can't be opened (the engine then opens it itself). [onLevel] runs on [main].
         */
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        fun start(main: Handler, onLevel: (Float) -> Unit): MicFeed? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            var record: AudioRecord? = null
            var pipe: Array<ParcelFileDescriptor>? = null
            return try {
                val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING)
                if (min <= 0) return null
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING,
                    maxOf(min, CHUNK * 2 * 4)
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    return null
                }
                pipe = ParcelFileDescriptor.createPipe()
                // The engine reads at its own pace and stops reading once it has the words; writing never waits.
                Os.fcntlInt(pipe[1].fileDescriptor, OsConstants.F_SETFL, OsConstants.O_NONBLOCK)
                val feed = MicFeed(record, pipe[0], pipe[1], main, onLevel)
                if (feed.start()) feed else {
                    feed.stop()
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not record for the speech engine", e)
                runCatching { record?.release() }
                pipe?.forEach { runCatching { it.close() } }
                null
            }
        }

        /** The loudness of 16-bit audio with root mean square [rms], on the scale Google's engine reports. */
        fun levelOf(rms: Double): Float {
            if (rms < 1.0) return -2f
            // About -50 dBFS in a quiet room to -15 dBFS speaking up close.
            val dbfs = 20.0 * log10(rms / 32_768.0)
            return ((dbfs + 50.0) * 12.0 / 35.0 - 2.0).toFloat().coerceIn(-2f, 10f)
        }
    }
}
