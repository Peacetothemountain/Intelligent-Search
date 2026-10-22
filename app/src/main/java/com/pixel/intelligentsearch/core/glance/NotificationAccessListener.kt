package com.pixel.intelligentsearch.core.glance

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.AlarmClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.system.WidgetMediaWatcher
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * The app's one notification listener, so it appears once in Android's Notification Access list and one grant covers
 * everything that needs it ([NotificationAccess]):
 * - Search Your Notifications and At a Glance's timers: reads the phone's notifications into the in-memory
 *   [NotificationIndex], only while that switch is on.
 * - At a Glance's Now Playing and the widget's Media Controls: Android shares media sessions only with a granted
 *   listener. Now Playing asks for them itself while the search is open; while a widget design shows its media row,
 *   this listener also watches them ([WidgetMediaWatcher]) so the widget keeps up. Neither reads notifications.
 *
 * Google Play policy: notification listener access is allowed for an app's core feature with a prominent
 * disclosure. Here each use is the user's own opt-in, granted by the user on Android's Notification Access screen
 * after the app explains it (the shared rationale dialog), and used only for those features. Nothing read is stored
 * (memory only, cleared when access or the switch goes), backed up or sent anywhere, and the app sees only what each
 * notification itself shows, never the messages or emails behind it. While nothing needs it ([staysBound]) the
 * listener unbinds itself at once, so it never reads anything the user hasn't asked it to.
 */
@AndroidEntryPoint
class NotificationAccessListener : NotificationListenerService() {

    @Inject lateinit var index: NotificationIndex
    @Inject lateinit var settingsManager: SettingsManager
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private lateinit var indexer: NotificationIndexer
    private lateinit var mediaWatcher: WidgetMediaWatcher
    /** Whether Android has the listener connected; touched on the main thread. */
    private var connected = false

    override fun onCreate() {
        super.onCreate()
        mediaWatcher = WidgetMediaWatcher(applicationContext, component(this), widgetUpdater, appScope)
        indexer = NotificationIndexer(
            index = index,
            isOptedIn = { settingsManager[SettingsManager.SEARCH_NOTIFICATIONS] },
            extract = NotificationExtractor(applicationContext)::extract,
            // Search is off; a widget's media row may still need the listener.
            unbind = { unbindUnlessNeeded() }
        )
        // The switch turned off while connected: forget everything and stop listening straight away.
        scope.launch {
            settingsManager.observe(SettingsManager.SEARCH_NOTIFICATIONS).drop(1).collect { on ->
                if (!on) indexer.onOptInChanged(false)
            }
        }
        // A widget's media row switched on or off while connected: start or stop watching what's playing.
        scope.launch {
            combine(WidgetDesign.entries.map { settingsManager.observe(it.showMediaKey) }) { shows -> shows.any { it } }
                .distinctUntilChanged()
                .drop(1)
                .collect { wanted ->
                    if (!wanted) unbindUnlessNeeded()
                    withContext(Dispatchers.Main) {
                        when {
                            !connected -> Unit
                            wanted -> mediaWatcher.start()
                            else -> mediaWatcher.stop()
                        }
                    }
                }
        }
    }

    override fun onListenerConnected() {
        connected = true
        current = WeakReference(this)
        scope.launch {
            // Read off the main thread; then the watcher starts there, where its callbacks arrive.
            if (WidgetMediaWatcher.anyWidgetShowsMedia(this@NotificationAccessListener)) {
                withContext(Dispatchers.Main) { if (connected) mediaWatcher.start() }
            }
            indexer.onConnected(runCatching { activeNotifications?.toList() }.getOrNull().orEmpty())
        }
    }

    override fun onListenerDisconnected() {
        connected = false
        if (current?.get() === this) current = null
        mediaWatcher.stop()
        indexer.onDisconnected()
    }

    /** Unbinds unless Search Your Notifications or a widget's media row still needs the listener. */
    internal fun unbindUnlessNeeded() {
        val search = settingsManager[SettingsManager.SEARCH_NOTIFICATIONS]
        if (!staysBound(search, WidgetMediaWatcher.anyWidgetShowsMedia(this))) runCatching { requestUnbind() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        scope.launch { indexer.onPosted(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        scope.launch { indexer.onRemoved(sbn.key) }
    }

    override fun onDestroy() {
        connected = false
        mediaWatcher.stop()
        indexer.onDisconnected()
        scope.cancel()
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        /** The connected listener, so turning a switch off can unbind it at once. */
        @Volatile internal var current: WeakReference<NotificationAccessListener>? = null

        fun component(context: Context) = ComponentName(context, NotificationAccessListener::class.java)

        /**
         * Whether the listener stays connected: while Search Your Notifications is on ([searchNotifications]) it reads
         * notifications, and while a widget design shows its media row ([widgetMedia]) it watches media sessions. Now
         * Playing needs only the grant, not the connection.
         */
        fun staysBound(searchNotifications: Boolean, widgetMedia: Boolean): Boolean = searchNotifications || widgetMedia
    }
}

/**
 * The listener's decisions, apart from Android so they can be tested: index only while the user has opted in, and
 * forget everything when the listener disconnects (access revoked) or the opt-in goes.
 */
internal class NotificationIndexer(
    private val index: NotificationIndex,
    private val isOptedIn: () -> Boolean,
    private val extract: (StatusBarNotification) -> IndexedNotification?,
    private val unbind: () -> Unit
) {
    /** False once disconnected, so a notification still queued from before can't land in the cleared index. */
    @Volatile private var connected = false

    fun onConnected(active: List<StatusBarNotification>) {
        if (!isOptedIn()) {
            connected = false
            index.clear()
            unbind()
            return
        }
        connected = true
        index.replaceAll(active.mapNotNull(extract))
    }

    fun onPosted(sbn: StatusBarNotification) {
        if (!connected || !isOptedIn()) return
        val entry = extract(sbn)
        if (entry != null) index.post(entry) else index.remove(sbn.key)
    }

    fun onRemoved(key: String) = index.remove(key)

    fun onDisconnected() {
        connected = false
        index.clear()
    }

    fun onOptInChanged(on: Boolean) {
        if (on) return
        onDisconnected()
        unbind()
    }
}

/** Turns a notification into what the index keeps, or null for one it leaves out. */
internal class NotificationExtractor(private val context: Context) {

    private val labels = HashMap<String, String>()

    /** Apps that run timers and stopwatches: whatever opens timers, plus the clocks phones ship with. */
    private val clockPackages: Set<String> by lazy {
        val resolved = runCatching {
            context.packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_TIMERS), PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
        }.getOrNull().orEmpty()
        resolved.toSet() + KNOWN_CLOCKS
    }

    fun extract(sbn: StatusBarNotification): IndexedNotification? {
        if (sbn.packageName == context.packageName) return null
        return fromNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = label(sbn.packageName),
            postedAt = sbn.postTime,
            notification = sbn.notification ?: return null,
            clockPackages = clockPackages
        )
    }

    private fun label(packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    companion object {
        private val KNOWN_CLOCKS = setOf(
            "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
            "com.oneplus.deskclock", "com.motorola.timeweatherwidget"
        )
        /** Long enough for any message preview; a huge notification can't swell the index. */
        private const val MAX_TEXT = 1_000
        private const val MESSAGES_KEPT = 3

        /**
         * The index entry for [notification], or null for what search leaves out: group summaries (their children
         * are indexed), media players (Now Playing shows those) and notifications with no words or timer.
         */
        fun fromNotification(
            key: String,
            packageName: String,
            appLabel: String,
            postedAt: Long,
            notification: Notification,
            clockPackages: Set<String>
        ): IndexedNotification? {
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
            val extras = notification.extras ?: Bundle.EMPTY
            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
            val messages = messagesOf(extras)
            val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim().orEmpty()
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
            val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString().orEmpty()
            val text = when {
                messages.isNotEmpty() -> messages.joinToString("\n")
                lines.isNotBlank() -> lines
                else -> body
            }.trim().take(MAX_TEXT)
            val chronometer = chronometerOf(notification, packageName, clockPackages)
            if (title.isEmpty() && text.isEmpty() && chronometer == null) return null
            return IndexedNotification(
                key = key,
                packageName = packageName,
                appLabel = appLabel,
                title = title.take(MAX_TEXT),
                text = text,
                postedAt = postedAt,
                category = notification.category,
                contentIntent = notification.contentIntent,
                chronometer = chronometer
            )
        }

        /** The last few messages of a conversation, "Sender: text" where the sender is named. */
        private fun messagesOf(extras: Bundle): List<String> {
            @Suppress("DEPRECATION")
            val parcels = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
            return parcels.mapNotNull { (it as? Bundle)?.getCharSequence("text")?.toString() }.takeLast(MESSAGES_KEPT)
        }

        /** A clock app's running timer (counting down) or stopwatch (counting up), read from its chronometer. */
        fun chronometerOf(notification: Notification, packageName: String, clockPackages: Set<String>): IndexedNotification.Chronometer? {
            val extras = notification.extras ?: return null
            if (!extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) return null
            val fromClock = packageName in clockPackages || notification.category == Notification.CATEGORY_STOPWATCH
            if (!fromClock) return null
            return IndexedNotification.Chronometer(
                countDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
                baseTime = notification.`when`
            )
        }
    }
}
