package com.pixel.intelligentsearch.feature.search

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.glance.GlanceBattery
import com.pixel.intelligentsearch.core.glance.GlanceBirthday
import com.pixel.intelligentsearch.core.glance.GlanceSnapshot
import com.pixel.intelligentsearch.core.glance.GlanceSource
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.core.glance.NotificationIndex
import com.pixel.intelligentsearch.core.glance.NotificationSearchListener
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import javax.inject.Inject

/**
 * At a Glance and the Notifications section in the real overlay: At a Glance sits above the recent searches on
 * the empty search and leaves as typing starts; matching notifications show as their own section and open what the
 * notification opens.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class AtAGlanceOverlayTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    @Inject lateinit var index: NotificationIndex

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private val snapshot = GlanceSnapshot(
        birthdays = listOf(GlanceBirthday(7, "lk", "Ana Lima", 0, LocalDate.now(), turning = null)),
        battery = GlanceBattery(15, pluggedIn = false, statusLabel = null)
    )

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
        index.clear()
    }

    private fun showOverlay(query: String = "", history: List<String> = emptyList()) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    history.forEach(viewModel::addSearchHistory)
                    if (query.isNotEmpty()) viewModel.onQueryChanged(query)
                }
                CompositionLocalProvider(LocalGlanceSource provides GlanceSource { _, _ -> flowOf(snapshot) }) {
                    SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
                }
            }
        }
        compose.settle()
    }

    @Test
    fun atAGlanceSitsAboveTheRecentSearches() {
        showOverlay(history = listOf("weather tomorrow"))
        val glance = compose.onNodeWithTag(GLANCE_HEADER_TAG).getUnclippedBoundsInRoot()
        val birthday = compose.onNodeWithText("Ana Lima's Birthday").getUnclippedBoundsInRoot()
        val battery = compose.onNodeWithText("Battery · 15%").getUnclippedBoundsInRoot()
        val recent = compose.onNodeWithText("Recent").getUnclippedBoundsInRoot()
        assertTrue("heading, birthday, battery, then Recent", glance.top < birthday.top && birthday.top < battery.top && battery.top < recent.top)
    }

    @Test
    fun atAGlanceLeavesAsTypingStarts() {
        showOverlay(query = "ana")
        compose.onNodeWithTag(GLANCE_HEADER_TAG).assertDoesNotExist()
        compose.onNodeWithText("Ana Lima's Birthday").assertDoesNotExist()
    }

    @Test
    fun theAtAGlanceSwitchHidesTheSection() {
        prefs.edit().put(SettingsManager.GLANCE_ENABLED, false).commit()
        showOverlay()
        compose.onNodeWithTag(GLANCE_HEADER_TAG).assertDoesNotExist()
    }

    private fun grantNotificationSearch() {
        prefs.edit().put(SettingsManager.SEARCH_NOTIFICATIONS, true).commit()
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(NotificationSearchListener.component(context), true)
    }

    private fun indexDinner(): Intent {
        val opens = Intent(Intent.ACTION_VIEW).setClassName("com.example.chat", "com.example.chat.Conversation")
        index.post(
            IndexedNotification(
                key = "0|com.example.chat|1|null|10001", packageName = "com.example.chat", appLabel = "Chat",
                title = "Ana Lima", text = "Dinner at the café at 8?", postedAt = System.currentTimeMillis(),
                contentIntent = PendingIntent.getActivity(context, 0, opens, PendingIntent.FLAG_IMMUTABLE)
            )
        )
        return opens
    }

    @Test
    fun matchingNotificationsShowAndOpenWhatTheyOpen() {
        grantNotificationSearch()
        val opens = indexDinner()
        showOverlay(query = "dinner")
        compose.onNodeWithText("Notifications").assertExists()
        compose.onNodeWithText("Dinner at the café at 8?").performClick()
        compose.settle(steps = 3)
        val started = shadowOf(compose.activity.application).nextStartedActivity
        assertEquals(opens.component, started?.component)
    }

    @Test
    fun withoutTheOptInNoNotificationIsSearched() {
        indexDinner()
        showOverlay(query = "dinner")
        compose.onNodeWithText("Dinner at the café at 8?").assertDoesNotExist()
    }

    @Test
    fun revokedAccessForgetsTheIndexAsSearchOpens() {
        prefs.edit().put(SettingsManager.SEARCH_NOTIFICATIONS, true).commit()
        indexDinner()
        showOverlay(query = "dinner")
        compose.onNodeWithText("Dinner at the café at 8?").assertDoesNotExist()
        assertTrue(index.entries.value.isEmpty())
    }
}
