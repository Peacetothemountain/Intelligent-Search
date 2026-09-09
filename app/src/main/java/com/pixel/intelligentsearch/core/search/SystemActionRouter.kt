package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.media.AudioManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import com.pixel.intelligentsearch.core.ui.SystemToggleUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep System Action & Settings Toggle Router.
 * Resolves user query intents into actionable system toggles and live volume sliders.
 *
 * Supports:
 * - Wi-Fi, Bluetooth, Flashlight, Battery Saver, Do Not Disturb, Airplane Mode, Auto-Rotate, Hotspot, Dark Theme, NFC, Location
 * - Direct inline Volume Sliders for Media, Ringtone, Alarm, and Notification streams
 */
@Singleton
class SystemActionRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemToggleManager: SystemToggleManager
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    data class VolumeSliderState(
        val streamType: Int,
        val streamName: String,
        val currentVolume: Int,
        val maxVolume: Int,
        val iconType: String
    )

    private val _mediaVolumeState = MutableStateFlow(getStreamState(AudioManager.STREAM_MUSIC, "Media Volume", "volume_media"))
    val mediaVolumeState: StateFlow<VolumeSliderState> = _mediaVolumeState.asStateFlow()

    private val _ringVolumeState = MutableStateFlow(getStreamState(AudioManager.STREAM_RING, "Ringtone Volume", "volume_ring"))
    val ringVolumeState: StateFlow<VolumeSliderState> = _ringVolumeState.asStateFlow()

    private val _alarmVolumeState = MutableStateFlow(getStreamState(AudioManager.STREAM_ALARM, "Alarm Volume", "volume_alarm"))
    val alarmVolumeState: StateFlow<VolumeSliderState> = _alarmVolumeState.asStateFlow()

    sealed class ActionResult {
        data class Toggle(val toggleUiState: SystemToggleUiState) : ActionResult()
        data class VolumeSlider(
            val streamType: Int,
            val title: String,
            val currentLevel: Int,
            val maxLevel: Int,
            val onVolumeChange: (Int) -> Unit
        ) : ActionResult()
    }

    private fun getStreamState(stream: Int, name: String, icon: String): VolumeSliderState {
        val current = audioManager?.getStreamVolume(stream) ?: 0
        val max = audioManager?.getStreamMaxVolume(stream) ?: 100
        return VolumeSliderState(stream, name, current, max, icon)
    }

    fun setStreamVolume(streamType: Int, level: Int) {
        audioManager?.setStreamVolume(streamType, level, 0)
        when (streamType) {
            AudioManager.STREAM_MUSIC -> _mediaVolumeState.value = getStreamState(streamType, "Media Volume", "volume_media")
            AudioManager.STREAM_RING -> _ringVolumeState.value = getStreamState(streamType, "Ringtone Volume", "volume_ring")
            AudioManager.STREAM_ALARM -> _alarmVolumeState.value = getStreamState(streamType, "Alarm Volume", "volume_alarm")
        }
    }

    /**
     * Matches a query string against known system toggles, settings, and volume slider actions.
     */
    fun matchAction(rawQuery: String): ActionResult? {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return null

        // 1. Volume Sliders matching
        if (q in listOf("volume", "media volume", "music volume", "sound slider", "audio")) {
            val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            return ActionResult.VolumeSlider(
                streamType = AudioManager.STREAM_MUSIC,
                title = "Media Volume",
                currentLevel = current,
                maxLevel = max,
                onVolumeChange = { setStreamVolume(AudioManager.STREAM_MUSIC, it) }
            )
        }

        if (q in listOf("ring volume", "ringtone volume", "call volume", "ring")) {
            val current = audioManager?.getStreamVolume(AudioManager.STREAM_RING) ?: 0
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_RING) ?: 7
            return ActionResult.VolumeSlider(
                streamType = AudioManager.STREAM_RING,
                title = "Ringtone Volume",
                currentLevel = current,
                maxLevel = max,
                onVolumeChange = { setStreamVolume(AudioManager.STREAM_RING, it) }
            )
        }

        if (q in listOf("alarm volume", "alarm sound")) {
            val current = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 0
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
            return ActionResult.VolumeSlider(
                streamType = AudioManager.STREAM_ALARM,
                title = "Alarm Volume",
                currentLevel = current,
                maxLevel = max,
                onVolumeChange = { setStreamVolume(AudioManager.STREAM_ALARM, it) }
            )
        }

        // 2. Binary System Toggles
        return when {
            q in listOf("flashlight", "torch", "flash light", "light", "flash") -> {
                val isTorch = systemToggleManager.isTorchEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "flashlight",
                        title = "Flashlight",
                        subtitle = systemToggleManager.getTorchStatusText(isTorch),
                        iconType = "flashlight",
                        isEnabled = isTorch,
                        onToggle = { systemToggleManager.toggleTorch(it) },
                        onOpenSettings = { systemToggleManager.openTorchSettings() }
                    )
                )
            }

            q in listOf("bluetooth", "bt", "blue tooth") -> {
                val isBt = systemToggleManager.isBluetoothEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "bluetooth",
                        title = "Bluetooth",
                        subtitle = systemToggleManager.getBluetoothStatusText(isBt),
                        iconType = "bluetooth",
                        isEnabled = isBt,
                        onToggle = { systemToggleManager.toggleBluetooth(it) },
                        onOpenSettings = { systemToggleManager.openBluetoothSettings() }
                    )
                )
            }

            q in listOf("wifi", "wi-fi", "internet", "wireless", "wlan") -> {
                val isWifi = systemToggleManager.isWifiEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "wifi",
                        title = "Wi-Fi",
                        subtitle = systemToggleManager.getWifiStatusText(isWifi),
                        iconType = "wifi",
                        isEnabled = isWifi,
                        onToggle = { systemToggleManager.toggleWifiDirect(it) },
                        onOpenSettings = { systemToggleManager.openWifiSettings() }
                    )
                )
            }

            q in listOf("hotspot", "tethering", "portable hotspot", "wifi hotspot", "personal hotspot") -> {
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "hotspot",
                        title = "Hotspot & Tethering",
                        subtitle = systemToggleManager.getHotspotStatusText(),
                        iconType = "hotspot",
                        isEnabled = false,
                        isActionOnly = true,
                        onToggle = { systemToggleManager.openHotspotSettings() },
                        onOpenSettings = { systemToggleManager.openHotspotSettings() }
                    )
                )
            }

            q in listOf("battery saver", "power saver", "low power mode", "battery", "saver") -> {
                val isBat = systemToggleManager.isBatterySaverEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "battery",
                        title = "Battery Saver",
                        subtitle = systemToggleManager.getBatterySaverStatusText(isBat),
                        iconType = "battery",
                        isEnabled = isBat,
                        isActionOnly = true,
                        onToggle = { systemToggleManager.openBatterySaverSettings() },
                        onOpenSettings = { systemToggleManager.openBatterySaverSettings() }
                    )
                )
            }

            q in listOf("dnd", "do not disturb", "silence", "mute phone", "priority only") -> {
                val isDnd = systemToggleManager.isDndEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "dnd",
                        title = "Do Not Disturb",
                        subtitle = systemToggleManager.getDndStatusText(isDnd),
                        iconType = "dnd",
                        isEnabled = isDnd,
                        onToggle = { systemToggleManager.toggleDnd(it) },
                        onOpenSettings = { systemToggleManager.openDndSettings() }
                    )
                )
            }

            q in listOf("airplane", "airplane mode", "aeroplane mode", "flight mode") -> {
                val isAir = systemToggleManager.isAirplaneModeEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "airplane",
                        title = "Airplane Mode",
                        subtitle = systemToggleManager.getAirplaneModeStatusText(isAir),
                        iconType = "airplane",
                        isEnabled = isAir,
                        isActionOnly = true,
                        onToggle = { systemToggleManager.openAirplaneModeSettings() },
                        onOpenSettings = { systemToggleManager.openAirplaneModeSettings() }
                    )
                )
            }

            q in listOf("auto rotate", "autorotate", "rotation", "screen rotation", "rotate") -> {
                val isRotate = systemToggleManager.isAutoRotateEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "autorotate",
                        title = "Auto-Rotate",
                        subtitle = systemToggleManager.getAutoRotateStatusText(isRotate),
                        iconType = "autorotate",
                        isEnabled = isRotate,
                        onToggle = { systemToggleManager.toggleAutoRotate(it) },
                        onOpenSettings = { systemToggleManager.openAutoRotateSettings() }
                    )
                )
            }

            q in listOf("dark mode", "dark theme", "night mode", "light mode", "theme") -> {
                val isDark = systemToggleManager.isDarkModeEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "darkmode",
                        title = "Dark Theme",
                        subtitle = systemToggleManager.getDarkModeStatusText(isDark),
                        iconType = "darkmode",
                        isEnabled = isDark,
                        onToggle = { systemToggleManager.toggleDarkMode(it) },
                        onOpenSettings = { systemToggleManager.openDarkModeSettings() }
                    )
                )
            }

            q in listOf("nfc", "contactless", "google pay") -> {
                val isNfc = systemToggleManager.isNfcEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "nfc",
                        title = "NFC",
                        subtitle = systemToggleManager.getNfcStatusText(isNfc),
                        iconType = "nfc",
                        isEnabled = isNfc,
                        isActionOnly = true,
                        onToggle = { systemToggleManager.openNfcSettings() },
                        onOpenSettings = { systemToggleManager.openNfcSettings() }
                    )
                )
            }

            q in listOf("location", "gps", "locate") -> {
                val isLoc = systemToggleManager.isLocationEnabled()
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "location",
                        title = "Location",
                        subtitle = systemToggleManager.getLocationStatusText(isLoc),
                        iconType = "location",
                        isEnabled = isLoc,
                        isActionOnly = true,
                        onToggle = { systemToggleManager.openLocationSettings() },
                        onOpenSettings = { systemToggleManager.openLocationSettings() }
                    )
                )
            }

            else -> null
        }
    }
}
