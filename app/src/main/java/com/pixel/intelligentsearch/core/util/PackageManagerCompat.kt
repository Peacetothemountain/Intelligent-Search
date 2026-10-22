package com.pixel.intelligentsearch.core.util

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/** [PackageManager.resolveActivity] with the flags type each Android version takes. */
fun PackageManager.resolveActivityCompat(intent: Intent, flags: Long = 0L): ResolveInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags))
    } else {
        @Suppress("DEPRECATION")
        resolveActivity(intent, flags.toInt())
    }

/** [PackageManager.queryIntentActivities] with the flags type each Android version takes. */
fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Long = 0L): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, flags.toInt())
    }

/** [PackageManager.getApplicationInfo] with the flags type each Android version takes. */
fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Long = 0L): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags))
    } else {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, flags.toInt())
    }
