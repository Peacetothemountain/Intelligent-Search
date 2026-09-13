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
    @param:ApplicationContext private val context: Context,
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
    companion object {
        internal val KEYWORDS_MEDIA_VOLUME = setOf("volume", "media volume", "music volume", "sound slider", "audio")
        internal val KEYWORDS_RING_VOLUME = setOf("ring volume", "ringtone volume", "call volume", "ring")
        internal val KEYWORDS_ALARM_VOLUME = setOf("alarm volume", "alarm sound")
        internal val KEYWORDS_TORCH = setOf("flashlight", "torch", "flash light", "light", "flash")
        internal val KEYWORDS_BLUETOOTH = setOf("bluetooth", "bt", "blue tooth")
        internal val KEYWORDS_WIFI = setOf("wifi", "wi-fi", "internet", "wireless", "wlan")
        internal val KEYWORDS_HOTSPOT = setOf("hotspot", "tethering", "portable hotspot", "wifi hotspot", "personal hotspot")
        internal val KEYWORDS_BATTERY = setOf("battery saver", "power saver", "low power mode", "battery", "saver")
        internal val KEYWORDS_DND = setOf("dnd", "do not disturb", "silence", "mute phone", "priority only")
        internal val KEYWORDS_AIRPLANE = setOf("airplane", "airplane mode", "aeroplane mode", "flight mode")
        internal val KEYWORDS_ROTATE = setOf("auto rotate", "autorotate", "rotation", "screen rotation", "rotate")
        internal val KEYWORDS_DARK_MODE = setOf("dark mode", "dark theme", "night mode", "light mode", "theme")
        internal val KEYWORDS_NFC = setOf("nfc", "contactless", "google pay")
        internal val KEYWORDS_LOCATION = setOf("location", "gps", "locate")
        internal val KEYWORDS_CAST = setOf("cast", "screen cast", "screen mirroring", "chromecast")
        internal val KEYWORDS_BRIGHTNESS = setOf("brightness", "auto brightness", "screen brightness")
        internal val KEYWORDS_PRIVACY = setOf("privacy", "camera access", "mic access", "sensor privacy")
    }

    fun matchAction(rawQuery: String): ActionResult? {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return null

        // 1. Volume Sliders matching
        if (q in KEYWORDS_MEDIA_VOLUME) {
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

        if (q in KEYWORDS_RING_VOLUME) {
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

        if (q in KEYWORDS_ALARM_VOLUME) {
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
            q in KEYWORDS_TORCH -> {
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

            q in KEYWORDS_BLUETOOTH -> {
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

            q in KEYWORDS_WIFI -> {
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

            q in KEYWORDS_HOTSPOT -> {
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

            q in KEYWORDS_BATTERY -> {
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

            q in KEYWORDS_DND -> {
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

            q in KEYWORDS_AIRPLANE -> {
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

            q in KEYWORDS_ROTATE -> {
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

            q in KEYWORDS_DARK_MODE -> {
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

            q in KEYWORDS_NFC -> {
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

            q in KEYWORDS_LOCATION -> {
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

            q in KEYWORDS_CAST -> {
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "cast",
                        title = "Screen Cast",
                        subtitle = "Mirror screen to TV / Displays",
                        iconType = "cast",
                        isEnabled = false,
                        isActionOnly = true,
                        onToggle = { systemToggleManager.openCastSettings() },
                        onOpenSettings = { systemToggleManager.openCastSettings() }
                    )
                )
            }

            q in KEYWORDS_BRIGHTNESS -> {
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "brightness",
                        title = "Display Brightness",
                        subtitle = "Screen & adaptive brightness",
                        iconType = "brightness",
                        isEnabled = false,
                        isActionOnly = true,
                        onToggle = { systemToggleManager.openDisplayBrightnessSettings() },
                        onOpenSettings = { systemToggleManager.openDisplayBrightnessSettings() }
                    )
                )
            }

            q in KEYWORDS_PRIVACY -> {
                ActionResult.Toggle(
                    SystemToggleUiState(
                        id = "privacy",
                        title = "Privacy & Sensors",
                        subtitle = "Microphone & Camera Permissions",
                        iconType = "privacy",
                        isEnabled = true,
                        isActionOnly = true,
                        onToggle = { systemToggleManager.openPrivacySettings() },
                        onOpenSettings = { systemToggleManager.openPrivacySettings() }
                    )
                )
            }

            else -> null
        }
    }
}
