package com.pixel.intelligentsearch.screenshots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.feature.widget.DiscoverInput
import com.pixel.intelligentsearch.feature.widget.DiscoverWeather
import com.pixel.intelligentsearch.feature.widget.DiscoverWidgetProvider
import com.pixel.intelligentsearch.feature.widget.DiscoverWidgetProvider.DiscoverSize
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZonedDateTime

/**
 * Goldens of the Discover widget as a launcher shows it, in the search bar's default System and Material designs,
 * light and dark: one row high (the pill), as a 2 x 2 tile, wide, all three under the search bar itself (family), to
 * see that they belong together, with nothing left today, with the event alone (no weather, and a long name), and
 * with nothing on yet.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class DiscoverWidgetGoldenTest(private val case: String, private val material: Boolean, private val dark: Boolean) {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Before fun inject() = hilt.inject()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val afternoon: ZonedDateTime = ZonedDateTime.parse("2026-11-14T14:38-05:00[America/New_York]")

    private fun at(time: String): Long = ZonedDateTime.parse("${time}-05:00[America/New_York]").toInstant().toEpochMilli()

    private val weather = DiscoverWeather(
        at = afternoon.toInstant().toEpochMilli(), unit = TemperatureUnit.FAHRENHEIT, temperature = 63.0, code = 0, isDay = true,
        high = 68.0, low = 52.0, rainAt = at("2026-11-14T20:00")
    )
    private val dentist = CalendarEvent("Dentist", "", eventId = 1, beginMillis = at("2026-11-14T15:30"), location = "1200 Main St")
    private val long = CalendarEvent(
        "Quarterly planning review with the whole leadership team", "", eventId = 2, beginMillis = at("2026-11-14T15:30"),
        location = "Conference Room B, 4th Floor, 500 Market Street, San Francisco"
    )

    @Test
    fun discover() {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light")
            .putString(SettingsManager.WIDGET_THEME_STYLE.name, if (material) "Material You (Minimal)" else "System Default")
            .commit()
        val input = when (case) {
            "nothing" -> DiscoverInput(weather, listOf(CalendarEvent("Breakfast", "", eventId = 3, beginMillis = at("2026-11-15T08:00"))))
            "eventonly" -> DiscoverInput(null, listOf(long), leaveAt = at("2026-11-14T15:08"))
            "setup" -> DiscoverInput(null, null)
            else -> DiscoverInput(weather, listOf(dentist), leaveAt = at("2026-11-14T15:08"))
        }
        val sizes = DiscoverWidgetProvider.buildSizes(context, appWidgetId = 1, input = input, prefs = prefs, withTap = false, at = afternoon)

        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            lateinit var host: FrameLayout
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                fun dp(value: Int) = (value * density).toInt()
                host = FrameLayout(activity).apply {
                    setBackgroundColor(if (dark) 0xFF1F1F1F.toInt() else 0xFFF1F3F4.toInt())
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                }
                fun cell(size: DiscoverSize): View = sizes.getValue(size.size).apply(activity, host)
                // A home screen cell of each size on a phone: 4 x 1, 2 x 2 and 4 x 2.
                val (widget, params) = when (case) {
                    "pill" -> cell(DiscoverSize.PILL) to FrameLayout.LayoutParams(dp(380), dp(80))
                    "tile" -> cell(DiscoverSize.TILE) to FrameLayout.LayoutParams(dp(180), dp(190))
                    "wide" -> cell(DiscoverSize.WIDE) to FrameLayout.LayoutParams(dp(380), dp(180))
                    else -> {
                        // One over the other as on a home screen; the family has the search bar on top.
                        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                        if (case == "family") {
                            val bar = SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 2, isMaterialYou = material, prefs = prefs)
                                .getValue(SearchWidgetProvider.EXPANDED_WIDTH).apply(activity, column)
                            column.addView(bar, LinearLayout.LayoutParams(dp(380), dp(80)))
                        }
                        column.addView(cell(DiscoverSize.PILL), LinearLayout.LayoutParams(dp(380), dp(80)).apply { topMargin = dp(8) })
                        column.addView(cell(DiscoverSize.TILE), LinearLayout.LayoutParams(dp(180), dp(190)).apply { topMargin = dp(16) })
                        column.addView(cell(DiscoverSize.WIDE), LinearLayout.LayoutParams(dp(380), dp(180)).apply { topMargin = dp(16) })
                        column to FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                }
                host.addView(widget, params)
                activity.setContentView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            shadowOf(android.os.Looper.getMainLooper()).idle()
            host.captureRoboImage(
                filePath = "src/test/screenshots/widget_render/discover_${case}_${if (material) "material" else "system"}_${if (dark) "dark" else "light"}.png",
                roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
            )
        }
    }

    companion object {
        /** Each case in the designs it is recorded in. */
        private val CASES = listOf(
            "pill" to listOf(false, true),
            "tile" to listOf(false, true),
            "wide" to listOf(false, true),
            "family" to listOf(false, true),
            "nothing" to listOf(true),
            "eventonly" to listOf(false),
            "setup" to listOf(false)
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} material={1} dark={2}")
        fun parameters(): List<Array<Any>> = CASES.flatMap { (case, designs) ->
            designs.flatMap { material -> listOf(arrayOf<Any>(case, material, false), arrayOf<Any>(case, material, true)) }
        }
    }
}
