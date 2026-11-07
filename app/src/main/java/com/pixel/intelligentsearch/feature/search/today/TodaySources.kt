package com.pixel.intelligentsearch.feature.search.today

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.pixel.intelligentsearch.core.answers.ForecastDay
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.answers.LiveAnswerQuery
import com.pixel.intelligentsearch.core.answers.ScreenTimeReport
import com.pixel.intelligentsearch.core.answers.ScreenTimeRepository
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.core.occasion.Occasion
import com.pixel.intelligentsearch.core.occasion.OccasionCalendar
import com.pixel.intelligentsearch.di.appEntryPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The battery now: its level, whether it is charging, and how long until full when Android can tell. */
@Immutable
data class TodayBattery(val level: Int, val charging: Boolean, val minutesToFull: Long? = null, val saver: Boolean = false)

/** The next big holiday, [days] from today. */
@Immutable
data class TodayCountdown(val name: String, val days: Int, val date: LocalDate)

/**
 * Where Today reads what At a Glance doesn't: the weather, the battery, screen time, the notifications to catch up
 * on, and the phone's quick controls. The app reads the phone ([AndroidTodaySources]); screenshot tests hand in fixed
 * data.
 */
interface TodaySources {
    /** The weather for [day] where the user set it (or roughly where the phone is, if allowed); empty with Web Suggestions off. */
    fun weather(day: ForecastDay): Flow<WeatherReport>
    fun battery(): TodayBattery?
    /** Today's screen time, or null without Usage Access. */
    fun screenTime(): ScreenTimeReport?
    /** What the notification index holds now; empty without notification access. */
    fun notifications(): Flow<List<IndexedNotification>>
    fun countdown(today: LocalDate): TodayCountdown?
    val controls: TodayControls
}

/** Do Not Disturb, the flashlight, the hotspot and Battery Saver. */
interface TodayControls {
    fun doNotDisturb(): Boolean
    /** Switches Do Not Disturb, when the app may; false when it has to send the user to Android's page instead. */
    fun setDoNotDisturb(on: Boolean): Boolean
    fun flashlight(): Flow<Boolean>
    fun setFlashlight(on: Boolean)
    fun batterySaver(): Boolean
}

internal val LocalTodaySources = staticCompositionLocalOf<TodaySources?> { null }

/** The holidays worth counting down to; the doodles' smaller days aren't. */
private val COUNTDOWN_OCCASIONS = setOf(
    Occasion.NEW_YEARS_DAY, Occasion.VALENTINES_DAY, Occasion.EASTER, Occasion.MOTHERS_DAY, Occasion.MEMORIAL_DAY,
    Occasion.FATHERS_DAY, Occasion.INDEPENDENCE_DAY, Occasion.LABOR_DAY, Occasion.HALLOWEEN, Occasion.THANKSGIVING,
    Occasion.HOLIDAYS, Occasion.NEW_YEARS_EVE, Occasion.CANADA_DAY, Occasion.DIWALI, Occasion.LUNAR_NEW_YEAR
)

/** The next of [COUNTDOWN_OCCASIONS] after [today], in [zone] and [region], named by [name]. */
internal fun nextCountdown(today: LocalDate, zone: ZoneId, region: String, name: (Occasion) -> String): TodayCountdown? {
    var date = today.plusDays(1)
    repeat(400) {
        val occasion = OccasionCalendar.on(date, zone, region)
        if (occasion != null && occasion in COUNTDOWN_OCCASIONS) {
            return TodayCountdown(name(occasion), ChronoUnit.DAYS.between(today, date).toInt(), date)
        }
        date = date.plusDays(1)
    }
    return null
}

/** Today's sources on the phone. */
class AndroidTodaySources(private val context: Context) : TodaySources {
    private val app = context.applicationContext

    override fun weather(day: ForecastDay): Flow<WeatherReport> = runCatching {
        app.appEntryPoint.liveAnswerEngine()
            .answer(LiveAnswerQuery.Weather(place = null, day = day), "weather")
            .filterIsInstance<LiveAnswer.Weather>()
            .map { it.report }
    }.getOrDefault(emptyFlow())

    override fun battery(): TodayBattery? {
        val status = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return null
        val state = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL
        val toFull = if (charging) {
            app.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining()?.takeIf { it > 0 }?.let { it / 60_000L }
        } else null
        val saver = app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        return TodayBattery(level * 100 / scale, charging, toFull, saver)
    }

    override fun screenTime(): ScreenTimeReport? = runCatching { ScreenTimeRepository(app).today() }.getOrNull()

    override fun notifications(): Flow<List<IndexedNotification>> = app.appEntryPoint.notificationIndex().entries

    override fun countdown(today: LocalDate): TodayCountdown? =
        nextCountdown(today, ZoneId.systemDefault(), Locale.getDefault().country.uppercase(Locale.ROOT)) { occasion ->
            if (occasion == Occasion.HOLIDAYS) "Christmas" else app.getString(occasion.title)
        }

    override val controls: TodayControls = object : TodayControls {
        private val notifications = app.getSystemService(NotificationManager::class.java)
        private val cameras = app.getSystemService(CameraManager::class.java)
        private val flashCamera: String? by lazy {
            runCatching {
                cameras?.cameraIdList?.firstOrNull { id ->
                    cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }.getOrNull()
        }

        override fun doNotDisturb(): Boolean =
            notifications?.currentInterruptionFilter?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN } == true

        override fun setDoNotDisturb(on: Boolean): Boolean {
            val manager = notifications ?: return false
            if (!manager.isNotificationPolicyAccessGranted) return false
            manager.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
            return true
        }

        override fun flashlight(): Flow<Boolean> = callbackFlow {
            val manager = cameras
            val id = flashCamera
            if (manager == null || id == null) {
                trySend(false)
                awaitClose { }
                return@callbackFlow
            }
            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == id) trySend(enabled)
                }
            }
            manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            awaitClose { manager.unregisterTorchCallback(callback) }
        }

        override fun setFlashlight(on: Boolean) {
            val id = flashCamera ?: return
            runCatching { cameras?.setTorchMode(id, on) }
        }

        override fun batterySaver(): Boolean = app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    }
}
