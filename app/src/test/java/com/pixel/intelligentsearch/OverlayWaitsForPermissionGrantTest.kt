package com.pixel.intelligentsearch

import android.app.ActivityManager
import android.app.TaskInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.MessageQueue
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.system.openSettingsPageToGrant
import com.pixel.intelligentsearch.feature.search.FakeDeviceSearchSources
import com.pixel.intelligentsearch.feature.search.SearchActivity
import com.pixel.intelligentsearch.feature.search.SearchViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAppTask
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Turning on Usage Access from the screen time answer opens Android's page over the overlay, in its own task, and the
 * overlay waits behind it with what was typed instead of closing as it goes out of sight: Back from Android's page
 * comes back to the search, not to the home screen. Once back, it closes out of sight again as always.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class OverlayWaitsForPermissionGrantTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        hilt.inject()
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        // The overlay's endless animations would keep Robolectric's looper busy; nothing here is drawn.
        ShadowChoreographer.setPaused(true)
    }

    @After
    fun resumeFrames() = ShadowChoreographer.setPaused(false)

    @Test
    fun theOverlayWaitsWithTheQueryWhileUsageAccessIsTurnedOn() {
        val search = Robolectric.buildActivity(SearchActivity::class.java, Intent(context, SearchActivity::class.java)).setup()
        val activity = search.get()
        val viewModel = ViewModelProvider(activity)[SearchViewModel::class.java]
        viewModel.onQueryChanged("screen time")

        assertTrue(openSettingsPageToGrant(activity, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, opened.action)
        assertEquals("opened in the overlay's own task, so Back returns to it", 0, opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK)

        search.pause().stop()
        assertFalse("it waits behind Android's page", activity.isFinishing)
        assertEquals("screen time", viewModel.uiState.value.query)

        // Back from Android's page: the search is there, and out of sight later it closes as always.
        search.restart().resume()
        assertEquals("screen time", viewModel.uiState.value.query)
        search.pause().stop()
        assertTrue(activity.isFinishing)
        search.destroy()
    }

    @Test
    fun goingHomeFromAndroidsPageClosesTheWaitingOverlay() {
        val search = Robolectric.buildActivity(SearchActivity::class.java, Intent(context, SearchActivity::class.java)).setup()
        val activity = search.get()
        assertTrue(openSettingsPageToGrant(activity, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
        val usageAccess = ComponentName("com.android.settings", "com.android.settings.UsageAccess")
        overlayTask(activity, top = usageAccess, onScreen = true)
        search.pause().stop()
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS)
        assertFalse("it waits while Android's page is up", activity.isFinishing)

        // The screen turns off over Android's page: the user comes back to it, so the overlay still waits.
        overlayTask(activity, top = usageAccess, onScreen = false)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS)
        assertFalse("the screen going off isn't leaving", activity.isFinishing)

        // The user goes Home instead of Back: the overlay left behind would come up again later with the old search.
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS)
        assertTrue(activity.isFinishing)
        search.destroy()
    }

    @Test
    fun aPermissionPageInATaskOfItsOwnStillEndsTheWait() {
        val search = Robolectric.buildActivity(SearchActivity::class.java, Intent(context, SearchActivity::class.java)).setup()
        val activity = search.get()
        assertTrue(openSettingsPageToGrant(activity, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
        // Android's settings opened the page in a task of their own (two panes on a large screen), so the overlay's
        // task holds only the overlay, out of sight, whether the user is on that page or has gone Home.
        overlayTask(activity, top = ComponentName(context, SearchActivity::class.java), onScreen = false)
        search.pause().stop()
        ShadowLooper.idleMainLooper(1, TimeUnit.MINUTES)
        assertFalse("the user may still be turning it on", activity.isFinishing)

        // It waited, checking every second, for as long as the app lived.
        ShadowLooper.idleMainLooper(2, TimeUnit.MINUTES)
        assertTrue(activity.isFinishing)
        assertFalse(stillChecking(activity))
        search.destroy()
    }

    @Test
    fun stillOnAndroidsPageItKeepsWaitingPastTheGiveUpTime() {
        val search = Robolectric.buildActivity(SearchActivity::class.java, Intent(context, SearchActivity::class.java)).setup()
        val activity = search.get()
        assertTrue(openSettingsPageToGrant(activity, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
        overlayTask(activity, top = ComponentName("com.android.settings", "com.android.settings.UsageAccess"), onScreen = true)
        search.pause().stop()
        ShadowLooper.idleMainLooper(5, TimeUnit.MINUTES)
        assertFalse("the user is still on Android's page", activity.isFinishing)
        search.destroy()
    }

    /** Android 12 can't say whether a task is in sight, so a check every second could never decide anything. */
    @Test
    @Config(sdk = [31])
    fun onAndroid12TheOverlayChecksOnceAndGivesUpInTheEnd() {
        // Android 12 checks androidx's receiver permission against the test manifest.
        shadowOf(context as android.app.Application).grantPermissions("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val search = Robolectric.buildActivity(SearchActivity::class.java, Intent(context, SearchActivity::class.java)).setup()
        val activity = search.get()
        assertTrue(openSettingsPageToGrant(activity, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
        search.pause().stop()
        val nextCheck = nextCheckInMs(activity)
        assertTrue("the next check is in $nextCheck ms; it ran every second for good", nextCheck != null && nextCheck > 60_000)

        ShadowLooper.idleMainLooper(1, TimeUnit.MINUTES)
        assertFalse("the user may still be turning it on", activity.isFinishing)
        ShadowLooper.idleMainLooper(2, TimeUnit.MINUTES)
        assertTrue(activity.isFinishing)
        assertFalse(stillChecking(activity))
        search.destroy()
    }

    /** Whether the overlay's check (MainActivity.leftWhileWaiting) is still scheduled. */
    private fun stillChecking(activity: MainActivity): Boolean = waitCheck(activity).let { (handler, check) -> handler.hasCallbacks(check) }

    /** In how many milliseconds the overlay's check next runs, or null when it isn't scheduled. */
    private fun nextCheckInMs(activity: MainActivity): Long? {
        val check = waitCheck(activity).second
        fun field(owner: Class<*>, name: String) = owner.getDeclaredField(name).apply { isAccessible = true }
        var message = field(MessageQueue::class.java, "mMessages").get(Looper.getMainLooper().queue) as Message?
        while (message != null) {
            if (message.callback === check) return field(Message::class.java, "when").getLong(message) - SystemClock.uptimeMillis()
            message = field(Message::class.java, "next").get(message) as Message?
        }
        return null
    }

    private fun waitCheck(activity: MainActivity): Pair<Handler, Runnable> =
        MainActivity::class.java.getDeclaredField("waitWatch").apply { isAccessible = true }.get(activity) as Handler to
            MainActivity::class.java.getDeclaredField("leftWhileWaiting").apply { isAccessible = true }.get(activity) as Runnable

    /** Android's view of the overlay's task: [top] on top of it, on screen or not. */
    private fun overlayTask(activity: android.app.Activity, top: ComponentName, onScreen: Boolean) {
        val info = ActivityManager.RecentTaskInfo().apply {
            taskId = activity.taskId
            topActivity = top
        }
        TaskInfo::class.java.getDeclaredField("isVisible").apply { isAccessible = true }.setBoolean(info, onScreen)
        val task = ShadowAppTask.newInstance()
        Shadow.extract<ShadowAppTask>(task).setTaskInfo(info)
        shadowOf(context.getSystemService(ActivityManager::class.java)).setAppTasks(listOf(task))
    }
}
