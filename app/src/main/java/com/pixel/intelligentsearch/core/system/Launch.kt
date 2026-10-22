package com.pixel.intelligentsearch.core.system

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.pixel.intelligentsearch.core.util.findActivity

/**
 * Starting other apps and Android's own pages from the search, settings and widgets, one way everywhere: nothing
 * throws, every launch says whether it worked so the caller can fall back, and nothing falls back to a page the caller
 * didn't name.
 */
private const val TAG = "Launch"

/** Starts [intent]; returns false when nothing could handle it so callers can fall back. */
fun launchSafeIntent(context: Context, intent: Intent, options: Bundle? = null): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        if (options != null) context.startActivity(intent, options) else context.startActivity(intent)
        true
    } catch (e: Exception) {
        try {
            val activity = context.findActivity() ?: return false
            activity.startActivity(intent, options)
            true
        } catch (e2: Exception) {
            Log.w(TAG, "Nothing could open ${intent.action}", e2)
            false
        }
    }
}

/** Opens [packageName]'s launcher activity; false if it has none. */
fun launchApp(context: Context, packageName: String): Boolean {
    val launch = runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }.getOrNull() ?: return false
    return launchSafeIntent(context, launch)
}

/**
 * Sends another app's [intent] (a notification's tap action, a player's activity) from the search on screen. From
 * Android 14 the sender has to say it lets the intent open an activity; the search is in front, so it may.
 */
fun sendPendingIntent(context: Context, intent: PendingIntent): Boolean = try {
    val options = when {
        Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
            .toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
        else -> null
    }
    intent.send(context, 0, null, null, null, null, options)
    true
} catch (_: PendingIntent.CanceledException) {
    false
} catch (_: RuntimeException) {
    false
}

/**
 * What tapping a notification, a timer or a player does: its own [pending] action, else [fallback] (the clock's
 * timers, say), else [packageName]'s app. False when none of them opened.
 */
fun openPendingOrApp(context: Context, pending: PendingIntent?, packageName: String, fallback: Intent? = null): Boolean =
    (pending != null && sendPendingIntent(context, pending)) ||
        (fallback != null && launchSafeIntent(context, fallback)) ||
        launchApp(context, packageName)

/**
 * Opens the first of [pages] (an Android settings page, then its more general fallbacks) that this phone has, else
 * this app's own page in Android settings, where every permission can be changed. False only when even that failed.
 */
fun openSettingsPage(context: Context, vararg pages: Intent): Boolean =
    pages.any { launchSafeIntent(context, it) } ||
        launchSafeIntent(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
