package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Search from Selected Text is built in, so Launch Portal no longer offers a switch for it. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SearchFromSelectedTextSettingTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    @Test
    fun launchPortalHasNoSelectedTextSwitch() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { LaunchPortalScreen(prefs) {} } }
        compose.onNodeWithText("Home Screen Shortcut").assertIsDisplayed()
        compose.onAllNodesWithText("Search from Selected Text").assertCountEquals(0)
    }
}
