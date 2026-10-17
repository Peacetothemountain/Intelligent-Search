package com.ngdesigns.intelligentsearch.engine.toggles

import com.ngdesigns.intelligentsearch.engine.normalize

/** A system setting search can show as a switch. */
enum class Toggle(val label: String, vararg val words: String) {
    Wifi("Wi-Fi", "wifi", "wi fi", "wlan", "wireless", "internet"),
    Bluetooth("Bluetooth", "bluetooth", "bt"),
    Flashlight("Flashlight", "flashlight", "torch", "flash light"),
    BatterySaver("Battery Saver", "battery saver", "power saving", "power saver", "low power", "battery"),
    DoNotDisturb("Do Not Disturb", "do not disturb", "dnd", "silence", "quiet mode"),
    AirplaneMode("Airplane Mode", "airplane mode", "aeroplane mode", "flight mode", "airplane"),
    AutoRotate("Auto-rotate", "auto rotate", "autorotate", "rotation", "rotate"),
    Hotspot("Hotspot", "hotspot", "tethering", "hot spot"),
    DarkTheme("Dark Theme", "dark theme", "dark mode", "night mode"),
    Nfc("NFC", "nfc", "tap to pay"),
    Location("Location", "location", "gps"),
}

/** A volume stream search can show as a slider. */
enum class VolumeStream(val label: String, vararg val words: String) {
    Media("Media volume", "media volume", "music volume", "volume"),
    Ring("Ring volume", "ring volume", "ringer volume", "ringtone volume", "ringer"),
    Alarm("Alarm volume", "alarm volume"),
}

/** A toggle a query names, and the state it asks for ("turn wifi off": false; "wifi": null, just show it). */
data class ToggleRequest(val toggle: Toggle, val wanted: Boolean?)

object Toggles {
    private val on = Regex("^(?:turn on|switch on|enable|activate|start)\\s+|\\s+(?:on)$")
    private val off = Regex("^(?:turn off|switch off|disable|deactivate|stop)\\s+|\\s+(?:off)$")

    /** Toggles a query names, best first; a word can be partly typed ("flas"), from three letters. */
    fun find(query: String): List<ToggleRequest> {
        var q = normalize(query)
        val wanted = when {
            on.containsMatchIn(q) -> true.also { q = on.replace(q, "") }
            off.containsMatchIn(q) -> false.also { q = off.replace(q, "") }
            else -> null
        }
        q = q.removePrefix("toggle ").trim()
        if (q.length < 2) return emptyList()
        return Toggle.entries.mapNotNull { t ->
            val exact = t.words.any { it == q }
            val prefix = q.length >= 3 && t.words.any { it.startsWith(q) }
            when { exact -> 0 to t; prefix -> 1 to t; else -> null }
        }.sortedBy { it.first }.map { ToggleRequest(it.second, wanted) }
    }

    fun volumes(query: String): List<VolumeStream> {
        val q = normalize(query)
        if (q.length < 3) return emptyList()
        return VolumeStream.entries.filter { s -> s.words.any { it == q || it.startsWith(q) || q.startsWith(it) } }
    }
}
