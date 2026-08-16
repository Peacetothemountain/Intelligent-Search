package com.pixel.intelligentsearch.core.system

import android.app.NotificationManager
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SystemToggleManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val nfcAdapter: NfcAdapter? = try { NfcAdapter.getDefaultAdapter(context) } catch (e: Exception) { null }

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn = _isFlashlightOn.asStateFlow()

    private var primaryCameraId: String? = null

    init {
        try {
            cameraManager?.let { cm ->
                for (id in cm.cameraIdList) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        primaryCameraId = id
                        break
                    }
                }
                if (primaryCameraId == null && cm.cameraIdList.isNotEmpty()) {
                    primaryCameraId = cm.cameraIdList[0]
                }

                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == primaryCameraId) {
                            _isFlashlightOn.value = enabled
                        }
                    }
                }, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 1. FLASHLIGHT / TORCH
    fun isTorchEnabled(): Boolean = _isFlashlightOn.value

    fun toggleTorch(enabled: Boolean) {
        try {
            val camId = primaryCameraId ?: return
            cameraManager?.setTorchMode(camId, enabled)
            _isFlashlightOn.value = enabled
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    fun getTorchStatusText(enabled: Boolean): String {
        return if (enabled) "Flashlight is on" else "Flashlight is off"
    }

    fun openTorchSettings() {
        openSettingsIntent(Settings.ACTION_SETTINGS)
    }

    // 2. BLUETOOTH
    fun isBluetoothEnabled(): Boolean = try { bluetoothAdapter?.isEnabled == true } catch (e: Exception) { false }

    fun toggleBluetooth(enabled: Boolean) {
        try {
            @Suppress("DEPRECATION")
            if (enabled) bluetoothAdapter?.enable() else bluetoothAdapter?.disable()
        } catch (e: Exception) {
            e.printStackTrace()
            openBluetoothSettings()
        }
    }

    fun getBluetoothStatusText(enabled: Boolean): String {
        if (!enabled) return "Bluetooth is off"
        try {
            if (bluetoothAdapter != null) {
                val bonded = bluetoothAdapter.bondedDevices
                if (bluetoothManager != null) {
                    val connectedGatt = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
                    if (connectedGatt.isNotEmpty()) {
                        return "Connected to ${connectedGatt.first().name ?: "Device"}"
                    }
                }
                val firstConnected = bonded?.firstOrNull { it.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED }
                if (firstConnected != null) {
                    return "Paired with ${firstConnected.name ?: "Bluetooth Device"}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Bluetooth is on • Ready to connect"
    }

    fun openBluetoothSettings() {
        openSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    // 3. WI-FI
    fun isWifiEnabled(): Boolean = try { wifiManager?.isWifiEnabled == true } catch (e: Exception) { false }

    fun toggleWifiDirect(enabled: Boolean) {
        try {
            val intent = Intent(Settings.Panel.ACTION_WIFI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (e: Exception) {
            openWifiSettings()
        }
    }

    fun getWifiStatusText(enabled: Boolean): String {
        if (!enabled) return "Wi-Fi is turned off"
        try {
            val activeNet = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNet)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                var foundSsid: String? = null
                
                // 1. Android 10+ TransportInfo WifiInfo
                val wifiInfo = caps.transportInfo as? android.net.wifi.WifiInfo
                val tSsid = wifiInfo?.ssid?.replace("\"", "")?.trim()
                if (!tSsid.isNullOrBlank() && tSsid != "<unknown ssid>" && tSsid != "0x") {
                    foundSsid = tSsid
                }

                // 2. WifiManager ConnectionInfo
                if (foundSsid == null) {
                    @Suppress("DEPRECATION")
                    val cSsid = wifiManager?.connectionInfo?.ssid?.replace("\"", "")?.trim()
                    if (!cSsid.isNullOrBlank() && cSsid != "<unknown ssid>" && cSsid != "0x") {
                        foundSsid = cSsid
                    }
                }

                // 3. NetworkInfo ExtraInfo
                if (foundSsid == null) {
                    @Suppress("DEPRECATION")
                    val extraInfo = connectivityManager?.activeNetworkInfo?.extraInfo?.replace("\"", "")?.trim()
                    if (!extraInfo.isNullOrBlank() && extraInfo != "<unknown ssid>" && extraInfo != "0x") {
                        foundSsid = extraInfo
                    }
                }

                // 4. ConfiguredNetworks active status
                if (foundSsid == null) {
                    try {
                        @Suppress("DEPRECATION")
                        val currentConfig = wifiManager?.configuredNetworks?.firstOrNull { 
                            it.status == android.net.wifi.WifiConfiguration.Status.CURRENT 
                        }
                        val confSsid = currentConfig?.SSID?.replace("\"", "")?.trim()
                        if (!confSsid.isNullOrBlank() && confSsid != "<unknown ssid>" && confSsid != "0x") {
                            foundSsid = confSsid
                        }
                    } catch (e: Exception) {}
                }

                if (!foundSsid.isNullOrBlank()) {
                    return "Connected to $foundSsid"
                }
                return "Connected to Wi-Fi"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Wi-Fi is on • Not connected"
    }

    fun openWifiSettings() {
        openSettingsIntent(Settings.ACTION_WIFI_SETTINGS)
    }

    // 4. MOBILE DATA / CELLULAR
    fun isMobileDataEnabled(): Boolean {
        return try {
            val activeNet = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNet)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } catch (e: Exception) { false }
    }

    fun getMobileDataStatusText(): String {
        val operator = telephonyManager?.networkOperatorName
        val isConnected = isMobileDataEnabled()
        return if (isConnected) {
            if (!operator.isNullOrBlank()) "Connected to $operator" else "Cellular data is active"
        } else {
            if (!operator.isNullOrBlank()) "$operator • Mobile data inactive" else "Mobile data is off"
        }
    }

    fun openMobileDataSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openSettingsIntent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            openSettingsIntent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        }
    }

    // 5. AIRPLANE MODE
    fun isAirplaneModeEnabled(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (e: Exception) { false }
    }

    fun getAirplaneModeStatusText(enabled: Boolean): String {
        return if (enabled) "Airplane mode is on • Radios off" else "Airplane mode is off"
    }

    fun openAirplaneModeSettings() {
        openSettingsIntent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    }

    // 6. AUTO-ROTATE
    fun isAutoRotateEnabled(): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        } catch (e: Exception) { false }
    }

    fun toggleAutoRotate(enabled: Boolean) {
        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    if (enabled) 1 else 0
                )
            } else {
                openAutoRotateSettings()
            }
        } catch (e: Exception) {
            openAutoRotateSettings()
        }
    }

    fun getAutoRotateStatusText(enabled: Boolean): String {
        return if (enabled) "Auto-rotate is on (Portrait & Landscape)" else "Portrait orientation locked"
    }

    fun openAutoRotateSettings() {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Grant write settings permission to toggle Auto-Rotate directly", Toast.LENGTH_LONG).show()
        } else {
            openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
        }
    }

    // 7. DO NOT DISTURB
    fun isDndEnabled(): Boolean {
        return try {
            val filter = notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Exception) { false }
    }

    fun toggleDnd(enabled: Boolean) {
        try {
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                val filter = if (enabled) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
                notificationManager.setInterruptionFilter(filter)
            } else {
                openDndSettings()
            }
        } catch (e: Exception) {
            openDndSettings()
        }
    }

    fun getDndStatusText(enabled: Boolean): String {
        return if (enabled) "Do Not Disturb is on • Calls & Alerts silenced" else "Do Not Disturb is off"
    }

    fun openDndSettings() {
        if (notificationManager?.isNotificationPolicyAccessGranted != true) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Grant Do Not Disturb access", Toast.LENGTH_LONG).show()
        } else {
            openSettingsIntent(Settings.ACTION_SOUND_SETTINGS)
        }
    }

    // 8. BATTERY SAVER
    fun isBatterySaverEnabled(): Boolean {
        return try { powerManager?.isPowerSaveMode == true } catch (e: Exception) { false }
    }

    fun getBatterySaverStatusText(enabled: Boolean): String {
        return if (enabled) "Battery Saver is active" else "Normal battery consumption"
    }

    fun openBatterySaverSettings() {
        openSettingsIntent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    }

    // 9. LOCATION / GPS
    fun isLocationEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager?.isLocationEnabled == true
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
            }
        } catch (e: Exception) { false }
    }

    fun getLocationStatusText(enabled: Boolean): String {
        return if (enabled) "Location is enabled • Accurate GPS" else "Location is turned off"
    }

    fun openLocationSettings() {
        openSettingsIntent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    // 10. HOTSPOT / TETHERING
    fun getHotspotStatusText(): String = "Tap to manage portable Wi-Fi hotspot"

    fun openHotspotSettings() {
        openSettingsIntent(Settings.ACTION_WIRELESS_SETTINGS)
    }

    // 11. DARK THEME / DARK MODE
    fun isDarkModeEnabled(): Boolean {
        return try {
            val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            currentNightMode == Configuration.UI_MODE_NIGHT_YES
        } catch (e: Exception) { false }
    }

    fun toggleDarkMode(enabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && uiModeManager != null) {
                uiModeManager.setApplicationNightMode(
                    if (enabled) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
                )
            } else {
                openDarkModeSettings()
            }
        } catch (e: Exception) {
            openDarkModeSettings()
        }
    }

    fun getDarkModeStatusText(enabled: Boolean): String {
        return if (enabled) "Dark theme is active" else "Light theme is active"
    }

    fun openDarkModeSettings() {
        openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
    }

    // 12. NIGHT LIGHT / BLUE LIGHT FILTER
    fun openNightLightSettings() {
        openSettingsIntent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)
    }

    // 13. NFC
    fun isNfcEnabled(): Boolean = try { nfcAdapter?.isEnabled == true } catch (e: Exception) { false }

    fun getNfcStatusText(enabled: Boolean): String {
        return if (enabled) "NFC is on • Contactless payments ready" else "NFC is turned off"
    }

    fun openNfcSettings() {
        openSettingsIntent(Settings.ACTION_NFC_SETTINGS)
    }

    // 14. VOLUME / SOUND RINGER MODE
    fun isSilentOrVibrate(): Boolean {
        return try {
            audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL
        } catch (e: Exception) { false }
    }

    fun toggleSoundMode(makeSilent: Boolean) {
        try {
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                audioManager?.ringerMode = if (makeSilent) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
            } else {
                openSoundSettings()
            }
        } catch (e: Exception) {
            openSoundSettings()
        }
    }

    fun getSoundStatusText(isVibrateOrSilent: Boolean): String {
        return if (isVibrateOrSilent) "Vibrate / Silent mode active" else "Ringtone & alerts active"
    }

    fun openSoundSettings() {
        openSettingsIntent(Settings.ACTION_SOUND_SETTINGS)
    }

    // 15. SCREEN CAST / SCREEN MIRRORING
    fun openCastSettings() {
        openSettingsIntent(Settings.ACTION_CAST_SETTINGS)
    }

    // 16. BRIGHTNESS / ADAPTIVE BRIGHTNESS
    fun openDisplayBrightnessSettings() {
        openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
    }

    // 17. PRIVACY / SENSOR PERMISSIONS (Camera & Mic Access)
    fun openPrivacySettings() {
        openSettingsIntent(Settings.ACTION_PRIVACY_SETTINGS)
    }

    private fun openSettingsIntent(action: String) {
        try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(fallback)
        }
    }
}
