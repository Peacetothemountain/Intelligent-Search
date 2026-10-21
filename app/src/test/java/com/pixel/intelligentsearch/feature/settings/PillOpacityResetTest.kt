package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Resetting Search Pill Opacity puts the pill back to fully opaque, its default. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h640dp-xhdpi")
class PillOpacityResetTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun resetMakesThePillOpaque() {
        hilt.inject()
        prefs.edit().clear().put(SettingsManager.PILL_OPACITY, 30).commit()
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppearanceScreen(prefs, {}, {}) } }

        compose.onNodeWithContentDescription("Reset Search Pill Opacity").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(100, prefs[SettingsManager.PILL_OPACITY])
    }
}
