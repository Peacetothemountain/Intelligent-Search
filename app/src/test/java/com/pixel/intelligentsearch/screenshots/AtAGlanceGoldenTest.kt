package com.pixel.intelligentsearch.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.glance.GlanceAlarm
import com.pixel.intelligentsearch.core.glance.GlanceBattery
import com.pixel.intelligentsearch.core.glance.GlanceBirthday
import com.pixel.intelligentsearch.core.glance.GlanceItem
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.glance.GlanceTimer
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.core.glance.MediaControls
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.GlanceClock
import com.pixel.intelligentsearch.feature.search.GlanceSection
import com.pixel.intelligentsearch.feature.search.LocalGlanceClock
import com.pixel.intelligentsearch.feature.search.NotificationResultRow
import com.pixel.intelligentsearch.feature.search.NotificationsHeader
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.TimeZone

/**
 * Golden images of At a Glance with one of every card (a running timer, the next alarm, what is playing with a
 * stand-in cover, a birthday and the battery), and of the Notifications results section, in both
 * themes. The data, the clock and the time zone are fixed, so nothing depends on the machine. See
 * [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h640dp-xhdpi")
class AtAGlanceGoldenTest(private val state: String, private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val zone = TimeZone.getDefault()

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        // App icons are cached for the process: forget any another test drew for these apps (the widget goldens give
        // com.example.mail one), so the rows show their own fallback as the goldens were drawn.
        com.pixel.intelligentsearch.core.ui.clearThemedIconCache()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun tearDown() = TimeZone.setDefault(zone)

    private object StillControls : MediaControls {
        override fun play() {}
        override fun pause() {}
        override fun next() {}
        override fun open() {}
    }

    /** A two-tone stand-in cover: a disc on a square. */
    private fun cover() = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).apply {
            drawColor(0xFF6D5A8C.toInt())
            drawCircle(40f, 40f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7C26A.toInt() })
        }
    }.asImageBitmap()

    private val items = listOf(
        GlanceItem.Timer(GlanceTimer("t", GlanceTimer.Kind.Timer, "Pasta", NOW + 272_000L, "Clock", "com.google.android.deskclock", null)),
        // 07:00 UTC, five hours away.
        GlanceItem.Alarm(GlanceAlarm(NOW + 5 * 60 * 60 * 1000L)),
        GlanceItem.Media(GlanceMedia("Midnight City", "M83", "YouTube Music", "com.example.music", isPlaying = true, artwork = cover(), controls = StillControls)),
        GlanceItem.Birthday(GlanceBirthday(7, "lk", "Ana Lima", 0, LocalDate.of(2027, 1, 15), turning = 30)),
        GlanceItem.Battery(GlanceBattery(64, pluggedIn = true, statusLabel = "Charging"))
    )

    private val notifications = listOf(
        IndexedNotification("a", "com.example.chat", "Chat", "Ana Lima", "Dinner at the café at 8? I booked the table by the window.", NOW - 5 * 60_000L),
        IndexedNotification("b", "com.example.mail", "Mail", "Your table is booked", "Café Lumière, Friday at 8 pm for two.", NOW - 3 * 60 * 60_000L)
    )

    @Test
    fun section() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalGlanceClock provides GlanceClock({ NOW }, ticks = false)) {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                        when (state) {
                            "glance" -> GlanceSection(items)
                            else -> {
                                NotificationsHeader()
                                notifications.forEach { NotificationResultRow(it, now = NOW, iconPack = null, onOpened = {}) }
                            }
                        }
                    }
                }
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/overlay/${state}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        /** 2027-01-15 02:00 UTC. */
        private const val NOW = 1_800_000_000_000L - 1_800_000_000_000L % 86_400_000L + 2 * 60 * 60 * 1000L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> = listOf("glance", "notifications")
            .flatMap { state -> listOf(arrayOf<Any>(state, false), arrayOf<Any>(state, true)) }
    }
}
