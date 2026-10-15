package com.pixel.intelligentsearch.core.performance

import com.pixel.intelligentsearch.di.appEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import javax.inject.Inject
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PerformanceHintManager
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
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
@Singleton
class ADPFThermalManager @Inject constructor(@param:ApplicationContext private val context: Context) {

    enum class ThermalThrottleLevel {
        NORMAL,   // Full 120/144 FPS target, full AGSL shaders, 48dp+ blur, full particle density
        LIGHT,    // 120/144 FPS target, AGSL enabled, max 24dp blur, 75% particle density
        MODERATE, // 90 FPS / 60 FPS target, static gradient fallback, max 12dp blur, 50% particle density
        SEVERE,   // 60 FPS locked, disable window blur behind, disable AGSL shaders, zero particles
        CRITICAL  // 60/30 FPS locked, minimal UI rendering, cancel non-essential IO
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val performanceHintManager = context.getSystemService(PerformanceHintManager::class.java)

    private val _thermalThrottleLevel = MutableStateFlow(ThermalThrottleLevel.NORMAL)
    val thermalThrottleLevel: StateFlow<ThermalThrottleLevel> = _thermalThrottleLevel.asStateFlow()

    private val _thermalHeadroom = MutableStateFlow(0.0f)

    // Active PerformanceHintManager.Session on Android 12+ (API 31+). Written on the monitor thread
    // and read from the frame-metrics thread, so it must be volatile.
    @Volatile
    private var hintSession: Any? = null // Type erased for API compatibility
    private val isSessionInitialized = AtomicBoolean(false)

    // Current target frame duration in nanoseconds (Default: 120 FPS -> 8.333ms)
    private val currentTargetDurationNanos = AtomicLong(FRAME_DURATION_120HZ_NANOS)
    private var registeredTids: IntArray = intArrayOf(Process.myTid())
    @Volatile
    private var renderThreadTid: Int = -1

    // Dedicated background handler for thermal polling & ADPF maintenance
    private val monitorThread = HandlerThread("ADPF-Thermal-Monitor").apply { start() }
    private val monitorHandler = Handler(monitorThread.looper)

    private var powerSaveReceiver: BroadcastReceiver? = null

    // Level reported by the thermal service, before the battery-saver floor is applied. Keeping it
    // separate stops each headroom poll from resetting the published level to the raw value and the
    // mitigation pass from immediately raising it again.
    @Volatile
    private var rawThrottleLevel = ThermalThrottleLevel.NORMAL

    private val headroomPoll = object : Runnable {
        override fun run() {
            if (renderThreadTid == -1) {
                ensureRenderThreadRegistered()
            }
            updateThermalHeadroom()
            monitorHandler.postDelayed(this, HEADROOM_POLL_INTERVAL_MS)
        }
    }

    private val thermalStatusListener: PowerManager.OnThermalStatusChangedListener? by lazy {
        PowerManager.OnThermalStatusChangedListener { status ->
            evaluateThermalState(status)
        }
    }

    init {
        registerThermalListeners()
        initHintSession(targetFps = 120)
        // Applies the battery-saver floor straight away instead of waiting for the first thermal event.
        applyThermalMitigations()
    }

    private fun registerThermalListeners() {
        if (powerManager != null && thermalStatusListener != null) {
            try {
                powerManager.addThermalStatusListener(context.mainExecutor, thermalStatusListener!!)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to register OnThermalStatusChangedListener: ${e.message}")
            }
        }
        try {
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    applyThermalMitigations()
                }
            }
            powerSaveReceiver = receiver
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Throwable) {}
    }

    /**
     * Polls thermal headroom while the overlay is visible. The thermal status listener keeps working
     * in the background, so polling is only needed for the finer-grained headroom signal on screen.
     */
    fun startHeadroomMonitoring() {
        monitorHandler.removeCallbacks(headroomPoll)
        monitorHandler.post(headroomPoll)
    }

    fun stopHeadroomMonitoring() {
        monitorHandler.removeCallbacks(headroomPoll)
    }

    /**
     * Initializes the [PerformanceHintManager.Session] with the process main thread and RenderThread.
     */
    fun initHintSession(targetFps: Int = 120) {
        if (performanceHintManager == null) {
            return
        }

        val targetNanos = getFrameDurationNanosForFps(targetFps)
        currentTargetDurationNanos.set(targetNanos)

        monitorHandler.post {
            rebuildHintSessionOnMonitorThread(targetNanos)
        }
    }

    private fun ensureRenderThreadRegistered() {
        if (renderThreadTid != -1 || performanceHintManager == null) return
        val discoveredTid = findRenderThreadTid() ?: return
        renderThreadTid = discoveredTid
        rebuildHintSessionOnMonitorThread(currentTargetDurationNanos.get())
    }

    private fun rebuildHintSessionOnMonitorThread(targetNanos: Long) {
        if (performanceHintManager == null) return
        try {
            val mainTid = Process.myPid()
            val resolvedRenderTid = if (renderThreadTid != -1) renderThreadTid else (findRenderThreadTid() ?: -1)
            if (resolvedRenderTid != -1) {
                renderThreadTid = resolvedRenderTid
            }
            val tids = if (resolvedRenderTid != -1 && resolvedRenderTid != mainTid) {
                intArrayOf(mainTid, resolvedRenderTid)
            } else {
                intArrayOf(mainTid)
            }
            registeredTids = tids

            val oldSession = hintSession as? PerformanceHintManager.Session
            val session = performanceHintManager.createHintSession(tids, targetNanos)
            hintSession = session
            try {
                oldSession?.close()
            } catch (_: Throwable) {}
            isSessionInitialized.set(true)
            Log.i(TAG, "ADPF HintSession initialized with tids=${tids.contentToString()} target=${targetNanos / 1_000_000f}ms")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to create HintSession: ${e.message}")
        }
    }

    /**
     * Dynamically updates the target frame rate for the ADPF session (e.g. 144Hz, 120Hz, 90Hz, 60Hz).
     */
    fun updateTargetFps(targetFps: Int) {
        val targetNanos = getFrameDurationNanosForFps(targetFps)
        currentTargetDurationNanos.set(targetNanos)

        if (renderThreadTid == -1) {
            monitorHandler.post { ensureRenderThreadRegistered() }
        }

        val session = hintSession as? PerformanceHintManager.Session ?: return
        try {
            session.updateTargetWorkDuration(targetNanos)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to update target work duration: ${e.message}")
        }
    }

    /**
     * Reports actual work duration (CPU + GPU) for a completed frame to the OS governor.
     * This directly prevents thermal throttling while guaranteeing frame deadlines.
     */
    fun reportActualWorkDuration(actualDurationNanos: Long) {
        if (actualDurationNanos > 0) {
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
        if (totalDurationNanos > 0) {
            if (renderThreadTid == -1) {
                monitorHandler.post { ensureRenderThreadRegistered() }
            }
            val session = hintSession as? PerformanceHintManager.Session ?: return
            try {
                // WorkDuration and Session.reportActualWorkDuration(WorkDuration) were added in API 35. Guarding
                // on API 34 would throw NoClassDefFoundError on every frame report on Android 14.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && gpuDurationNanos > 0L) {
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
        if (powerManager != null) {
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
            headroom >= 1.25f -> ThermalThrottleLevel.CRITICAL
            headroom >= 1.00f -> ThermalThrottleLevel.SEVERE
            headroom >= 0.85f -> ThermalThrottleLevel.MODERATE
            headroom >= 0.75f -> ThermalThrottleLevel.LIGHT
            else -> ThermalThrottleLevel.NORMAL
        }
        if (rawThrottleLevel != newLevel) {
            rawThrottleLevel = newLevel
            applyThermalMitigations()
        }
    }

    private fun evaluateThermalState(status: Int) {
        val newLevel = run {
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
        }
        if (rawThrottleLevel != newLevel) {
            rawThrottleLevel = newLevel
            applyThermalMitigations()
        }
    }

    @Synchronized
    private fun applyThermalMitigations() {
        val level = rawThrottleLevel
        val effectiveLevel = if (powerManager?.isPowerSaveMode == true && level < ThermalThrottleLevel.SEVERE) {
            ThermalThrottleLevel.SEVERE
        } else {
            level
        }
        if (_thermalThrottleLevel.value == effectiveLevel) return
        _thermalThrottleLevel.value = effectiveLevel
        Log.i(TAG, "Thermal mitigation transition: $effectiveLevel (effective, requested=$level, headroom=${_thermalHeadroom.value})")
        when (effectiveLevel) {
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

    fun release() {
        if (powerManager != null && thermalStatusListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalStatusListener!!)
            } catch (_: Throwable) {}
        }
        powerSaveReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Throwable) {}
            powerSaveReceiver = null
        }
        val session = hintSession as? PerformanceHintManager.Session
        try {
            session?.close()
        } catch (_: Throwable) {}
        hintSession = null
        monitorThread.quitSafely()
    }

    companion object {
        private const val TAG = "ADPFThermalManager"
        private const val HEADROOM_POLL_INTERVAL_MS = 10_500L

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

        /** The app-wide instance, for places without injection. */
        fun getInstance(context: Context): ADPFThermalManager = context.appEntryPoint.adpfThermalManager()
    }
}
