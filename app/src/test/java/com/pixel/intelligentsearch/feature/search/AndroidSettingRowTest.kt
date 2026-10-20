package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What TalkBack hears for an Android Settings result, and that the row is one tappable target. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AndroidSettingRowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun inject() = hilt.inject()

    @Test
    fun aSettingsPageReadsAsItsNameAndSettings() {
        var clicks = 0
        val battery = AndroidSettingResult("battery", "Battery", "Settings", "battery", Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        compose.setContent { AndroidSettingRow(battery) { clicks++ } }

        compose.onNodeWithContentDescription("Battery settings").assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun anAppInfoPageReadsAsAppInfoForTheApp() {
        val chrome = AppItem("Chrome", "com.android.chrome", ColorDrawable())
        val result = AndroidSettingResult("app_info:chrome", "Chrome App Info", "Settings › Apps", "app_info", Intent(), chrome)
        compose.setContent { AndroidSettingRow(result) {} }

        compose.onNodeWithContentDescription("App info for Chrome").assertHasClickAction()
    }
}
