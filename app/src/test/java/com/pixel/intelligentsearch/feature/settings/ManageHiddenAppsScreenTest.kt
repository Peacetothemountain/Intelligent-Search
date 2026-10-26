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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

    @Test
    fun switchesWaitForSaveAndResetPutsTheSavedAppsBack() {
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Reset").assertIsNotEnabled()

        compose.onNodeWithText("Alpha").performClick()
        compose.settle(steps = 5)
        assertEquals("nothing is saved before Save", emptySet<String>(), hidden())
        compose.onNodeWithText("Reset").assertIsEnabled()

        compose.onNodeWithText("Reset").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Save").assertIsNotEnabled()

        compose.onNodeWithText("Beta").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Save").performClick()
        compose.settle(steps = 5)
        assertEquals(setOf("com.example.beta"), hidden())
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun selectAllHidesEveryAppShownAndOnlyThoseMatchingTheSearch() {
        compose.onNodeWithText("Select All").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Save").performClick()
        compose.settle(steps = 5)
        assertEquals(setOf("com.example.alpha", "com.example.beta"), hidden())

        // Off again shows them all.
        compose.onNodeWithText("Select All").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Save").performClick()
        compose.settle(steps = 5)
        assertEquals(emptySet<String>(), hidden())

        // While searching it acts on the matching apps only.
        compose.onNodeWithContentDescription("Search").performClick()
        compose.settle(steps = 5)
        compose.onNode(hasSetTextAction()).performTextInput("beta")
        compose.settle(steps = 5)
        compose.onNodeWithText("Select All").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Save").performClick()
        compose.settle(steps = 5)
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
