package com.pixel.intelligentsearch.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pixel.intelligentsearch.core.security.AttestationResult
import com.pixel.intelligentsearch.core.security.HardwareSecurityDetector
import com.pixel.intelligentsearch.core.security.HardwareSecurityLevel
import com.pixel.intelligentsearch.core.security.SecurityPosture
import androidx.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The Hardware & Security page is there at once, with no entrance to sit through, and its motion does a job: the
 * check indicator runs exactly while attestation runs, and Verify Again runs a fresh check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1600dp-xhdpi")
class HardwareSecurityPageTest {

    @get:Rule val compose = createComposeRule()

    private val hardware = HardwareSecurityDetector.resolveHardwareInfo(
        manufacturer = "Google", brand = "google", model = "Pixel 11 Pro XL", device = "kodiak", hardware = "malibu",
        board = "malibu", product = "kodiak", socModel = "Tensor G6", hasStrongBoxFeature = true
    )
    private val attestation = AttestationResult(
        isHardwareAttested = true, securityLevel = HardwareSecurityLevel.STRONGBOX, attestationChallenge = "",
        certificateCount = 4, issuerName = "", verifiedBootState = "VERIFIED", deviceLocked = true,
        osPatchLevel = 202608, isRootVerified = true
    )
    private val posture = SecurityPosture.score(
        HardwareSecurityLevel.STRONGBOX, attestation, "2026-08-05", LocalDate.of(2026, 10, 22), deviceSecure = true
    )

    private var verifying by mutableStateOf(false)
    private var checks by mutableIntStateOf(0)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                HardwareSecurityPage(
                    hardwareInfo = hardware,
                    attestationResult = attestation,
                    posture = posture,
                    verifying = verifying,
                    checkedAt = 0L,
                    onVerifyAgain = { checks++ }
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun everyLineIsThereFromTheFirstFrame() {
        show()
        // Not scrambled, not still on its way in: the real text, on the very first frame.
        compose.onNodeWithText("Device: ${hardware.deviceDisplayName}").assertExists()
        compose.onNodeWithText("Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): Hardware Verified", substring = true).assertExists()
        compose.onNodeWithText("Certificate Chain Depth: 4 certificates", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(hardware.description).assertExists()
    }

    @Test
    fun theCheckIndicatorRunsOnlyWhileAttestationRuns() {
        show()
        compose.onNodeWithTag(ATTESTATION_CHECKING_TAG).assertDoesNotExist()
        verifying = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag(ATTESTATION_CHECKING_TAG).assertExists()
        compose.onNodeWithText("Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): Verifying...", substring = true).assertExists()
        verifying = false
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(ATTESTATION_CHECKING_TAG).assertDoesNotExist()
    }

    @Test
    fun verifyAgainRunsAFreshCheckAndWaitsForIt() {
        show()
        compose.onNodeWithTag(VERIFY_AGAIN_TAG).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, checks)
        verifying = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        // One check at a time.
        compose.onNodeWithTag(VERIFY_AGAIN_TAG).assertIsNotEnabled()
    }
}
