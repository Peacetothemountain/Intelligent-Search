package com.pixel.intelligentsearch.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.voice.VoiceAssistantBottomSheet
import com.pixel.intelligentsearch.feature.voice.VoiceRecognitionState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of the assistant's voice sheet in each recognition state, so the voice session can be split
 * into focused files (R4) with proof that the sheet still looks the same.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class VoiceSheetGoldenTest(private val name: String, private val state: VoiceRecognitionState, private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    @Test
    fun sheet() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                VoiceAssistantBottomSheet(
                    state = state,
                    audioAmplitude = { if (state == VoiceRecognitionState.LISTENING) 0.6f else 0f },
                    spokenText = SPOKEN.getValue(state),
                    errorMessage = if (state == VoiceRecognitionState.ERROR) "Microphone permission required for voice search." else null,
                    onMicClick = {},
                    onDismiss = {},
                    onOpenFullSearch = {},
                    onQuickPromptClick = {}
                )
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/voice/${name}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        private val SPOKEN = mapOf(
            VoiceRecognitionState.INITIALIZING to "",
            VoiceRecognitionState.LISTENING to "what's the weather",
            VoiceRecognitionState.PROCESSING to "what's the weather tomorrow",
            VoiceRecognitionState.RESULT to "set a timer for 10 minutes",
            VoiceRecognitionState.ERROR to ""
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={2}")
        fun parameters(): List<Array<Any>> = VoiceRecognitionState.entries.flatMap { s ->
            listOf(arrayOf<Any>(s.name.lowercase(), s, false), arrayOf<Any>(s.name.lowercase(), s, true))
        }
    }
}
