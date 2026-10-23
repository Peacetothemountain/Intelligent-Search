package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.ResultShortcutStore
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.search.ResultShortcut
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Nicknames & Triggers lists what was set and edits or deletes it. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class NicknamesTriggersScreenTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val store get() = ResultShortcutStore(SettingsManager(ApplicationProvider.getApplicationContext<Context>()))
    private val john = "contact:john"
    private val spotify = "app:com.spotify.music/com.spotify.music.Main@0"

    @Before fun setUp() {
        hilt.inject()
        store.update { it.withNickname(john, "John Smith", "Dad").withTrigger(spotify, "Spotify", "sp").withPinned(spotify, "Spotify", true) }
    }

    @Test
    fun summariesEndWithAPeriod() {
        assertEquals("Nicknames: Dad, Father. Trigger Word: jo.", shortcutSummary(ResultShortcut(john, "John", nicknames = listOf("Dad", "Father"), trigger = "jo")))
        assertEquals("Nickname: Dad.", shortcutSummary(ResultShortcut(john, "John", nicknames = listOf("Dad"))))
        assertEquals("Trigger Word: sp.", shortcutSummary(ResultShortcut(spotify, "Spotify", trigger = "sp")))
    }

    @Test
    fun everyNamedResultIsListed() {
        compose.setContent { NicknamesTriggersScreen(onBack = {}) }
        compose.onNodeWithText("John Smith").assertExists()
        compose.onNodeWithText("Nickname: Dad.").assertExists()
        compose.onNodeWithText("Trigger Word: sp.").assertExists()
    }

    @Test
    fun deleteClearsTheNamesButKeepsThePin() {
        compose.setContent { NicknamesTriggersScreen(onBack = {}) }
        compose.onNodeWithText("Spotify").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitForIdle()
        assertEquals("", store.current[spotify]?.trigger)
        assertTrue(store.current.isPinned(spotify))
        compose.onNodeWithText("Trigger Word: sp.").assertDoesNotExist()
    }

    @Test
    fun aNicknameCanBeAddedAndAnotherRemoved() {
        compose.setContent { NicknamesTriggersScreen(onBack = {}) }
        compose.onNodeWithText("John Smith").performClick()
        compose.onNodeWithContentDescription("Remove Nickname Dad").performClick()
        compose.onNode(hasSetTextAction() and hasText("Add Nickname")).performTextInput("Father")
        compose.onNodeWithContentDescription("Add Nickname").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Father"), store.current.nicknamesOf(john))
    }

    @Test
    fun aTakenTriggerWordCantBeSaved() {
        compose.setContent { NicknamesTriggersScreen(onBack = {}) }
        compose.onNodeWithText("John Smith").performClick()
        compose.onNode(hasSetTextAction() and hasText("Trigger Word")).performTextReplacement("SP")
        compose.onNodeWithText("Already Opens Spotify.").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        assertNull(store.current[john]?.trigger?.takeIf { it.isNotEmpty() })
    }
}
