package com.pixel.intelligentsearch.core.system.battery

import android.os.BatteryManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure interpretation of the raw values the battery broadcast and BatteryManager report: health, charging state,
 * current and the time estimates built on it. Kept free of Android calls so each rule can be unit tested against
 * real device readings; the diagnostics page and At a Glance both read the battery through it.
 *
 * Apps cannot read /sys/class/power_supply (SELinux denies sysfs_batteryinfo to untrusted apps), and battery
 * state of health is a system-only API, so everything here works from the public battery broadcast and
 * BatteryManager properties.
 */
internal object BatteryReadings {

    // android.hardware.health BatteryHealth values that BatteryManager does not expose as constants.
    private const val HEALTH_FAIR = 8
    private const val HEALTH_NOT_AVAILABLE = 11
    private const val HEALTH_INCONSISTENT = 12

    /** Shown when the phone does not rate its battery (codes 1, 11, 12 and anything unrecognised). */
    const val HEALTH_NOT_REPORTED = "Not reported"

    /** Maps the broadcast's EXTRA_HEALTH code to a label, or [HEALTH_NOT_REPORTED] when there is no rating. */
    fun healthLabel(code: Int): String = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        HEALTH_FAIR -> "Fair (worn)"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        BatteryManager.BATTERY_HEALTH_UNKNOWN, HEALTH_NOT_AVAILABLE, HEALTH_INCONSISTENT -> HEALTH_NOT_REPORTED
        else -> HEALTH_NOT_REPORTED
    }

    enum class HealthSeverity { Ok, Caution, Bad }

    private val BAD_HEALTH_CODES = setOf(
        BatteryManager.BATTERY_HEALTH_OVERHEAT,
        BatteryManager.BATTERY_HEALTH_COLD,
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE,
        BatteryManager.BATTERY_HEALTH_DEAD,
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE
    )

    /** Fair (a marginal, worn battery) is a caution; overheating, cold, over voltage, dead and failure are bad. */
    fun healthSeverity(code: Int): HealthSeverity = when (code) {
        HEALTH_FAIR -> HealthSeverity.Caution
        in BAD_HEALTH_CODES -> HealthSeverity.Bad
        else -> HealthSeverity.Ok
    }

    enum class ChargeState { Charging, Full, PluggedNotCharging, PluggedUnknown, Discharging }

    /**
     * Charging state from EXTRA_STATUS and EXTRA_PLUGGED. NOT_CHARGING while plugged in means the charger is not
     * adding charge (adaptive charging, a charge limit or a weak port); the battery may still be draining.
     */
    fun chargeState(status: Int, plugged: Int): ChargeState = when {
        plugged == 0 -> ChargeState.Discharging
        status == BatteryManager.BATTERY_STATUS_CHARGING -> ChargeState.Charging
        status == BatteryManager.BATTERY_STATUS_FULL -> ChargeState.Full
        status == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeState.PluggedNotCharging
        status == BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeState.PluggedNotCharging
        else -> ChargeState.PluggedUnknown
    }

    /**
     * Learns how this device reports battery current from CURRENT_NOW. BatteryManager documents microamps with
     * positive meaning charge flowing in, but some vendors (many Samsung models) report milliamps and some invert the
     * sign, so nothing is guessed:
     * - microamps: any reading of 25,000 or more, since no phone battery moves 25 A. This wins over an earlier
     *   milliamps decision, because a milliamp device can never produce such a reading;
     * - milliamps: [CONFIRMATIONS] settled readings in a row under 10,000 while unplugged. This page is on screen, so
     *   a real drain is well above 10 mA, which in microamps would read at least 10,000.
     * The sign is learned from settled unplugged readings, which can only be outflow: it locks after
     * [CONFIRMATIONS] agreeing readings and only changes after [SIGN_RELEARN] contrary readings in a row.
     * "Settled" means [settleMs] after the last plug change, so stale fuel-gauge values from before it are ignored.
     * One instance lives for the process so reopening the page keeps what was learned.
     */
    class CurrentReader(private val settleMs: Long = 10_000L) {
        enum class Unit { Unknown, Microamps, Milliamps }

        var unit: Unit = Unit.Unknown
            private set
        /** True when this vendor reports outflow as positive. */
        var invertedSign: Boolean = false
            private set
        var signLocked: Boolean = false
            private set

        private var lastUnplugged: Boolean? = null
        private var plugChangedAtMs = 0L
        private var smallUnpluggedRun = 0
        private var signAgreeRun = 0
        private var signContraryRun = 0

        @Synchronized
        fun isSettled(nowMs: Long): Boolean = lastUnplugged != null && nowMs - plugChangedAtMs >= settleMs

        /**
         * Restarts the settle window at the next reading, keeping the learned unit and sign. Called when the page
         * opens (plug changes made while it was closed were not seen) and when the charging state changes.
         */
        @Synchronized
        fun restartSettleWindow() {
            lastUnplugged = null
        }

        /**
         * Feeds a CURRENT_NOW reading and learns from it. Returns the net current into the battery in milliamps
         * (negative when draining), or null when there is no reading or the unit is not yet known.
         */
        @Synchronized
        fun readNow(raw: Int, unplugged: Boolean, nowMs: Long): Float? {
            if (unplugged != lastUnplugged) {
                lastUnplugged = unplugged
                plugChangedAtMs = nowMs
                smallUnpluggedRun = 0
                signAgreeRun = 0
                signContraryRun = 0
            }
            if (raw == 0 || raw == Int.MIN_VALUE) return null
            val magnitude = abs(raw.toLong())
            val settled = nowMs - plugChangedAtMs >= settleMs

            if (magnitude >= MICROAMP_PROOF) {
                unit = Unit.Microamps
            } else if (unit == Unit.Unknown && unplugged && settled) {
                if (magnitude < MILLIAMP_PROOF_CEILING) {
                    if (++smallUnpluggedRun >= CONFIRMATIONS) unit = Unit.Milliamps
                } else {
                    smallUnpluggedRun = 0
                }
            }

            val ma = toMilliamps(magnitude) ?: return null
            if (unplugged && settled && ma >= 20f) learnSign(evidenceInverted = raw > 0)
            return signed(raw, ma)
        }

        /** Converts another reading (CURRENT_AVERAGE) with what CURRENT_NOW taught, without learning from it. */
        @Synchronized
        fun convert(raw: Int): Float? {
            if (raw == 0 || raw == Int.MIN_VALUE) return null
            val ma = toMilliamps(abs(raw.toLong())) ?: return null
            return signed(raw, ma)
        }

        private fun toMilliamps(magnitude: Long): Float? = when (unit) {
            Unit.Microamps -> magnitude / 1000f
            Unit.Milliamps -> magnitude.toFloat()
            Unit.Unknown -> null
        }

        private fun signed(raw: Int, ma: Float): Float {
            val standardSign = if (raw > 0) ma else -ma
            return if (invertedSign) -standardSign else standardSign
        }

        private fun learnSign(evidenceInverted: Boolean) {
            if (evidenceInverted == invertedSign) {
                signContraryRun = 0
                if (!signLocked && ++signAgreeRun >= CONFIRMATIONS) signLocked = true
            } else {
                signAgreeRun = 0
                if (++signContraryRun >= (if (signLocked) SIGN_RELEARN else CONFIRMATIONS)) {
                    invertedSign = evidenceInverted
                    signLocked = true
                    signContraryRun = 0
                }
            }
        }

        companion object {
            const val MICROAMP_PROOF = 25_000L
            const val MILLIAMP_PROOF_CEILING = 10_000L
            const val CONFIRMATIONS = 3
            const val SIGN_RELEARN = 10
        }
    }

    /**
     * Whether a CURRENT_AVERAGE reading can stand in for CURRENT_NOW in the time estimates: same direction and within
     * 10x. Otherwise the vendor likely reports it in another unit or sign, or it still averages over a past state.
     */
    fun averageAgrees(nowMa: Float?, avgMa: Float?): Boolean {
        if (nowMa == null || avgMa == null || nowMa == 0f || avgMa == 0f) return false
        if ((nowMa > 0f) != (avgMa > 0f)) return false
        val ratio = abs(avgMa) / abs(nowMa)
        return ratio in 0.1f..10f
    }

    /** Exponential smoothing for the time estimates, which would otherwise jump with every reading. */
    fun smooth(previous: Float?, next: Float, alpha: Float = 0.25f): Float =
        if (previous == null) next else previous + alpha * (next - previous)

    /**
     * Minutes until empty from the remaining charge (CHARGE_COUNTER, mAh) and the smoothed net current, or null when
     * either is unknown or the battery is not draining meaningfully.
     */
    fun minutesUntilEmpty(remainingMah: Float?, netMa: Float?): Int? {
        if (remainingMah == null || remainingMah <= 0f || netMa == null || netMa > -20f) return null
        return (remainingMah / -netMa * 60f).roundToInt().takeIf { it > 0 }
    }

    /**
     * Minutes until full from the current charge, the level, and the smoothed net current. The full capacity is
     * inferred from CHARGE_COUNTER and the level, so it is only trusted from 10 % upwards.
     */
    fun minutesUntilFull(chargeMah: Float?, level: Int, netMa: Float?): Int? {
        if (chargeMah == null || chargeMah <= 0f || level !in 10..99 || netMa == null || netMa < 20f) return null
        val fullMah = chargeMah / (level / 100f)
        return ((fullMah - chargeMah) / netMa * 60f).roundToInt().takeIf { it > 0 }
    }
}
