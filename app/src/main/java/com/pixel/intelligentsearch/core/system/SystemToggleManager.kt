package com.pixel.intelligentsearch.core.system

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemToggleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SystemToggleManager"
    }

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
    private val nfcAdapter: NfcAdapter? = try { NfcAdapter.getDefaultAdapter(context) } catch (_: Exception) { null }

    // 1. Live Flashlight State
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private var primaryCameraId: String? = null
    private var maxTorchStrengthLevel: Int = 1

    // 2. Live Bluetooth State
    private val _isBluetoothEnabled = MutableStateFlow(checkBluetoothEnabled())
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    // 3. Live Wi-Fi State
    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val _wifiSsid = MutableStateFlow<String?>(null)
    val wifiSsid: StateFlow<String?> = _wifiSsid.asStateFlow()

    // 4. Live DND State
    private val _isDndEnabled = MutableStateFlow(checkDndEnabled())
    val isDndEnabled: StateFlow<Boolean> = _isDndEnabled.asStateFlow()

    // 5. Live Ringer Mode State
    private val _ringerMode = MutableStateFlow(audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL)
    val ringerMode: StateFlow<Int> = _ringerMode.asStateFlow()

    // 6. Live Location State
    private val _isLocationEnabled = MutableStateFlow(checkLocationEnabled())
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    init {
        setupCameraTorch()
        setupWifiCallback()
        setupBroadcastReceivers()
    }

    private fun setupCameraTorch() {
        try {
            cameraManager?.let { cm ->
                for (id in cm.cameraIdList) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        primaryCameraId = id
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            maxTorchStrengthLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                        }
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

                    override fun onTorchModeUnavailable(cameraId: String) {
                        if (cameraId == primaryCameraId) {
                            _isFlashlightOn.value = false
                        }
                    }
                }, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize camera torch callback", e)
        }
    }

    private fun setupWifiCallback() {
        try {
            val cm = connectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isWifiConnected.value = true
                    updateWifiInfo()
                }

                override fun onLost(network: Network) {
                    _isWifiConnected.value = false
                    _wifiSsid.value = null
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    updateWifiInfo()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
        updateWifiInfo()
    }

    private fun setupBroadcastReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                        _isBluetoothEnabled.value = checkBluetoothEnabled()
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        _ringerMode.value = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
                    }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                        _isDndEnabled.value = checkDndEnabled()
                    }
                    LocationManager.PROVIDERS_CHANGED_ACTION, LocationManager.MODE_CHANGED_ACTION -> {
                        _isLocationEnabled.value = checkLocationEnabled()
                    }
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register system toggle broadcast receiver", e)
        }
    }

    // ==========================================
    // 1. FLASHLIGHT / TORCH
    // ==========================================
    fun isTorchEnabled(): Boolean = _isFlashlightOn.value

    fun toggleTorch(enabled: Boolean, strengthLevel: Int? = null) {
        try {
            val camId = primaryCameraId ?: return
            if (enabled && strengthLevel != null && strengthLevel > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxTorchStrengthLevel > 1) {
                val clamped = strengthLevel.coerceIn(1, maxTorchStrengthLevel)
                cameraManager?.turnOnTorchWithStrengthLevel(camId, clamped)
            } else {
                cameraManager?.setTorchMode(camId, enabled)
            }
            _isFlashlightOn.value = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle torch", e)
            safeToast("Flashlight unavailable", Toast.LENGTH_SHORT)
        }
    }

    private fun safeToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, message, length).show()
            } catch (_: Throwable) {}
        }
    }

    fun getTorchStatusText(enabled: Boolean): String {
        return if (enabled) "Flashlight is on" else "Flashlight is off"
    }

    fun openTorchSettings() {
        openSettingsIntent(Settings.ACTION_SETTINGS)
    }

    // ==========================================
    // 2. BLUETOOTH
    // ==========================================
    private fun checkBluetoothEnabled(): Boolean = try {
        bluetoothAdapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    fun isBluetoothEnabled(): Boolean = _isBluetoothEnabled.value

    @SuppressLint("MissingPermission")
    fun toggleBluetooth(enabled: Boolean) {
        try {
            // Android 13+ (API 33+) disables direct adapter toggle for third-party apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                openBluetoothSettings()
                return
            }
            @Suppress("DEPRECATION")
            if (enabled) bluetoothAdapter?.enable() else bluetoothAdapter?.disable()
        } catch (e: Exception) {
            Log.w(TAG, "Direct bluetooth toggle unsupported; opening settings", e)
            openBluetoothSettings()
        }
    }

    @SuppressLint("MissingPermission")
    fun getBluetoothStatusText(enabled: Boolean): String {
        if (!enabled) return "Bluetooth is off"
        try {
            if (bluetoothAdapter != null) {
                if (bluetoothManager != null) {
                    val connectedGatt = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
                    if (connectedGatt.isNotEmpty()) {
                        return "Connected to ${connectedGatt.first().name ?: "Device"}"
                    }
                }
                val bonded = bluetoothAdapter.bondedDevices
                val firstConnected = bonded?.firstOrNull { it.bondState == BluetoothDevice.BOND_BONDED }
                if (firstConnected != null) {
                    return "Paired with ${firstConnected.name ?: "Device"}"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching bluetooth status text", e)
        }
        return "Bluetooth is on • Ready to connect"
    }

    fun openBluetoothSettings() {
        openSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    // ==========================================
    // 3. WI-FI
    // ==========================================
    fun isWifiEnabled(): Boolean = try { wifiManager?.isWifiEnabled == true } catch (_: Exception) { false }

    fun toggleWifiDirect(enabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } else {
                openWifiSettings()
            }
        } catch (e: Exception) {
            openWifiSettings()
        }
    }

    private fun updateWifiInfo() {
        try {
            val activeNet = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNet)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                var foundSsid: String? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val wifiInfo = caps.transportInfo as? android.net.wifi.WifiInfo
                    val tSsid = wifiInfo?.ssid?.replace("\"", "")?.trim()
                    if (!tSsid.isNullOrBlank() && tSsid != "<unknown ssid>" && tSsid != "0x") {
                        foundSsid = tSsid
                    }
                }
                if (foundSsid == null) {
                    @Suppress("DEPRECATION")
                    val cSsid = wifiManager?.connectionInfo?.ssid?.replace("\"", "")?.trim()
                    if (!cSsid.isNullOrBlank() && cSsid != "<unknown ssid>" && cSsid != "0x") {
                        foundSsid = cSsid
                    }
                }
                _wifiSsid.value = foundSsid
                _isWifiConnected.value = true
                return
            }
        } catch (_: Exception) {}
        _wifiSsid.value = null
        _isWifiConnected.value = false
    }

    fun getWifiStatusText(enabled: Boolean): String {
        if (!enabled) return "Wi-Fi is turned off"
        val ssid = _wifiSsid.value
        return if (!ssid.isNullOrBlank()) "Connected to $ssid" else "Wi-Fi is on • Not connected"
    }

    fun openWifiSettings() {
        openSettingsIntent(Settings.ACTION_WIFI_SETTINGS)
    }

    // ==========================================
    // 4. MOBILE DATA / CELLULAR
    // ==========================================
    fun isMobileDataEnabled(): Boolean {
        return try {
            val activeNet = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNet)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } catch (_: Exception) { false }
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

    // ==========================================
    // 5. AIRPLANE MODE
    // ==========================================
    fun isAirplaneModeEnabled(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) { false }
    }

    fun getAirplaneModeStatusText(enabled: Boolean): String {
        return if (enabled) "Airplane mode is on • Radios off" else "Airplane mode is off"
    }

    fun openAirplaneModeSettings() {
        openSettingsIntent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    }

    // ==========================================
    // 6. AUTO-ROTATE
    // ==========================================
    fun isAutoRotateEnabled(): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        } catch (_: Exception) { false }
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
        } catch (_: Exception) {
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
            safeToast("Grant write settings permission to toggle Auto-Rotate directly", Toast.LENGTH_LONG)
        } else {
            openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
        }
    }

    // ==========================================
    // 7. DO NOT DISTURB (ZEN MODE)
    // ==========================================
    private fun checkDndEnabled(): Boolean {
        return try {
            val filter = notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Exception) { false }
    }

    fun isDndEnabled(): Boolean = _isDndEnabled.value

    fun toggleDnd(enabled: Boolean) {
        try {
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                val filter = if (enabled) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
                notificationManager.setInterruptionFilter(filter)
                _isDndEnabled.value = enabled
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
            safeToast("Grant Do Not Disturb access", Toast.LENGTH_LONG)
        } else {
            openSettingsIntent(Settings.ACTION_SOUND_SETTINGS)
        }
    }

    // ==========================================
    // 8. BATTERY SAVER
    // ==========================================
    fun isBatterySaverEnabled(): Boolean {
        return try { powerManager?.isPowerSaveMode == true } catch (_: Exception) { false }
    }

    fun getBatterySaverStatusText(enabled: Boolean): String {
        return if (enabled) "Battery Saver is active" else "Normal battery consumption"
    }

    fun openBatterySaverSettings() {
        openSettingsIntent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    }

    // ==========================================
    // 9. LOCATION / GPS
    // ==========================================
    private fun checkLocationEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager?.isLocationEnabled == true
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) != Settings.Secure.LOCATION_MODE_OFF
            }
        } catch (_: Exception) { false }
    }

    fun isLocationEnabled(): Boolean = _isLocationEnabled.value

    fun getLocationStatusText(enabled: Boolean): String {
        return if (enabled) "Location is enabled • Accurate GPS" else "Location is turned off"
    }

    fun openLocationSettings() {
        openSettingsIntent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    // ==========================================
    // 10. HOTSPOT / TETHERING
    // ==========================================
    fun getHotspotStatusText(): String = "Tap to manage portable Wi-Fi hotspot"

    fun openHotspotSettings() {
        openSettingsIntent(Settings.ACTION_WIRELESS_SETTINGS)
    }

    // ==========================================
    // 11. DARK THEME
    // ==========================================
    fun isDarkModeEnabled(): Boolean {
        return try {
            val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            currentNightMode == Configuration.UI_MODE_NIGHT_YES
        } catch (_: Exception) { false }
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
        } catch (_: Exception) {
            openDarkModeSettings()
        }
    }

    fun getDarkModeStatusText(enabled: Boolean): String {
        return if (enabled) "Dark theme is active" else "Light theme is active"
    }

    fun openDarkModeSettings() {
        openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
    }

    // ==========================================
    // 12. NIGHT LIGHT
    // ==========================================
    fun openNightLightSettings() {
        openSettingsIntent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)
    }

    // ==========================================
    // 13. NFC
    // ==========================================
    fun isNfcEnabled(): Boolean = try { nfcAdapter?.isEnabled == true } catch (_: Exception) { false }

    fun getNfcStatusText(enabled: Boolean): String {
        return if (enabled) "NFC is on • Contactless ready" else "NFC is turned off"
    }

    fun openNfcSettings() {
        openSettingsIntent(Settings.ACTION_NFC_SETTINGS)
    }

    // ==========================================
    // 14. VOLUME / SOUND RINGER MODE
    // ==========================================
    fun isSilentOrVibrate(): Boolean {
        return try {
            _ringerMode.value != AudioManager.RINGER_MODE_NORMAL
        } catch (_: Exception) { false }
    }

    fun toggleSoundMode(makeSilent: Boolean) {
        try {
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                val newMode = if (makeSilent) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
                audioManager?.ringerMode = newMode
                _ringerMode.value = newMode
            } else {
                openSoundSettings()
            }
        } catch (e: Exception) {
            openSoundSettings()
        }
    }

    fun getSoundStatusText(isVibrateOrSilent: Boolean): String {
        return when (_ringerMode.value) {
            AudioManager.RINGER_MODE_SILENT -> "Silent mode active"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate mode active"
            else -> "Ringtone & alerts active"
        }
    }

    fun openSoundSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val panelIntent = Intent(Settings.Panel.ACTION_VOLUME).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(panelIntent)
                return
            } catch (_: Exception) {}
        }
        openSettingsIntent(Settings.ACTION_SOUND_SETTINGS)
    }

    // ==========================================
    // 15. SCREEN CAST
    // ==========================================
    fun openCastSettings() {
        openSettingsIntent(Settings.ACTION_CAST_SETTINGS)
    }

    // ==========================================
    // 16. BRIGHTNESS
    // ==========================================
    fun openDisplayBrightnessSettings() {
        openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
    }

    // ==========================================
    // 17. PRIVACY & SENSORS
    // ==========================================
    fun openPrivacySettings() {
        openSettingsIntent(Settings.ACTION_PRIVACY_SETTINGS)
    }

    private fun openSettingsIntent(action: String) {
        try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(fallback)
        }
    }
}
