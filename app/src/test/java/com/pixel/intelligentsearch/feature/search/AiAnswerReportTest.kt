package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import com.pixel.intelligentsearch.core.ai.AiAnswerState
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.annotation.Config
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Google Play's AI-Generated Content policy: a finished AI answer can be reported from the card, which hides it at once
 * and opens an email to the developer naming the reason, the provider, the question and the answer.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AiAnswerReportTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun inject() = hilt.inject()

    private val answer = AiAnswerState.Answer(AiProvider.ANTHROPIC, "Why is the sky blue?", "Rayleigh scattering.", done = true)

    @Test
    fun reportingHidesTheAnswerAndEmailsTheDeveloper() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AiAnswerCard(answer, model = "claude-test") {} } }
        compose.onNodeWithText("Rayleigh scattering.").assertExists()

        compose.onNodeWithText("Report").performClick()
        compose.onNodeWithText("Inaccurate or Misleading").performClick()
        compose.onNodeWithText("Send Report").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Rayleigh scattering.").assertDoesNotExist()
        compose.onNodeWithText("You reported this answer, so it's hidden.").assertExists()
        compose.onNodeWithText("Copy").assertDoesNotExist()

        val sent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, sent.action)
        assertEquals("mailto", sent.data?.scheme)
        assertEquals(AI_REPORT_ADDRESS, sent.data?.schemeSpecificPart)
        assertEquals("AI Answer Report: Inaccurate or Misleading", sent.getStringExtra(Intent.EXTRA_SUBJECT))
        val body = sent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(body.contains("Why is the sky blue?"))
        assertTrue(body.contains("Rayleigh scattering."))
        assertTrue(body.contains("claude-test"))
    }

    @Test
    fun cancellingTheReportKeepsTheAnswer() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AiAnswerCard(answer, model = "claude-test") {} } }
        compose.onNodeWithText("Report").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Rayleigh scattering.").assertExists()
        assertEquals(null, shadowOf(compose.activity).nextStartedActivity)
    }

    @Test
    fun anAnswerStillStreamingCantBeReportedYet() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { AiAnswerCard(answer.copy(done = false), model = "claude-test") {} }
        }
        compose.onNodeWithText("Report").assertDoesNotExist()
    }
}
