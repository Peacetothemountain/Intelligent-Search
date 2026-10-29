package com.pixel.intelligentsearch.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pixel.intelligentsearch.screenshots.confinedEffects
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
import org.robolectric.shadows.ShadowToast

/**
 * The Copy chip on answer cards. Android 12 has no clipboard preview of its own, so the chip confirms with a toast
 * that names what was copied.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [32])
class AnimatedCopyChipTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    @Test
    fun copyingSaysWhatWasCopied() {
        compose.setContent { MathResultOneBox(expression = "2+2", result = "4") }

        compose.onNodeWithText("Copy Result").performClick()
        compose.waitForIdle()

        assertEquals("Calculation copied", ShadowToast.getTextOfLatestToast())
        compose.onNodeWithContentDescription("Calculation copied").assertExists()
    }
}
