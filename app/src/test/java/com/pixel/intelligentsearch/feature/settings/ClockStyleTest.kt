package com.pixel.intelligentsearch.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.material3.MaterialTheme
import com.pixel.intelligentsearch.feature.search.GlanceClockStyle
import com.pixel.intelligentsearch.feature.search.digitalClockFigures
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** At a Glance's Clock Style: the faces it offers, what the digital one reads, and the picker choosing among them. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ClockStyleTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun setUp() = hilt.inject()

    @Test
    fun theStylesAreFewAndReadBackFromWhatIsStored() {
        assertEquals(listOf("Cookie", "Sunny", "Square", "Digital"), GlanceClockStyle.entries.map { it.label })
        for (style in GlanceClockStyle.entries) assertEquals(style, GlanceClockStyle.from(style.storedValue))
        // Nothing stored, or something an older or edited backup holds, is the Cookie face it always was.
        assertEquals(GlanceClockStyle.COOKIE, GlanceClockStyle.from(null))
        assertEquals(GlanceClockStyle.COOKIE, GlanceClockStyle.from("neon"))
    }

    @Test
    fun theDigitalFaceReadsTheTimeInThePhonesStyle() {
        val utc = ZoneId.of("UTC")
        val day = 1_800_000_000_000L - 1_800_000_000_000L % 86_400_000L
        fun at(h: Int, m: Int) = day + (h * 3600 + m * 60) * 1000L
        assertEquals("8" to "05", digitalClockFigures(at(20, 5), utc, is24Hour = false))
        assertEquals("20" to "05", digitalClockFigures(at(20, 5), utc, is24Hour = true))
        assertEquals("12" to "00", digitalClockFigures(at(0, 0), utc, is24Hour = false))
        assertEquals("12" to "30", digitalClockFigures(at(12, 30), utc, is24Hour = false))
        assertEquals("00" to "00", digitalClockFigures(at(0, 0), utc, is24Hour = true))
    }

    @Test
    fun thePickerShowsEveryFaceAndPicksTheOneTapped() {
        var picked by mutableStateOf(GlanceClockStyle.COOKIE)
        compose.setContent {
            MaterialTheme { ClockStylePicker(selected = picked, onSelect = { picked = it }) }
        }
        compose.waitForIdle()
        // Every face is a choice of its own, the stored one selected.
        for (style in GlanceClockStyle.entries) compose.onNodeWithContentDescription("${style.label} clock").assertExists()
        compose.onNodeWithTag(CLOCK_STYLE_TAG_PREFIX + "cookie").assertIsSelected()
        compose.onNodeWithContentDescription("Digital clock").performClick()
        compose.waitForIdle()
        assertEquals(GlanceClockStyle.DIGITAL, picked)
        compose.onNodeWithTag(CLOCK_STYLE_TAG_PREFIX + "digital").assertIsSelected()
        compose.onNodeWithTag(CLOCK_STYLE_TAG_PREFIX + "cookie").assertIsNotSelected()
        val role = compose.onNodeWithTag(CLOCK_STYLE_TAG_PREFIX + "sunny").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
        assertEquals(androidx.compose.ui.semantics.Role.RadioButton, role)
    }
}
