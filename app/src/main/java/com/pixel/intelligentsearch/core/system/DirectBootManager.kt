package com.pixel.intelligentsearch.core.system

import android.content.Context
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
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DirectBootManager"
    }

    private val userManager = context.getSystemService(UserManager::class.java)

    private fun queryIsUserUnlocked(): Boolean {
        return try {
            userManager?.isUserUnlocked ?: true
        } catch (e: Exception) {
            Log.w(TAG, "Error checking isUserUnlocked, assuming unlocked", e)
            true
        }
    }

    private val _isUserUnlocked = MutableStateFlow(queryIsUserUnlocked())
    val isUserUnlocked: StateFlow<Boolean> = _isUserUnlocked.asStateFlow()

    // USER_UNLOCKED is only delivered to runtime-registered receivers, never to the manifest one, so while
    // the user is locked we listen for it here. Otherwise a process started before first unlock (e.g. by
    // a widget update) stayed in "locked" mode until BOOT_COMPLETED, which can arrive much later.
    private var unlockReceiver: android.content.BroadcastReceiver? = null

    init {
        if (!_isUserUnlocked.value) registerUnlockReceiver()
    }

    @Synchronized
    private fun registerUnlockReceiver() {
        if (unlockReceiver != null) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: android.content.Intent) {
                onUserUnlocked()
            }
        }
        try {
            context.registerReceiver(
                receiver,
                android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED),
                Context.RECEIVER_NOT_EXPORTED
            )
            unlockReceiver = receiver
            // The user may have unlocked between the first check and registration.
            if (queryIsUserUnlocked()) onUserUnlocked()
        } catch (e: Exception) {
            Log.w(TAG, "Could not register USER_UNLOCKED receiver", e)
        }
    }

    @Synchronized
    private fun unregisterUnlockReceiver() {
        val receiver = unlockReceiver ?: return
        unlockReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }

    fun checkIsUserUnlocked(): Boolean {
        val unlocked = queryIsUserUnlocked()
        _isUserUnlocked.value = unlocked
        return unlocked
    }

    fun onUserUnlocked() {
        val wasLocked = !_isUserUnlocked.value
        _isUserUnlocked.value = true
        unregisterUnlockReceiver()
        if (wasLocked) {
            Log.i(TAG, "Device Credential-Encrypted (CE) storage unlocked! Migrating or refreshing data.")
        }
    }
}
