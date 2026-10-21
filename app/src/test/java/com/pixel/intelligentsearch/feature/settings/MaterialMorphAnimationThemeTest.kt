package com.pixel.intelligentsearch.feature.settings

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Material Morph animation in the app's theme: its shapes and colours, in the light and the dark theme. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h914dp-xxhdpi")
class MaterialMorphAnimationThemeTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Before
    fun setUp() {
        hilt.inject()
    }

    /** Shows the animation in the app's theme, light or [dark], and returns the colour scheme it was drawn in. */
    private fun show(dark: Boolean, seed: Int = 2): Pair<ActivityScenario<HiltTestActivity>, () -> ColorScheme> {
        var scheme: ColorScheme? = null
        val scenario = ActivityScenario.launch(HiltTestActivity::class.java)
        compose.mainClock.autoAdvance = false
        scenario.onActivity {
            it.setContent {
                CompositionLocalProvider(LocalMorphRandomSeed provides seed) {
                    IntelligentSearchTheme(darkTheme = dark) {
                        scheme = MaterialTheme.colorScheme
                        Box(Modifier.size(411.dp, 300.dp)) { MaterialMorphAnimation(Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        return scenario to { scheme!! }
    }

    private fun info(): MorphSceneInfo =
        compose.onNodeWithTag(MORPH_ANIMATION_TAG, useUnmergedTree = true).fetchSemanticsNode().config[MorphSceneKey]

    @Test
    fun theShapesAreFilledWithTheThemesColourRolesInLightAndDark() {
        for (dark in listOf(false, true)) {
            val (scenario, scheme) = show(dark)
            try {
                val s = scheme()
                assertEquals("dark $dark", dark, s.background.luminance() < 0.5f)
                assertEquals(
                    "dark $dark",
                    listOf(s.primaryFixedDim, s.tertiaryContainer, s.secondaryFixedDim, s.primaryContainer, s.tertiaryFixedDim),
                    info().palette
                )
                repeat(60) { compose.mainClock.advanceTimeBy(16) }
                assertTrue("dark $dark: no shapes", info().physics.bodies.isNotEmpty())
            } finally {
                scenario.close()
            }
        }
    }

    @Test
    fun theShapesAreMaterialShapesInASeededOrder() {
        val names = mutableListOf<List<String>>()
        repeat(2) {
            val (scenario, _) = show(dark = false, seed = 9)
            try {
                names += info().shapeNames
            } finally {
                scenario.close()
            }
        }
        assertEquals(MaterialMorphShapes.sequence(9), names[0])
        assertEquals(names[0], names[1])
        assertEquals(MaterialMorphShapes.byName.keys, names[0].toSet())
    }
}
