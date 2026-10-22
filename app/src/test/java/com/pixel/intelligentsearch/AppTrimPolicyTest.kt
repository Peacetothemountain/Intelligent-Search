package com.pixel.intelligentsearch

import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.Locale

/**
 * What [App] drops when the system asks for memory or the language changes. Leaving the overlay (UI hidden) must keep
 * the app list, or every reopen rescans every installed app.
 */
@RunWith(RobolectricTestRunner::class)
class AppTrimPolicyTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun installApps() {
        for (name in listOf("Notes", "Maps")) {
            val component = ComponentName("org.example.${name.lowercase()}", "org.example.${name.lowercase()}.Main")
            val pm = shadowOf(context.packageManager)
            pm.addActivityIfNotPresent(component)
            pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        SystemDataProvider.invalidateAppsCache()
    }

    @After fun forgetApps() = SystemDataProvider.invalidateAppsCache()

    private fun apps() = runBlocking { SystemDataProvider.getAllApps(context) }

    @Test
    fun hidingTheOverlayKeepsTheAppList() {
        val apps = apps()
        App().onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertSame(apps, apps())
    }

    @Test
    fun aNewLanguageReloadsTheAppListForItsLabels() {
        val app = App()
        val french = Configuration().apply { setLocales(LocaleList(Locale.FRANCE)) }
        app.onConfigurationChanged(french)
        val apps = apps()

        // Turning the phone changes the configuration but not the labels.
        app.onConfigurationChanged(Configuration(french).apply { orientation = Configuration.ORIENTATION_LANDSCAPE })
        assertSame(apps, apps())

        app.onConfigurationChanged(Configuration().apply { setLocales(LocaleList(Locale.GERMANY)) })
        assertNotSame(apps, apps())
    }
}
