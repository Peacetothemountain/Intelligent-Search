package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.CountdownEventStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

/**
 * The launcher places a countdown widget only when its setup answers RESULT_OK with the widget's id; backing out
 * answers RESULT_CANCELED, and the launcher removes the widget it was placing.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class CountdownSetupResultTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = SettingsManager.storedPrefs(context)
    private val defaultNow = CountdownWidgetProvider.now

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        CountdownWidgetProvider.now = { ZonedDateTime.parse("2026-10-22T09:00-04:00[America/New_York]") }
    }

    @After fun restoreClock() {
        CountdownWidgetProvider.now = defaultNow
    }

    private fun setUpWidget(id: Int): ActivityScenario<CountdownConfigActivity> = ActivityScenario.launchActivityForResult(
        Intent(context, CountdownConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    )

    @Test
    fun savingAnswersOkWithTheWidgetsIdAndStoresTheEvent() {
        val scenario = setUpWidget(31)
        compose.onNodeWithText("Event Name").performTextInput("Launch Day")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(31, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals("Launch Day", CountdownEventStore(prefs).get(31)?.title)
    }

    @Test
    fun backingOutAnswersCanceledWithTheWidgetsId() {
        val scenario = setUpWidget(32)
        compose.onNodeWithText("Countdown").assertExists()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertEquals(32, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(null, CountdownEventStore(prefs).get(32))
    }
}
