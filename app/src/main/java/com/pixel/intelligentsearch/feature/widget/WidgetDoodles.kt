package com.pixel.intelligentsearch.feature.widget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pixel.intelligentsearch.core.occasion.Occasion
import com.pixel.intelligentsearch.core.occasion.OccasionCalendar
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The doodle the widgets draw around their G (see [Occasion]) and keeping it current: the widgets are redrawn just
 * after the midnight a doodle day starts or ends. Everything is decided on the phone; nothing is fetched.
 */
object WidgetDoodles {

    /** A doodle Developer Settings is previewing on the widgets, whatever the date. Never backed up (debug_ key). */
    const val PREVIEW_KEY = "debug_doodle_preview"

    private const val TAG = "WidgetDoodles"
    private const val CHANGE_WORK = "widget_doodle_change"
    private val AFTER_MIDNIGHT_MS = TimeUnit.SECONDS.toMillis(30)

    /** The doodle to draw now: the one being previewed, else today's; null on an ordinary day. */
    fun current(prefs: SharedPreferences): Occasion? = previewing(prefs) ?: OccasionCalendar.today()

    fun previewing(prefs: SharedPreferences): Occasion? =
        prefs.getString(PREVIEW_KEY, null)?.let { name -> Occasion.entries.firstOrNull { it.name == name } }

    /** Shows [doodle] on the widgets whatever the date, or with null ends the preview so they show today's again. */
    fun preview(
        context: Context,
        doodle: Occasion?,
        prefs: SharedPreferences = com.pixel.intelligentsearch.core.data.SettingsManager.storedPrefs(context),
        redraw: () -> Unit = { SearchWidgetProvider.updateAllWidgets(context) }
    ) {
        prefs.edit { if (doodle == null) remove(PREVIEW_KEY) else putString(PREVIEW_KEY, doodle.name) }
        redraw()
    }

    /** Arranges the redraw for the next time the doodle changes, replacing any arranged before. */
    fun scheduleNextChange(context: Context) {
        val now = OccasionCalendar.now()
        val delayMs = Duration.between(now, OccasionCalendar.nextChange(now, OccasionCalendar.region())).toMillis() + AFTER_MIDNIGHT_MS
        val request = OneTimeWorkRequestBuilder<DoodleChangeWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        // Replacing it from inside the run that is redrawing ends that run; its work is already done.
        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(CHANGE_WORK, ExistingWorkPolicy.REPLACE, request)
        }.onFailure { Log.w(TAG, "Couldn't schedule the next doodle change", it) }
    }
}

/** Redraws the widgets at a doodle day's start or end, then arranges the next one. */
class DoodleChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SearchWidgetProvider.updateAllWidgets(applicationContext)
        WidgetDoodles.scheduleNextChange(applicationContext)
        return Result.success()
    }
}
