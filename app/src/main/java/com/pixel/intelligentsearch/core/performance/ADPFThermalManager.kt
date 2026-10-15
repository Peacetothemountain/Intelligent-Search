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
import kotlin.math.abs

/**
 * Thermal state and frame-time hints (Android Dynamic Performance Framework).
 *
 * - Publishes a [ThermalThrottleLevel] from the thermal status listener and, while the overlay is visible,
 *   thermal headroom polling. Battery saver counts as at least [ThermalThrottleLevel.SEVERE]. Screens read it
 *   to drop blur and animated backgrounds.
 * - Keeps a [PerformanceHintManager.Session] for the main and render threads, reporting each frame's real
 *   duration against that frame's deadline (which follows the display's current refresh rate), so the CPU
 *   governor clocks for the panel actually in use. Under heat the target is relaxed to 90 or 60 fps.
 *
 * Engineered by NG Designs.
 */
@Singleton
class ADPFThermalManager @Inject constructor(@param:ApplicationContext private val context: Context) {

    enum class ThermalThrottleLevel {
        NORMAL,   // Full effects, frame target = display deadline
        LIGHT,    // Blur capped at 24 px
        MODERATE, // Blur capped at 12 px, frame target no faster than 90 fps
        SEVERE,   // No blur or animated backgrounds, frame target no faster than 60 fps
        CRITICAL  // As SEVERE
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val performanceHintManager = context.getSystemService(PerformanceHintManager::class.java)

    private val _thermalThrottleLevel = MutableStateFlow(ThermalThrottleLevel.NORMAL)
    val thermalThrottleLevel: StateFlow<ThermalThrottleLevel> = _thermalThrottleLevel.asStateFlow()

    // Written on the monitor thread and read from the frame-metrics thread.
    @Volatile
    private var hintSession: PerformanceHintManager.Session? = null
    @Volatile
    private var targetDurationNanos = FRAME_60_FPS_NANOS
    /** Slowest-allowed frame target under heat: frames are never asked to be faster than this. 0 = no limit. */
    @Volatile
    private var thermalMinDurationNanos = 0L
    @Volatile
    private var renderThreadTid: Int = -1

    // Dedicated background handler for thermal polling and hint-session maintenance.
    private val monitorThread = HandlerThread("ADPF-Thermal-Monitor").apply { start() }
    private val monitorHandler = Handler(monitorThread.looper)

    // Level reported by the thermal service, before the battery-saver floor is applied. Keeping it
    // separate stops each headroom poll from resetting the published level to the raw value and the
    // mitigation pass from immediately raising it again.
    @Volatile
    private var rawThrottleLevel = ThermalThrottleLevel.NORMAL

    private val headroomPoll = object : Runnable {
        override fun run() {
            if (renderThreadTid == -1) ensureRenderThreadRegistered()
            updateThermalHeadroom()
            monitorHandler.postDelayed(this, HEADROOM_POLL_INTERVAL_MS)
        }
    }

    private val thermalStatusListener = PowerManager.OnThermalStatusChangedListener { status ->
        evaluateThermalState(status)
    }

    init {
        registerThermalListeners()
        monitorHandler.post { rebuildHintSession() }
        // Applies the battery-saver floor straight away instead of waiting for the first thermal event.
        applyThermalMitigations()
    }

    private fun registerThermalListeners() {
        try {
            powerManager?.addThermalStatusListener(context.mainExecutor, thermalStatusListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register OnThermalStatusChangedListener: ${e.message}")
        }
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    applyThermalMitigations()
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
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

    private fun ensureRenderThreadRegistered() {
        if (renderThreadTid != -1 || performanceHintManager == null) return
        renderThreadTid = findRenderThreadTid() ?: return
        rebuildHintSession()
    }

    /** (Re)creates the hint session for the main thread plus the render thread once known. Monitor thread only. */
    private fun rebuildHintSession() {
        val manager = performanceHintManager ?: return
        try {
            if (renderThreadTid == -1) findRenderThreadTid()?.let { renderThreadTid = it }
            val mainTid = Process.myPid() // the main thread's tid equals the pid
            val tids = if (renderThreadTid != -1 && renderThreadTid != mainTid) intArrayOf(mainTid, renderThreadTid) else intArrayOf(mainTid)
            val oldSession = hintSession
            hintSession = manager.createHintSession(tids, targetDurationNanos)
            try {
                oldSession?.close()
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to create HintSession: ${e.message}")
        }
    }

    /**
     * Reports one frame. [deadlineNanos] is the time the frame had (it changes with the refresh rate); the
     * session's target follows it, but never faster than the current thermal limit allows.
     */
    fun reportFrameMetrics(totalDurationNanos: Long, gpuDurationNanos: Long = 0L, deadlineNanos: Long = 0L) {
        if (totalDurationNanos <= 0) return
        if (renderThreadTid == -1) monitorHandler.post { ensureRenderThreadRegistered() }
        val session = hintSession ?: return
        if (deadlineNanos > 0) {
            val target = maxOf(deadlineNanos, thermalMinDurationNanos)
            // Ignore jitter; only real refresh-rate or thermal changes move the target.
            if (abs(target - targetDurationNanos) > targetDurationNanos / 20) {
                targetDurationNanos = target
                try {
                    session.updateTargetWorkDuration(target)
                } catch (_: Throwable) {}
            }
        }
        try {
            // WorkDuration and Session.reportActualWorkDuration(WorkDuration) were added in API 35. Guarding
            // on API 34 would throw NoClassDefFoundError on every frame report on Android 14.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && gpuDurationNanos > 0L) {
                session.reportActualWorkDuration(android.os.WorkDuration().apply {
                    actualTotalDurationNanos = totalDurationNanos
                    actualCpuDurationNanos = (totalDurationNanos - gpuDurationNanos).coerceAtLeast(0L)
                    actualGpuDurationNanos = gpuDurationNanos
                })
            } else {
                session.reportActualWorkDuration(totalDurationNanos)
            }
        } catch (_: Throwable) {
            try {
                session.reportActualWorkDuration(totalDurationNanos)
            } catch (_: Throwable) {}
        }
    }

    /** Asks the scheduler to favour efficiency over speed for the session's threads (Android 15+). */
    private fun setPreferPowerEfficiency(preferEfficiency: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                hintSession?.setPreferPowerEfficiency(preferEfficiency)
            } catch (_: Throwable) {}
        }
    }

    /** Finds this process's RenderThread by name in `/proc/self/task`. */
    private fun findRenderThreadTid(): Int? {
        return try {
            File("/proc/self/task").listFiles()?.firstNotNullOfOrNull { task ->
                val tid = task.name.toIntOrNull()
                val comm = File(task, "comm")
                if (tid != null && comm.exists() && comm.readText().trim() == "RenderThread") tid else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun updateThermalHeadroom() {
        try {
            val headroom = powerManager?.getThermalHeadroom(10) ?: return
            if (!headroom.isNaN() && headroom >= 0.0f) evaluateThermalFromHeadroom(headroom)
        } catch (_: Throwable) {}
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
        val newLevel = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalThrottleLevel.NORMAL
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalThrottleLevel.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalThrottleLevel.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalThrottleLevel.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalThrottleLevel.CRITICAL
            else -> ThermalThrottleLevel.NORMAL
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
        Log.i(TAG, "Thermal mitigation transition: $effectiveLevel (requested=$level)")
        thermalMinDurationNanos = when (effectiveLevel) {
            ThermalThrottleLevel.NORMAL, ThermalThrottleLevel.LIGHT -> 0L
            ThermalThrottleLevel.MODERATE -> FRAME_90_FPS_NANOS
            ThermalThrottleLevel.SEVERE, ThermalThrottleLevel.CRITICAL -> FRAME_60_FPS_NANOS
        }
        setPreferPowerEfficiency(effectiveLevel >= ThermalThrottleLevel.MODERATE)
    }

    /** Returns the blur radius to use in pixels, scaled down during thermal events. */
    fun getRecommendedBlurRadius(requestedRadius: Float): Float {
        return when (_thermalThrottleLevel.value) {
            ThermalThrottleLevel.NORMAL -> requestedRadius
            ThermalThrottleLevel.LIGHT -> requestedRadius.coerceAtMost(24f)
            ThermalThrottleLevel.MODERATE -> requestedRadius.coerceAtMost(12f)
            ThermalThrottleLevel.SEVERE,
            ThermalThrottleLevel.CRITICAL -> 0f
        }
    }

    companion object {
        private const val TAG = "ADPFThermalManager"
        private const val HEADROOM_POLL_INTERVAL_MS = 10_500L
        private const val FRAME_90_FPS_NANOS = 11_111_111L
        private const val FRAME_60_FPS_NANOS = 16_666_666L

        /** The app-wide instance, for places without injection. */
        fun getInstance(context: Context): ADPFThermalManager = context.appEntryPoint.adpfThermalManager()
    }
}
