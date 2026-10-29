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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app's one notification listener, so it appears once in Android's Notification Access list and one grant covers
 * everything that needs it ([NotificationAccess]):
 * - Notification search and At a Glance's timers: reads the phone's notifications into the in-memory
 *   [NotificationIndex] while the user grants notification access.
 * - At a Glance's Media Controls: Android shares media sessions only with a granted listener. At a Glance asks for
 *   them itself while the search is open, which needs only the grant; it reads no notifications.
 *
 * Google Play policy: notification listener access is allowed for an app's core feature with a prominent
 * disclosure. Here each use is the user's own opt-in, granted by the user on Android's Notification Access screen
 * after the app explains it (the shared rationale dialog), and used only for those features. Nothing read is stored
 * (memory only, cleared when access goes), backed up or sent anywhere, and the app sees only what each notification
 * itself shows, never the messages or emails behind it. The user turns it off where they turned it on, on Android's
 * Notification Access screen; Android then disconnects the listener and everything read is forgotten.
 */
@AndroidEntryPoint
class NotificationAccessListener : NotificationListenerService() {

    @Inject lateinit var index: NotificationIndex

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private lateinit var indexer: NotificationIndexer

    override fun onCreate() {
        super.onCreate()
        indexer = NotificationIndexer(
            index = index,
            // The grant is the opt-in: connected but no longer granted (revoked mid-connect), read nothing.
            isOptedIn = { NotificationAccess.isGranted(applicationContext) },
            extract = NotificationExtractor(applicationContext, ::lockscreenVisibilityOverride)::extract,
            unbind = { runCatching { requestUnbind() } }
        )
    }

    override fun onListenerConnected() {
        scope.launch {
            indexer.onConnected(runCatching { activeNotifications?.toList() }.getOrNull().orEmpty())
        }
    }

    override fun onListenerDisconnected() {
        indexer.onDisconnected()
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
        indexer.onDisconnected()
        scope.cancel()
        super.onDestroy()
    }

    /** The lock screen visibility the notification's channel (or the user) sets for notification [key], if any. */
    private fun lockscreenVisibilityOverride(key: String): Int = runCatching {
        val ranking = Ranking()
        if (currentRanking?.getRanking(key, ranking) == true) ranking.lockscreenVisibilityOverride else null
    }.getOrNull() ?: NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE

    companion object {
        fun component(context: Context) = ComponentName(context, NotificationAccessListener::class.java)
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

/**
 * Turns a notification into what the index keeps, or null for one it leaves out. [lockscreenVisibilityOverride] is
 * the lock screen visibility a notification's channel or the user sets for it, by key
 * ([NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE] for none).
 */
internal class NotificationExtractor(
    private val context: Context,
    private val lockscreenVisibilityOverride: (String) -> Int = { NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE }
) {

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
            clockPackages = clockPackages,
            lockscreenVisibilityOverride = lockscreenVisibilityOverride(sbn.key)
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
         * The index entry for [notification], or null for what search leaves out: secret notifications (one-time
         * codes, banking: their app or channel keeps them off the lock screen altogether), group summaries (their
         * children are indexed), media players (Media Controls show those), one-time sign-in codes whatever their
         * visibility ([looksLikeOneTimeCode]) and notifications with no words or timer. Other private notifications
         * (every notification's default) are indexed: that only hides their content on a lock screen set to hide it.
         */
        fun fromNotification(
            key: String,
            packageName: String,
            appLabel: String,
            postedAt: Long,
            notification: Notification,
            clockPackages: Set<String>,
            lockscreenVisibilityOverride: Int = NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE
        ): IndexedNotification? {
            if (notification.visibility == Notification.VISIBILITY_SECRET) return null
            if (lockscreenVisibilityOverride == Notification.VISIBILITY_SECRET) return null
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
            // Codes are often posted only as private, which hides them on the lock screen but not from search.
            if (looksLikeOneTimeCode("$title\n$text")) return null
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

        /** A code: 4 to 8 digits, 3-3 or 4-4 grouped, or Google's "G-123456"; not part of a longer number. */
        private val CODE = Regex("(?<![\\p{N}-])(?:G-\\d{5,8}|\\d{3}[ -]\\d{3}|\\d{4}[ -]\\d{4}|\\d{4,8})(?![\\p{N}])")
        private val GOOGLE_CODE = Regex("(?<![\\p{L}\\p{N}])G-\\d{5,8}(?![\\p{N}])")
        private val CODE_WORD = Regex(
            "\\b(?:codes?|otp|passcode|verification|verify|one[- ]?time|2fa|two[- ]factor|pin)\\b",
            RegexOption.IGNORE_CASE
        )
        /** How far apart a code and its word may be and still be one sign-in code. */
        private const val CODE_WORD_REACH = 40

        /**
         * Whether [text] holds a one-time sign-in code: a code ("482913", "482 913", "G-482913") within
         * [CODE_WORD_REACH] characters of a word saying so (code, OTP, passcode, verification, one-time, 2FA, PIN).
         * Kept narrow so "Your order 123456 has shipped" is still found.
         */
        internal fun looksLikeOneTimeCode(text: String): Boolean {
            if (text.isEmpty()) return false
            if (GOOGLE_CODE.containsMatchIn(text)) return true
            val words = CODE_WORD.findAll(text).map { it.range }.toList()
            if (words.isEmpty()) return false
            return CODE.findAll(text).any { code ->
                words.any { word ->
                    val gap = if (word.first > code.range.last) word.first - code.range.last else code.range.first - word.last
                    gap <= CODE_WORD_REACH
                }
            }
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
