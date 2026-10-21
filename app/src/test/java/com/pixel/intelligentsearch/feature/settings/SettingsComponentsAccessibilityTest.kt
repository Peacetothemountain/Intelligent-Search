package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What TalkBack hears from the shared settings rows, sliders, reset buttons and section headers. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class SettingsComponentsAccessibilityTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { content() } }
        compose.waitForIdle()
    }

    @Test
    fun aToggleRowIsOneSwitchThatSaysWhetherItIsOn() {
        show {
            var checked by remember { mutableStateOf(true) }
            SettingsRowToggle(
                title = "Fuzzy Search",
                subtitle = "Allow Typos When Searching for Apps.",
                isChecked = checked,
                onCheckedChange = { checked = it }
            )
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNode(isToggleable()).assertTextContains("Fuzzy Search").assertIsOn()

        compose.onNode(isToggleable()).performClick()
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun aPrioritySliderNamesItsLevelInsteadOfAPercentage() {
        prefs.edit().put(SettingsManager.SEARCH_WEIGHT_CONTACTS, 50).commit()
        show { ContactSearchScreen(prefs) {} }
        compose.onNodeWithContentDescription("Contact priority weight")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Medium"))
    }

    @Test
    fun eachResetButtonSaysWhatItResets() {
        show { AppearanceScreen(prefs, {}, {}) }
        compose.onAllNodesWithContentDescription("Reset to Default").assertCountEquals(0)
        compose.onNodeWithContentDescription("Reset Background Blur").assertExists()
    }

    @Test
    fun aDropdownDrawsItsOptionIconsAndHasNoStrayLabel() {
        show {
            SettingsDropdownRow(
                title = "Primary Search App",
                subtitle = "Google",
                options = listOf("Google", "Bing"),
                selectedOption = "Google",
                onOptionSelected = {},
                showDivider = false,
                optionLeadingIcon = { Box(Modifier.size(4.dp).testTag("lead_$it")) }
            )
        }
        compose.onAllNodesWithContentDescription("Dropdown").assertCountEquals(0)
        compose.onAllNodesWithTag("lead_Google", useUnmergedTree = true).assertCountEquals(1)

        compose.onNodeWithText("Primary Search App").performClick()
        compose.waitForIdle()
        // The current choice beside the title, and again in the open menu.
        compose.onAllNodesWithTag("lead_Google", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithTag("lead_Bing", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun sectionHeadersAreHeadings() {
        show { AppSearchScreen(prefs, {}, {}) }
        compose.onNodeWithText("App Settings").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
