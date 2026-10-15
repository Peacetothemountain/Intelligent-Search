package com.pixel.intelligentsearch.feature.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pixel.intelligentsearch.R

/**
 * Quick Settings tile that opens the search overlay. On Android 14+ SystemUI launches the activity directly
 * ([Tile.setActivityLaunchForClick]); [onClick] covers older versions and launches SystemUI declined.
 */
class SearchTileService : TileService() {

    companion object {
        private const val TAG = "SearchTileService"
        private const val REQUEST_CODE_TILE_LAUNCH = 1001
        private const val LAUNCH_COOLDOWN_MS = 600L
        private const val ACTION_QUICK_TILE_LAUNCH = "com.pixel.intelligentsearch.QUICK_TILE_LAUNCH"

        @Volatile
        private var lastLaunchUptimeMs: Long = 0L
    }

    override fun onTileAdded() {
        super.onTileAdded()
        try {
            requestListeningState(this, ComponentName(this, SearchTileService::class.java))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not request initial listening state", e)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = getString(R.string.app_name)
        tile.subtitle = getString(R.string.widget_search_description)
        tile.contentDescription = getString(R.string.tile_content_description)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_search_lens_expressive)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                tile.activityLaunchForClick = launchPendingIntent()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "activityLaunchForClick rejected; onClick will launch instead", e)
            }
        }
        tile.updateTile()
    }

    private fun launchIntent() = Intent(this, WidgetActivity::class.java).apply {
        action = ACTION_QUICK_TILE_LAUNCH
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    /** Carries the background-activity-launch opt-in Android 14+ requires from the creator. */
    private fun launchPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_CODE_TILE_LAUNCH,
        launchIntent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        BackgroundLaunchOptions.forCreator(this).toBundle()
    )

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunchUptimeMs < LAUNCH_COOLDOWN_MS) return
        lastLaunchUptimeMs = now

        val launch = Runnable {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(launchPendingIntent())
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(launchIntent())
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "startActivityAndCollapse failed; sending the PendingIntent directly", e)
                try {
                    launchPendingIntent().send(this, 0, null, null, null, null, BackgroundLaunchOptions.forSender(this).toBundle())
                } catch (fallbackError: Exception) {
                    android.util.Log.e(TAG, "Tile launch failed", fallbackError)
                }
            }
        }
        // Behind the keyguard, ask the user to unlock first; the overlay shows personal results.
        if (isLocked) unlockAndRun(launch) else launch.run()
    }
}
