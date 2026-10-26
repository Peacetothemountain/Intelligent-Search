package com.pixel.intelligentsearch.screenshots

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider.CountdownSize
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Goldens of the countdown widget as a launcher shows it, in the search bar's default System and Material designs,
 * light and dark: one row high (the pill), as a 2 x 2 tile, wide, large, on the day, not set up yet, with a long name
 * at twice the text size, and beside the search bar itself (family), to see that they belong together.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class CountdownWidgetGoldenTest(private val case: String, private val material: Boolean, private val dark: Boolean) {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Before fun inject() = hilt.inject()

    private val defaultNow = CountdownWidgetProvider.now

    @After fun restore() {
        CountdownWidgetProvider.now = defaultNow
        RuntimeEnvironment.setFontScale(1f)
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun countdown() {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light")
            .putString(SettingsManager.WIDGET_THEME_STYLE.name, if (material) "Material You (Minimal)" else "System Default")
            .commit()
        CountdownWidgetProvider.now = {
            ZonedDateTime.parse(if (case == "today") "2026-10-31T09:00-04:00[America/New_York]" else "2026-10-22T09:00-04:00[America/New_York]")
        }
        if (case == "bigtext") RuntimeEnvironment.setFontScale(2f)
        val event = when (case) {
            "setup" -> null
            "bigtext" -> CountdownEvent("Grandma and Grandpa's Anniversary Party", LocalDate.of(2026, 12, 19), yearly = false)
            else -> CountdownEvent("Halloween", LocalDate.of(2026, 10, 31), yearly = true)
        }
        val sizes = CountdownWidgetProvider.buildSizes(context, appWidgetId = 1, event = event, prefs = prefs)

        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            lateinit var host: FrameLayout
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                fun dp(value: Int) = (value * density).toInt()
                host = FrameLayout(activity).apply {
                    setBackgroundColor(if (dark) 0xFF1F1F1F.toInt() else 0xFFF1F3F4.toInt())
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                }
                fun cell(size: CountdownSize, width: Int, height: Int) =
                    sizes.getValue(size.size).apply(activity, host) to FrameLayout.LayoutParams(dp(width), dp(height))
                // A home screen cell of each size on a phone: 4 x 1, 2 x 2, 4 x 2 and 4 x 4.
                val (widget, params) = when (case) {
                    "pill" -> cell(CountdownSize.PILL, 380, 80)
                    "wide" -> cell(CountdownSize.WIDE, 380, 180)
                    "large" -> cell(CountdownSize.LARGE, 380, 380)
                    "bigtext" -> {
                        // One row and a 2 x 2 tile, one over the other.
                        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                        val (pill, pillParams) = cell(CountdownSize.PILL, 380, 90)
                        val (tile, tileParams) = cell(CountdownSize.TILE, 180, 190)
                        column.addView(pill, LinearLayout.LayoutParams(pillParams.width, pillParams.height))
                        column.addView(tile, LinearLayout.LayoutParams(tileParams.width, tileParams.height).apply { topMargin = dp(12) })
                        column to FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    "family" -> {
                        // The search bar over the countdown one row high and a 2 x 2 countdown, as on a home screen.
                        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                        val bar = SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 2, isMaterialYou = material, prefs = prefs)
                            .getValue(SearchWidgetProvider.EXPANDED_WIDTH).apply(activity, column)
                        column.addView(bar, LinearLayout.LayoutParams(dp(380), dp(80)))
                        val (pill, _) = cell(CountdownSize.PILL, 380, 80)
                        column.addView(pill, LinearLayout.LayoutParams(dp(380), dp(80)).apply { topMargin = dp(8) })
                        val (tile, _) = cell(CountdownSize.TILE, 180, 190)
                        column.addView(tile, LinearLayout.LayoutParams(dp(180), dp(190)).apply { topMargin = dp(16) })
                        column to FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    else -> cell(CountdownSize.TILE, 180, 190)
                }
                host.addView(widget, params)
                activity.setContentView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            shadowOf(android.os.Looper.getMainLooper()).idle()
            host.captureRoboImage(
                filePath = "src/test/screenshots/widget_render/countdown_${case}_${if (material) "material" else "system"}_${if (dark) "dark" else "light"}.png",
                roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
            )
        }
    }

    companion object {
        /** Each case in the designs it is recorded in. */
        private val CASES = listOf(
            "tile" to listOf(false, true),
            "pill" to listOf(false, true),
            "family" to listOf(false, true),
            "wide" to listOf(true),
            "large" to listOf(false),
            "today" to listOf(true),
            "setup" to listOf(false),
            "bigtext" to listOf(true)
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} material={1} dark={2}")
        fun parameters(): List<Array<Any>> = CASES.flatMap { (case, designs) ->
            designs.flatMap { material -> listOf(arrayOf<Any>(case, material, false), arrayOf<Any>(case, material, true)) }
        }
    }
}
