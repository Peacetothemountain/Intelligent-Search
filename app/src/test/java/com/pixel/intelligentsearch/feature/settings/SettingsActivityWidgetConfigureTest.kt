package com.pixel.intelligentsearch.feature.settings

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.di.AppModule
import com.pixel.intelligentsearch.di.ApplicationScope
import com.pixel.intelligentsearch.feature.widget.CountdownConfigActivity
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Settings is exported, so only the launcher's configure action sets a widget up: another app sending a widget id
 * without it reaches neither a widget's setup nor a "configured" answer.
 */
@HiltAndroidTest
@UninstallModules(AppModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SettingsActivityWidgetConfigureTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @BindValue @JvmField
    val widgetUpdater: WidgetUpdater = WidgetUpdater { }

    @BindValue @JvmField @ApplicationScope
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations()
        val manager = AppWidgetManager.getInstance(context)
        val countdown = AppWidgetProviderInfo().apply { provider = ComponentName(context, CountdownWidgetProvider::class.java) }
        val search = AppWidgetProviderInfo().apply { provider = ComponentName(context, SearchWidgetProvider::class.java) }
        shadowOf(manager).addInstalledProvider(countdown)
        shadowOf(manager).addInstalledProvider(search)
        shadowOf(manager).addBoundWidget(COUNTDOWN, countdown)
        shadowOf(manager).addBoundWidget(SEARCH, search)
    }

    private fun intent(widgetId: Int, configure: Boolean) = Intent(context, SettingsActivity::class.java).apply {
        if (configure) action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }

    private fun created(intent: Intent): SettingsActivity = Robolectric.buildActivity(SettingsActivity::class.java, intent).create().get()

    @Test
    fun aWidgetIdWithoutTheConfigureActionIsIgnored() {
        val search = created(intent(SEARCH, configure = false))
        assertNotEquals(Activity.RESULT_OK, shadowOf(search).resultCode)

        val countdown = created(intent(COUNTDOWN, configure = false))
        assertNotEquals(Activity.RESULT_OK, shadowOf(countdown).resultCode)
        assertFalse("no countdown setup", shadowOf(countdown).nextStartedActivity?.component?.className == CountdownConfigActivity::class.java.name)
        assertFalse(countdown.isFinishing)
    }

    @Test
    fun theLaunchersConfigureActionStillSetsTheWidgetUp() {
        val search = created(intent(SEARCH, configure = true))
        assertEquals(Activity.RESULT_OK, shadowOf(search).resultCode)
        assertEquals(SEARCH, shadowOf(search).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))

        val countdown = created(intent(COUNTDOWN, configure = true))
        val handOff = shadowOf(countdown).nextStartedActivity
        assertEquals(CountdownConfigActivity::class.java.name, handOff.component?.className)
        assertEquals(COUNTDOWN, handOff.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test
    fun onlyTheConfigureActionCarriesAWidgetId() {
        assertEquals(SEARCH, SettingsActivity.configuredWidgetId(context, intent(SEARCH, configure = true)))
        assertEquals(AppWidgetManager.INVALID_APPWIDGET_ID, SettingsActivity.configuredWidgetId(context, intent(SEARCH, configure = false)))
        assertEquals(AppWidgetManager.INVALID_APPWIDGET_ID, SettingsActivity.configuredWidgetId(context, null))
    }

    @Test
    fun theConfigureActionCountsOnlyForThisAppsBoundWidget() {
        // With the action, any id was taken: another app's widget, or one never bound, answered as configured.
        val foreign = AppWidgetProviderInfo().apply { provider = ComponentName("com.example.other", "com.example.other.Widget") }
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(FOREIGN, foreign)
        for (id in listOf(FOREIGN, UNBOUND)) {
            assertEquals("$id", AppWidgetManager.INVALID_APPWIDGET_ID, SettingsActivity.configuredWidgetId(context, intent(id, configure = true)))
            val settings = created(intent(id, configure = true))
            assertNotEquals("$id", Activity.RESULT_OK, shadowOf(settings).resultCode)
            assertFalse("$id", settings.isFinishing)
        }
    }

    private companion object {
        const val COUNTDOWN = 11
        const val SEARCH = 12
        const val FOREIGN = 13
        const val UNBOUND = 14
    }
}
