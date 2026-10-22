package com.pixel.intelligentsearch.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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

/** The card that answers a unit or currency conversion. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ConversionOneBoxTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    @Test
    fun aCurrencyAnswerIsHeadedCurrencyConversion() {
        compose.setContent {
            ConversionOneBox(
                conversionText = "100 USD ≈ 92 EUR",
                ratesLabel = "Built-in rates",
                header = "CURRENCY CONVERSION",
                clipLabel = "Currency Conversion"
            )
        }
        compose.onNodeWithText("CURRENCY CONVERSION").assertExists()
        compose.onNodeWithText("UNIT CONVERSION").assertDoesNotExist()
        compose.onNodeWithContentDescription("Copy, Currency Conversion").assertExists()
    }

    @Test
    fun aUnitAnswerKeepsItsHeader() {
        compose.setContent { ConversionOneBox(conversionText = "1 km = 1,000 m") }
        compose.onNodeWithText("UNIT CONVERSION").assertExists()
        compose.onNodeWithContentDescription("Copy, Unit Conversion").assertExists()
    }
}
