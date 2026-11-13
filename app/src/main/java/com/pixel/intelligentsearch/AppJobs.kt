package com.pixel.intelligentsearch

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.PersistableBundle
import android.util.Log
import com.pixel.intelligentsearch.core.backup.AutoBackup
import com.pixel.intelligentsearch.core.currency.CurrencyRateWork
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider
import com.pixel.intelligentsearch.feature.widget.WidgetDoodles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's background jobs, each with a fixed id, run by Android's JobScheduler through [AppJobService]. The service
 * isn't direct-boot aware, so a job never runs before the user's first unlock after a restart, while scheduling one
 * then is fine. A force stop cancels them all; [StartupSync] arranges them again at the next start.
 */
enum class AppJob(val id: Int) {
    AUTO_BACKUP(1001) {
        override suspend fun run(context: Context, extras: PersistableBundle) = AutoBackup.runScheduled(context)
    },
    CURRENCY_DAILY(1002) {
        override suspend fun run(context: Context, extras: PersistableBundle) = CurrencyRateWork.run(context, attempt = 0)
    },
    CURRENCY_FIRST_FETCH(1003) {
        override suspend fun run(context: Context, extras: PersistableBundle) = CurrencyRateWork.run(context, attempt = 0)
    },
    CURRENCY_RETRY(1004) {
        override suspend fun run(context: Context, extras: PersistableBundle) =
            CurrencyRateWork.run(context, extras.getInt(CurrencyRateWork.ATTEMPT, 1))
    },
    COUNTDOWN_MIDNIGHT(1005) {
        override suspend fun run(context: Context, extras: PersistableBundle) {
            AppWidgetManager.getInstance(context)?.let { CountdownWidgetProvider.redrawAll(context, it) }
        }
    },
    DOODLE_CHANGE(1006) {
        override suspend fun run(context: Context, extras: PersistableBundle) = WidgetDoodles.changeNow(context)
    },
    PALETTE_CHANGE(1007) {
        override suspend fun run(context: Context, extras: PersistableBundle) = WidgetPaletteWatch.changed(context)
    };

    abstract suspend fun run(context: Context, extras: PersistableBundle)

    /** Whether this job is waiting to run. */
    fun isPending(context: Context): Boolean = AppJobs.ours(context, context.jobScheduler()?.getPendingJob(id))

    /**
     * Schedules this job, replacing one already waiting (a running one is stopped), or with [keepExisting] leaving a
     * waiting one as it is. A job that can't be scheduled is logged.
     */
    fun schedule(context: Context, keepExisting: Boolean = false, setUp: JobInfo.Builder.() -> Unit) {
        runCatching {
            val scheduler = context.jobScheduler() ?: return
            if (keepExisting && isPending(context)) return
            scheduler.schedule(JobInfo.Builder(id, ComponentName(context, AppJobService::class.java)).apply(setUp).build())
        }.onFailure { Log.w(AppJobs.TAG, "Couldn't schedule $name", it) }
    }

    fun cancel(context: Context) {
        runCatching { context.jobScheduler()?.cancel(id) }.onFailure { Log.w(AppJobs.TAG, "Couldn't cancel $name", it) }
    }

    companion object {
        fun byId(id: Int): AppJob? = entries.firstOrNull { it.id == id }
    }
}

internal object AppJobs {
    const val TAG = "AppJobs"

    fun ours(context: Context, job: JobInfo?): Boolean =
        job?.service == ComponentName(context, AppJobService::class.java)

    /**
     * Removes what WorkManager, which versions before 2026-11-07 ran these jobs with, left behind: its jobs, which
     * name a service the app no longer has, and its database.
     */
    fun removeWorkManagerLeftovers(context: Context) {
        runCatching {
            val scheduler = context.jobScheduler() ?: return@runCatching
            for (job in scheduler.allPendingJobs) if (!ours(context, job)) scheduler.cancel(job.id)
        }.onFailure { Log.w(TAG, "Couldn't cancel WorkManager's jobs", it) }
        for (database in listOf(File(context.noBackupFilesDir, WORK_DATABASE), context.getDatabasePath(WORK_DATABASE))) {
            if (database.exists()) runCatching { SQLiteDatabase.deleteDatabase(database) }
        }
    }

    private const val WORK_DATABASE = "androidx.work.workdb"
}

private fun Context.jobScheduler(): JobScheduler? = getSystemService(JobScheduler::class.java)

/** Runs [AppJob]s, each on its own coroutine; one Android stops is cancelled and left for Android to run again. */
class AppJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val job = AppJob.byId(params.jobId) ?: return false
        val work = scope.launch(start = CoroutineStart.LAZY) {
            try {
                job.run(applicationContext, params.extras)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(AppJobs.TAG, "${job.name} failed", e)
            }
            if (running.remove(params.jobId, coroutineContext[Job])) jobFinished(params, false)
        }
        running[params.jobId] = work
        work.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
