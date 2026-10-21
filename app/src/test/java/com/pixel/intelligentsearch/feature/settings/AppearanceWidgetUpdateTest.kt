package com.pixel.intelligentsearch.feature.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager

/**
 * The overlay's wallpaper, blur, transparency and pill opacity never reach the home-screen widget, so changing
 * them must not rebuild every widget on each slider step; the app theme does reach it, so that still does.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class AppearanceWidgetUpdateTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val manager get() = AppWidgetManager.getInstance(context)
    private val widgets: ShadowAppWidgetManager get() = shadowOf(manager)
    private val widgetId = 7

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        // Every update then builds a new view, so a rebuild shows as a different view instance.
        widgets.setAlwaysRecreateViewsDuringUpdate(true)
        widgets.bindAppWidgetId(widgetId, ComponentName(context, SearchWidgetProvider::class.java))
        SearchWidgetProvider.updateWidgetsSync(context, manager, intArrayOf(widgetId))
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppearanceScreen(prefs, {}, {}) } }
        compose.waitForIdle()
    }

    /** Widget rebuilds run on a background thread; waits a while for one to replace [before]. */
    private fun widgetViewAfterUpdates(before: View?): View? {
        repeat(40) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            if (widgets.getViewFor(widgetId) !== before) return widgets.getViewFor(widgetId)
        }
        return widgets.getViewFor(widgetId)
    }

    @Test
    fun anOverlaySliderLeavesTheWidgetAlone() {
        val before = widgets.getViewFor(widgetId)
        assertNotNull(before)

        compose.onNodeWithContentDescription("Background Blur").performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        compose.waitForIdle()

        assertEquals(80, prefs[SettingsManager.BACKGROUND_BLUR])
        assertSame(before, widgetViewAfterUpdates(before))
    }

    @Test
    fun changingTheAppThemeRebuildsTheWidget() {
        val before = widgets.getViewFor(widgetId)
        assertNotNull(before)

        compose.onNodeWithText("App Theme").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Material Dark").performClick()
        compose.waitForIdle()

        assertNotSame(before, widgetViewAfterUpdates(before))
    }
}
