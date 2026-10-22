package com.pixel.intelligentsearch.feature.search

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.theme.OverlayIconRecipe
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The search overlay's search bar draws its lens and its sparkle mic in the recipe the widget's Material icons follow
 * ([OverlayIconRecipe]), in the app's light and dark themes, with the widget's icon theme left as it comes (Material).
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class OverlayIconsTest(private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theOverlaysIconsFollowTheRecipe() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Row {
                    SearchOverlayLensIcon(Modifier.size(24.dp).testTag("lens"))
                    SearchOverlayMicIcon(Modifier.size(24.dp).testTag("mic"))
                }
            }
        }
        val colors = OverlayIconRecipe.theme(context, isLight = !dark)
        val label = if (dark) "dark" else "light"
        OverlayIconRecipe.assertDrawn("$label overlay lens", compose.onNodeWithTag("lens").captureToImage().asAndroidBitmap(), OverlayIconRecipe.lens(colors))
        // The overlay's mic keeps its sparkle, which the widget's mic doesn't have.
        OverlayIconRecipe.assertDrawn("$label overlay mic", compose.onNodeWithTag("mic").captureToImage().asAndroidBitmap(), OverlayIconRecipe.mic(colors))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
