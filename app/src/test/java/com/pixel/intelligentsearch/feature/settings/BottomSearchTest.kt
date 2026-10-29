package com.pixel.intelligentsearch.feature.settings

import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The bottom search pill: it waits for the keyboard so the two land together, and its halftone shader, compiled by the
 * device at run time where a mistake would only show as a missing pill, compiles here with Android's own compiler.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomSearchTest {

    @Test
    fun thePillStartsOnceTheKeyboardIsMostOfTheWayUp() {
        assertFalse("no keyboard coming yet", pillMayStart(keyboardNow = 0, keyboardTarget = 0))
        assertFalse("just starting to rise", pillMayStart(keyboardNow = 200, keyboardTarget = 900))
        assertTrue("most of the way up", pillMayStart(keyboardNow = 560, keyboardTarget = 900))
        assertTrue("already up", pillMayStart(keyboardNow = 900, keyboardTarget = 900))
    }

    @Test
    fun theManifestingShaderCompilesAndTakesEveryUniform() {
        val shader = RuntimeShader(BOTTOM_SEARCH_SHADER)
        for (progress in listOf(0f, 0.4f, 1f)) {
            shader.setPillUniforms(Size(1140f, 144f), 3f, progress, 1f, 0.5f, Color(0xFF3A2D33), Color(0xFFF3B2D4))
        }
    }
}
