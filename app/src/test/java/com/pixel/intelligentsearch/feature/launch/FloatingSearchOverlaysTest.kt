package com.pixel.intelligentsearch.feature.launch

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** The floating button and edge swipe draw nothing without "Display over other apps", whatever their switches say. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class FloatingSearchOverlaysTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        FloatingSearchOverlays.sync(context)
    }

    @Test
    fun withoutThePermissionNeitherWindowShows() {
        ShadowSettings.setCanDrawOverlays(false)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).put(SettingsManager.EDGE_SWIPE, true).commit()
        FloatingSearchOverlays.sync(context)
        assertFalse(FloatingSearchOverlays.isButtonShowing)
        assertFalse(FloatingSearchOverlays.isStripShowing)
    }

    @Test
    fun withThePermissionEachFollowsItsSwitch() {
        ShadowSettings.setCanDrawOverlays(true)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).commit()
        FloatingSearchOverlays.sync(context)
        assertTrue(FloatingSearchOverlays.isButtonShowing)
        assertFalse(FloatingSearchOverlays.isStripShowing)

        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, false).put(SettingsManager.EDGE_SWIPE, true).commit()
        FloatingSearchOverlays.sync(context)
        assertFalse(FloatingSearchOverlays.isButtonShowing)
        assertTrue(FloatingSearchOverlays.isStripShowing)
    }

    @Test
    fun revokingThePermissionTakesThemDown() {
        ShadowSettings.setCanDrawOverlays(true)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).put(SettingsManager.EDGE_SWIPE, true).commit()
        FloatingSearchOverlays.sync(context)
        assertTrue(FloatingSearchOverlays.isButtonShowing)

        ShadowSettings.setCanDrawOverlays(false)
        FloatingSearchOverlays.sync(context)
        assertFalse(FloatingSearchOverlays.isButtonShowing)
        assertFalse(FloatingSearchOverlays.isStripShowing)
    }
}
