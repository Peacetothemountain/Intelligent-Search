package com.pixel.intelligentsearch.feature.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Singleton

/**
 * With results over the bottom bar, the Lens, Voice and Assistant rows head the list on a phone held upright. On a
 * phone on its side those three rows alone filled the list, so the overlay opened with the best match below the fold;
 * there they follow the results instead.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ShortWindowResultsTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit()
            .put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false)
            .put(SettingsManager.BOTTOM_SEARCH, true)
            .put(SettingsManager.BOTTOM_SEARCH_RESULT, true)
            .commit()
    }

    /**
     * The overlay opened on [query] with its results already found, as when it comes back to a search or the results
     * keep up with typing: the list is laid out once, with the rows and the results together.
     */
    private fun search(query: String) {
        compose.mainClock.autoAdvance = false
        var showOverlay by mutableStateOf(false)
        lateinit var viewModel: SearchViewModel
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                viewModel = hiltViewModel()
                if (showOverlay) {
                    SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
                }
            }
        }
        compose.settle()
        viewModel.onQueryChanged(query)
        compose.settle()
        showOverlay = true
        compose.settle()
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    @Config(qualifiers = "w915dp-h411dp-land-xhdpi")
    fun onAPhoneOnItsSideTheBestMatchOpensInView() {
        search("camera")
        compose.onNodeWithText("BEST MATCH", useUnmergedTree = true).assertIsDisplayed()
        // The Lens row comes after the results: below the best match, or not even laid out yet below the fold.
        val lens = compose.onAllNodesWithText("Search with Google Lens", useUnmergedTree = true).fetchSemanticsNodes()
        val bestMatchTop = compose.onNodeWithText("BEST MATCH", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue("the results come before the Lens row", lens.all { it.boundsInRoot.top > bestMatchTop })
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun uprightTheLensVoiceAndAssistantRowsStillLeadTheList() {
        search("camera")
        compose.onNodeWithText("BEST MATCH", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("the Lens row heads the list", top("Search with Google Lens") < top("BEST MATCH"))
    }
}
