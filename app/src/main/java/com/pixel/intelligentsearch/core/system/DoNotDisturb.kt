package com.pixel.intelligentsearch.core.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Do Not Disturb, read and switched the one way every part of the app does it. From Android 15 an app can't change the
 * phone's own Do Not Disturb: switching it on starts a mode of the app's own, and switching it off ends only that
 * mode, so Do Not Disturb the user (or another mode) started stays on and only Android's page can end it.
 */
object DoNotDisturb {

    fun isOn(manager: NotificationManager?): Boolean = runCatching { manager?.currentInterruptionFilter }.getOrNull()
        ?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN } == true

    /**
     * Switches Do Not Disturb [on] or off. Null once it is as asked; otherwise the Android page where the user can do
     * it: Do Not Disturb access while the app hasn't been allowed it, else Do Not Disturb itself.
     */
    fun switch(context: Context, on: Boolean): Intent? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
        if (!manager.isNotificationPolicyAccessGranted) return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        runCatching {
            manager.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        return if (isOn(manager) == on) null else Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
    }
}
