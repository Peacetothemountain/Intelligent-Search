package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.di.AppModule
import com.pixel.intelligentsearch.di.ApplicationScope
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Leaving Settings redraws the widgets only when a setting changed since they were last drawn. */
@HiltAndroidTest
@UninstallModules(AppModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SettingsActivityWidgetUpdateTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private var updates = 0

    @BindValue @JvmField
    val widgetUpdater: WidgetUpdater = WidgetUpdater { updates++ }

    @BindValue @JvmField @ApplicationScope
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        // A finished tutorial keeps a launcher start on Settings instead of forwarding to the search screen.
        prefs.edit().putBoolean(SettingsManager.TUTORIAL_COMPLETED.name, true).commit()
        com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations()
    }

    @Test
    fun pausingWithNothingChangedLeavesTheWidgetsAlone() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.moveToState(Lifecycle.State.STARTED)

            assertEquals(0, updates)
        }
    }

    @Test
    fun pausingAfterAWidgetSettingChangedRedrawsThemOnce() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            prefs.edit().putString("widget_system_subtheme", "Dark").commit()
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.moveToState(Lifecycle.State.STARTED)

            assertEquals(1, updates)
        }
    }
}
