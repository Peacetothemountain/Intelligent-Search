package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.backup.BackupManager
import com.pixel.intelligentsearch.core.backup.BackupPreferenceSchema
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Show Microphone in Search Bar (Appearance): off, the overlay's search bar has no voice search button. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class OverlayMicSettingTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
    }

    private fun showOverlay() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
            }
        }
        compose.settle()
    }

    @Test
    fun theMicIsShownByDefault() {
        assertTrue(SettingsManager.OVERLAY_SHOW_MIC.default)
        assertTrue(SettingsManager.OVERLAY_SHOW_MIC in SettingsManager.ALL)
        showOverlay()
        compose.onNodeWithContentDescription("Search by voice").assertIsDisplayed()
    }

    @Test
    fun turnedOffTheMicLeavesTheBar() {
        prefs.edit().put(SettingsManager.OVERLAY_SHOW_MIC, false).commit()
        showOverlay()
        compose.onAllNodesWithContentDescription("Search by voice").assertCountEquals(0)
        // The rest of the bar stays.
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithText("Search…").assertIsDisplayed()
    }

    @Test
    fun turningItBackOnBringsTheMicBack() {
        prefs.edit().put(SettingsManager.OVERLAY_SHOW_MIC, false).commit()
        showOverlay()
        prefs.edit().put(SettingsManager.OVERLAY_SHOW_MIC, true).commit()
        compose.settle(steps = 3)
        compose.onNodeWithContentDescription("Search by voice").assertIsDisplayed()
    }

    @Test
    fun theSettingIsBackedUpAndRestored() = runBlocking {
        assertEquals("BOOLEAN", BackupPreferenceSchema.expectedType(SettingsManager.OVERLAY_SHOW_MIC.name))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IntelligentSearchDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsManager(context)
        val backups = BackupManager(context, settings, db.historyDao(), StrongBoxSecurityManager(context), WidgetUpdater { })
        val uri = Uri.fromFile(File(context.cacheDir, "overlay_mic.json"))

        settings.updateSetting(SettingsManager.OVERLAY_SHOW_MIC, false)
        backups.exportToFile(uri, "correct horse battery").getOrThrow()
        settings.updateSetting(SettingsManager.OVERLAY_SHOW_MIC, true)

        backups.importFromFile(uri, "correct horse battery").getOrThrow()
        assertEquals(false, settings[SettingsManager.OVERLAY_SHOW_MIC])
        db.close()
    }
}
