package com.pixel.intelligentsearch.core.security

import com.pixel.intelligentsearch.core.security.SecurityPosture.Axis
import com.pixel.intelligentsearch.core.security.SecurityPosture.Rating
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Hardware & Security radar chart scores each check from the page's own readings. */
class SecurityPostureTest {

    private val today = LocalDate.of(2026, 10, 22)

    private fun attested(
        bootState: String = "VERIFIED",
        locked: Boolean = true,
        patch: Int = 202610,
        hardwareAttested: Boolean = true,
        rootVerified: Boolean = true,
        certificates: Int = 4
    ) = AttestationResult(
        isHardwareAttested = hardwareAttested,
        securityLevel = HardwareSecurityLevel.STRONGBOX,
        attestationChallenge = "",
        certificateCount = certificates,
        issuerName = "",
        verifiedBootState = bootState,
        deviceLocked = locked,
        osPatchLevel = patch,
        isRootVerified = rootVerified
    )

    private fun score(
        keyLevel: HardwareSecurityLevel = HardwareSecurityLevel.STRONGBOX,
        attestation: AttestationResult? = attested(),
        patch: String? = "2026-10-05",
        secure: Boolean = true
    ) = SecurityPosture.score(keyLevel, attestation, patch, today, secure)

    private fun List<SecurityPosture.AxisScore>.of(axis: Axis) = single { it.axis == axis }

    @Test
    fun aLockedVerifiedPatchedPixelScoresFullMarks() {
        val axes = score()
        assertEquals(Axis.entries.toList(), axes.map { it.axis })
        axes.filter { it.axis != Axis.KeyStorage }.forEach { assertEquals(it.axis.name, 1f, it.score, 0f) }
        assertEquals(1f, axes.of(Axis.KeyStorage).score, 0f)
        assertEquals("Strong", SecurityPosture.overallLabel(SecurityPosture.overall(axes)))
        assertTrue(axes.all { it.rating == Rating.Good })
    }

    @Test
    fun keyStorageRanksStrongBoxOverTheTeeOverSoftware() {
        val strongBox = score(HardwareSecurityLevel.STRONGBOX).of(Axis.KeyStorage)
        val tee = score(HardwareSecurityLevel.TEE).of(Axis.KeyStorage)
        val software = score(HardwareSecurityLevel.SOFTWARE).of(Axis.KeyStorage)
        assertTrue(strongBox.score > tee.score && tee.score > software.score)
        assertEquals("TEE", tee.value)
        assertEquals(Rating.Weak, software.rating)
    }

    @Test
    fun checksStillRunningArePendingAndLeftOutOfTheOverall() {
        val axes = score(attestation = null)
        listOf(Axis.Attestation, Axis.VerifiedBoot, Axis.Bootloader).forEach {
            assertEquals(Rating.Pending, axes.of(it).rating)
            assertEquals(0f, axes.of(it).score, 0f)
            assertEquals("Checking...", axes.of(it).value)
        }
        // Key storage, the patch from the build and the screen lock are known straight away.
        assertEquals(1f, SecurityPosture.overall(axes)!!, 0.001f)
        assertNull(SecurityPosture.overall(axes.map { it.copy(rating = Rating.Pending) }))
        assertEquals("Checking...", SecurityPosture.overallLabel(null))
    }

    @Test
    fun anUnlockedBootloaderAndUnverifiedBootAreWeak() {
        val axes = score(attestation = attested(bootState = "UNVERIFIED", locked = false))
        assertEquals("Unlocked", axes.of(Axis.Bootloader).value)
        assertEquals(Rating.Weak, axes.of(Axis.Bootloader).rating)
        assertEquals("Unverified", axes.of(Axis.VerifiedBoot).value)
        assertEquals(Rating.Weak, axes.of(Axis.VerifiedBoot).rating)
    }

    @Test
    fun aSelfSignedOsWithARelockedBootloaderIsFair() {
        val boot = score(attestation = attested(bootState = "SELF_SIGNED")).of(Axis.VerifiedBoot)
        assertEquals("Self-Signed", boot.value)
        assertEquals(Rating.Fair, boot.rating)
        assertTrue(boot.score in 0.4f..0.8f)
        assertEquals(0f, score(attestation = attested(bootState = "FAILED")).of(Axis.VerifiedBoot).score, 0f)
    }

    @Test
    fun attestationIsRatedByHowFarItsChainVerifies() {
        assertEquals("Verified", score().of(Axis.Attestation).value)
        assertEquals("Software Root", score(attestation = attested(hardwareAttested = false)).of(Axis.Attestation).value)
        assertEquals("Unverified", score(attestation = attested(hardwareAttested = false, rootVerified = false)).of(Axis.Attestation).value)
        assertEquals(
            "Unavailable",
            score(attestation = attested(hardwareAttested = false, rootVerified = false, certificates = 0)).of(Axis.Attestation).value
        )
    }

    @Test
    fun anUnverifiedChainsBootClaimsAreUnconfirmed() {
        // A chain that doesn't reach a hardware root can't vouch for the boot state or lock it reports.
        val axes = score(attestation = attested(hardwareAttested = false, bootState = "VERIFIED", locked = true))
        assertEquals("Unconfirmed", axes.of(Axis.VerifiedBoot).value)
        assertEquals("Unconfirmed", axes.of(Axis.Bootloader).value)
        assertEquals(Rating.Fair, axes.of(Axis.Bootloader).rating)
    }

    @Test
    fun aFailedAttestationLeavesBootAndLockUnknown() {
        val failed = AttestationResult(false, HardwareSecurityLevel.SOFTWARE, "", 0, "Unverified", verifiedBootState = "UNVERIFIED", deviceLocked = false)
        val axes = score(attestation = failed)
        assertEquals("Unknown", axes.of(Axis.VerifiedBoot).value)
        assertEquals("Unknown", axes.of(Axis.Bootloader).value)
    }

    @Test
    fun patchFreshnessFallsAMonthAtATime() {
        assertEquals(1f, SecurityPosture.patchScore(null, "2026-10-05", today).score, 0f)
        assertEquals("Current", SecurityPosture.patchScore(null, "2026-10-05", today).value)
        assertEquals(1f, SecurityPosture.patchScore(null, "2026-09-05", today).score, 0f)
        assertEquals("1 Month Old", SecurityPosture.patchScore(null, "2026-09-05", today).value)
        assertEquals(0.8f, SecurityPosture.patchScore(null, "2026-07-01", today).score, 0.001f)
        assertEquals(Rating.Fair, SecurityPosture.patchScore(null, "2026-07-01", today).rating)
        assertEquals(0.5f, SecurityPosture.patchScore(null, "2026-04-01", today).score, 0.001f)
        val stale = SecurityPosture.patchScore(null, "2024-10-01", today)
        assertEquals(0.1f, stale.score, 0.001f)
        assertEquals("24 Months Old", stale.value)
        assertEquals(Rating.Weak, stale.rating)
    }

    @Test
    fun theAttestedPatchLevelWinsOverTheBuildString() {
        assertEquals("3 Months Old", score(attestation = attested(patch = 202607), patch = "2026-10-05").of(Axis.PatchLevel).value)
        assertEquals("3 Months Old", score(attestation = attested(patch = 20260705), patch = "2026-10-05").of(Axis.PatchLevel).value)
        // Unattested chains and missing levels fall back to the build's patch string.
        assertEquals("Current", score(attestation = attested(patch = 0), patch = "2026-10-05").of(Axis.PatchLevel).value)
        assertEquals("Current", score(attestation = attested(patch = 202001, hardwareAttested = false), patch = "2026-10-05").of(Axis.PatchLevel).value)
    }

    @Test
    fun unreadablePatchDatesAreUnknown() {
        assertEquals("Unknown", SecurityPosture.patchScore(null, null, today).value)
        assertEquals("Unknown", SecurityPosture.patchScore(null, "", today).value)
        assertEquals("Unknown", SecurityPosture.patchScore(null, "garbage", today).value)
        assertNull(SecurityPosture.parseAttestedPatch(13))
        assertNull(SecurityPosture.parseAttestedPatch(202613))
        assertEquals(LocalDate.of(2026, 7, 1), SecurityPosture.parseAttestedPatch(202607))
        // A future patch date (clock behind) counts as current, not negative.
        assertEquals(0, SecurityPosture.patchAgeMonths(LocalDate.of(2027, 1, 1), today))
    }

    @Test
    fun noScreenLockIsWeak() {
        val lock = score(secure = false).of(Axis.ScreenLock)
        assertEquals("None", lock.value)
        assertEquals(Rating.Weak, lock.rating)
        assertEquals("Set", score().of(Axis.ScreenLock).value)
    }

    @Test
    fun overallWordsFollowTheMean() {
        assertEquals("Strong", SecurityPosture.overallLabel(0.9f))
        assertEquals("Good", SecurityPosture.overallLabel(0.7f))
        assertEquals("Fair", SecurityPosture.overallLabel(0.5f))
        assertEquals("Weak", SecurityPosture.overallLabel(0.2f))
        val weak = score(HardwareSecurityLevel.SOFTWARE, attested(bootState = "UNVERIFIED", locked = false, patch = 202401), "2024-01-01", secure = false)
        assertEquals("Weak", SecurityPosture.overallLabel(SecurityPosture.overall(weak)))
    }

    @Test
    fun screenReadersHearEveryCheck() {
        val text = SecurityPosture.describe(score(HardwareSecurityLevel.TEE))
        assertTrue(text, text.startsWith("Security posture: Strong, 96 percent"))
        Axis.entries.forEach { assertTrue(it.label, it.label in text) }
        assertTrue(text, "Key Storage: TEE" in text)
        assertEquals("Security posture: checking. Key Storage: Checking....", SecurityPosture.describe(listOf(
            SecurityPosture.AxisScore(Axis.KeyStorage, 0f, "Checking...", Rating.Pending)
        )))
    }
}
