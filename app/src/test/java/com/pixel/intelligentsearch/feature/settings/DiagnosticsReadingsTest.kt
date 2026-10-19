package com.pixel.intelligentsearch.feature.settings

import android.os.BatteryManager
import android.os.PowerManager
import com.pixel.intelligentsearch.feature.settings.DiagnosticsReadings.ChargeState
import com.pixel.intelligentsearch.feature.settings.DiagnosticsReadings.CurrentReader
import com.pixel.intelligentsearch.feature.settings.DiagnosticsReadings.HealthSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are real readings from a Pixel 11 Pro XL (16 GB, Android 17) taken over adb. */
class DiagnosticsReadingsTest {

    private val gib = 1_073_741_824L

    private val pixel11MemInfo = """
        MemTotal:       15516452 kB
        MemFree:         2549040 kB
        MemAvailable:    4796904 kB
        Buffers:            5596 kB
        Cached:          3245072 kB
        SwapCached:         6380 kB
        Active(anon):    1561920 kB
        Inactive(anon):   741344 kB
        SwapTotal:       7758220 kB
        SwapFree:        2057008 kB
        AnonPages:       2263396 kB
        Shmem:            143792 kB
        KReclaimable:     744872 kB
        Slab:            1164016 kB
        SReclaimable:     326916 kB
        SUnreclaim:       837100 kB
        KernelStack:      206708 kB
        PageTables:       366432 kB
    """.trimIndent()

    // --- Battery health ---

    @Test
    fun healthCode11MeansNotReportedNotOptimized() {
        assertEquals(DiagnosticsReadings.HEALTH_NOT_REPORTED, DiagnosticsReadings.healthLabel(11))
        assertEquals(HealthSeverity.Ok, DiagnosticsReadings.healthSeverity(11))
    }

    @Test
    fun healthCode8IsAWornBatteryCaution() {
        assertEquals("Fair (worn)", DiagnosticsReadings.healthLabel(8))
        assertEquals(HealthSeverity.Caution, DiagnosticsReadings.healthSeverity(8))
    }

    @Test
    fun unknownInconsistentAndUnrecognisedCodesAreNotReported() {
        listOf(BatteryManager.BATTERY_HEALTH_UNKNOWN, 12, 9, 42, -1).forEach {
            assertEquals("code $it", DiagnosticsReadings.HEALTH_NOT_REPORTED, DiagnosticsReadings.healthLabel(it))
            assertEquals("code $it", HealthSeverity.Ok, DiagnosticsReadings.healthSeverity(it))
        }
    }

    @Test
    fun standardHealthCodesKeepTheirMeaning() {
        assertEquals("Good", DiagnosticsReadings.healthLabel(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals(HealthSeverity.Ok, DiagnosticsReadings.healthSeverity(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals(HealthSeverity.Bad, DiagnosticsReadings.healthSeverity(BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals(HealthSeverity.Bad, DiagnosticsReadings.healthSeverity(BatteryManager.BATTERY_HEALTH_DEAD))
    }

    // --- Charging state ---

    @Test
    fun pluggedInButNotChargingIsNotDischarging() {
        val usb = BatteryManager.BATTERY_PLUGGED_USB
        assertEquals(ChargeState.PluggedNotCharging, DiagnosticsReadings.chargeState(BatteryManager.BATTERY_STATUS_NOT_CHARGING, usb))
        assertEquals(ChargeState.Charging, DiagnosticsReadings.chargeState(BatteryManager.BATTERY_STATUS_CHARGING, usb))
        assertEquals(ChargeState.Full, DiagnosticsReadings.chargeState(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals(ChargeState.PluggedUnknown, DiagnosticsReadings.chargeState(BatteryManager.BATTERY_STATUS_UNKNOWN, usb))
    }

    @Test
    fun unpluggedIsAlwaysDischargingWhateverTheStatus() {
        listOf(
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ).forEach { assertEquals("status $it", ChargeState.Discharging, DiagnosticsReadings.chargeState(it, 0)) }
    }

    // --- Current unit and sign ---

    private val settle = 10_000L

    /** Feeds [count] CURRENT_NOW readings 2.5 s apart starting at [startMs]; returns the time after the last one. */
    private fun CurrentReader.feed(raw: Int, unplugged: Boolean, startMs: Long, count: Int): Long {
        var t = startMs
        repeat(count) { readNow(raw, unplugged, t); t += 2_500L }
        return t
    }

    @Test
    fun pixelMicroampReadingIsProvenAtOnceAndKeepsItsSign() {
        val reader = CurrentReader(settle)
        assertEquals(-150f, reader.readNow(-150_000, unplugged = true, nowMs = 0L)!!, 0.01f)
        assertEquals(CurrentReader.Unit.Microamps, reader.unit)
        // Sign locks only after settled, agreeing readings.
        assertFalse(reader.signLocked)
        reader.feed(-150_000, unplugged = true, startMs = settle, count = CurrentReader.CONFIRMATIONS)
        assertTrue(reader.signLocked)
        assertFalse(reader.invertedSign)
    }

    @Test
    fun pixelPluggedInNotChargingIsProvenAndReadsAsADrain() {
        // The Pixel 11's real state: USB, status NOT_CHARGING, current_now -150000 µA.
        val reader = CurrentReader(settle)
        assertEquals(-150f, reader.readNow(-150_000, unplugged = false, nowMs = 0L)!!, 0.01f)
    }

    @Test
    fun smallReadingWhilePluggedInIsNotGuessedBeforeTheUnitIsKnown() {
        // A full Pixel on a charger can read +2000 µA; the old rule would show 2000 mA / 7.7 W.
        val reader = CurrentReader(settle)
        assertNull(reader.readNow(2000, unplugged = false, nowMs = 0L))
        assertEquals(CurrentReader.Unit.Unknown, reader.unit)
        reader.readNow(-150_000, unplugged = false, nowMs = 2_500L)
        assertEquals(2f, reader.readNow(2000, unplugged = false, nowMs = 5_000L)!!, 0.01f)
    }

    @Test
    fun samsungStyleMilliampsNeedSettledConfirmationsWhileUnplugged() {
        val reader = CurrentReader(settle)
        // Unsettled readings right after unplugging prove nothing.
        assertNull(reader.readNow(-450, unplugged = true, nowMs = 0L))
        reader.feed(-450, unplugged = true, startMs = settle, count = CurrentReader.CONFIRMATIONS - 1)
        assertEquals(CurrentReader.Unit.Unknown, reader.unit)
        reader.readNow(-450, unplugged = true, nowMs = 2 * settle)
        assertEquals(CurrentReader.Unit.Milliamps, reader.unit)
        // A 2.5 A fast charge stays 2500 mA (the old >1000 rule made it 2.5 mA); 12 A stays milliamps.
        assertEquals(2500f, reader.readNow(2500, unplugged = false, nowMs = 3 * settle)!!, 0.01f)
        assertEquals(12_000f, reader.readNow(12_000, unplugged = false, nowMs = 3 * settle + 2_500L)!!, 0.01f)
    }

    @Test
    fun aWronglyLearnedMilliampUnitIsOverturnedByAMicroampReading() {
        val reader = CurrentReader(settle)
        // A µA phone at 100 % with a near-zero stale reading looks like milliamps for a while.
        reader.readNow(3000, unplugged = true, nowMs = 0L)
        reader.feed(3000, unplugged = true, startMs = settle, count = CurrentReader.CONFIRMATIONS)
        assertEquals(CurrentReader.Unit.Milliamps, reader.unit)
        assertEquals(-300f, reader.readNow(-300_000, unplugged = true, nowMs = 10 * settle)!!, 0.01f)
        assertEquals(CurrentReader.Unit.Microamps, reader.unit)
    }

    @Test
    fun invertedSignVendorsAreLearnedFromSettledUnpluggedReadings() {
        val reader = CurrentReader(settle)
        reader.readNow(300_000, unplugged = true, nowMs = 0L) // unsettled: no learning yet
        assertFalse(reader.invertedSign)
        reader.feed(300_000, unplugged = true, startMs = settle, count = CurrentReader.CONFIRMATIONS)
        assertTrue(reader.invertedSign)
        assertTrue(reader.signLocked)
        // On the charger the same vendor's negative reading is charge flowing in.
        assertEquals(1200f, reader.readNow(-1_200_000, unplugged = false, nowMs = 10 * settle)!!, 0.01f)
    }

    @Test
    fun aStalePositiveReadingAfterUnpluggingCannotFlipTheSign() {
        val reader = CurrentReader(settle)
        reader.readNow(-300_000, unplugged = true, nowMs = 0L)
        reader.feed(-300_000, unplugged = true, startMs = settle, count = CurrentReader.CONFIRMATIONS)
        assertTrue(reader.signLocked)
        // Plug in, unplug: the fuel gauge still reports charge current for a few polls.
        reader.readNow(1_500_000, unplugged = false, nowMs = 100_000L)
        reader.feed(1_500_000, unplugged = true, startMs = 110_000L, count = 4)
        assertFalse(reader.invertedSign)
        // Even settled contrary readings need a long run to overturn a locked sign.
        reader.feed(1_500_000, unplugged = true, startMs = 130_000L, count = CurrentReader.SIGN_RELEARN - 1)
        assertFalse(reader.invertedSign)
        // Plugging back in still reads as charging.
        assertEquals(2000f, reader.readNow(2_000_000, unplugged = false, nowMs = 300_000L)!!, 0.01f)
    }

    @Test
    fun restartingTheSettleWindowKeepsWhatWasLearned() {
        val reader = CurrentReader(settle)
        reader.readNow(-300_000, unplugged = true, nowMs = 0L)
        reader.feed(-300_000, unplugged = true, startMs = settle, count = CurrentReader.CONFIRMATIONS)
        assertTrue(reader.isSettled(20_000L))
        // Page reopened (or charge state changed): readings start a fresh settle window.
        reader.restartSettleWindow()
        assertFalse(reader.isSettled(20_000L))
        reader.readNow(-300_000, unplugged = true, nowMs = 50_000L)
        assertFalse(reader.isSettled(55_000L))
        assertTrue(reader.isSettled(60_000L))
        assertEquals(CurrentReader.Unit.Microamps, reader.unit)
        assertTrue(reader.signLocked)
    }

    @Test
    fun averageIsConvertedWithoutTeachingTheReader() {
        val reader = CurrentReader(settle)
        assertNull(reader.convert(-350_000)) // unit unknown: CURRENT_AVERAGE alone proves nothing
        assertEquals(CurrentReader.Unit.Unknown, reader.unit)
        reader.readNow(-150_000, unplugged = true, nowMs = 0L)
        assertEquals(-350f, reader.convert(-350_000)!!, 0.01f)
    }

    @Test
    fun averageOnlyStandsInWhenItAgreesWithTheLiveReading() {
        assertTrue(DiagnosticsReadings.averageAgrees(-150f, -300f))
        assertFalse(DiagnosticsReadings.averageAgrees(-150f, 300f)) // lagging average from before a plug change
        assertFalse(DiagnosticsReadings.averageAgrees(-350f, -0.35f)) // average reported in mA on a µA device
        assertFalse(DiagnosticsReadings.averageAgrees(-450f, -450_000f)) // and the reverse
        assertFalse(DiagnosticsReadings.averageAgrees(null, -300f))
    }

    @Test
    fun missingCurrentIsNull() {
        val reader = CurrentReader(settle)
        assertNull(reader.readNow(0, unplugged = true, nowMs = 0L))
        assertNull(reader.readNow(Int.MIN_VALUE, unplugged = true, nowMs = 0L))
        assertNull(reader.convert(Int.MIN_VALUE))
        assertEquals(CurrentReader.Unit.Unknown, reader.unit)
    }

    // --- Time estimates ---

    @Test
    fun timeUntilEmptyNeedsARealDrain() {
        // 2337.5 mAh left at a 150 mA drain is about 15.6 h.
        assertEquals(935, DiagnosticsReadings.minutesUntilEmpty(2337.5f, -150f))
        assertNull(DiagnosticsReadings.minutesUntilEmpty(2337.5f, 150f)) // gaining charge
        assertNull(DiagnosticsReadings.minutesUntilEmpty(2337.5f, -5f))
        assertNull(DiagnosticsReadings.minutesUntilEmpty(null, -150f))
        assertNull(DiagnosticsReadings.minutesUntilEmpty(2337.5f, null))
    }

    @Test
    fun timeUntilFullNeedsChargeFlowingIn() {
        // 45 % with 2337.5 mAh implies ~5194 mAh full; 2857 mAh to go at 1000 mA is ~171 min.
        assertEquals(171, DiagnosticsReadings.minutesUntilFull(2337.5f, 45, 1000f))
        assertNull(DiagnosticsReadings.minutesUntilFull(2337.5f, 45, -150f)) // draining on a weak charger
        assertNull(DiagnosticsReadings.minutesUntilFull(2337.5f, 5, 1000f))
        assertNull(DiagnosticsReadings.minutesUntilFull(2337.5f, 100, 1000f))
    }

    @Test
    fun smoothingMovesTowardNewReadingsWithoutJumping() {
        assertEquals(400f, DiagnosticsReadings.smooth(null, 400f), 0.01f)
        assertEquals(175f, DiagnosticsReadings.smooth(100f, 400f), 0.01f)
        assertEquals(-175f, DiagnosticsReadings.smooth(-100f, -400f), 0.01f)
    }

    @Test
    fun formatsMinutes() {
        assertEquals("4h 41m", DiagnosticsReadings.formatMinutes(281))
        assertEquals("12m", DiagnosticsReadings.formatMinutes(12))
    }

    @Test
    fun thermalStatusLabelsMatchThrottlingLevels() {
        assertEquals("Normal", DiagnosticsReadings.thermalLabel(PowerManager.THERMAL_STATUS_NONE))
        assertEquals("Light", DiagnosticsReadings.thermalLabel(PowerManager.THERMAL_STATUS_LIGHT))
        assertEquals("Severe", DiagnosticsReadings.thermalLabel(PowerManager.THERMAL_STATUS_SEVERE))
        assertNull(DiagnosticsReadings.thermalLabel(null))
    }

    // --- Memory ---

    @Test
    fun parsesPixelMemInfo() {
        val m = DiagnosticsReadings.parseMemInfo(pixel11MemInfo)
        assertEquals(15_516_452L, m.totalKb)
        assertEquals(4_796_904L, m.availableKb)
        assertEquals(2_263_396L, m.anonPagesKb)
        assertEquals(837_100L, m.sUnreclaimKb)
        assertEquals(7_758_220L, m.swapTotalKb)
        assertEquals(2_057_008L, m.swapFreeKb)
    }

    @Test
    fun availableMemoryIsTheKernelsMemAvailable() {
        val figures = DiagnosticsReadings.memoryFigures(DiagnosticsReadings.parseMemInfo(pixel11MemInfo), 0L, 0L)
        assertEquals(15_516_452L * 1024, figures.totalBytes)
        assertEquals(4_796_904L * 1024, figures.availableBytes)
        // 4.9 GB available, 11.0 GB used of 15.9 GB (the old blend claimed 8.6 GB free).
        assertEquals(4.91, figures.availableBytes / 1e9, 0.01)
        assertEquals(10.97, figures.usedBytes / 1e9, 0.01)
        assertEquals(69, figures.usedPercent)
        assertEquals("Normal", DiagnosticsReadings.memoryPressure(false, figures.availablePercent))
    }

    @Test
    fun memoryFallsBackToActivityManagerWhenMemInfoIsUnreadable() {
        val figures = DiagnosticsReadings.memoryFigures(null, 8_000_000_000L, 2_000_000_000L)
        assertEquals(8_000_000_000L, figures.totalBytes)
        assertEquals(6_000_000_000L, figures.usedBytes)
        assertEquals(75, figures.usedPercent)
        assertNull(DiagnosticsReadings.usedBreakdown(null, figures.usedBytes))
    }

    @Test
    fun usedBreakdownAddsUpToUsed() {
        val mem = DiagnosticsReadings.parseMemInfo(pixel11MemInfo)
        val used = DiagnosticsReadings.memoryFigures(mem, 0L, 0L).usedBytes
        val b = DiagnosticsReadings.usedBreakdown(mem, used)!!
        assertEquals(used, b.appsBytes + b.kernelBytes + b.otherBytes)
        assertEquals(2_263_396L * 1024, b.appsBytes)
        assertEquals((837_100L + 206_708L + 366_432L) * 1024, b.kernelBytes)
        // The remainder (GPU, DMA buffers, zRAM, locked and unreclaimable cache) is ~7.2 GB on this phone.
        assertEquals(7.22, b.otherBytes / 1e9, 0.01)
    }

    @Test
    fun usedBreakdownNeverGoesNegative() {
        val mem = DiagnosticsReadings.parseMemInfo(pixel11MemInfo)
        val b = DiagnosticsReadings.usedBreakdown(mem, 1_000_000_000L)!!
        assertEquals(1_000_000_000L, b.appsBytes + b.kernelBytes + b.otherBytes)
        assertTrue(b.kernelBytes >= 0 && b.otherBytes >= 0)
    }

    @Test
    fun memoryPressureRespectsTheLowMemoryFlag() {
        assertEquals("Critical", DiagnosticsReadings.memoryPressure(true, 50))
        assertEquals("Critical", DiagnosticsReadings.memoryPressure(false, 8))
        assertEquals("High Load", DiagnosticsReadings.memoryPressure(false, 12))
        assertEquals("Moderate", DiagnosticsReadings.memoryPressure(false, 20))
    }

    // --- Retail RAM size ---

    @Test
    fun roundStorageSizeMatchesAosp() {
        assertEquals(16_000_000_000L, DiagnosticsReadings.roundStorageSize(12_030_000_000L))
        assertEquals(16_000_000_000L, DiagnosticsReadings.roundStorageSize(15_888_846_848L))
        assertEquals(8_000_000_000L, DiagnosticsReadings.roundStorageSize(5_600_000_000L))
        assertEquals(512_000_000L, DiagnosticsReadings.roundStorageSize(400_000_000L))
        assertEquals(1_000_000_000L, DiagnosticsReadings.roundStorageSize(600_000_000L))
        // Between 8×10⁹ and 8×2³⁰ AOSP compares against the binary step, so it stays at 8 GB.
        assertEquals(8_000_000_000L, DiagnosticsReadings.roundStorageSize(8_100_000_000L))
    }

    @Test
    fun advertisedMemoryWinsInEitherUnit() {
        assertEquals(16, DiagnosticsReadings.retailRamGb(16 * gib, 15_888_846_848L)) // Pixel 11: ddr_size=16GiB
        assertEquals(12, DiagnosticsReadings.retailRamGb(12_000_000_000L, (11.2 * gib).toLong()))
        assertEquals(12, DiagnosticsReadings.retailRamGb(12 * gib, 0L))
    }

    @Test
    fun androidsPowerOfTwoAdvertisedFallbackIsIgnored() {
        // Without ro.boot.ddr_size, Android sets advertisedMem = roundStorageSize(totalMem): 16 GB for a 12 GB phone.
        // Values worked out by hand from AOSP FileUtils.roundStorageSize, not from the app's copy.
        assertEquals(12, DiagnosticsReadings.retailRamGb(16_000_000_000L, (11.2 * gib).toLong()))
        assertEquals(6, DiagnosticsReadings.retailRamGb(8_000_000_000L, (5.5 * gib).toLong()))
        assertEquals(3, DiagnosticsReadings.retailRamGb(4_000_000_000L, (2.7 * gib).toLong()))
        // An 8 GB phone whose total lands between 8×10⁹ and 8×2³⁰ gets 8 GB from AOSP; either way the answer is 8.
        assertEquals(8, DiagnosticsReadings.retailRamGb(8_000_000_000L, 8_100_000_000L))
    }

    @Test
    fun withoutAdvertisedMemoryTheUsableTotalRoundsUpToARetailSize() {
        assertEquals(16, DiagnosticsReadings.retailRamGb(-1L, 15_888_846_848L)) // Pixel 11: 14.8 GiB usable
        assertEquals(3, DiagnosticsReadings.retailRamGb(-1L, (2.7 * gib).toLong())) // old buckets said 4
        assertEquals(18, DiagnosticsReadings.retailRamGb(-1L, (17.1 * gib).toLong())) // old buckets said 24
        assertEquals(32, DiagnosticsReadings.retailRamGb(-1L, (30.5 * gib).toLong())) // old buckets said 24
        assertEquals(12, DiagnosticsReadings.retailRamGb(-1L, (11.2 * gib).toLong()))
        assertEquals(0, DiagnosticsReadings.retailRamGb(-1L, 0L))
    }
}
