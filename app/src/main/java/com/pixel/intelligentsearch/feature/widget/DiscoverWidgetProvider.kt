package com.pixel.intelligentsearch.feature.widget

import android.Manifest
import android.app.job.JobInfo
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.UserManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import com.pixel.intelligentsearch.AppJob
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.WidgetPaletteWatch
import com.pixel.intelligentsearch.core.answers.AndroidDeviceLocation
import com.pixel.intelligentsearch.core.answers.HomeLocationCodec
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.security.NetworkPolicy
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.feature.search.today.AndroidTodaySources
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.Companion.WidgetDesignConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The Discover widget: the next calendar event and the weather on the home screen, drawn in the search bar's own
 * design as the countdown widget is (the one saved in Widget Customization: its colors, Transparency, outline and
 * corners, light or dark as the app's theme is when it's drawn). Tapping it opens the Discover page.
 *
 * It shows only what is already on for Discover and already allowed: the event with Up Next on and calendar access,
 * the weather with Weather on and a place to ask for. It never asks for anything; with neither, it says where to turn
 * one on, and a tap opens those settings. Placed on the home screen only, as it shows the calendar.
 *
 * It is responsive ([DiscoverSize]): one row high a pill like the bar, two rows by two a tile, and wider the event
 * with the weather underneath. It is a still picture, drawn again about once an hour, sooner as the next event nears
 * ([DiscoverWidgetModel.nextRedrawDelayMs]), when the calendar changes, when the search opens, and with the search
 * bars whenever the design or the theme changes. A redraw never waits for the forecast: it shows the last reading,
 * and a newer one is drawn when it arrives.
 */
class DiscoverWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Exported for the launcher: only the system's own broadcasts trigger anything beyond the widget ones.
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> updateAll(context, goAsync())
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context, goAsync())
    }

    override fun onEnabled(context: Context) {
        placed = true
        // Placed while the app was already running: watch the palette from now, not from the next start.
        WidgetPaletteWatch.sync(context, keepExisting = true)
    }

    override fun onDisabled(context: Context) {
        placed = false
        trip = null
        AppJob.DISCOVER_REFRESH.cancel(context)
        AppJob.DISCOVER_CALENDAR.cancel(context)
        // The last reading was only for the widget; with none left, nothing is kept.
        runCatching { store(context).edit { clear() } }
        WidgetPaletteWatch.sync(context, keepExisting = true)
    }

    /**
     * The layouts the widget has, each for the sizes from [size] up ([RemoteViews] shows the one whose size is the
     * nearest below the widget's): one row high a pill, two rows a tile, and wide the event over the weather.
     */
    internal enum class DiscoverSize(val size: SizeF, @param:LayoutRes val layout: Int) {
        PILL(SizeF(100f, 40f), R.layout.widget_discover_pill),
        TILE(SizeF(100f, 100f), R.layout.widget_discover_tile),
        WIDE(SizeF(240f, 150f), R.layout.widget_discover_wide)
    }

    /**
     * The widget's colors, outline and corners: the countdown widget's ([CountdownWidgetProvider.CountdownStyle]), the
     * search bar's own. The temperature is the bar's accent, as the countdown's number is; [soft] is that accent
     * faint, behind the weather strip and how soon the event starts; the dot is [sun] or [rain].
     */
    internal data class DiscoverStyle(
        val base: CountdownWidgetProvider.CountdownStyle,
        val soft: Int,
        val sun: Int,
        val rain: Int
    )

    /** How the widget is drawn again: from what it has, or reading the forecast too. */
    internal enum class Refresh {
        /** From the calendar and the last reading of the weather: nothing that can wait on the network. */
        QUICK,
        /** Reads the forecast again when the last reading is no longer fresh. */
        WEATHER,
        /** The app is on screen, so the phone's location can be read: the forecast, and when to leave. */
        FOREGROUND
    }

    /** Where the widget reads what it shows; tests hand in fixed data. */
    internal interface Sources {
        /** The calendar from now to the end of tomorrow. */
        suspend fun events(context: Context): List<CalendarEvent>
        /** Today's weather where the user set it, or roughly where the phone is; may ask the forecast service. */
        suspend fun weather(context: Context): WeatherReport?
        /** When to leave for [event], when the phone knows where it is and where the event is. */
        suspend fun leaveAt(context: Context, event: CalendarEvent): Long?
    }

    /** When to leave for one event, as worked out at [at]. Kept in memory only: it follows from where the phone was. */
    private data class Trip(val eventId: Long, val beginMillis: Long, val place: String, val leaveAt: Long?, val at: Long)

    companion object {
        private const val TAG = "DiscoverWidget"

        /** How far the words sit inside the pill, the tile and the wide size's surface, in dp. */
        private const val PILL_INSET = 20
        private const val TILE_INSET = 16
        private const val WIDE_INSET = 18

        /** The space between the parts of the line under the event, in dp. */
        private const val META_GAP = 12f

        /** How faint the accent is behind the weather strip and how soon the event starts. */
        private const val SOFT_ALPHA = 0x26

        /** The weather shown may be this old; older, the widget shows none rather than a stale temperature. */
        private val WEATHER_SHOWN_MS = TimeUnit.HOURS.toMillis(3)
        /** A reading younger than this isn't asked for again. */
        private val WEATHER_FRESH_MS = TimeUnit.MINUTES.toMillis(45)
        /** When to leave is shown this long after it was worked out, and worked out again after half of it. */
        private val TRIP_SHOWN_MS = TimeUnit.MINUTES.toMillis(60)
        /** The longest a redraw waits for the forecast or for when to leave. */
        private const val READ_TIMEOUT_MS = 8_000L
        /** The search's own entrance plays first; the widget is brought up to date after it. */
        private const val AFTER_OPENING_MS = 1_500L

        /** The widget's own store: the last reading of the weather, which is not a setting and never leaves the phone. */
        private const val STORE = "discover_widget"
        private const val KEY_WEATHER = "weather"

        /** The phone's clock; tests set it. */
        internal var now: () -> ZonedDateTime = { ZonedDateTime.now() }

        internal val PHONE: Sources = object : Sources {
            override suspend fun events(context: Context) = SystemDataProvider.getUpcomingEvents(context)
            override suspend fun weather(context: Context) = context.appEntryPoint.liveAnswerEngine().weatherHere()?.first
            override suspend fun leaveAt(context: Context, event: CalendarEvent) = AndroidTodaySources(context).trip(event)?.leaveAt
        }

        /** Where the widget reads from; tests set it. */
        internal var sources: Sources = PHONE

        /** Whether a Discover widget is placed, once known, so the search opening costs nothing without one. */
        @Volatile private var placed: Boolean? = null
        @Volatile private var trip: Trip? = null

        private fun store(context: Context): SharedPreferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

        private fun widgetIds(context: Context, manager: AppWidgetManager): IntArray =
            manager.getAppWidgetIds(ComponentName(context, DiscoverWidgetProvider::class.java))

        /** Redraws every Discover widget on a background thread, then [pendingResult] finishes. */
        fun updateAll(context: Context, pendingResult: BroadcastReceiver.PendingResult? = null) {
            val appContext = context.applicationContext
            appContext.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
                try {
                    AppWidgetManager.getInstance(appContext)?.let { redrawAll(appContext, it, Refresh.QUICK) }
                } catch (e: Throwable) {
                    Log.e(TAG, "Couldn't redraw the Discover widgets", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        /**
         * The search came on screen: brings the widgets up to date, with the forecast and when to leave read where
         * the phone is now, which Android allows only while the app is in view. Nothing happens without a widget.
         */
        fun refreshInForeground(context: Context) {
            if (placed == false) return
            val appContext = context.applicationContext
            appContext.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
                try {
                    delay(AFTER_OPENING_MS)
                    AppWidgetManager.getInstance(appContext)?.let { redrawAll(appContext, it, Refresh.FOREGROUND) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "Couldn't bring the Discover widgets up to date", e)
                }
            }
        }

        /**
         * Redraws every Discover widget and arranges the next redraw. Drawn first from the calendar and the last
         * reading of the weather; with [refresh] asking for more, the forecast is read after and the widgets drawn
         * again only if it changed something. Without a widget placed it does nothing, and before the phone's first
         * unlock too: the calendar can't be read yet.
         */
        internal suspend fun redrawAll(context: Context, manager: AppWidgetManager, refresh: Refresh) {
            val ids = widgetIds(context, manager)
            placed = ids.isNotEmpty()
            if (ids.isEmpty()) return
            if (context.getSystemService(UserManager::class.java)?.isUserUnlocked == false) return
            val prefs = SettingsManager.storedPrefs(context)
            var input = read(context, prefs)
            redraw(context, manager, ids, input, prefs)
            if (refresh != Refresh.QUICK) {
                val newer = withTimeoutOrNull(READ_TIMEOUT_MS) { refreshed(context, prefs, input, refresh == Refresh.FOREGROUND) }
                if (newer != null && newer != input) {
                    input = newer
                    redraw(context, manager, ids, input, prefs)
                }
            }
            // A redraw that read nothing new leaves the forecast to the job, which may wait for it; a job's own run
            // goes back to the usual pace, so a forecast that can't be reached is never asked for in a loop.
            val soon = refresh == Refresh.QUICK && weatherOn(prefs) && !isFresh(input.weather)
            arrange(context, prefs, input, soon)
        }

        private fun redraw(context: Context, manager: AppWidgetManager, ids: IntArray, input: DiscoverInput, prefs: SharedPreferences) {
            for (id in ids) {
                try {
                    manager.updateAppWidget(id, buildViews(context, id, input, prefs))
                } catch (e: Throwable) {
                    Log.e(TAG, "Couldn't draw Discover widget $id", e)
                }
            }
        }

        /** Whether Up Next is on and the calendar may be read. */
        private fun eventsOn(context: Context, prefs: SharedPreferences): Boolean = prefs[SettingsManager.TODAY_UP_NEXT] &&
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

        /** Whether Weather is on and forecasts may be asked for at all (Web Suggestions on, Tor not the engine). */
        private fun weatherOn(prefs: SharedPreferences): Boolean =
            prefs[SettingsManager.TODAY_WEATHER] && NetworkPolicy.webAnswersAllowed(prefs)

        private fun nowMs(): Long = now().toInstant().toEpochMilli()

        private fun isFresh(weather: DiscoverWeather?): Boolean = weather != null && nowMs() - weather.at in 0..WEATHER_FRESH_MS

        /**
         * The last reading of the weather, while it may still be shown: not too old, and in the scale Settings asks
         * for now (no weather beats old numbers under a new scale).
         */
        internal fun savedWeather(context: Context, prefs: SharedPreferences): DiscoverWeather? {
            val saved = DiscoverWeather.decode(runCatching { store(context).getString(KEY_WEATHER, null) }.getOrNull()) ?: return null
            val unit = TemperatureUnit.chosen(prefs[SettingsManager.TEMPERATURE_UNIT], Locale.getDefault())
            return saved.takeIf { it.unit == unit && nowMs() - it.at in 0..WEATHER_SHOWN_MS }
        }

        /** Keeps [weather] as the last reading. */
        internal fun saveWeather(context: Context, weather: DiscoverWeather) {
            runCatching { store(context).edit { putString(KEY_WEATHER, weather.encode()) } }
        }

        /** What the widget shows now, from the calendar and what is already known: nothing here waits on the network. */
        internal suspend fun read(context: Context, prefs: SharedPreferences): DiscoverInput {
            val events = if (eventsOn(context, prefs)) sources.events(context) else null
            val weather = if (weatherOn(prefs)) {
                savedWeather(context, prefs)
            } else {
                // Off means off: with Weather or Web Suggestions off, the last reading goes too.
                runCatching { store(context).let { if (it.contains(KEY_WEATHER)) it.edit { remove(KEY_WEATHER) } } }
                null
            }
            val next = events?.let { DiscoverWidgetModel.nextEvent(it, now()) }
            val known = trip?.takeIf {
                next != null && leaveByOn(prefs) && it.eventId == next.eventId && it.beginMillis == next.beginMillis &&
                    it.place == next.location && nowMs() - it.at in 0..TRIP_SHOWN_MS
            }
            return DiscoverInput(weather, events, known?.leaveAt, weatherDue = weather == null && weatherOn(prefs) && hasPlace(context, prefs))
        }

        /** Whether there is a place to ask the forecast for: one set in the app, or the phone's own when allowed. */
        private fun hasPlace(context: Context, prefs: SharedPreferences): Boolean =
            HomeLocationCodec.decode(prefs[SettingsManager.WEATHER_HOME_LOCATION]) != null || AndroidDeviceLocation(context).permitted()

        /** Whether Discover is on with its Leave-By Time: the event's address is looked up only then. */
        private fun leaveByOn(prefs: SharedPreferences): Boolean = prefs[SettingsManager.TODAY_BUTTON] && prefs[SettingsManager.TODAY_LEAVE_BY]

        /**
         * [input] with the forecast read again, when the last reading is no longer fresh, and in the [foreground] with
         * when to leave for the next event. Each is kept for the redraws that follow.
         */
        private suspend fun refreshed(context: Context, prefs: SharedPreferences, input: DiscoverInput, foreground: Boolean): DiscoverInput {
            var result = input
            if (weatherOn(prefs) && !isFresh(input.weather)) {
                quietly { sources.weather(context) }?.let { report ->
                    val weather = DiscoverWeather.from(report, nowMs())
                    saveWeather(context, weather)
                    result = result.copy(weather = weather)
                }
            }
            val next = input.events?.let { DiscoverWidgetModel.nextEvent(it, now()) }
            if (foreground && next != null && next.location.isNotBlank() && leaveByOn(prefs)) {
                val last = trip?.takeIf { it.eventId == next.eventId && it.beginMillis == next.beginMillis && it.place == next.location }
                if (last == null || nowMs() - last.at !in 0..TRIP_SHOWN_MS / 2) {
                    val leaveAt = quietly { sources.leaveAt(context, next) }
                    trip = Trip(next.eventId, next.beginMillis, next.location, leaveAt, nowMs())
                    result = result.copy(leaveAt = leaveAt)
                }
            }
            return result
        }

        /** [read]'s answer, or null when it fails: a source that can't be reached leaves the widget as it is. */
        private suspend fun <T> quietly(read: suspend () -> T?): T? = try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Couldn't read for the Discover widget", e)
            null
        }

        /**
         * Arranges the next redraw ([DiscoverWidgetModel.nextRedrawDelayMs], or right away with [soon]), replacing any
         * arranged before, and a redraw for when the calendar next changes while events are shown.
         */
        private fun arrange(context: Context, prefs: SharedPreferences, input: DiscoverInput, soon: Boolean) {
            val delayMs = if (soon) 0L else DiscoverWidgetModel.nextRedrawDelayMs(now(), input.events)
            AppJob.DISCOVER_REFRESH.schedule(context) {
                setMinimumLatency(delayMs)
                setPersisted(true)
            }
            watchCalendar(context, prefs, keepExisting = true)
        }

        /**
         * Watches the calendar while events are shown, and stops when they aren't. A watch fires once; with
         * [keepExisting] one already set is kept.
         */
        internal fun watchCalendar(context: Context, prefs: SharedPreferences, keepExisting: Boolean) {
            if (!eventsOn(context, prefs)) {
                AppJob.DISCOVER_CALENDAR.cancel(context)
                return
            }
            AppJob.DISCOVER_CALENDAR.schedule(context, keepExisting) {
                addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                // A sync changes many rows at once: one redraw once it has been quiet for a moment.
                setTriggerContentUpdateDelay(CALENDAR_SETTLED_MS)
                setTriggerContentMaxDelay(CALENDAR_LATEST_MS)
            }
        }

        private const val CALENDAR_SETTLED_MS = 2_000L
        private const val CALENDAR_LATEST_MS = 15_000L

        /** The calendar changed: redraws the widgets, then watches again (a watch fires once). */
        internal suspend fun calendarChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            redrawAll(context, manager, Refresh.WEATHER)
            if (placed != true) return
            // Replacing it from inside the run that is redrawing ends that run; its work is already done.
            watchCalendar(context, SettingsManager.storedPrefs(context), keepExisting = false)
        }

        /**
         * Arranges the redraws while a Discover widget is placed; a force stop cancels them. One already arranged is
         * kept: this runs at every start, and the start Android makes to run the redraw must not replace it first.
         */
        fun syncSchedule(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            if (widgetIds(context, manager).isEmpty()) return
            AppJob.DISCOVER_REFRESH.schedule(context, keepExisting = true) {
                setMinimumLatency(0L)
                setPersisted(true)
            }
        }

        /** The Discover widget's colors, outline and corners in the search bar's design [config]. */
        internal fun style(config: WidgetDesignConfig): DiscoverStyle {
            val base = CountdownWidgetProvider.style(config)
            // The sun and the rain as the Discover page draws them, on a light or a dark surface.
            return DiscoverStyle(
                base = base,
                soft = ColorUtils.setAlphaComponent(base.hero, SOFT_ALPHA),
                sun = if (config.isPillLight) 0xFFB5651D.toInt() else 0xFFF2B872.toInt(),
                rain = if (config.isPillLight) 0xFF3A6FB8.toInt() else 0xFF7FB0F0.toInt()
            )
        }

        /** The search bar's design last saved in Widget Customization, resolved as the bar resolves it now. */
        private fun designConfig(context: Context, prefs: SharedPreferences): WidgetDesignConfig = SearchWidgetProvider.resolveWidgetConfig(
            context, prefs, isMaterialYou = SearchWidgetProvider.isMaterialStyle(prefs[SettingsManager.WIDGET_THEME_STYLE])
        )

        /** The Discover widget [appWidgetId] showing [input], at every size. */
        internal fun buildViews(context: Context, appWidgetId: Int, input: DiscoverInput, prefs: SharedPreferences): RemoteViews =
            RemoteViews(buildSizes(context, appWidgetId, input, prefs))

        /**
         * [buildViews]' views for each [DiscoverSize], keyed the way [RemoteViews] expects for a responsive widget.
         * Only the placed widget's own draw sets the tap ([withTap]): the tap is a PendingIntent keyed by the widget's
         * id, so the picker's sample must not make one.
         */
        internal fun buildSizes(
            context: Context,
            appWidgetId: Int,
            input: DiscoverInput,
            prefs: SharedPreferences,
            style: DiscoverStyle = style(designConfig(context, prefs)),
            withTap: Boolean = true,
            at: ZonedDateTime = now()
        ): Map<SizeF, RemoteViews> {
            val texts = DiscoverWidgetModel.texts(input, at, DateFormat.is24HourFormat(context))
            val tapIntent = if (withTap) {
                val tap = if (texts.setUp) discoverSettingsIntent(context) else discoverPageIntent(context, prefs, appWidgetId)
                tap.identifier = "discover_$appWidgetId"
                SearchWidgetProvider.overlayPendingIntent(context, appWidgetId, tap)
            } else {
                null
            }
            return DiscoverSize.entries.associate { size ->
                val views = RemoteViews(context.packageName, size.layout)
                bind(views, context, size, texts, style)
                if (tapIntent != null) views.setOnClickPendingIntent(android.R.id.background, tapIntent)
                size.size to views
            }
        }

        /**
         * The Android 15+ widget picker's preview: a sample afternoon in the design the widget would be placed in,
         * drawn already blended over the editor's page as the search bars' previews are
         * ([SearchWidgetProvider.pickerPreviewConfig]), so it shows the same colors there.
         */
        internal fun pickerPreview(context: Context, prefs: SharedPreferences): RemoteViews {
            val isDark = ThemeMode.from(prefs[SettingsManager.THEME]).isDark(ThemeMode.systemIsDark(context.resources.configuration))
            val config = SearchWidgetProvider.pickerPreviewConfig(designConfig(context, prefs), SearchWidgetProvider.editorPageColor(context, isDark))
            val afternoon = now().toLocalDate().atTime(14, 38).atZone(now().zone)
            val fahrenheit = TemperatureUnit.chosen(prefs[SettingsManager.TEMPERATURE_UNIT], Locale.getDefault()) == TemperatureUnit.FAHRENHEIT
            val sample = DiscoverInput(
                weather = DiscoverWeather(
                    at = afternoon.toInstant().toEpochMilli(),
                    unit = if (fahrenheit) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS,
                    temperature = if (fahrenheit) 63.0 else 17.0,
                    code = 0,
                    isDay = true,
                    high = if (fahrenheit) 68.0 else 20.0,
                    low = if (fahrenheit) 52.0 else 11.0
                ),
                events = listOf(
                    CalendarEvent(
                        title = context.getString(R.string.widget_discover_preview_title),
                        startTime = "",
                        beginMillis = afternoon.plusMinutes(52).toInstant().toEpochMilli()
                    )
                )
            )
            return RemoteViews(buildSizes(context, appWidgetId = 0, sample, prefs, style(config), withTap = false, at = afternoon))
        }

        /**
         * Draws [texts] in [style] on the layout of [size]. Everything is set on every redraw, whatever it is: a
         * launcher redraws a placed widget by applying the new views onto the ones it shows, so anything left unset
         * would keep the previous design's or state's.
         */
        private fun bind(views: RemoteViews, context: Context, size: DiscoverSize, texts: DiscoverTexts, style: DiscoverStyle) {
            val base = style.base
            val shape = base.shape
            val density = context.resources.displayMetrics.density
            fun px(dp: Int) = (dp * density).roundToInt()
            fun show(id: Int, shown: Boolean) = views.setViewVisibility(id, if (shown) View.VISIBLE else View.GONE)
            fun tint(id: Int, color: Int) = views.setColorStateList(id, "setImageTintList", ColorStateList.valueOf(color))
            fun words(id: Int, text: String?, color: Int) {
                show(id, text != null)
                views.setTextViewText(id, text.orEmpty())
                views.setTextColor(id, color)
            }

            // The backdrop, as the search bar's: the outline around the surface, faded at once by Transparency.
            show(R.id.discover_outline, shape.outline > 0)
            views.setImageViewResource(R.id.discover_outline, WidgetBarShape.background(shape.outerRadius))
            views.setImageViewResource(R.id.discover_surface, WidgetBarShape.background(shape.pillRadius))
            for (side in listOf(RemoteViews.MARGIN_LEFT, RemoteViews.MARGIN_TOP, RemoteViews.MARGIN_RIGHT, RemoteViews.MARGIN_BOTTOM)) {
                views.setViewLayoutMargin(R.id.discover_surface, side, shape.outline.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
            views.setFloat(R.id.discover_backdrop, "setAlpha", base.backdrop.backdrop)
            tint(R.id.discover_outline, base.rim)
            views.setInt(R.id.discover_outline, "setImageAlpha", WidgetColorRules.alphaInt(base.backdrop.rim))
            tint(R.id.discover_surface, base.surface)
            views.setInt(R.id.discover_surface, "setImageAlpha", 255)
            // The outer corners, told to the launcher, so its open and close animations keep them (see the bar's).
            views.setViewOutlinePreferredRadius(android.R.id.background, shape.outerRadius.toFloat(), TypedValue.COMPLEX_UNIT_DIP)

            val dot = when (texts.dot) {
                DiscoverDot.SUN -> style.sun
                DiscoverDot.RAIN -> style.rain
                else -> base.secondaryText
            }
            when (size) {
                DiscoverSize.PILL -> {
                    val pill = texts.pill
                    // As the bar: the pill's height (or more, for large text) inside the outline.
                    val inset = px(shape.outline)
                    views.setViewPadding(R.id.discover_content, inset + px(PILL_INSET), inset, inset + px(PILL_INSET), inset)
                    views.setInt(R.id.discover_lead, "setMinHeight", px(shape.pillHeight))
                    show(R.id.discover_dot, pill.temperature != null)
                    tint(R.id.discover_dot, dot)
                    words(R.id.discover_temperature, pill.temperature, base.hero)
                    show(R.id.discover_separator, pill.temperature != null)
                    tint(R.id.discover_separator, base.secondaryText)
                    words(R.id.discover_lead, pill.lead, base.text)
                    words(R.id.discover_time, pill.time, base.text)
                }
                DiscoverSize.TILE -> {
                    val tile = texts.tile
                    val inset = px(shape.outline + TILE_INSET)
                    views.setViewPadding(R.id.discover_content, inset, inset, inset, inset)
                    words(R.id.discover_top, tile.top, base.secondaryText)
                    words(R.id.discover_hero, tile.hero, base.hero)
                    words(R.id.discover_headline, tile.headline, base.text)
                    show(R.id.discover_event, tile.lead != null)
                    words(R.id.discover_lead, tile.lead, if (tile.accentBottom) base.hero else base.text)
                    words(R.id.discover_time, tile.time, base.text)
                }
                DiscoverSize.WIDE -> {
                    val wide = texts.wide
                    val sides = px(shape.outline + WIDE_INSET)
                    val ends = px(shape.outline + TILE_INSET)
                    views.setViewPadding(R.id.discover_content, sides, ends, sides, ends)
                    words(R.id.discover_top, wide.label, base.secondaryText)
                    words(R.id.discover_badge, wide.badge, base.hero)
                    views.setColorStateList(R.id.discover_badge, "setBackgroundTintList", ColorStateList.valueOf(style.soft))
                    words(R.id.discover_headline, wide.title, base.text)
                    show(R.id.discover_meta, wide.meta.isNotEmpty())
                    words(R.id.discover_meta_before, wide.before, base.secondaryText)
                    words(R.id.discover_meta_place, wide.place, base.secondaryText)
                    words(R.id.discover_meta_after, wide.after, base.secondaryText)
                    // Each part stands a gap after the one before it, and the first at the line's start.
                    views.setViewLayoutMargin(
                        R.id.discover_meta_place, RemoteViews.MARGIN_START, if (wide.before != null) META_GAP else 0f, TypedValue.COMPLEX_UNIT_DIP
                    )
                    views.setViewLayoutMargin(
                        R.id.discover_meta_after, RemoteViews.MARGIN_START,
                        if (wide.before != null || wide.place != null) META_GAP else 0f, TypedValue.COMPLEX_UNIT_DIP
                    )
                    show(R.id.discover_weather, wide.weather != null)
                    views.setColorStateList(R.id.discover_weather, "setBackgroundTintList", ColorStateList.valueOf(style.soft))
                    tint(R.id.discover_dot, dot)
                    views.setTextViewText(R.id.discover_weather_text, wide.weather.orEmpty())
                    views.setTextColor(R.id.discover_weather_text, base.text)
                }
            }
            views.setContentDescription(android.R.id.background, texts.spoken)
        }
    }
}
