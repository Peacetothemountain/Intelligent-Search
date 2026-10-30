package com.pixel.intelligentsearch.core.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pixel.intelligentsearch.App
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.feature.widget.WidgetDoodles
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * After a reboot with a PIN, Android can start the app (the assistant service, the widgets) before the user unlocks,
 * when WorkManager's storage can't be opened: WorkManager starting then crashed the app. It starts only when asked for,
 * the widgets don't ask for it before the unlock, and their work is arranged once the user has unlocked.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LockedBootWorkTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val workManager get() = WorkManager.getInstance(context)

    private fun setUnlocked(unlocked: Boolean) =
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(unlocked)

    private fun pending(work: String) =
        workManager.getWorkInfosForUniqueWork(work).get().filter { it.state == WorkInfo.State.ENQUEUED }

    @Before fun setUp() {
        hilt.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After fun unlock() = setUnlocked(true)

    @Test
    fun workManagerDoesNotStartWithTheProcess() {
        val startup = context.packageManager.getProviderInfo(
            ComponentName(context, "androidx.startup.InitializationProvider"), PackageManager.GET_META_DATA
        )
        assertFalse(startup.metaData?.containsKey("androidx.work.WorkManagerInitializer") == true)
    }

    @Test
    fun aWorkManagerThatCantStartIsLoggedNotACrash() {
        assertNotNull(App().workManagerConfiguration.initializationExceptionHandler)
    }

    @Test
    fun theWidgetsArrangeNoWorkBeforeTheUnlock() {
        setUnlocked(false)

        // What the widgets do when Android draws them, or the clock or language changes, before the unlock.
        SearchWidgetProvider().onReceive(context, Intent(Intent.ACTION_LOCALE_CHANGED))
        WidgetDoodles.scheduleNextChange(context)
        CountdownWidgetProvider.scheduleMidnight(context)
        CountdownWidgetProvider().onDisabled(context)

        assertEquals(emptyList<WorkInfo>(), pending("widget_doodle_change"))
        assertEquals(emptyList<WorkInfo>(), pending("countdown_widget_midnight"))
    }

    @Test
    fun theWidgetsWorkIsArrangedOnceUnlocked() {
        setUnlocked(false)
        WidgetDoodles.scheduleNextChange(context)
        CountdownWidgetProvider.scheduleMidnight(context)

        setUnlocked(true)
        WidgetDoodles.scheduleNextChange(context)
        CountdownWidgetProvider.scheduleMidnight(context)

        assertTrue(pending("widget_doodle_change").isNotEmpty())
        assertTrue(pending("countdown_widget_midnight").isNotEmpty())
    }
}
