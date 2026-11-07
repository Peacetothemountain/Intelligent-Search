package com.pixel.intelligentsearch.screenshots

import android.Manifest
import android.app.Notification
import com.pixel.intelligentsearch.core.answers.ForecastDay
import com.pixel.intelligentsearch.core.answers.ScreenTimeApp
import com.pixel.intelligentsearch.core.answers.ScreenTimeReport
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.answers.WeatherHour
import com.pixel.intelligentsearch.core.answers.WeatherReport
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.feature.search.today.LocalTodaySources
import com.pixel.intelligentsearch.feature.search.today.TodayBattery
import com.pixel.intelligentsearch.feature.search.today.TodayControls
import com.pixel.intelligentsearch.feature.search.today.TodayCountdown
import com.pixel.intelligentsearch.feature.search.today.TodaySources
import com.pixel.intelligentsearch.feature.search.today.TodayMemory
import com.pixel.intelligentsearch.feature.search.today.TodayTrip
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.glance.GlanceAlarm
import com.pixel.intelligentsearch.core.glance.GlanceBirthday
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.glance.GlanceSnapshot
import com.pixel.intelligentsearch.core.glance.GlanceSource
import com.pixel.intelligentsearch.core.glance.GlanceTimer
import com.pixel.intelligentsearch.core.glance.MediaControls
import com.pixel.intelligentsearch.core.system.MediaSessionAccess
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.GlanceClock
import com.pixel.intelligentsearch.feature.search.LocalGlanceClock
import com.pixel.intelligentsearch.feature.search.LocalGlanceSource
import com.pixel.intelligentsearch.feature.search.today.TodayPage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.TimeZone

/**
 * Golden images of the whole Today page with one of everything it reads (a birthday, the weather, the next event with a
 * place and two more later today, a running timer, the next alarm, what is playing, notifications to catch up on, the
 * controls, the battery and screen time, recent searches, the word and countdown, and tomorrow), in both themes, at a
 * fixed time and zone, drawn in one tall window. See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class TodayGoldenTest(private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val zone = TimeZone.getDefault()

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(Manifest.permission.READ_CALENDAR)
    }

    @After fun tearDown() = TimeZone.setDefault(zone)

    private object StillControls : MediaControls {
        override fun play() {}
        override fun pause() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) {}
        override fun open() {}
    }

    private fun cover() = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).apply {
            drawColor(0xFF6D5A8C.toInt())
            drawCircle(40f, 40f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7C26A.toInt() })
        }
    }.asImageBitmap()

    private val snapshot = GlanceSnapshot(
        alarm = GlanceAlarm(NOW + 16 * HOUR + 22 * MINUTE),
        timers = listOf(GlanceTimer("t", GlanceTimer.Kind.Timer, "Pasta", NOW + 272_000L, "Clock", "com.google.android.deskclock", null)),
        media = GlanceMedia(
            "Midnight City", "M83", "YouTube Music", "com.example.music", isPlaying = true, artwork = cover(), controls = StillControls,
            progress = MediaSessionAccess.Progress(durationMs = 243_000L, positionMs = 98_000L, updatedAtElapsed = 0L, speed = 1f, canSeek = true)
        ),
        events = listOf(
            CalendarEvent("Dentist", "3:30 PM", 1, NOW + 52 * MINUTE, location = "1200 Main St", phone = "423-555-0100"),
            CalendarEvent("Pick up Ana", "6:00 PM", 2, NOW + 3 * HOUR + 22 * MINUTE),
            CalendarEvent("Ana's party", "8:30 PM", 3, NOW + 5 * HOUR + 52 * MINUTE)
        ),
        birthdays = listOf(GlanceBirthday(7, "lk", "Ana Lima", 0, LocalDate.of(2027, 1, 15), turning = 30))
    )

    /** Everything Today reads besides At a Glance, fixed. */
    private object FixedSources : TodaySources {
        private val start = LocalDateTime.of(2027, 1, 15, 15, 0)
        private val today = WeatherReport(
            "Johnson City", TemperatureUnit.FAHRENHEIT, ForecastDay.TODAY, 63.0, 61.0, 0, true, 68.0, 39.0,
            listOf(65.0, 66.0, 68.0, 66.0, 61.0, 57.0).mapIndexed { i, t ->
                WeatherHour(start.plusHours(i.toLong()), t, if (i < 4) 0 else 3, i < 3, listOf(0, 0, 5, 10, 40, 60)[i])
            },
            sunset = LocalDateTime.of(2027, 1, 15, 17, 24)
        )
        private val tomorrow = WeatherReport(
            "Johnson City", TemperatureUnit.FAHRENHEIT, ForecastDay.TOMORROW, 58.0, null, 61, true, 58.0, 41.0, emptyList()
        )

        override fun weather(day: ForecastDay): Flow<WeatherReport> = flowOf(if (day == ForecastDay.TODAY) today else tomorrow)
        override fun battery() = TodayBattery(level = 78, charging = false)
        override fun screenTime() = ScreenTimeReport(
            134 * MINUTE,
            listOf(ScreenTimeApp("a", "YouTube", 52 * MINUTE), ScreenTimeApp("b", "Chrome", 31 * MINUTE), ScreenTimeApp("c", "Messages", 18 * MINUTE))
        )
        override fun notifications(): Flow<List<IndexedNotification>> = flowOf(
            listOf(
                IndexedNotification("1", "com.example.dialer", "Phone", "Mom", "Missed call", NOW - 118 * MINUTE, Notification.CATEGORY_MISSED_CALL),
                IndexedNotification("2", "com.example.chat", "Messages", "Sam", "Are we still on for 6?", NOW - 20 * MINUTE, Notification.CATEGORY_MESSAGE),
                IndexedNotification("3", "com.example.shop", "Shop", "Your package", "Your order is out for delivery", NOW - 40 * MINUTE)
            )
        )
        override fun countdown(today: LocalDate) = TodayCountdown("Valentine's Day", 30, today.plusDays(30))
        override suspend fun trip(event: CalendarEvent) = TodayTrip(event.beginMillis - 22 * MINUTE, 22)
        override suspend fun memory(today: LocalDate) = TodayMemory(3, 24, android.net.Uri.EMPTY, photo())
        override val controls = object : TodayControls {
            override fun doNotDisturb() = false
            override fun setDoNotDisturb(on: Boolean) = false
            override fun flashlight(): Flow<Boolean> = flowOf(false)
            override fun setFlashlight(on: Boolean) {}
            override fun batterySaver() = true
        }
    }

    @Test
    fun page() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalGlanceClock provides GlanceClock({ NOW }, ticks = false, elapsed = { 0L }),
                    LocalGlanceSource provides GlanceSource { _, _ -> flowOf(snapshot) },
                    LocalTodaySources provides FixedSources
                ) {
                    TodayPage(
                        active = true, hiddenApps = emptySet(), onBack = {}, onOpenSettings = {},
                        recentSearches = listOf("obama speech", "pizza near me", "weather tomorrow")
                    )
                }
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/today/page_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE

        /** 2027-01-15, 2:38 PM UTC. */
        private const val NOW = 1_800_000_000_000L - 1_800_000_000_000L % 86_400_000L + 14 * HOUR + 38 * MINUTE

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}

/** A stand-in photo: a sun going down behind a hill. */
private fun photo() = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
    Canvas(this).apply {
        drawColor(0xFF9D4F53.toInt())
        drawCircle(84f, 40f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD59A.toInt() })
        drawCircle(60f, 160f, 80f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A2E4F.toInt() })
    }
}.asImageBitmap()

