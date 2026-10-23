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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.glance.GlanceAlarm
import com.pixel.intelligentsearch.core.glance.GlanceBirthday
import com.pixel.intelligentsearch.core.glance.GlanceSnapshot
import com.pixel.intelligentsearch.core.glance.GlanceSource
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.core.glance.NotificationIndex
import com.pixel.intelligentsearch.core.glance.NotificationAccessListener
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
        alarm = GlanceAlarm(System.currentTimeMillis() + 2 * 60 * 60 * 1000L),
        birthdays = listOf(GlanceBirthday(7, "lk", "Ana Lima", 0, LocalDate.now(), turning = null))
    )

    private val alarmRow get() = compose.onNodeWithText("Alarm · ", substring = true)

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
        index.clear()
    }

    /** How many collectors At a Glance has on the source right now. */
    private val collecting = java.util.concurrent.atomic.AtomicInteger()
    private val source = GlanceSource { _, _ ->
        kotlinx.coroutines.flow.flow {
            collecting.incrementAndGet()
            try {
                emit(snapshot)
                kotlinx.coroutines.awaitCancellation()
            } finally {
                collecting.decrementAndGet()
            }
        }
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
                CompositionLocalProvider(LocalGlanceSource provides source) {
                    SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
                }
            }
        }
        compose.settle()
    }

    @Test
    fun atAGlanceSitsAboveTheRecentSearches() {
        prefs.edit().put(SettingsManager.SEARCH_CONTACTS, true).commit()
        showOverlay(history = listOf("weather tomorrow"))
        val glance = compose.onNodeWithTag(GLANCE_HEADER_TAG).getUnclippedBoundsInRoot()
        val alarm = alarmRow.getUnclippedBoundsInRoot()
        val birthday = compose.onNodeWithText("Ana Lima's Birthday").getUnclippedBoundsInRoot()
        val recent = compose.onNodeWithText("Recent").getUnclippedBoundsInRoot()
        assertTrue("heading, alarm, birthday, then Recent", glance.top < alarm.top && alarm.top < birthday.top && birthday.top < recent.top)
        assertEquals("the empty search reads At a Glance", 1, collecting.get())
    }

    /** At a Glance is one squarish card in the top start corner holding every item, not a full-width card for each. */
    @Test
    fun atAGlanceIsOneCardInTheTopStartCorner() {
        prefs.edit().put(SettingsManager.SEARCH_CONTACTS, true).commit()
        showOverlay()
        val heading = compose.onNodeWithTag(GLANCE_HEADER_TAG).getUnclippedBoundsInRoot()
        val birthday = compose.onNodeWithText("Ana Lima's Birthday").getUnclippedBoundsInRoot()
        val alarm = alarmRow.getUnclippedBoundsInRoot()
        val clock = compose.onNodeWithTag(GLANCE_CLOCK_TAG).getUnclippedBoundsInRoot()
        assertTrue("the card starts at the list's start margin", clock.left < 40.dp)
        assertTrue("the card is about 62% of the 412 dp width, not all of it", heading.right < 300.dp)
        assertTrue("the clock is in the card's top start corner", clock.right <= heading.left && clock.top < alarm.top)
        assertEquals("the clock is 64 dp", 64f, (clock.right - clock.left).value, 0.5f)
        // The heading is in the top end corner: as high as the clock, and 8 dp short of the rows' own end, which
        // keeps it 14 dp in from the card's side (the rows sit 6 dp in), clear of the rounded corner.
        assertTrue("the heading sits at the top, level with the clock", kotlin.math.abs((heading.top - clock.top).value) < 2f)
        assertEquals("the heading ends 8 dp short of the rows", (alarm.right - GLANCE_HEADER_END_INSET).value, heading.right.value, 0.6f)
        assertTrue("the items sit inside the card, under its heading", birthday.right <= alarm.right + 0.5.dp && birthday.top > heading.bottom)
    }

    @Test
    fun showClockOffLeavesTheHeadingWithoutTheClock() {
        prefs.edit().put(SettingsManager.GLANCE_CLOCK, false).commit()
        showOverlay()
        compose.onNodeWithTag(GLANCE_HEADER_TAG).assertExists()
        compose.onNodeWithTag(GLANCE_CLOCK_TAG).assertDoesNotExist()
        alarmRow.assertExists()
        // Still in the top end corner without the clock beside it.
        val heading = compose.onNodeWithTag(GLANCE_HEADER_TAG).getUnclippedBoundsInRoot()
        val alarm = alarmRow.getUnclippedBoundsInRoot()
        assertEquals((alarm.right - GLANCE_HEADER_END_INSET).value, heading.right.value, 0.6f)
    }

    @Test
    fun eachItemsOwnSwitchHidesIt() {
        prefs.edit().put(SettingsManager.SEARCH_CONTACTS, true).put(SettingsManager.GLANCE_ALARM, false).commit()
        showOverlay()
        alarmRow.assertDoesNotExist()
        compose.onNodeWithText("Ana Lima's Birthday").assertExists()
    }

    @Test
    fun theBirthdaysSwitchHidesThemEvenWithContactSearchOn() {
        prefs.edit().put(SettingsManager.SEARCH_CONTACTS, true).put(SettingsManager.GLANCE_BIRTHDAYS, false).commit()
        showOverlay()
        compose.onNodeWithText("Ana Lima's Birthday").assertDoesNotExist()
        alarmRow.assertExists()
    }

    @Test
    fun withEveryItemSwitchedOffNothingIsRead() {
        prefs.edit()
            .put(SettingsManager.GLANCE_ALARM, false).put(SettingsManager.GLANCE_TIMERS, false)
            .put(SettingsManager.GLANCE_MEDIA, false).put(SettingsManager.GLANCE_BIRTHDAYS, false)
            .commit()
        showOverlay()
        compose.onNodeWithTag(GLANCE_HEADER_TAG).assertDoesNotExist()
        assertEquals(0, collecting.get())
    }

    /** The card is about 62% of the list, 232 to 340 dp, wider with larger text, and the whole width where that won't fit. */
    @Test
    fun theCardsWidthFollowsTheListAndTheText() {
        assertEquals(380f * 0.62f, glanceCardWidth(380.dp, 1f).value, 0.01f)
        assertEquals(232f, glanceCardWidth(300.dp, 1f).value, 0.01f)
        assertEquals(340f, glanceCardWidth(800.dp, 1f).value, 0.01f)
        assertEquals("larger text widens the narrowest card", 232f * 1.3f, glanceCardWidth(380.dp, 1.3f).value, 0.01f)
        assertEquals("a narrow window gets the whole width", 208f, glanceCardWidth(208.dp, 1f).value, 0.01f)
        assertEquals("double text gets the whole width", 380f, glanceCardWidth(380.dp, 2f).value, 0.01f)
    }

    @Test
    fun atAGlanceLeavesAsTypingStarts() {
        showOverlay(query = "ana")
        compose.onNodeWithTag(GLANCE_HEADER_TAG).assertDoesNotExist()
        compose.onNodeWithText("Ana Lima's Birthday").assertDoesNotExist()
    }

    @Test
    fun nothingIsReadForAtAGlanceWhileTyping() {
        showOverlay(query = "ana")
        assertEquals("At a Glance kept listening while typing", 0, collecting.get())
    }

    @Test
    fun birthdaysNeedContactSearch() {
        prefs.edit().put(SettingsManager.SEARCH_CONTACTS, false).commit()
        showOverlay()
        compose.onNodeWithText("Ana Lima's Birthday").assertDoesNotExist()
        alarmRow.assertExists()
    }

    @Test
    fun theAtAGlanceSwitchHidesTheSection() {
        prefs.edit().put(SettingsManager.GLANCE_ENABLED, false).commit()
        showOverlay()
        compose.onNodeWithTag(GLANCE_HEADER_TAG).assertDoesNotExist()
    }

    private fun grantNotificationSearch() {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(NotificationAccessListener.component(context), true)
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
        compose.onNodeWithText("Dinner at the café at 8?", substring = true).performClick()
        compose.settle(steps = 3)
        val started = shadowOf(compose.activity.application).nextStartedActivity
        assertEquals(opens.component, started?.component)
    }

    @Test
    fun withoutNotificationAccessNoNotificationIsSearched() {
        indexDinner()
        showOverlay(query = "dinner")
        compose.onNodeWithText("Dinner at the café at 8?", substring = true).assertDoesNotExist()
    }

    @Test
    fun revokedAccessForgetsTheIndexAsSearchOpens() {
        indexDinner()
        showOverlay(query = "dinner")
        compose.onNodeWithText("Dinner at the café at 8?", substring = true).assertDoesNotExist()
        assertTrue(index.entries.value.isEmpty())
    }

    /** A notification row has the title and one smaller line, as the file and contact rows do. */
    @Test
    fun aNotificationRowsSecondLineNamesTheAppTheAgeAndTheText() {
        val entry = IndexedNotification("k", "com.example.chat", "Chat", "Ana Lima", " Dinner at 8? ", postedAt = 0L)
        assertEquals("Chat · 5 min ago · Dinner at 8?", notificationDetail(entry, now = 5 * 60_000L))
        assertEquals("Chat · Now", notificationDetail(entry.copy(text = ""), now = 0L))
    }
}
