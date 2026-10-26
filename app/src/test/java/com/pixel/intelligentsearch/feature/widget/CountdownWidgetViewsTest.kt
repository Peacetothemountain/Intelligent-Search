package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.res.ColorStateList
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider.CountdownSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The countdown widget's words, its layout at each size, its colors (the search bar's own, from the design saved in
 * Widget Customization), and what tapping it does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class CountdownWidgetViewsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("countdown_views_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private val defaultNow = CountdownWidgetProvider.now
    private val halloween = CountdownEvent("Halloween", LocalDate.of(2026, 10, 31), yearly = true)

    @Before fun setClock() {
        prefs.edit().clear().commit()
        CountdownWidgetProvider.now = { ZonedDateTime.parse("2026-10-22T09:00-04:00[America/New_York]") }
    }

    @After fun restoreClock() {
        CountdownWidgetProvider.now = defaultNow
    }

    /**
     * The activity a tap started (the newest), wherever the launcher-side PendingIntent recorded it. A tap that opens
     * the overlay is a broadcast to WidgetLaunchReceiver, which starts it once the broadcast is delivered.
     */
    private fun started(): android.content.Intent {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return shadowOf(activity).nextStartedActivity ?: shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
    }

    private fun texts(event: CountdownEvent?, today: String) = CountdownWidgetProvider.texts(context, event, LocalDate.parse(today))

    /** The widget's views at [size], applied as a launcher shows them. */
    private fun shown(event: CountdownEvent?, size: CountdownSize, id: Int = 4): View =
        CountdownWidgetProvider.buildSizes(context, id, event, prefs).getValue(size.size).apply(activity, FrameLayout(activity))

    @Test
    fun theWordsFollowTheDaysLeft() {
        val nine = texts(halloween, "2026-10-22")
        assertEquals(listOf("9", "days", "Halloween", "Sat, Oct 31"), listOf(nine.hero, nine.unit, nine.title, nine.date))
        assertEquals("9 days until Halloween, Saturday, October 31, search for Halloween", nine.spoken)
        assertEquals("days · Halloween", CountdownWidgetProvider.caption(context, nine))

        val one = texts(halloween, "2026-10-30")
        assertEquals("1" to "day", one.hero to one.unit)

        // On the day: "Today", and the event alone one row high.
        val today = texts(halloween, "2026-10-31")
        assertEquals("Today" to "", today.hero to today.unit)
        assertEquals("Halloween", CountdownWidgetProvider.caption(context, today))
        assertEquals("Halloween is today, Saturday, October 31, search for Halloween", today.spoken)

        // A yearly event the day after counts to next year's, with its year.
        val next = texts(halloween, "2026-11-01")
        assertEquals("364" to "Sun, Oct 31, 2027", next.hero to next.date)

        // A one-off date that has passed counts the days since.
        val launch = CountdownEvent("Launch", LocalDate.of(2026, 10, 1), yearly = false)
        val since = texts(launch, "2026-10-22")
        assertEquals("21" to "days ago", since.hero to since.unit)
        assertEquals("days ago · Launch", CountdownWidgetProvider.caption(context, since))
        assertEquals("21 days since Launch, Thursday, October 1, search for Launch", since.spoken)
        assertEquals("1" to "day ago", texts(launch, "2026-10-02").let { it.hero to it.unit })

        val unset = texts(null, "2026-10-22")
        assertEquals(listOf("", "Tap to choose a date", "Countdown"), listOf(unset.hero, unset.unit, unset.title))
        assertEquals("Tap to choose a date", CountdownWidgetProvider.caption(context, unset))
    }

    @Test
    fun eachSizeHasItsLayoutAndTheLauncherPicksByTheWidgetsSize() {
        val sizes = CountdownWidgetProvider.buildSizes(context, 4, halloween, prefs)
        assertEquals(CountdownSize.entries.map { it.size }.toSet(), sizes.keys)
        assertEquals(R.layout.widget_countdown_pill, sizes.getValue(CountdownSize.PILL.size).layoutId)
        assertEquals(R.layout.widget_countdown_tile, sizes.getValue(CountdownSize.TILE.size).layoutId)
        assertEquals(R.layout.widget_countdown_wide, sizes.getValue(CountdownSize.WIDE.size).layoutId)
        assertEquals(R.layout.widget_countdown_tile, sizes.getValue(CountdownSize.LARGE.size).layoutId)

        // What the platform shows for the sizes a phone's launcher gives the widget (its own choice, as a launcher's).
        val views = RemoteViews(sizes)
        val pick = RemoteViews::class.java.getDeclaredMethod("getRemoteViewsToApply", Context::class.java, SizeF::class.java)
            .apply { isAccessible = true }
        fun at(width: Float, height: Float) = (pick.invoke(views, context, SizeF(width, height)) as RemoteViews).let { chosen ->
            CountdownSize.entries.first { sizes.getValue(it.size) === chosen }
        }
        assertEquals(CountdownSize.PILL, at(180f, 70f)) // 2 x 1
        assertEquals(CountdownSize.PILL, at(380f, 90f)) // 4 x 1
        assertEquals(CountdownSize.TILE, at(180f, 190f)) // 2 x 2
        assertEquals(CountdownSize.TILE, at(180f, 300f)) // 2 x 3
        assertEquals(CountdownSize.WIDE, at(270f, 190f)) // 3 x 2
        assertEquals(CountdownSize.WIDE, at(380f, 190f)) // 4 x 2
        assertEquals(CountdownSize.LARGE, at(270f, 300f)) // 3 x 3
        assertEquals(CountdownSize.LARGE, at(380f, 400f)) // 4 x 4
    }

    @Test
    fun eachSizeShowsWhatItHasRoomFor() {
        val pill = shown(halloween, CountdownSize.PILL)
        assertEquals("9", pill.findViewById<TextView>(R.id.countdown_hero).text.toString())
        assertEquals("days · Halloween", pill.findViewById<TextView>(R.id.countdown_caption).text.toString())

        val tile = shown(halloween, CountdownSize.TILE)
        assertEquals("Halloween", tile.findViewById<TextView>(R.id.countdown_title).text.toString())
        assertEquals("9", tile.findViewById<TextView>(R.id.countdown_hero).text.toString())
        assertEquals("days", tile.findViewById<TextView>(R.id.countdown_unit).text.toString())
        assertEquals(View.GONE, tile.findViewById<View>(R.id.countdown_date).visibility)
        for (size in listOf(CountdownSize.WIDE, CountdownSize.LARGE)) {
            val date = shown(halloween, size).findViewById<TextView>(R.id.countdown_date)
            assertEquals(size.name, View.VISIBLE, date.visibility)
            assertEquals("Sat, Oct 31", date.text.toString())
        }

        // Not set up: a calendar instead of the number, at every size.
        for (size in CountdownSize.entries) {
            val unset = shown(null, size)
            assertEquals(size.name, View.GONE, unset.findViewById<View>(R.id.countdown_hero).visibility)
            assertEquals(size.name, View.VISIBLE, unset.findViewById<View>(R.id.countdown_icon).visibility)
            assertEquals(size.name, "Countdown, tap to choose a date", unset.findViewById<View>(android.R.id.background).contentDescription)
        }
        // On the day there is no unit under "Today".
        CountdownWidgetProvider.now = { ZonedDateTime.parse("2026-10-31T09:00-04:00[America/New_York]") }
        val onTheDay = shown(halloween, CountdownSize.TILE)
        assertEquals("Today", onTheDay.findViewById<TextView>(R.id.countdown_hero).text.toString())
        assertEquals(View.GONE, onTheDay.findViewById<View>(R.id.countdown_unit).visibility)
    }

    /** The countdown's backdrop and words at [size] are the search bar's, as [SearchWidgetProvider] resolves them. */
    private fun assertDrawnInTheBarsDesign(event: CountdownEvent?, isMaterial: Boolean) {
        val config = SearchWidgetProvider.resolveWidgetConfig(context, prefs, isMaterial)
        for (size in CountdownSize.entries) {
            val widget = shown(event, size)
            val outline = widget.findViewById<ImageView>(R.id.countdown_outline)
            assertEquals(if (config.shape.outline > 0) View.VISIBLE else View.GONE, outline.visibility)
            assertEquals(ColorStateList.valueOf(config.rimColorOpaque), outline.imageTintList)
            assertEquals(ColorStateList.valueOf(config.pillColorOpaque), widget.findViewById<ImageView>(R.id.countdown_surface).imageTintList)
            assertEquals(config.backdrop.backdrop, widget.findViewById<View>(R.id.countdown_backdrop).alpha, 0.001f)
            assertEquals(config.themePColor, widget.findViewById<TextView>(R.id.countdown_hero).currentTextColor)
            val words = if (size == CountdownSize.PILL) R.id.countdown_caption else R.id.countdown_title
            assertEquals(config.plainIconTint, widget.findViewById<TextView>(words).currentTextColor)
        }
    }

    @Test
    fun itIsDrawnInTheSearchBarsSavedDesignLightOrDark() {
        for (dark in listOf(false, true)) {
            prefs.edit().putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light").commit()
            // The design last saved in Widget Customization: the System design by default, then the Material one.
            prefs.edit().remove(SettingsManager.WIDGET_THEME_STYLE.name).commit()
            assertEquals(WidgetDesign.SYSTEM, CountdownWidgetProvider.designOf(halloween, prefs))
            assertDrawnInTheBarsDesign(halloween, isMaterial = false)
            prefs.edit().putString(SettingsManager.WIDGET_THEME_STYLE.name, "Material You (Minimal)").commit()
            assertEquals(WidgetDesign.MATERIAL, CountdownWidgetProvider.designOf(halloween, prefs))
            assertDrawnInTheBarsDesign(halloween, isMaterial = true)
            // The Material design's rim is there by default, as on the bar.
            assertEquals(View.VISIBLE, shown(halloween, CountdownSize.TILE).findViewById<View>(R.id.countdown_outline).visibility)
        }
    }

    @Test
    fun aDesignChosenAtSetupWinsOverTheSearchBars() {
        prefs.edit().putString(SettingsManager.WIDGET_THEME_STYLE.name, "Material You (Minimal)").commit()
        val system = halloween.copy(design = WidgetDesign.SYSTEM)
        assertEquals(WidgetDesign.SYSTEM, CountdownWidgetProvider.designOf(system, prefs))
        assertDrawnInTheBarsDesign(system, isMaterial = false)
    }

    @Test
    fun aCustomizedDesignReachesTheCountdownAsItReachesTheBar() {
        val defaultSurface = shown(halloween, CountdownSize.TILE).findViewById<ImageView>(R.id.countdown_surface).imageTintList
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_subtheme", "Custom")
            .putInt("widget_system_custom_hue", 200)
            .putInt("widget_system_custom_saturation", 60)
            .putInt("widget_system_custom_lightness", 90)
            .putInt("widget_system_custom_color_opacity", 100)
            .putInt("widget_system_background_transparency", 30)
            .putInt(SettingsManager.WIDGET_SYSTEM_OUTLINE_THICKNESS.name, 4)
            .putInt(SettingsManager.WIDGET_SYSTEM_CORNER_RADIUS.name, 12)
            .commit()
        assertDrawnInTheBarsDesign(halloween, isMaterial = false)
        val tile = shown(halloween, CountdownSize.TILE)
        assertNotEquals(defaultSurface, tile.findViewById<ImageView>(R.id.countdown_surface).imageTintList)
        // The bar's own pill is the same color: the two are one family.
        val bar = SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = false, prefs = prefs)
            .getValue(SearchWidgetProvider.STANDARD_WIDTH).apply(activity, FrameLayout(activity))
        assertEquals(
            bar.findViewById<ImageView>(R.id.widget_pill_background).imageTintList,
            tile.findViewById<ImageView>(R.id.countdown_surface).imageTintList
        )
        assertEquals(0.7f, tile.findViewById<View>(R.id.countdown_backdrop).alpha, 0.001f)
        assertEquals(View.VISIBLE, tile.findViewById<View>(R.id.countdown_outline).visibility)
    }

    /** The launcher sets every widget up through SettingsActivity, which hands a countdown widget on to its own screen. */
    @Test
    fun aCountdownWidgetsSetupIsHandedOnWithItsResult() {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val countdown = android.appwidget.AppWidgetProviderInfo().apply {
            provider = android.content.ComponentName(context, CountdownWidgetProvider::class.java)
        }
        val search = android.appwidget.AppWidgetProviderInfo().apply {
            provider = android.content.ComponentName(context, SearchWidgetProvider::class.java)
        }
        shadowOf(manager).addInstalledProvider(countdown)
        shadowOf(manager).addInstalledProvider(search)
        shadowOf(manager).addBoundWidget(11, countdown)
        shadowOf(manager).addBoundWidget(12, search)

        assertEquals(true, CountdownConfigActivity.isCountdownWidget(context, 11))
        assertEquals(false, CountdownConfigActivity.isCountdownWidget(context, 12))
        assertEquals(false, CountdownConfigActivity.isCountdownWidget(context, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID))

        val handOff = CountdownConfigActivity.setupIntent(context, 11)
        assertEquals(CountdownConfigActivity::class.java.name, handOff.component?.className)
        assertEquals(11, handOff.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT, handOff.flags and android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT)
    }

    @Test
    fun tappingSearchesForTheEventOrSetsTheWidgetUp() {
        prefs.edit().putBoolean(SettingsManager.SEARCH_OVERLAY_ENABLED.name, true).commit()
        for (size in CountdownSize.entries) {
            shown(halloween, size).findViewById<View>(android.R.id.background).performClick()
            val search = started()
            assertEquals(size.name, WidgetActivity::class.java.name, search.component?.className)
            assertEquals(size.name, "Halloween", search.getStringExtra(SearchManager.QUERY))
        }

        shown(null, CountdownSize.TILE, id = 5).findViewById<View>(android.R.id.background).performClick()
        val setup = started()
        assertEquals(CountdownConfigActivity::class.java.name, setup.component?.className)
        assertEquals(5, setup.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }
}
