package com.pixel.intelligentsearch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.feature.search.SelectedTextSearchActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The real [App], with its production Hilt graph, and the work its start-up does once the user is unlocked. */
@RunWith(RobolectricTestRunner::class)
@Config(application = App::class)
class AppStartupTest {

    private val app get() = ApplicationProvider.getApplicationContext<App>()

    /** Waits for the background start-up work [App.onCreate] launched, letting main-thread work it posts run too. */
    private fun awaitStartup() {
        val work: List<Job> = app.appScope.coroutineContext.job.children.toList()
        val deadline = System.currentTimeMillis() + 10_000
        while (work.any { it.isActive } && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("start-up work did not finish", work.none { it.isActive })
    }

    @Test
    fun startUpMatchesTheSelectedTextEntryToTheSetting() {
        val pm = app.packageManager
        val entry = ComponentName(app, SelectedTextSearchActivity::class.java)
        awaitStartup()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(entry))

        // Android keeps the entry's state outside the app's data, so after a restore or a data clear it can differ from
        // the setting. Robolectric starts the app before the test body, so start it again with the setting off.
        app.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .put(SettingsManager.SEARCH_FROM_SELECTED_TEXT, false).commit()
        app.onCreate()
        awaitStartup()

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(entry))
    }
}
