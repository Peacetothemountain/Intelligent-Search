package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
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
 * Adding the widget to the Home screen with unsaved changes asks first: the launcher previews and places the saved
 * design, so the editor's design is saved (and only then added) when the user says so, and nothing happens otherwise.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class SaveBeforePinDialogTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun inject() = hilt.inject()

    private var saved = 0
    private var dismissed = 0

    private fun show() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                SaveBeforePinDialog(onSaveAndAdd = { saved++ }, onDismiss = { dismissed++ })
            }
        }
    }

    @Test
    fun saveAndAddSavesThenAdds() {
        show()
        compose.onNodeWithText("Save and Add to Home Screen?").assertExists()
        compose.onNodeWithText("Save and Add").performClick()
        assertEquals(1, saved)
        assertEquals(0, dismissed)
    }

    @Test
    fun cancelLeavesTheDesignUnsaved() {
        show()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, saved)
        assertEquals(1, dismissed)
    }
}
