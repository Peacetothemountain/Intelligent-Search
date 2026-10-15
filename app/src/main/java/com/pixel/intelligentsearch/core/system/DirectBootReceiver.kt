package com.pixel.intelligentsearch.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * After boot (delivered once the user has unlocked), marks storage unlocked and redraws widgets from the
 * user's real settings. Unlocks later in a session are observed at runtime by [DirectBootManager]; manifest
 * receivers never get ACTION_USER_UNLOCKED.
 */
@AndroidEntryPoint
class DirectBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var directBootManager: DirectBootManager

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        directBootManager.onUserUnlocked()
        SystemDataProvider.invalidateAppsCache()
        // goAsync keeps the low-priority boot-time process alive until the redraw finishes.
        widgetUpdater.updateAll(goAsync())
    }
}
