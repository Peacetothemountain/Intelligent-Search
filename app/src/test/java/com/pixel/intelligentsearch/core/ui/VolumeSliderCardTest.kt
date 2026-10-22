package com.pixel.intelligentsearch.core.ui

import android.media.AudioManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack names the volume slider by the card's title, and reads the title once. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class VolumeSliderCardTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun inject() = hilt.inject()

    @Test
    fun theSliderCarriesTheTitle() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                VolumeSliderCard(
                    title = "Media volume",
                    streamType = AudioManager.STREAM_MUSIC,
                    currentLevel = 5,
                    maxLevel = 15,
                    onVolumeChange = {}
                )
            }
        }

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertContentDescriptionEquals("Media volume")
        compose.onAllNodes(hasText("Media volume") or hasContentDescription("Media volume")).assertCountEquals(1)
    }
}
