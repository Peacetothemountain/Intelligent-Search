package com.pixel.intelligentsearch.core.security

import android.content.Context
import android.os.Build
import android.security.advancedprotection.AdvancedProtectionManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks Android Advanced Protection mode (API 36+).
 *
 * When the user turns on Advanced Protection, the app stops reading the clipboard and stops
 * sending keystrokes to remote suggestion endpoints. On older releases, or if the system
 * service is unavailable, the mode is reported as off.
 */
object AdvancedProtectionMonitor {
    private const val TAG = "AdvancedProtection"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Volatile
    private var registered = false

    fun isEnabled(context: Context): Boolean {
        ensureRegistered(context)
        return _enabled.value
    }

    fun ensureRegistered(context: Context) {
        if (registered || Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        synchronized(this) {
            if (registered) return
            registered = true
            val manager = try {
                context.applicationContext.getSystemService(AdvancedProtectionManager::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "Advanced Protection service unavailable: ${e.message}")
                null
            } ?: return

            try {
                _enabled.value = manager.isAdvancedProtectionEnabled
                manager.registerAdvancedProtectionCallback(context.applicationContext.mainExecutor) { on ->
                    _enabled.value = on
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing QUERY_ADVANCED_PROTECTION_MODE permission: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query Advanced Protection state: ${e.message}")
            }
        }
    }
}
