package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import com.pixel.intelligentsearch.App
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*

data class ProcessRamEntry(
    val name: String,
    val ramMb: Float
)

private data class MemorySnapshot(
    val totalGb: Double,
    val usedGb: Double,
    val availGb: Double,
    val pct: Int,
    val zRamUsedGb: Double,
    val zRamTotalGb: Double,
    val cachedGb: Double,
    val isLowMem: Boolean,
    val thresholdMb: Int,
    val memPressure: String,
    val processes: List<ProcessRamEntry>,
    val physicalGb: Int = 12
)

private data class BatteryTelemetrySnapshot(
    val level: Int,
    val temp: Float,
    val volt: Float,
    val watts: Float,
    val health: String,
    val healthPct: Int,
    val cycles: Int,
    val technology: String,
    val thermalStatus: String,
    val isCharging: Boolean,
    val chargingRateStr: String,
    val timeEstimateStr: String
)

@Composable
fun BatteryAndMemoryDiagnosticsPage(context: Context) {
    var batteryLevel by remember { mutableIntStateOf(0) }
    var batteryTemp by remember { mutableFloatStateOf(0f) }
    var batteryVolt by remember { mutableFloatStateOf(0f) }
    var batteryWatts by remember { mutableFloatStateOf(0f) }
    var batteryHealth by remember { mutableStateOf("Unknown") }
    var batteryHealthPct by remember { mutableIntStateOf(-1) }
    var batteryCycles by remember { mutableIntStateOf(-1) }
    var batteryTechnology by remember { mutableStateOf("Li-ion") }
    var thermalStatus by remember { mutableStateOf("Optimal") }
    var isCharging by remember { mutableStateOf(false) }
    var chargingRateStr by remember { mutableStateOf("Calculating...") }
    var timeEstimateStr by remember { mutableStateOf("Calculating...") }

    var totalRamGb by remember { mutableStateOf("0.0") }
    var usedRamGb by remember { mutableStateOf("0.0") }
    var usedRamPercent by remember { mutableIntStateOf(0) }
    var availableRamGb by remember { mutableStateOf("0.0") }
    val initialPhysicalRam = remember(context) {
        val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)
        val totGb = memInfo.totalMem.toDouble() / 1_000_000_000.0
        when {
            totGb <= 4.5 -> 4
            totGb <= 6.5 -> 6
            totGb <= 8.5 -> 8
            totGb <= 12.8 -> 12
            totGb <= 16.8 -> 16
            else -> 24
        }
    }
    var physicalRamGb by remember { mutableIntStateOf(initialPhysicalRam) }
    var zRamUsedGb by remember { mutableStateOf("0.0") }
    var zRamTotalGb by remember { mutableStateOf("0.0") }
    var cachedRamGb by remember { mutableStateOf("0.0") }
    var isLowMemory by remember { mutableStateOf(false) }
    var memoryPressure by remember { mutableStateOf("Optimal") }
    var lmkThresholdMb by remember { mutableIntStateOf(0) }
    var topProcesses by remember { mutableStateOf<List<ProcessRamEntry>>(emptyList()) }

    val actMgr = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager }
    val bm = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager }

    LaunchedEffect(Unit) {
        val appContext = context.applicationContext ?: context
        while (true) {
            try {
                val (bSnap, mSnap) = withContext(Dispatchers.IO) {
                    val ifilter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val batteryStatus = appContext.registerReceiver(null, ifilter)
                    val lvl = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val bLevel = if (lvl >= 0 && scale > 0) (lvl * 100 / scale) else 0

                    val t = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                    val bTemp = t / 10f

                    val v = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                    val bVolt = v / 1000f

                    val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val bIsCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL

                    val plugged = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0

                    val h = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN
                    val bHealth = when (h) {
                        android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                        android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                        android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                        android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                        android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
                        android.os.BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                        8 -> "Dock Defender"
                        11 -> "Optimized"
                        android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN -> "Unknown"
                        else -> "Normal"
                    }

                    // Battery cycle count (Android 14+ / API 34+ property & sysfs fallback)
                    var cycles = -1
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        try {
                            cycles = batteryStatus?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
                        } catch (_: Throwable) {}
                        if (cycles <= 0) {
                            try {
                                cycles = bm?.getIntProperty(8 /* BATTERY_PROPERTY_CYCLE_COUNT */) ?: -1
                            } catch (_: Throwable) {}
                        }
                    }
                    if (cycles <= 0) {
                        val cycleFiles = listOf(
                            "/sys/class/power_supply/battery/cycle_count",
                            "/sys/class/power_supply/max77779fg/cycle_count",
                            "/sys/class/power_supply/maxfg/cycle_count",
                            "/sys/class/power_supply/bms/battery_cycle"
                        )
                        for (cf in cycleFiles) {
                            try {
                                val file = java.io.File(cf)
                                if (file.exists() && file.canRead()) {
                                    val parsed = file.readText().trim().toIntOrNull()
                                    if (parsed != null && parsed >= 0) {
                                        cycles = parsed
                                        break
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }

                    val bTechnology = batteryStatus?.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "Li-ion"

                    // Thermals
                    val pmPower = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val osThermal = try {
                        when (pmPower?.currentThermalStatus) {
                            android.os.PowerManager.THERMAL_STATUS_NONE -> "Cool"
                            android.os.PowerManager.THERMAL_STATUS_LIGHT -> "Optimal"
                            android.os.PowerManager.THERMAL_STATUS_MODERATE -> "Warm"
                            android.os.PowerManager.THERMAL_STATUS_SEVERE -> "Hot"
                            android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
                            android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
                            android.os.PowerManager.THERMAL_STATUS_SHUTDOWN -> "Overheat"
                            else -> null
                        }
                    } catch (_: Exception) { null }

                    val bThermalStatus = osThermal ?: when {
                        bTemp < 20f -> "Cool"
                        bTemp <= 35f -> "Optimal"
                        bTemp <= 41f -> "Warm"
                        else -> "Overheat"
                    }

                    // Health Wear / State of Health (SoH %) from actual fuel gauge or design capacity
                    var healthPct = -1
                    val chargeFullPaths = listOf(
                        "/sys/class/power_supply/battery/charge_full" to "/sys/class/power_supply/battery/charge_full_design",
                        "/sys/class/power_supply/max77779fg/charge_full" to "/sys/class/power_supply/max77779fg/charge_full_design",
                        "/sys/class/power_supply/maxfg/charge_full" to "/sys/class/power_supply/maxfg/charge_full_design",
                        "/sys/class/power_supply/bms/charge_full" to "/sys/class/power_supply/bms/charge_full_design"
                    )
                    for ((fullPath, designPath) in chargeFullPaths) {
                        try {
                            val f1 = java.io.File(fullPath)
                            val f2 = java.io.File(designPath)
                            if (f1.exists() && f1.canRead() && f2.exists() && f2.canRead()) {
                                val fullVal = f1.readText().trim().toDoubleOrNull() ?: 0.0
                                val designVal = f2.readText().trim().toDoubleOrNull() ?: 0.0
                                if (fullVal > 1000.0 && designVal > 1000.0) {
                                    healthPct = ((fullVal / designVal) * 100.0).toInt().coerceIn(30, 100)
                                    break
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                    if (healthPct <= 0) {
                        healthPct = -1
                    }

                    // Accurate Current & Power (Watts)
                    val rawCurrentNow = try { bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Throwable) { null } ?: Integer.MIN_VALUE
                    val rawCurrentAvg = try { bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) } catch (_: Throwable) { null } ?: Integer.MIN_VALUE

                    val rawCurrent = when {
                        rawCurrentNow != Integer.MIN_VALUE && rawCurrentNow != 0 -> rawCurrentNow
                        rawCurrentAvg != Integer.MIN_VALUE && rawCurrentAvg != 0 -> rawCurrentAvg
                        else -> listOf(
                            "/sys/class/power_supply/battery/current_now",
                            "/sys/class/power_supply/max77779fg/current_now",
                            "/sys/class/power_supply/battery/current_avg"
                        ).firstNotNullOfOrNull { path ->
                            try {
                                val f = java.io.File(path)
                                if (f.exists() && f.canRead()) f.readText().trim().toIntOrNull() else null
                            } catch (_: Throwable) { null }
                        } ?: 0
                    }
                    val absCurrent = kotlin.math.abs(rawCurrent).toFloat()
                    val currentMa = when {
                        absCurrent > 1000f -> (absCurrent / 1000f).coerceAtMost(12000f)
                        absCurrent > 0f -> absCurrent
                        else -> 0f
                    }
                    val bWatts = if (bVolt > 0f && currentMa > 0f) (bVolt * currentMa) / 1000f else 0f

                    // Battery Design Capacity Resolution Engine
                    val sysfsCapacity = listOf(
                        "/sys/class/power_supply/battery/charge_full_design",
                        "/sys/class/power_supply/max77779fg/charge_full_design",
                        "/sys/class/power_supply/maxfg/charge_full_design",
                        "/sys/class/power_supply/bms/charge_full_design"
                    ).firstNotNullOfOrNull { path ->
                        try {
                            val f = java.io.File(path)
                            if (f.exists() && f.canRead()) {
                                val v = f.readText().trim().toFloatOrNull()
                                if (v != null && v > 1000f) v / 1000f else v
                            } else null
                        } catch (_: Throwable) { null }
                    }

                    val resourceCapacity = try {
                        val resId = appContext.resources.getIdentifier("config_batteryCapacity", "dimen", "android")
                        if (resId > 0) appContext.resources.getDimension(resId) else null
                    } catch (_: Throwable) { null }

                    // Public sources only: the hidden PowerProfile class is blocked for apps targeting new SDKs.
                    val designMah = sysfsCapacity?.takeIf { it in 1500f..10000f }
                        ?: resourceCapacity?.takeIf { it in 1500f..10000f }
                        ?: 5000f

                    val chargeCounter = try {
                        val cc = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1
                        if (cc != Integer.MIN_VALUE && cc > 0) cc / 1000f else -1f
                    } catch (_: Throwable) { -1f }

                    val totalCapacityMah = if (bLevel > 5 && chargeCounter > 0f) {
                        (chargeCounter / (bLevel / 100f).coerceAtLeast(0.05f)).coerceIn(2000f, 10000f)
                    } else {
                        designMah
                    }
                    val currentChargeMah = if (chargeCounter > 0f) chargeCounter else (bLevel / 100f * totalCapacityMah)

                    val bChargingRateStr: String
                    val bTimeEstimateStr: String

                    if (bIsCharging) {
                        val pluggedType = when (plugged) {
                            android.os.BatteryManager.BATTERY_PLUGGED_AC -> if (bWatts >= 15f) "Rapid (AC)" else "AC"
                            android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                            android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                            android.os.BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
                            else -> "Charger"
                        }
                        bChargingRateStr = if (bWatts > 0.05f) {
                            "Charging ($pluggedType) · ${String.format(java.util.Locale.US, "%.1f", bWatts)}W (${currentMa.toInt()}mA)"
                        } else {
                            "Charging ($pluggedType)"
                        }

                        if (bLevel >= 100) {
                            bTimeEstimateStr = "Fully Charged"
                        } else {
                            var sysTimeRemainingMs = -1L
                            try {
                                sysTimeRemainingMs = bm?.computeChargeTimeRemaining() ?: -1L
                            } catch (_: Throwable) {}
                            if (sysTimeRemainingMs > 60_000L) {
                                val totalMinutes = (sysTimeRemainingMs / 60_000L).toInt()
                                val hours = totalMinutes / 60
                                val mins = totalMinutes % 60
                                bTimeEstimateStr = if (hours > 0) "${hours}h ${mins}m until full" else "${mins}m until full"
                            } else {
                                val remainingMah = ((100 - bLevel) / 100f * totalCapacityMah).coerceAtLeast(0f)
                                if (currentMa > 50f) {
                                    val estHours = remainingMah / currentMa.coerceAtLeast(1f)
                                    val totalMinutes = (estHours * 60f).toInt().coerceIn(1, 1440)
                                    val hours = totalMinutes / 60
                                    val mins = totalMinutes % 60
                                    bTimeEstimateStr = if (hours > 0) "${hours}h ${mins}m until full" else "${mins}m until full"
                                } else {
                                    bTimeEstimateStr = "Charging..."
                                }
                            }
                        }
                    } else {
                        bChargingRateStr = if (bWatts > 0.05f) {
                            "Discharge · ${String.format(java.util.Locale.US, "%.1f", bWatts)}W (${currentMa.toInt()}mA)"
                        } else {
                            "Discharging"
                        }

                        if (currentMa > 50f) {
                            val estHours = (currentChargeMah / currentMa.coerceAtLeast(1f)).coerceIn(0.5f, 96f)
                            val totalMinutes = (estHours * 60f).toInt()
                            val hours = totalMinutes / 60
                            val mins = totalMinutes % 60
                            bTimeEstimateStr = if (hours > 0) "${hours}h ${mins}m until depleted" else "${mins}m until depleted"
                        } else {
                            val estHours = (bLevel / 100f * 24f).coerceAtLeast(1f)
                            val hours = estHours.toInt()
                            bTimeEstimateStr = "~${hours}h until depleted"
                        }
                    }

                    val batterySnap = BatteryTelemetrySnapshot(
                        level = bLevel,
                        temp = bTemp,
                        volt = bVolt,
                        watts = bWatts,
                        health = bHealth,
                        healthPct = healthPct,
                        cycles = cycles,
                        technology = bTechnology,
                        thermalStatus = bThermalStatus,
                        isCharging = bIsCharging,
                        chargingRateStr = bChargingRateStr,
                        timeEstimateStr = bTimeEstimateStr
                    )

                    // Memory Snapshot on IO
                    val memInfo = android.app.ActivityManager.MemoryInfo()
                    actMgr?.getMemoryInfo(memInfo)
                    var totalBytes = memInfo.totalMem
                    var availBytes = memInfo.availMem

                    var procTotalKb = -1L
                    var procAvailKb = -1L
                    var procMemFreeKb = 0L
                    var procBuffersKb = 0L
                    var procCachedKb = 0L
                    var procSwapTotalKb = 0L
                    var procSwapFreeKb = 0L
                    var procAnonPagesKb = 0L
                    var procInactiveAnonKb = 0L
                    var procActiveAnonKb = 0L
                    var procKReclaimableKb = 0L
                    var procSlabKb = 0L
                    var procKernelStackKb = 0L
                    var procPageTablesKb = 0L
                    var procZramKb = 0L
                    var procShmemKb = 0L

                    try {
                        val memInfoFile = java.io.File("/proc/meminfo")
                        if (memInfoFile.exists() && memInfoFile.canRead()) {
                            memInfoFile.bufferedReader().use { reader ->
                                reader.forEachLine { l ->
                                    when {
                                        l.startsWith("MemTotal:") -> procTotalKb = l.substringAfter("MemTotal:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: -1L
                                        l.startsWith("MemFree:") -> procMemFreeKb = l.substringAfter("MemFree:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("MemAvailable:") -> procAvailKb = l.substringAfter("MemAvailable:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: -1L
                                        l.startsWith("Buffers:") -> procBuffersKb = l.substringAfter("Buffers:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("Cached:") -> procCachedKb = l.substringAfter("Cached:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("SwapTotal:") -> procSwapTotalKb = l.substringAfter("SwapTotal:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("SwapFree:") -> procSwapFreeKb = l.substringAfter("SwapFree:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("AnonPages:") -> procAnonPagesKb = l.substringAfter("AnonPages:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("Inactive(anon):") -> procInactiveAnonKb = l.substringAfter("Inactive(anon):").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("Active(anon):") -> procActiveAnonKb = l.substringAfter("Active(anon):").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("KReclaimable:") -> procKReclaimableKb = l.substringAfter("KReclaimable:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("Slab:") -> procSlabKb = l.substringAfter("Slab:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("KernelStack:") -> procKernelStackKb = l.substringAfter("KernelStack:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("PageTables:") -> procPageTablesKb = l.substringAfter("PageTables:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("Zram:") -> procZramKb = l.substringAfter("Zram:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                        l.startsWith("Shmem:") -> procShmemKb = l.substringAfter("Shmem:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}

                    if (procTotalKb > 0) totalBytes = procTotalKb * 1024L

                    // Modern Android Runtime (Android 14-17 / Canary QPR3) True RAM Accounting:
                    // In Android Settings, "Free RAM" comprises:
                    // 1. Raw MemFree
                    // 2. Cached Kernel pages (Buffers + Cached + KReclaimable - Shmem)
                    // 3. Cached background applications (Inactive Anon + background swap) which the OS evicts on demand.
                    // "Used RAM" is Total RAM minus this true available headroom.
                    val kernelReclaimableKb = (procMemFreeKb + procCachedKb + procBuffersKb + procKReclaimableKb - procShmemKb).coerceAtLeast(0L)
                    val cachedAppsKb = procInactiveAnonKb + if (procZramKb > 0) (procZramKb * 0.9).toLong() else (((procSwapTotalKb - procSwapFreeKb).coerceAtLeast(0L)) * 0.25).toLong()

                    val trueAvailableKb = if (kernelReclaimableKb > 0) {
                        (kernelReclaimableKb + cachedAppsKb).coerceAtMost(if (procTotalKb > 0) procTotalKb else Long.MAX_VALUE)
                    } else {
                        // Fallback if /proc/meminfo is inaccessible: ActivityManager availMem only contains kernel free/cache.
                        (memInfo.availMem / 1024L) + (memInfo.totalMem / 1024L * 0.14).toLong()
                    }

                    val trueAvailBytes = trueAvailableKb * 1024L
                    val trueUsedBytes = (totalBytes - trueAvailBytes).coerceAtLeast(0L)
                    val calculatedPct = if (totalBytes > 0) ((trueUsedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else 0

                    // Android System Settings uses decimal gigabytes (10^9) for marketing specs & memory settings display
                    val totGb = totalBytes.toDouble() / 1_000_000_000.0
                    val usdGb = trueUsedBytes.toDouble() / 1_000_000_000.0
                    val avlGb = trueAvailBytes.toDouble() / 1_000_000_000.0

                    val physGb = when {
                        totGb <= 4.5 -> 4
                        totGb <= 6.5 -> 6
                        totGb <= 8.5 -> 8
                        totGb <= 12.8 -> 12
                        totGb <= 16.8 -> 16
                        else -> 24
                    }

                    val swapUsedKb = (procSwapTotalKb - procSwapFreeKb).coerceAtLeast(0L)
                    // /proc/meminfo reports kB; convert to bytes before scaling to decimal GB.
                    val zRamUsedGb = swapUsedKb * 1024.0 / 1_000_000_000.0
                    val zRamTotGb = procSwapTotalKb * 1024.0 / 1_000_000_000.0
                    val cacheGb = (procBuffersKb + procCachedKb + procKReclaimableKb) * 1024.0 / 1_000_000_000.0

                    val isLow = memInfo.lowMemory
                    val threshMb = (memInfo.threshold / (1024L * 1024L)).toInt()

                    val freePct = if (totalBytes > 0) ((trueAvailBytes.toDouble() / totalBytes) * 100).toInt() else 30
                    val pressure = when {
                        isLow || freePct < 15 -> "Critical"
                        freePct < 25 -> "High Load"
                        freePct < 40 -> "Moderate"
                        else -> "Optimal"
                    }

                    val rt = Runtime.getRuntime()
                    // Decimal MB (10^6 bytes) to match the GB figures above; /proc/meminfo kB are KiB.
                    val myHeapMb = ((rt.totalMemory() - rt.freeMemory()) + android.os.Debug.getNativeHeapAllocatedSize()) / 1_000_000f

                    val activeAppsMb = (procActiveAnonKb.takeIf { it > 0 } ?: (procAnonPagesKb - procInactiveAnonKb).coerceAtLeast(0L)) * 1.024f / 1000f
                    val sysCacheMb = (procCachedKb + procBuffersKb + procKReclaimableKb) * 1.024f / 1000f
                    val kernelMb = (procSlabKb + procKernelStackKb + procPageTablesKb) * 1.024f / 1000f

                    val topProcessesList = listOf(
                        ProcessRamEntry(name = "Active Apps", ramMb = activeAppsMb),
                        ProcessRamEntry(name = "System Cache", ramMb = sysCacheMb),
                        ProcessRamEntry(name = "Kernel & OS", ramMb = kernelMb),
                        ProcessRamEntry(name = "App Footprint", ramMb = myHeapMb)
                    )

                    val memSnap = MemorySnapshot(
                        totalGb = totGb,
                        usedGb = usdGb,
                        availGb = avlGb,
                        pct = calculatedPct,
                        zRamUsedGb = zRamUsedGb,
                        zRamTotalGb = zRamTotGb,
                        cachedGb = cacheGb,
                        isLowMem = isLow,
                        thresholdMb = threshMb,
                        memPressure = pressure,
                        processes = topProcessesList,
                        physicalGb = physGb
                    )

                    Pair(batterySnap, memSnap)
                }

                // Update Compose state on Main thread
                batteryLevel = bSnap.level
                batteryTemp = bSnap.temp
                batteryVolt = bSnap.volt
                batteryWatts = bSnap.watts
                batteryHealth = bSnap.health
                batteryHealthPct = bSnap.healthPct
                batteryCycles = bSnap.cycles
                batteryTechnology = bSnap.technology
                thermalStatus = bSnap.thermalStatus
                isCharging = bSnap.isCharging
                chargingRateStr = bSnap.chargingRateStr
                timeEstimateStr = bSnap.timeEstimateStr

                totalRamGb = String.format(java.util.Locale.US, "%.1f", mSnap.totalGb)
                usedRamGb = String.format(java.util.Locale.US, "%.1f", mSnap.usedGb)
                usedRamPercent = mSnap.pct
                availableRamGb = String.format(java.util.Locale.US, "%.1f", mSnap.availGb)
                physicalRamGb = mSnap.physicalGb
                zRamUsedGb = String.format(java.util.Locale.US, "%.1f", mSnap.zRamUsedGb)
                zRamTotalGb = String.format(java.util.Locale.US, "%.1f", mSnap.zRamTotalGb)
                cachedRamGb = String.format(java.util.Locale.US, "%.1f", mSnap.cachedGb)
                isLowMemory = mSnap.isLowMem
                memoryPressure = mSnap.memPressure
                lmkThresholdMb = mSnap.thresholdMb
                topProcesses = mSnap.processes
            } catch (_: Throwable) {}

            kotlinx.coroutines.delay(2500)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "diagPulseTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "diagWavePhase"
    )
    val pointPulse by infiniteTransition.animateFloat(
        initialValue = 3.dp.value,
        targetValue = 5.5.dp.value,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pointPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- TOP CARD: Battery Health ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 176.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Battery Health",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (batteryHealthPct > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "$batteryHealthPct% Health",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = if (batteryHealth.isNotBlank() && batteryHealth != "Unknown") batteryHealth else "Hardware reporting unavailable",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 86.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val scheme = MaterialTheme.colorScheme
                    // Lighter Material You accented color from the user's dynamic palette (tone ~65: balanced between light & dark, never yellowish-white)
                    val waveAccentColor = remember(scheme, isDark) {
                        if (isDark) {
                            androidx.compose.ui.graphics.lerp(scheme.primary, scheme.primaryContainer, 0.35f)
                        } else {
                            androidx.compose.ui.graphics.lerp(scheme.primary, scheme.primaryContainer, 0.50f)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(86.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val fillHeight = (h * (batteryLevel / 100f).coerceIn(0.05f, 1f))
                            val baseWaterY = h - fillHeight

                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, h)
                                lineTo(0f, baseWaterY)
                                for (x in 0..w.toInt() step 4) {
                                    val xf = x.toFloat()
                                    val normX = xf / w
                                    val waveOffset = kotlin.math.sin(normX * 6.28 + wavePhase) * 3.dp.toPx()
                                    lineTo(xf, baseWaterY + waveOffset.toFloat())
                                }
                                lineTo(w, h)
                                close()
                            }
                            drawPath(
                                path = path,
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(waveAccentColor.copy(alpha = 0.85f), waveAccentColor)
                                )
                            )
                        }
                        Text(
                            text = "$batteryLevel%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color.White else scheme.onPrimaryContainer
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = chargingRateStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = timeEstimateStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", batteryTemp)} °C / ${String.format(java.util.Locale.US, "%.0f", batteryTemp * 1.8f + 32f)} °F ($thermalStatus)  •  ${String.format(java.util.Locale.US, "%.2f", batteryVolt)} V",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                // Diagnostic Metric Chips for Battery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val chipBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

                    // Cycle count chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = chipBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Cycles",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (batteryCycles >= 0) "$batteryCycles" else "N/A",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Live Power chip (Watts)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = chipBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Power",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", batteryWatts)}W",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Thermal chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = chipBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Thermals",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = thermalStatus,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (thermalStatus) {
                                    "Overheat", "Emergency", "Critical", "Hot" -> MaterialTheme.colorScheme.error
                                    "Warm" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }

                    // Chemistry chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = chipBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Type",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = batteryTechnology,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // --- BOTTOM CARD: System RAM Usage (Live Graph on Left, RAM Usage Breakdown on Right) ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 176.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "System RAM",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pressureColor = when (memoryPressure) {
                            "Critical" -> MaterialTheme.colorScheme.error
                            "High Load" -> MaterialTheme.colorScheme.errorContainer
                            "Moderate" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = pressureColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = memoryPressure,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = pressureColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${usedRamGb}G / ${totalRamGb}G (${physicalRamGb}G)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                val materialAppColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.inversePrimary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 86.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Live App RAM Usage Distribution Graph
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val outlineColor = MaterialTheme.colorScheme.outlineVariant
                    val surfaceColor = MaterialTheme.colorScheme.surface

                    val ceilingMb = if (topProcesses.isNotEmpty()) {
                        val maxVal = (topProcesses.maxOfOrNull { it.ramMb } ?: 500f).coerceAtLeast(100f)
                        (maxVal * 1.35f).toInt()
                    } else 500

                    Box(
                        modifier = Modifier
                            .weight(1.25f)
                            .height(86.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val gridDashEffect = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) }
                        val stemDashEffect = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) }
                        val gradientBrush = remember(primaryColor) {
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(primaryColor.copy(alpha = 0.32f), primaryColor.copy(alpha = 0.04f), Color.Transparent)
                            )
                        }

                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)
                        ) {
                            val w = size.width
                            val h = size.height

                            // Horizontal dashed grid lines
                            val gridLines = 3
                            for (g in 1..gridLines) {
                                val gy = h * (g.toFloat() / (gridLines + 1))
                                drawLine(
                                    color = outlineColor.copy(alpha = 0.25f),
                                    start = androidx.compose.ui.geometry.Offset(0f, gy),
                                    end = androidx.compose.ui.geometry.Offset(w, gy),
                                    strokeWidth = 0.8.dp.toPx(),
                                    pathEffect = gridDashEffect
                                )
                            }

                            if (topProcesses.isNotEmpty()) {
                                val n = topProcesses.size
                                val floatCeiling = ceilingMb.toFloat()

                                // Calculate (X, Y) coordinate for each app's peak based on its actual RAM
                                val appPoints = topProcesses.mapIndexed { i, proc ->
                                    val px = if (n > 1) {
                                        val margin = w * 0.12f
                                        margin + (i.toFloat() / (n - 1)) * (w - 2f * margin)
                                    } else {
                                        w * 0.5f
                                    }
                                    val normY = (proc.ramMb / floatCeiling).coerceIn(0.15f, 0.88f)
                                    val breathe = kotlin.math.sin(wavePhase + i * 1.4f) * 2.5.dp.toPx()
                                    val py = (h - (h * normY) + breathe).coerceIn(4.dp.toPx(), h - 8.dp.toPx())
                                    androidx.compose.ui.geometry.Offset(px, py)
                                }

                                // Build smooth spline mountain curve connecting the app peaks
                                val linePath = androidx.compose.ui.graphics.Path()
                                val fillPath = androidx.compose.ui.graphics.Path()

                                fillPath.moveTo(0f, h)
                                val firstPt = appPoints.first()
                                val startY = (h + firstPt.y) / 2f
                                fillPath.lineTo(0f, startY)
                                linePath.moveTo(0f, startY)

                                for (i in 0 until appPoints.size) {
                                    val curr = appPoints[i]
                                    if (i == 0) {
                                        val cx = curr.x * 0.5f
                                        linePath.cubicTo(cx, startY, cx, curr.y, curr.x, curr.y)
                                        fillPath.cubicTo(cx, startY, cx, curr.y, curr.x, curr.y)
                                    } else {
                                        val prev = appPoints[i - 1]
                                        val midX = (prev.x + curr.x) / 2f
                                        val valleyY = ((prev.y + curr.y) / 2f + h * 0.18f).coerceAtMost(h - 4.dp.toPx())
                                        linePath.cubicTo(
                                            (prev.x + midX) / 2f, prev.y,
                                            (prev.x + midX) / 2f, valleyY,
                                            midX, valleyY
                                        )
                                        linePath.cubicTo(
                                            (midX + curr.x) / 2f, valleyY,
                                            (midX + curr.x) / 2f, curr.y,
                                            curr.x, curr.y
                                        )
                                        fillPath.cubicTo(
                                            (prev.x + midX) / 2f, prev.y,
                                            (prev.x + midX) / 2f, valleyY,
                                            midX, valleyY
                                        )
                                        fillPath.cubicTo(
                                            (midX + curr.x) / 2f, valleyY,
                                            (midX + curr.x) / 2f, curr.y,
                                            curr.x, curr.y
                                        )
                                    }
                                }

                                val lastPt = appPoints.last()
                                val endY = (h + lastPt.y) / 2f
                                val endControlX = (lastPt.x + w) / 2f
                                linePath.cubicTo(endControlX, lastPt.y, endControlX, endY, w, endY)
                                fillPath.cubicTo(endControlX, lastPt.y, endControlX, endY, w, endY)
                                fillPath.lineTo(w, h)
                                fillPath.close()

                                // Soft Material You primary vertical gradient fill
                                drawPath(
                                    path = fillPath,
                                    brush = gradientBrush
                                )

                                // Crisp spline outline
                                drawPath(
                                    path = linePath,
                                    color = primaryColor.copy(alpha = 0.85f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 2.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )

                                // Vertical dashed drop stems and pulsing peak dots for each app in its Material You color
                                appPoints.forEachIndexed { i, pt ->
                                    val appColor = materialAppColors[i % materialAppColors.size]

                                    // Vertical dashed stem from peak down to baseline
                                    drawLine(
                                        color = appColor.copy(alpha = 0.45f),
                                        start = pt,
                                        end = androidx.compose.ui.geometry.Offset(pt.x, h),
                                        strokeWidth = 1.dp.toPx(),
                                        pathEffect = stemDashEffect
                                    )

                                    // Pulsing halo aura
                                    drawCircle(
                                        color = appColor.copy(alpha = 0.25f),
                                        radius = pointPulse.dp.toPx() * 1.6f,
                                        center = pt
                                    )
                                    // Clean surface ring
                                    drawCircle(
                                        color = surfaceColor,
                                        radius = 3.5.dp.toPx(),
                                        center = pt
                                    )
                                    // Solid center dot in app's Material You color
                                    drawCircle(
                                        color = appColor,
                                        radius = 2.dp.toPx(),
                                        center = pt
                                    )
                                }
                            }
                        }

                        // X and Y Coordinate axis markings
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${ceilingMb}M",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                            Text(
                                text = "0M",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = 10.dp)
                            )
                            Text(
                                text = "RAM by Category",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 1.dp)
                            )
                        }
                    }

                    // RIGHT: RAM Usage Breakdown showing what apps/processes are using RAM
                    Column(
                        modifier = Modifier
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Memory Breakdown",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        if (topProcesses.isEmpty()) {
                            Text(
                                text = "Analyzing processes...",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            topProcesses.forEachIndexed { i, entry ->
                                val appColor = materialAppColors[i % materialAppColors.size]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(appColor)
                                    )
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (entry.ramMb >= 1000f) {
                                            String.format(java.util.Locale.US, "%.1f GB", entry.ramMb / 1000f)
                                        } else {
                                            "${entry.ramMb.toInt()} MB"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            Text(
                                text = "Free RAM",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${availableRamGb} GB",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Diagnostic Metric Chips for RAM Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val chipBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

                    // zRAM Swap Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = chipBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "zRAM Swap",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${zRamUsedGb}G / ${zRamTotalGb}G",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Cache & Buffers Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = chipBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Cache/Buffers",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${cachedRamGb}G",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Available RAM Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = chipBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Available",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${availableRamGb}G",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // LMK Kill Floor Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = chipBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "LMK Kill Floor",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${lmkThresholdMb}MB",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
