package com.pixel.intelligentsearch.core.diagnostics

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

@Immutable
data class LatencyBreakdown(
    val totalMs: Long = 0,
    val appsMs: Long = 0,
    val contactsMs: Long = 0,
    val filesMs: Long = 0,
    val shortcutsMs: Long = 0,
    val webMs: Long = 0,
    val mathMs: Long = 0
)

@Immutable
data class FrameTelemetry(
    val currentFps: Float = 120f,
    val avgFrameDurationMs: Float = 8.3f,
    val totalFrames: Long = 0,
    val jankFrames: Long = 0,
    val jankPercentage: Float = 0f
)

@Immutable
data class MemoryTelemetry(
    val heapUsedMb: Long = 0,
    val heapMaxMb: Long = 0,
    val heapUsagePercent: Float = 0f,
    val iconCacheEntries: Int = 0,
    val iconCacheHitRate: Float = 0f
)

@Immutable
data class IndexHealth(
    val historyCount: Int = 0,
    val databaseSizeBytes: Long = 0,
    val installedAppsCount: Int = 0,
    val customBangsCount: Int = 0
)

@Immutable
data class DiagnosticsState(
    val recentLatencies: List<Long> = emptyList(),
    val latestBreakdown: LatencyBreakdown = LatencyBreakdown(),
    val p50LatencyMs: Double = 0.0,
    val p90LatencyMs: Double = 0.0,
    val p99LatencyMs: Double = 0.0,
    val frameStats: FrameTelemetry = FrameTelemetry(),
    val memoryStats: MemoryTelemetry = MemoryTelemetry(),
    val indexHealth: IndexHealth = IndexHealth()
)

object PerformanceTelemetry {

    private const val MAX_LATENCY_HISTORY = 100
    private val latencyHistory = ConcurrentLinkedDeque<Long>()

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    private val totalFramesCounter = AtomicLong(0)
    private val jankFramesCounter = AtomicLong(0)
    private val totalFrameDurationNs = AtomicLong(0)

    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null
    private val handler = Handler(Looper.getMainLooper())

    fun recordQueryLatency(breakdown: LatencyBreakdown) {
        latencyHistory.addLast(breakdown.totalMs)
        while (latencyHistory.size > MAX_LATENCY_HISTORY) {
            latencyHistory.pollFirst()
        }

        val snapshot = latencyHistory.toList().sorted()
        val p50 = if (snapshot.isNotEmpty()) snapshot[(snapshot.size * 0.50).toInt()].toDouble() else 0.0
        val p90 = if (snapshot.isNotEmpty()) snapshot[(snapshot.size * 0.90).toInt().coerceAtMost(snapshot.lastIndex)].toDouble() else 0.0
        val p99 = if (snapshot.isNotEmpty()) snapshot[(snapshot.size * 0.99).toInt().coerceAtMost(snapshot.lastIndex)].toDouble() else 0.0

        _state.update { curr ->
            curr.copy(
                recentLatencies = latencyHistory.toList(),
                latestBreakdown = breakdown,
                p50LatencyMs = p50,
                p90LatencyMs = p90,
                p99LatencyMs = p99
            )
        }
    }

    fun attachFrameMetrics(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            detachFrameMetrics(activity)
            val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                val durationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
                val durationMs = durationNs / 1_000_000f

                val total = totalFramesCounter.incrementAndGet()
                val totalNs = totalFrameDurationNs.addAndGet(durationNs)

                // 120Hz frame deadline is 8.33ms; 60Hz is 16.66ms
                val isJank = durationMs > 16.0f
                if (isJank) {
                    jankFramesCounter.incrementAndGet()
                }

                val jankCount = jankFramesCounter.get()
                val avgDuration = if (total > 0) (totalNs / total.toDouble() / 1_000_000.0).toFloat() else 8.3f
                val jankPct = if (total > 0) (jankCount.toFloat() / total.toFloat()) * 100f else 0f

                val displayRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.display?.refreshRate ?: 120f
                } else {
                    120f
                }

                _state.update { curr ->
                    curr.copy(
                        frameStats = FrameTelemetry(
                            currentFps = displayRate,
                            avgFrameDurationMs = avgDuration,
                            totalFrames = total,
                            jankFrames = jankCount,
                            jankPercentage = jankPct
                        )
                    )
                }
            }
            frameMetricsListener = listener
            activity.window?.addOnFrameMetricsAvailableListener(listener, handler)
        }
    }

    fun detachFrameMetrics(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && frameMetricsListener != null) {
            try {
                activity.window?.removeOnFrameMetricsAvailableListener(frameMetricsListener)
            } catch (_: Exception) {}
            frameMetricsListener = null
        }
    }

    fun refreshSystemMetrics(context: Context, historyCount: Int, customBangsCount: Int, appsCount: Int) {
        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory()
        val freeMem = runtime.freeMemory()
        val maxMem = runtime.maxMemory()
        val usedMb = (totalMem - freeMem) / (1024 * 1024)
        val maxMb = maxMem / (1024 * 1024)
        val usagePct = if (maxMb > 0) (usedMb.toFloat() / maxMb.toFloat()) * 100f else 0f

        val dbFile = context.getDatabasePath("intelligent_search_db")
        val dbSize = if (dbFile.exists()) dbFile.length() else 0L

        _state.update { curr ->
            curr.copy(
                memoryStats = MemoryTelemetry(
                    heapUsedMb = usedMb,
                    heapMaxMb = maxMb,
                    heapUsagePercent = usagePct,
                    iconCacheEntries = 128
                ),
                indexHealth = IndexHealth(
                    historyCount = historyCount,
                    databaseSizeBytes = dbSize,
                    installedAppsCount = appsCount,
                    customBangsCount = customBangsCount
                )
            )
        }
    }

    fun resetCounters() {
        totalFramesCounter.set(0)
        jankFramesCounter.set(0)
        totalFrameDurationNs.set(0)
        latencyHistory.clear()
        _state.update {
            DiagnosticsState()
        }
    }
}
