package com.pixel.intelligentsearch.feature.search

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
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

    @Before fun inject() = hilt.inject()

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
    fun withTheSettingOffThereAreNoButtons() {
        compose.setContent {
            ContactActionButtons(ContactActions.forContact(alex, targets, enabled = false), "system_default") { }
        }
        compose.onAllNodesWithContentDescription("Alex", substring = true).assertCountEquals(0)
    }
}
