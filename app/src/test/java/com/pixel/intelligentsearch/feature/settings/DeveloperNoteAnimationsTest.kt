package com.pixel.intelligentsearch.feature.settings

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.system.SearchRoleManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class DeveloperNoteAnimationsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun setUp() = hilt.inject()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun setupReachesTheFurthestStepThatIsTrue() {
        assertEquals(0, setupStepReached(usbDebugging = false, listed = false, isDefault = false))
        assertEquals(1, setupStepReached(usbDebugging = true, listed = false, isDefault = false))
        assertEquals(2, setupStepReached(usbDebugging = true, listed = true, isDefault = false))
        assertEquals(3, setupStepReached(usbDebugging = true, listed = true, isDefault = true))
    }

    @Test
    fun aDefaultSearchEngineIsDoneEvenWithUsbDebuggingTurnedBackOff() {
        assertEquals(3, setupStepReached(usbDebugging = false, listed = true, isDefault = true))
        assertEquals(2, setupStepReached(usbDebugging = false, listed = true, isDefault = false))
    }

    @Test
    fun theLoopTypesIntThenPausesThenFinishesTheName() {
        assertEquals(0, SearchLoopTimes.typedAt(0f))
        assertEquals("Int", SearchLoopTimes.NAME.take(SearchLoopTimes.typedAt(SearchLoopTimes.RESULT_POPS)))
        assertEquals(3, SearchLoopTimes.typedAt(SearchLoopTimes.REST_OF_NAME - 0.01f))
        assertEquals(SearchLoopTimes.NAME.length, SearchLoopTimes.typedAt(SearchLoopTimes.ENTER))
    }

    @Test
    fun theStillFrameHasTheNameTypedAndTheAppNotOpening() {
        assertEquals(SearchLoopTimes.NAME.length, SearchLoopTimes.typedAt(SearchLoopTimes.STILL))
        assertEquals(0f, SearchLoopTimes.openedAt(SearchLoopTimes.STILL))
    }

    @Test
    fun theAppOpensAfterEnterAndComesToRestBeforeTheLoopEnds() {
        assertEquals(0f, SearchLoopTimes.openedAt(SearchLoopTimes.ENTER))
        assertTrue(SearchLoopTimes.openedAt(SearchLoopTimes.OPENS + 0.05f) in 0.01f..0.99f)
        assertTrue(SearchLoopTimes.openedAt(SearchLoopTimes.LEAVES) > 0.999f)
    }

    @Test
    fun usbDebuggingCountsWhenOnlyTheUsbConnectionReportsIt() {
        // Seen on the Pixel 11: USB debugging on, and the row read as if the setting said off.
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        assertEquals(false, isUsbDebuggingOn(context))
        @Suppress("DEPRECATION")
        context.sendStickyBroadcast(Intent("android.hardware.usb.action.USB_STATE").putExtra("adb", true))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(true, isUsbDebuggingOn(context))
    }

    @Test
    fun finishedSetupSaysOnlyThatItIsAllSet() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { SetupAllSet() }
        }
        compose.waitForIdle()
        compose.onNodeWithText("You're all set").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("Search engine list")).assertCountEquals(0)
    }

    @Test
    fun finishedSetupKeepsTheRowsWithAllSetUnderThem() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { SetupStatusCard(LocalContext.current, 3, true) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Search engine list").assertExists()
        compose.onNodeWithText("You're all set").assertExists()
    }

    @Test
    fun unfinishedSetupDoesNotSayItIsAllSet() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { SetupStatusCard(LocalContext.current, 2, true) }
        }
        compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.hasText("You're all set")).assertCountEquals(0)
    }

    private fun showPath() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { SetupPath(LocalContext.current) }
        }
        compose.waitForIdle()
    }

    @Test
    fun withNothingDoneOnlyUsbDebuggingIsTappable() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        showPath()
        compose.onNodeWithText("Turn this on", substring = true).assertHasClickAction()
        compose.onNodeWithText("Run the commands", substring = true).assertHasNoClickAction()
        compose.onNodeWithText("Choose it in Settings", substring = true).assertHasNoClickAction()
    }

    @Test
    fun withUsbDebuggingOnTheNextStepCopiesTheCommands() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        showPath()
        compose.onNodeWithText("On", useUnmergedTree = true).assertHasNoClickAction()
        compose.onNodeWithText("Run the commands", substring = true).performClick()
        compose.waitForIdle()
        val copied = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString()
        assertEquals(SearchRoleManager.ADB_ACTIVATION_COMMANDS, copied)
    }
}
