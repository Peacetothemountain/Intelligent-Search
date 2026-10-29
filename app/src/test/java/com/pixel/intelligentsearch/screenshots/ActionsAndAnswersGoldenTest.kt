package com.pixel.intelligentsearch.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.ai.AiAnswerState
import com.pixel.intelligentsearch.core.ai.AiProblem
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.AiAnswerCard
import com.pixel.intelligentsearch.feature.search.AskAiRow
import com.pixel.intelligentsearch.feature.search.DirectActionRow
import com.pixel.intelligentsearch.feature.settings.AiAnswersScreen
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

/** AI answer cards as the overlay shows them: a stand-in finished answer, and a failure from a bad key. */
internal object AiAnswerSamples {
    @Composable
    fun Cards() {
        AiAnswerCard(
            AiAnswerState.Answer(
                AiProvider.ANTHROPIC,
                "how tall is everest?",
                "Mount Everest is **8,849 m** (29,032 ft) tall.\n\n- Measured by China and Nepal in 2020\n- Its summit is the highest point above sea level",
                done = true
            ),
            model = "claude-sonnet-5-5"
        ) {}
        AiAnswerCard(AiAnswerState.Failed(AiProvider.OPENAI, "why is the sky blue?", AiProblem.BAD_KEY), model = "gpt-5.1") {}
    }
}

/**
 * Golden images, light and dark, of the short-phrasing action suggestions with the Ask row, of an AI answer card (a
 * stand-in answer and a failure), and of the AI Answers settings page.
 * See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class ActionsAndAnswersGoldenTest(private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val suffix get() = if (dark) "dark" else "light"

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    private fun capture(path: String) {
        compose.settle()
        compose.onRoot().captureRoboImage(filePath = path, roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS)
    }

    @Test
    fun actionSuggestions() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp)) {
                    DirectActionRow("Start 15 min Timer", "Clock Timer", Icons.Default.Timer) {}
                    DirectActionRow("Set Alarm for 7:30 AM", "Clock Alarm · Tomorrow", Icons.Default.Alarm) {}
                    DirectActionRow("Remind: Laundry at 4:12 PM", "Calendar Reminder · Thu, Oct 22", Icons.Default.Event) {}
                    AskAiRow(AiProvider.ANTHROPIC, "how tall is everest?") {}
                }
            }
        }
        capture("src/test/screenshots/overlay/action_suggestions_$suffix.png")
    }

    @Test
    fun aiAnswerCard() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp)) {
                    AiAnswerSamples.Cards()
                }
            }
        }
        capture("src/test/screenshots/overlay/ai_answer_$suffix.png")
    }

    @Test
    fun aiAnswersPage() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) { AiAnswersScreen(onBack = {}) }
            }
        }
        capture("src/test/screenshots/settings/ai_answers_$suffix.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
