package com.pixel.intelligentsearch.feature.widget

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pixel.intelligentsearch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Intelligent Search Quick Settings Tile Service.
 * Modernized for Android 17 (Canary ZP11) and Android 14+ / Android 15 (API 35) Quick Settings architecture:
 * - Direct SystemUI click-to-activity dispatch via [Tile.setActivityLaunchForClick] on Android 14+ (API 34+)
 * - Strict Background Activity Launch (BAL) creator guards via [ActivityOptions.setPendingIntentCreatorBackgroundActivityStartMode]
 * - Non-blocking asynchronous preference caching to ensure 120 FPS QS shade pull-downs with zero disk I/O
 * - Keyguard unlock mediation and refractory click debounce guards for legacy / fallback paths
 * - Material 3 Expressive active accent state with contextual query subtitles
 */
class SearchTileService : TileService() {

    companion object {
        private const val TAG = "SearchTileService"
        private const val REQUEST_CODE_TILE_LAUNCH = 1001
        private const val LAUNCH_COOLDOWN_MS = 600L
        private const val PREFS_NAME = "PREFERENCES_CUSTOMISATIONS"
        private const val KEY_LAST_QUERY = "last_search_query"

        @Volatile
        private var lastLaunchUptimeMs: Long = 0L

        // Process-wide so a freshly created service instance can paint the last known query instantly.
        @Volatile
        private var cachedLastQuery: String? = null

        private val NEWLINE_RUNS = Regex("[\\r\\n\\t]+")
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Per instance on purpose: the listener is unregistered in onDestroy(), so a recreated service
    // must register its own again. Tracking this process-wide would leave later instances deaf to
    // preference changes.
    @Volatile
    private var isPrefsListenerRegistered = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == KEY_LAST_QUERY) {
            val updated = prefs.getString(KEY_LAST_QUERY, null)
            cachedLastQuery = updated
            serviceScope.launch(Dispatchers.Main) {
                updateTileContent()
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        try {
            requestListeningState(this, android.content.ComponentName(this, SearchTileService::class.java))
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "Could not request initial listening state on tile added", e)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileContent()
        ensurePrefsAsync()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Throwable) {}
        serviceScope.cancel()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
    }

    /**
     * Updates the Quick Settings Tile state, labels, subtitles, and click intent.
     * Guaranteed to execute without synchronous disk I/O on the main thread.
     */
    private fun updateTileContent() {
        val tile = qsTile ?: return

        val appName = getString(R.string.app_name)
        val defaultSubtitle = getString(R.string.widget_search_description)

        // Modern Material 3 Expressive Quick Settings: Tile is active with system accent color
        tile.state = Tile.STATE_ACTIVE
        tile.label = appName
        tile.contentDescription = getString(R.string.tile_content_description)

        // Set high-fidelity active vector icon
        tile.icon = Icon.createWithResource(this, R.drawable.ic_search_lens_expressive)

        // Android 14+ (API 34+) / Android 15 / Android 17: direct platform launch without TileService wake-up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                tile.activityLaunchForClick = createLaunchPendingIntent(this)
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed setting activityLaunchForClick, relying on onClick fallback", e)
            }
        }

        val isDeviceLocked = try { isLocked } catch (_: Throwable) { false }
        val lastQuery = cachedLastQuery
        val sanitizedQuery = lastQuery
            ?.replace(NEWLINE_RUNS, " ")
            ?.trim()
            ?.take(24)
            ?.let { if (it.length < (lastQuery.trim().length)) "$it…" else it }

        val subtitle = if (!isDeviceLocked && !sanitizedQuery.isNullOrBlank()) {
            getString(R.string.tile_subtitle_last_query, sanitizedQuery)
        } else {
            defaultSubtitle
        }

        tile.subtitle = subtitle
        tile.stateDescription = subtitle

        tile.updateTile()
    }

    /**
     * Asynchronously preloads or synchronizes the preference cache to eliminate main-thread disk I/O.
     */
    private fun ensurePrefsAsync() {
        if (isPrefsListenerRegistered) return
        isPrefsListenerRegistered = true
        serviceScope.launch {
            try {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val initialQuery = prefs.getString(KEY_LAST_QUERY, null)
                cachedLastQuery = initialQuery
                prefs.registerOnSharedPreferenceChangeListener(prefsListener)

                if (!initialQuery.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        updateTileContent()
                    }
                }
            } catch (e: Throwable) {
                isPrefsListenerRegistered = false
                android.util.Log.w(TAG, "Failed to load preferences asynchronously", e)
            }
        }
    }

    /**
     * Constructs a hardened PendingIntent equipped with Android 15+ / 14+ Background Activity Launch (BAL) permissions.
     */
    @Suppress("DEPRECATION")
    private fun createLaunchPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetActivity::class.java).apply {
            action = "com.pixel.intelligentsearch.QUICK_TILE_LAUNCH"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        val options = ActivityOptions.makeCustomAnimation(context, 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_TILE_LAUNCH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options.toBundle()
        )
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        // Background Launch Guard 1: Enforce refractory click debounce to eliminate race conditions
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunchUptimeMs < LAUNCH_COOLDOWN_MS) {
            android.util.Log.d(TAG, "Ignoring QS Tile click within refractory debounce period")
            return
        }
        lastLaunchUptimeMs = now

        val launchAction = Runnable {
            val intent = Intent(this, WidgetActivity::class.java).apply {
                action = "com.pixel.intelligentsearch.QUICK_TILE_LAUNCH"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = createLaunchPendingIntent(this)
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            } catch (e: Throwable) {
                // Background Launch Guard 3: Guarded fallback against platform-specific BAL rejections
                android.util.Log.e(TAG, "Failed to launch activity from QS Tile via startActivityAndCollapse", e)
                try {
                    val pendingIntent = createLaunchPendingIntent(this)
                    val senderOptions = ActivityOptions.makeCustomAnimation(this, 0, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        @Suppress("DEPRECATION")
                        senderOptions.setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }
                    pendingIntent.send(this, 0, null, null, null, null, senderOptions.toBundle())
                } catch (fallbackError: Throwable) {
                    try {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(intent)
                    } catch (lastError: Throwable) {
                        android.util.Log.e(TAG, "Guarded fallback activity launch also failed", lastError)
                    }
                }
            }
        }

        // Background Launch Guard 4: Keyguard authentication mediation
        val locked = try { isLocked } catch (_: Throwable) { false }
        if (locked) {
            try {
                unlockAndRun(launchAction)
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed unlockAndRun, fallback to direct run", e)
                launchAction.run()
            }
        } else {
            launchAction.run()
        }
    }
}
