package com.pixel.intelligentsearch

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.feature.search.FakeDeviceSearchSources
import com.pixel.intelligentsearch.feature.search.SearchActivity
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
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import javax.inject.Singleton

/**
 * The overlay keeps itself out of Recents screenshots, so when its Recents card's live preview ends (Home pressed in
 * Recents) Android draws the card from the task's colors. Left to Material's theme that was a black card under a
 * lavender status bar, the "lavender header" seen on the Pixel 11.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class OverlayRecentsCardTest {

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

    private fun Activity.recentsCard(): ActivityManager.TaskDescription =
        Activity::class.java.getDeclaredField("mTaskDescription").apply { isAccessible = true }.get(this) as ActivityManager.TaskDescription

    @Test
    fun theRecentsCardHasNoLavenderHeaderAndIsntBlack() {
        val controller = Robolectric.buildActivity(SearchActivity::class.java, Intent(context, SearchActivity::class.java)).setup()
        val card = controller.get().recentsCard()
        assertEquals("no status bar color drawn on the card", 0, android.graphics.Color.alpha(card.statusBarColor))
        assertEquals("no navigation bar color drawn on the card", 0, android.graphics.Color.alpha(card.navigationBarColor))
        assertNotEquals("unset, Android draws the card black", 0, card.backgroundColor)
        controller.pause().stop().destroy()
    }

    @Test
    fun theHomeScreenBarsOverlayThemeKeepsTheLavenderOffToo() {
        for (theme in listOf(R.style.Theme_IntelligentSearch_Overlay_WidgetLaunch, R.style.Theme_IntelligentSearch_Overlay_Launcher)) {
            val a = context.obtainStyledAttributes(theme, intArrayOf(android.R.attr.statusBarColor, android.R.attr.navigationBarColor))
            assertEquals(0, android.graphics.Color.alpha(a.getColor(0, -1)))
            assertEquals(0, android.graphics.Color.alpha(a.getColor(1, -1)))
            a.recycle()
        }
    }
}
