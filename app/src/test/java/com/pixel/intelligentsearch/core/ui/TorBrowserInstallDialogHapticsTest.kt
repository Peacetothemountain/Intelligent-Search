package com.pixel.intelligentsearch.core.ui

import android.view.HapticFeedbackConstants
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.pixel.intelligentsearch.core.haptics.RecordingViewHaptics
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

/** The Tor Browser dialog's buttons play the app's one button haptic, as every other button does. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, shadows = [RecordingViewHaptics::class])
class TorBrowserInstallDialogHapticsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun inject() {
        hilt.inject()
        RecordingViewHaptics.played.clear()
    }

    @Test
    fun closePlaysTheButtonHaptic() {
        var dismissed = false
        compose.setContent { TorBrowserInstallDialog(onDismiss = { dismissed = true }) }
        compose.onNodeWithContentDescription("Close Tor Browser required dialog").performClick()
        compose.waitForIdle()
        assertEquals(true, dismissed)
        assertEquals(listOf(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE), RecordingViewHaptics.played)
    }
}
