package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.pixel.intelligentsearch.core.security.AttestationResult
import com.pixel.intelligentsearch.core.security.HardwareSecurityDetector
import com.pixel.intelligentsearch.core.security.HardwareSecurityLevel
import com.pixel.intelligentsearch.core.security.SecurityPosture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The Hardware & Security page is there at once and its text never moves: no entrance, no button to run the check
 * again, and the attestation line reads its answer as soon as there is one. The banner's secure chip is what moves.
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

    private var result by mutableStateOf<AttestationResult?>(attestation)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { HardwareSecurityPage(hardware, result, posture) } }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun everyLineIsThereFromTheFirstFrame() {
        show()
        // Not scrambled, not still on its way in: the real text, on the very first frame.
        compose.onNodeWithText("Device: ${hardware.deviceDisplayName}").assertExists()
        compose.onNodeWithText("Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): Hardware Verified ✓").assertExists()
        compose.onNodeWithText("Certificate Chain Depth: 4 certificates", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(hardware.description).assertExists()
        compose.onNodeWithTag(SECURE_CHIP_TAG).assertExists()
    }

    @Test
    fun thereIsNoButtonToCheckAgain() {
        show()
        compose.onNodeWithText("Verify Again").assertDoesNotExist()
    }

    @Test
    fun whileVerifyingTheLineSaysSoThenReadsTheAnswer() {
        result = null
        show()
        compose.onNodeWithText("Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): Verifying...").assertExists()
        result = attestation
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): Hardware Verified ✓").assertExists()
    }
}
