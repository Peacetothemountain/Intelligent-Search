package com.pixel.intelligentsearch.core.wellbeing

import android.content.Context
import com.pixel.intelligentsearch.AppJob
import java.io.File

/**
 * Brings Wellbeing's daily summary up to date about once a day, whether or not the page is opened. The phone lets its
 * own records go after about 10 days, so without this a stretch of 9 days or more away from the page left days that
 * could never be filled in. It does what opening the page does ([WellbeingRepository.load]): reads what is new, on the
 * phone, and saves the days that have ended. Nothing leaves the phone, and nothing is shown.
 *
 * Run by [AppJob.WELLBEING_CATCH_UP]. It is set up the first time the page reads the records with Usage Access on,
 * arranged again at each start while there is a summary to keep (a force stop cancels it), and it removes itself
 * when Usage Access has been turned off.
 */
object WellbeingCatchUp {
    private const val DAY_MS = 24 * 3_600_000L
    /** The phone may run it at any point in the last part of each day-long period, when it suits it. */
    private const val FLEX_MS = 8 * 3_600_000L

    suspend fun run(context: Context) {
        val repo = WellbeingRepository(context)
        // A read that fails costs nothing: the phone keeps the records for days, and tomorrow's run reads them.
        val read = runCatching { repo.load() }.getOrNull()
        if (read == null && !repo.hasAccess()) cancel(context)
    }

    /** Sets the daily read up, once: a run already waiting is left as it is. */
    fun schedule(context: Context) {
        AppJob.WELLBEING_CATCH_UP.schedule(context, keepExisting = true) {
            setPeriodic(DAY_MS, FLEX_MS)
            setRequiresBatteryNotLow(true)
        }
    }

    /** At each start: arranged again while there is a summary to keep (a force stop cancels every job). */
    fun syncSchedule(context: Context) {
        if (File(context.filesDir, HistoryStore.FILE_NAME).isFile) schedule(context)
    }

    fun cancel(context: Context) = AppJob.WELLBEING_CATCH_UP.cancel(context)

}
