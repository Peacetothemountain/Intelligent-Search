package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Dragging the thumb towards the "on" end turns the switch on, whichever way the layout runs. */
@RunWith(RobolectricTestRunner::class)
class GoogleMessagesSwitchRtlTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun swipingLeftTurnsItOnRightToLeft() {
        assertEquals(listOf(true), changesAfterSwipe(LayoutDirection.Rtl) { swipeLeft() })
    }

    @Test
    fun swipingRightTurnsItOnLeftToRight() {
        assertEquals(listOf(true), changesAfterSwipe(LayoutDirection.Ltr) { swipeRight() })
    }

    private fun changesAfterSwipe(
        direction: LayoutDirection,
        swipe: androidx.compose.ui.test.TouchInjectionScope.() -> Unit
    ): List<Boolean> {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                GoogleMessagesSwitch(checked = false, onCheckedChange = { changes += it }, label = "Wi-Fi")
            }
        }
        compose.onNodeWithContentDescription("Wi-Fi").performTouchInput(swipe)
        compose.waitForIdle()
        return changes
    }
}
