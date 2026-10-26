package com.pixel.intelligentsearch.core.util

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/** [PackageManager.resolveActivity] with the flags type each Android version takes. */
fun PackageManager.resolveActivityCompat(intent: Intent): ResolveInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        resolveActivity(intent, 0)
    }

/** [PackageManager.queryIntentActivities] with the flags type each Android version takes. */
fun PackageManager.queryIntentActivitiesCompat(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, 0)
    }

/** [PackageManager.getApplicationInfo] with the flags type each Android version takes. */
fun PackageManager.getApplicationInfoCompat(packageName: String): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, 0)
    }
