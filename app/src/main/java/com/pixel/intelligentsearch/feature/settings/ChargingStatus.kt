package com.pixel.intelligentsearch.feature.settings

import android.os.BatteryManager

/**
 * What the phone is plugged into and what that connection is doing, as the battery card shows it. Pure, so every
 * combination of plug type, charging state and USB mode can be unit tested.
 *
 * It reads only what an ordinary app may: the battery broadcast (EXTRA_PLUGGED, EXTRA_STATUS and the charging state
 * extra), the sticky USB state broadcast (whether a computer is on the other end and which USB functions are on) and
 * the USB debugging setting. UsbManager's port status is a system API, so it is not used.
 */
internal object ChargingStatus {

    /** The sticky `android.hardware.usb.action.USB_STATE` broadcast; UsbManager keeps the name hidden. */
    const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"

    /**
     * The battery broadcast's charging state (android.hardware.health BatteryChargingState), present from Android 14.
     * The extra and its values are hidden in BatteryManager, so they are named here.
     */
    const val EXTRA_CHARGING_STATUS = "android.os.extra.CHARGING_STATUS"
    const val CHARGING_STATE_TOO_COLD = 2
    const val CHARGING_STATE_TOO_HOT = 3
    const val CHARGING_STATE_LONG_LIFE = 4
    const val CHARGING_STATE_ADAPTIVE = 5

    /**
     * The USB state broadcast's extras. [connected] means a USB host (a computer) is on the other end of the cable;
     * a wall charger never makes it true. The function flags say which USB modes the phone currently offers.
     */
    data class UsbLink(
        val connected: Boolean = false,
        val configured: Boolean = false,
        val hostConnected: Boolean = false,
        val adb: Boolean = false,
        val mtp: Boolean = false,
        val ptp: Boolean = false,
        val rndis: Boolean = false,
        val ncm: Boolean = false,
        val midi: Boolean = false,
        val uvc: Boolean = false,
        val accessory: Boolean = false,
        val audioSource: Boolean = false
    ) {
        companion object {
            /** Reads the broadcast's boolean extras through [flag], e.g. `intent::getBooleanExtra` with a false default. */
            fun from(flag: (String) -> Boolean) = UsbLink(
                connected = flag("connected"),
                configured = flag("configured"),
                hostConnected = flag("host_connected"),
                adb = flag("adb"),
                mtp = flag("mtp"),
                ptp = flag("ptp"),
                rndis = flag("rndis"),
                ncm = flag("ncm"),
                midi = flag("midi"),
                uvc = flag("uvc"),
                accessory = flag("accessory"),
                audioSource = flag("audio_source")
            )
        }
    }

    enum class Source { WallCharger, Computer, UsbCharger, Wireless, Dock, Unknown, UsbDevice }

    /**
     * [label] is the full description for the card's status line ("USB Debugging · Charging"); [shortLabel] fits the
     * header chip ("USB Debugging").
     */
    data class Status(val label: String, val shortLabel: String, val source: Source)

    /**
     * The status for the battery card, or null while the phone runs on battery with nothing attached.
     *
     * The other end of the cable decides the source: when the USB state reports a connected host the phone is on a
     * computer, even if its USB-C port supplies power-delivery current the battery broadcast calls AC; otherwise AC is a
     * wall charger and USB a plain USB charger or power bank. Active USB modes (tethering, file transfer, debugging...)
     * lead the label, followed by what the charger is doing.
     *
     * @param usb the USB state broadcast, or null when it could not be read.
     * @param adbEnabled the USB debugging setting; only used to name debugging when [usb] is unavailable.
     * @param chargingState the broadcast's [EXTRA_CHARGING_STATUS], or 0 when absent.
     * @param fastCharging whether the charger is delivering fast-charging power.
     */
    fun status(
        plugged: Int,
        chargeState: DiagnosticsReadings.ChargeState,
        usb: UsbLink?,
        adbEnabled: Boolean,
        chargingState: Int = 0,
        fastCharging: Boolean = false
    ): Status? {
        if (plugged == 0 || chargeState == DiagnosticsReadings.ChargeState.Discharging) {
            // Running on battery; a USB device the phone powers (OTG) is still worth naming.
            return if (usb?.hostConnected == true) {
                Status("USB Device Connected (OTG)", "USB Device", Source.UsbDevice)
            } else {
                null
            }
        }

        val computer = usb?.connected == true
        val source = when {
            (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> Source.Wireless
            (plugged and BatteryManager.BATTERY_PLUGGED_DOCK) != 0 -> Source.Dock
            computer -> Source.Computer
            (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> Source.WallCharger
            // Without the USB state, a USB plug with debugging on is a computer; otherwise a plain USB charger.
            (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 ->
                if (usb == null && adbEnabled) Source.Computer else Source.UsbCharger
            else -> Source.Unknown
        }

        val modes = if (source == Source.Computer) usbModes(usb, adbEnabled) else emptyList()
        val charge = chargePhrase(chargeState, chargingState, fastCharging)
        if (modes.isNotEmpty()) {
            return Status((modes + charge.short).joinToString(" · "), modes.first(), source)
        }

        val label = when (source) {
            Source.WallCharger -> when (charge.kind) {
                ChargeKind.Adding -> "${charge.short} (Wall Charger)"
                ChargeKind.Full -> "Fully Charged"
                ChargeKind.Held -> "Plugged In, ${charge.short}"
                ChargeKind.Unknown -> "Plugged In"
            }
            Source.Computer -> when (charge.kind) {
                ChargeKind.Adding -> if (charge.short == "Charging") "Charging over USB (Computer)" else "${charge.short} (Computer)"
                ChargeKind.Full -> "Fully Charged (Computer)"
                ChargeKind.Held -> "Plugged Into Computer, ${charge.short}"
                ChargeKind.Unknown -> "Plugged Into Computer"
            }
            Source.UsbCharger -> when (charge.kind) {
                ChargeKind.Adding -> if (charge.short == "Charging") "Charging over USB" else "${charge.short} (USB)"
                ChargeKind.Full -> "Fully Charged"
                ChargeKind.Held -> "Plugged In, ${charge.short}"
                ChargeKind.Unknown -> "Plugged In over USB"
            }
            Source.Wireless -> when (charge.kind) {
                ChargeKind.Adding -> if (charge.short == "Charging") "Wireless Charging" else "${charge.short} (Wireless)"
                ChargeKind.Full -> "Fully Charged (Wireless)"
                ChargeKind.Held -> "On Wireless Charger, ${charge.short}"
                ChargeKind.Unknown -> "On Wireless Charger"
            }
            Source.Dock -> when (charge.kind) {
                ChargeKind.Unknown -> "Docked"
                ChargeKind.Held -> "Docked, ${charge.short}"
                else -> "Docked · ${charge.short}"
            }
            Source.Unknown, Source.UsbDevice -> when (charge.kind) {
                ChargeKind.Held -> "Plugged In, ${charge.short}"
                ChargeKind.Unknown -> "Plugged In"
                else -> charge.short
            }
        }
        val shortLabel = when (source) {
            Source.Wireless -> if (charge.kind == ChargeKind.Adding) "Wireless Charging" else charge.short
            Source.Dock -> "Docked"
            else -> if (charge.kind == ChargeKind.Unknown) "Plugged In" else charge.short
        }
        return Status(label, shortLabel, source)
    }

    /** The USB modes the computer can use, most specific first; debugging comes last as it runs alongside the others. */
    private fun usbModes(usb: UsbLink?, adbEnabled: Boolean): List<String> = buildList {
        if (usb != null) {
            if (usb.rndis || usb.ncm) add("USB Tethering")
            if (usb.mtp) add("File Transfer")
            if (usb.ptp) add("Photo Transfer")
            if (usb.midi) add("MIDI")
            if (usb.uvc) add("Webcam")
            if (usb.accessory) add("USB Accessory")
            if (usb.audioSource) add("USB Audio")
        }
        if (usb?.adb ?: adbEnabled) add("USB Debugging")
    }

    private enum class ChargeKind { Adding, Full, Held, Unknown }

    private class ChargePhrase(val short: String, val kind: ChargeKind)

    private fun chargePhrase(state: DiagnosticsReadings.ChargeState, chargingState: Int, fast: Boolean): ChargePhrase = when (state) {
        DiagnosticsReadings.ChargeState.Charging -> ChargePhrase(
            when (chargingState) {
                CHARGING_STATE_TOO_HOT -> "Charging Slowly, Too Warm"
                CHARGING_STATE_TOO_COLD -> "Charging Slowly, Too Cold"
                CHARGING_STATE_ADAPTIVE -> "Adaptive Charging"
                CHARGING_STATE_LONG_LIFE -> "Charging Optimized"
                else -> if (fast) "Fast Charging" else "Charging"
            },
            ChargeKind.Adding
        )
        DiagnosticsReadings.ChargeState.Full -> ChargePhrase("Fully Charged", ChargeKind.Full)
        DiagnosticsReadings.ChargeState.PluggedNotCharging -> ChargePhrase(
            when (chargingState) {
                CHARGING_STATE_LONG_LIFE -> "Paused to Protect Battery"
                CHARGING_STATE_ADAPTIVE -> "Paused by Adaptive Charging"
                CHARGING_STATE_TOO_HOT -> "Paused, Too Warm"
                CHARGING_STATE_TOO_COLD -> "Paused, Too Cold"
                else -> "Not Charging"
            },
            ChargeKind.Held
        )
        DiagnosticsReadings.ChargeState.PluggedUnknown, DiagnosticsReadings.ChargeState.Discharging ->
            ChargePhrase("Plugged In", ChargeKind.Unknown)
    }

    /**
     * The header chip of the battery card: a health problem always shows, so an overheating phone on a charger is not
     * hidden behind "Charging"; otherwise a plugged-in phone shows its charging status, and a phone on battery its
     * health. Returns the label and whether it is the health rating.
     */
    fun headerChip(health: String, severity: DiagnosticsReadings.HealthSeverity, status: Status?): Pair<String, Boolean> = when {
        severity != DiagnosticsReadings.HealthSeverity.Ok -> health to true
        status != null -> status.shortLabel to false
        else -> health to true
    }
}
