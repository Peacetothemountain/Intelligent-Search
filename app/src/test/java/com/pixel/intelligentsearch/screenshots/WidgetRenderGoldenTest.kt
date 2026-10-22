package com.pixel.intelligentsearch.screenshots

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.occasion.OccasionCalendar
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.feature.widget.WidgetExtras
import com.pixel.intelligentsearch.core.system.MediaSessionAccess
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.util.SizeF
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

/**
 * Goldens of the home-screen widget itself: the RemoteViews the provider builds at a phone's default width,
 * applied the way a launcher applies them, for each design in light and dark, with default settings, with a
 * customized design, with settings as an older version saved them (shared keys, no per-design keys), and on
 * Halloween, when the G has its doodle around it (the doodle's first frame); with a thin outline and tight corners;
 * and three rows tall with the favourite apps and what's playing above the bar.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class WidgetRenderGoldenTest(private val case: String, private val material: Boolean, private val dark: Boolean) {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Before fun inject() = hilt.inject()

    private val defaultNow = OccasionCalendar.now

    private val defaultNowPlaying = WidgetExtras.nowPlaying

    @After fun restoreClock() {
        OccasionCalendar.now = defaultNow
        WidgetExtras.nowPlaying = defaultNowPlaying
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun widget() {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit().putString(SettingsManager.THEME.name,if (dark) "Material Dark" else "Material Light")
        val p = if (material) "widget_material" else "widget_system"
        if (case.startsWith("doodle")) OccasionCalendar.now = { java.time.ZonedDateTime.parse("2026-10-31T09:00-04:00[America/New_York]") }
        when (case) {
            "custom" -> edit
                .putBoolean("${p}_design_saved", true)
                .putString("${p}_subtheme", "Custom")
                .putString("${p}_material_g_icon", "Custom G Icon")
                .putInt("${p}_custom_hue", 200)
                .putInt("${p}_custom_saturation", 60)
                .putInt("${p}_custom_lightness", 90)
                .putInt("${p}_custom_color_opacity", 100)
                .putInt("${p}_background_transparency", 10)
                .putInt("${p}_custom_g_icon_color", 0xFFB3261E.toInt())
                .putInt("${p}_custom_mic_color", 0xFF1E6FB3.toInt())
                .putInt("${p}_custom_shortcut1_color", 0xFF2E7D32.toInt())
                .putInt("${p}_custom_shortcut2_color", 0xFF6A1B9A.toInt())
                .putBoolean("${p}_custom_sync_colors", false)
                .putString("${p}_shortcut_1", "Weather")
                .putString("${p}_shortcut_2", "Translate (text)")
                .putString("${p}_shortcut_order", "mic,shortcut1,shortcut2,shortcut3")
                .putBoolean("${p}_lock_black", false)
            "legacy" -> edit
                .putString("widget_subtheme", "Custom")
                .putInt("widget_custom_hue", 30)
                .putInt("widget_custom_saturation", 80)
                .putInt("widget_custom_lightness", 95)
                .putString("widget_shortcut_1", "Song Search")
                .putString("widget_shortcut_2", "Weather")
                .putBoolean("widget_show_g_icon", true)
            // On a doodle day with doodles switched off the widget must be exactly the default one.
            "doodle_off" -> edit.putBoolean("${p}_show_doodles", false)
            // A thin outline with tight corners.
            "shape" -> edit
                .putInt("${p}_outline_thickness", 4)
                .putInt("${p}_corner_radius", 12)
            // Three rows tall: the favourite apps and what's playing above the bar.
            "extras" -> {
                // Themed (monochrome) icons, and an old-style icon the overlay draws as a Material outline: all on the
                // pill's colour in the accent.
                val apps = mapOf(
                    "com.example.mail" to context.getDrawable(com.pixel.intelligentsearch.R.mipmap.ic_launcher)!!,
                    "com.example.maps" to context.getDrawable(com.pixel.intelligentsearch.R.mipmap.ic_launcher)!!,
                    "com.example.photos" to context.getDrawable(com.pixel.intelligentsearch.R.drawable.ic_weather)!!
                )
                // Icons are cached for the process: forget any another test drew for these apps.
                com.pixel.intelligentsearch.core.ui.clearThemedIconCache()
                com.pixel.intelligentsearch.feature.widget.clearBitmapCache()
                for ((packageName, icon) in apps) {
                    val app = ComponentName(packageName, "$packageName.Main")
                    shadowOf(context.packageManager).apply {
                        addActivityIfNotPresent(app)
                        addIntentFilterForActivity(app, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
                        setApplicationIcon(packageName, icon)
                    }
                }
                WidgetExtras.nowPlaying = { MediaSessionAccess.NowPlaying("Golden Hour", "JVKE", isPlaying = true, packageName = "com.example.music") }
                edit
                    .putString("${p}_favourite_apps", apps.keys.joinToString(","))
                    .putBoolean("${p}_show_media", true)
            }
        }
        edit.commit()

        val size = if (case == "extras") SizeF(SearchWidgetProvider.STANDARD_WIDTH.width, SearchWidgetProvider.TWO_EXTRA_ROWS_HEIGHT) else SearchWidgetProvider.STANDARD_WIDTH
        val views = SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = material, prefs = prefs)
            .getValue(size)

        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            lateinit var host: FrameLayout
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                host = FrameLayout(activity).apply {
                    setBackgroundColor(if (dark) 0xFF1F1F1F.toInt() else 0xFFF1F3F4.toInt())
                    val pad = (12 * density).toInt()
                    setPadding(pad, pad, pad, pad)
                }
                host.addView(views.apply(activity, host), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                activity.setContentView(host, ViewGroup.LayoutParams((284 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            shadowOf(android.os.Looper.getMainLooper()).idle()
            host.captureRoboImage(
                filePath = "src/test/screenshots/widget_render/${if (case == "doodle_off") "default" else case}_${if (material) "material" else "system"}_${if (dark) "dark" else "light"}.png",
                roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} material={1} dark={2}")
        fun parameters(): List<Array<Any>> = listOf("default", "custom", "legacy", "doodle", "doodle_off", "shape", "extras").flatMap { case ->
            listOf(false, true).flatMap { material -> listOf(arrayOf<Any>(case, material, false), arrayOf<Any>(case, material, true)) }
        }
    }
}
