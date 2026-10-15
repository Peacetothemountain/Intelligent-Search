package com.pixel.intelligentsearch.core.performance

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window

/**
 * Forwards each rendered frame's timings from an activity window to [ADPFThermalManager], which reports
 * them to the system's performance-hint session.
 *
 * Engineered by NG Designs.
 */
class FrameMetricsMonitor(
    private val adpfThermalManager: ADPFThermalManager
) {

    companion object {
        // One looper shared by every monitor: each overlay activity creates its own monitor, and a
        // thread per instance was never quit, leaking one HandlerThread per launch.
        private val metricsHandler: Handler by lazy {
            Handler(HandlerThread("HWUI-FrameMetrics").apply { start() }.looper)
        }
    }

    private var attachedWindow: Window? = null

    private val frameMetricsListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        adpfThermalManager.reportFrameMetrics(
            totalDurationNanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION),
            gpuDurationNanos = metrics.getMetric(FrameMetrics.GPU_DURATION),
            deadlineNanos = metrics.getMetric(FrameMetrics.DEADLINE)
        )
    }

    /** Starts sampling [activity]'s window. Pair with [detach] (the overlay calls them from onStart/onStop). */
    fun attach(activity: Activity) {
        try {
            val window = activity.window ?: return
            if (attachedWindow == window) return
            detach()
            window.addOnFrameMetricsAvailableListener(frameMetricsListener, metricsHandler)
            attachedWindow = window
        } catch (_: Throwable) {}
    }

    fun detach() {
        try {
            attachedWindow?.removeOnFrameMetricsAvailableListener(frameMetricsListener)
        } catch (_: Throwable) {}
        attachedWindow = null
    }
}
