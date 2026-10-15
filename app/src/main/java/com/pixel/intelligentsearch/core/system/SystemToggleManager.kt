package com.pixel.intelligentsearch.core.system

import com.pixel.intelligentsearch.core.data.SettingsManager
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SystemToggleManager"
        private const val KEY_APP_NIGHT_MODE_RESET = "app_night_mode_override_cleared_v1"
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

    // 7. Live Battery Saver State
    private val _isBatterySaverEnabled = MutableStateFlow(isBatterySaverEnabled())
    val isBatterySaverEnabled: StateFlow<Boolean> = _isBatterySaverEnabled.asStateFlow()

    // 8. Live Airplane Mode State
    private val _isAirplaneModeEnabled = MutableStateFlow(isAirplaneModeEnabled())
    val isAirplaneModeEnabled: StateFlow<Boolean> = _isAirplaneModeEnabled.asStateFlow()

    private var torchCallback: CameraManager.TorchCallback? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var toggleReceiver: BroadcastReceiver? = null

    init {
        registerListenersSafely()
    }

    fun registerListenersSafely() {
        setupCameraTorch()
        setupWifiCallback()
        setupBroadcastReceivers()
    }

    private fun setupCameraTorch() {
        if (torchCallback != null) return
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

                val callback = object : CameraManager.TorchCallback() {
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
                }
                cm.registerTorchCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
                torchCallback = callback
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize camera torch callback", e)
        }
    }

    private fun setupWifiCallback() {
        if (networkCallback != null) return
        try {
            val cm = connectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isWifiConnected.value = true
                    updateWifiInfo()
                }

                override fun onLost(network: Network) {
                    _isWifiConnected.value = false
                    _wifiSsid.value = null
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    updateWifiInfo(networkCapabilities)
                }
            }
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
        updateWifiInfo()
    }

    private fun setupBroadcastReceivers() {
        if (toggleReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
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
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        _isBatterySaverEnabled.value = isBatterySaverEnabled()
                    }
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        _isAirplaneModeEnabled.value = isAirplaneModeEnabled()
                    }
                }
            }
        }

        try {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            toggleReceiver = receiver
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
            if (primaryCameraId == null) {
                setupCameraTorch()
            }
            val camId = primaryCameraId ?: run {
                safeToast("Flashlight unavailable", Toast.LENGTH_SHORT)
                return
            }
            if (enabled && strengthLevel != null && strengthLevel > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxTorchStrengthLevel > 1) {
                val clamped = strengthLevel.coerceIn(1, maxTorchStrengthLevel)
                cameraManager?.turnOnTorchWithStrengthLevel(camId, clamped)
            } else {
                cameraManager?.setTorchMode(camId, enabled)
            }
            _isFlashlightOn.value = enabled
        } catch (se: SecurityException) {
            Log.w(TAG, "Camera flashlight access restricted", se)
            safeToast("Flashlight permission restricted", Toast.LENGTH_SHORT)
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
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            false
        } else {
            bluetoothAdapter?.isEnabled == true
        }
    } catch (_: SecurityException) {
        false
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
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openBluetoothSettings()
                return
            }
            @Suppress("DEPRECATION")
            if (enabled) bluetoothAdapter?.enable() else bluetoothAdapter?.disable()
        } catch (se: SecurityException) {
            Log.w(TAG, "Direct bluetooth toggle restricted; opening settings", se)
            openBluetoothSettings()
        } catch (e: Exception) {
            Log.w(TAG, "Direct bluetooth toggle unsupported; opening settings", e)
            openBluetoothSettings()
        }
    }

    fun getBluetoothStatusText(enabled: Boolean): String {
        return if (enabled) "Bluetooth is on" else "Bluetooth is off"
    }

    fun openBluetoothSettings() {
        openSettingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    // ==========================================
    // 3. WI-FI
    // ==========================================
    fun isWifiEnabled(): Boolean = try { wifiManager?.isWifiEnabled == true } catch (_: SecurityException) { false } catch (_: Exception) { false }

    fun toggleWifiDirect(enabled: Boolean) {
        try {
            val intent = Intent(Settings.Panel.ACTION_WIFI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
            openWifiSettings()
        } catch (_: Exception) {
            openWifiSettings()
        }
    }

    private fun updateWifiInfo(caps: NetworkCapabilities? = null) {
        try {
            val effectiveCaps = caps ?: connectivityManager?.activeNetwork?.let { activeNet ->
                connectivityManager.getNetworkCapabilities(activeNet)
            }
            if (effectiveCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                var foundSsid: String? = null
                val wifiInfo = effectiveCaps.transportInfo as? android.net.wifi.WifiInfo
                val tSsid = wifiInfo?.ssid?.replace("\"", "")?.trim()
                if (!tSsid.isNullOrBlank() && tSsid != "<unknown ssid>" && tSsid != "0x") {
                    foundSsid = tSsid
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
        } catch (_: SecurityException) {
            openAutoRotateSettings()
        } catch (_: Exception) {
            openAutoRotateSettings()
        }
    }

    fun getAutoRotateStatusText(enabled: Boolean): String {
        return if (enabled) "Auto-rotate is on (Portrait & Landscape)" else "Portrait orientation locked"
    }

    fun openAutoRotateSettings() {
        if (!Settings.System.canWrite(context)) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    safeToast("Grant write settings permission to toggle Auto-Rotate directly", Toast.LENGTH_LONG)
                    return
                }
            } catch (_: Exception) {}
            try {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (fallbackIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(fallbackIntent)
                    safeToast("Grant write settings permission to toggle Auto-Rotate directly", Toast.LENGTH_LONG)
                    return
                }
            } catch (_: Exception) {}
        }
        openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
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
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification policy access restricted by system", e)
            openDndSettings()
        } catch (e: Exception) {
            openDndSettings()
        }
    }

    fun getDndStatusText(enabled: Boolean): String {
        return if (enabled) "Do Not Disturb is on • Calls & Alerts silenced" else "Do Not Disturb is off"
    }

    fun openDndSettings() {
        if (notificationManager?.isNotificationPolicyAccessGranted != true) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    safeToast("Grant Do Not Disturb access", Toast.LENGTH_LONG)
                    return
                }
            } catch (_: Exception) {}
            openSettingsIntent(Settings.ACTION_SOUND_SETTINGS)
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
            locationManager?.isLocationEnabled == true
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

    /**
     * Third-party apps cannot change the system-wide dark theme (that needs MODIFY_DAY_NIGHT_MODE), so
     * the toggle opens the system page. UiModeManager.setApplicationNightMode only re-themes this app
     * and persists, which left the overlay stuck in one theme while reporting the system as switched.
     */
    fun toggleDarkMode(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        openDarkModeSettings()
    }

    /**
     * Clears a per-app night-mode override persisted by older builds' dark-theme toggle so the app
     * follows the system theme again. Runs once.
     */
    fun clearLegacyAppNightModeOverride() {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_APP_NIGHT_MODE_RESET, false)) return
        try {
            uiModeManager?.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
            prefs.edit().putBoolean(KEY_APP_NIGHT_MODE_RESET, true).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear per-app night mode override", e)
        }
    }

    fun getDarkModeStatusText(enabled: Boolean): String {
        return if (enabled) "Dark theme is active" else "Light theme is active"
    }

    fun openDarkModeSettings() {
        try {
            val darkIntent = Intent("android.settings.DARK_THEME_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (darkIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(darkIntent)
                return
            }
        } catch (_: Exception) {}
        openSettingsIntent(Settings.ACTION_DISPLAY_SETTINGS)
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
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (fallback.resolveActivity(context.packageManager) != null) {
                context.startActivity(fallback)
            }
        } catch (_: Exception) {}
    }
}
