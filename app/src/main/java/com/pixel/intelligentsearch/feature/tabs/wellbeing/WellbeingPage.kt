package com.pixel.intelligentsearch.feature.tabs.wellbeing

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import com.pixel.intelligentsearch.core.util.resolveActivityCompat
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pixel.intelligentsearch.feature.tabs.PageChrome
import com.pixel.intelligentsearch.feature.tabs.rememberTabsSession
import com.pixel.intelligentsearch.feature.tabs.ui.Snack
import com.pixel.intelligentsearch.core.wellbeing.WellbeingCatchUp
import com.pixel.intelligentsearch.core.wellbeing.WellbeingRepository
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.History
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.WellbeingModel
import com.pixel.intelligentsearch.feature.tabs.wellbeing.ui.Load
import com.pixel.intelligentsearch.feature.tabs.wellbeing.ui.ViewState
import com.pixel.intelligentsearch.feature.tabs.wellbeing.ui.WellbeingActions
import com.pixel.intelligentsearch.feature.tabs.wellbeing.ui.WellbeingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.util.Locale

/**
 * Wellbeing's part of the preview's session (TabsSession), kept above the tabs: the last data the page read, where
 * the person was on it (Day, Week or Month, the period, the open row, the recap), and the snackbar showing. Going back
 * to Wellbeing shows it at once, as it was left, and reads the phone's records again behind it.
 */
@Stable
internal class WellbeingSession {
    var load by mutableStateOf<Load>(Load.Waiting)
    /** Where the person was on the page; after the activity is recreated, taken from what the screen saved. */
    var view = ViewState()
    /** Whether [view] has been matched with the screen's saved state since this session began. */
    var viewAdopted = false
    var snack by mutableStateOf<Snack?>(null)
}

/**
 * The Wellbeing tab: screen time, unlocks, notifications and AI apps from the phone's own usage records, worked out
 * on the phone. Reads them when the page opens and each time it comes back to the front.
 */
@Composable
fun WellbeingPage(chrome: PageChrome, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val session = rememberTabsSession().wellbeing
    // The page's place (Day, Week or Month, the period, the scroll and the rest) is saved with the screen, so it comes
    // back when Android recreates the activity; within one session the session's own copy is kept as it is.
    val saved = rememberSaveable(saver = ViewState.Saver) { session.view }
    if (!session.viewAdopted) { session.view = saved; session.viewAdopted = true }
    val view = session.view
    val repo = remember { WellbeingRepository(context) }
    val scope = rememberCoroutineScope()
    var load by session::load
    var generation by remember { mutableIntStateOf(0) }

    suspend fun refresh(): Boolean {
        if (!repo.hasAccess()) { load = Load.NoAccess; return false }
        val before = load
        // A first read takes about a second: show the catch-up card only if it isn't done in a moment.
        val slow = scope.launch { delay(250); if (load !is Load.Ready) load = Load.Loading(0, 0, true) }
        val data = repo.load { done, total -> if (load is Load.Loading) load = Load.Loading(done, total, true) }
        slow.cancel()
        // From here on the summary is kept up to date about once a day, opened or not (set up once; see WellbeingCatchUp).
        if (data != null) WellbeingCatchUp.schedule(context.applicationContext)
        load = if (data == null) Load.NoAccess else Load.Ready(
            // Today is always there to show, even before it has any record (just after midnight, or on a phone with none yet).
            WellbeingModel(History(data.days.withToday(data.today), data.today, data.nowMin, data.labels, DateFormat.is24HourFormat(context), firstDayOfWeek())),
            fresh = before !is Load.Ready
        )
        return data != null
    }

    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            refresh()
            // While the page is open: read again as each minute turns, so today's numbers keep up with the phone (this
            // page is on screen too) and a new day shows as today just after midnight. Only what is new is read.
            while (true) {
                delay(60_000L - System.currentTimeMillis() % 60_000L + 1_000L)
                refresh()
            }
        }
        // The clock, the date or the time zone changed: "today" may be another day now.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { scope.launch { refresh() } }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onPauseOrDispose {
            job.cancel()
            context.unregisterReceiver(receiver)
        }
    }
    LaunchedEffect(generation) {
        if (generation == 0) return@LaunchedEffect
        // After Clear history: everything the phone still keeps is read again, then said so.
        if (refresh()) session.snack = Snack("Wellbeing history cleared. The days your phone still keeps are back.")
    }

    val actions = remember {
        WellbeingActions(
            grantAccess = {
                context.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData("package:${context.packageName}".toUri()))
                    || context.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
            // Digital Wellbeing (timers, Bedtime, Focus), where the card's limits are: the first of the phone's that
            // opens. With none, the card isn't shown, rather than a button that does nothing.
            openDigitalWellbeing = digitalWellbeingIntent(context)?.let { intent -> { context.launch(Intent(intent)) } },
            // A timer for one app is set on the phone's own app-usage page; where there is none, in Digital Wellbeing,
            // and with neither the button isn't shown.
            openTimer = when {
                context.opens(Intent(Settings.ACTION_APP_USAGE_SETTINGS).putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)) ->
                    { pkg -> context.launch(Intent(Settings.ACTION_APP_USAGE_SETTINGS).putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)) }
                else -> digitalWellbeingIntent(context)?.let { intent -> { _: String -> context.launch(Intent(intent)) } }
            },
            openNotifications = { pkg -> context.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)) },
            clearHistory = { scope.launch { repo.clearHistory(); generation++ } }
        )
    }
    WellbeingScreen(load, chrome, actions, modifier, view = view, snack = session.snack, onSnackDone = { session.snack = null })
}

private const val DW = "com.google.android.apps.wellbeing"
private const val DW_DASHBOARD = "com.google.android.apps.wellbeing.action.APP_USAGE_DASHBOARD"
private const val DW_HOME = "com.google.android.apps.wellbeing.action.HOME"
/** Samsung's Digital Wellbeing, which Galaxy phones have in place of Google's. */
private const val SAMSUNG_DW = "com.samsung.android.forest"

/**
 * The phone's Digital Wellbeing: Google's home, else its usage list, else Samsung's or Google's app as its icon opens
 * it; null when the phone has none of them.
 */
private fun digitalWellbeingIntent(context: Context): Intent? =
    sequenceOf(
        { Intent(DW_HOME).setPackage(DW) },
        { Intent(DW_DASHBOARD).setPackage(DW) },
        { context.packageManager.getLaunchIntentForPackage(SAMSUNG_DW) },
        { context.packageManager.getLaunchIntentForPackage(DW) }
    ).mapNotNull { runCatching(it).getOrNull() }.firstOrNull(context::opens)

/** Whether something on the phone opens [intent]. */
private fun Context.opens(intent: Intent): Boolean =
    runCatching { packageManager.resolveActivityCompat(intent) != null }.getOrDefault(false)

/**
 * The phone's first day of the week, as Digital Wellbeing uses it: the first-day setting in Settings > System >
 * Languages > Regional preferences (Android 14 and later, carried in the locale as "fw"), else the region's own (Sunday
 * in the US, Monday in most of Europe).
 */
private fun firstDayOfWeek(): DayOfWeek {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    when (locale.getUnicodeLocaleType("fw")) {
        "mon" -> return DayOfWeek.MONDAY; "tue" -> return DayOfWeek.TUESDAY; "wed" -> return DayOfWeek.WEDNESDAY
        "thu" -> return DayOfWeek.THURSDAY; "fri" -> return DayOfWeek.FRIDAY; "sat" -> return DayOfWeek.SATURDAY
        "sun" -> return DayOfWeek.SUNDAY
    }
    // ICU counts Sunday as 1 to Saturday as 7; java.time counts Monday as 1 to Sunday as 7.
    val icu = android.icu.util.Calendar.getInstance(android.icu.util.ULocale.forLocale(locale)).firstDayOfWeek
    return DayOfWeek.of((icu + 5) % 7 + 1)
}

/** Starts [intent]; false when nothing on the phone opens it. */
private fun Context.launch(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

private fun Map<java.time.LocalDate, com.pixel.intelligentsearch.core.wellbeing.DaySummary>.withToday(today: java.time.LocalDate) =
    if (containsKey(today)) this else this + (today to com.pixel.intelligentsearch.core.wellbeing.DaySummary.empty(today))
