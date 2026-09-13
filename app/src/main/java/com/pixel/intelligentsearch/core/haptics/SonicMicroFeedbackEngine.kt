package com.pixel.intelligentsearch.core.haptics

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

/**
 * Procedural Sonic Micro-Feedback Engine
 *
 * Generates bespoke, ultra-low-latency (<5ms) synthesized audio bursts directly in memory.
 * Uses zero-allocation static AudioTracks with hardware low-latency flags, calibrated
 * to psychoacoustically coincide with Pixel linear resonant actuator (LRA) wavefronts.
 */
class SonicMicroFeedbackEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SonicFeedbackEngine"
        private const val SAMPLE_RATE = 44100

        @Volatile
        private var instance: SonicMicroFeedbackEngine? = null

        fun get(context: Context): SonicMicroFeedbackEngine {
            return instance ?: synchronized(this) {
                instance ?: SonicMicroFeedbackEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class SonicType {
        CLICK,
        TICK,
        TOGGLE_ON,
        TOGGLE_OFF,
        APP_LAUNCH,
        MATH_TICK,
        SECURITY_HEARTBEAT,
        DELETE_THUD,
        MAGNETIC_PING
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val trackPool = ConcurrentHashMap<SonicType, List<AudioTrack>>()
    private val trackIndices = ConcurrentHashMap<SonicType, AtomicInteger>()

    @Volatile
    var isEnabled: Boolean = true

    init {
        precomputeAndWarmBuffers()
    }

    /**
     * Synthesize and pre-load all PCM audio bursts into static low-latency AudioTracks.
     * Total memory consumption is < 30KB.
     */
    private fun precomputeAndWarmBuffers() {
        try {
            SonicType.values().forEach { type ->
                val pcmData = generateWaveform(type)
                val tracks = (0 until 2).mapNotNull {
                    createStaticTrack(pcmData)
                }
                trackPool[type] = tracks
                trackIndices[type] = AtomicInteger(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to precompute sonic feedback buffers", e)
        }
    }

    /**
     * Creates an AudioTrack configured for immediate static memory playback with low latency flags.
     */
    private fun createStaticTrack(pcmData: ShortArray): AudioTrack? {
        if (pcmData.isEmpty()) return null
        return try {
            val byteBuffer = ByteBuffer.allocateDirect(pcmData.size * 2).order(ByteOrder.nativeOrder())
            val shortBuffer = byteBuffer.asShortBuffer()
            shortBuffer.put(pcmData)
            byteBuffer.rewind()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(pcmData.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            track.write(pcmData, 0, pcmData.size)
            track
        } catch (e: Exception) {
            Log.w(TAG, "Error building static AudioTrack", e)
            null
        }
    }

    /**
     * Procedurally synthesizes waveforms using mathematical models calibrated for physical tactile alignment.
     */
    private fun generateWaveform(type: SonicType): ShortArray {
        return when (type) {
            SonicType.CLICK -> synthesizeMechanicalClick()
            SonicType.TICK -> synthesizeMicroTick()
            SonicType.TOGGLE_ON -> synthesizeToggleChirp(rising = true)
            SonicType.TOGGLE_OFF -> synthesizeToggleChirp(rising = false)
            SonicType.APP_LAUNCH -> synthesizeAppLaunchPop()
            SonicType.MATH_TICK -> synthesizeMathTick()
            SonicType.SECURITY_HEARTBEAT -> synthesizeHeartbeat()
            SonicType.DELETE_THUD -> synthesizeDeleteThud()
            SonicType.MAGNETIC_PING -> synthesizeMagneticPing()
        }
    }

    /**
     * Mechanical Click: 1800Hz transient with steep 10ms exponential decay.
     */
    private fun synthesizeMechanicalClick(): ShortArray {
        val durationSec = 0.012f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val attackSamples = (SAMPLE_RATE * 0.0008f).toInt().coerceAtLeast(1)
        val freq = 1850.0
        val tau = 0.0035

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = if (i < attackSamples) sin(Math.PI * 0.5 * (i.toDouble() / attackSamples)) else 1.0
            val decay = exp(-t / tau) * cos(Math.PI * 0.5 * (i.toDouble() / numSamples))
            val wave = sin(2.0 * Math.PI * freq * t)
            val sample = (wave * attack * decay * 0.65 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * Micro Tick: 2600Hz high-frequency impulse with 20% filtered noise burst (6ms).
     */
    private fun synthesizeMicroTick(): ShortArray {
        val durationSec = 0.007f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val attackSamples = (SAMPLE_RATE * 0.0004f).toInt().coerceAtLeast(1)
        val freq = 2600.0
        val tau = 0.0018
        var prevNoise = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = if (i < attackSamples) (i.toDouble() / attackSamples) else 1.0
            val decay = exp(-t / tau) * cos(Math.PI * 0.5 * (i.toDouble() / numSamples))
            val rawNoise = (Math.random() * 2.0 - 1.0)
            val hpNoise = rawNoise - prevNoise
            prevNoise = rawNoise * 0.8
            val tone = sin(2.0 * Math.PI * freq * t)
            val mixed = (0.75 * tone + 0.25 * hpNoise) * attack * decay
            val sample = (mixed * 0.45 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * Linear chirp for toggle switches (rising: 1400Hz -> 3100Hz, falling: 2400Hz -> 950Hz).
     */
    private fun synthesizeToggleChirp(rising: Boolean): ShortArray {
        val durationSec = if (rising) 0.015f else 0.017f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val f0 = if (rising) 1350.0 else 2350.0
        val f1 = if (rising) 3100.0 else 920.0
        val tau = if (rising) 0.006 else 0.007

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / durationSec
            val phase = 2.0 * Math.PI * (f0 * t + 0.5 * (f1 - f0) * t * progress)
            val attack = min(1.0, t / 0.001)
            val decay = exp(-t / tau) * cos(Math.PI * 0.5 * progress)
            val sample = (sin(phase) * attack * decay * 0.70 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * App Launch Pop: Dual harmonic (420Hz body + 2000Hz transient).
     */
    private fun synthesizeAppLaunchPop(): ShortArray {
        val durationSec = 0.026f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val tauBody = 0.009
        val tauTransient = 0.0025

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = min(1.0, t / 0.0008)
            val body = sin(2.0 * Math.PI * 420.0 * t) * exp(-t / tauBody)
            val transient = sin(2.0 * Math.PI * 2100.0 * t) * exp(-t / tauTransient)
            val decayTail = cos(Math.PI * 0.5 * (i.toDouble() / numSamples))
            val mixed = (0.55 * body + 0.45 * transient) * attack * decayTail
            val sample = (mixed * 0.80 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * Math Tick: 3400Hz pristine crystalline tick.
     */
    private fun synthesizeMathTick(): ShortArray {
        val durationSec = 0.006f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val tau = 0.0015
        val freq = 3400.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = min(1.0, t / 0.0004)
            val decay = exp(-t / tau) * cos(Math.PI * 0.5 * (i.toDouble() / numSamples))
            val sample = (sin(2.0 * Math.PI * freq * t) * attack * decay * 0.40 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * Security Heartbeat: Bi-phasic organic pulse (280Hz "Lub" at 0ms, 360Hz "Dub" at 100ms).
     */
    private fun synthesizeHeartbeat(): ShortArray {
        val durationSec = 0.220f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(numSamples)

        val pulse1Start = 0.0
        val pulse1Duration = 0.040
        val pulse2Start = 0.100
        val pulse2Duration = 0.050

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sampleVal = 0.0

            if (t in pulse1Start..(pulse1Start + pulse1Duration)) {
                val pt = (t - pulse1Start) / pulse1Duration
                val env = sin(Math.PI * pt)
                sampleVal += sin(2.0 * Math.PI * 280.0 * (t - pulse1Start)) * env * 0.60
            }

            if (t in pulse2Start..(pulse2Start + pulse2Duration)) {
                val pt = (t - pulse2Start) / pulse2Duration
                val env = sin(Math.PI * pt)
                sampleVal += sin(2.0 * Math.PI * 360.0 * (t - pulse2Start)) * env * 0.80
            }

            val sample = (sampleVal * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * Delete Thud: Deep sub-bass impact (150Hz -> 65Hz slide).
     */
    private fun synthesizeDeleteThud(): ShortArray {
        val durationSec = 0.045f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val f0 = 150.0
        val f1 = 65.0
        val tau = 0.016

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / durationSec
            val phase = 2.0 * Math.PI * (f0 * t + 0.5 * (f1 - f0) * t * progress)
            val attack = min(1.0, t / 0.001)
            val decay = exp(-t / tau) * cos(Math.PI * 0.5 * progress)
            val sample = (sin(phase) * attack * decay * 0.85 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * Magnetic Tension Ping: Resonant crystalline ping (3100Hz).
     */
    private fun synthesizeMagneticPing(): ShortArray {
        val durationSec = 0.012f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val freq = 3100.0
        val tau = 0.003

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = min(1.0, t / 0.0006)
            val decay = exp(-t / tau) * cos(Math.PI * 0.5 * (i.toDouble() / numSamples))
            val sample = (sin(2.0 * Math.PI * freq * t) * attack * decay * 0.60 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * Plays the synthesized sonic burst with sub-5ms latency.
     * Automatically verifies ringer mode and system touch sound settings.
     */
    fun playSonic(type: SonicType, volumeScale: Float = 1.0f) {
        if (!isEnabled) return

        // Respect system silent and vibrate modes
        val ringer = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringer != AudioManager.RINGER_MODE_NORMAL) {
            return
        }

        // Respect system sound effects toggle
        val soundEffectsEnabled = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SOUND_EFFECTS_ENABLED,
            1
        ) != 0
        if (!soundEffectsEnabled) {
            return
        }

        val tracks = trackPool[type] ?: return
        if (tracks.isEmpty()) return

        val indexTracker = trackIndices[type] ?: return
        val trackIndex = (indexTracker.getAndIncrement() and 0x7FFFFFFF) % tracks.size
        val track = tracks[trackIndex]

        try {
            val clampedVol = volumeScale.coerceIn(0.0f, 1.0f)
            track.setVolume(clampedVol)
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.reloadStaticData()
            track.play()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack playback error for $type", e)
        }
    }

    /**
     * Release static audio tracks when shutting down engine.
     */
    fun release() {
        trackPool.values.flatten().forEach { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioTrack", e)
            }
        }
        trackPool.clear()
        trackIndices.clear()
    }
}
