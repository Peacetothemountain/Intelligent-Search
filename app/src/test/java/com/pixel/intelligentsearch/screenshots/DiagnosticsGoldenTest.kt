package com.pixel.intelligentsearch.screenshots

import com.pixel.intelligentsearch.core.system.battery.BatteryReadings
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.security.AttestationResult
import com.pixel.intelligentsearch.core.security.HardwareSecurityDetector
import com.pixel.intelligentsearch.core.security.HardwareSecurityLevel
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.BatteryAndMemoryDiagnosticsPage
import com.pixel.intelligentsearch.feature.settings.BatteryTelemetrySnapshot
import com.pixel.intelligentsearch.core.system.battery.ChargingStatus
import com.pixel.intelligentsearch.feature.settings.DiagnosticsSample
import com.pixel.intelligentsearch.feature.settings.DiagnosticsSampler
import com.pixel.intelligentsearch.feature.settings.HardwareSecurityPage
import com.pixel.intelligentsearch.feature.settings.MemorySnapshot
import com.pixel.intelligentsearch.feature.settings.ProcessRamEntry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of the Developer Note's Battery & System and Hardware & Security pages with fixed readings, at the
 * default text size, at 2x text and in a narrow (zoomed) window, so the roomier cards, the wrapping chip grid and the
 * security posture chart's two label layouts are all pinned.
 *
 * The page is drawn at the dialog's content width on its container colour. Record with
 * `./gradlew testDebugUnitTest --tests "*DiagnosticsGoldenTest*" -Proborazzi.record=true`.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class DiagnosticsGoldenTest(
    private val page: String,
    private val dark: Boolean,
    private val fontScale: Float,
    private val widthDp: Int,
    private val heightDp: Int
) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    @Test
    fun page() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(
                        Modifier
                            .testTag(CAPTURE_TAG)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp)
                            .width(widthDp.dp)
                            .height(heightDp.dp)
                    ) {
                        when (page) {
                            "battery" -> BatteryAndMemoryDiagnosticsPage(
                                ApplicationProvider.getApplicationContext(),
                                newSampler = { DiagnosticsSampler { BATTERY_SAMPLE } },
                                powerEvents = { emptyFlow() }
                            )
                            "security" -> HardwareSecurityPage(HARDWARE, ATTESTATION, POSTURE, onVerifyAgain = {})
                            else -> error("unknown page $page")
                        }
                    }
                }
            }
        }
        compose.settle()
        val scale = if (fontScale == 1f) "" else "_font${(fontScale * 10).toInt()}"
        val width = if (widthDp == DIALOG_WIDTH) "" else "_w$widthDp"
        compose.onNodeWithTag(CAPTURE_TAG).captureRoboImage(
            filePath = "src/test/screenshots/diagnostics/${page}${scale}${width}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        /** About the width an AlertDialog gives its content on a 412 dp phone. */
        private const val DIALOG_WIDTH = 312
        private const val CAPTURE_TAG = "diagnostics_golden"

        private val USB_DEBUGGING = ChargingStatus.status(
            plugged = BatteryManager.BATTERY_PLUGGED_USB,
            chargeState = BatteryReadings.ChargeState.Charging,
            usb = ChargingStatus.UsbLink(connected = true, configured = true, adb = true),
            adbEnabled = true
        )

        private val BATTERY_SAMPLE = DiagnosticsSample(
            battery = BatteryTelemetrySnapshot(
                level = 72, temp = 31.4f, volt = 4.21f, watts = 7.6f, health = "Good",
                healthSeverity = BatteryReadings.HealthSeverity.Ok, cycles = 143, technology = "Li-ion",
                thermalStatus = "Normal", isCharging = true, chargingRateStr = USB_DEBUGGING!!.label,
                timeEstimateStr = "1 hr 12 min until full", power = USB_DEBUGGING
            ),
            memory = MemorySnapshot(
                totalGb = 15.6, usedGb = 9.1, availGb = 6.5, pct = 58, zRamUsedGb = 1.2, zRamTotalGb = 8.0, cachedGb = 3.4,
                isLowMem = false, thresholdMb = 288, memPressure = "Normal",
                processes = listOf(ProcessRamEntry("Apps", 5400f), ProcessRamEntry("Kernel", 900f), ProcessRamEntry("Other", 2800f)),
                breakdownAvailable = true, physicalGb = 16
            )
        )

        private val HARDWARE = HardwareSecurityDetector.resolveHardwareInfo(
            manufacturer = "Google", brand = "google", model = "Pixel 11 Pro XL", device = "kodiak", hardware = "malibu",
            board = "malibu", product = "kodiak", socModel = "Tensor G6", hasStrongBoxFeature = true
        )

        private val ATTESTATION = AttestationResult(
            isHardwareAttested = true, securityLevel = HardwareSecurityLevel.STRONGBOX, attestationChallenge = "",
            certificateCount = 4, issuerName = "", verifiedBootState = "VERIFIED", deviceLocked = true,
            osPatchLevel = 202608, isRootVerified = true
        )

        private val POSTURE = SecurityPosture.score(
            HardwareSecurityLevel.STRONGBOX, ATTESTATION, "2026-08-05", LocalDate.of(2026, 10, 22), deviceSecure = true
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1} font={2} w={3}")
        fun parameters(): List<Array<Any>> = listOf(
            arrayOf("battery", false, 1f, DIALOG_WIDTH, 572),
            arrayOf("battery", true, 1f, DIALOG_WIDTH, 572),
            arrayOf("battery", false, 2f, DIALOG_WIDTH, 1360),
            arrayOf("battery", false, 1f, 240, 820),
            arrayOf("security", false, 1f, DIALOG_WIDTH, 740),
            arrayOf("security", true, 1f, DIALOG_WIDTH, 740),
            arrayOf("security", false, 2f, DIALOG_WIDTH, 1880),
            arrayOf("security", false, 1f, 240, 960)
        )
    }
}
