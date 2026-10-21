package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.core.theme.OverlayIconRecipe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget editor's preview draws the Material G style's G, mic, shortcuts and circle button as the search overlay
 * draws its icons ([OverlayIconRecipe]): the lens and the sparkle mic, each part in the overlay's colour, from the
 * colours it is given, on a light and a dark pill, for the theme's colours and for a custom colour's.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetPreviewIconsDrawingTest(private val custom: Boolean, private val light: Boolean) {

    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun previewDrawsTheOverlaysIcons() {
        val (p, s, t) = if (custom) {
            MaterialYouPaletteHelper.getMaterialYouTonalColors(140f, 70f, isDarkSurface = !light).let { Triple(it.primary, it.secondary, it.tertiary) }
        } else {
            DynamicThemeDetector.resolveWidgetColors(context, !light).let { Triple(it.primary, it.secondary, it.tertiary) }
        }
        compose.setContent {
            Row {
                ComposeGIcon(Modifier.size(24.dp).testTag("g"), Color(p), Color(s), Color(t), isPillLight = light)
                ComposeThemedShortcutIcon(
                    R.drawable.ic_mic, "Voice Search", Color(p), Color(s), Color(t), isMaterialTheme = true,
                    modifier = Modifier.size(24.dp).testTag("mic"), isPillLight = light
                )
                ComposeThemedShortcutIcon(
                    R.drawable.ic_search_lens_expressive, "Live", Color(p), Color(s), Color(t), isMaterialTheme = true,
                    modifier = Modifier.size(24.dp).testTag("live"), isPillLight = light
                )
                ComposeThemedShortcutIcon(
                    R.drawable.ic_camera, "Google Lens", Color(p), Color(s), Color(t), isMaterialTheme = true,
                    modifier = Modifier.size(24.dp).testTag("camera"), isPillLight = light
                )
                ComposeActionIcon(
                    "Search", Modifier.size(24.dp).testTag("circle"), Color(p), Color(s), Color(t),
                    isMaterialTheme = true, isPillLight = light
                )
            }
        }
        val colors = OverlayIconRecipe.colors(p, s, t, light)
        val label = "${if (custom) "custom" else "theme"} ${if (light) "light" else "dark"} pill"
        fun drawn(tag: String) = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        OverlayIconRecipe.assertDrawn("$label G", drawn("g"), OverlayIconRecipe.g(colors))
        OverlayIconRecipe.assertDrawn("$label mic", drawn("mic"), OverlayIconRecipe.mic(colors))
        OverlayIconRecipe.assertDrawn("$label Live shortcut", drawn("live"), OverlayIconRecipe.lens(colors))
        OverlayIconRecipe.assertDrawn("$label Lens shortcut", drawn("camera"), OverlayIconRecipe.camera(colors), across = 100f)
        OverlayIconRecipe.assertDrawn("$label circle button", drawn("circle"), OverlayIconRecipe.lens(colors))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "custom={0} light={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { custom -> listOf(arrayOf<Any>(custom, true), arrayOf<Any>(custom, false)) }
    }
}
