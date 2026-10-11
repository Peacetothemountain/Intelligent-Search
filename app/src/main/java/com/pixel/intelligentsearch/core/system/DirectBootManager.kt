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

    fun checkIsUserUnlocked(): Boolean {
        val unlocked = queryIsUserUnlocked()
        _isUserUnlocked.value = unlocked
        return unlocked
    }

    fun onUserUnlocked() {
        val wasLocked = !_isUserUnlocked.value
        _isUserUnlocked.value = true
        if (wasLocked) {
            Log.i(TAG, "Device Credential-Encrypted (CE) storage unlocked! Migrating or refreshing data.")
        }
    }
}
