package com.pixel.intelligentsearch.feature.search.today

import android.app.NotificationManager
import android.content.BroadcastReceiver
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The battery now: its level, whether it is charging, and how long until full when Android can tell. */
@Immutable
data class TodayBattery(val level: Int, val charging: Boolean, val minutesToFull: Long? = null, val saver: Boolean = false)

/** When to leave for an event: at [leaveAt] (wall clock), for a trip of about [minutes]. */
@Immutable
data class TodayTrip(val leaveAt: Long, val minutes: Int)

/** A photo from the past for On This Day: what to call it ("3 years ago"), a line under that, the photo to open and its thumbnail. */
@Immutable
data class TodayMemory(val title: String, val detail: String, val uri: android.net.Uri, val thumbnail: androidx.compose.ui.graphics.ImageBitmap?)

/** A photo in the library: its id and when it was taken (or added, when the camera didn't say). */
internal data class LibraryPhoto(val id: Long, val takenAt: java.time.LocalDate)

/**
 * The photos On This Day shows on [today], best first: those taken on this date in an earlier year (the latest such
 * year), else in the same week in an earlier year, else in the same month, else one photo from the library chosen by
 * the date, so there is a picture every day. Returns the photos and what to call them.
 */
internal fun pickMemory(photos: List<LibraryPhoto>, today: LocalDate): Triple<List<LibraryPhoto>, String, String>? {
    fun yearsAgo(years: Int) = if (years == 1) "1 year ago" else "$years years ago"
    fun count(list: List<LibraryPhoto>) = if (list.size == 1) "1 photo" else "${list.size} photos"
    // Each match says how many years back a photo is (null when it doesn't match); the fewest years win.
    fun latest(yearsBack: (LibraryPhoto) -> Int?): Pair<List<LibraryPhoto>, Int>? {
        val hits = photos.mapNotNull { photo -> yearsBack(photo)?.takeIf { it >= 1 }?.let { photo to it } }
        val years = hits.minOfOrNull { it.second } ?: return null
        return hits.filter { it.second == years }.map { it.first } to years
    }
    latest { if (it.takenAt.monthValue == today.monthValue && it.takenAt.dayOfMonth == today.dayOfMonth) today.year - it.takenAt.year else null }?.let { (list, years) ->
        return Triple(list, yearsAgo(years), count(list))
    }
    // The week around today a whole number of years back, so Dec 30 is in the week of Jan 2 a year later.
    latest { photo ->
        val years = Math.round(ChronoUnit.DAYS.between(photo.takenAt, today) / 365.2425).toInt()
        years.takeIf { kotlin.math.abs(ChronoUnit.DAYS.between(photo.takenAt.plusYears(it.toLong()), today)) <= 3 }
    }?.let { (list, years) -> return Triple(list, "This week, " + yearsAgo(years), count(list)) }
    latest { if (it.takenAt.monthValue == today.monthValue) today.year - it.takenAt.year else null }?.let { (list, years) ->
        return Triple(list, "This month, " + yearsAgo(years), count(list))
    }
    val older = photos.filter { java.time.temporal.ChronoUnit.DAYS.between(it.takenAt, today) >= 30 }.ifEmpty { photos }
    if (older.isEmpty()) return null
    val pick = older[Math.floorMod(today.toEpochDay(), older.size.toLong()).toInt()]
    val month = pick.takenAt.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    return Triple(listOf(pick), "From your photos", "$month ${pick.takenAt.year}")
}

/** The next big holiday, [days] from today. */
@Immutable
data class TodayCountdown(val name: String, val days: Int, val date: LocalDate)

/**
 * Where Today reads what At a Glance doesn't: the weather, the battery, screen time, the notifications to catch up
 * on, and the phone's quick controls. The app reads the phone ([AndroidTodaySources]); screenshot tests hand in fixed
 * data.
 */
interface TodaySources {
    /** Today's and tomorrow's weather where the user set it (or roughly where the phone is, if allowed); null with Web Suggestions off. */
    suspend fun weather(): Pair<WeatherReport, WeatherReport?>?
    fun battery(): TodayBattery?
    /** Today's screen time, or null without Usage Access. */
    fun screenTime(): ScreenTimeReport?
    /** What the notification index holds now; empty without notification access. */
    fun notifications(): Flow<List<IndexedNotification>>
    fun countdown(today: LocalDate): TodayCountdown?
    val controls: TodayControls
    /** When to leave for [event], from the phone's approximate location to its place; null when either is unknown. */
    suspend fun trip(event: com.pixel.intelligentsearch.core.data.CalendarEvent): TodayTrip? = null
    /** The latest year's photos taken on [today]'s date, when photos may be read and there are any. */
    suspend fun memory(today: LocalDate): TodayMemory? = null
}

/**
 * About how long a drive of [straightKm] as the crow flies takes, in whole minutes: roads run about 1.3 times the
 * straight line, at city speed for short trips and highway speed for long ones, plus five minutes to park.
 */
internal fun driveMinutes(straightKm: Double): Int {
    val roadKm = straightKm * 1.3
    val kmPerHour = when {
        roadKm < 3 -> 25.0
        roadKm < 25 -> 40.0
        else -> 70.0
    }
    return (kmPerHour.let { roadKm / it * 60 }).let { kotlin.math.ceil(it).toInt() } + 5
}

/** The distance between two points on the earth, in kilometers. */
internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) * kotlin.math.sin(dLon / 2).let { it * it }
    return 6371.0 * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

/** Do Not Disturb, the flashlight, the hotspot and Battery Saver; each state is read now and again whenever it changes. */
interface TodayControls {
    fun doNotDisturb(): Flow<Boolean>
    /** Switches Do Not Disturb, when the app may; false when it has to send the user to Android's page instead. */
    fun setDoNotDisturb(on: Boolean): Boolean
    fun flashlight(): Flow<Boolean>
    fun setFlashlight(on: Boolean)
    fun batterySaver(): Flow<Boolean>
}

internal val LocalTodaySources = staticCompositionLocalOf<TodaySources?> { null }

/** The holidays worth counting down to; the doodles' smaller days aren't. */
private val COUNTDOWN_OCCASIONS = setOf(
    Occasion.NEW_YEARS_DAY, Occasion.VALENTINES_DAY, Occasion.EASTER, Occasion.MOTHERS_DAY, Occasion.MEMORIAL_DAY,
    Occasion.FATHERS_DAY, Occasion.INDEPENDENCE_DAY, Occasion.LABOR_DAY, Occasion.HALLOWEEN, Occasion.THANKSGIVING,
    Occasion.HOLIDAYS, Occasion.NEW_YEARS_EVE, Occasion.CANADA_DAY, Occasion.DIWALI, Occasion.LUNAR_NEW_YEAR
)

/**
 * The next of [COUNTDOWN_OCCASIONS] after [today], in [zone] and [region], named by [name]. The holidays doodle runs
 * Christmas Eve and Christmas Day; the countdown is to Christmas Day and says so.
 */
internal fun nextCountdown(today: LocalDate, zone: ZoneId, region: String, name: (Occasion) -> String): TodayCountdown? {
    var date = today.plusDays(1)
    repeat(400) {
        val occasion = OccasionCalendar.on(date, zone, region)
        val christmas = occasion == Occasion.HOLIDAYS
        if (occasion != null && occasion in COUNTDOWN_OCCASIONS && (!christmas || date.dayOfMonth == 25)) {
            return TodayCountdown(if (christmas) "Christmas" else name(occasion), ChronoUnit.DAYS.between(today, date).toInt(), date)
        }
        date = date.plusDays(1)
    }
    return null
}

/** Today's sources on the phone. */
class AndroidTodaySources(private val context: Context) : TodaySources {
    private val app = context.applicationContext

    override suspend fun weather(): Pair<WeatherReport, WeatherReport?>? =
        runCatching { app.appEntryPoint.liveAnswerEngine().weatherHere() }.getOrNull()

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

    private val places = java.util.concurrent.ConcurrentHashMap<String, Pair<Double, Double>>()

    override suspend fun trip(event: com.pixel.intelligentsearch.core.data.CalendarEvent): TodayTrip? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (event.location.isBlank()) return@withContext null
        val here = com.pixel.intelligentsearch.core.answers.AndroidDeviceLocation(app)
        if (!here.permitted()) return@withContext null
        val from = here.approximate() ?: return@withContext null
        val to = places[event.location] ?: geocode(event.location)?.also { places[event.location] = it } ?: return@withContext null
        val minutes = driveMinutes(distanceKm(from.first, from.second, to.first, to.second))
        TodayTrip(event.beginMillis - minutes * 60_000L, minutes)
    }

    /** Where [address] is, from Android's own geocoder; null when it has none or finds nothing. */
    private suspend fun geocode(address: String): Pair<Double, Double>? {
        if (!android.location.Geocoder.isPresent()) return null
        val geocoder = android.location.Geocoder(app)
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine { done ->
                        geocoder.getFromLocationName(address, 1, object : android.location.Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                done.resumeWith(Result.success(addresses.firstOrNull()?.let { it.latitude to it.longitude }))
                            }
                            override fun onError(errorMessage: String?) {
                                done.resumeWith(Result.success(null))
                            }
                        })
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(address, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
            }
        }.getOrNull()
    }

    override suspend fun memory(today: LocalDate): TodayMemory? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!com.pixel.intelligentsearch.feature.settings.photosAllowed(app)) return@withContext null
        val zone = ZoneId.systemDefault()
        val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val photos = ArrayList<LibraryPhoto>()
        runCatching {
            app.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DATE_TAKEN,
                    android.provider.MediaStore.Images.Media.DATE_ADDED
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val taken = cursor.getLong(1).takeIf { it > 0 } ?: (cursor.getLong(2) * 1000L)
                    if (taken <= 0) continue
                    photos += LibraryPhoto(cursor.getLong(0), java.time.Instant.ofEpochMilli(taken).atZone(zone).toLocalDate())
                }
            }
        }
        val (picked, title, detail) = pickMemory(photos, today) ?: return@withContext null
        val first = android.content.ContentUris.withAppendedId(uri, picked.first().id)
        val thumbnail = runCatching {
            app.contentResolver.loadThumbnail(first, android.util.Size(360, 360), null).asImageBitmap()
        }.getOrNull()
        TodayMemory(title, detail, first, thumbnail)
    }

    override fun countdown(today: LocalDate): TodayCountdown? =
        nextCountdown(today, ZoneId.systemDefault(), Locale.getDefault().country.uppercase(Locale.ROOT)) { app.getString(it.title) }

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

        override fun doNotDisturb(): Flow<Boolean> = broadcastState(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
            notifications?.currentInterruptionFilter?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN } == true
        }

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

        override fun batterySaver(): Flow<Boolean> = broadcastState(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
            app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        }
    }

    /** [read] now, and again each time Android broadcasts [action], for as long as it is collected. */
    private fun broadcastState(action: String, read: () -> Boolean): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read())
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(read())
        awaitClose { app.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
