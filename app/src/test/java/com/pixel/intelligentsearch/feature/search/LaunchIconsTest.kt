package com.pixel.intelligentsearch.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The overlay's record of its app icons on screen: only icons still shown count, the first of several keys wins. */
@RunWith(RobolectricTestRunner::class)
class LaunchIconsTest {

    @get:Rule val compose = createComposeRule()

    private val icons = LaunchIcons()
    private val showBestMatch = mutableStateOf(true)

    private fun show() = compose.setContent {
        Column {
            if (showBestMatch.value) Box(Modifier.size(52.dp).launchIcon(icons, LaunchIconKeys.BEST_MATCH))
            Box(Modifier.size(48.dp).launchIcon(icons, "app_camera"))
        }
    }

    private fun boundsOf(key: String) = compose.onNode(hasTestTag(launchIconTag(key))).fetchSemanticsNode().boundsInRoot

    @Test
    fun theFirstIconOnScreenWins() {
        show()
        assertEquals(boundsOf(LaunchIconKeys.BEST_MATCH), icons.bounds(listOf(LaunchIconKeys.BEST_MATCH, "app_camera")))
        assertEquals(boundsOf("app_camera"), icons.bounds(listOf("pill_camera", "app_camera")))
    }

    @Test
    fun anIconNoLongerShownIsForgotten() {
        show()
        showBestMatch.value = false
        compose.waitForIdle()
        assertEquals(boundsOf("app_camera"), icons.bounds(listOf(LaunchIconKeys.BEST_MATCH, "app_camera")))
        assertNull(icons.bounds(listOf(LaunchIconKeys.BEST_MATCH)))
    }
}
