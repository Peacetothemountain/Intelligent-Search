package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.util.clampSafely
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

/** Process-wide so the learned current unit and sign survive reopening the page. */
private val batteryCurrentReader = DiagnosticsReadings.CurrentReader()

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
    val breakdownAvailable: Boolean,
    val physicalGb: Int
)

private data class BatteryTelemetrySnapshot(
    val level: Int,
    val temp: Float,
    val volt: Float,
    val watts: Float?,
    val health: String,
    val healthSeverity: DiagnosticsReadings.HealthSeverity,
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
    var batteryWatts by remember { mutableStateOf<Float?>(null) }
    var batteryHealth by remember { mutableStateOf(DiagnosticsReadings.HEALTH_NOT_REPORTED) }
    var batteryHealthSeverity by remember { mutableStateOf(DiagnosticsReadings.HealthSeverity.Ok) }
    var batteryCycles by remember { mutableIntStateOf(-1) }
    var batteryTechnology by remember { mutableStateOf("Li-ion") }
    var thermalStatus by remember { mutableStateOf("Normal") }
    var isCharging by remember { mutableStateOf(false) }
    var chargingRateStr by remember { mutableStateOf("Reading battery...") }
    var timeEstimateStr by remember { mutableStateOf("Estimating...") }

    var totalRamGb by remember { mutableStateOf("0.0") }
    var usedRamGb by remember { mutableStateOf("0.0") }
    var usedRamPercent by remember { mutableIntStateOf(0) }
    var availableRamGb by remember { mutableStateOf("0.0") }
    val initialPhysicalRam = remember(context) {
        val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)
        val advertised = if (Build.VERSION.SDK_INT >= 34) memInfo.advertisedMem else -1L
        DiagnosticsReadings.retailRamGb(advertised, memInfo.totalMem)
    }
    var physicalRamGb by remember { mutableIntStateOf(initialPhysicalRam) }
    var zRamUsedGb by remember { mutableStateOf("0.0") }
    var zRamTotalGb by remember { mutableStateOf("0.0") }
    var cachedRamGb by remember { mutableStateOf("0.0") }
    var isLowMemory by remember { mutableStateOf(false) }
    var memoryPressure by remember { mutableStateOf("Normal") }
    var lmkThresholdMb by remember { mutableIntStateOf(0) }
    var topProcesses by remember { mutableStateOf<List<ProcessRamEntry>>(emptyList()) }
    var breakdownAvailable by remember { mutableStateOf(true) }

    val actMgr = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager }
    val bm = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager }

    LaunchedEffect(Unit) {
        val appContext = context.applicationContext ?: context
        // Carried across polls: the smoothed net current behind the time estimates, reset whenever the charging
        // state changes. The current unit and sign are learned process-wide by batteryCurrentReader.
        var smoothedNetMa: Float? = null
        var lastChargeState: DiagnosticsReadings.ChargeState? = null
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
                    val plugged = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                    val chargeState = DiagnosticsReadings.chargeState(status, plugged)
                    if (chargeState != lastChargeState) {
                        // Also covers the page opening: plug changes made while it was closed were not seen.
                        smoothedNetMa = null
                        lastChargeState = chargeState
                        batteryCurrentReader.restartSettleWindow()
                    }

                    val h = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN)
                        ?: android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN
                    val bHealth = DiagnosticsReadings.healthLabel(h)
                    val bHealthSeverity = DiagnosticsReadings.healthSeverity(h)

                    // Cycle count is part of the battery broadcast from Android 14 (API 34); 0 is valid on a new battery.
                    val cycles = if (Build.VERSION.SDK_INT >= 34) {
                        batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
                    } else {
                        -1
                    }

                    val bTechnology = batteryStatus?.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "Li-ion"

                    // Device-wide thermal throttling state (not battery temperature).
                    val pmPower = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val bThermalStatus = DiagnosticsReadings.thermalLabel(
                        try { pmPower?.currentThermalStatus } catch (_: Exception) { null }
                    ) ?: "N/A"

                    // Net current into the battery (negative while draining): CURRENT_NOW for live power and for
                    // learning the unit and sign; CURRENT_AVERAGE (smoothed further) for time estimates once the plug
                    // state has settled and it agrees with CURRENT_NOW, so a stale or differently scaled average
                    // can't drive them.
                    val unplugged = chargeState == DiagnosticsReadings.ChargeState.Discharging
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    val rawCurrentNow = try { bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Throwable) { null } ?: Int.MIN_VALUE
                    val rawCurrentAvg = try { bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) } catch (_: Throwable) { null } ?: Int.MIN_VALUE
                    val netNowMa = batteryCurrentReader.readNow(rawCurrentNow, unplugged, nowMs)
                    val netAvgMa = batteryCurrentReader.convert(rawCurrentAvg)
                    val netForEstimate = if (batteryCurrentReader.isSettled(nowMs) && DiagnosticsReadings.averageAgrees(netNowMa, netAvgMa)) netAvgMa else netNowMa
                    if (netForEstimate != null) smoothedNetMa = DiagnosticsReadings.smooth(smoothedNetMa, netForEstimate)
                    val bWatts = if (bVolt > 0f && netNowMa != null) bVolt * kotlin.math.abs(netNowMa) / 1000f else null

                    // CHARGE_COUNTER is µAh; dropped when it implies an implausible full capacity.
                    val chargeMah = try {
                        val cc = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
                        if (cc != Int.MIN_VALUE && cc > 0) cc / 1000f else null
                    } catch (_: Throwable) { null }?.takeIf { mah ->
                        bLevel < 10 || (mah / (bLevel / 100f)) in 1000f..20000f
                    }

                    fun untilEmpty(otherwise: String): String {
                        val minutes = DiagnosticsReadings.minutesUntilEmpty(chargeMah, smoothedNetMa)
                        return when {
                            minutes == null -> otherwise
                            minutes > 7 * 24 * 60 -> "Over a week left"
                            else -> "${DiagnosticsReadings.formatMinutes(minutes)} until empty"
                        }
                    }

                    val bChargingRateStr: String
                    val bTimeEstimateStr: String
                    when (chargeState) {
                        DiagnosticsReadings.ChargeState.Charging -> {
                            bChargingRateStr = when (plugged) {
                                android.os.BatteryManager.BATTERY_PLUGGED_AC -> if ((bWatts ?: 0f) >= 15f) "Fast charging" else "Charging via AC"
                                android.os.BatteryManager.BATTERY_PLUGGED_USB -> "Charging via USB"
                                android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Charging wirelessly"
                                android.os.BatteryManager.BATTERY_PLUGGED_DOCK -> "Charging on dock"
                                else -> "Charging"
                            }
                            val systemMs = try { bm?.computeChargeTimeRemaining() ?: -1L } catch (_: Throwable) { -1L }
                            // Until an unplugged reading has confirmed the vendor's sign, the direction is unproven:
                            // trust the CHARGING status for the estimate and never claim the phone is losing charge.
                            val signKnown = batteryCurrentReader.signLocked
                            val chargeInMa = if (signKnown) smoothedNetMa else smoothedNetMa?.let { kotlin.math.abs(it) }
                            val minutes = if (systemMs > 0L) (systemMs / 60_000L).toInt().coerceAtLeast(1)
                                else DiagnosticsReadings.minutesUntilFull(chargeMah, bLevel, chargeInMa)
                            // Both the live and the smoothed current must show a drain, so a lagging average right
                            // after plugging in can't claim the phone is losing charge.
                            val draining = signKnown && (netNowMa ?: 0f) < -20f && (smoothedNetMa ?: 0f) < -20f
                            bTimeEstimateStr = when {
                                bLevel >= 100 -> "Fully charged"
                                systemMs > 0L && minutes != null && minutes <= 48 * 60 ->
                                    "${DiagnosticsReadings.formatMinutes(minutes)} until full"
                                draining -> "Not gaining charge"
                                minutes == null -> "Estimating..."
                                minutes > 48 * 60 -> "Charging slowly"
                                else -> "${DiagnosticsReadings.formatMinutes(minutes)} until full"
                            }
                        }
                        DiagnosticsReadings.ChargeState.Full -> {
                            bChargingRateStr = "Plugged in"
                            bTimeEstimateStr = "Fully charged"
                        }
                        DiagnosticsReadings.ChargeState.PluggedNotCharging -> {
                            // The charger is not adding charge; the phone may still be running down slowly.
                            bChargingRateStr = "Plugged in, not charging"
                            bTimeEstimateStr = untilEmpty(otherwise = "Charging paused")
                        }
                        DiagnosticsReadings.ChargeState.PluggedUnknown -> {
                            bChargingRateStr = "Plugged in"
                            bTimeEstimateStr = untilEmpty(otherwise = "Estimating...")
                        }
                        DiagnosticsReadings.ChargeState.Discharging -> {
                            bChargingRateStr = "Discharging"
                            bTimeEstimateStr = untilEmpty(otherwise = "Estimating...")
                        }
                    }

                    val batterySnap = BatteryTelemetrySnapshot(
                        level = bLevel,
                        temp = bTemp,
                        volt = bVolt,
                        watts = bWatts,
                        health = bHealth,
                        healthSeverity = bHealthSeverity,
                        cycles = cycles,
                        technology = bTechnology,
                        thermalStatus = bThermalStatus,
                        isCharging = chargeState == DiagnosticsReadings.ChargeState.Charging ||
                            chargeState == DiagnosticsReadings.ChargeState.Full,
                        chargingRateStr = bChargingRateStr,
                        timeEstimateStr = bTimeEstimateStr
                    )

                    // Memory: the kernel's own MemAvailable, the same figure ActivityManager reports as availMem.
                    val memInfo = android.app.ActivityManager.MemoryInfo()
                    actMgr?.getMemoryInfo(memInfo)
                    val procMem = try {
                        DiagnosticsReadings.parseMemInfo(java.io.File("/proc/meminfo").readText())
                    } catch (_: Throwable) { null }
                    val figures = DiagnosticsReadings.memoryFigures(procMem, memInfo.totalMem, memInfo.availMem)

                    // Decimal gigabytes, as Android Settings displays memory.
                    val totGb = figures.totalBytes / 1_000_000_000.0
                    val usdGb = figures.usedBytes / 1_000_000_000.0
                    val avlGb = figures.availableBytes / 1_000_000_000.0
                    val advertised = if (Build.VERSION.SDK_INT >= 34) memInfo.advertisedMem else -1L
                    val physGb = DiagnosticsReadings.retailRamGb(advertised, figures.totalBytes)

                    // /proc/meminfo reports KiB; convert to bytes before scaling to decimal units.
                    fun kbToGb(kb: Long) = kb * 1024.0 / 1_000_000_000.0
                    fun bytesToMb(bytes: Long) = bytes / 1_000_000f
                    val swapTotalKb = procMem?.swapTotalKb ?: 0L
                    val swapUsedKb = (swapTotalKb - (procMem?.swapFreeKb ?: swapTotalKb)).coerceAtLeast(0L)
                    val fileCacheKb = (procMem?.cachedKb ?: 0L) + (procMem?.buffersKb ?: 0L)

                    val isLow = memInfo.lowMemory
                    val threshMb = (memInfo.threshold / (1024L * 1024L)).toInt()
                    val pressure = DiagnosticsReadings.memoryPressure(isLow, figures.availablePercent)

                    // Used memory split into parts that add up to it; file cache the kernel can reclaim is in Available.
                    val breakdown = DiagnosticsReadings.usedBreakdown(procMem, figures.usedBytes)
                    val hasBreakdown = breakdown != null
                    val topProcessesList = if (breakdown != null) listOf(
                        ProcessRamEntry(name = "Apps", ramMb = bytesToMb(breakdown.appsBytes)),
                        ProcessRamEntry(name = "Kernel", ramMb = bytesToMb(breakdown.kernelBytes)),
                        ProcessRamEntry(name = "Other", ramMb = bytesToMb(breakdown.otherBytes))
                    ) else emptyList()

                    val memSnap = MemorySnapshot(
                        totalGb = totGb,
                        usedGb = usdGb,
                        availGb = avlGb,
                        pct = figures.usedPercent,
                        zRamUsedGb = kbToGb(swapUsedKb),
                        zRamTotalGb = kbToGb(swapTotalKb),
                        cachedGb = kbToGb(fileCacheKb),
                        isLowMem = isLow,
                        thresholdMb = threshMb,
                        memPressure = pressure,
                        processes = topProcessesList,
                        breakdownAvailable = hasBreakdown,
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
                batteryHealthSeverity = bSnap.healthSeverity
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
                breakdownAvailable = mSnap.breakdownAvailable
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
                        val (healthChipBg, healthChipFg) = when (batteryHealthSeverity) {
                            DiagnosticsReadings.HealthSeverity.Bad ->
                                MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                            DiagnosticsReadings.HealthSeverity.Caution ->
                                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                            DiagnosticsReadings.HealthSeverity.Ok ->
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) to MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = healthChipBg
                        ) {
                            Text(
                                text = batteryHealth,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = healthChipFg,
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
                            text = "${String.format(java.util.Locale.US, "%.1f", batteryTemp)} °C / ${String.format(java.util.Locale.US, "%.0f", batteryTemp * 1.8f + 32f)} °F  •  ${String.format(java.util.Locale.US, "%.2f", batteryVolt)} V",
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
                                text = batteryWatts?.let { "${String.format(java.util.Locale.US, "%.1f", it)}W" } ?: "N/A",
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
                                    "Severe", "Critical", "Emergency", "Shutdown" -> MaterialTheme.colorScheme.error
                                    "Light", "Moderate" -> MaterialTheme.colorScheme.tertiary
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
                                    val py = (h - (h * normY) + breathe).clampSafely(4.dp.toPx(), h - 8.dp.toPx())
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
                            text = "Used Memory",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        if (topProcesses.isEmpty()) {
                            Text(
                                text = if (breakdownAvailable) "Reading memory..." else "Breakdown unavailable",
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
                                text = "Available",
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
                                text = "Swapped",
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

                    // Used RAM share Chip
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
                                text = "Used",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$usedRamPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // ActivityManager low-memory threshold Chip
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
                                text = "Low-Mem Level",
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
