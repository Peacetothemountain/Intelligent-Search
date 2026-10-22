package com.pixel.intelligentsearch.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.ui.expressive.MatteGrain
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.feature.settings.ShellSelector
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The one golden that keeps the matte shells' film grain. Every other golden leaves it out (it doesn't compress, see
 * [MatteGrain.drawn]), so this pins the grain, glow and rim of a top-level shell holding a nested shell and a
 * [ShellSelector], in both themes.
 *
 * Record with `./gradlew testDebugUnitTest --tests "*MatteShellGoldenTest*" -Proborazzi.record=true`.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class MatteShellGoldenTest(private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private var grainWasDrawn = true

    @Before fun drawTheGrain() {
        hilt.inject()
        disableHandRolledAnimations()
        grainWasDrawn = MatteGrain.drawn
        MatteGrain.drawn = true
    }

    @After fun restoreTheGrain() {
        MatteGrain.drawn = grainWasDrawn
    }

    @Test
    fun shell() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Column(
                    Modifier
                        .testTag(CAPTURE_TAG)
                        .background(MaterialTheme.colorScheme.surface)
                        .width(360.dp)
                        .padding(16.dp)
                ) {
                    MatteShell(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Matte Shell", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Grain, a soft Material You glow and a rim light.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ShellSelector(options = OPTIONS, selected = OPTIONS[1], onSelect = {}) { option, selected ->
                                Text(
                                    option,
                                    modifier = Modifier.align(Alignment.Center),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            MatteShell(Modifier.fillMaxWidth(), nested = true) {
                                Text("Nested", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
        compose.settle()
        compose.onNodeWithTag(CAPTURE_TAG).captureRoboImage(
            filePath = "src/test/screenshots/settings/matte_shell_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        private const val CAPTURE_TAG = "matte_shell_golden"
        private val OPTIONS = listOf("Home", "Plan", "Billing")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
