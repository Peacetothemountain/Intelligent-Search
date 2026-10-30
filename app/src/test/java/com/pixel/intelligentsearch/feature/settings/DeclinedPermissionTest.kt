package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.launch.FloatingSearchOverlays
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast

/**
 * Every permission the app asks for says access is off, and offers Android's settings, only once the user has
 * declined it for good, not when they only closed Android's prompt (Back, or a tap outside), which Android shows
 * again next time. A decline is forgotten once the permission is allowed: Android can take it back later (a one-time
 * grant lapsing, an unused app's permissions being reset) and ask afresh.
 *
 * Android's rules, as modelled here: a first "Don't allow" sets its reason flag, a second clears it for good, and a
 * closed prompt changes nothing.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class DeclinedPermissionTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        // The pages' Material Morph animation (on by default) never idles; these tests are about their rows.
        prefs.edit().clear().putBoolean(SettingsManager.MORPH_ANIMATION_ENABLED.name, false).commit()
        ShadowSettings.setCanDrawOverlays(false)
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        FloatingSearchOverlays.sync(context)
    }

    private fun androidShowsItsReason(permission: String, shown: Boolean) =
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(permission, shown)

    /** What Android's prompt answers next: [allow], after [whileAsking] changes what Android remembers. */
    private var answer: Pair<Boolean, () -> Unit> = false to {}

    private val prompt = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                answer.second()
                @Suppress("UNCHECKED_CAST")
                val result: Any = if (input is Array<*>) input.associate { it as String to answer.first } else answer.first
                dispatchResult(requestCode, result as O)
            }
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides prompt) { content() }
            }
        }
        compose.waitForIdle()
    }

    /** One button per request that explains first, then asks Android. */
    private fun showRequests() = show {
        val contacts = rememberContactsRequest { }
        val location = rememberApproximateLocationRequest { }
        Column {
            TextButton(onClick = contacts::request) { Text("Ask Contacts") }
            TextButton(onClick = location::request) { Text("Ask Location") }
        }
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun shown(text: String) = runCatching { compose.onNodeWithText(text).assertExists() }.isSuccess

    @Test
    fun closingTheFirstContactsPromptDoesNotSayContactsAreOff() {
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
        androidShowsItsReason(Manifest.permission.READ_CONTACTS, false)
        showRequests()
        tap("Ask Contacts")
        tap("Allow")
        assertEquals("'Contacts Access Is Off' after only closing Android's first prompt", false, shown("Contacts Access Is Off"))
    }

    @Test
    fun aLapsedGrantClosedAgainDoesNotSayLocationIsOff() {
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        androidShowsItsReason(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        showRequests()
        // Declined once: Android then shows its reason.
        answer = false to { androidShowsItsReason(Manifest.permission.ACCESS_COARSE_LOCATION, true) }
        tap("Ask Location")
        tap("Allow")
        // Then allowed "Only this time".
        answer = true to {
            shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            androidShowsItsReason(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        }
        tap("Ask Location")
        tap("Allow")
        // The grant lapsed and Android asks afresh; the user only closes its prompt.
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        answer = false to {}
        tap("Ask Location")
        tap("Allow")
        assertEquals("'Location Access Is Off' after only closing a prompt Android shows again", false, shown("Location Access Is Off"))
    }

    @Test
    fun aDeclineIsForgottenOnceContactsAreSeenAllowed() {
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
        androidShowsItsReason(Manifest.permission.READ_CONTACTS, false)
        showRequests()
        answer = false to { androidShowsItsReason(Manifest.permission.READ_CONTACTS, true) }
        tap("Ask Contacts")
        tap("Allow")
        // Allowed in Android's settings since, and used: nothing is asked.
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        androidShowsItsReason(Manifest.permission.READ_CONTACTS, false)
        tap("Ask Contacts")
        // Android resets the permissions of an app left unused, and asks afresh; the user closes its prompt.
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
        answer = false to {}
        tap("Ask Contacts")
        tap("Allow")
        assertEquals(false, shown("Contacts Access Is Off"))
    }

    @Test
    fun openSettingsOnAnAccessIsOffDialogOpensAppInfoOverThisScreen() {
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.ACCESS_COARSE_LOCATION)
        showRequests()
        for ((ask, permission, off) in listOf(
            Triple("Ask Contacts", Manifest.permission.READ_CONTACTS, "Contacts Access Is Off"),
            Triple("Ask Location", Manifest.permission.ACCESS_COARSE_LOCATION, "Location Access Is Off"),
        )) {
            // Declined once before; this time the user picks Don't Allow, and Android stops showing its reason.
            androidShowsItsReason(permission, true)
            answer = false to { androidShowsItsReason(permission, false) }
            tap(ask)
            tap("Allow")
            tap("Open Settings")
            val opened = shadowOf(context).nextStartedActivity
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened?.action)
            // In Android settings' own task, Back went to whatever page those were last left on, and the search,
            // not told to wait, closed with what was typed.
            assertEquals("$off: App info opens in this screen's task, so Back comes back here", 0, opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Test
    fun aGrantMadeOnAppInfoIsSeenWhenSettingsComesBack() {
        val contacts = Manifest.permission.READ_CONTACTS
        shadowOf(context).denyPermissions(contacts)
        showRequests()
        androidShowsItsReason(contacts, true)
        answer = false to { androidShowsItsReason(contacts, false) }
        tap("Ask Contacts")
        tap("Allow")
        assertEquals("control: a decline for good says so", true, shown("Contacts Access Is Off"))
        tap("Not Now")

        // Allowed on App info, then Back to Settings.
        shadowOf(context).grantPermissions(contacts)
        prefs.edit().putBoolean(SettingsManager.TUTORIAL_COMPLETED.name, true).commit()
        Robolectric.buildActivity(SettingsActivity::class.java).create().start().resume().pause().stop().destroy()

        // Later Android takes it back and asks afresh; the user only closes its prompt.
        shadowOf(context).denyPermissions(contacts)
        answer = false to {}
        tap("Ask Contacts")
        tap("Allow")
        assertEquals(false, shown("Contacts Access Is Off"))
    }

    @Test
    fun closingAPromptFromFinishSettingUpDoesNotJumpToAppInfo() {
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALENDAR)
        androidShowsItsReason(Manifest.permission.READ_CONTACTS, false)
        androidShowsItsReason(Manifest.permission.READ_CALENDAR, false)
        show { SetupChecklistScreen(prefs, {}, {}) }
        for (step in listOf("Contacts", "Calendar")) {
            tap(step)
            assertNull("$step: a closed prompt opened App info", shadowOf(context).nextStartedActivity)
            assertNull("$step: a closed prompt said access was declined", ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun declinedForGoodFinishSettingUpStillOpensAppInfo() {
        shadowOf(context).denyPermissions(Manifest.permission.READ_CALENDAR)
        // Declined once before; this time the user picks Don't Allow, and Android stops showing its reason.
        androidShowsItsReason(Manifest.permission.READ_CALENDAR, true)
        answer = false to { androidShowsItsReason(Manifest.permission.READ_CALENDAR, false) }
        show { SetupChecklistScreen(prefs, {}, {}) }
        tap("Calendar")
        assertEquals("Calendar access was declined. You can allow it in Android settings.", ShadowToast.getTextOfLatestToast())
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(context).nextStartedActivity?.action)
    }

    @Test
    fun closingTheFirstNotificationsPromptDoesNotSayNotificationsAreOff() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        androidShowsItsReason(Manifest.permission.POST_NOTIFICATIONS, false)
        show { LaunchPortalScreen(prefs) {} }
        compose.onNode(isToggleable() and hasText("Notification Shortcut")).performScrollTo().performClick()
        compose.waitForIdle()
        tap("Allow")
        assertEquals(false, shown("Notifications Are Off"))
    }
}
