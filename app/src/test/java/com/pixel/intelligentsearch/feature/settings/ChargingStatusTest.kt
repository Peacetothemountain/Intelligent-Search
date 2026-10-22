package com.pixel.intelligentsearch.feature.settings

import android.os.BatteryManager.BATTERY_PLUGGED_AC
import android.os.BatteryManager.BATTERY_PLUGGED_DOCK
import android.os.BatteryManager.BATTERY_PLUGGED_USB
import android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS
import com.pixel.intelligentsearch.feature.settings.ChargingStatus.Source
import com.pixel.intelligentsearch.feature.settings.ChargingStatus.UsbLink
import com.pixel.intelligentsearch.feature.settings.DiagnosticsReadings.ChargeState
import com.pixel.intelligentsearch.feature.settings.DiagnosticsReadings.HealthSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The battery card names what the phone is plugged into and what the connection is doing. */
class ChargingStatusTest {

    private val computer = UsbLink(connected = true, configured = true)
    private val noHost = UsbLink()

    private fun status(
        plugged: Int,
        state: ChargeState = ChargeState.Charging,
        usb: UsbLink? = noHost,
        adb: Boolean = false,
        chargingState: Int = 0,
        fast: Boolean = false
    ) = ChargingStatus.status(plugged, state, usb, adb, chargingState, fast)

    private fun label(plugged: Int, state: ChargeState = ChargeState.Charging, usb: UsbLink? = noHost, adb: Boolean = false, chargingState: Int = 0, fast: Boolean = false) =
        status(plugged, state, usb, adb, chargingState, fast)!!.label

    @Test
    fun onBatteryThereIsNoStatus() {
        assertNull(status(0, ChargeState.Discharging))
        assertNull(status(0, ChargeState.Discharging, usb = null, adb = true))
    }

    @Test
    fun aUsbDeviceThePhonePowersIsNamed() {
        val otg = status(0, ChargeState.Discharging, usb = UsbLink(hostConnected = true))!!
        assertEquals("USB Device Connected (OTG)", otg.label)
        assertEquals(Source.UsbDevice, otg.source)
    }

    @Test
    fun aWallChargerIsNamedByWhatItIsDoing() {
        assertEquals("Charging (Wall Charger)", label(BATTERY_PLUGGED_AC))
        assertEquals("Fast Charging (Wall Charger)", label(BATTERY_PLUGGED_AC, fast = true))
        assertEquals("Fully Charged", label(BATTERY_PLUGGED_AC, ChargeState.Full))
        assertEquals("Plugged In, Not Charging", label(BATTERY_PLUGGED_AC, ChargeState.PluggedNotCharging))
        assertEquals("Plugged In", label(BATTERY_PLUGGED_AC, ChargeState.PluggedUnknown))
        assertEquals(Source.WallCharger, status(BATTERY_PLUGGED_AC)!!.source)
        assertEquals("Charging", status(BATTERY_PLUGGED_AC)!!.shortLabel)
    }

    @Test
    fun aComputerInChargingOnlyModeIsNamed() {
        assertEquals("Charging over USB (Computer)", label(BATTERY_PLUGGED_USB, usb = computer))
        assertEquals("Fully Charged (Computer)", label(BATTERY_PLUGGED_USB, ChargeState.Full, usb = computer))
        assertEquals("Plugged Into Computer, Not Charging", label(BATTERY_PLUGGED_USB, ChargeState.PluggedNotCharging, usb = computer))
        assertEquals(Source.Computer, status(BATTERY_PLUGGED_USB, usb = computer)!!.source)
    }

    @Test
    fun aComputerPortSupplyingPowerDeliveryIsStillAComputer() {
        // Laptop USB-C ports often negotiate power delivery, which the battery broadcast reports as AC.
        assertEquals("Charging over USB (Computer)", label(BATTERY_PLUGGED_AC, usb = computer))
        assertEquals("USB Debugging · Charging", label(BATTERY_PLUGGED_AC, usb = computer.copy(adb = true)))
    }

    @Test
    fun usbDebuggingIsNamed() {
        val debugging = status(BATTERY_PLUGGED_USB, usb = computer.copy(adb = true))!!
        assertEquals("USB Debugging · Charging", debugging.label)
        assertEquals("USB Debugging", debugging.shortLabel)
        assertEquals("USB Debugging · Fully Charged", label(BATTERY_PLUGGED_USB, ChargeState.Full, usb = computer.copy(adb = true)))
    }

    @Test
    fun theDebuggingSettingAloneDoesNotMeanADebuggingSession() {
        // Debugging switched on but the cable goes to a wall charger: no computer, so no debugging.
        assertEquals("Charging (Wall Charger)", label(BATTERY_PLUGGED_AC, usb = noHost, adb = true))
        // Connected to a computer with the adb function off (e.g. debugging revoked): charging only.
        assertEquals("Charging over USB (Computer)", label(BATTERY_PLUGGED_USB, usb = computer, adb = true))
    }

    @Test
    fun withoutTheUsbStateTheDebuggingSettingNamesTheComputer() {
        assertEquals("USB Debugging · Charging", label(BATTERY_PLUGGED_USB, usb = null, adb = true))
        assertEquals("Charging over USB", label(BATTERY_PLUGGED_USB, usb = null, adb = false))
        assertEquals("Charging (Wall Charger)", label(BATTERY_PLUGGED_AC, usb = null, adb = true))
    }

    @Test
    fun eachUsbModeIsNamed() {
        assertEquals("File Transfer · Charging", label(BATTERY_PLUGGED_USB, usb = computer.copy(mtp = true)))
        assertEquals("Photo Transfer · Charging", label(BATTERY_PLUGGED_USB, usb = computer.copy(ptp = true)))
        assertEquals("USB Tethering · Charging", label(BATTERY_PLUGGED_USB, usb = computer.copy(rndis = true)))
        assertEquals("USB Tethering · Charging", label(BATTERY_PLUGGED_USB, usb = computer.copy(ncm = true)))
        assertEquals("MIDI · Charging", label(BATTERY_PLUGGED_USB, usb = computer.copy(midi = true)))
        assertEquals("Webcam · Charging", label(BATTERY_PLUGGED_USB, usb = computer.copy(uvc = true)))
        assertEquals("USB Accessory · Charging", label(BATTERY_PLUGGED_USB, usb = computer.copy(accessory = true)))
        assertEquals("USB Audio · Charging", label(BATTERY_PLUGGED_USB, usb = computer.copy(audioSource = true)))
    }

    @Test
    fun aDataModeLeadsAndDebuggingFollows() {
        val both = status(BATTERY_PLUGGED_USB, usb = computer.copy(mtp = true, adb = true))!!
        assertEquals("File Transfer · USB Debugging · Charging", both.label)
        assertEquals("File Transfer", both.shortLabel)
        assertEquals("USB Tethering", status(BATTERY_PLUGGED_USB, usb = computer.copy(rndis = true, adb = true))!!.shortLabel)
    }

    @Test
    fun usbModesNeedAComputer() {
        // Function flags left over from a previous connection don't count without a host on the cable.
        assertEquals("Charging over USB", label(BATTERY_PLUGGED_USB, usb = UsbLink(mtp = true, adb = true)))
    }

    @Test
    fun aPlainUsbChargerIsNamed() {
        assertEquals("Charging over USB", label(BATTERY_PLUGGED_USB))
        assertEquals("Fully Charged", label(BATTERY_PLUGGED_USB, ChargeState.Full))
        assertEquals("Plugged In, Not Charging", label(BATTERY_PLUGGED_USB, ChargeState.PluggedNotCharging))
        assertEquals(Source.UsbCharger, status(BATTERY_PLUGGED_USB)!!.source)
    }

    @Test
    fun wirelessChargingIsNamed() {
        val wireless = status(BATTERY_PLUGGED_WIRELESS)!!
        assertEquals("Wireless Charging", wireless.label)
        assertEquals("Wireless Charging", wireless.shortLabel)
        assertEquals("Fully Charged (Wireless)", label(BATTERY_PLUGGED_WIRELESS, ChargeState.Full))
        assertEquals("On Wireless Charger, Not Charging", label(BATTERY_PLUGGED_WIRELESS, ChargeState.PluggedNotCharging))
        // Wireless wins even if a stale USB host flag is still set.
        assertEquals(Source.Wireless, status(BATTERY_PLUGGED_WIRELESS, usb = computer)!!.source)
    }

    @Test
    fun aDockIsNamed() {
        val dock = status(BATTERY_PLUGGED_DOCK)!!
        assertEquals("Docked · Charging", dock.label)
        assertEquals("Docked", dock.shortLabel)
        assertEquals("Docked · Fully Charged", label(BATTERY_PLUGGED_DOCK, ChargeState.Full))
        assertEquals("Docked, Not Charging", label(BATTERY_PLUGGED_DOCK, ChargeState.PluggedNotCharging))
        assertEquals("Docked", label(BATTERY_PLUGGED_DOCK, ChargeState.PluggedUnknown))
    }

    @Test
    fun anUnknownPlugTypeStillSaysWhatTheChargerIsDoing() {
        assertEquals("Charging", label(0x40))
        assertEquals("Fully Charged", label(0x40, ChargeState.Full))
        assertEquals("Plugged In, Not Charging", label(0x40, ChargeState.PluggedNotCharging))
    }

    @Test
    fun theChargingStateExplainsAPauseOrSlowdown() {
        assertEquals("Plugged In, Paused to Protect Battery",
            label(BATTERY_PLUGGED_AC, ChargeState.PluggedNotCharging, chargingState = ChargingStatus.CHARGING_STATE_LONG_LIFE))
        assertEquals("Plugged In, Paused by Adaptive Charging",
            label(BATTERY_PLUGGED_AC, ChargeState.PluggedNotCharging, chargingState = ChargingStatus.CHARGING_STATE_ADAPTIVE))
        assertEquals("Plugged In, Paused, Too Warm",
            label(BATTERY_PLUGGED_AC, ChargeState.PluggedNotCharging, chargingState = ChargingStatus.CHARGING_STATE_TOO_HOT))
        assertEquals("Charging Slowly, Too Cold (Wall Charger)",
            label(BATTERY_PLUGGED_AC, chargingState = ChargingStatus.CHARGING_STATE_TOO_COLD))
        assertEquals("Adaptive Charging (Wall Charger)",
            label(BATTERY_PLUGGED_AC, chargingState = ChargingStatus.CHARGING_STATE_ADAPTIVE, fast = true))
        assertEquals("Charging Optimized (Computer)",
            label(BATTERY_PLUGGED_USB, usb = computer, chargingState = ChargingStatus.CHARGING_STATE_LONG_LIFE))
        assertEquals("Paused to Protect Battery",
            status(BATTERY_PLUGGED_AC, ChargeState.PluggedNotCharging, chargingState = ChargingStatus.CHARGING_STATE_LONG_LIFE)!!.shortLabel)
    }

    @Test
    fun theUsbStateIsReadFromTheBroadcastExtras() {
        val extras = mapOf("connected" to true, "configured" to true, "adb" to true, "mtp" to true, "host_connected" to false, "audio_source" to true)
        val link = UsbLink.from { extras[it] ?: false }
        assertEquals(UsbLink(connected = true, configured = true, adb = true, mtp = true, audioSource = true), link)
    }

    @Test
    fun theHeaderChipShowsChargingWhenPluggedInEvenWithoutAHealthRating() {
        val charging = status(BATTERY_PLUGGED_AC)
        assertEquals("Charging" to false,
            ChargingStatus.headerChip(DiagnosticsReadings.HEALTH_NOT_REPORTED, HealthSeverity.Ok, charging))
        assertEquals("Good" to true, ChargingStatus.headerChip("Good", HealthSeverity.Ok, null))
    }

    @Test
    fun aHealthProblemOutranksTheChargingStatus() {
        val charging = status(BATTERY_PLUGGED_AC)
        assertEquals("Overheating" to true, ChargingStatus.headerChip("Overheating", HealthSeverity.Bad, charging))
        assertEquals("Fair (worn)" to true, ChargingStatus.headerChip("Fair (worn)", HealthSeverity.Caution, charging))
    }
}
