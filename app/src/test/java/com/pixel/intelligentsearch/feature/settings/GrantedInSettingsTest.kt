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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.launch.FloatingSearchOverlays
import com.pixel.intelligentsearch.feature.launch.NotificationShortcut
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Declined for good, a permission can only be allowed on App info. Coming back from there with it allowed, what the
 * user was turning on turns on: the Contacts switch, the weather where they are, the Notification Shortcut. It
 * stayed off until tapped again, the screen not noticing the grant.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class GrantedInSettingsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private class PageLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private val page = PageLifecycle()

    @Before fun setUp() {
        hilt.inject()
        // The pages' Material Morph animation (on by default) never idles; these tests are about their rows.
        prefs.edit().clear().putBoolean(SettingsManager.MORPH_ANIMATION_ENABLED.name, false).commit()
        context.getSharedPreferences("declined_permissions", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(false)
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        FloatingSearchOverlays.sync(context)
    }

    /** Android's prompt: "Don't allow" a second time, after which Android won't ask again. */
    private val declinedForGood = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                val permissions = if (input is Array<*>) input.map { it as String } else listOf(input as String)
                permissions.forEach { shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(it, false) }
                @Suppress("UNCHECKED_CAST")
                val result: Any = if (input is Array<*>) permissions.associateWith { false } else false
                dispatchResult(requestCode, result as O)
            }
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides declinedForGood,
                    LocalLifecycleOwner provides page
                ) { content() }
            }
        }
        compose.waitForIdle()
    }

    /** Declined once before: Android shows its reason as it asks, and stops after this decline. */
    private fun declinedBefore(permission: String) {
        shadowOf(context).denyPermissions(permission)
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(permission, true)
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    /** "Open Settings", then on Android's page, allowing [permission] there or not, and Back. */
    private fun openSettingsAndComeBack(permission: String, allow: Boolean): Intent? {
        shadowOf(context).clearNextStartedActivities()
        tap("Open Settings")
        val opened = shadowOf(context).nextStartedActivity
        page.registry.currentState = Lifecycle.State.CREATED
        if (allow) shadowOf(context).grantPermissions(permission)
        page.registry.currentState = Lifecycle.State.RESUMED
        compose.waitForIdle()
        return opened
    }

    @Test
    fun contactsAllowedOnAppInfoTurnTheSearchSourcesSwitchOn() {
        declinedBefore(Manifest.permission.READ_CONTACTS)
        show { SearchSourcesScreen({}, {}) }
        compose.onNode(isToggleable() and hasContentDescription("Contacts")).performClick()
        compose.waitForIdle()
        tap("Allow")
        assertFalse(prefs[SettingsManager.SEARCH_CONTACTS])

        openSettingsAndComeBack(Manifest.permission.READ_CONTACTS, allow = true)
        assertTrue("the switch the user turned on is on", prefs[SettingsManager.SEARCH_CONTACTS])
    }

    @Test
    fun comingBackWithoutAllowingLeavesContactsOff() {
        declinedBefore(Manifest.permission.READ_CONTACTS)
        show { SearchSourcesScreen({}, {}) }
        compose.onNode(isToggleable() and hasContentDescription("Contacts")).performClick()
        compose.waitForIdle()
        tap("Allow")

        openSettingsAndComeBack(Manifest.permission.READ_CONTACTS, allow = false)
        assertFalse(prefs[SettingsManager.SEARCH_CONTACTS])
        // Allowed some other time, without the user asking here: the switch is theirs to turn on.
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        page.registry.currentState = Lifecycle.State.CREATED
        page.registry.currentState = Lifecycle.State.RESUMED
        compose.waitForIdle()
        assertFalse(prefs[SettingsManager.SEARCH_CONTACTS])
    }

    @Test
    fun locationAllowedOnAppInfoLooksTheWeatherUpAgain() {
        declinedBefore(Manifest.permission.ACCESS_COARSE_LOCATION)
        var lookedUp = 0
        show {
            val location = rememberApproximateLocationRequest { lookedUp++ }
            TextButton(onClick = location::request) { Text("Use Approximate Location") }
        }
        tap("Use Approximate Location")
        tap("Allow")
        assertEquals(0, lookedUp)

        openSettingsAndComeBack(Manifest.permission.ACCESS_COARSE_LOCATION, allow = true)
        assertEquals("the weather card asks for the forecast again", 1, lookedUp)
    }

    @Test
    fun contactsAllowedOnAppInfoFinishThatSetupStep() {
        declinedBefore(Manifest.permission.READ_CONTACTS)
        show { SetupChecklistScreen(prefs, {}, {}) }
        // Finish Setting Up asks Android at once and sends a decline for good straight to App info.
        shadowOf(context).clearNextStartedActivities()
        tap("Contacts")
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(context).nextStartedActivity?.action)

        page.registry.currentState = Lifecycle.State.CREATED
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        page.registry.currentState = Lifecycle.State.RESUMED
        compose.waitForIdle()
        assertTrue(prefs[SettingsManager.SEARCH_CONTACTS])
    }

    @Test
    fun notificationsAllowedInSettingsTurnTheNotificationShortcutOn() {
        declinedBefore(Manifest.permission.POST_NOTIFICATIONS)
        show { LaunchPortalScreen(prefs) {} }
        compose.onNode(isToggleable() and hasText("Notification Shortcut")).performScrollTo().performClick()
        compose.waitForIdle()
        tap("Allow")

        val opened = openSettingsAndComeBack(Manifest.permission.POST_NOTIFICATIONS, allow = true)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened?.action)
        assertEquals("opened over Launch Portal, so Back comes back to it", 0, opened!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(NotificationShortcut.canPost(context))
        assertTrue("the shortcut the user turned on is on", prefs[SettingsManager.NOTIFICATION_SHORTCUT])
    }
}
