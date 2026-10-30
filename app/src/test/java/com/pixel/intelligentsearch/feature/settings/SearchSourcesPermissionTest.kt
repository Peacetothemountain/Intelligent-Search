package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Turning on Contacts and declining the permission once just leaves Contacts off, as with Calendar; only when
 * Android will no longer ask (declined for good) does the page offer to open the app's Android settings.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class SearchSourcesPermissionTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether Android shows its reason after the user's "Don't allow": after a first decline, not after the last. */
    private var androidWillAskAgain = true

    /** Answers every permission request with a denial, as the system dialog would after "Don't allow". */
    private val denyingOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS, androidWillAskAgain)
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, mapOf(Manifest.permission.READ_CONTACTS to false) as O)
            }
        }
    }

    @Before fun setUp() {
        hilt.inject()
        // The page's Material Morph animation (on by default) never idles; this test is about the page's rows.
        prefs.edit().clear().putBoolean(SettingsManager.MORPH_ANIMATION_ENABLED.name, false).commit()
    }

    private fun turnOnContactsAndDecline(androidWillAskAgain: Boolean) {
        // Declined once before, so Android shows its reason as it asks; whether it still does after this decline
        // tells a decline for good from one more. Only closing the prompt, never declined, isn't for good.
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS, true)
        this.androidWillAskAgain = androidWillAskAgain
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides denyingOwner) {
                    SearchSourcesScreen({}, {})
                }
            }
        }
        compose.waitForIdle()
        compose.onNode(isToggleable() and hasContentDescription("Contacts")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Allow").performClick()
        compose.waitForIdle()
    }

    @Test
    fun decliningOnceJustLeavesContactsOff() {
        turnOnContactsAndDecline(androidWillAskAgain = true)
        compose.onNodeWithText("Contacts Access Is Off").assertDoesNotExist()
        assertFalse(prefs[SettingsManager.SEARCH_CONTACTS])
    }

    @Test
    fun decliningForGoodOffersAndroidSettings() {
        turnOnContactsAndDecline(androidWillAskAgain = false)
        compose.onNodeWithText("Contacts Access Is Off").assertExists()
        assertFalse(prefs[SettingsManager.SEARCH_CONTACTS])
    }
}
