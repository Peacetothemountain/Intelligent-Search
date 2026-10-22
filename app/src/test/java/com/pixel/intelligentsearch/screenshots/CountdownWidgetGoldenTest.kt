package com.pixel.intelligentsearch.screenshots

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
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
import java.time.ZonedDateTime

/**
 * Goldens of the countdown widget as a launcher shows it, in a 2 x 2 cell, light and dark: counting down to Halloween,
 * and not set up yet.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class CountdownWidgetGoldenTest(private val case: String, private val dark: Boolean) {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Before fun inject() = hilt.inject()

    private val defaultNow = CountdownWidgetProvider.now

    @After fun restoreClock() {
        CountdownWidgetProvider.now = defaultNow
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun countdown() {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light").commit()
        CountdownWidgetProvider.now = { ZonedDateTime.parse("2026-10-22T09:00-04:00[America/New_York]") }
        val event = if (case == "halloween") CountdownEvent("Halloween", LocalDate.of(2026, 10, 31), yearly = true) else null
        val views = CountdownWidgetProvider.buildViews(context, appWidgetId = 1, event = event, prefs = prefs)

        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            lateinit var host: FrameLayout
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                host = FrameLayout(activity).apply {
                    setBackgroundColor(if (dark) 0xFF1F1F1F.toInt() else 0xFFF1F3F4.toInt())
                    val pad = (12 * density).toInt()
                    setPadding(pad, pad, pad, pad)
                }
                host.addView(views.apply(activity, host), FrameLayout.LayoutParams((150 * density).toInt(), (150 * density).toInt()))
                activity.setContentView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            shadowOf(android.os.Looper.getMainLooper()).idle()
            host.captureRoboImage(
                filePath = "src/test/screenshots/widget_render/countdown_${case}_${if (dark) "dark" else "light"}.png",
                roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> = listOf("halloween", "setup").flatMap { case ->
            listOf(arrayOf<Any>(case, false), arrayOf<Any>(case, true))
        }
    }
}
