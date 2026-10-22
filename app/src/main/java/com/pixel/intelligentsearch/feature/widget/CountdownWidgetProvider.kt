package com.pixel.intelligentsearch.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.util.Log
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.core.data.widget.CountdownEvents
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.di.appEntryPoint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The countdown widget: the days left until a date the user picks ("12 days to Halloween"), in the theme's colours,
 * light or dark as the app's theme is when it's drawn. Redrawn just after each local midnight by WorkManager (no
 * exact alarm), and when the clock or time zone changes. Tapping it searches for the event; a widget not set up yet
 * opens its setup ([CountdownConfigActivity]).
 */
@AndroidEntryPoint
class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Exported for the launcher: only the system's own clock broadcasts trigger anything beyond the widget ones.
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> updateAll(context, goAsync())
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        context.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
            try {
                update(context, appWidgetManager, appWidgetIds)
                scheduleMidnight(context)
            } catch (e: Throwable) {
                Log.e(TAG, "Couldn't draw the countdown widgets", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        CountdownEvents(SearchWidgetProvider.getSafeSharedPreferences(context)).remove(appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        runCatching { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(MIDNIGHT_WORK) }
    }

    companion object {
        private const val TAG = "CountdownWidget"
        private const val MIDNIGHT_WORK = "countdown_widget_midnight"
        private val AFTER_MIDNIGHT_MS = TimeUnit.SECONDS.toMillis(30)

        /** The phone's clock; tests set it. */
        internal var now: () -> ZonedDateTime = { ZonedDateTime.now() }

        /** Redraws every countdown widget, then [pendingResult] finishes. */
        fun updateAll(context: Context, pendingResult: android.content.BroadcastReceiver.PendingResult? = null) {
            val appContext = context.applicationContext
            appContext.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
                try {
                    val manager = AppWidgetManager.getInstance(appContext) ?: return@launch
                    val ids = manager.getAppWidgetIds(ComponentName(appContext, CountdownWidgetProvider::class.java))
                    if (ids.isNotEmpty()) {
                        update(appContext, manager, ids)
                        scheduleMidnight(appContext)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Couldn't redraw the countdown widgets", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        private fun update(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
            val prefs = SearchWidgetProvider.getSafeSharedPreferences(context)
            val events = CountdownEvents(prefs)
            for (id in appWidgetIds) {
                manager.updateAppWidget(id, buildViews(context, id, events.get(id), prefs))
            }
        }

        /** Arranges the redraw just after the next local midnight, replacing any arranged before. */
        fun scheduleMidnight(context: Context) {
            val current = now()
            val delayMs = Duration.between(current, CountdownEvents.nextMidnight(current)).toMillis() + AFTER_MIDNIGHT_MS
            val request = OneTimeWorkRequestBuilder<CountdownMidnightWorker>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build()
            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(MIDNIGHT_WORK, ExistingWorkPolicy.REPLACE, request)
            }.onFailure { Log.w(TAG, "Couldn't schedule the midnight redraw", it) }
        }

        /** What the widget shows for [event] on [today]: the big line and the caption under it. */
        internal fun texts(context: Context, event: CountdownEvent?, today: LocalDate): Pair<String, String> {
            if (event == null) return context.getString(R.string.widget_countdown_label) to context.getString(R.string.widget_countdown_set_up)
            val days = event.daysUntil(today)
            val res = context.resources
            return when {
                days == 0L -> context.getString(R.string.widget_countdown_today) to context.getString(R.string.widget_countdown_today_caption, event.title)
                days > 0 -> days.toString() to res.getQuantityString(R.plurals.widget_countdown_days_to, days.toQuantity(), event.title)
                else -> (-days).toString() to res.getQuantityString(R.plurals.widget_countdown_days_since, (-days).toQuantity(), event.title)
            }
        }

        private fun Long.toQuantity(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        /** The countdown widget [appWidgetId] for [event] (null: not set up yet), drawn for the app's light or dark theme. */
        internal fun buildViews(context: Context, appWidgetId: Int, event: CountdownEvent?, prefs: SharedPreferences): RemoteViews {
            val isDark = ThemeMode.from(prefs[SettingsManager.THEME]).isDark(ThemeMode.systemIsDark(context.resources.configuration))
            val colors = DynamicThemeDetector.resolveWidgetColors(context, isDark)
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)
            views.setColorStateList(R.id.countdown_background, "setImageTintList", ColorStateList.valueOf(colors.surfaceContainer))
            val (number, caption) = texts(context, event, now().toLocalDate())
            views.setTextViewText(R.id.countdown_number, number)
            views.setTextViewText(R.id.countdown_caption, caption)
            views.setTextColor(R.id.countdown_number, colors.primary)
            views.setTextColor(R.id.countdown_caption, colors.onSurface)

            val tap = if (event == null) {
                Intent(context, CountdownConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                SearchWidgetProvider.mainSearchIntent(context, prefs, appWidgetId, query = event.title)
            }
            tap.identifier = "countdown_$appWidgetId"
            views.setOnClickPendingIntent(
                android.R.id.background,
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    tap,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    SearchWidgetProvider.getPendingIntentActivityOptions(context)
                )
            )
            views.setContentDescription(
                android.R.id.background,
                if (event == null) "$number, $caption" else context.getString(R.string.widget_talkback_countdown, number, caption, event.title)
            )
            return views
        }
    }
}

/** Redraws the countdown widgets just after local midnight, then arranges the next night's. */
class CountdownMidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        CountdownWidgetProvider.updateAll(applicationContext)
        return Result.success()
    }
}
