package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
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
import org.robolectric.annotation.GraphicsMode

/**
 * Slider titles share the settings row title style, fit on one line at the default text size, and use Title Case.
 * Text is measured with real fonts (native graphics), as on a phone.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class SettingsTitleLayoutTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    @Test
    fun thePriorityTitleFitsOnOneLine() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { ContactSearchScreen(prefs) {} } }
        compose.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Contact Priority Weight", substring = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        // The title leaves room for its reset button instead of pushing it out of the row.
        compose.onAllNodesWithContentDescription("Reset", substring = true).onLast().assertWidthIsAtLeast(32.dp)
    }

    /** Values sit at the title's end, as every SettingsSliderRow shows them, not in the title ("Results: 5"). */
    @Test
    fun sliderValuesSitApartFromTheirTitles() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { ContactSearchScreen(prefs) {} } }
        compose.waitForIdle()
        compose.onNodeWithText("Contact Results", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("5", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Contact Priority Weight", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Medium", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Results:", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Weight:", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun appSearchHeadersUseTitleCase() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppSearchScreen(prefs, {}, {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Quick Panel Apps").assertExists()
        compose.onNodeWithText("Results Preferences").assertExists()
        compose.onNodeWithText("Max Quick Panel Apps").assertExists()
    }
}
