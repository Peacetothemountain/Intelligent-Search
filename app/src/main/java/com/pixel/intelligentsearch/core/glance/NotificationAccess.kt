package com.pixel.intelligentsearch.core.glance

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Notification Access, the one grant behind notification search, At a Glance's timers and its Media Controls: the
 * app's single listener ([component]), whether it is granted, where to grant it, and connecting it. There is no switch
 * of the app's own: the grant on Android's Notification Access screen turns it all on, and taking it back there turns
 * it all off.
 */
object NotificationAccess {

    /** The app's one notification listener, which Android's Notification Access screen lists and grants. */
    fun component(context: Context): ComponentName = NotificationAccessListener.component(context)

    /** Whether the user has granted this app notification access on Android's Notification Access screen. */
    fun isGranted(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(component(context)) == true
    }.getOrDefault(false)

    /** Android's page for granting this app's listener, or the list of all listeners where the page is missing. */
    fun settingsIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString()),
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** Access was just granted (or something needs the listener): asks Android to connect it, if granted. */
    fun start(context: Context) {
        if (isGranted(context)) runCatching { NotificationListenerService.requestRebind(component(context)) }
    }

    /**
     * Brings the index in line with Android's grant, as the search opens: whatever was read is forgotten the moment
     * access is revoked, even if the listener never heard of it.
     */
    fun syncIndex(context: Context, index: NotificationIndex) {
        if (!isGranted(context)) index.clear()
    }
}
