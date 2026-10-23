package com.pixel.intelligentsearch.feature.settings

import android.os.PowerManager
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Pure interpretation of the raw values the diagnostics page reads from PowerManager and /proc/meminfo. Kept free
 * of Android calls so each rule can be unit tested against real device readings. The battery readings live in
 * [com.pixel.intelligentsearch.core.system.battery.BatteryReadings], which At a Glance shares.
 */
internal object DiagnosticsReadings {

    /** A time estimate in the app's one duration style: "4 hr 41 min", "12 min". */
    fun formatMinutes(minutes: Int): String = com.pixel.intelligentsearch.core.util.Durations.minutes(minutes * 60_000L)

    /** Device-wide thermal throttling state. It describes the whole phone, not the battery. */
    fun thermalLabel(status: Int?): String? = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
        else -> null
    }

    /** /proc/meminfo fields, in KiB. Missing fields are null. */
    data class MemInfo(
        val totalKb: Long?,
        val availableKb: Long?,
        val freeKb: Long?,
        val buffersKb: Long?,
        val cachedKb: Long?,
        val anonPagesKb: Long?,
        val kReclaimableKb: Long?,
        val sUnreclaimKb: Long?,
        val kernelStackKb: Long?,
        val pageTablesKb: Long?,
        val swapTotalKb: Long?,
        val swapFreeKb: Long?
    )

    fun parseMemInfo(text: String): MemInfo {
        val values = HashMap<String, Long>()
        text.lineSequence().forEach { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val number = line.substring(colon + 1).trim().substringBefore(' ').toLongOrNull() ?: return@forEach
            values[line.substring(0, colon).trim()] = number
        }
        return MemInfo(
            totalKb = values["MemTotal"],
            availableKb = values["MemAvailable"],
            freeKb = values["MemFree"],
            buffersKb = values["Buffers"],
            cachedKb = values["Cached"],
            anonPagesKb = values["AnonPages"],
            kReclaimableKb = values["KReclaimable"] ?: values["SReclaimable"],
            sUnreclaimKb = values["SUnreclaim"],
            kernelStackKb = values["KernelStack"],
            pageTablesKb = values["PageTables"],
            swapTotalKb = values["SwapTotal"],
            swapFreeKb = values["SwapFree"]
        )
    }

    /** Memory figures in bytes. Available is the kernel's own MemAvailable estimate; used is total minus that. */
    data class MemoryFigures(val totalBytes: Long, val availableBytes: Long) {
        val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
        val usedPercent: Int get() = if (totalBytes > 0) (usedBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0
        val availablePercent: Int get() = 100 - usedPercent
    }

    /**
     * Prefers /proc/meminfo, falling back to ActivityManager's totalMem/availMem, which come from the same kernel
     * counters (availMem is MemAvailable).
     */
    fun memoryFigures(memInfo: MemInfo?, amTotalBytes: Long, amAvailBytes: Long): MemoryFigures {
        val total = memInfo?.totalKb?.takeIf { it > 0 }?.times(1024L) ?: amTotalBytes
        val avail = memInfo?.availableKb?.takeIf { it >= 0 }?.times(1024L) ?: amAvailBytes
        return MemoryFigures(total.coerceAtLeast(0L), avail.coerceIn(0L, total.coerceAtLeast(0L)))
    }

    /**
     * Splits used memory (total minus MemAvailable) into parts that add up to it, in bytes:
     * app memory (AnonPages, resident anonymous pages of every process including system daemons), kernel
     * (unreclaimable slab, kernel stacks and page tables), and everything else (GPU and DMA buffers, compressed
     * zRAM, locked pages and file cache the kernel cannot reclaim). Null when meminfo lacks the fields.
     */
    data class UsedBreakdown(val appsBytes: Long, val kernelBytes: Long, val otherBytes: Long)

    fun usedBreakdown(memInfo: MemInfo?, usedBytes: Long): UsedBreakdown? {
        val anonKb = memInfo?.anonPagesKb ?: return null
        val kernelKb = (memInfo.sUnreclaimKb ?: return null) + (memInfo.kernelStackKb ?: 0L) + (memInfo.pageTablesKb ?: 0L)
        val apps = (anonKb * 1024L).coerceIn(0L, usedBytes)
        val kernel = (kernelKb * 1024L).coerceIn(0L, usedBytes - apps)
        return UsedBreakdown(apps, kernel, usedBytes - apps - kernel)
    }

    /** Memory pressure from the system low-memory flag and the kernel's available share. */
    fun memoryPressure(lowMemory: Boolean, availablePercent: Int): String = when {
        lowMemory || availablePercent < 10 -> "Critical"
        availablePercent < 15 -> "High Load"
        availablePercent < 25 -> "Moderate"
        else -> "Normal"
    }

    private val RETAIL_RAM_SIZES_GB = intArrayOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32)
    private const val GIB = 1_073_741_824.0

    /**
     * Mirror of AOSP FileUtils.roundStorageSize: compares against binary steps (1, 2, 4 … 512 × 1024ⁿ) and returns the
     * matching decimal size (× 1000ⁿ).
     */
    fun roundStorageSize(size: Long): Long {
        var value = 1L
        var pow = 1L
        var pow1024 = 1L
        while (value * pow1024 < size) {
            value = value shl 1
            if (value > 512) {
                value = 1
                pow *= 1000
                pow1024 *= 1024
            }
        }
        return value * pow
    }

    /**
     * Retail RAM size in GB. ActivityManager.MemoryInfo.advertisedMem (API 34+) comes from the bootloader's DDR size
     * when the device provides one; otherwise Android fills it with roundStorageSize(totalMem), a power-of-two guess
     * that turns 12 GB phones into 16, so that value is ignored. A real advertised value may be binary or decimal;
     * whichever reading lands closer to a whole number wins. Without one, the usable total (which excludes kernel and
     * firmware carve-outs) rounds up to the next retail size.
     */
    fun retailRamGb(advertisedBytes: Long, totalBytes: Long): Int {
        val trustedAdvertised = advertisedBytes > 0 && (totalBytes <= 0 || advertisedBytes != roundStorageSize(totalBytes))
        if (trustedAdvertised) {
            val binary = advertisedBytes / GIB
            val decimal = advertisedBytes / 1_000_000_000.0
            val pick = if (abs(binary - binary.roundToInt()) <= abs(decimal - decimal.roundToInt())) binary else decimal
            pick.roundToInt().takeIf { it > 0 }?.let { return it }
        }
        if (totalBytes <= 0) return 0
        val usableGib = totalBytes / GIB
        return RETAIL_RAM_SIZES_GB.firstOrNull { it >= usableGib } ?: (ceil(usableGib / 4.0) * 4).toInt()
    }
}
