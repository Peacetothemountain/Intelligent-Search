package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.occasion.Occasion
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.widget.WidgetDoodles
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
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

/**
 * The search bar preview in widget customization never plays a doodle, even on a doodle day or while Developer
 * Settings is previewing one on the widgets; doodles are only on the widgets themselves.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WidgetPreviewHasNoDoodleTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        WidgetDoodles.preview(context, Occasion.HALLOWEEN, redraw = {})
    }

    @After fun tearDown() = WidgetDoodles.preview(context, null, redraw = {})

    @Test
    fun thePreviewSearchBarHasNoDoodle() {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        compose.setContent { IntelligentSearchTheme { WidgetSettingsScreen(prefs) {} } }
        compose.settle(steps = 5)
        assertEquals(0, doodlePlayers(compose.activity.window.decorView))
    }

    /** Doodles play in a ProgressBar (see res/layout/doodle_*); nothing else on the page is one. */
    private fun doodlePlayers(view: View): Int = when (view) {
        is ProgressBar -> 1
        is ViewGroup -> (0 until view.childCount).sumOf { doodlePlayers(view.getChildAt(it)) }
        else -> 0
    }
}
