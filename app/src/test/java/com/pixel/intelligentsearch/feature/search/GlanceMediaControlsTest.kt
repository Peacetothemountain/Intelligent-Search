package com.pixel.intelligentsearch.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.glance.MediaControls
import com.pixel.intelligentsearch.core.system.MediaSessionAccess
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** At a Glance's media controls (the squiggly seek bar and the transport pill) and the clock in the card's corner. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class GlanceMediaControlsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @org.junit.Before fun inject() = hilt.inject()

    /** Records what the controls were asked to do. */
    private class RecordingControls : MediaControls {
        val calls = mutableListOf<String>()
        val seeks = mutableListOf<Long>()
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun seekTo(positionMs: Long) { seeks += positionMs }
        override fun open() { calls += "open" }
    }

    private fun media(controls: MediaControls, playing: Boolean = true, canSeek: Boolean = true) = GlanceMedia(
        title = "Midnight City", artist = "M83", appLabel = "Music", packageName = "com.example.music",
        isPlaying = playing, artwork = null, controls = controls,
        progress = MediaSessionAccess.Progress(durationMs = 200_000L, positionMs = 50_000L, updatedAtElapsed = 0L, speed = 1f, canSeek = canSeek)
    )

    private fun show(media: GlanceMedia) {
        // A playing track's wave flows for as long as it plays, so Compose never goes idle on its own: the test clock
        // only moves when a test moves it.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(LocalGlanceClock provides GlanceClock({ 0L }, ticks = false, elapsed = { 0L })) {
                    Box(Modifier.width(300.dp)) { GlanceMediaControls(media) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
    }

    @Test
    fun theThreeButtonsAreInOnePillAndControlThePlayer() {
        val controls = RecordingControls()
        show(media(controls, playing = true))
        compose.onNodeWithTag(GLANCE_TRANSPORT_TAG).assertExists()
        compose.onNodeWithContentDescription("Previous Track").performClick()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("Next Track").performClick()
        assertEquals(listOf("previous", "pause", "next"), controls.calls)
    }

    @Test
    fun thePillSits8dpUnderTheTimes() {
        show(media(RecordingControls()))
        val timesBottom = compose.onNodeWithText("0:50", useUnmergedTree = true).getBoundsInRoot().bottom
        // The tag sits inside the pill's own 2dp of vertical padding, so its edge is 2dp under the pill's.
        val pillTop = compose.onNodeWithTag(GLANCE_TRANSPORT_TAG).getBoundsInRoot().top - 2.dp
        assertEquals(8f, (pillTop - timesBottom).value, 0.5f)
    }

    @Test
    fun pausedThePillOffersPlay() {
        val controls = RecordingControls()
        show(media(controls, playing = false))
        compose.onNodeWithContentDescription("Play").performClick()
        assertEquals(listOf("play"), controls.calls)
    }

    @Test
    fun theBarSaysWhereTheTrackIs() {
        show(media(RecordingControls()))
        compose.onNodeWithTag(GLANCE_SEEK_BAR_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Playback Position")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0:50 of 3:20"))
        val range = compose.onNodeWithTag(GLANCE_SEEK_BAR_TAG).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.25f, range.current, 0.001f)
    }

    @Test
    fun draggingSeeksOnceOnRelease() {
        val controls = RecordingControls()
        show(media(controls))
        compose.onNodeWithTag(GLANCE_SEEK_BAR_TAG).performTouchInput {
            swipe(start = Offset(width * 0.25f, centerY), end = Offset(width - 1f, centerY), durationMillis = 300)
        }
        compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
        assertEquals(1, controls.seeks.size)
        assertTrue("sought to ${controls.seeks.single()}", controls.seeks.single() > 190_000L)
    }

    @Test
    fun talkBackCanSetThePosition() {
        val controls = RecordingControls()
        show(media(controls))
        compose.onNodeWithTag(GLANCE_SEEK_BAR_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(listOf(100_000L), controls.seeks)
    }

    @Test
    fun aPlayerThatTakesNoSeekGetsABarThatOnlyShows() {
        val controls = RecordingControls()
        show(media(controls, canSeek = false))
        val node = compose.onNodeWithTag(GLANCE_SEEK_BAR_TAG).fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsActions.SetProgress))
        compose.onNodeWithTag(GLANCE_SEEK_BAR_TAG).performTouchInput {
            swipe(start = Offset(width * 0.25f, centerY), end = Offset(width - 1f, centerY), durationMillis = 300)
        }
        compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
        assertTrue(controls.seeks.isEmpty())
    }

    @Test
    fun withoutALengthThereIsNoBarButStillThePill() {
        show(media(RecordingControls()).copy(progress = null))
        compose.onNodeWithTag(GLANCE_SEEK_BAR_TAG).assertDoesNotExist()
        compose.onNodeWithTag(GLANCE_TRANSPORT_TAG).assertExists()
    }

    @Test
    fun timesReadAsPlayersShowThem() {
        assertEquals("0:00", mediaTimeLabel(0L))
        assertEquals("0:00", mediaTimeLabel(-5_000L))
        assertEquals("3:05", mediaTimeLabel(185_400L))
        assertEquals("1:02:03", mediaTimeLabel(3_723_000L))
    }

    @Test
    fun aTouchLandsOnTheTrackMirroredRightToLeft() {
        assertEquals(0f, seekFractionAt(-10f, 10f, 110f, rtl = false))
        assertEquals(0.5f, seekFractionAt(60f, 10f, 110f, rtl = false))
        assertEquals(1f, seekFractionAt(500f, 10f, 110f, rtl = false))
        assertEquals(0.25f, seekFractionAt(85f, 10f, 110f, rtl = true))
        assertEquals(0f, seekFractionAt(50f, 10f, 10f, rtl = false))
    }

    @Test
    fun theClockShowsTheLocalTime() {
        val utc = ZoneId.of("UTC")
        // 20:30:15 UTC.
        val at = 1_800_000_000_000L - 1_800_000_000_000L % 86_400_000L + (20 * 3600 + 30 * 60 + 15) * 1000L
        val angles = clockAngles(at, utc)
        assertEquals(8 * 30f + 15f, angles.hour)
        assertEquals(180f, angles.minute)
        assertEquals(90f, angles.second)
        // With animations off the seconds marker rests at 12.
        assertEquals(0f, clockAngles(at, utc, secondsStill = true).second)
        assertEquals("Current time, 8:30 PM", clockDescription("8:30 PM"))
    }

    @Test
    fun theClockIsLabelledForTalkBack() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(LocalGlanceClock provides GlanceClock({ 0L }, ticks = false)) { GlanceClockFace() }
            }
        }
        compose.waitForIdle()
        val description = compose.onNodeWithTag(GLANCE_CLOCK_TAG).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull()
        assertNotNull(description)
        assertTrue(description, description!!.startsWith("Current time, "))
        assertFalse(description, description.endsWith(", "))
    }

    private companion object {
        /** How far a test moves the paused clock to let a gesture or a first layout land. */
        const val FRAME_STEP_MS = 500L
    }
}
