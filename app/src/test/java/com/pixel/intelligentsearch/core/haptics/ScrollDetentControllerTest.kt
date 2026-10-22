package com.pixel.intelligentsearch.core.haptics

import android.content.Context
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Scroll detents follow the list without recomposing the screen that hosts it on every scroll frame. */
@RunWith(RobolectricTestRunner::class)
class ScrollDetentControllerTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun scrollingLeavesTheHostAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TactileSonicEngine(PixelHapticEngine(context))
        val listState = LazyListState()
        var compositions = 0
        compose.setContent {
            SideEffect { compositions++ }
            rememberScrollDetentController(listState, engine)
            LazyColumn(state = listState, modifier = Modifier.height(400.dp)) {
                items(200) { Text("Item $it", modifier = Modifier.height(48.dp)) }
            }
        }
        compose.waitForIdle()
        val before = compositions

        repeat(5) {
            compose.runOnIdle { runBlocking { listState.scrollBy(300f) } }
            compose.waitForIdle()
        }

        assertTrue(listState.firstVisibleItemIndex > 0)
        assertEquals(before, compositions)
    }
}
