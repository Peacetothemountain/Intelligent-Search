package com.pixel.intelligentsearch.feature.launch

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import java.lang.ref.WeakReference

/**
 * Launch Portal's always-there ways to open search: the Notification Shortcut and the floating button.
 * [start] runs at every app start (StartupSync, so also after a reboot) and from the Launch Portal page; from then on
 * the surfaces follow their settings, whoever changes them (the page, a backup restore).
 */
object LaunchSurfaces {

    private val NOTIFICATION_KEYS = SettingsManager.NOTIFICATION_SHORTCUT.allNames.toSet()
    private val WINDOW_KEYS = listOf(
        SettingsManager.FLOATING_SEARCH_BUTTON, SettingsManager.FLOATING_BUTTON_EDGE, SettingsManager.FLOATING_BUTTON_POSITION
    ).flatMap { it.allNames }.toSet()

    private val main = Handler(Looper.getMainLooper())

    // Held strongly: SharedPreferences keeps its listeners weakly.
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var listenedPrefs: SharedPreferences? = null
    // Which application the listener was made for (a test, or a restarted process, brings a new one).
    private var listenedApp: WeakReference<Context>? = null

    internal fun prefs(context: Context): SharedPreferences =
        SettingsManager.storedPrefs(context)

    /** Shows what is switched on and allowed, and keeps following the settings. Safe from any thread, and to repeat. */
    fun start(context: Context) {
        val app = context.applicationContext
        onMain {
            val prefs = prefs(app)
            if (listenedPrefs !== prefs || listenedApp?.get() !== app) {
                listenedPrefs?.let { old -> listener?.let(old::unregisterOnSharedPreferenceChangeListener) }
                // Each surface follows only its own settings: moving the button mustn't bring back a notification
                // the user swiped away.
                val follow = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    when (key) {
                        null -> sync(app)
                        in NOTIFICATION_KEYS -> NotificationShortcut.sync(app, sp[SettingsManager.NOTIFICATION_SHORTCUT])
                        in WINDOW_KEYS -> FloatingSearchOverlays.sync(app)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(follow)
                listener = follow
                listenedPrefs = prefs
                listenedApp = WeakReference(app)
            }
            sync(app)
        }
    }

    /** Brings every surface in line with its setting and permission now (after a permission was granted, say). */
    fun sync(context: Context) {
        val app = context.applicationContext
        onMain {
            NotificationShortcut.sync(app, prefs(app)[SettingsManager.NOTIFICATION_SHORTCUT])
            FloatingSearchOverlays.sync(app)
        }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }
}
