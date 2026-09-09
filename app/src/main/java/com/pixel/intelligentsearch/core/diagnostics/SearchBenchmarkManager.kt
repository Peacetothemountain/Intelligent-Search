package com.pixel.intelligentsearch.core.diagnostics

import android.content.Context
import android.os.Build
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.performance.ADPFThermalManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureNanoTime

data class BenchmarkSummary(
    val queryCount: Int = 0,
    val totalTimeMs: Long = 0,
    val minLatencyMs: Double = 0.0,
    val maxLatencyMs: Double = 0.0,
    val avgLatencyMs: Double = 0.0,
    val p50LatencyMs: Double = 0.0,
    val p90LatencyMs: Double = 0.0,
    val p99LatencyMs: Double = 0.0,
    val qps: Double = 0.0
)

data class BenchmarkRunState(
    val isRunning: Boolean = false,
    val currentProgress: Float = 0f,
    val completedQueries: Int = 0,
    val totalQueries: Int = 100,
    val summary: BenchmarkSummary? = null
)

@Singleton
class SearchBenchmarkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bangManager: SearchBangManager
) {

    private val adpfThermalManager = ADPFThermalManager(context)

    private val _benchmarkState = MutableStateFlow(BenchmarkRunState())
    val benchmarkState: StateFlow<BenchmarkRunState> = _benchmarkState.asStateFlow()

    private val syntheticQueries = listOf(
        "chrome", "camera", "calc", "youtube", "music", "settings",
        "photos", "drive", "maps", "notes", "file", "download",
        "!yt lofi chill beats", "!w quantum mechanics", "!g pixel 10 pro",
        "12 * 45", "100 usd to eur", "500 miles in km", "weather in tokyo",
        "time in london", "torch", "bluetooth", "wifi", "battery saver"
    )

    suspend fun runBenchmark(totalRuns: Int = 100): BenchmarkSummary = withContext(Dispatchers.Default) {
        _benchmarkState.value = BenchmarkRunState(
            isRunning = true,
            currentProgress = 0f,
            completedQueries = 0,
            totalQueries = totalRuns,
            summary = null
        )

        val latencies = mutableListOf<Double>()
        val startOverall = System.currentTimeMillis()

        // Warmup JIT compiler
        for (i in 0 until 10) {
            val q = syntheticQueries[i % syntheticQueries.size]
            SystemDataProvider.evaluateMath(q)
            bangManager.parseBangQuery(q)
        }

        for (i in 0 until totalRuns) {
            val q = syntheticQueries[i % syntheticQueries.size]
            val durationNs = measureNanoTime {
                // 1. Bang Parser
                val parsedBang = bangManager.parseBangQuery(q)
                // 2. Math & Unit Conversion
                val math = SystemDataProvider.evaluateMath(q)
                val unit = SystemDataProvider.evaluateUnitConversion(q)
                // 3. App Search matching
                val apps = SystemDataProvider.getAllApps(context).filter {
                    it.name.contains(q, ignoreCase = true)
                }
            }
            val ms = durationNs / 1_000_000.0
            latencies.add(ms)

            val progress = (i + 1).toFloat() / totalRuns.toFloat()
            _benchmarkState.value = _benchmarkState.value.copy(
                currentProgress = progress,
                completedQueries = i + 1
            )
        }

        val totalTime = System.currentTimeMillis() - startOverall
        val sorted = latencies.sorted()

        val min = sorted.firstOrNull() ?: 0.0
        val max = sorted.lastOrNull() ?: 0.0
        val avg = sorted.average()
        val p50 = sorted[(sorted.size * 0.50).toInt().coerceAtMost(sorted.lastIndex)]
        val p90 = sorted[(sorted.size * 0.90).toInt().coerceAtMost(sorted.lastIndex)]
        val p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.lastIndex)]
        val qps = if (totalTime > 0) (totalRuns.toDouble() / (totalTime / 1000.0)) else 0.0

        val result = BenchmarkSummary(
            queryCount = totalRuns,
            totalTimeMs = totalTime,
            minLatencyMs = min,
            maxLatencyMs = max,
            avgLatencyMs = avg,
            p50LatencyMs = p50,
            p90LatencyMs = p90,
            p99LatencyMs = p99,
            qps = qps
        )

        _benchmarkState.value = BenchmarkRunState(
            isRunning = false,
            currentProgress = 1.0f,
            completedQueries = totalRuns,
            totalQueries = totalRuns,
            summary = result
        )

        result
    }

    fun generateDiagnosticReportJson(): String {
        val runtime = Runtime.getRuntime()
        val telemetry = PerformanceTelemetry.state.value
        val root = JSONObject()

        // Device Info
        val deviceObj = JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("release", Build.VERSION.RELEASE)
            put("supportedAbis", Build.SUPPORTED_ABIS.joinToString(","))
            put("thermalHeadroom", adpfThermalManager.getThermalHeadroom())
        }
        root.put("device", deviceObj)

        // Frame Metrics
        val frameObj = JSONObject().apply {
            put("displayFps", telemetry.frameStats.currentFps)
            put("avgFrameDurationMs", telemetry.frameStats.avgFrameDurationMs)
            put("totalFramesTracked", telemetry.frameStats.totalFrames)
            put("jankFrames", telemetry.frameStats.jankFrames)
            put("jankPercentage", telemetry.frameStats.jankPercentage)
        }
        root.put("frameTelemetry", frameObj)

        // Memory
        val memObj = JSONObject().apply {
            put("heapUsedMb", telemetry.memoryStats.heapUsedMb)
            put("heapMaxMb", telemetry.memoryStats.heapMaxMb)
            put("heapUsagePercent", telemetry.memoryStats.heapUsagePercent)
        }
        root.put("memory", memObj)

        // Latency
        val latObj = JSONObject().apply {
            put("p50LatencyMs", telemetry.p50LatencyMs)
            put("p90LatencyMs", telemetry.p90LatencyMs)
            put("p99LatencyMs", telemetry.p99LatencyMs)
            put("recentSampleCount", telemetry.recentLatencies.size)
        }
        root.put("latency", latObj)

        // Benchmark Latest
        val lastBench = _benchmarkState.value.summary
        if (lastBench != null) {
            val benchObj = JSONObject().apply {
                put("queryCount", lastBench.queryCount)
                put("p50Ms", lastBench.p50LatencyMs)
                put("p90Ms", lastBench.p90LatencyMs)
                put("p99Ms", lastBench.p99LatencyMs)
                put("qps", lastBench.qps)
                put("totalTimeMs", lastBench.totalTimeMs)
            }
            root.put("latestBenchmark", benchObj)
        }

        return root.toString(2)
    }
}
