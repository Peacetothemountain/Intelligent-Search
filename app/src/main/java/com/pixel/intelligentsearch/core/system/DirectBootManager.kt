package com.pixel.intelligentsearch.core.system

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectBootManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DirectBootManager"
        private const val PREFS_DIRECT_BOOT = "direct_boot_emergency_cache"
        const val KEY_EMERGENCY_DATA_MIGRATED = "emergency_data_migrated"
    }

    private val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager

    private val _isUserUnlocked = MutableStateFlow(checkIsUserUnlocked())
    val isUserUnlocked: StateFlow<Boolean> = _isUserUnlocked.asStateFlow()

    fun checkIsUserUnlocked(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                userManager?.isUserUnlocked(Process.myUserHandle()) ?: true
            } else {
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking isUserUnlocked, assuming unlocked", e)
            true
        }
    }

    fun onUserUnlocked() {
        val wasLocked = !_isUserUnlocked.value
        _isUserUnlocked.value = true
        if (wasLocked) {
            Log.i(TAG, "Device Credential-Encrypted (CE) storage unlocked! Migrating or refreshing data.")
        }
    }

    fun getDeviceProtectedContext(): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    fun getDeviceProtectedPreferences(): SharedPreferences {
        val dpContext = getDeviceProtectedContext()
        return dpContext.getSharedPreferences(PREFS_DIRECT_BOOT, Context.MODE_PRIVATE)
    }

    fun isDirectBootActive(): Boolean = !_isUserUnlocked.value
}
