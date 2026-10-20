package com.pixel.intelligentsearch.feature.search

import android.app.Application
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The contact buttons as TalkBack meets them: each one named for who and how, and each one opening its app. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ContactActionButtonsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() {
        hilt.inject()
        ContactActionTargets.cached = null
    }

    @After fun clearCache() {
        ContactActionTargets.cached = null
    }

    private val alex = ContactItem("Alex", "+14155552671", "", email = "alex@example.com")
    private val targets = ContactActionTargets(setOf(ContactActionKind.CALL, ContactActionKind.MESSAGE, ContactActionKind.WHATSAPP), countryIso = "US")

    @Test
    fun everyRowButtonIsNamedAndOpensItsApp() {
        var launched = 0
        compose.setContent {
            ContactActionButtons(ContactActions.forContact(alex, targets), "system_default") { launched++ }
        }
        for (description in listOf("Call Alex", "Message Alex", "Message Alex on WhatsApp")) {
            compose.onNodeWithContentDescription(description).assertHasClickAction()
        }
        // Email isn't available on this device, so there is no button for it.
        compose.onAllNodesWithContentDescription("Email Alex").assertCountEquals(0)

        compose.onNodeWithContentDescription("Message Alex on WhatsApp").performClick()
        compose.waitForIdle()
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://wa.me/14155552671", started.dataString)
        assertEquals("com.whatsapp", started.`package`)
        assertEquals(1, launched)
    }

    @Test
    fun bestMatchChipsReadTheirFullDescription() {
        compose.setContent {
            ContactActionChips(ContactActions.forContact(alex, targets), "system_default") { }
        }
        compose.onNodeWithContentDescription("Message Alex on WhatsApp").assertHasClickAction()
        // The chip's visible label is not read on its own as well.
        val nodes = compose.onAllNodes(hasContentDescription("Call Alex")).fetchSemanticsNodes()
        assertTrue(nodes.size == 1)
    }

    @Test
    fun bestMatchChipsTickOnPressAndClickOnReleaseLikeTheCardsRow() {
        lateinit var host: View
        compose.setContent {
            host = LocalView.current
            ContactActionChips(ContactActions.forContact(alex, targets), "system_default") { }
        }
        val chip = compose.onNodeWithContentDescription("Call Alex")
        chip.performTouchInput { down(center) }
        // A press inside a scrolling row shows only after the tap delay.
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        // No vibrator primitives under Robolectric, so the haptics fall back to the view's own feedback.
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, shadowOf(host).lastHapticFeedbackPerformed())
        chip.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, shadowOf(host).lastHapticFeedbackPerformed())
    }

    @Test
    fun aBestMatchChipClearsFocusAndHidesTheKeyboardLikeTheCardsRow() {
        val calls = mutableListOf<String>()
        val focus = object : FocusManager {
            override fun clearFocus(force: Boolean) { calls += "clearFocus(force=$force)" }
            override fun moveFocus(focusDirection: FocusDirection) = false
        }
        val keyboard = object : SoftwareKeyboardController {
            override fun show() { calls += "show" }
            override fun hide() { calls += "hide" }
        }
        compose.setContent {
            CompositionLocalProvider(LocalFocusManager provides focus, LocalSoftwareKeyboardController provides keyboard) {
                ContactActionChips(ContactActions.forContact(alex, targets), "system_default") { calls += "launched" }
            }
        }
        compose.onNodeWithContentDescription("Message Alex").performClick()
        compose.waitForIdle()
        assertEquals(listOf("launched", "clearFocus(force=true)", "hide"), calls)
    }

    @Test
    fun aLaterOverlayHasTheLastFoundButtonsFromItsFirstFrame() {
        val earlier = ContactActionTargets(setOf(ContactActionKind.CALL, ContactActionKind.SIGNAL), countryIso = "GB")
        ContactActionTargets.cached = earlier
        val frames = mutableListOf<ContactActionTargets>()
        compose.setContent { frames += rememberContactActionTargets(enabled = true) }
        assertEquals(earlier, frames.first())
        // Then the check made on resume replaces it, for this overlay and the next.
        val now = ContactActionTargets.resolve(ApplicationProvider.getApplicationContext())
        compose.waitUntil(5_000) { frames.last() == now }
        assertEquals(now, ContactActionTargets.cached)
    }

    @Test
    fun withTheSettingOffNothingIsLookedUpOrCached() {
        ContactActionTargets.cached = null
        compose.setContent { rememberContactActionTargets(enabled = false) }
        compose.waitForIdle()
        assertNull(ContactActionTargets.cached)
    }

    @Test
    fun withTheSettingOffThereAreNoButtons() {
        compose.setContent {
            ContactActionButtons(ContactActions.forContact(alex, targets, enabled = false), "system_default") { }
        }
        compose.onAllNodesWithContentDescription("Alex", substring = true).assertCountEquals(0)
    }
}
