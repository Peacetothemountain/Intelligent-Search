package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.biometrics.BiometricManager
import android.os.Looper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
    private var backCalls = 0
    private lateinit var dispatcher: OnBackPressedDispatcher

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        // No screen lock or biometrics on this device, so the page opens without a prompt.
        Shadow.extract<ShadowBiometricManager>(context.getSystemService(BiometricManager::class.java)).setCanAuthenticate(false)
        disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            IntelligentSearchTheme(darkTheme = false) {
                ManageHiddenAppsScreen(prefs, onBack = { backCalls++ }, loadApps = {
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

    /**
     * Search opens in a pill at the bottom of the page, so the title and Enable All stay where they are, and the X
     * closes it again. (The pill's slide out of the halftone is drawn by a shader; its timing is in BottomSearchTest.)
     */
    @Test
    fun searchOpensAtTheBottomLeavingTheTitleAndTheXClosesIt() {
        val title = compose.onNodeWithText("Manage Hidden Apps").getBoundsInRoot()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        compose.onNodeWithContentDescription("Search").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Manage Hidden Apps").assertExists()
        compose.onNodeWithText("Enable All").assertExists()
        assertEquals("the title doesn't move", title, compose.onNodeWithText("Manage Hidden Apps").getBoundsInRoot())
        val field = compose.onNode(hasSetTextAction()).getBoundsInRoot()
        assertTrue("the pill is at the bottom of the page, far below the title", field.top > title.bottom + 1000.dp)

        compose.onNodeWithContentDescription("Close search").performClick()
        compose.settle(steps = 5)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Manage Hidden Apps").assertExists()
    }

    @Test
    fun backClosesSearchBeforeLeaving() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.settle(steps = 5)
        compose.onNode(hasSetTextAction()).performTextInput("alp")
        compose.settle(steps = 5)
        compose.onNodeWithText("Beta").assertDoesNotExist()

        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.settle(steps = 5)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Beta").assertExists()
        assertEquals("still on the page", 0, backCalls)
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
