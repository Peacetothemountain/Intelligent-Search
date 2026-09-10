package com.pixel.intelligentsearch.core.performance

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PerformanceHintManager
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Android Dynamic Performance Framework (ADPF) Thermal & Frame Pacing Engine.
 *
 * Implements hardware-synchronized thermal headroom monitoring, dynamic frame deadline pacing
 * via [PerformanceHintManager.Session], and adaptive render quality governance (144Hz / 120Hz / 90Hz / 60Hz).
 *
 * Designed and engineered by NG Designs.
 */
class ADPFThermalManager(private val context: Context) {

    enum class ThermalThrottleLevel {
        NORMAL,   // Full 120/144 FPS target, full AGSL shaders, 48dp+ blur, full particle density
        LIGHT,    // 120/144 FPS target, AGSL enabled, max 24dp blur, 75% particle density
        MODERATE, // 90 FPS / 60 FPS target, static gradient fallback, max 12dp blur, 50% particle density
        SEVERE,   // 60 FPS locked, disable window blur behind, disable AGSL shaders, zero particles
        CRITICAL  // 60/30 FPS locked, minimal UI rendering, cancel non-essential IO
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val performanceHintManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(PerformanceHintManager::class.java)
    } else {
        null
    }

    private val _thermalThrottleLevel = MutableStateFlow(ThermalThrottleLevel.NORMAL)
    val thermalThrottleLevel: StateFlow<ThermalThrottleLevel> = _thermalThrottleLevel.asStateFlow()

    private val _thermalHeadroom = MutableStateFlow(0.0f)
    val thermalHeadroom: StateFlow<Float> = _thermalHeadroom.asStateFlow()

    // Active PerformanceHintManager.Session on Android 12+ (API 31+)
    private var hintSession: Any? = null // Type erased for API compatibility
    private val isSessionInitialized = AtomicBoolean(false)

    // Current target frame duration in nanoseconds (Default: 120 FPS -> 8.333ms)
    private val currentTargetDurationNanos = AtomicLong(FRAME_DURATION_120HZ_NANOS)
    private var registeredTids: IntArray = intArrayOf(Process.myTid())

    // Dedicated background handler for thermal polling & ADPF maintenance
    private val monitorThread = HandlerThread("ADPF-Thermal-Monitor").apply { start() }
    private val monitorHandler = Handler(monitorThread.looper)

    private val thermalStatusListener: PowerManager.OnThermalStatusChangedListener? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { status ->
                evaluateThermalState(status)
            }
        } else {
            null
        }
    }

    init {
        registerThermalListeners()
        initHintSession(targetFps = 120)
    }

    private fun registerThermalListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalStatusListener != null) {
            try {
                powerManager.addThermalStatusListener(context.mainExecutor, thermalStatusListener!!)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to register OnThermalStatusChangedListener: ${e.message}")
            }
        }
        // Kick off periodic headroom query on the monitor thread
        scheduleHeadroomPoll()
    }

    private fun scheduleHeadroomPoll() {
        monitorHandler.postDelayed(object : Runnable {
            override fun run() {
                updateThermalHeadroom()
                monitorHandler.postDelayed(this, HEADROOM_POLL_INTERVAL_MS)
            }
        }, HEADROOM_POLL_INTERVAL_MS)
    }

    /**
     * Initializes the [PerformanceHintManager.Session] with the process main thread and RenderThread.
     */
    fun initHintSession(targetFps: Int = 120) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || performanceHintManager == null) {
            return
        }

        val targetNanos = getFrameDurationNanosForFps(targetFps)
        currentTargetDurationNanos.set(targetNanos)

        monitorHandler.post {
            try {
                val mainTid = Process.myPid()
                val renderTid = findRenderThreadTid()
                val tids = if (renderTid != null && renderTid != mainTid) {
                    intArrayOf(mainTid, renderTid)
                } else {
                    intArrayOf(mainTid)
                }
                registeredTids = tids

                val session = performanceHintManager.createHintSession(tids, targetNanos)
                hintSession = session
                isSessionInitialized.set(true)
                Log.i(TAG, "ADPF HintSession initialized with tids=${tids.contentToString()} target=${targetNanos / 1_000_000f}ms")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to create HintSession: ${e.message}")
            }
        }
    }

    /**
     * Dynamically updates the target frame rate for the ADPF session (e.g. 144Hz, 120Hz, 90Hz, 60Hz).
     */
    fun updateTargetFps(targetFps: Int) {
        val targetNanos = getFrameDurationNanosForFps(targetFps)
        currentTargetDurationNanos.set(targetNanos)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val session = hintSession as? PerformanceHintManager.Session ?: return
            try {
                session.updateTargetWorkDuration(targetNanos)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to update target work duration: ${e.message}")
            }
        }
    }

    /**
     * Reports actual work duration (CPU + GPU) for a completed frame to the OS governor.
     * This directly prevents thermal throttling while guaranteeing frame deadlines.
     */
    fun reportActualWorkDuration(actualDurationNanos: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && actualDurationNanos > 0) {
            val session = hintSession as? PerformanceHintManager.Session ?: return
            try {
                session.reportActualWorkDuration(actualDurationNanos)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Reports detailed frame metrics from HWUI / WindowFrameMetricsListener.
     */
    fun reportFrameMetrics(totalDurationNanos: Long, gpuDurationNanos: Long = 0L) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && totalDurationNanos > 0) {
            val session = hintSession as? PerformanceHintManager.Session ?: return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && gpuDurationNanos > 0L) {
                    val cpuDurationNanos = (totalDurationNanos - gpuDurationNanos).coerceAtLeast(0L)
                    val workDuration = android.os.WorkDuration().apply {
                        this.actualTotalDurationNanos = totalDurationNanos
                        this.actualCpuDurationNanos = cpuDurationNanos
                        this.actualGpuDurationNanos = gpuDurationNanos
                    }
                    session.reportActualWorkDuration(workDuration)
                } else {
                    session.reportActualWorkDuration(totalDurationNanos)
                }
            } catch (_: Throwable) {
                // Fallback to legacy single-duration reporting
                try {
                    session.reportActualWorkDuration(totalDurationNanos)
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Enables energy-aware power efficiency mode during low-demand or high-heat states on Android 15+.
     */
    fun setPreferPowerEfficiency(preferEfficiency: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val session = hintSession as? PerformanceHintManager.Session ?: return
            try {
                session.setPreferPowerEfficiency(preferEfficiency)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Scans `/proc/self/task/` to discover the TID of the hardware RenderThread.
     */
    private fun findRenderThreadTid(): Int? {
        return try {
            val taskDir = File("/proc/self/task")
            if (!taskDir.exists() || !taskDir.isDirectory) return null
            val tasks = taskDir.listFiles() ?: return null
            for (task in tasks) {
                val tid = task.name.toIntOrNull() ?: continue
                val commFile = File(task, "comm")
                if (commFile.exists()) {
                    val name = commFile.readText().trim()
                    if (name == "RenderThread") {
                        return tid
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Updates and caches thermal headroom from [PowerManager].
     */
    fun updateThermalHeadroom(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                val headroom = powerManager.getThermalHeadroom(10)
                if (!headroom.isNaN() && headroom >= 0.0f) {
                    _thermalHeadroom.value = headroom
                    evaluateThermalFromHeadroom(headroom)
                    return headroom
                }
            } catch (_: Throwable) {}
        }
        return 0.0f
    }

    fun getThermalHeadroom(): Float {
        val cached = _thermalHeadroom.value
        return if (cached > 0f) cached else updateThermalHeadroom()
    }

    private fun evaluateThermalFromHeadroom(headroom: Float) {
        val newLevel = when {
            headroom >= 1.50f -> ThermalThrottleLevel.CRITICAL
            headroom >= 1.25f -> ThermalThrottleLevel.SEVERE
            headroom >= 1.00f -> ThermalThrottleLevel.MODERATE
            headroom >= 0.85f -> ThermalThrottleLevel.LIGHT
            else -> ThermalThrottleLevel.NORMAL
        }
        if (_thermalThrottleLevel.value != newLevel) {
            _thermalThrottleLevel.value = newLevel
            applyThermalMitigations(newLevel)
        }
    }

    private fun evaluateThermalState(status: Int) {
        val newLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (status) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalThrottleLevel.NORMAL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalThrottleLevel.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalThrottleLevel.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalThrottleLevel.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalThrottleLevel.CRITICAL
                else -> ThermalThrottleLevel.NORMAL
            }
        } else {
            ThermalThrottleLevel.NORMAL
        }
        if (_thermalThrottleLevel.value != newLevel) {
            _thermalThrottleLevel.value = newLevel
            applyThermalMitigations(newLevel)
        }
    }

    private fun applyThermalMitigations(level: ThermalThrottleLevel) {
        Log.i(TAG, "Thermal mitigation transition: $level (headroom=${_thermalHeadroom.value})")
        when (level) {
            ThermalThrottleLevel.NORMAL -> {
                setPreferPowerEfficiency(false)
                updateTargetFps(120)
            }
            ThermalThrottleLevel.LIGHT -> {
                setPreferPowerEfficiency(false)
                updateTargetFps(120)
            }
            ThermalThrottleLevel.MODERATE -> {
                setPreferPowerEfficiency(true)
                updateTargetFps(90)
            }
            ThermalThrottleLevel.SEVERE -> {
                setPreferPowerEfficiency(true)
                updateTargetFps(60)
            }
            ThermalThrottleLevel.CRITICAL -> {
                setPreferPowerEfficiency(true)
                updateTargetFps(60)
            }
        }
    }

    // =========================================================================
    // Dynamic Render Engine Gating Queries
    // =========================================================================

    /**
     * Whether intensive backdrop blur should be throttled to prevent GPU overdraw jank.
     */
    fun shouldThrottleBlurEffects(): Boolean {
        return _thermalThrottleLevel.value >= ThermalThrottleLevel.MODERATE
    }

    /**
     * Whether high-complexity AGSL runtime shaders should be substituted with lightweight linear gradients.
     */
    fun shouldThrottleAGSLShaders(): Boolean {
        return _thermalThrottleLevel.value >= ThermalThrottleLevel.MODERATE
    }

    /**
     * Returns the recommended blur radius in pixels, scaling down dynamically during thermal events.
     */
    fun getRecommendedBlurRadius(requestedRadius: Float): Float {
        return when (_thermalThrottleLevel.value) {
            ThermalThrottleLevel.NORMAL -> requestedRadius
            ThermalThrottleLevel.LIGHT -> requestedRadius.coerceAtMost(24f)
            ThermalThrottleLevel.MODERATE -> requestedRadius.coerceAtMost(12f)
            ThermalThrottleLevel.SEVERE,
            ThermalThrottleLevel.CRITICAL -> 0f
        }
    }

    /**
     * Returns the recommended refresh rate for the display panel given current thermal conditions.
     */
    fun getRecommendedRefreshRate(maxDisplayRate: Float = 120f): Float {
        return when (_thermalThrottleLevel.value) {
            ThermalThrottleLevel.NORMAL,
            ThermalThrottleLevel.LIGHT -> maxDisplayRate
            ThermalThrottleLevel.MODERATE -> if (maxDisplayRate >= 90f) 90f else 60f
            ThermalThrottleLevel.SEVERE,
            ThermalThrottleLevel.CRITICAL -> 60f
        }
    }

    fun applyTopAppThreadPriority() {
        try {
            // -10 corresponds to TOP_APP foreground audio/rendering nice level in Android Linux kernel
            Process.setThreadPriority(-10)
        } catch (_: Throwable) {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
            } catch (_: Throwable) {}
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalStatusListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalStatusListener!!)
            } catch (_: Throwable) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val session = hintSession as? PerformanceHintManager.Session
            try {
                session?.close()
            } catch (_: Throwable) {}
            hintSession = null
        }
        monitorThread.quitSafely()
    }

    companion object {
        private const val TAG = "ADPFThermalManager"
        private const val HEADROOM_POLL_INTERVAL_MS = 2500L

        const val FRAME_DURATION_144HZ_NANOS = 6_944_444L
        const val FRAME_DURATION_120HZ_NANOS = 8_333_333L
        const val FRAME_DURATION_90HZ_NANOS = 11_111_111L
        const val FRAME_DURATION_60HZ_NANOS = 16_666_666L

        fun getFrameDurationNanosForFps(fps: Int): Long {
            return when {
                fps >= 140 -> FRAME_DURATION_144HZ_NANOS
                fps >= 110 -> FRAME_DURATION_120HZ_NANOS
                fps >= 85 -> FRAME_DURATION_90HZ_NANOS
                else -> FRAME_DURATION_60HZ_NANOS
            }
        }

        @Volatile
        private var INSTANCE: ADPFThermalManager? = null

        fun getInstance(context: Context): ADPFThermalManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ADPFThermalManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
