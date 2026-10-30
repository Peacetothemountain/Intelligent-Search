package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import org.junit.Assert.assertEquals
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.robolectric.annotation.Config
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Approximate location is asked for the app's one way (from the overlay's weather card, its only use): at once when
 * allowed, otherwise after the shared rationale and Android's prompt.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ApproximateLocationRequestTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private var granted = 0
    private var asked = 0
    private var granting = false

    /** Android's prompt, answering [allow]. */
    private fun owner(allow: Boolean) = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                asked++
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, allow as O)
            }
        }
    }

    /** Shows a "Use My Location" button that asks the way the weather card does, and taps it. */
    private fun askWith(allow: Boolean) {
        if (!granting) shadowOf(context).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val registry = owner(allow)
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                    val request = rememberApproximateLocationRequest { granted++ }
                    TextButton(onClick = request::request) { androidx.compose.material3.Text("Use My Location") }
                }
            }
        }
        compose.onNodeWithText("Use My Location").performClick()
        compose.waitForIdle()
    }

    @Test
    fun allowedAlreadyItAnswersAtOnce() {
        granting = true
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        askWith(allow = true)
        assertEquals(1, granted)
        assertEquals(0, asked)
        compose.onNodeWithText("Allow Approximate Location?").assertDoesNotExist()
    }

    @Test
    fun otherwiseItExplainsFirstThenAsksAndroid() {
        askWith(allow = true)
        compose.onNodeWithText("Not Now").assertExists()
        assertEquals("answered without asking", 0, granted)
        compose.onNodeWithText("Allow").performClick()
        compose.waitForIdle()
        assertEquals(1, asked)
        assertEquals(1, granted)
    }

    @Test
    fun declinedForGoodItOffersAndroidSettings() {
        // Android won't ask again: "Allow" answered no at once and nothing else happened.
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        askWith(allow = false)
        compose.onNodeWithText("Allow").performClick()
        compose.waitForIdle()
        assertEquals(0, granted)
        compose.onNodeWithText("Location Access Is Off").assertExists()
    }

    @Test
    fun decliningOnceJustLeavesItOff() {
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION, true)
        askWith(allow = false)
        compose.onNodeWithText("Allow").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Location Access Is Off").assertDoesNotExist()
    }

    @Test
    fun notNowAsksNothing() {
        askWith(allow = true)
        compose.onNodeWithText("Not Now").performClick()
        compose.waitForIdle()
        assertEquals(0, asked)
        assertEquals(0, granted)
    }
}
