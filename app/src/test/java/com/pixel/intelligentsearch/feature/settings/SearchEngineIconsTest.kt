package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
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

/** A custom engine only gets the Tor onion when it is Tor, not whenever its name happens to contain "tor". */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SearchEngineIconsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    private fun iconFor(name: String) {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { SearchAppColorfulIcon(appName = name, modifier = Modifier.size(24.dp)) }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    @Test
    fun aNameContainingTorIsNotTor() {
        iconFor("Play Store")
        compose.onNodeWithContentDescription("Custom Search").assertExists()
    }

    @Test
    fun torIsStillTor() {
        iconFor("Tor Browser")
        compose.onNodeWithContentDescription("Custom Search").assertDoesNotExist()
    }
}
