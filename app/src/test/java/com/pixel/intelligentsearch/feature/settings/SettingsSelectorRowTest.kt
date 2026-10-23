package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** The shared rows every settings page uses for a few choices and for information only. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SettingsSelectorRowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    @Test
    fun aChoiceIsSelectedFromTheRowsSelector() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                var side by remember { mutableStateOf("Left") }
                SettingsCard {
                    SettingsSelectorRow(
                        title = "Edge", subtitle = "The Side of the Screen You Swipe In From.", icon = Icons.Outlined.Swipe,
                        options = listOf("Left", "Right"), selected = side, onSelect = { side = it }, label = { it },
                        showDivider = false
                    )
                }
            }
        }
        compose.onNodeWithText("Edge").assertExists()
        compose.onNodeWithText("Left").assertIsSelected()
        compose.onNodeWithText("Right").performClick()
        compose.onNodeWithText("Right").assertIsSelected()
    }

    @Test
    fun anInfoRowHasNothingToTap() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                SettingsCard(lit = false) {
                    SettingsInfoRow(title = "Privacy", subtitle = "Nothing Else Is Sent.", icon = Icons.Outlined.Info, showDivider = false)
                }
            }
        }
        // Read as one stop, and offering no click.
        compose.onNode(hasText("Privacy") and hasText("Nothing Else Is Sent.")).assertExists()
        compose.onNode(hasText("Privacy") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertDoesNotExist()
    }
}
