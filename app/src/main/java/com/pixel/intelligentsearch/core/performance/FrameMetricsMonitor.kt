package com.pixel.intelligentsearch.core.performance

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * High-precision HWUI Frame Metrics Monitor.
 *
 * Hooks directly into [Window.addOnFrameMetricsAvailableListener] on a background looper
 * to measure nanosecond-precise frame durations, compute rolling jank statistics,
 * and feed hardware timings directly into [ADPFThermalManager].
 *
 * Engineered by NG Designs.
 */
class FrameMetricsMonitor(
    private val adpfThermalManager: ADPFThermalManager
) {

    data class FrameJankStats(
        val totalFrames: Long = 0L,
        val jankFrames: Long = 0L,
        val jankPercentage: Float = 0.0f,
        val medianDurationMs: Float = 0.0f,
        val p90DurationMs: Float = 0.0f,
        val p95DurationMs: Float = 0.0f,
        val p99DurationMs: Float = 0.0f,
        val maxDurationMs: Float = 0.0f
    )

    private val _jankStats = MutableStateFlow(FrameJankStats())
    val jankStats: StateFlow<FrameJankStats> = _jankStats.asStateFlow()

    private val metricsThread = HandlerThread("HWUI-FrameMetrics").apply { start() }
    private val metricsHandler = Handler(metricsThread.looper)

    // Rolling frame duration ring buffer (120 frames = ~1 sec at 120Hz)
    private val ringBufferSize = 120
    private val frameDurationsMs = FloatArray(ringBufferSize)
    private var ringIndex = 0
    private var totalFramesSampled = 0L
    private var totalJankFrames = 0L

    private var attachedWindow: Window? = null

    private val frameMetricsListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Window.OnFrameMetricsAvailableListener { _, frameMetrics, dropCountSinceLastInvocation ->
            processFrameMetrics(frameMetrics, dropCountSinceLastInvocation)
        }
    } else {
        null
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun processFrameMetrics(metrics: FrameMetrics, dropCount: Int) {
        val totalDurationNanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val gpuDurationNanos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            metrics.getMetric(FrameMetrics.GPU_DURATION)
        } else {
            0L
        }

        // Feed immediately to ADPF session for DVFS governor feedback
        adpfThermalManager.reportFrameMetrics(totalDurationNanos, gpuDurationNanos)

        val totalDurationMs = totalDurationNanos / 1_000_000f

        synchronized(frameDurationsMs) {
            frameDurationsMs[ringIndex] = totalDurationMs
            ringIndex = (ringIndex + 1) % ringBufferSize
            totalFramesSampled++

            // Frame is considered jank if it exceeded 8.33ms (120Hz threshold)
            val jankThresholdMs = 8.33f
            if (totalDurationMs > jankThresholdMs || dropCount > 0) {
                totalJankFrames += 1 + dropCount
            }

            // Update stats every 60 frames
            if (totalFramesSampled % 60 == 0L) {
                publishStats()
            }
        }
    }

    private fun publishStats() {
        val sampleSize = if (totalFramesSampled < ringBufferSize) totalFramesSampled.toInt() else ringBufferSize
        if (sampleSize == 0) return

        val sorted = frameDurationsMs.copyOf(sampleSize).apply { sort() }
        val median = sorted[sampleSize / 2]
        val p90 = sorted[(sampleSize * 0.90f).toInt().coerceAtMost(sampleSize - 1)]
        val p95 = sorted[(sampleSize * 0.95f).toInt().coerceAtMost(sampleSize - 1)]
        val p99 = sorted[(sampleSize * 0.99f).toInt().coerceAtMost(sampleSize - 1)]
        val max = sorted.last()
        val jankPct = if (totalFramesSampled > 0) (totalJankFrames.toFloat() / totalFramesSampled) * 100f else 0f

        _jankStats.value = FrameJankStats(
            totalFrames = totalFramesSampled,
            jankFrames = totalJankFrames,
            jankPercentage = jankPct,
            medianDurationMs = median,
            p90DurationMs = p90,
            p95DurationMs = p95,
            p99DurationMs = p99,
            maxDurationMs = max
        )
    }

    fun attach(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && frameMetricsListener != null) {
            try {
                val window = activity.window ?: return
                if (attachedWindow == window) return
                detach()
                window.addOnFrameMetricsAvailableListener(frameMetricsListener, metricsHandler)
                attachedWindow = window
            } catch (_: Throwable) {}
        }
    }

    fun detach() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && frameMetricsListener != null) {
            try {
                attachedWindow?.removeOnFrameMetricsAvailableListener(frameMetricsListener)
            } catch (_: Throwable) {}
            attachedWindow = null
        }
    }

    fun release() {
        detach()
        metricsThread.quitSafely()
    }
}
