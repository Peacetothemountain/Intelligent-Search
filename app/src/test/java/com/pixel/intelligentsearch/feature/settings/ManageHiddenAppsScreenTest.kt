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
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Search apps")))
            .assertIsFocused()

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
    fun selectAllResetAndSaveSitInTheTopBarAsOnTheOtherPages() {
        // Select All at the top left, beside Back; Reset and Save at the top right, as on Application Search.
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        val selectAll = compose.onNodeWithContentDescription("Select All").getBoundsInRoot()
        val reset = compose.onNodeWithContentDescription("Reset to Saved State").getBoundsInRoot()
        val save = compose.onNodeWithContentDescription("Save Hidden Apps").getBoundsInRoot()
        assertTrue("Select All is just right of Back", selectAll.left >= back.right && selectAll.left < reset.left)
        assertEquals("all in the top bar", back.top, selectAll.top)
        assertEquals(back.top, reset.top)
        assertTrue("Save is right of Reset", save.left >= reset.right)
        compose.onNodeWithText("Select All").assertDoesNotExist()
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
    fun selectAllHidesEveryAppShownAndOnlyThoseMatchingTheSearch() {
        compose.onNodeWithContentDescription("Select All").assertIsOff()
        tap("Select All")
        compose.onNodeWithContentDescription("Select All").assertIsOn()
        tap("Save Hidden Apps")
        assertEquals(setOf("com.example.alpha", "com.example.beta"), hidden())

        // Checked again shows them all.
        tap("Select All")
        tap("Save Hidden Apps")
        assertEquals(emptySet<String>(), hidden())

        // While searching it acts on the matching apps only.
        tap("Search")
        compose.onNode(hasSetTextAction()).performTextInput("beta")
        compose.settle(steps = 5)
        tap("Select All")
        tap("Save Hidden Apps")
        assertEquals(setOf("com.example.beta"), hidden())
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
