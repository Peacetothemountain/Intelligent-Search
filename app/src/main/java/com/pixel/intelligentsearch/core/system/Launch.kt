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

/**
 * A screen that closes once it's out of sight (the search overlay), which can be asked to wait instead while the user
 * turns on a permission on one of Android's pages, so Back from there comes back to it as it was.
 */
interface WaitsForPermissionGrant {
    /** Whether the screen waits, rather than closes, the next time it goes out of sight; cleared once it's back. */
    fun waitWhileGranting(waiting: Boolean)
}

/**
 * Opens the first of [pages] as [openSettingsPage] does, for a permission the user turns on there and then comes Back
 * from. The page opens in the asking screen's own task, so Back returns to that screen rather than to wherever
 * Android's settings were last left, and a screen that closes out of sight ([WaitsForPermissionGrant]) waits behind
 * it, with what was typed.
 */
fun openSettingsPageToGrant(context: Context, vararg pages: Intent): Boolean {
    val activity = context.findActivity() ?: return openSettingsPage(context, *pages)
    val waiting = activity as? WaitsForPermissionGrant
    waiting?.waitWhileGranting(true)
    val opened = pages.any { page ->
        try {
            activity.startActivity(page)
            true
        } catch (_: Exception) {
            false
        }
    } || openSettingsPage(context)
    if (!opened) waiting?.waitWhileGranting(false)
    return opened
}
