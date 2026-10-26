package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.biometrics.BiometricManager
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager
import org.robolectric.annotation.Config
import java.util.Collections

/** The hidden-apps list once unlocked: its search field and the work it keeps off the main thread. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class ManageHiddenAppsScreenTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    /** An app icon that remembers which threads drew it. */
    private class RecordingIcon : ColorDrawable(Color.BLUE) {
        val drawThreads: MutableList<Thread> = Collections.synchronizedList(mutableListOf())
        override fun getIntrinsicWidth() = 192
        override fun getIntrinsicHeight() = 192
        override fun draw(canvas: Canvas) {
            drawThreads += Thread.currentThread()
            super.draw(canvas)
        }
    }

    private val icon = RecordingIcon()

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        // No screen lock or biometrics on this device, so the page opens without a prompt.
        Shadow.extract<ShadowBiometricManager>(context.getSystemService(BiometricManager::class.java)).setCanAuthenticate(false)
        disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                ManageHiddenAppsScreen(prefs, onBack = {}, loadApps = {
                    listOf(AppItem("Alpha", "com.example.alpha", icon), AppItem("Beta", "com.example.beta", icon))
                })
            }
        }
        compose.settle()
    }

    @Test
    fun appIconsAreDrawnOffTheMainThread() {
        compose.onNodeWithText("Alpha").assertExists()
        assertTrue("the icon should have been converted", icon.drawThreads.isNotEmpty())
        assertFalse(icon.drawThreads.any { it == Looper.getMainLooper().thread })
    }

    @Test
    fun theSearchFieldIsNamedFocusedAndSaysWhenNothingMatches() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.settle(steps = 5)

        compose.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Search Hidden Apps")))
            .assertIsFocused()
        compose.onNodeWithText("Search Hidden Apps").assertExists()

        compose.onNode(hasSetTextAction()).performTextInput("zzzz")
        compose.settle(steps = 5)
        compose.onNodeWithText("No Apps Match").assertExists()
    }

    private fun hidden(): Set<String> = prefs[SettingsManager.HIDDEN_APPS]

    private fun tap(description: String) {
        compose.onNodeWithContentDescription(description).performClick()
        compose.settle(steps = 5)
    }

    @Test
    fun theTitleSitsFlushLeftUnderBackWithEnableAllJustAfterIt() {
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        val title = compose.onNodeWithText("Manage Hidden Apps").getBoundsInRoot()
        val enableAll = compose.onNodeWithText("Enable All").getBoundsInRoot()
        val search = compose.onNodeWithContentDescription("Search").getBoundsInRoot()
        val reset = compose.onNodeWithContentDescription("Reset to Saved State").getBoundsInRoot()
        val save = compose.onNodeWithContentDescription("Save Hidden Apps").getBoundsInRoot()
        assertTrue("the title has its own line under the bar", title.top >= back.bottom)
        assertEquals("flush left, in line with the back arrow's glyph", 16f, title.left.value, 1f)
        assertTrue("Enable All follows the title, a little after it", enableAll.left >= title.right && enableAll.left - title.right <= 12.dp)
        assertTrue("on the title's line", enableAll.top < title.bottom && enableAll.bottom > title.top)
        assertTrue("search, Reset and Save stay in the bar", search.right <= reset.left && reset.right <= save.left && reset.top == back.top)
    }

    @Test
    fun switchesWaitForSaveAndResetPutsTheSavedAppsBack() {
        compose.onNodeWithText("Alpha").performClick()
        compose.settle(steps = 5)
        assertEquals("nothing is saved before Save", emptySet<String>(), hidden())

        tap("Reset to Saved State")

        compose.onNodeWithText("Beta").performClick()
        compose.settle(steps = 5)
        tap("Save Hidden Apps")
        assertEquals("Reset dropped Alpha; Save kept Beta", setOf("com.example.beta"), hidden())
    }

    @Test
    fun enableAllHidesEveryAppAndCheckedAgainShowsThemAll() {
        compose.onNodeWithText("Enable All").assertIsOff()
        compose.onNodeWithText("Enable All").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Enable All").assertIsOn()
        tap("Save Hidden Apps")
        assertEquals(setOf("com.example.alpha", "com.example.beta"), hidden())

        compose.onNodeWithText("Enable All").performClick()
        compose.settle(steps = 5)
        tap("Save Hidden Apps")
        assertEquals(emptySet<String>(), hidden())
    }

    @Test
    fun searchOpensOverTheTitleAndEnableAllAndTheXPlaysItBackward() {
        val field = hasSetTextAction()
        // The field's uncovered left edge each frame (its bounds are clipped to what the animation shows), until it
        // stops changing or is gone.
        fun frames(): List<Float> {
            val lefts = mutableListOf<Float>()
            repeat(60) {
                compose.mainClock.advanceTimeByFrame()
                val bounds = runCatching { compose.onNode(field).getBoundsInRoot() }.getOrNull()
                lefts += if (bounds == null || bounds.width.value == 0f) Float.NaN else bounds.left.value
            }
            return lefts
        }
        fun moving(lefts: List<Float>) = lefts.zipWithNext().filter { (a, b) -> a != b && !(a.isNaN() && b.isNaN()) }.map { it.second }

        compose.onNodeWithContentDescription("Search").performClick()
        val openingFrames = frames()
        val opening = moving(openingFrames)
        compose.waitForIdle()
        compose.onNodeWithText("Manage Hidden Apps").assertDoesNotExist()
        compose.onNodeWithText("Enable All").assertDoesNotExist()
        assertTrue("the field slides open over several frames: $opening", opening.size >= 4)
        assertTrue("with no slow start: it shows on the transition's first frame after the tap recomposes: $openingFrames", openingFrames.take(4).any { !it.isNaN() })
        assertTrue("from the right edge leftward: $opening", opening.filterNot { it.isNaN() }.zipWithNext().all { (a, b) -> b < a })

        compose.onNodeWithContentDescription("Close search").performClick()
        val closing = moving(frames())
        // The X plays it backward: the edge goes back to the right over the same number of frames. (Frames land at a
        // different point in the curve each time, so the two don't pass through exactly the same places.)
        val forward = opening.filterNot { it.isNaN() }
        val backward = closing.filterNot { it.isNaN() }
        assertTrue("back to the right edge: $backward", backward.zipWithNext().all { (a, b) -> b > a })
        assertTrue("as long as the open: $forward, $backward", kotlin.math.abs(forward.size - backward.size) <= 1)
        val frameStep = forward.zipWithNext().maxOf { (a, b) -> a - b }
        assertEquals("ending where the open began, give or take a frame", forward.first(), backward.last(), frameStep)
        compose.onNode(field).assertDoesNotExist()
        compose.onNodeWithText("Manage Hidden Apps").assertExists()
        compose.onNodeWithText("Enable All").assertExists()
    }

    @Test
    fun leavingWithUnsavedChangesAsksFirst() {
        compose.onNodeWithText("Alpha").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Discard").performClick()
        compose.settle(steps = 5)
        assertEquals("Discard leaves without saving", emptySet<String>(), hidden())
    }
}
