package com.pixel.intelligentsearch.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.data.WebSuggestion
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.OverlayWebSuggestionRow
import com.pixel.intelligentsearch.feature.search.ResultThumbnails
import com.pixel.intelligentsearch.feature.search.SUGGESTION_LEADING_SIZE
import com.pixel.intelligentsearch.feature.search.ThumbnailSource
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
 * Golden images of web suggestion rows as Google lists them for "obama": a past search, the entity with its picture in
 * the matte circle and its description, and plain suggestions with the completion bold. The entity picture is a
 * stand-in drawn by the test, so nothing is fetched. See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h420dp-xhdpi")
class WebSuggestionGoldenTest(private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val picture = "https://encrypted-tbn0.gstatic.com/images?q=tbn:golden"

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        ResultThumbnails.clear()
        // A two-tone stand-in portrait: a disc for a head over a wider one for shoulders.
        val px = (SUGGESTION_LEADING_SIZE.value * 2).toInt() // xhdpi
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(0xFF8DA9C4.toInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3E5C76.toInt() }
            drawCircle(px / 2f, px * 0.42f, px * 0.2f, paint)
            drawCircle(px / 2f, px * 1.05f, px * 0.42f, paint)
        }
        ResultThumbnails.put(ThumbnailSource.Web(picture), px, bitmap.asImageBitmap())
    }

    @After fun tearDown() = ResultThumbnails.clear()

    @Test
    fun suggestions() {
        val rows = listOf(
            WebSuggestion("obama speech") to true,
            WebSuggestion("obama", "Barack Obama", "44th U.S. President", picture) to false,
            WebSuggestion("obama presidential center", boldRanges = listOf(5..24)) to false,
            WebSuggestion("obama library") to false
        )
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                    for ((suggestion, recent) in rows) {
                        OverlayWebSuggestionRow(suggestion, typed = "obama", isRecent = recent, showThumbnail = true, onSearch = {}, onRemove = {}, onFill = {})
                    }
                }
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/overlay/web_suggestions_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
