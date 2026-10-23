package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A slider row's value never gets clipped by its title: at 2x text on a narrow phone (where the layout audit caught
 * "None" cut off beside "Outline Thickness"), the value moves under the title instead.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
class SettingsSliderRowFitTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun inject() = hilt.inject()

    @Test
    fun theValueIsNotClippedAtLargeTextOnANarrowPhone() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    // The card's content width on a 360dp phone.
                    Box(Modifier.width(312.dp)) {
                        SettingsSliderRow(
                            title = "Outline Thickness",
                            value = 0f,
                            onValueChange = {},
                            valueRange = 0f..12f,
                            icon = Icons.Outlined.LineWeight,
                            showDivider = false,
                            steps = 5,
                            valueText = { "None" }
                        )
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("None").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        // The layout audit's own test for clipped text: a line wider than the box it's drawn in.
        val r = layouts.single()
        val widest = (0 until r.lineCount).maxOf { r.getLineRight(it) - r.getLineLeft(it) }
        assertFalse("the value is clipped: $widest px in ${r.size.width} px", widest > r.size.width + 1f)
    }
}
