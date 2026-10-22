package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
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

/** The tutorial card is legible in both app themes and TalkBack reads each step once. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class TutorialSpotlightOverlayTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    @Test
    fun stepLabelAndTextAreLegibleOnTheCardInBothThemes() {
        for ((scheme, dark) in listOf(lightColorScheme() to false, darkColorScheme() to true)) {
            val colors = tutorialCardColors(scheme, dark)
            val card = colors.container.compositeOver(scheme.background)
            assertTrue("step label, dark=$dark", contrast(colors.stepLabel, card) >= 4.5)
            assertTrue("text, dark=$dark", contrast(colors.content, card) >= 4.5)
        }
    }

    @Test
    fun advancingAStepIsReadByTheCardAloneUnderAFixedPaneTitle() {
        disableHandRolledAnimations()
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(SettingsManager.TUTORIAL_COMPLETED.name, false)
            .putInt(SettingsManager.TUTORIAL_STEP.name, 3)
            .commit()
        // With TalkBack on, an announcement through the host view reaches the accessibility manager as an event.
        val accessibility = shadowOf(ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java))
        accessibility.setEnabled(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                TutorialSpotlightOverlay(
                    prefs = prefs,
                    stepsInfo = mapOf(
                        3 to TutorialStepInfo("First", "The first step.", showArrow = false),
                        4 to TutorialStepInfo("Second", "The second step.", showArrow = false)
                    ),
                    onComplete = {}
                )
            }
        }
        compose.settle(steps = 5)
        val pane = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
        val paneTitle = pane.fetchSemanticsNode().config[SemanticsProperties.PaneTitle]

        compose.onNodeWithContentDescription("Next tutorial step").performClick()
        compose.settle(steps = 5)

        compose.onNodeWithText("Second").assertExists()
        // The card's live region reads each step; a separate announcement would read it a second time.
        val announcements = accessibility.sentAccessibilityEvents.filter { it.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT }
        assertEquals(emptyList<List<CharSequence>>(), announcements.map { it.text.toList() })
        assertEquals(paneTitle, pane.fetchSemanticsNode().config[SemanticsProperties.PaneTitle])
    }

    private fun contrast(foreground: Color, background: Color): Double =
        MaterialYouPaletteHelper.calculateContrastRatio(foreground.compositeOver(background).toArgb(), background.toArgb())
}
