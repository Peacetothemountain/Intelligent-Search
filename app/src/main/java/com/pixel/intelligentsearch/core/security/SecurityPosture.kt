package com.pixel.intelligentsearch.core.security

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Scores the device's security posture for the Hardware & Security page's radar chart, from what the page has
 * already measured: where keys are kept, the key attestation result (its chain, and the boot state and bootloader
 * lock it reports), the security patch date and whether a screen lock is set. No Play Integrity call is involved.
 *
 * Each axis scores 0..1. Pure, so the scoring can be unit tested.
 */
object SecurityPosture {

    /**
     * The chart's axes, clockwise from the top. The patch sits at the bottom, where its long value ("5 Months Old")
     * has the full width to itself.
     */
    enum class Axis(val label: String) {
        KeyStorage("Key Storage"),
        Attestation("Attestation"),
        VerifiedBoot("Verified Boot"),
        PatchLevel("Security Patch"),
        Bootloader("Bootloader"),
        ScreenLock("Screen Lock")
    }

    /** How an axis reads: [Good] and [Fair] are healthy to middling, [Weak] a real gap, [Pending] still being checked. */
    enum class Rating { Good, Fair, Weak, Pending }

    data class AxisScore(val axis: Axis, val score: Float, val value: String, val rating: Rating)

    /**
     * @param keyLevel where this app's keys are kept (StrongBox, TEE or software).
     * @param attestation the key attestation result, or null while it is still running.
     * @param securityPatch the patch date as `YYYY-MM-DD` (Build.VERSION.SECURITY_PATCH), used when the attestation does not
     *   carry one.
     * @param deviceSecure whether a PIN, pattern or password is set (KeyguardManager.isDeviceSecure).
     */
    fun score(
        keyLevel: HardwareSecurityLevel,
        attestation: AttestationResult?,
        securityPatch: String?,
        today: LocalDate,
        deviceSecure: Boolean
    ): List<AxisScore> {
        // Boot state, lock and patch level come from the attestation certificate. With no certificate there is nothing
        // to read; with a chain that doesn't verify back to a hardware root, what it claims is unconfirmed.
        val hasCertificate = attestation != null && attestation.certificateCount > 0
        val trusted = hasCertificate && attestation?.isHardwareAttested == true
        return listOf(
            when (keyLevel) {
                HardwareSecurityLevel.STRONGBOX -> AxisScore(Axis.KeyStorage, 1f, "StrongBox", Rating.Good)
                HardwareSecurityLevel.TEE -> AxisScore(Axis.KeyStorage, 0.75f, "TEE", Rating.Good)
                HardwareSecurityLevel.SOFTWARE -> AxisScore(Axis.KeyStorage, 0.2f, "Software", Rating.Weak)
            },
            when {
                attestation == null -> pending(Axis.Attestation)
                attestation.isHardwareAttested -> AxisScore(Axis.Attestation, 1f, "Verified", Rating.Good)
                attestation.isRootVerified -> AxisScore(Axis.Attestation, 0.4f, "Software Root", Rating.Fair)
                hasCertificate -> AxisScore(Axis.Attestation, 0.25f, "Unverified", Rating.Weak)
                else -> AxisScore(Axis.Attestation, 0.1f, "Unavailable", Rating.Weak)
            },
            when {
                attestation == null -> pending(Axis.VerifiedBoot)
                !hasCertificate -> AxisScore(Axis.VerifiedBoot, 0.1f, "Unknown", Rating.Weak)
                !trusted -> AxisScore(Axis.VerifiedBoot, 0.3f, "Unconfirmed", Rating.Fair)
                else -> when (attestation.verifiedBootState) {
                    "VERIFIED" -> AxisScore(Axis.VerifiedBoot, 1f, "Verified", Rating.Good)
                    // A custom OS signed with the owner's key and a relocked bootloader: verified, but not by the maker.
                    "SELF_SIGNED" -> AxisScore(Axis.VerifiedBoot, 0.6f, "Self-Signed", Rating.Fair)
                    "UNVERIFIED" -> AxisScore(Axis.VerifiedBoot, 0.2f, "Unverified", Rating.Weak)
                    "FAILED" -> AxisScore(Axis.VerifiedBoot, 0f, "Failed", Rating.Weak)
                    else -> AxisScore(Axis.VerifiedBoot, 0.1f, "Unknown", Rating.Weak)
                }
            },
            patchScore(attestation?.osPatchLevel?.takeIf { trusted }, securityPatch, today),
            when {
                attestation == null -> pending(Axis.Bootloader)
                !hasCertificate -> AxisScore(Axis.Bootloader, 0.1f, "Unknown", Rating.Weak)
                !trusted -> AxisScore(Axis.Bootloader, 0.3f, "Unconfirmed", Rating.Fair)
                attestation.deviceLocked -> AxisScore(Axis.Bootloader, 1f, "Locked", Rating.Good)
                else -> AxisScore(Axis.Bootloader, 0.15f, "Unlocked", Rating.Weak)
            },
            if (deviceSecure) {
                AxisScore(Axis.ScreenLock, 1f, "Set", Rating.Good)
            } else {
                AxisScore(Axis.ScreenLock, 0.15f, "None", Rating.Weak)
            }
        )
    }

    private fun pending(axis: Axis) = AxisScore(axis, 0f, "Checking...", Rating.Pending)

    /**
     * Patch freshness: full marks within a month of the patch date, then a tenth less for each further month, never
     * below 0.1. The attestation's patch level (`YYYYMM` or `YYYYMMDD`) wins, as the hardware vouches for it.
     */
    internal fun patchScore(attestedPatchLevel: Int?, securityPatch: String?, today: LocalDate): AxisScore {
        val patchDate = attestedPatchLevel?.let(::parseAttestedPatch) ?: securityPatch?.let(::parsePatchString)
            ?: return AxisScore(Axis.PatchLevel, 0.1f, "Unknown", Rating.Weak)
        val months = patchAgeMonths(patchDate, today)
        val score = (1f - (months - 1).coerceAtLeast(0) / 10f).coerceIn(0.1f, 1f)
        val value = when {
            months <= 0 -> "Current"
            months == 1 -> "1 Month Old"
            else -> "$months Months Old"
        }
        val rating = when {
            months <= 2 -> Rating.Good
            months <= 6 -> Rating.Fair
            else -> Rating.Weak
        }
        return AxisScore(Axis.PatchLevel, score, value, rating)
    }

    /** Whole months from the patch to today, counting a part month as none (a patch from last month is one month old). */
    internal fun patchAgeMonths(patch: LocalDate, today: LocalDate): Int =
        ChronoUnit.MONTHS.between(patch.withDayOfMonth(1), today.withDayOfMonth(1)).toInt().coerceAtLeast(0)

    internal fun parseAttestedPatch(level: Int): LocalDate? {
        if (level <= 0) return null
        val (year, month) = when {
            level >= 1_000_000 -> level / 10_000 to (level / 100) % 100
            level >= 100_000 -> level / 100 to level % 100
            else -> return null
        }
        return if (year in 2000..2200 && month in 1..12) LocalDate.of(year, month, 1) else null
    }

    internal fun parsePatchString(patch: String): LocalDate? {
        val parts = patch.trim().split('-')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return if (year in 2000..2200 && month in 1..12) LocalDate.of(year, month, 1) else null
    }

    /** The mean of the axes that have been checked, 0..1; null while every axis is still pending. */
    fun overall(axes: List<AxisScore>): Float? {
        val checked = axes.filter { it.rating != Rating.Pending }
        return if (checked.isEmpty()) null else checked.map { it.score }.average().toFloat()
    }

    /** A word for the overall score, for the chart's header chip. */
    fun overallLabel(overall: Float?): String = when {
        overall == null -> "Checking..."
        overall >= 0.85f -> "Strong"
        overall >= 0.65f -> "Good"
        overall >= 0.4f -> "Fair"
        else -> "Weak"
    }

    /** What the chart shows, for screen readers. */
    fun describe(axes: List<AxisScore>): String {
        val overall = overall(axes)
        val summary = if (overall == null) {
            "Security posture: checking"
        } else {
            "Security posture: ${overallLabel(overall)}, ${(overall * 100).roundToInt()} percent"
        }
        return summary + axes.joinToString(separator = "") { ". ${it.axis.label}: ${it.value}" } + "."
    }
}
