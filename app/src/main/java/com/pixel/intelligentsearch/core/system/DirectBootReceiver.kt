package com.pixel.intelligentsearch.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DirectBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DirectBootReceiver"
        private const val REFRESH_DEBOUNCE_MS = 2500L
        @Volatile
        private var lastRefreshTimestamp = 0L
    }

    @Inject
    lateinit var directBootManager: DirectBootManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "DirectBoot broadcast received: $action")

        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(TAG, "Device booted in Direct Boot mode. Emergency caches active.")
            }
            Intent.ACTION_USER_UNLOCKED, Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Device unlocked / Boot completed event: $action")
                directBootManager.onUserUnlocked()
                val now = System.currentTimeMillis()
                if (now - lastRefreshTimestamp > REFRESH_DEBOUNCE_MS) {
                    lastRefreshTimestamp = now
                    SystemDataProvider.invalidateAppsCache()
                    com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.updateAllWidgets(context)
                }
            }
        }
    }
}
