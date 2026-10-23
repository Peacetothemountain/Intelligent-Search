package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rows a switch attaches under itself rise away as soon as it turns off, steadily and without bouncing, and are
 * gone within [ExpressiveMotionTokens.Reveal.COLLAPSE_MILLIS]; turned back on, they are fully down again soon after.
 */
@RunWith(RobolectricTestRunner::class)
class AttachedRowsTest {

    @get:Rule val compose = createComposeRule()

    private var visible by mutableStateOf(true)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column(Modifier.testTag("card").width(300.dp)) {
                Text("Switch row", Modifier.height(72.dp))
                AttachedRows(visible = visible) {
                    Column(Modifier.testTag("rows")) {
                        repeat(4) { Text("Attached row $it", Modifier.height(72.dp)) }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun cardHeight() = compose.onNodeWithTag("card").getUnclippedBoundsInRoot().let { it.bottom - it.top }

    @Test
    fun switchedOffTheRowsAreGoneWithinTheCollapse() {
        show()
        val open = cardHeight()
        visible = false
        Snapshot.sendApplyNotifications()
        var last = open
        // Two frames to pick the change up and start, then the collapse itself.
        val frames = (ExpressiveMotionTokens.Reveal.COLLAPSE_MILLIS / 16) + 3
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            val now = cardHeight()
            assertTrue("the card grew back while closing ($last -> $now)", now <= last + 0.5.dp)
            last = now
        }
        compose.onNodeWithTag("rows").assertDoesNotExist()
        assertTrue("the card closed up to the switch row: $last", last <= 72.5.dp)
    }

    @Test
    fun switchedOnTheRowsAreDownSoonAfter() {
        visible = false
        show()
        visible = true
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(400)
        assertTrue("the card opened fully: ${cardHeight()}", cardHeight() >= (72 * 5).dp - 0.5.dp)
    }
}
