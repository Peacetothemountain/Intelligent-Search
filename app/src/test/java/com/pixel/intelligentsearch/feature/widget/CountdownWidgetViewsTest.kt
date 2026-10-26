package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZonedDateTime

/** The countdown widget's words and colours, and what tapping it does. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class CountdownWidgetViewsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("countdown_views_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private val defaultNow = CountdownWidgetProvider.now
    private val halloween = CountdownEvent("Halloween", LocalDate.of(2026, 10, 31), yearly = true)

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

    @Test
    fun theWordsFollowTheDaysLeft() {
        assertEquals("9" to "days to Halloween", texts(halloween, "2026-10-22"))
        assertEquals("1" to "day to Halloween", texts(halloween, "2026-10-30"))
        assertEquals("Today" to "is Halloween", texts(halloween, "2026-10-31"))
        val launch = CountdownEvent("Launch", LocalDate.of(2026, 10, 1), yearly = false)
        assertEquals("21" to "days since Launch", texts(launch, "2026-10-22"))
        assertEquals("1" to "day since Launch", texts(launch, "2026-10-02"))
        assertEquals("Countdown" to "Tap to choose a date", texts(null, "2026-10-22"))
    }

    @Test
    fun itIsDrawnInTheThemesColoursLightOrDark() {
        CountdownWidgetProvider.now = { ZonedDateTime.parse("2026-10-22T09:00-04:00[America/New_York]") }
        for (dark in listOf(false, true)) {
            prefs.edit().putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light").commit()
            val colors = DynamicThemeDetector.resolveWidgetColors(context, dark)
            val widget = CountdownWidgetProvider.buildViews(context, 4, halloween, prefs).apply(activity, FrameLayout(activity))
            assertEquals(ColorStateList.valueOf(colors.surfaceContainer), widget.findViewById<ImageView>(R.id.countdown_background).imageTintList)
            val number = widget.findViewById<TextView>(R.id.countdown_number)
            assertEquals("9", number.text.toString())
            assertEquals(colors.primary, number.currentTextColor)
            assertEquals(colors.onSurface, widget.findViewById<TextView>(R.id.countdown_caption).currentTextColor)
            assertEquals("9 days to Halloween, search for Halloween", widget.findViewById<View>(android.R.id.background).contentDescription)
        }
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
        val set = CountdownWidgetProvider.buildViews(context, 4, halloween, prefs).apply(activity, FrameLayout(activity))
        set.findViewById<View>(android.R.id.background).performClick()
        val search = started()
        assertEquals(WidgetActivity::class.java.name, search.component?.className)
        assertEquals("Halloween", search.getStringExtra(SearchManager.QUERY))

        val unset = CountdownWidgetProvider.buildViews(context, 5, null, prefs).apply(activity, FrameLayout(activity))
        unset.findViewById<View>(android.R.id.background).performClick()
        val setup = started()
        assertEquals(CountdownConfigActivity::class.java.name, setup.component?.className)
        assertEquals(5, setup.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }
}
