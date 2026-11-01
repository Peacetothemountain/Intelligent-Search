package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * At a Glance's Calendar Events switch, directly above Birthdays. Setup no longer asks for the calendar, so this
 * switch does: turned on without it, it explains, then asks Android, and turns on only once allowed. Declined, it
 * stays off; declined for good, it offers App info and turns on on the way back if the calendar was allowed there.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class AtAGlanceCalendarSettingTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private class PageLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private val page = PageLifecycle()

    /** The calendar requests Android's prompt saw. */
    private val asked = mutableListOf<String>()

    /** What Android's prompt answers next: [first] allows, after [second] changes what Android remembers. */
    private var answer: Pair<Boolean, () -> Unit> = false to {}

    private val prompt = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                asked += input as String
                answer.second()
                if (answer.first) shadowOf(context).grantPermissions(input)
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, answer.first as O)
            }
        }
    }

    private val calendarSwitch = isToggleable() and hasText("Calendar Events")

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().putBoolean(SettingsManager.MORPH_ANIMATION_ENABLED.name, false).commit()
        context.getSharedPreferences("declined_permissions", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(context).denyPermissions(Manifest.permission.READ_CALENDAR)
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR, false)
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
    }

    private fun show() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides prompt,
                    LocalLifecycleOwner provides page
                ) { Column { AtAGlanceSettingsCard() } }
            }
        }
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun tapSwitch() {
        compose.onNode(calendarSwitch).performClick()
        compose.waitForIdle()
    }

    @Test
    fun calendarEventsSitsDirectlyAboveBirthdays() {
        show()
        compose.onNodeWithText("Show Your Calendar Events for the Next 24 Hours.").assertExists()
        val switches = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { node -> node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString() }
        val titles = listOf("Media Controls", "Calendar Events", "Birthdays")
        val at = titles.map { title -> switches.indexOfFirst { it.contains(title) } }
        assertTrue("found each of $titles in $switches", at.none { it < 0 })
        assertEquals("Media Controls, then Calendar Events, then Birthdays in $switches", listOf(at[0], at[0] + 1, at[0] + 2), at)
        assertTrue(compose.onNode(calendarSwitch).getUnclippedBoundsInRoot().top < compose.onNode(isToggleable() and hasText("Birthdays")).getUnclippedBoundsInRoot().top)
    }

    @Test
    fun calendarEventsStartsOffAndHidesWithAtAGlance() {
        assertFalse(SettingsManager.GLANCE_CALENDAR.default)
        prefs.edit().put(SettingsManager.GLANCE_ENABLED, false).commit()
        show()
        compose.onNode(calendarSwitch).assertDoesNotExist()
    }

    @Test
    fun turningItOnWithoutTheCalendarAsksForItThenTurnsOn() {
        show()
        compose.onNode(calendarSwitch).assertIsOff()
        tapSwitch()
        compose.onNodeWithText("Allow Calendar Access?").assertExists()
        assertTrue("nothing is asked of Android before the explanation", asked.isEmpty())
        answer = true to {}
        tap("Allow")
        assertEquals(listOf(Manifest.permission.READ_CALENDAR), asked)
        assertTrue(prefs[SettingsManager.GLANCE_CALENDAR])
        compose.onNode(calendarSwitch).assertIsOn()
    }

    @Test
    fun declinedTheSwitchStaysOff() {
        show()
        tapSwitch()
        answer = false to { shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR, true) }
        tap("Allow")
        assertFalse(prefs[SettingsManager.GLANCE_CALENDAR])
        compose.onNode(calendarSwitch).assertIsOff()
        compose.onNodeWithText("Calendar Access Is Off").assertDoesNotExist()
    }

    @Test
    fun notNowLeavesItOffWithoutAsking() {
        show()
        tapSwitch()
        tap("Not Now")
        assertTrue(asked.isEmpty())
        assertFalse(prefs[SettingsManager.GLANCE_CALENDAR])
        compose.onNode(calendarSwitch).assertIsOff()
    }

    @Test
    fun declinedForGoodOffersAppInfoAndTurnsOnOnceAllowedThere() {
        // Declined once before; this time the user picks Don't Allow, and Android stops showing its reason.
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR, true)
        answer = false to { shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR, false) }
        show()
        tapSwitch()
        tap("Allow")
        compose.onNodeWithText("Calendar Access Is Off").assertExists()
        shadowOf(context).clearNextStartedActivities()
        tap("Open Settings")
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(context).nextStartedActivity?.action)
        assertFalse("off until allowed", prefs[SettingsManager.GLANCE_CALENDAR])

        // Allowed on App info, then Back.
        page.registry.currentState = Lifecycle.State.CREATED
        shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR)
        page.registry.currentState = Lifecycle.State.RESUMED
        compose.waitForIdle()
        assertTrue("the switch the user turned on is on", prefs[SettingsManager.GLANCE_CALENDAR])
        compose.onNode(calendarSwitch).assertIsOn()
    }

    @Test
    fun withTheCalendarAllowedItSwitchesStraightOnAndOff() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR)
        show()
        tapSwitch()
        compose.onNodeWithText("Allow Calendar Access?").assertDoesNotExist()
        assertTrue(asked.isEmpty())
        assertTrue(prefs[SettingsManager.GLANCE_CALENDAR])
        compose.onNode(calendarSwitch).assertIsOn()
        // Past the rows' tap debounce.
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        tapSwitch()
        assertFalse(prefs[SettingsManager.GLANCE_CALENDAR])
        compose.onNode(calendarSwitch).assertIsOff()
    }

    /** Taken back in Android's settings while away: shown off on the way back, though it is on underneath. */
    @Test
    fun aCalendarTakenBackShowsTheSwitchOff() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR)
        prefs.edit().put(SettingsManager.GLANCE_CALENDAR, true).commit()
        show()
        compose.onNode(calendarSwitch).assertIsOn()
        page.registry.currentState = Lifecycle.State.CREATED
        shadowOf(context).denyPermissions(Manifest.permission.READ_CALENDAR)
        page.registry.currentState = Lifecycle.State.RESUMED
        // The resume's read is a plain state write; the screen redraws with it on the next frame.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNode(calendarSwitch).assertIsOff()
        assertNull(compose.onAllNodes(hasText("Allow Calendar Access?")).fetchSemanticsNodes().firstOrNull())
    }
}
