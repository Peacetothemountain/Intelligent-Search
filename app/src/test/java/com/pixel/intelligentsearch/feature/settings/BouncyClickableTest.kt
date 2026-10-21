package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A tapped [bouncyClickable] shows the theme's ripple, and one focused from a keyboard shows a focus ring.
 * The ripple itself is Material's; these tests check that it is the indication the control uses and that it hears
 * the control's presses and focus.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h900dp-xhdpi")
class BouncyClickableTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun inject() = hilt.inject()

    /** Records every interaction the control reports to its indication. */
    private class RecordingIndication : IndicationNodeFactory {
        val interactions = mutableListOf<Interaction>()

        override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {
            override fun onAttach() {
                coroutineScope.launch { interactionSource.interactions.collect { interactions += it } }
            }
        }

        override fun equals(other: Any?) = other === this
        override fun hashCode() = System.identityHashCode(this)
    }

    @Test
    fun theAppThemesIndicationIsARipple() {
        var indicationName = ""
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { indicationName = LocalIndication.current::class.java.name }
        }
        compose.waitForIdle()
        assertTrue("indication was $indicationName", indicationName.contains("Ripple"))
    }

    @Test
    fun aPressReachesTheIndication() {
        val indication = RecordingIndication()
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(LocalIndication provides indication) {
                    Box(Modifier.size(200.dp, 80.dp).testTag("row").bouncyClickable {})
                }
            }
        }
        compose.onNodeWithTag("row").performTouchInput { down(center) }
        compose.waitForIdle()
        assertTrue(indication.interactions.any { it is PressInteraction.Press })
        compose.onNodeWithTag("row").performTouchInput { up() }
        compose.waitForIdle()
        assertTrue(indication.interactions.any { it is PressInteraction.Release })
    }

    private fun showFocusable(indication: RecordingIndication? = null): Pair<FocusRequester, () -> Unit> {
        val focusRequester = FocusRequester()
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                inputModeManager = LocalInputModeManager.current
                ringColor = MaterialTheme.colorScheme.secondary
                pageColor = MaterialTheme.colorScheme.background
                val content = @androidx.compose.runtime.Composable {
                    Box(
                        Modifier
                            .background(pageColor)
                            .size(200.dp, 80.dp)
                            .testTag("row")
                            .focusRequester(focusRequester)
                            .bouncyClickable(shape = RoundedCornerShape(8.dp)) {}
                    )
                }
                if (indication != null) CompositionLocalProvider(LocalIndication provides indication) { content() } else content()
            }
        }
        compose.waitForIdle()
        return focusRequester to {
            compose.runOnIdle {
                // Controls take focus from a keyboard, not from touch.
                inputModeManager.requestInputMode(InputMode.Keyboard)
                focusRequester.requestFocus()
            }
            compose.waitForIdle()
        }
    }

    private var ringColor = Color.Unspecified
    private var pageColor = Color.Unspecified

    @Test
    fun keyboardFocusReachesTheIndication() {
        val indication = RecordingIndication()
        val (_, focus) = showFocusable(indication)
        focus()
        assertTrue(indication.interactions.any { it is FocusInteraction.Focus })
    }

    @Test
    fun keyboardFocusDrawsARing() {
        val (_, focus) = showFocusable()
        fun edgePixel(): Int {
            val image = compose.onNodeWithTag("row").captureToImage()
            // Halfway down the left edge, inside the ring's width.
            return image.toPixelMap()[2, image.height / 2].toArgb()
        }
        assertNotEquals(ringColor.toArgb(), edgePixel())
        focus()
        assertEquals(ringColor.toArgb(), edgePixel())
    }
}
