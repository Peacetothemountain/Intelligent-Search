package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.system.battery.ChargingStatus
import com.pixel.intelligentsearch.core.system.battery.BatteryReadings
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The battery and memory page reads the phone only while it is on screen, and its pressure chip is legible. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class DebugDiagnosticsPageTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    @Test
    fun stopsReadingWhileThePageIsInTheBackground() {
        val owner = TestOwner()
        var samples = 0
        val sampler = DiagnosticsSampler { samples++; SAMPLE }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                IntelligentSearchTheme(darkTheme = false) {
                    BatteryAndMemoryDiagnosticsPage(ApplicationProvider.getApplicationContext(), newSampler = { sampler })
                }
            }
        }
        compose.settle(steps = 5)
        assertTrue("reads while visible", samples > 0)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        val whenHidden = samples
        compose.settle(steps = 100)
        assertEquals(whenHidden, samples)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.settle(steps = 5)
        assertTrue("reads again once visible", samples > whenHidden)
    }

    @Test
    fun aPluggedInPhoneShowsItsChargingStatusNotAnUnratedHealth() {
        // Pixels stop rating battery health while plugged in, which left the card's status chip on "Not reported".
        val status = ChargingStatus.status(
            plugged = android.os.BatteryManager.BATTERY_PLUGGED_USB,
            chargeState = BatteryReadings.ChargeState.Charging,
            usb = ChargingStatus.UsbLink(connected = true, configured = true, adb = true),
            adbEnabled = true
        )
        val plugged = SAMPLE.copy(
            battery = SAMPLE.battery.copy(
                health = BatteryReadings.HEALTH_NOT_REPORTED, isCharging = true,
                chargingRateStr = status!!.label, power = status
            )
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                BatteryAndMemoryDiagnosticsPage(ApplicationProvider.getApplicationContext(), newSampler = { DiagnosticsSampler { plugged } })
            }
        }
        compose.settle(steps = 5)
        compose.onNodeWithTag(BATTERY_STATUS_CHIP_TAG).assertTextEquals("USB Debugging")
        compose.onNodeWithText("USB Debugging · Charging").assertExists()
    }

    @Suppress("DEPRECATION") // Sticky broadcasts are how the system publishes these states; tests stand in for it.
    @Test
    fun theSamplerNamesTheConnectionFromTheSystemBroadcasts() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.sendStickyBroadcast(
            android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED)
                .putExtra(android.os.BatteryManager.EXTRA_PLUGGED, android.os.BatteryManager.BATTERY_PLUGGED_USB)
                .putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_CHARGING)
                .putExtra(android.os.BatteryManager.EXTRA_HEALTH, android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN)
                .putExtra(android.os.BatteryManager.EXTRA_LEVEL, 50)
                .putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        )
        context.sendStickyBroadcast(
            android.content.Intent(ChargingStatus.ACTION_USB_STATE)
                .putExtra("connected", true).putExtra("configured", true).putExtra("adb", true)
        )
        val battery = DeviceDiagnosticsSampler(context).sample().battery
        assertEquals("USB Debugging · Charging", battery.chargingRateStr)
        assertEquals("USB Debugging", battery.power?.shortLabel)

        // Unplugged: no connection status, and the chip goes back to the health rating.
        context.sendStickyBroadcast(
            android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED)
                .putExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
                .putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
                .putExtra(android.os.BatteryManager.EXTRA_HEALTH, android.os.BatteryManager.BATTERY_HEALTH_GOOD)
        )
        val unplugged = DeviceDiagnosticsSampler(context).sample().battery
        assertEquals("Discharging", unplugged.chargingRateStr)
        assertEquals(null, unplugged.power)
    }

    @Test
    fun readsAgainAsSoonAsTheUsbStateChanges() {
        var samples = 0
        val sampler = DiagnosticsSampler { samples++; SAMPLE }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                BatteryAndMemoryDiagnosticsPage(context, newSampler = { sampler })
            }
        }
        compose.settle(steps = 3)
        val before = samples
        assertTrue("reads on opening", before > 0)

        // Well inside the 2.5 s poll: only the broadcast can cause another reading.
        context.sendBroadcast(android.content.Intent(ChargingStatus.ACTION_USB_STATE).putExtra("connected", true))
        compose.settle(steps = 2)
        assertTrue("reads when the USB state changes", samples > before)
    }

    @Test
    fun listensForPlugChangesOnlyWhileVisible() {
        val owner = TestOwner()
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        fun usbListeners() = org.robolectric.Shadows.shadowOf(app).registeredReceivers
            .count { it.intentFilter.hasAction(ChargingStatus.ACTION_USB_STATE) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                IntelligentSearchTheme(darkTheme = false) {
                    BatteryAndMemoryDiagnosticsPage(app, newSampler = { DiagnosticsSampler { SAMPLE } })
                }
            }
        }
        compose.settle(steps = 3)
        assertEquals(1, usbListeners())

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.settle(steps = 3)
        assertEquals(0, usbListeners())
    }

    @Test
    fun everyPressureChipIsLegibleInBothThemes() {
        for (scheme in listOf(lightColorScheme(), darkColorScheme())) {
            for (pressure in listOf("Critical", "High Load", "Moderate", "Normal")) {
                val (container, content) = pressureChipColors(pressure, scheme)
                val chip = container.compositeOver(scheme.surfaceContainerLow)
                assertTrue("$pressure on ${scheme.surface}", contrast(content, chip) >= 4.5)
            }
        }
    }

    private fun contrast(foreground: Color, background: Color): Double =
        MaterialYouPaletteHelper.calculateContrastRatio(foreground.compositeOver(background).toArgb(), background.toArgb())

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        val SAMPLE = DiagnosticsSample(
            battery = BatteryTelemetrySnapshot(
                level = 80, temp = 30f, volt = 4f, watts = null, health = "Good",
                healthSeverity = BatteryReadings.HealthSeverity.Ok, cycles = -1, technology = "Li-ion",
                thermalStatus = "Normal", isCharging = false, chargingRateStr = "Discharging", timeEstimateStr = "Estimating..."
            ),
            memory = MemorySnapshot(
                totalGb = 8.0, usedGb = 4.0, availGb = 4.0, pct = 50, zRamUsedGb = 0.0, zRamTotalGb = 0.0, cachedGb = 1.0,
                isLowMem = false, thresholdMb = 0, memPressure = "Normal", processes = emptyList(),
                breakdownAvailable = false, physicalGb = 8
            )
        )
    }
}
