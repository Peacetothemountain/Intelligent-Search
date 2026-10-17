package com.pixel.intelligentsearch.core.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The overlay crashed in production with "Key was already used" when its results list held two items with
 * the same key. [uniqueItems] must render such a list, showing each key once.
 */
@RunWith(RobolectricTestRunner::class)
class UniqueLazyItemsTest {

    @get:Rule
    val compose = createComposeRule()

    private val withRepeat = listOf("Maps", "Chrome", "Maps")

    @Test
    fun plainKeyedItemsCrashOnARepeatedKey() {
        // The production failure, reproduced: proves the next test exercises the real problem.
        val error = assertThrows(IllegalArgumentException::class.java) {
            compose.setContent {
                LazyColumn { items(withRepeat, key = { it }) { Text(it) } }
            }
            compose.waitForIdle()
        }
        assertTrue(error.message.orEmpty().contains("was already used"))
    }

    @Test
    fun uniqueItemsShowsEachKeyOnceInsteadOfCrashing() {
        compose.setContent {
            LazyColumn { uniqueItems(withRepeat, key = { it }) { Text(it) } }
        }
        compose.onAllNodesWithText("Maps").assertCountEquals(1)
        compose.onNodeWithText("Chrome").assertIsDisplayed()
    }
}
