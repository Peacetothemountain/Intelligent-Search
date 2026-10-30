package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Turning on Direct Call asks to make phone calls, the app's one way: the shared rationale, then Android's prompt.
 * Declining keeps Direct Call on; a tap then opens the dialer with the number.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class PhoneCallsRequestTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val asked = mutableListOf<Any?>()

    /** Android's prompt, answering [allow]. */
    private fun owner(allow: Boolean) = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                asked += input
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, allow as O)
            }
        }
    }

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    private fun turnOnDirectCall(allow: Boolean = false) {
        val registry = owner(allow)
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                    ContactSearchScreen(prefs) {}
                }
            }
        }
        compose.waitForIdle()
        compose.onNode(isToggleable() and hasText("Direct Call")).performClick()
        compose.waitForIdle()
    }

    @Test
    fun turningItOnExplainsThenAsksToMakeCalls() {
        shadowOf(context).denyPermissions(Manifest.permission.CALL_PHONE)
        turnOnDirectCall()
        compose.onNodeWithText("Allow Phone Calls?").assertExists()
        compose.onNodeWithText("Allow").performClick()
        compose.waitForIdle()
        assertEquals(listOf<Any?>(Manifest.permission.CALL_PHONE), asked)
        // Declined, it stays on: a tap opens the dialer instead.
        assertTrue(prefs[SettingsManager.CONTACT_DIRECT_CALL])
    }

    @Test
    fun notNowStillTurnsItOnAndAsksNothing() {
        shadowOf(context).denyPermissions(Manifest.permission.CALL_PHONE)
        turnOnDirectCall()
        compose.onNodeWithText("Not Now").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<Any?>(), asked)
        assertTrue(prefs[SettingsManager.CONTACT_DIRECT_CALL])
    }

    @Test
    fun allowedAlreadyItAsksNothing() {
        shadowOf(context).grantPermissions(Manifest.permission.CALL_PHONE)
        turnOnDirectCall()
        compose.onNodeWithText("Allow Phone Calls?").assertDoesNotExist()
        assertEquals(emptyList<Any?>(), asked)
        assertTrue(prefs[SettingsManager.CONTACT_DIRECT_CALL])
    }
}
