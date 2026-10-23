package com.pixel.intelligentsearch.feature.launch

import com.pixel.intelligentsearch.core.system.GoogleApps
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.feature.voice.IntelligentVoiceInteractionSession
import com.pixel.intelligentsearch.feature.widget.BackgroundLaunchOptions
import com.pixel.intelligentsearch.feature.widget.WidgetActivity

/**
 * Notification Shortcut: an optional, silent, ongoing notification that opens search, like the search bar some phones
 * keep in the shade. A tap opens the overlay with the keyboard up; Voice opens it listening; Lens (only when Google
 * Lens is installed) opens Lens.
 *
 * It is a plain ongoing notification, with no foreground service behind it: nothing runs while it shows. Android 14
 * and later let the user swipe even an ongoing notification away; it is then posted again the next time the app
 * starts (opening search, a widget, the tile, or after a reboot) while the setting is on, rather than at once, which
 * would fight the user's swipe. On the lock screen it shows as private: Android hides its content there.
 */
object NotificationShortcut {

    const val CHANNEL_ID = "search_shortcut"
    const val CHANNEL_NAME = "Notification Shortcut"
    const val NOTIFICATION_ID = 0x5EA2C4
    const val TITLE = "Search Your Phone"

    private const val TAG = "SearchNotification"
    private const val REQUEST_SEARCH = 2101
    private const val REQUEST_VOICE = 2102
    private const val REQUEST_LENS = 2103

    /** The action [WidgetActivity] hands to Google Lens (see MainActivity.handleIntent). */
    internal const val ACTION_LAUNCH_LENS = "com.pixel.intelligentsearch.LAUNCH_LENS"
    private const val ACTION_SEARCH = "com.pixel.intelligentsearch.NOTIFICATION_SEARCH"
    private const val ACTION_VOICE = "com.pixel.intelligentsearch.NOTIFICATION_VOICE"


    /**
     * Whether this app may post it: on Android 13+ the notifications permission, and on every version notifications
     * and its channel not blocked by the user.
     */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        return manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Whether asking for the notifications permission is needed (and possible) before it can show. */
    fun needsPermission(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Its own quiet channel: low importance (no sound, no vibration, no pop-up), no badge, private on the lock screen. */
    fun channel(): NotificationChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
        description = "Keeps search one tap away in your notifications."
        setShowBadge(false)
        setSound(null, null)
        enableVibration(false)
        enableLights(false)
        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    }

    /** Opens the search overlay, the keyboard up, as the Quick Settings tile does. */
    fun searchIntent(context: Context): Intent = Intent(context, WidgetActivity::class.java).apply {
        action = ACTION_SEARCH
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    /** Opens the overlay listening, as the assistant gesture with Start Assistant with Voice does. */
    fun voiceIntent(context: Context): Intent = searchIntent(context).apply {
        action = ACTION_VOICE
        putExtra(IntelligentVoiceInteractionSession.EXTRA_START_VOICE, true)
    }

    fun lensIntent(context: Context): Intent = Intent(context, WidgetActivity::class.java).apply {
        action = ACTION_LAUNCH_LENS
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    /** Immutable, so nothing the notification hands to the system can be filled in or redirected by another app. */
    private fun activityIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) BackgroundLaunchOptions.forCreator(context).toBundle() else null
    )

    /** The notification: the search glyph and "Search Your Phone", a tap to search, Voice, and Lens when installed. */
    fun build(context: Context, lensAvailable: Boolean = GoogleApps.isLensAvailable(context)): Notification {
        val glyph = Icon.createWithResource(context, R.drawable.ic_search_lens_expressive)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(glyph)
            .setContentTitle(TITLE)
            .setContentIntent(activityIntent(context, REQUEST_SEARCH, searchIntent(context)))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_mic), "Voice",
                    activityIntent(context, REQUEST_VOICE, voiceIntent(context))
                ).build()
            )
        if (lensAvailable) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_camera), "Lens",
                    activityIntent(context, REQUEST_LENS, lensIntent(context))
                ).build()
            )
        }
        return builder.build().apply { flags = flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT }
    }

    /** Shows it (again) when [on] and allowed, and removes it otherwise. */
    fun sync(context: Context, on: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            if (on) {
                manager.createNotificationChannel(channel())
                if (canPost(context)) manager.notify(NOTIFICATION_ID, build(context)) else manager.cancel(NOTIFICATION_ID)
            } else {
                manager.cancel(NOTIFICATION_ID)
            }
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the post.
            Log.w(TAG, "Search notification not allowed", e)
        }
    }
}
