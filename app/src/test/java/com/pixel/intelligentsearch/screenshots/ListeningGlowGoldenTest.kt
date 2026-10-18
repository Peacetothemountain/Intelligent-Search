package com.pixel.intelligentsearch.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.listeningGlow
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

/** The search bar's listening animation (Gboard Rambler style) at a quiet and a loud voice level, light and dark. */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class ListeningGlowGoldenTest(private val level: Float, private val dark: Boolean) {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun setUp() = hilt.inject()

    @Test
    fun glow() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    Box(
                        Modifier
                            .size(380.dp, 64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .listeningGlow(active = true, level = { level }, cornerRadius = 32.dp)
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_300)
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/voice/listening_${if (level > 0.5f) "loud" else "quiet"}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "level={0} dark={1}")
        fun cases() = listOf(0.1f, 0.8f).flatMap { l -> listOf(arrayOf<Any>(l, false), arrayOf<Any>(l, true)) }
    }
}
