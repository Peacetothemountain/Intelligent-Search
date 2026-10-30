package com.pixel.intelligentsearch.core.glance

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
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
            lockscreenVisibilityOverride = lockscreenVisibilityOverride(sbn.key),
            bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
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
         * [bootTime] is the wall-clock time the phone started, to place a timer Android 17 counts in time since boot.
         */
        fun fromNotification(
            key: String,
            packageName: String,
            appLabel: String,
            postedAt: Long,
            notification: Notification,
            clockPackages: Set<String>,
            lockscreenVisibilityOverride: Int = NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE,
            bootTime: Long = 0L
        ): IndexedNotification? {
            if (notification.visibility == Notification.VISIBILITY_SECRET) return null
            if (lockscreenVisibilityOverride == Notification.VISIBILITY_SECRET) return null
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
            val extras = notification.extras ?: Bundle.EMPTY
            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
            val messages = messagesOf(extras)
            val chronometer = chronometerOf(notification, packageName, clockPackages, bootTime)
            // Android 17's Clock posts no title: the timer's name ("10m Timer") is the label of its time.
            val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)
                ?: chronometer?.let { timerMetricOf(extras)?.getCharSequence(METRIC_LABEL) })?.toString()?.trim().orEmpty()
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

        /**
         * A code's digits, in any script ("482913", Arabic "٤٨٢٩١٣", Hindi "४८२९१३"): 4 to 8 of them, grouped 3-3,
         * 4-4, 2-2-2 or one by one ("1 2 3 4 5 6"), or Google's "G-123456".
         */
        private const val DIGITS = "(?:G-\\p{Nd}{5,8}|\\p{Nd}{3}[ -]\\p{Nd}{3}|\\p{Nd}{4}[ -]\\p{Nd}{4}|" +
            "\\p{Nd}{2} \\p{Nd}{2} \\p{Nd}{2}|\\p{Nd}(?: \\p{Nd}){3,7}|\\p{Nd}{4,8})"
        /**
         * A code not part of a longer number; a word may run into it ("OTP-482913"), a number can't ("555-1234"), nor a
         * decimal point (the "37.4219,-122.0840" of a dropped pin).
         */
        private val CODE = Regex("(?<!\\p{N})(?<!\\p{N}-)(?<!\\p{N}[.,])$DIGITS(?!\\p{N})(?![.,]\\p{N})")
        /**
         * Where a number is a street address's ("2045 Oak Ave", "1600 Amphitheatre Pkwy"), a room, a gate or a time
         * ("at 1400"), not a code: the words around it say so.
         */
        private val ADDRESS_AFTER = Regex(
            "^\\s+(?:\\p{Lu}\\p{L}*\\.?\\s+){1,3}(?:St|Street|Ave|Avenue|Rd|Road|Blvd|Boulevard|Dr|Drive|Ln|Lane|Way|" +
                "Pkwy|Parkway|Ct|Court|Pl|Place|Hwy|Highway|Sq|Square|Ter|Terrace|Cres|Crescent)(?!\\p{L})"
        )
        private val PLACE_BEFORE = Regex(
            "(?<!\\p{L})(?:room|rm|suite|ste|gate|unit|apt|flat|floor|platform|flight|at)\\.?\\s*#?\\s*$",
            RegexOption.IGNORE_CASE
        )
        /**
         * Words of a shop's offer ("Use code FALL2026 at checkout for 20% off"): there a plain "code" is a promo code.
         * A word that names a sign-in code outright (OTP, verification) still counts.
         */
        private val SHOPPING_WORD = Regex(
            "(?<!\\p{L})(?:checkout|check out|discount|promo|coupon|voucher|sale|free shipping|\\d+\\s?% off|off your|" +
                "off orders?|save \\d+)(?!\\p{L})",
            RegexOption.IGNORE_CASE
        )
        private val PLAIN_CODE_WORD = Regex("codes?", RegexOption.IGNORE_CASE)
        /** "Dropped pin": a place on a map, not a PIN. */
        private val DROPPED_PIN = Regex("(?<!\\p{L})dropped\\s+pin(?!\\p{L})", RegexOption.IGNORE_CASE)
        private val GOOGLE_CODE = Regex("(?<![\\p{L}\\p{N}])G-\\p{Nd}{5,8}(?![\\p{N}])")
        /** "Enter 482913 to sign in": a code that names no code word but says what it's for. */
        private val SIGN_IN_PROMPT = Regex(
            "(?<!\\p{L})(?:enter|use|type)\\s+$DIGITS(?!\\p{N})\\s+to\\s+(?:sign|log)[- ]?in(?!\\p{L})",
            RegexOption.IGNORE_CASE
        )
        /** A code of capital letters and digits ("K7P-2Q9", "4X7QF"); one only right after its word ([LABEL_REACH]). */
        private val LETTERS_CODE = Regex(
            "(?<![\\p{L}\\p{N}-])(?=[A-Z\\d-]*\\d)(?=[A-Z\\d-]*[A-Z])(?:[A-Z\\d]{4,8}|[A-Z\\d]{3,4}-[A-Z\\d]{3,4})(?![\\p{L}\\p{N}-])"
        )
        /**
         * A word saying a number is a code, in the languages most phones use. A word may end in code ("Codes",
         * German "Bestätigungscode", Dutch "verificatiecode", Turkish "kodu", Spanish "código"); Chinese, Japanese and
         * Korean words have no spaces around them, so they're found anywhere.
         */
        private val CODE_WORD = Regex(
            "(?<![\\p{L}\\p{M}\\p{N}])(?:" +
                "otp|passcode|verification|verify|one[- ]?time|2fa|two[- ]factor|pin|clave|token|authentication|" +
                "temporary password|einmalpasswort|m[ãa] x[áa]c (?:th[ựu]c|nh[ậa]n|minh)|(?:ה)?קוד|" +
                "mot de passe [àa] usage unique|do[ğg]rulama|(?:ال)?رمز(?:ك|كم)?|كود|कोड|ओटीपी|код(?:а|у|ом|ы)?|" +
                "\\p{L}*(?:codes?|kod(?:u|y|e|em|un|um|unuz)?|codic[ei]|c[oó]digos?)" +
                ")(?![\\p{L}\\p{M}\\p{N}])" +
                "|验证码|驗證碼|校验码|校驗碼|动态码|動態碼|認証コード|確認コード|認証番号|確認番号|인증\\s?(?:번호|코드)",
            RegexOption.IGNORE_CASE
        )
        /**
         * Codes that aren't a sign-in code, joined or not: "barcode", "zip code", "dress code", "source code", "error
         * code", "tax code", and India's postal "pincode".
         */
        private val NOT_SIGN_IN_CODE = Regex(
            "(?<![\\p{L}\\p{N}])(?:(?:bar|post(?:al)?|zip|promo|qr|area|dress|country|source|discount|coupon|voucher|gift|pin|" +
                "error|status|course|tax|product|tracking)[ -]?|uni|en|de)codes?(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )
        /**
         * "PIN code" is a card's or a parcel locker's PIN too, so it's India's postal code only as one: six digits
         * ([POSTAL_PIN]) beside a word about a delivery or address, and nothing asking to share it or open a locker.
         */
        private val PIN_CODE = Regex("(?<![\\p{L}\\p{N}])pin[ -]codes?(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        private val DELIVERY_WORD = Regex("(?<!\\p{L})(?:deliver\\p{L}*|address\\p{L}*|courier|shipping|parcel|postal)(?!\\p{L})", RegexOption.IGNORE_CASE)
        private val POSTAL_PIN = Regex("(?<!\\p{N})[1-9]\\p{Nd}{2} ?\\p{Nd}{3}(?!\\p{N})")
        private val ACCESS_WORD = Regex("(?<!\\p{L})(?:share|locker|agent|pick ?up|collect\\p{L}*|unlock|open)(?!\\p{L})", RegexOption.IGNORE_CASE)
        /** How far apart a code and its word may be and still be one sign-in code. */
        private const val CODE_WORD_REACH = 40
        /** How far after its word a letters-and-digits code may start: "code: K7P-2Q9", "code is A1B2C3". */
        private const val LABEL_REACH = 6

        /**
         * Whether [text] holds a one-time sign-in code: a code ("482913", "482 913", "G-482913") within
         * [CODE_WORD_REACH] characters of a word saying so ([CODE_WORD]: code, OTP, verification, código, код, 验证码...),
         * a letters-and-digits code right after one ("code: K7P-2Q9"), or "Enter 482913 to sign in". Kept narrow so
         * "Your order 123456 has shipped", "Your zip code 94103" and a delivery's "pincode 560001" are still found, as
         * are a shop's "Use code FALL2026 at checkout", a "Dropped pin" and an address ("verify 1600 Amphitheatre Pkwy").
         */
        internal fun looksLikeOneTimeCode(text: String): Boolean {
            if (text.isEmpty()) return false
            if (GOOGLE_CODE.containsMatchIn(text) || SIGN_IN_PROMPT.containsMatchIn(text)) return true
            val postal = DELIVERY_WORD.containsMatchIn(text) && POSTAL_PIN.containsMatchIn(text) && !ACCESS_WORD.containsMatchIn(text)
            // Beside a shop's offer a plain "code" is a promo code ("FALL2026"); a code of digits alone is still one.
            val shopping = SHOPPING_WORD.containsMatchIn(text)
            val notSignIn = NOT_SIGN_IN_CODE.findAll(text).map { it.range }
                .filter { range -> postal || !PIN_CODE.matches(text.substring(range)) }.toList()
            val droppedPins = DROPPED_PIN.findAll(text).map { it.range }.toList()
            val words = CODE_WORD.findAll(text).filterNot { word ->
                notSignIn.any { word.range.first in it } || droppedPins.any { word.range.first in it }
            }.toList()
            if (words.isEmpty()) return false
            fun isPromoWord(word: MatchResult) = shopping && PLAIN_CODE_WORD.matches(word.value)
            val digits = CODE.findAll(text).filterNot { code ->
                ADDRESS_AFTER.containsMatchIn(text.substring(code.range.last + 1)) ||
                    PLACE_BEFORE.containsMatchIn(text.substring(0, code.range.first))
            }.any { code ->
                val runIntoWord = code.range.first > 0 && text[code.range.first - 1].isLetter()
                words.any { match ->
                    val word = match.range
                    val gap = if (word.first > code.range.last) word.first - code.range.last else code.range.first - word.last
                    gap <= CODE_WORD_REACH && !(runIntoWord && isPromoWord(match))
                }
            }
            return digits || LETTERS_CODE.findAll(text).any { code ->
                words.any { match ->
                    val word = match.range
                    !isPromoWord(match) && word.last < code.range.first && code.range.first - word.last <= LABEL_REACH
                }
            }
        }

        /** The last few messages of a conversation, "Sender: text" where the sender is named. */
        private fun messagesOf(extras: Bundle): List<String> {
            @Suppress("DEPRECATION")
            val parcels = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
            return parcels.mapNotNull { (it as? Bundle)?.getCharSequence("text")?.toString() }.takeLast(MESSAGES_KEPT)
        }

        /**
         * A clock app's running timer (counting down) or stopwatch (counting up). Read from its chronometer, or, on
         * Android 17, from its MetricStyle time ([timerMetricOf]), which may count in time since boot: [bootTime] is
         * the wall-clock time the phone started. A paused timer has no running time and is left out, as before.
         */
        fun chronometerOf(
            notification: Notification,
            packageName: String,
            clockPackages: Set<String>,
            bootTime: Long = 0L
        ): IndexedNotification.Chronometer? {
            val extras = notification.extras ?: return null
            val fromClock = packageName in clockPackages || notification.category == Notification.CATEGORY_STOPWATCH
            if (!fromClock) return null
            if (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) {
                return IndexedNotification.Chronometer(
                    countDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
                    baseTime = notification.`when`
                )
            }
            val time = timerMetricOf(extras)?.getBundle(METRIC_VALUE) ?: return null
            val baseTime = if (time.containsKey(TIME_ZERO)) time.getLong(TIME_ZERO) else bootTime + time.getLong(TIME_ZERO_ELAPSED)
            return IndexedNotification.Chronometer(countDown = time.getBoolean(TIME_COUNT_DOWN), baseTime = baseTime)
        }

        /**
         * The running time among a MetricStyle notification's metrics, the one it marks most important first.
         * Android 17's Clock posts its timer this way with no chronometer, as
         * `android.metrics=[Bundle{label=10m Timer, value=Bundle{zeroElapsedRealtime=6895293, countDown=true, ...}}]`;
         * paused, the value holds `pausedDuration` instead.
         */
        private fun timerMetricOf(extras: Bundle): Bundle? {
            @Suppress("DEPRECATION")
            val metrics = runCatching { extras.getParcelableArrayList<Bundle>(EXTRA_METRICS) }.getOrNull() ?: return null
            val critical = metrics.getOrNull(extras.getInt(EXTRA_CRITICAL_METRIC, -1))
            return (listOfNotNull(critical) + metrics).firstOrNull { metric ->
                val time = runCatching { metric.getBundle(METRIC_VALUE) }.getOrNull() ?: return@firstOrNull false
                time.containsKey(TIME_COUNT_DOWN) && (time.containsKey(TIME_ZERO) || time.containsKey(TIME_ZERO_ELAPSED))
            }
        }

        // Android 17's MetricStyle extras (Notification.Metric and Metric.TimeDifference), read as plain bundles.
        private const val EXTRA_METRICS = "android.metrics"
        private const val EXTRA_CRITICAL_METRIC = "android.metrics.criticalIndex"
        private const val METRIC_LABEL = "label"
        private const val METRIC_VALUE = "value"
        /** When the time reaches zero (or a stopwatch started): wall-clock or since-boot milliseconds. */
        private const val TIME_ZERO = "zeroTime"
        private const val TIME_ZERO_ELAPSED = "zeroElapsedRealtime"
        private const val TIME_COUNT_DOWN = "countDown"
    }
}
