package com.pixel.intelligentsearch.core.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings

/** Settings' extra carrying the arguments of the page it opens. */
internal const val SETTINGS_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

/** Settings' extra naming the row to scroll to and highlight. */
internal const val SETTINGS_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"

/** The key of App info's "Display over other apps" row. */
internal const val OVERLAY_PREFERENCE_KEY = "system_alert_window"

/**
 * The pages to try, in order, to reach [packageName]'s "Display over other apps" switch on Android [sdkInt], as close
 * to it as each release lets an app go. The per-app page itself is closed to other apps, and the list page ignores
 * the package it is given, so:
 *
 * - Android 12 and 13: App info, scrolled to its "Display over other apps" row with Settings' own highlight on it
 *   (one tap from the switch), then the list page.
 * - Android 14 and later: App info is a new screen that drops the highlight, so the list page, where the app is
 *   listed by name, then App info.
 */
fun overlayPermissionPages(packageName: String, sdkInt: Int = Build.VERSION.SDK_INT): List<Intent> {
    val packageUri = Uri.fromParts("package", packageName, null)
    val list = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
    val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return listOf(list, appInfo)
    val highlighted = appInfo
        .putExtra(SETTINGS_FRAGMENT_ARGS_KEY, OVERLAY_PREFERENCE_KEY)
        .putExtra(SETTINGS_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(SETTINGS_FRAGMENT_ARGS_KEY, OVERLAY_PREFERENCE_KEY) })
    return listOf(highlighted, list)
}

/** Opens the first of [overlayPermissionPages] this phone has; false only when not even App info opened. */
fun openOverlayPermission(context: Context): Boolean =
    openSettingsPage(context, *overlayPermissionPages(context.packageName).toTypedArray())
