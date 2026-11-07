package com.pixel.intelligentsearch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/**
 * Installs [component] as an activity that opens [intent], declared as an app's manifest would on a phone: its action,
 * categories and data scheme or type. When [label] is given the app carries that name, so pickers list it by it.
 */
fun installActivityFor(intent: Intent, component: ComponentName, label: String? = null) {
    val packageManager = shadowOf(ApplicationProvider.getApplicationContext<Context>().packageManager)
    if (label != null) {
        packageManager.installPackage(PackageInfo().apply {
            packageName = component.packageName
            applicationInfo = ApplicationInfo().apply {
                packageName = component.packageName
                nonLocalizedLabel = label
            }
        })
    }
    packageManager.addActivityIfNotPresent(component)
    packageManager.addIntentFilterForActivity(component, IntentFilter().apply {
        intent.action?.let(::addAction)
        intent.categories?.forEach(::addCategory)
        addCategory(Intent.CATEGORY_DEFAULT)
        intent.type?.let(::addDataType)
        intent.data?.scheme?.let(::addDataScheme)
    })
}

/** The Settings app's page activity, which opens most of its pages. */
val settingsPage = ComponentName("com.android.settings", "com.android.settings.SubSettings")
