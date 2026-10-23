package com.pixel.intelligentsearch.core.glance

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get

/**
 * Notification Access, the one grant behind Search Your Notifications, At a Glance's Now Playing and the widget's
 * Media Controls: the app's single listener ([component]), whether it is granted, where to grant it, and connecting
 * and disconnecting it. Every feature asks [isGranted]; notifications themselves are read only while Search Your
 * Notifications is also on ([isActive]), while media sessions need only the grant.
 */
object NotificationAccess {

    /** The app's one notification listener, which Android's Notification Access screen lists and grants. */
    fun component(context: Context): ComponentName = NotificationAccessListener.component(context)

    /** Whether the user has granted this app notification access on Android's Notification Access screen. */
    fun isGranted(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(component(context)) == true
    }.getOrDefault(false)

    /** Whether notifications are read now: the user's Search Your Notifications switch and Android's grant. */
    fun isActive(context: Context): Boolean =
        SettingsManager.storedPrefs(context)[SettingsManager.SEARCH_NOTIFICATIONS] &&
            isGranted(context)

    /** Android's page for granting this app's listener, or the list of all listeners where the page is missing. */
    fun settingsIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString()),
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /**
     * A feature that needs the listener connected (Search Your Notifications, a widget's media row) was switched on:
     * with access granted, asks Android to connect it. The listener unbinds itself while nothing needs it.
     */
    fun start(context: Context) {
        if (isGranted(context)) runCatching { NotificationListenerService.requestRebind(component(context)) }
    }

    /** [start], unless the listener is already connected in this process (the widget asks on every redraw). */
    fun ensureConnected(context: Context) {
        if (NotificationAccessListener.current?.get() == null) start(context)
    }

    /**
     * Search Your Notifications switched off: forgets every notification read at once, and disconnects the listener
     * unless a widget's media row still needs it.
     */
    fun stop(index: NotificationIndex) {
        index.clear()
        NotificationAccessListener.current?.get()?.unbindUnlessNeeded()
    }

    /**
     * Brings the index in line with the switch and Android's grant, as the search opens: whatever was read is
     * forgotten the moment access is revoked or the switch is off, even if the listener never heard of it.
     */
    fun syncIndex(context: Context, index: NotificationIndex) {
        if (!isActive(context)) index.clear()
    }
}
