package com.pixel.intelligentsearch.core.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** One way to reach Google Lens and the weather app, wherever the search, widget or notification hands off. */
@RunWith(RobolectricTestRunner::class)
class GoogleAppsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun install(packageName: String, activity: String, filter: IntentFilter?) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
        })
        val component = ComponentName(packageName, activity)
        pm.addActivityIfNotPresent(component)
        filter?.let { pm.addIntentFilterForActivity(component, it) }
    }

    private val launcher get() = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

    @Test
    fun withoutLensOrTheGoogleAppThereIsNoLens() {
        assertNull(GoogleApps.lensIntent(context))
        assertFalse(GoogleApps.isLensAvailable(context))
    }

    @Test
    fun theLensAppIsPreferredAndCanOpenTranslating() {
        install(GoogleApps.GOOGLE_APP, GoogleApps.LENS_DEEPLINK_ACTIVITY, null)
        install(GoogleApps.LENS_APP, "${GoogleApps.LENS_APP}.MainActivity", launcher)
        val lens = GoogleApps.lensIntent(context)!!
        assertEquals(GoogleApps.LENS_APP, lens.component?.packageName)
        assertTrue(lens.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("translate", GoogleApps.lensIntent(context, translate = true)?.getStringExtra("lens_mode"))
        assertNull(lens.getStringExtra("lens_mode"))
    }

    @Test
    fun withoutTheLensAppTheGoogleAppsLensOpens() {
        install(GoogleApps.GOOGLE_APP, GoogleApps.LENS_DEEPLINK_ACTIVITY, null)
        assertEquals(ComponentName(GoogleApps.GOOGLE_APP, GoogleApps.LENS_DEEPLINK_ACTIVITY), GoogleApps.lensIntent(context)?.component)
        assertTrue(GoogleApps.isLensAvailable(context))
    }

    @Test
    fun theWeatherAppIsPixelWeatherElseTheGoogleAppsWeather() {
        assertNull(GoogleApps.weatherIntent(context))
        install(GoogleApps.GOOGLE_APP, GoogleApps.WEATHER_ACTIVITY, IntentFilter(Intent.ACTION_VIEW))
        assertEquals(GoogleApps.WEATHER_ACTIVITY, GoogleApps.weatherIntent(context)?.component?.className)
        install(GoogleApps.WEATHER_APP, "${GoogleApps.WEATHER_APP}.Main", launcher)
        assertEquals(GoogleApps.WEATHER_APP, GoogleApps.weatherIntent(context)?.component?.packageName)
    }
}
