package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.IntentCompat
import com.pixel.intelligentsearch.MainActivity
import com.pixel.intelligentsearch.feature.search.SearchActivity

/**
 * Opens what a tap on the home screen's search bar opens (the search overlay, Lens, voice, a shortcut) without Pixel
 * Launcher's widget launch animation.
 *
 * Tapped as an activity, the target is grown out of the bar by the launcher (decompiled QuickstepTransitionManager):
 * a copy of the bar's background (the lavender bar at the top), a black fill and a dark scrim for an opaque app, a
 * sharp-cornered window growing out of the rounded bar for a translucent one, and back into the bar on the way home.
 * Tapped as a broadcast, the launcher only hands its animation to this app's next activity start (decompiled
 * QuickstepInteractionHandler's registerRemoteAnimationForNextActivityStart), with a launch cookie that ties the
 * opened task to the bar; [LaunchAnimationSink], started right after and never shown, takes them, so the target opens
 * and closes as it does from anywhere else.
 */
class WidgetLaunchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val target = IntentCompat.getParcelableExtra(intent, EXTRA_TARGET, Intent::class.java) ?: return
        // The home app changed since the bar was drawn: redrawn, its taps go back to activities, which always open.
        if (!homeIsPixelLauncher(context)) refreshWidgets(context)
        val overlay = target.component?.className in OVERLAY_ENTRIES
        if (overlay) closeLeftoverOverlays(context)
        try {
            // The overlay brings its own entrance; Lens, voice and shortcuts open with Android's usual motion.
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (overlay) target.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            context.startActivity(target)
        } catch (e: Exception) {
            Log.w(TAG, "The search bar's tap couldn't open its target", e)
        }
        try {
            context.startActivity(
                Intent(context, LaunchAnimationSink::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            )
        } catch (e: Exception) {
            Log.w(TAG, "The launch animation sink couldn't start", e)
        }
    }

    /**
     * An overlay left in the background (Settings opened from it, then Home) would be brought back, and Android shows a
     * placeholder of it first: drawn from the theme, as the overlay keeps itself out of Recents screenshots, a black
     * screen under a lavender status bar. Closed here, the bar always opens a fresh one.
     */
    private fun closeLeftoverOverlays(context: Context) {
        runCatching {
            context.getSystemService(ActivityManager::class.java).appTasks
                .filter { it.taskInfo?.baseActivity?.className in OVERLAY_ENTRIES }
                .forEach { it.finishAndRemoveTask() }
        }
    }

    private fun refreshWidgets(context: Context) {
        runCatching {
            SearchWidgetProvider.updateAllWidgets(context)
            CountdownWidgetProvider.updateAll(context)
        }.onFailure { Log.w(TAG, "Couldn't redraw the widgets for the new home app", it) }
    }

    companion object {
        private const val TAG = "WidgetLaunchReceiver"
        private const val EXTRA_TARGET = "com.pixel.intelligentsearch.extra.WIDGET_LAUNCH_TARGET"
        private const val PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher"

        /** The activities the overlay opens as. */
        private val OVERLAY_ENTRIES = setOf(MainActivity::class.java.name, SearchActivity::class.java.name, WidgetActivity::class.java.name)

        /**
         * Whether the home screen is Pixel Launcher, which lets a broadcast tap start an activity (it sends widget taps
         * with background activity starts allowed). Other launchers keep activity taps, which always open.
         */
        fun homeIsPixelLauncher(context: Context): Boolean = runCatching {
            context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName == PIXEL_LAUNCHER
        }.getOrDefault(false)

        /** A widget tap that opens [target] through this receiver; [requestCode] keeps each tap's own. */
        fun pendingIntent(context: Context, requestCode: Int, target: Intent): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WidgetLaunchReceiver::class.java)
                .putExtra(EXTRA_TARGET, target)
                .setIdentifier(target.identifier)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Never shows (Theme.NoDisplay): it takes the launcher's launch animation and finishes at once. */
class LaunchAnimationSink : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
