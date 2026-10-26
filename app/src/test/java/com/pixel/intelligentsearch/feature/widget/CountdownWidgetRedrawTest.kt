package com.pixel.intelligentsearch.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.core.data.widget.CountdownEventStore
import com.pixel.intelligentsearch.feature.settings.updateWidgets
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * A placed countdown widget is redrawn whenever the search bars are: when a design is saved in Widget Customization
 * (it is drawn in the same design) and by the midnight redraw, which finishes drawing before its work counts as done.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class CountdownWidgetRedrawTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = SettingsManager.storedPrefs(context)
    private val manager get() = AppWidgetManager.getInstance(context)
    private val widgets: ShadowAppWidgetManager get() = shadowOf(manager)
    private val widgetId = 21
    private val defaultNow = CountdownWidgetProvider.now

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        CountdownWidgetProvider.now = { ZonedDateTime.parse("2026-10-22T09:00-04:00[America/New_York]") }
        // Every update then builds a new view, so a redraw shows as a different view instance.
        widgets.setAlwaysRecreateViewsDuringUpdate(true)
        widgets.bindAppWidgetId(widgetId, ComponentName(context, CountdownWidgetProvider::class.java))
        CountdownEventStore(prefs).put(widgetId, CountdownEvent("Halloween", LocalDate.of(2026, 10, 31), yearly = true))
    }

    @After fun restoreClock() {
        CountdownWidgetProvider.now = defaultNow
    }

    /** Redraws run on a background thread; waits a while for one to replace [before]. */
    private fun viewAfterRedraw(before: View?): View {
        repeat(60) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            if (widgets.getViewFor(widgetId) !== before) return widgets.getViewFor(widgetId)
        }
        return widgets.getViewFor(widgetId)
    }

    @Test
    fun savingADesignInWidgetCustomizationRedrawsTheCountdownInIt() {
        // As Widget Customization's Save does: store the design, then redraw the widgets.
        updateWidgets(context)
        val system = viewAfterRedraw(null)
        assertEquals(View.GONE, system.findViewById<View>(R.id.countdown_outline).visibility)

        prefs.edit().putString(SettingsManager.WIDGET_THEME_STYLE.name, "Material You (Minimal)").commit()
        updateWidgets(context)
        val material = viewAfterRedraw(system)
        assertNotSame(system, material)
        val config = SearchWidgetProvider.resolveWidgetConfig(context, prefs, isMaterialYou = true)
        val rim = material.findViewById<ImageView>(R.id.countdown_outline)
        assertEquals(View.VISIBLE, rim.visibility)
        assertEquals(ColorStateList.valueOf(config.rimColorOpaque), rim.imageTintList)
        assertEquals(ColorStateList.valueOf(config.pillColorOpaque), material.findViewById<ImageView>(R.id.countdown_surface).imageTintList)
    }

    @Test
    fun theMidnightRedrawHasDrawnTheWidgetWhenItsWorkIsDone() {
        assertEquals(null, widgets.getViewFor(widgetId))
        val result = runBlocking { TestListenableWorkerBuilder<CountdownMidnightWorker>(context).build().doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
        // Drawn already: nothing was left running in the background.
        assertNotNull(widgets.getViewFor(widgetId))
    }
}
