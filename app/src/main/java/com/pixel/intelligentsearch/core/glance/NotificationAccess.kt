package com.pixel.intelligentsearch.core.glance

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.util.storageContext

/** Notification access for Search Your Notifications: whether it is granted, where to grant it, and switching on and off. */
object NotificationAccess {

    /** Whether the user has granted this app notification access on Android's Notification Access screen. */
    fun isGranted(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(NotificationSearchListener.component(context)) == true
    }.getOrDefault(false)

    /** Whether notifications are read now: the user's Search Your Notifications switch and Android's grant. */
    fun isActive(context: Context): Boolean =
        context.storageContext().getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)[SettingsManager.SEARCH_NOTIFICATIONS] &&
            isGranted(context)

    /** Android's page for granting this app's listener, or the list of all listeners where the page is missing. */
    fun settingsIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotificationSearchListener.component(context).flattenToString()),
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** Opens Android's notification access page; false if no page could open. */
    fun openSettings(context: Context): Boolean = settingsIntents(context).any { intent ->
        runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Switched on with access granted: asks Android to connect the listener (it unbinds itself while off). */
    fun start(context: Context) {
        if (isGranted(context)) runCatching { NotificationListenerService.requestRebind(NotificationSearchListener.component(context)) }
    }

    /** Switched off: forgets every notification read and disconnects the listener. */
    fun stop(index: NotificationIndex) {
        index.clear()
        NotificationSearchListener.current?.get()?.let { runCatching { it.requestUnbind() } }
    }

    /**
     * Brings the index in line with the switch and Android's grant, as the search opens: whatever was read is
     * forgotten the moment access is revoked or the switch is off, even if the listener never heard of it.
     */
    fun syncIndex(context: Context, index: NotificationIndex) {
        if (!isActive(context)) index.clear()
    }
}
