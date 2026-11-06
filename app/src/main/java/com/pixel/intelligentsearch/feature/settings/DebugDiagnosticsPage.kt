package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.ui.symbols.Symbols
import com.pixel.intelligentsearch.core.system.battery.ChargingStatus
import com.pixel.intelligentsearch.core.system.battery.BatteryReadings
import com.pixel.intelligentsearch.core.util.clampSafely
import androidx.compose.ui.draw.clip
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.repeatOnLifecycle
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
private val batteryCurrentReader = BatteryReadings.CurrentReader()

internal data class MemorySnapshot(
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

internal data class BatteryTelemetrySnapshot(
    val level: Int,
    val temp: Float,
    val volt: Float,
    val watts: Float?,
    val health: String,
    val healthSeverity: BatteryReadings.HealthSeverity,
    val cycles: Int,
    val technology: String,
    val thermalStatus: String,
    val isCharging: Boolean,
    val chargingRateStr: String,
    val timeEstimateStr: String,
    /** What the phone is plugged into and doing, or null on battery; [chargingRateStr] is its full label. */
    val power: ChargingStatus.Status? = null
)

/** Test tag of the battery card's header chip, which shows the charging status (or a health warning). */
internal const val BATTERY_STATUS_CHIP_TAG = "battery_status_chip"

/**
 * Fires when the phone is plugged in or unplugged, starts or stops charging, or changes USB mode, so the page can
 * read again at once instead of at its next poll. Registered only while collected.
 */
internal fun powerChangeEvents(context: Context): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.callbackFlow {
    val appContext = context.applicationContext ?: context
    // The battery broadcast also fires for every level and voltage step; only plug and charging changes count.
    var lastPlugState: Pair<Int, Int>? = null
    val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            intent ?: return
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val plugState = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) to
                    intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val changed = lastPlugState != null && plugState != lastPlugState
                lastPlugState = plugState
                if (changed) trySend(Unit)
            } else if (!isInitialStickyBroadcast) {
                trySend(Unit)
            }
        }
    }
    val filter = android.content.IntentFilter().apply {
        addAction(Intent.ACTION_BATTERY_CHANGED)
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
        addAction(ChargingStatus.ACTION_USB_STATE)
    }
    androidx.core.content.ContextCompat.registerReceiver(appContext, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    awaitClose { appContext.unregisterReceiver(receiver) }
}

/**
 * The memory pressure chip's background and label colours, as a pair: Material container roles with the text
 * colour made for each, so every level stays legible in both themes.
 */
internal fun pressureChipColors(pressure: String, scheme: ColorScheme): Pair<Color, Color> = when (pressure) {
    "Critical" -> scheme.error to scheme.onError
    "High Load" -> scheme.errorContainer to scheme.onErrorContainer
    "Moderate" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    else -> scheme.secondaryContainer to scheme.onSecondaryContainer
}

/** One battery and memory reading for the diagnostics page. */
internal data class DiagnosticsSample(val battery: BatteryTelemetrySnapshot, val memory: MemorySnapshot)

/**
 * Takes one reading per call, off the main thread. A sampler carries the battery current's smoothing from one
 * reading to the next, so the page starts a new one each time it comes back into view.
 */
internal fun interface DiagnosticsSampler {
    fun sample(): DiagnosticsSample
}

/** The phone's own battery and memory readings. */
internal class DeviceDiagnosticsSampler(context: Context) : DiagnosticsSampler {
    private val appContext = context.applicationContext ?: context
    private val actMgr = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
    private val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager

    // Carried across polls: the smoothed net current behind the time estimates, reset whenever the charging
    // state changes. The current unit and sign are learned process-wide by batteryCurrentReader.
    private var smoothedNetMa: Float? = null
    private var lastChargeState: BatteryReadings.ChargeState? = null

    override fun sample(): DiagnosticsSample {
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
        val chargeState = BatteryReadings.chargeState(status, plugged)
        if (chargeState != lastChargeState) {
            // Also covers the page opening: plug changes made while it was closed were not seen.
            smoothedNetMa = null
            lastChargeState = chargeState
            batteryCurrentReader.restartSettleWindow()
        }

        val h = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN
        val bHealth = BatteryReadings.healthLabel(h)
        val bHealthSeverity = BatteryReadings.healthSeverity(h)

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
        val unplugged = chargeState == BatteryReadings.ChargeState.Discharging
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val rawCurrentNow = try { bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Throwable) { null } ?: Int.MIN_VALUE
        val rawCurrentAvg = try { bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) } catch (_: Throwable) { null } ?: Int.MIN_VALUE
        val netNowMa = batteryCurrentReader.readNow(rawCurrentNow, unplugged, nowMs)
        val netAvgMa = batteryCurrentReader.convert(rawCurrentAvg)
        val netForEstimate = if (batteryCurrentReader.isSettled(nowMs) && BatteryReadings.averageAgrees(netNowMa, netAvgMa)) netAvgMa else netNowMa
        if (netForEstimate != null) smoothedNetMa = BatteryReadings.smooth(smoothedNetMa, netForEstimate)
        val bWatts = if (bVolt > 0f && netNowMa != null) bVolt * kotlin.math.abs(netNowMa) / 1000f else null

        // CHARGE_COUNTER is µAh; dropped when it implies an implausible full capacity.
        val chargeMah = try {
            val cc = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
            if (cc != Int.MIN_VALUE && cc > 0) cc / 1000f else null
        } catch (_: Throwable) { null }?.takeIf { mah ->
            bLevel < 10 || (mah / (bLevel / 100f)) in 1000f..20000f
        }

        fun untilEmpty(otherwise: String): String {
            val minutes = BatteryReadings.minutesUntilEmpty(chargeMah, smoothedNetMa)
            return when {
                minutes == null -> otherwise
                minutes > 7 * 24 * 60 -> "Over a week left"
                else -> "${DiagnosticsReadings.formatMinutes(minutes)} until empty"
            }
        }

        // What the phone is plugged into: the sticky USB state says whether a computer is on the other end and which
        // USB modes are on; the debugging setting stands in when that broadcast can't be read.
        val usbState = try {
            androidx.core.content.ContextCompat.registerReceiver(
                appContext, null, android.content.IntentFilter(ChargingStatus.ACTION_USB_STATE),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Throwable) { null }
        val usbLink = usbState?.let { usb -> ChargingStatus.UsbLink.from { usb.getBooleanExtra(it, false) } }
        val adbEnabled = try {
            android.provider.Settings.Global.getInt(appContext.contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Throwable) { false }
        val power = ChargingStatus.status(
            plugged = plugged,
            chargeState = chargeState,
            usb = usbLink,
            adbEnabled = adbEnabled,
            chargingState = batteryStatus?.getIntExtra(ChargingStatus.EXTRA_CHARGING_STATUS, 0) ?: 0,
            fastCharging = (bWatts ?: 0f) >= 15f
        )

        val bChargingRateStr = power?.label ?: "Discharging"
        val bTimeEstimateStr: String
        when (chargeState) {
            BatteryReadings.ChargeState.Charging -> {
                val systemMs = try { bm?.computeChargeTimeRemaining() ?: -1L } catch (_: Throwable) { -1L }
                // Until an unplugged reading has confirmed the vendor's sign, the direction is unproven:
                // trust the CHARGING status for the estimate and never claim the phone is losing charge.
                val signKnown = batteryCurrentReader.signLocked
                val chargeInMa = if (signKnown) smoothedNetMa else smoothedNetMa?.let { kotlin.math.abs(it) }
                val minutes = if (systemMs > 0L) (systemMs / 60_000L).toInt().coerceAtLeast(1)
                    else BatteryReadings.minutesUntilFull(chargeMah, bLevel, chargeInMa)
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
            BatteryReadings.ChargeState.Full -> {
                bTimeEstimateStr = "Fully charged"
            }
            BatteryReadings.ChargeState.PluggedNotCharging -> {
                // The charger is not adding charge; the phone may still be running down slowly.
                bTimeEstimateStr = untilEmpty(otherwise = "Charging paused")
            }
            BatteryReadings.ChargeState.PluggedUnknown, BatteryReadings.ChargeState.Discharging -> {
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
            isCharging = chargeState == BatteryReadings.ChargeState.Charging ||
                chargeState == BatteryReadings.ChargeState.Full,
            chargingRateStr = bChargingRateStr,
            timeEstimateStr = bTimeEstimateStr,
            power = power
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

        return DiagnosticsSample(batterySnap, memSnap)
    }
}

@Composable
internal fun BatteryAndMemoryDiagnosticsPage(
    context: Context,
    newSampler: (Context) -> DiagnosticsSampler = ::DeviceDiagnosticsSampler,
    powerEvents: (Context) -> kotlinx.coroutines.flow.Flow<Unit> = ::powerChangeEvents
) {
    var batteryLevel by remember { mutableIntStateOf(0) }
    var batteryTemp by remember { mutableFloatStateOf(0f) }
    var batteryVolt by remember { mutableFloatStateOf(0f) }
    var batteryWatts by remember { mutableStateOf<Float?>(null) }
    var batteryHealth by remember { mutableStateOf(BatteryReadings.HEALTH_NOT_REPORTED) }
    var batteryHealthSeverity by remember { mutableStateOf(BatteryReadings.HealthSeverity.Ok) }
    var batteryCycles by remember { mutableIntStateOf(-1) }
    var batteryTechnology by remember { mutableStateOf("Li-ion") }
    var thermalStatus by remember { mutableStateOf("Normal") }
    var isCharging by remember { mutableStateOf(false) }
    var chargingRateStr by remember { mutableStateOf("Reading battery...") }
    var timeEstimateStr by remember { mutableStateOf("Estimating...") }
    var power by remember { mutableStateOf<ChargingStatus.Status?>(null) }

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
    var memoryPressure by remember { mutableStateOf("Normal") }
    var lmkThresholdMb by remember { mutableIntStateOf(0) }
    var topProcesses by remember { mutableStateOf<List<ProcessRamEntry>>(emptyList()) }
    var breakdownAvailable by remember { mutableStateOf(true) }

    val currentNewSampler by rememberUpdatedState(newSampler)
    val currentPowerEvents by rememberUpdatedState(powerEvents)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    // Reads only while the page is on screen; each time it comes back a new sampler starts the smoothing afresh.
    LaunchedEffect(lifecycle, context) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            val sampler = currentNewSampler(context)
            // Plugging in, unplugging or switching USB mode reads again at once instead of at the next poll.
            val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
            launch { currentPowerEvents(context).collect { wake.trySend(Unit) } }
            while (true) {
                try {
                    val (bSnap, mSnap) = withContext(Dispatchers.IO) { sampler.sample() }

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
                    power = bSnap.power

                    totalRamGb = String.format(java.util.Locale.US, "%.1f", mSnap.totalGb)
                    usedRamGb = String.format(java.util.Locale.US, "%.1f", mSnap.usedGb)
                    usedRamPercent = mSnap.pct
                    availableRamGb = String.format(java.util.Locale.US, "%.1f", mSnap.availGb)
                    physicalRamGb = mSnap.physicalGb
                    zRamUsedGb = String.format(java.util.Locale.US, "%.1f", mSnap.zRamUsedGb)
                    zRamTotalGb = String.format(java.util.Locale.US, "%.1f", mSnap.zRamTotalGb)
                    cachedRamGb = String.format(java.util.Locale.US, "%.1f", mSnap.cachedGb)
                    memoryPressure = mSnap.memPressure
                    lmkThresholdMb = mSnap.thresholdMb
                    topProcesses = mSnap.processes
                    breakdownAvailable = mSnap.breakdownAvailable
                } catch (_: Throwable) {}

                kotlinx.coroutines.withTimeoutOrNull(2500) { wake.receive() }
            }
        }
    }

    val animationsEnabled by com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled()
    val motion = rememberDiagnosticsMotion(animationsEnabled)
    val wavePhase by motion.wavePhase

    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(DiagnosticsCardSpacing)
    ) {
        // --- TOP CARD: Battery Health ---
        val (statusChipText, statusChipIsHealth) = ChargingStatus.headerChip(batteryHealth, batteryHealthSeverity, power)
        DiagnosticsCard(
            icon = if (isCharging) Symbols.Filled.BatteryChargingFull else Symbols.Filled.BatteryFull,
            title = "Battery Health",
            accent = scheme.primary,
            trailing = {
                val (chipBg, chipFg) = if (statusChipIsHealth) {
                    healthChipColors(batteryHealthSeverity, scheme)
                } else {
                    scheme.primaryContainer.copy(alpha = 0.6f) to scheme.onPrimaryContainer
                }
                DiagnosticsStatusChip(
                    text = statusChipText,
                    container = chipBg,
                    content = chipFg,
                    modifier = Modifier
                        .semantics(mergeDescendants = true) {}
                        .testTag(BATTERY_STATUS_CHIP_TAG)
                )
            }
        ) {
            DiagnosticsHeroRow(
                graphWeight = 1.2f,
                detailsWeight = 1.1f,
                graph = { graphModifier ->
                    BatteryLevelWave(batteryLevel = batteryLevel, wavePhase = wavePhase, modifier = graphModifier)
                },
                details = { detailsModifier ->
                    Column(
                        modifier = detailsModifier,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = chargingRateStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = timeEstimateStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurface,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", batteryTemp)} °C / ${String.format(java.util.Locale.US, "%.0f", batteryTemp * 1.8f + 32f)} °F  •  ${String.format(java.util.Locale.US, "%.2f", batteryVolt)} V  •  $batteryTechnology",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            )

            DiagnosticsMetricGrid(
                listOf(
                    DiagnosticsMetric("Cycles", if (batteryCycles >= 0) "$batteryCycles" else "N/A"),
                    DiagnosticsMetric("Power", batteryWatts?.let { "${String.format(java.util.Locale.US, "%.1f", it)}W" } ?: "N/A"),
                    DiagnosticsMetric(
                        "Thermals",
                        thermalStatus,
                        when (thermalStatus) {
                            "Severe", "Critical", "Emergency", "Shutdown" -> scheme.error
                            "Light", "Moderate" -> scheme.tertiary
                            else -> scheme.primary
                        }
                    ),
                    DiagnosticsMetric(
                        "Health",
                        batteryHealth,
                        when (batteryHealthSeverity) {
                            BatteryReadings.HealthSeverity.Bad -> scheme.error
                            BatteryReadings.HealthSeverity.Caution -> scheme.tertiary
                            BatteryReadings.HealthSeverity.Ok -> Color.Unspecified
                        }
                    )
                )
            )
        }

        // --- BOTTOM CARD: System RAM Usage (Live Graph on Left, RAM Usage Breakdown on Right) ---
        DiagnosticsCard(
            icon = Symbols.Outlined.Speed,
            title = "System RAM",
            accent = scheme.secondary,
            trailing = {
                val (pressureContainer, pressureContent) = pressureChipColors(memoryPressure, scheme)
                DiagnosticsStatusChip(text = memoryPressure, container = pressureContainer, content = pressureContent)
                Text(
                    text = "${usedRamGb}G / ${totalRamGb}G (${physicalRamGb}G)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.secondary
                )
            }
        ) {
            val materialAppColors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.inversePrimary)

            DiagnosticsHeroRow(
                graphWeight = 1.25f,
                detailsWeight = 1f,
                graph = { graphModifier ->
                    RamCategoryGraph(
                        processes = topProcesses,
                        appColors = materialAppColors,
                        modifier = graphModifier
                    )
                },
                details = { detailsModifier ->
                    // RAM Usage Breakdown showing what apps/processes are using RAM
                    Column(
                        modifier = detailsModifier,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Used Memory",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.secondary
                        )

                        if (topProcesses.isEmpty()) {
                            Text(
                                text = if (breakdownAvailable) "Reading memory..." else "Breakdown unavailable",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.5.sp,
                                color = scheme.onSurfaceVariant
                            )
                        } else {
                            topProcesses.forEachIndexed { i, entry ->
                                RamLegendRow(
                                    dotColor = materialAppColors[i % materialAppColors.size],
                                    name = entry.name,
                                    nameColor = scheme.onSurface,
                                    value = if (entry.ramMb >= 1000f) {
                                        String.format(java.util.Locale.US, "%.1f GB", entry.ramMb / 1000f)
                                    } else {
                                        "${entry.ramMb.toInt()} MB"
                                    },
                                    valueColor = scheme.onSurfaceVariant
                                )
                            }
                        }

                        RamLegendRow(
                            dotColor = scheme.outlineVariant,
                            name = "Available",
                            nameColor = scheme.onSurfaceVariant,
                            value = "$availableRamGb GB",
                            valueColor = scheme.secondary
                        )
                    }
                }
            )

            DiagnosticsMetricGrid(
                listOf(
                    DiagnosticsMetric("Swapped", "${zRamUsedGb}G / ${zRamTotalGb}G"),
                    DiagnosticsMetric("Cache/Buffers", "${cachedRamGb}G"),
                    DiagnosticsMetric("Used", "$usedRamPercent%"),
                    DiagnosticsMetric("Low-Mem Level", "${lmkThresholdMb}MB")
                )
            )
        }
    }
}

/** The health chip's background and label colours for each rating. */
internal fun healthChipColors(severity: BatteryReadings.HealthSeverity, scheme: ColorScheme): Pair<Color, Color> = when (severity) {
    BatteryReadings.HealthSeverity.Bad -> scheme.errorContainer to scheme.onErrorContainer
    BatteryReadings.HealthSeverity.Caution -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    BatteryReadings.HealthSeverity.Ok -> scheme.primaryContainer.copy(alpha = 0.6f) to scheme.onPrimaryContainer
}

/** Height of the battery wave and the RAM graph. */
private val HeroGraphHeight = 104.dp

/**
 * A card's graph beside its readings, or above them once the readings column would be too narrow for the text
 * size (large fonts, display zoom or a narrow window), so the text wraps over the full width instead of clipping.
 */
@Composable
private fun DiagnosticsHeroRow(
    graphWeight: Float,
    detailsWeight: Float,
    graph: @Composable (Modifier) -> Unit,
    details: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = DiagnosticsCardSpacing
        val detailsWidth = (maxWidth - gap) * (detailsWeight / (graphWeight + detailsWeight))
        if (detailsWidth >= 104.dp * LocalDensity.current.fontScale) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                graph(Modifier.weight(graphWeight).height(HeroGraphHeight))
                details(Modifier.weight(detailsWeight))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                graph(Modifier.fillMaxWidth().height(HeroGraphHeight))
                details(Modifier.fillMaxWidth())
            }
        }
    }
}

/** The battery level as a gently rolling wave of Material You colour. */
/** The battery graph's motion: its wave's phase. */
internal class DiagnosticsMotion(val wavePhase: State<Float>)

/**
 * The wave rolling for as long as the page is open, or a still frame while animations are
 * turned off ([animationsEnabled], Remove animations), as the settings slider's wave does.
 */
@Composable
internal fun rememberDiagnosticsMotion(animationsEnabled: Boolean): DiagnosticsMotion {
    if (!animationsEnabled) {
        return remember { DiagnosticsMotion(mutableFloatStateOf(0f)) }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "diagPulseTransition")
    val wavePhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "diagWavePhase"
    )
    return remember(wavePhase) { DiagnosticsMotion(wavePhase) }
}

@Composable
private fun BatteryLevelWave(batteryLevel: Int, wavePhase: Float, modifier: Modifier) {
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
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f)),
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
}

/** One line of the RAM breakdown: a colour dot, the category and its size. */
@Composable
private fun RamLegendRow(dotColor: Color, name: String, nameColor: Color, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dotColor)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.5.sp,
            color = nameColor,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
    }
}

/** How tall a category's bar is against the graph's [ceilingMb]: its true share, so sizes can be compared by eye. */
internal fun ramBarFraction(ramMb: Float, ceilingMb: Int): Float =
    if (ceilingMb <= 0) 0f else (ramMb / ceilingMb).coerceIn(0f, 1f)

/** RAM by category: one bar each, in the color of its row in the list beside it. */
@Composable
private fun RamCategoryGraph(
    processes: List<ProcessRamEntry>,
    appColors: List<Color>,
    modifier: Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val ceilingMb = if (processes.isNotEmpty()) {
        val maxVal = (processes.maxOfOrNull { it.ramMb } ?: 500f).coerceAtLeast(100f)
        (maxVal * 1.35f).toInt()
    } else 500

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        val gridDashEffect = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) }

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

            val slot = w / processes.size.coerceAtLeast(1)
            val barWidth = slot * 0.56f
            val corner = androidx.compose.ui.geometry.CornerRadius(minOf(barWidth / 2f, 6.dp.toPx()))
            processes.forEachIndexed { i, process ->
                val barHeight = h * ramBarFraction(process.ramMb, ceilingMb)
                val x = slot * i + (slot - barWidth) / 2f
                val bar = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = x,
                            top = h - barHeight,
                            right = x + barWidth,
                            bottom = h,
                            topLeftCornerRadius = corner,
                            topRightCornerRadius = corner
                        )
                    )
                }
                drawPath(bar, appColors[i % appColors.size])
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
}
