package com.pixel.intelligentsearch.feature.search

import android.app.Application
import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The contact buttons in the real overlay: a search for two contacts puts one in the Best Match card (chips) and the
 * other in the contact rows (round buttons). They have no switch of their own: with contact search on they always
 * show, and with it off nothing is looked up.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class ContactActionsOverlayTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        ContactActionTargets.cached = null
        FakeContactBook.install(context)
        FakeContactBook.installContactApps(context)
        TutorialManager.completeTutorial(prefs)
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
    }

    @After fun clearCache() {
        ContactActionTargets.cached = null
    }

    @Test
    fun theBestMatchAndTheRowsGetButtons() {
        showOverlay()
        assertButtonsShown()
        // The check made on resume is kept for the next overlay.
        assertEquals(ContactActionTargets.resolve(context), ContactActionTargets.cached)
    }

    @Test
    fun theOldContactActionsSwitchNoLongerHidesThem() {
        // An older build's Contact Actions switch, left off, means nothing now.
        prefs.edit().putBoolean("contact_actions", false).commit()
        showOverlay()
        assertButtonsShown()
    }

    @Test
    fun theButtonsAreLookedUpWhileContactSearchIsOn() {
        showOverlay()
        assertNotNull(ContactActionTargets.cached)
    }

    @Test
    fun withContactSearchOffNothingIsLookedUp() {
        prefs.edit().putBoolean(SettingsManager.SEARCH_CONTACTS.name, false).commit()
        showOverlay(expectContacts = false)
        assertNull(ContactActionTargets.cached)
    }

    private fun assertButtonsShown() {
        // One Robin is the Best Match (chips), the other a row (round buttons); each has its own set.
        for (who in listOf("Robin Example", "Robin Sample")) {
            compose.onAllNodesWithContentDescription("Call $who").assertCountEquals(1)
            compose.onAllNodesWithContentDescription("Message $who").assertCountEquals(1)
            compose.onAllNodesWithContentDescription("Message $who on WhatsApp").assertCountEquals(1)
        }
        compose.onAllNodesWithContentDescription("Email Robin Example").assertCountEquals(1)
    }

    private fun showOverlay(expectContacts: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                LaunchedEffect(Unit) { viewModel.onQueryChanged(FakeContactBook.QUERY) }
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { _, _, _, _ -> },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
        if (!expectContacts) return
        // Both contacts are on screen, whatever the buttons.
        for (who in listOf("Robin Example", "Robin Sample")) {
            assertTrue("$who is shown", compose.onAllNodesWithText(who).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
