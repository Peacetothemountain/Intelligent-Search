package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.platform.LocalContext
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
 * [ShellSelector], the tab and choice selector of every settings page: options announce whether they are selected,
 * a tap picks one, and the picked one is drawn in the indicator's content colour while the rest are muted.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
class ShellSelectorTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun inject() = hilt.inject()

    private val options = listOf("Home", "Plan", "Billing", "Shop", "Support")
    private val picks = mutableListOf<String>()
    private val colors = HashMap<String, Color>()
    private var selectedColor = Color.Unspecified
    private var mutedColor = Color.Unspecified

    private fun show(role: Role = Role.RadioButton) {
        compose.setContent {
            MaterialTheme(colorScheme = dynamicDarkColorScheme(LocalContext.current)) {
                selectedColor = MaterialTheme.colorScheme.onSecondaryContainer
                mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
                var selected by remember { mutableStateOf("Home") }
                ShellSelector(
                    options = options,
                    selected = selected,
                    onSelect = { picks += it; selected = it },
                    role = role
                ) { option, _ ->
                    colors[option] = LocalContentColor.current
                    Text(option)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aTapSelectsTheOptionAndAnnouncesIt() {
        show()
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("Billing").assertIsNotSelected()

        compose.onNodeWithText("Billing").performClick()
        compose.waitForIdle()

        assertEquals(listOf("Billing"), picks)
        compose.onNodeWithText("Billing").assertIsSelected()
        compose.onNodeWithText("Home").assertIsNotSelected()
    }

    @Test
    fun theSelectedOptionIsInTheIndicatorsContentColourAndTheRestAreMuted() {
        show()
        compose.onNodeWithText("Shop").performClick()
        compose.waitForIdle()

        assertEquals(selectedColor, colors["Shop"])
        for (option in options - "Shop") assertEquals(option, mutedColor, colors[option])
    }

    @Test
    fun optionsTakeTheGivenRole() {
        show(Role.Tab)
        compose.onNodeWithText("Plan").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }
}
