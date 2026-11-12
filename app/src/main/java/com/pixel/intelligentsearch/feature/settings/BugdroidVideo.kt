package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Plays one bugdroid clip, silent and looping, into a [TextureView], with the platform's own decoder.
 *
 * The clips are MP4 files in res/raw with one H.264 track, no sound and a single key frame. At the end of the file
 * the same samples are fed again from the start with their times moved on by exactly one loop, so the decoder never
 * stops and the loop is gapless, every frame kept as rendered. Each decoded frame is handed to the view at its own
 * time on a clock that stops while paused. While paused, frames up to the paused position still show, so the first
 * frame appears as soon as the view has a surface, playing or not.
 *
 * Decoding starts when the view's surface is ready and stops when it goes, starting again from the first frame when a
 * new one arrives: the clip's poster is that first frame, so the page shows the poster and the video takes over from
 * it with no jump. A decoder that fails before its first frame is replaced by the next one the device has for the
 * format, so a broken hardware decoder leaves the clip to a software one rather than to the poster. Everything about the decoder runs on one thread of its own; [play], [pause] and [attach] only post
 * to it. Taking a surface away waits for the decoder to let go of it, so a surface is never released while in use.
 */
class BugdroidVideo(context: Context, @param:RawRes private val rawRes: Int) {
    private val context = context.applicationContext
    private val thread = HandlerThread("BugdroidVideo").apply { start() }
    private val handler = Handler(thread.looper)

    // Owned by the main thread.
    private var view: TextureView? = null
    private var onFrameShown: (() -> Unit)? = null
    private var frameShown = false
    private var released = false

    // Owned by the decoder thread.
    private var surface: Surface? = null
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var decoders: List<String> = emptyList()
    private var decoder = 0 // the one in [decoders] in use; kept once one works
    private var decoded = false
    // Decoded frames not yet handed to the surface: their presentation times (us) and output buffer indexes.
    private val ready = ArrayDeque<Long>()
    private val readyIndex = ArrayDeque<Int>()
    private var playing = false
    private var anchorUs = 0L // clip position (us) at anchorNanos
    private var anchorNanos = 0L
    private var loopOffsetUs = 0L
    private var firstUs = Long.MAX_VALUE
    private var lastUs = Long.MIN_VALUE
    private var firstPassSamples = 0
    private var loopUs = 0L
    private val drain = Runnable { releaseDueFrames() }

    private val listener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = start(texture)
        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            stopAndWait()
            return true
        }
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
            if (frameShown) return
            frameShown = true
            onFrameShown?.invoke()
        }
    }

    /** Shows the clip in [view]; [onFrameShown] runs on the main thread once the view has drawn its first frame. */
    fun attach(view: TextureView, onFrameShown: () -> Unit) {
        this.onFrameShown = onFrameShown
        if (released || this.view === view) return
        this.view?.let { detach(it) }
        this.view = view
        view.surfaceTextureListener = listener
        view.surfaceTexture?.takeIf { view.isAvailable }?.let { start(it) }
    }

    fun detach(view: TextureView) {
        if (this.view !== view) return
        stopAndWait()
        view.surfaceTextureListener = null
        this.view = null
    }

    fun play() {
        handler.post {
            if (playing) return@post
            playing = true
            anchorNanos = System.nanoTime()
            releaseDueFrames()
        }
    }

    fun pause() {
        handler.post {
            if (!playing) return@post
            anchorUs = positionUs()
            playing = false
            releaseDueFrames()
        }
    }

    fun release() {
        if (released) return
        view?.let { detach(it) }
        released = true
        thread.quitSafely()
    }

    private fun start(texture: SurfaceTexture) {
        frameShown = false
        handler.post {
            stopDecoder()
            startDecoder(Surface(texture))
        }
    }

    /** Stops the decoder and lets go of the surface before returning, waiting at most half a second. */
    private fun stopAndWait() {
        val done = CountDownLatch(1)
        if (handler.post { stopDecoder(); done.countDown() }) done.await(500, TimeUnit.MILLISECONDS)
    }

    private fun startDecoder(surface: Surface) {
        this.surface = surface
        openCodec()
    }

    /** Opens the clip with the decoder in use, moving on to the next one while one can't even start. */
    private fun openCodec() {
        val surface = surface ?: return
        while (true) {
            val started = runCatching { openCodec(surface) }
            if (started.isSuccess) return
            closeCodec()
            if (decoder + 1 >= decoders.size) return
            decoder++
        }
    }

    private fun openCodec(surface: Surface) {
        val extractor = MediaExtractor().also { this.extractor = it }
        context.resources.openRawResourceFd(rawRes).use {
            extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length)
        }
        val track = (0 until extractor.trackCount).first {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        if (decoders.isEmpty()) decoders = decodersFor(mime)
        loopOffsetUs = 0L
        firstUs = Long.MAX_VALUE
        lastUs = Long.MIN_VALUE
        firstPassSamples = 0
        loopUs = 0L
        decoded = false
        // The clock starts at the first frame; it is set again when that frame is decoded (see the callback).
        anchorUs = extractor.sampleTime.coerceAtLeast(0L)
        anchorNanos = System.nanoTime()
        val codec = (decoders.getOrNull(decoder)?.let { MediaCodec.createByCodecName(it) }
            ?: MediaCodec.createDecoderByType(mime)).also { this.codec = it }
        codec.setCallback(callback, handler)
        codec.configure(format, surface, null, 0)
        codec.start()
    }

    /** A decoder went wrong: try the next one if this one never decoded a frame, else stop where the clip is. */
    private fun fail() {
        closeCodec()
        if (!decoded && decoder + 1 < decoders.size) {
            decoder++
            openCodec()
        }
    }

    private fun closeCodec() {
        handler.removeCallbacks(drain)
        readyIndex.clear()
        ready.clear()
        codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
        codec = null
        extractor?.release()
        extractor = null
    }

    private fun stopDecoder() {
        closeCodec()
        surface?.release()
        surface = null
    }

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (codec !== this@BugdroidVideo.codec) return
            runCatching { queueSample(codec, index) }.onFailure { fail() }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (codec !== this@BugdroidVideo.codec) return
            if (!decoded) {
                // The clock starts when the first frame is ready, not when the decoder was asked for it: the time the
                // decoder took to start would otherwise make the first frames late, and they would all show at once.
                anchorUs = info.presentationTimeUs
                anchorNanos = System.nanoTime()
            }
            decoded = true
            readyIndex.addLast(index)
            ready.addLast(info.presentationTimeUs)
            releaseDueFrames()
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            if (codec === this@BugdroidVideo.codec) fail()
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) = Unit
    }

    /** Feeds the next sample, going back to the start of the file, one loop later, at its end. */
    private fun queueSample(codec: MediaCodec, index: Int) {
        val extractor = extractor ?: return
        val buffer = codec.getInputBuffer(index) ?: return
        var size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            if (loopUs == 0L) {
                // A constant frame rate: the loop is every frame's time, the last frame's included.
                val frame = if (firstPassSamples > 1) (lastUs - firstUs) / (firstPassSamples - 1) else 0L
                loopUs = (lastUs - firstUs + frame).coerceAtLeast(1L)
            }
            loopOffsetUs += loopUs
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            size = extractor.readSampleData(buffer, 0)
            if (size < 0) return
        }
        val sampleUs = extractor.sampleTime
        if (loopUs == 0L) {
            firstUs = minOf(firstUs, sampleUs)
            lastUs = maxOf(lastUs, sampleUs)
            firstPassSamples++
        }
        codec.queueInputBuffer(index, 0, size, sampleUs + loopOffsetUs, 0)
        extractor.advance()
    }

    private fun positionUs(): Long =
        if (playing) anchorUs + (System.nanoTime() - anchorNanos) / 1_000L else anchorUs

    /** Hands each decoded frame to the surface at its time, and waits for the next one's. */
    private fun releaseDueFrames() {
        handler.removeCallbacks(drain)
        val codec = codec ?: return
        while (ready.isNotEmpty()) {
            val frameUs = ready.peekFirst()!!
            val now = System.nanoTime()
            if (!playing) {
                if (frameUs > anchorUs) return
                releaseFrame(codec, now)
                continue
            }
            val dueNanos = anchorNanos + (frameUs - anchorUs) * 1_000L
            val early = dueNanos - now
            if (early > HAND_OVER_AHEAD_NANOS) {
                // Rounded up: rounded down, the last millisecond's wait was 0 and the thread spun until the frame was due.
                handler.postAtTime(drain, SystemClock.uptimeMillis() + (early - HAND_OVER_AHEAD_NANOS + 999_999L) / 1_000_000L)
                return
            }
            releaseFrame(codec, maxOf(dueNanos, now))
        }
    }

    private fun releaseFrame(codec: MediaCodec, atNanos: Long) {
        ready.removeFirst()
        val index = readyIndex.removeFirst()
        runCatching { codec.releaseOutputBuffer(index, atNanos) }.onFailure { fail() }
    }

    private companion object {
        /** Every decoder for [mime], in the device's order of preference: hardware ones first. */
        fun decodersFor(mime: String): List<String> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { type -> type.equals(mime, ignoreCase = true) } }
            .map { it.name }

        /** Frames go to the surface this far ahead of their time; it shows each on the display frame nearest it. */
        const val HAND_OVER_AHEAD_NANOS = 20_000_000L
    }
}
