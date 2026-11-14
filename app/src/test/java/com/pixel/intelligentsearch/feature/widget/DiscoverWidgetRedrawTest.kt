package com.pixel.intelligentsearch.feature.widget

import android.Manifest
import android.app.Application
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.AppJob
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.answers.AnswerCache
import com.pixel.intelligentsearch.core.answers.DeviceLocation
import com.pixel.intelligentsearch.core.answers.DictionaryRepository
import com.pixel.intelligentsearch.core.answers.ForecastDay
import com.pixel.intelligentsearch.core.answers.LiveAnswerEngine
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.answers.WeatherRepository
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.net.HttpsGet
import com.pixel.intelligentsearch.feature.widget.DiscoverWidgetProvider.Refresh
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The Discover widget's redraws: arranged only while one is placed and only when what it shows changes with time,
 * never waiting for the forecast (the last reading stands in, and is dropped once it is old, in another scale, or
 * Weather is off), never sent again when nothing changed, reading the phone's location only with the search on
 * screen, and showing only what is on and allowed, or what keeps it from showing.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class DiscoverWidgetRedrawTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = SettingsManager.storedPrefs(context)
    private val manager get() = AppWidgetManager.getInstance(context)
    private val widgets: ShadowAppWidgetManager get() = shadowOf(manager)
    private val jobs get() = context.getSystemService(JobScheduler::class.java)
    private val widgetId = 31
    private val defaultNow = DiscoverWidgetProvider.now

    private var time: ZonedDateTime = ZonedDateTime.parse("2026-11-14T14:38-05:00[America/New_York]")
    private var forecast: WeatherReport? = null
    private var forecastReads = 0
    /** For each read of the forecast, whether it may use the phone's location (the search is on screen). */
    private val forecastInForeground = mutableListOf<Boolean>()
    private var calendar: List<CalendarEvent> = emptyList()

    private fun report(temperature: Double = 63.0, unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT) = WeatherReport(
        placeName = "Here", unit = unit, day = ForecastDay.TODAY, temperature = temperature, feelsLike = null, code = 0,
        isDay = true, high = 68.0, low = 51.0, hours = emptyList()
    )

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        DiscoverWidgetProvider.now = { time }
        DiscoverWidgetProvider.sources = object : DiscoverWidgetProvider.Sources {
            override suspend fun events(context: Context) = calendar
            override suspend fun weather(context: Context, foreground: Boolean): WeatherReport? {
                forecastReads++
                forecastInForeground += foreground
                return forecast
            }
            override suspend fun leaveAt(context: Context, event: CalendarEvent): Long? = event.beginMillis - TimeUnit.MINUTES.toMillis(22)
        }
    }

    @After fun restore() {
        // Takes the widget's jobs and its last reading with it.
        DiscoverWidgetProvider().onDisabled(context)
        DiscoverWidgetProvider.now = defaultNow
        DiscoverWidgetProvider.sources = DiscoverWidgetProvider.PHONE
    }

    private fun place() = widgets.bindAppWidgetId(widgetId, ComponentName(context, DiscoverWidgetProvider::class.java))

    private fun allowCalendar() = shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CALENDAR)

    private fun redraw(refresh: Refresh = Refresh.QUICK) = runBlocking { DiscoverWidgetProvider.redrawAll(context, manager, refresh) }

    private fun shown() = runBlocking { DiscoverWidgetProvider.read(context, prefs) }

    private fun setHomePlace() = prefs.edit().putString(SettingsManager.WEATHER_HOME_LOCATION.name, "36.31,-82.35;Johnson City, Tennessee").commit()

    /** What TalkBack reads for the placed widget, as last drawn. */
    private fun spoken(): String = widgets.getViewFor(widgetId).findViewById<View>(android.R.id.background).contentDescription.toString()

    /**
     * The words the wide size shows in [id] for what the widget has now, in a 4 x 2 cell on a phone; none when that
     * part isn't shown.
     */
    private fun wideWords(id: Int): String {
        val sizes = DiscoverWidgetProvider.buildSizes(context, widgetId, shown(), prefs, withTap = false)
        val views = DiscoverWidgetProvider.forCell(sizes, 380f, 180f)
        val view = views.apply(context, FrameLayout(context)).findViewById<TextView>(id)
        return if (generateSequence<View>(view) { it.parent as? View }.all { it.visibility == View.VISIBLE }) view.text.toString() else ""
    }

    @Test
    fun theRedrawsAreArrangedOnlyWhileAWidgetIsPlaced() {
        allowCalendar()
        // None placed: a redraw with the search bars and the start of the app arrange nothing.
        redraw()
        runBlocking { DiscoverWidgetProvider.syncSchedule(context) }
        runBlocking { AppJob.DISCOVER_REFRESH.run(context, PersistableBundle()) }
        runBlocking { AppJob.DISCOVER_CALENDAR.run(context, PersistableBundle()) }
        assertFalse(AppJob.DISCOVER_REFRESH.isPending(context))
        assertFalse(AppJob.DISCOVER_CALENDAR.isPending(context))

        place()
        redraw()
        assertNotNull(widgets.getViewFor(widgetId))
        assertTrue(AppJob.DISCOVER_REFRESH.isPending(context))
        assertTrue(AppJob.DISCOVER_CALENDAR.isPending(context))

        // The last one removed: nothing is left arranged.
        DiscoverWidgetProvider().onDisabled(context)
        assertFalse(AppJob.DISCOVER_REFRESH.isPending(context))
        assertFalse(AppJob.DISCOVER_CALENDAR.isPending(context))
    }

    @Test
    fun aStartOfTheAppArrangesTheRedrawAgainAfterAForceStop() {
        place()
        // Something to show that changes with time (the day's events).
        allowCalendar()
        assertFalse(AppJob.DISCOVER_REFRESH.isPending(context))
        runBlocking { DiscoverWidgetProvider.syncSchedule(context) }
        assertTrue(AppJob.DISCOVER_REFRESH.isPending(context))
    }

    @Test
    fun aStartOfTheAppDrawsAgainSoAnEventNoLongerAllowedIsGoneAtOnce() {
        place()
        allowCalendar()
        calendar = listOf(CalendarEvent("Dentist", "", eventId = 4, beginMillis = time.plusMinutes(52).toInstant().toEpochMilli()))
        redraw()
        assertTrue("Dentist" in spoken())
        val arranged = jobs.getPendingJob(AppJob.DISCOVER_REFRESH.id)!!.minLatencyMillis

        // Taking calendar access away ends the app; its next start draws the widget again without the event.
        shadowOf(context as Application).denyPermissions(Manifest.permission.READ_CALENDAR)
        runBlocking { DiscoverWidgetProvider.syncSchedule(context) }
        assertEquals("Discover. Set a weather place in Discover.", spoken())
        // The redraw arranged before is kept: the start Android makes to run it must not replace it.
        assertEquals(arranged, jobs.getPendingJob(AppJob.DISCOVER_REFRESH.id)!!.minLatencyMillis)
    }

    @Test
    fun theCalendarIsWatchedOnlyWhileEventsAreShown() {
        place()
        // No calendar access: nothing to watch.
        redraw()
        assertFalse(AppJob.DISCOVER_CALENDAR.isPending(context))

        allowCalendar()
        redraw()
        assertTrue(AppJob.DISCOVER_CALENDAR.isPending(context))
        // The watch fires once, so a change arranges the next one.
        runBlocking { AppJob.DISCOVER_CALENDAR.run(context, PersistableBundle()) }
        assertTrue(AppJob.DISCOVER_CALENDAR.isPending(context))

        prefs.edit().putBoolean(SettingsManager.TODAY_UP_NEXT.name, false).commit()
        redraw()
        assertFalse(AppJob.DISCOVER_CALENDAR.isPending(context))
    }

    @Test
    fun aRedrawNeverWaitsForTheForecastAndTheJobReadsIt() {
        place()
        // A place set in the app: the job, which runs with the app out of view, reads the forecast for it.
        setHomePlace()
        forecast = report()
        // Drawn with the search bars: the forecast isn't asked for, and the job is asked to run right away for it.
        redraw()
        assertEquals(0, forecastReads)
        assertNull(shown().weather)
        assertEquals(0L, jobs.getPendingJob(AppJob.DISCOVER_REFRESH.id)!!.minLatencyMillis)

        runBlocking { AppJob.DISCOVER_REFRESH.run(context, PersistableBundle()) }
        assertEquals(1, forecastReads)
        assertEquals(listOf(false), forecastInForeground)
        assertEquals(63.0, shown().weather!!.temperature, 0.0)
        // With nothing else to wait for, the next redraw is in an hour.
        assertEquals(TimeUnit.MINUTES.toMillis(60), jobs.getPendingJob(AppJob.DISCOVER_REFRESH.id)!!.minLatencyMillis)

        // A fresh reading isn't asked for again.
        time = time.plusMinutes(20)
        runBlocking { AppJob.DISCOVER_REFRESH.run(context, PersistableBundle()) }
        assertEquals(1, forecastReads)
    }

    @Test
    fun theLastReadingStandsInWhileItIsRecentAndAnUnreachableForecastIsNotAskedForInALoop() {
        place()
        allowCalendar()
        forecast = report()
        redraw(Refresh.WEATHER)
        assertEquals("63° Clear · H 68°", wideWords(R.id.discover_weather_text))

        // Offline an hour later: the job can't read a newer one, shows the last, and goes back to its usual pace.
        forecast = null
        time = time.plusMinutes(61)
        runBlocking { AppJob.DISCOVER_REFRESH.run(context, PersistableBundle()) }
        assertEquals(2, forecastReads)
        assertEquals("63° Clear · H 68°", wideWords(R.id.discover_weather_text))
        assertTrue(jobs.getPendingJob(AppJob.DISCOVER_REFRESH.id)!!.minLatencyMillis > 0)

        // Too old to stand for the weather now: none is shown rather than a stale temperature.
        time = time.plusHours(3)
        assertNull(shown().weather)
        assertEquals("", wideWords(R.id.discover_weather_text))
    }

    @Test
    fun aReadingInAnotherScaleIsNotShown() {
        place()
        forecast = report()
        redraw(Refresh.WEATHER)
        assertNotNull(shown().weather)

        prefs.edit().putString(SettingsManager.TEMPERATURE_UNIT.name, "celsius").commit()
        assertNull(shown().weather)
        // The job reads it again in the new scale.
        forecast = report(temperature = 17.0, unit = TemperatureUnit.CELSIUS)
        redraw(Refresh.WEATHER)
        assertEquals(17.0, shown().weather!!.temperature, 0.0)
    }

    @Test
    fun withWeatherOffNothingOfItIsShownOrKept() {
        place()
        forecast = report()
        redraw(Refresh.WEATHER)
        assertNotNull(shown().weather)

        prefs.edit().putBoolean(SettingsManager.TODAY_WEATHER.name, false).commit()
        redraw(Refresh.WEATHER)
        assertNull(shown().weather)
        assertEquals(1, forecastReads)
        // Turned back on, the old reading isn't there to come back.
        prefs.edit().putBoolean(SettingsManager.TODAY_WEATHER.name, true).commit()
        assertNull(DiscoverWidgetProvider.savedWeather(context, prefs))

        // Web Suggestions off stops forecasts everywhere, here too.
        redraw(Refresh.WEATHER)
        assertNotNull(shown().weather)
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
        assertNull(shown().weather)
    }

    @Test
    fun eventsShowOnlyWithUpNextOnAndCalendarAccess() {
        place()
        calendar = listOf(CalendarEvent("Dentist", "", eventId = 4, beginMillis = time.plusMinutes(52).toInstant().toEpochMilli(), location = "1200 Main St"))
        forecast = report()
        redraw(Refresh.WEATHER)
        // No calendar access: the weather alone, and never "Nothing else today" for a calendar that can't be read.
        assertNull(shown().events)
        assertEquals("63° · Clear", wideWords(R.id.discover_headline))

        allowCalendar()
        assertEquals("Dentist", wideWords(R.id.discover_headline))
        assertEquals("within 1 hr", wideWords(R.id.discover_badge))

        prefs.edit().putBoolean(SettingsManager.TODAY_UP_NEXT.name, false).commit()
        assertNull(shown().events)
    }

    @Test
    fun withNothingOnOrAllowedItSaysWhereToTurnOneOn() {
        place()
        prefs.edit().putBoolean(SettingsManager.TODAY_WEATHER.name, false).putBoolean(SettingsManager.TODAY_UP_NEXT.name, false).commit()
        redraw()
        assertEquals("Turn on Up Next or Weather in Discover", wideWords(R.id.discover_headline))
        assertEquals("Discover. Turn on Up Next or Weather in Discover.", widgets.getViewFor(widgetId).findViewById<View>(android.R.id.background).contentDescription)
        // Nothing it shows changes with time: no redraw is arranged.
        assertFalse(AppJob.DISCOVER_REFRESH.isPending(context))

        // Up Next on, without calendar access: that is what it says.
        prefs.edit().putBoolean(SettingsManager.TODAY_UP_NEXT.name, true).commit()
        assertEquals("Allow calendar access for Up Next", wideWords(R.id.discover_headline))
    }

    @Test
    fun weatherThatIsOnButBlockedSaysWhatBlocksIt() {
        place()
        // Web Suggestions off stops forecasts: the widget says so rather than asking to turn Weather on.
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
        assertEquals("Turn on Web Suggestions for weather", wideWords(R.id.discover_headline))
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, true).putBoolean(SettingsManager.SEARCH_WEB.name, false).commit()
        assertEquals("Turn on Web for weather", wideWords(R.id.discover_headline))
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB.name, true).commit()
        // Allowed, with no place set and no location access.
        assertEquals("Set a weather place in Discover", wideWords(R.id.discover_headline))
        redraw()
        assertFalse(AppJob.DISCOVER_REFRESH.isPending(context))
    }

    @Test
    fun weatherThatIsOnWithAPlaceButNoReadingSaysSoInsteadOfAskingToTurnItOn() {
        place()
        // Weather is on, but there is nowhere to ask the forecast for: setting a place in Discover is what helps.
        redraw()
        assertEquals("Set a weather place in Discover", wideWords(R.id.discover_headline))

        // A place is set, and the forecast can't be reached.
        setHomePlace()
        redraw(Refresh.WEATHER)
        assertEquals("No weather right now", wideWords(R.id.discover_headline))

        forecast = report()
        time = time.plusMinutes(1)
        redraw(Refresh.WEATHER)
        assertEquals("63° · Clear", wideWords(R.id.discover_headline))
    }

    @Test
    fun whenToLeaveIsWorkedOutOnlyWithTheSearchOnScreenAndDiscoverOn() {
        place()
        allowCalendar()
        val begin = time.plusMinutes(52).toInstant().toEpochMilli()
        calendar = listOf(CalendarEvent("Dentist", "", eventId = 4, beginMillis = begin, location = "1200 Main St"))
        prefs.edit().putBoolean(SettingsManager.TODAY_BUTTON.name, true).commit()

        // The job runs with the app out of view, where the phone's location can't be read.
        redraw(Refresh.WEATHER)
        assertNull(shown().leaveAt)

        redraw(Refresh.FOREGROUND)
        assertEquals(begin - TimeUnit.MINUTES.toMillis(22), shown().leaveAt)
        assertEquals("Leave by 3:08 PM", wideWords(R.id.discover_meta_after))

        // With Discover's Leave-By Time off it is neither shown nor looked up.
        prefs.edit().putBoolean(SettingsManager.TODAY_LEAVE_BY.name, false).commit()
        assertNull(shown().leaveAt)
    }

    @Test
    fun aForecastWithNowhereToAskForIsNotAskedForAtOnce() {
        place()
        allowCalendar()
        forecast = report()
        // Weather is on with no reading, but no place is set in the app: the job, which can't use the phone's
        // location, isn't run at once for a forecast it can't read.
        redraw()
        assertTrue(jobs.getPendingJob(AppJob.DISCOVER_REFRESH.id)!!.minLatencyMillis > 0)

        setHomePlace()
        redraw()
        assertEquals(0L, jobs.getPendingJob(AppJob.DISCOVER_REFRESH.id)!!.minLatencyMillis)
    }

    @Test
    fun onlyTheSearchOnScreenMayUseThePhonesLocationForTheForecast() {
        place()
        forecast = report()
        redraw(Refresh.WEATHER)
        time = time.plusMinutes(61)
        redraw(Refresh.FOREGROUND)
        assertEquals(listOf(false, true), forecastInForeground)

        // The forecast the widget reads in the background is for the place set in the app only: with none, the
        // phone's location isn't asked for and nothing is sent.
        var locationAsked = 0
        val location = object : DeviceLocation {
            override fun permitted() = true
            override suspend fun approximate(): Pair<Double, Double> {
                locationAsked++
                return 48.8566 to 2.3522
            }
        }
        val sent = mutableListOf<String>()
        val http = HttpsGet { url, _, _ ->
            sent += url
            null
        }
        val engine = LiveAnswerEngine(
            prefs = { prefs },
            dictionary = DictionaryRepository(http, AnswerCache(60_000, 16), AnswerCache(60_000, 16)),
            weather = WeatherRepository(http, AnswerCache(60_000, 16), AnswerCache(60_000, 16)),
            screenTime = { null },
            location = location
        )
        assertNull(runBlocking { engine.weatherHere(useDeviceLocation = false) })
        assertEquals(0, locationAsked)
        assertTrue(sent.isEmpty())
        // The search on screen may.
        runBlocking { engine.weatherHere() }
        assertEquals(1, locationAsked)
    }

    @Test
    fun aRedrawThatWouldDrawTheSameIsNotSentToTheLauncherAgain() {
        place()
        allowCalendar()
        widgets.setAlwaysRecreateViewsDuringUpdate(true)
        redraw()
        val first = widgets.getViewFor(widgetId)
        // Drawn again with the search bars, and the calendar changing with nothing it shows changing.
        redraw()
        runBlocking { DiscoverWidgetProvider.calendarChanged(context) }
        assertSame(first, widgets.getViewFor(widgetId))

        // Something new to say is drawn.
        calendar = listOf(CalendarEvent("Dentist", "", eventId = 4, beginMillis = time.plusMinutes(52).toInstant().toEpochMilli()))
        runBlocking { DiscoverWidgetProvider.calendarChanged(context) }
        assertNotSame(first, widgets.getViewFor(widgetId))
    }

    @Test
    fun openingTheSearchSoonAfterARedrawWithNothingNewReadsNothing() {
        place()
        allowCalendar()
        setHomePlace()
        forecast = report()
        calendar = listOf(
            CalendarEvent("Dentist", "", eventId = 4, beginMillis = time.plusMinutes(52).toInstant().toEpochMilli(), location = "1200 Main St")
        )
        prefs.edit().putBoolean(SettingsManager.TODAY_BUTTON.name, true).commit()
        // Drawn with the search bars: when to leave isn't worked out yet, so the search opening has something to do.
        redraw()
        assertFalse(DiscoverWidgetProvider.upToDate(context))

        redraw(Refresh.FOREGROUND)
        assertTrue(DiscoverWidgetProvider.upToDate(context))
        // A setting the widget follows changed: brought up to date again.
        prefs.edit().putBoolean(SettingsManager.TODAY_LEAVE_BY.name, false).commit()
        assertFalse(DiscoverWidgetProvider.upToDate(context))
        prefs.edit().putBoolean(SettingsManager.TODAY_LEAVE_BY.name, true).commit()
        assertTrue(DiscoverWidgetProvider.upToDate(context))
        // More than a few minutes later: brought up to date again.
        time = time.plusMinutes(6)
        assertFalse(DiscoverWidgetProvider.upToDate(context))
    }
}
