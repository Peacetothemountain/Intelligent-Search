package com.pixel.intelligentsearch.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.pixel.intelligentsearch.core.theme.appColorScheme
import androidx.compose.ui.platform.LocalContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What widget previews sit on. In light the page is the same tint as the System design's default bar, so a preview on
 * the page's own color showed no bar at all; it sits on the lightest surface instead. Dark keeps the page's color.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetPreviewStageTest {

    @get:Rule val compose = createComposeRule()

    private fun stage(dark: Boolean): Triple<Color, Color, Color> {
        var result: Triple<Color, Color, Color>? = null
        compose.setContent {
            // The app's own scheme (IntelligentSearchTheme needs the app's Hilt graph, which this test doesn't start).
            val context = LocalContext.current
            MaterialTheme(colorScheme = appColorScheme(context, dark)) {
                val scheme = MaterialTheme.colorScheme
                result = Triple(widgetPreviewStageColor(), scheme.background, scheme.surfaceContainerLowest)
            }
        }
        compose.waitForIdle()
        return result!!
    }

    @Test
    fun inLightThePreviewStandsApartFromThePage() {
        val (stage, page, lowest) = stage(dark = false)
        assertEquals(lowest, stage)
        assertNotEquals("the preview's stage is not the page's color", page, stage)
    }

    @Test
    fun inDarkThePreviewSitsOnThePage() {
        val (stage, page, _) = stage(dark = true)
        assertEquals(page, stage)
    }
}
